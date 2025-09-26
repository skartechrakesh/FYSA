package com.skar.fysa;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyBookingsFragment extends Fragment {

    private final List<Booking> bookingList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerBookings;
    private BookingAdapter adapter;
    private FirebaseFirestore firestore;

    public MyBookingsFragment() {
        super(R.layout.fragment_my_bookings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerBookings = view.findViewById(R.id.recyclerBookings);

        recyclerBookings.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BookingAdapter();
        recyclerBookings.setAdapter(adapter);

        firestore = FirebaseFirestore.getInstance();

        swipeRefreshLayout.setOnRefreshListener(this::loadBookingsFromFirestore);
        swipeRefreshLayout.setRefreshing(true);
        loadBookingsFromFirestore();
    }

    private void loadBookingsFromFirestore() {
        bookingList.clear();
        adapter.submitList(new ArrayList<>()); // Clear RecyclerView immediately
        swipeRefreshLayout.setRefreshing(true);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            swipeRefreshLayout.setRefreshing(false);
            swipeRefreshLayout.setEnabled(false);
            return;
        }

        swipeRefreshLayout.setEnabled(true);
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        firestore.collection("references")
                .document("booking_ref")
                .get()
                .addOnSuccessListener(refDoc -> {
                    if (!refDoc.exists() || !refDoc.contains(currentUserId)) {
                        finishLoading();
                        return;
                    }

                    List<String> bookingIds = (List<String>) refDoc.get(currentUserId);
                    if (bookingIds == null || bookingIds.isEmpty()) {
                        finishLoading();
                        return;
                    }

                    final int total = bookingIds.size();
                    final int[] processed = {0};

                    for (String bookingId : bookingIds) {
                        firestore.collection("bookings")
                                .document(bookingId)
                                .get()
                                .addOnSuccessListener(bookingSnap -> {
                                    if (bookingSnap.exists()) {
                                        String bookedBy = bookingSnap.getString("bookedBy");
                                        Long createdAt = bookingSnap.getLong("createdAt");
                                        String spaceId = bookingSnap.getString("spaceId");

                                        if (spaceId != null) {
                                            firestore.collection("spaces")
                                                    .document(spaceId)
                                                    .get()
                                                    .addOnSuccessListener(spaceSnap -> {
                                                        addBookingFromSpaceSnap(spaceSnap, bookingSnap, bookingId, bookedBy, createdAt);
                                                        processed[0]++;
                                                        if (processed[0] == total) finishLoading();
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        processed[0]++;
                                                        if (processed[0] == total) finishLoading();
                                                    });
                                        } else {
                                            processed[0]++;
                                            if (processed[0] == total) finishLoading();
                                        }
                                    } else {
                                        processed[0]++;
                                        if (processed[0] == total) finishLoading();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    processed[0]++;
                                    if (processed[0] == total) finishLoading();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Snackbar.make(requireView(), "Failed to load bookings: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
    }

    private void addBookingFromSpaceSnap(@NonNull com.google.firebase.firestore.DocumentSnapshot spaceSnap,
                                         @NonNull com.google.firebase.firestore.DocumentSnapshot bookingSnap,
                                         String bookingId, String bookedBy, Long createdAt) {

        String spaceName = spaceSnap.getString("name");
        String spaceAddress = spaceSnap.getString("address");
        String spaceType = spaceSnap.getString("category");
        String ownerId = spaceSnap.getString("ownerId");
        double latitude = spaceSnap.contains("latitude") ? spaceSnap.getDouble("latitude") : 0.0;
        double longitude = spaceSnap.contains("longitude") ? spaceSnap.getDouble("longitude") : 0.0;

        Booking booking = new Booking(
                bookingId,
                bookedBy,
                null,
                ownerId,
                spaceSnap.getId(),
                spaceName,
                spaceAddress,
                spaceType,
                "Active",
                createdAt != null ? createdAt : 0,
                latitude,
                longitude,
                null
        );

        bookingList.add(booking);
    }

    private void finishLoading() {
        bookingList.sort((b1, b2) -> Long.compare(b2.getCreatedAt(), b1.getCreatedAt()));
        adapter.submitList(new ArrayList<>(bookingList));
        swipeRefreshLayout.setRefreshing(false);

        if (bookingList.isEmpty()) {
            Snackbar.make(requireView(), "No bookings found", Snackbar.LENGTH_SHORT).show();
        }
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
            TextView tvSpaceName, tvSpaceAddress, tvSpaceType, tvDate;
            Button btnDirections, btnDetails, btnAction;

            BookingViewHolder(@NonNull View itemView) {
                super(itemView);
                tvSpaceName = itemView.findViewById(R.id.tvBookingName);
                tvSpaceAddress = itemView.findViewById(R.id.tvBookingAddress);
                tvSpaceType = itemView.findViewById(R.id.tvBookingType);
                tvDate = itemView.findViewById(R.id.tvBookingDate);
                btnDirections = itemView.findViewById(R.id.btnDirections);
                btnDetails = itemView.findViewById(R.id.btnDetails);
                btnAction = itemView.findViewById(R.id.btnAction);
            }

            void bind(Booking booking) {
                tvSpaceName.setText(booking.getSpaceName() != null ? booking.getSpaceName() : "N/A");
                tvSpaceAddress.setText(booking.getSpaceAddress() != null ? booking.getSpaceAddress() : "N/A");
                tvSpaceType.setText(booking.getSpaceType() != null ? booking.getSpaceType() : "N/A");

                if (booking.getCreatedAt() > 0) {
                    Date date = new Date(booking.getCreatedAt());
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                    tvDate.setText(sdf.format(date));
                } else {
                    tvDate.setText("N/A");
                }

                btnDirections.setOnClickListener(v -> getDirections(booking.getLatitude(), booking.getLongitude()));

                btnDetails.setOnClickListener(v -> {
                    if (booking.getBookingId() == null) {
                        Snackbar.make(requireView(), "Booking ID missing", Snackbar.LENGTH_SHORT).show();
                        return;
                    }

                    firestore.collection("bookings")
                            .document(booking.getBookingId())
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists() && doc.contains("spaceId")) {
                                    String spaceId = doc.getString("spaceId");
                                    if (spaceId != null) {
                                        SpaceDetailsFragment fragment = new SpaceDetailsFragment();
                                        Bundle args = new Bundle();
                                        args.putBoolean("fromMyBookings", true);
                                        args.putString("spaceId", spaceId);
                                        fragment.setArguments(args);
                                        requireActivity().getSupportFragmentManager()
                                                .beginTransaction()
                                                .replace(R.id.fragmentContainer, fragment)
                                                .addToBackStack(null)
                                                .commit();
                                    } else {
                                        Snackbar.make(requireView(), "Space ID not found", Snackbar.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .addOnFailureListener(e ->
                                    Snackbar.make(requireView(), "Failed to fetch spaceId: " + e.getMessage(), Snackbar.LENGTH_LONG).show()
                            );
                });

                btnAction.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Cancel Booking")
                        .setMessage("Are you sure you want to cancel this booking?")
                        .setPositiveButton("Yes", (dialog, which) -> cancelBooking(booking))
                        .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                        .show()
                );
            }
        }
    }

    private void cancelBooking(Booking booking) {
        if (booking.getBookingId() == null || booking.getBookedBy() == null) {
            Snackbar.make(requireView(), "Cannot cancel this booking", Snackbar.LENGTH_SHORT).show();
            return;
        }

        String bookingId = booking.getBookingId();
        String userId = booking.getBookedBy();

        firestore.collection("bookings")
                .document(bookingId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !doc.contains("spaceId")) {
                        Snackbar.make(requireView(), "Booking or spaceId missing", Snackbar.LENGTH_SHORT).show();
                        return;
                    }

                    String spaceId = doc.getString("spaceId");

                    // ----- Update booking_ref -----
                    firestore.collection("references")
                            .document("booking_ref")
                            .get()
                            .addOnSuccessListener(refDoc -> {
                                if (refDoc.exists() && refDoc.contains(userId)) {
                                    List<String> userBookings = (List<String>) refDoc.get(userId);
                                    if (userBookings != null && userBookings.contains(bookingId)) {
                                        userBookings.remove(bookingId);

                                        if (userBookings.isEmpty()) {
                                            firestore.collection("references")
                                                    .document("booking_ref")
                                                    .update(userId, FieldValue.delete());
                                        } else {
                                            firestore.collection("references")
                                                    .document("booking_ref")
                                                    .update(userId, userBookings);
                                        }
                                    }
                                }

                                // ----- Update space_booking_ref -----
                                firestore.collection("references")
                                        .document("space_booking_ref")
                                        .get()
                                        .addOnSuccessListener(spaceDoc -> {
                                            if (spaceDoc.exists() && spaceDoc.contains(spaceId)) {
                                                List<String> spaceBookings = (List<String>) spaceDoc.get(spaceId);
                                                if (spaceBookings != null && spaceBookings.contains(bookingId)) {
                                                    spaceBookings.remove(bookingId);

                                                    if (spaceBookings.isEmpty()) {
                                                        firestore.collection("references")
                                                                .document("space_booking_ref")
                                                                .update(spaceId, FieldValue.delete());
                                                    } else {
                                                        firestore.collection("references")
                                                                .document("space_booking_ref")
                                                                .update(spaceId, spaceBookings);
                                                    }
                                                }
                                            }

                                            // Delete booking document
                                            firestore.collection("bookings")
                                                    .document(bookingId)
                                                    .delete()
                                                    .addOnSuccessListener(aVoid -> {
                                                        bookingList.remove(booking);
                                                        adapter.submitList(new ArrayList<>(bookingList));
                                                        Snackbar.make(requireView(), "Booking canceled", Snackbar.LENGTH_SHORT).show();
                                                    })
                                                    .addOnFailureListener(e ->
                                                            Snackbar.make(requireView(), "Failed to cancel booking: " + e.getMessage(), Snackbar.LENGTH_LONG).show()
                                                    );
                                        });
                            });
                })
                .addOnFailureListener(e ->
                        Snackbar.make(requireView(), "Failed to fetch booking: " + e.getMessage(), Snackbar.LENGTH_LONG).show()
                );
    }
}
