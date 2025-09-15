package com.skar.fysa;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import com.google.android.material.snackbar.Snackbar;
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

    private final List<Object> mixedItems = new ArrayList<>();
    private final List<Booking> bookingList = new ArrayList<>();

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private DatabaseReference notificationsRef;
    private ValueEventListener notificationsListener;

    private static final String NATIVE_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/2247696110"; // Test Ad Unit ID

    private static final int PAGE_SIZE = 10;
    private int currentPage = 0;

    public MyBookingsFragment() {
        super(R.layout.fragment_my_bookings);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerBookings = view.findViewById(R.id.recyclerBookings);
        recyclerBookings.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new BookingAdapter();
        recyclerBookings.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadBookingsFromFirebase);

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

                bookingList.sort((b1, b2) -> {
                    Long t1 = b1.getCreatedAt();
                    Long t2 = b2.getCreatedAt();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return Long.compare(t2, t1);
                });

                currentPage = 0;
                loadNextPage();

                swipeRefreshLayout.setRefreshing(false);

                if (bookingList.isEmpty()) {
                    Snackbar.make(requireView(), "No bookings found", Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefreshLayout.setRefreshing(false);
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    Snackbar.make(requireView(),
                            "Failed to load bookings: " + error.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                }
            }
        };

        notificationsRef.addValueEventListener(notificationsListener);
    }

    private void loadNextPage() {
        int start = currentPage * PAGE_SIZE;
        if (start >= bookingList.size()) return;

        int end = Math.min(start + PAGE_SIZE, bookingList.size());
        List<Booking> pageItems = bookingList.subList(start, end);

        for (int i = 0; i < pageItems.size(); i++) {
            mixedItems.add(pageItems.get(i));

            if ((mixedItems.size()) % 3 == 0) {
                loadNativeAdAt(mixedItems.size());
            }
        }

        currentPage++;
        adapter.setShowLoadMore(currentPage * PAGE_SIZE < bookingList.size());
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
                Snackbar.make(requireView(),
                        "Booking cancelled successfully", Snackbar.LENGTH_SHORT).show();
            } else if (task.getException() != null) {
                Snackbar.make(requireView(),
                        "Failed to cancel booking: " + task.getException().getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notificationsRef != null && notificationsListener != null) {
            notificationsRef.removeEventListener(notificationsListener);
        }

        for (Object item : mixedItems) {
            if (item instanceof NativeAd) {
                ((NativeAd) item).destroy();
            }
        }
    }

    // ---------------------- INNER BOOKING ADAPTER ----------------------

    private class BookingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_BOOKING = 1;
        private static final int TYPE_NATIVE_AD = 2;
        private static final int TYPE_LOAD_MORE = 3;

        private boolean showLoadMore = true;

        public void setShowLoadMore(boolean show) {
            this.showLoadMore = show;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mixedItems.size() + (showLoadMore ? 1 : 0);
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= mixedItems.size()) return TYPE_LOAD_MORE;
            Object item = mixedItems.get(position);
            if (item instanceof Booking) return TYPE_BOOKING;
            if (item instanceof NativeAd) return TYPE_NATIVE_AD;
            return TYPE_BOOKING;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_BOOKING) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_my_bookings_list, parent, false);
                return new BookingViewHolder(view);
            } else if (viewType == TYPE_NATIVE_AD) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_native_ad, parent, false);
                return new NativeAdViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_load_more, parent, false);
                return new LoadMoreViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof BookingViewHolder) {
                ((BookingViewHolder) holder).bind((Booking) mixedItems.get(position));
            } else if (holder instanceof NativeAdViewHolder) {
                ((NativeAdViewHolder) holder).bind((NativeAd) mixedItems.get(position));
            } else if (holder instanceof LoadMoreViewHolder) {
                ((LoadMoreViewHolder) holder).bind(() -> loadNextPage());
            }
        }

        // --- Booking ViewHolder ---
        private class BookingViewHolder extends RecyclerView.ViewHolder {
            TextView tvSpaceName, tvSpaceType, tvDate, tvStatus, tvCancel;

            BookingViewHolder(@NonNull View itemView) {
                super(itemView);
                tvSpaceName = itemView.findViewById(R.id.tvBookingName);
                tvSpaceType = itemView.findViewById(R.id.tvBookingType);
                tvDate = itemView.findViewById(R.id.tvBookingDate);
                tvStatus = itemView.findViewById(R.id.tvBookingStatus);
                tvCancel = itemView.findViewById(R.id.btnAction);
            }

            void bind(Booking booking) {
                tvSpaceName.setText(booking.getSpaceName());
                tvSpaceType.setText(booking.getSpaceType());
                tvDate.setText(booking.getBookingDate());
                tvStatus.setText(booking.getStatus());
                tvCancel.setOnClickListener(v -> cancelBooking(booking));
            }
        }

        // --- NativeAd ViewHolder ---
        private class NativeAdViewHolder extends RecyclerView.ViewHolder {
            // Your existing NativeAd binding logic
            NativeAdViewHolder(@NonNull View itemView) { super(itemView); }
            void bind(NativeAd nativeAd) { /* bind native ad here */ }
        }

        // --- Load More ViewHolder ---
        private class LoadMoreViewHolder extends RecyclerView.ViewHolder {
            TextView tvLoadMore;

            LoadMoreViewHolder(@NonNull View itemView) {
                super(itemView);
                tvLoadMore = itemView.findViewById(R.id.tvLoadMore);
            }

            void bind(Runnable onClick) {
                tvLoadMore.setOnClickListener(v -> onClick.run());
            }
        }
    }
}
