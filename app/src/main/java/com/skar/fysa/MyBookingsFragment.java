package com.skar.fysa;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MyBookingsFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerBookings;
    private BookingAdapter adapter;

    // Store both Bookings and Native Ads
    private final List<Object> mixedItems = new ArrayList<>();
    private final List<Booking> bookingList = new ArrayList<>();

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private DatabaseReference notificationsRef;
    private ValueEventListener notificationsListener;

    private static final String NATIVE_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/2247696110"; // Test Ad Unit ID

    public MyBookingsFragment() {
        super(R.layout.fragment_my_bookings);
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerBookings = view.findViewById(R.id.recyclerBookings);
        recyclerBookings.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new BookingAdapter(mixedItems, BookingAdapter.Mode.USER, this::cancelBooking);
        recyclerBookings.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadBookingsFromFirebase);

        // Initialize AdMob
        MobileAds.initialize(requireContext(), initializationStatus -> {});
        swipeRefreshLayout.setRefreshing(true);
        loadBookingsFromFirebase();
    }

    private void loadBookingsFromFirebase() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            swipeRefreshLayout.setRefreshing(false);
            swipeRefreshLayout.setEnabled(false);
            return;
        }

        swipeRefreshLayout.setEnabled(true);

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        notificationsRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("notifications");

        if (notificationsListener != null) {
            notificationsRef.removeEventListener(notificationsListener);
        }

        notificationsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                bookingList.clear();
                mixedItems.clear();

                for (DataSnapshot ownerSnapshot : snapshot.getChildren()) {
                    for (DataSnapshot notificationSnap : ownerSnapshot.getChildren()) {
                        NotificationModel notification = notificationSnap.getValue(NotificationModel.class);
                        if (notification != null && notification.getUserId().equals(currentUserId)) {
                            Booking booking = new Booking(
                                    notificationSnap.getKey(),
                                    notification.getUserId(),
                                    ownerSnapshot.getKey(),
                                    notification.getSpaceId(),
                                    notification.getSpaceName(),
                                    notification.getSpaceType(),
                                    notification.getBookingDate(),
                                    notification.getStatus(),
                                    notification.getUsername()
                            );
                            booking.setCreatedAt(notification.getCreatedAt());
                            bookingList.add(booking);
                        }
                    }
                }

                if (bookingList.isEmpty()) {
                    Toast.makeText(requireContext(),
                            "No bookings found", Toast.LENGTH_SHORT).show();
                }

                insertBookingsWithAds();
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefreshLayout.setRefreshing(false);
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    Toast.makeText(requireContext(),
                            "Failed to load bookings: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        };

        notificationsRef.addValueEventListener(notificationsListener);
    }

    private void insertBookingsWithAds() {
        mixedItems.clear();

        for (int i = 0; i < bookingList.size(); i++) {
            mixedItems.add(bookingList.get(i));

            // Insert a native ad after every 3 bookings
            if ((i + 1) % 3 == 0) {
                int adPosition = mixedItems.size();
                loadNativeAdAt(adPosition);
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void loadNativeAdAt(int position) {
        AdLoader adLoader = new AdLoader.Builder(requireContext(), NATIVE_AD_UNIT_ID)
                .forNativeAd(nativeAd -> {
                    if (position <= mixedItems.size()) {
                        mixedItems.add(position, nativeAd);
                        adapter.notifyItemInserted(position);
                    }
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull com.google.android.gms.ads.LoadAdError adError) {
                        Log.e("MyBookingsFragment", "Failed to load native ad: " + adError.getMessage());
                    }
                })
                .build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void cancelBooking(Booking booking) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null || booking == null) return;

        String ownerId = booking.getOwnerId();
        String notificationId = booking.getBookingId();

        DatabaseReference notificationRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("notifications")
                .child(ownerId)
                .child(notificationId);

        notificationRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                bookingList.remove(booking);
                mixedItems.remove(booking);
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(),
                        "Booking cancelled successfully", Toast.LENGTH_SHORT).show();
            } else if (task.getException() != null) {
                Toast.makeText(requireContext(),
                        "Failed to cancel booking: " + task.getException().getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notificationsRef != null && notificationsListener != null) {
            notificationsRef.removeEventListener(notificationsListener);
        }

        // Destroy native ads when fragment is destroyed
        for (Object item : mixedItems) {
            if (item instanceof NativeAd) {
                ((NativeAd) item).destroy();
            }
        }
    }
}
