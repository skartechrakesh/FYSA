package com.skar.fysa;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
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
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MyBookingsFragment extends Fragment {

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static final String NATIVE_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/2247696110"; // Test Ad Unit ID
    private final List<Booking> bookingList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerBookings;
    private BookingAdapter adapter;
    private NativeAdView nativeAdView;
    private NativeAd loadedNativeAd;
    private DatabaseReference spacesRef;
    private ValueEventListener spacesListener;

    public MyBookingsFragment() {
        super(R.layout.fragment_my_bookings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerBookings = view.findViewById(R.id.recyclerBookings);
        nativeAdView = view.findViewById(R.id.native_ad_view);

        recyclerBookings.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BookingAdapter();
        recyclerBookings.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadBookingsFromFirebase);

        MobileAds.initialize(requireContext(), initializationStatus -> loadNativeAd());

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
        spacesRef = FirebaseDatabase.getInstance(DB_URL).getReference("spaces");

        if (spacesListener != null) {
            spacesRef.removeEventListener(spacesListener);
        }

        spacesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                bookingList.clear();

                for (DataSnapshot spaceSnap : snapshot.getChildren()) {
                    String spaceId = spaceSnap.getKey();
                    String ownerId = spaceSnap.child("ownerId").getValue(String.class);
                    String spaceName = spaceSnap.child("name").getValue(String.class);
                    String category = spaceSnap.child("category").getValue(String.class);

                    DataSnapshot bookingsSnap = spaceSnap.child("bookings");
                    for (DataSnapshot bookingSnap : bookingsSnap.getChildren()) {
                        BookingsDataModel bookingData = bookingSnap.getValue(BookingsDataModel.class);
                        if (bookingData == null) continue;

                        boolean isOwner = currentUserId.equals(ownerId);
                        boolean isUserBooking = currentUserId.equals(bookingData.getUserId());

                        if (isOwner || isUserBooking) {
                            // ✅ Updated constructor call to pass all required arguments
                            Booking booking = new Booking(
                                    bookingSnap.getKey(),
                                    bookingData.getUserId(),
                                    ownerId,
                                    spaceId,
                                    spaceName,
                                    category,
                                    bookingData.getBookingDate(),
                                    bookingData.getStatus(),
                                    bookingData.getUsername(),
                                    bookingData.getCreatedAt(),
                                    bookingData.getLatitude(),
                                    bookingData.getLongitude()
                            );

                            bookingList.add(booking);
                        }
                    }
                }

                // Sort by latest
                bookingList.sort((b1, b2) -> Long.compare(b2.getCreatedAt(), b1.getCreatedAt()));

                adapter.submitList(new ArrayList<>(bookingList));
                swipeRefreshLayout.setRefreshing(false);

                if (bookingList.isEmpty()) {
                    Snackbar.make(requireView(), "No bookings found", Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefreshLayout.setRefreshing(false);
                Snackbar.make(requireView(),
                        "Failed to load bookings: " + error.getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        };

        spacesRef.addValueEventListener(spacesListener);
    }

    private void cancelBooking(Booking booking) {
        if (booking == null) return;

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference bookingRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("spaces")
                .child(booking.getSpaceId())
                .child("bookings")
                .child(booking.getBookingId());

        DatabaseReference userBookingRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(currentUserId)
                .child("booking_reference")
                .child(booking.getBookingId());

        // First delete from spaces node
        bookingRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Now also delete from user's booking_reference
                userBookingRef.removeValue().addOnCompleteListener(refTask -> {
                    if (refTask.isSuccessful()) {
                        bookingList.remove(booking);
                        adapter.submitList(new ArrayList<>(bookingList));
                        Snackbar.make(requireView(),
                                "Booking cancelled successfully",
                                Snackbar.LENGTH_SHORT).show();
                    } else if (refTask.getException() != null) {
                        Snackbar.make(requireView(),
                                "Failed to remove user reference: " + refTask.getException().getMessage(),
                                Snackbar.LENGTH_LONG).show();
                    }
                });
            } else if (task.getException() != null) {
                Snackbar.make(requireView(),
                        "Failed to cancel booking: " + task.getException().getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void loadNativeAd() {
        if (nativeAdView == null) return;

        AdLoader adLoader = new AdLoader.Builder(requireContext(), NATIVE_AD_UNIT_ID)
                .forNativeAd(nativeAd -> {
                    if (loadedNativeAd != null) loadedNativeAd.destroy();
                    loadedNativeAd = nativeAd;
                    nativeAdView.setVisibility(View.VISIBLE);
                    populateNativeAdView(nativeAd, nativeAdView);
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull com.google.android.gms.ads.LoadAdError adError) {
                        Log.e("MyBookingsFragment", "Failed to load native ad: " + adError.getMessage());
                        if (nativeAdView != null) nativeAdView.setVisibility(View.GONE);
                    }
                }).build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        TextView headline = adView.findViewById(R.id.ad_headline);
        TextView body = adView.findViewById(R.id.ad_body);
        Button cta = adView.findViewById(R.id.ad_call_to_action);

        if (headline != null) headline.setText(nativeAd.getHeadline());
        if (body != null) body.setText(nativeAd.getBody());
        if (cta != null) cta.setText(nativeAd.getCallToAction());

        adView.setHeadlineView(headline);
        adView.setBodyView(body);
        adView.setCallToActionView(cta);
        adView.setNativeAd(nativeAd);
    }

    private void getDirections(double latitude, double longitude) {
        if (latitude == 0.0 && longitude == 0.0) {
            Snackbar.make(requireView(), "Location not available for this booking.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        String uri = String.format(Locale.getDefault(), "geo:0,0?q=%f,%f", latitude, longitude);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(requireContext(), "Google Maps app not installed.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (spacesRef != null && spacesListener != null) {
            spacesRef.removeEventListener(spacesListener);
        }
        if (loadedNativeAd != null) loadedNativeAd.destroy();
    }

    // ---------------------- INNER BOOKING ADAPTER ----------------------

    private class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

        private final List<Booking> items = new ArrayList<>();

        void submitList(List<Booking> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_bookings_list, parent, false);
            return new BookingViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private class BookingViewHolder extends RecyclerView.ViewHolder {
            TextView tvSpaceName, tvSpaceType, tvDate, tvStatus, tvCancel;
            Button btnDirections;

            BookingViewHolder(@NonNull View itemView) {
                super(itemView);
                tvSpaceName = itemView.findViewById(R.id.tvBookingName);
                tvSpaceType = itemView.findViewById(R.id.tvBookingType);
                tvDate = itemView.findViewById(R.id.tvBookingDate);
                tvStatus = itemView.findViewById(R.id.tvBookingStatus);
                tvCancel = itemView.findViewById(R.id.btnAction);
                btnDirections = itemView.findViewById(R.id.btnDirections);
            }

            void bind(Booking booking) {
                tvSpaceName.setText(booking.getSpaceName());
                tvSpaceType.setText(booking.getSpaceType());
                tvDate.setText(booking.getBookingDate());
                tvStatus.setText(booking.getStatus());

                if (tvCancel != null) {
                    tvCancel.setOnClickListener(v -> cancelBooking(booking));
                }

                if (btnDirections != null) {
                    btnDirections.setOnClickListener(v -> {
                        getDirections(booking.getLatitude(), booking.getLongitude());
                    });
                }
            }
        }
    }
}