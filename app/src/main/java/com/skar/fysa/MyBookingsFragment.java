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
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MyBookingsFragment extends Fragment {

    private static final String NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"; // Test
    private final List<Booking> bookingList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerBookings;
    private BookingAdapter adapter;
    private NativeAdView nativeAdView;
    private NativeAd loadedNativeAd;
    private FirebaseFirestore firestore;

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

        firestore = FirebaseFirestore.getInstance();

        swipeRefreshLayout.setOnRefreshListener(this::loadBookingsFromFirestore);

        MobileAds.initialize(requireContext(), initializationStatus -> loadNativeAd());

        swipeRefreshLayout.setRefreshing(true);
        loadBookingsFromFirestore();
    }

    private void loadBookingsFromFirestore() {
        bookingList.clear();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            swipeRefreshLayout.setRefreshing(false);
            swipeRefreshLayout.setEnabled(false);
            return;
        }

        swipeRefreshLayout.setEnabled(true);
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        firestore.collection("spaces")
                .get()
                .addOnSuccessListener(spacesSnapshot -> {
                    List<DocumentSnapshot> spaceDocs = spacesSnapshot.getDocuments();
                    if (spaceDocs.isEmpty()) {
                        swipeRefreshLayout.setRefreshing(false);
                        Snackbar.make(requireView(), "No bookings found", Snackbar.LENGTH_SHORT).show();
                        return;
                    }

                    final int[] processedCount = {0}; // Track processed spaces

                    for (DocumentSnapshot spaceDoc : spaceDocs) {
                        String spaceId = spaceDoc.getId();
                        String ownerId = spaceDoc.getString("ownerId");
                        String spaceName = spaceDoc.getString("name");
                        String category = spaceDoc.getString("category");
                        String spaceAddress = spaceDoc.getString("address"); // ✅ fetch address

                        CollectionReference bookingsRef = firestore.collection("spaces")
                                .document(spaceId)
                                .collection("bookings");

                        bookingsRef.whereEqualTo("userId", currentUserId)
                                .get()
                                .addOnSuccessListener(bookingSnapshots -> {
                                    for (DocumentSnapshot bookingSnap : bookingSnapshots.getDocuments()) {
                                        BookingsDataModel bookingData = bookingSnap.toObject(BookingsDataModel.class);
                                        if (bookingData == null) continue;

                                        boolean isOwner = currentUserId.equals(ownerId);
                                        boolean isUserBooking = currentUserId.equals(bookingData.getUserId());

                                        if (isOwner || isUserBooking) {
                                            Booking booking = new Booking(
                                                    bookingSnap.getId(),
                                                    bookingData.getUserId(),
                                                    ownerId,
                                                    spaceId,
                                                    spaceName,
                                                    spaceAddress, // ✅ pass address
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

                                    processedCount[0]++;
                                    if (processedCount[0] == spaceDocs.size()) {
                                        // All spaces processed
                                        bookingList.sort((b1, b2) -> Long.compare(b2.getCreatedAt(), b1.getCreatedAt()));
                                        adapter.submitList(new ArrayList<>(bookingList));
                                        swipeRefreshLayout.setRefreshing(false);

                                        if (bookingList.isEmpty()) {
                                            Snackbar.make(requireView(), "No bookings found", Snackbar.LENGTH_SHORT).show();
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    processedCount[0]++;
                                    if (processedCount[0] == spaceDocs.size()) {
                                        swipeRefreshLayout.setRefreshing(false);
                                        if (bookingList.isEmpty()) {
                                            Snackbar.make(requireView(), "No bookings found", Snackbar.LENGTH_SHORT).show();
                                        }
                                    }
                                    Snackbar.make(requireView(), "Failed to load bookings: " + e.getMessage(),
                                            Snackbar.LENGTH_LONG).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Snackbar.make(requireView(), "Failed to load spaces: " + e.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                });
    }

    private void cancelBooking(Booking booking) {
        if (booking == null || FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        firestore.collection("spaces")
                .document(booking.getSpaceId())
                .collection("bookings")
                .document(booking.getBookingId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Remove booking_reference from user document map
                    firestore.collection("users")
                            .document(currentUserId)
                            .update("booking_reference." + booking.getBookingId(), FieldValue.delete())
                            .addOnSuccessListener(aVoid1 -> {
                                bookingList.remove(booking);
                                adapter.submitList(new ArrayList<>(bookingList));
                                Snackbar.make(requireView(), "Booking cancelled successfully", Snackbar.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Snackbar.make(requireView(), "Failed to remove booking reference: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Snackbar.make(requireView(), "Failed to cancel booking: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
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
            TextView tvSpaceName, tvSpaceAddress, tvSpaceType, tvDate, tvStatus, tvCancel;
            Button btnDirections;

            BookingViewHolder(@NonNull View itemView) {
                super(itemView);
                tvSpaceName = itemView.findViewById(R.id.tvBookingName);
                tvSpaceAddress = itemView.findViewById(R.id.tvBookingAddress); // ✅ bind address view
                tvSpaceType = itemView.findViewById(R.id.tvBookingType);
                tvDate = itemView.findViewById(R.id.tvBookingDate);
                tvStatus = itemView.findViewById(R.id.tvBookingStatus);
                tvCancel = itemView.findViewById(R.id.btnAction);
                btnDirections = itemView.findViewById(R.id.btnDirections);
            }

            void bind(Booking booking) {
                tvSpaceName.setText(booking.getSpaceName());
                tvSpaceAddress.setText(booking.getSpaceAddress()); // ✅ set address
                tvSpaceType.setText(booking.getSpaceType());
                tvDate.setText(booking.getBookingDate());
                tvStatus.setText(booking.getStatus());

                if (tvCancel != null) {
                    tvCancel.setOnClickListener(v -> cancelBooking(booking));
                }

                if (btnDirections != null) {
                    btnDirections.setOnClickListener(v -> getDirections(booking.getLatitude(), booking.getLongitude()));
                }
            }
        }
    }
}
