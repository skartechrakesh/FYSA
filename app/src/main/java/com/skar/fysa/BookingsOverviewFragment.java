package com.skar.fysa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BookingsOverviewFragment extends Fragment {

    private static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private static final int PAGE_SIZE = 20; // Adjust page size if needed

    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout bookingsListContainer;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private List<DocumentSnapshot> allBookings = new ArrayList<>();
    private int currentIndex = 0;
    private Button btnLoadMore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_bookings_overview, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        bookingsListContainer = view.findViewById(R.id.bookingsListContainer);

        // Add Load More button
        btnLoadMore = new Button(requireContext());
        btnLoadMore.setText("Load More");
        btnLoadMore.setVisibility(View.GONE);
        bookingsListContainer.addView(btnLoadMore);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (currentUser != null) {
                refreshBookings();
            } else {
                clearUI();
                showSnackbar("Please login to view bookings");
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        btnLoadMore.setOnClickListener(v -> loadNextBatch());

        if (currentUser != null) {
            refreshBookings();
        } else {
            clearUI();
            showSnackbar("Please login to view bookings");
        }

        return view;
    }

    private void refreshBookings() {
        bookingsListContainer.removeAllViews();
        bookingsListContainer.addView(btnLoadMore);
        allBookings.clear();
        currentIndex = 0;
        btnLoadMore.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(true);

        // Step 1: Get all spaces of owner
        firestore.collection("spaces")
                .whereEqualTo("ownerId", currentUser.getUid())
                .get()
                .addOnSuccessListener(spaceQuery -> {
                    if (spaceQuery.isEmpty()) {
                        showSnackbar("No bookings found for your spaces.");
                        swipeRefreshLayout.setRefreshing(false);
                        return;
                    }

                    List<String> spaceIds = new ArrayList<>();
                    for (DocumentSnapshot spaceSnap : spaceQuery.getDocuments()) {
                        spaceIds.add(spaceSnap.getId());
                    }

                    // Step 2: Get all bookings for these spaces
                    firestore.collection("bookings")
                            .whereIn("spaceId", spaceIds)
                            .get()
                            .addOnSuccessListener(bookingQuery -> {
                                if (bookingQuery.isEmpty()) {
                                    showSnackbar("No bookings found.");
                                    swipeRefreshLayout.setRefreshing(false);
                                    return;
                                }

                                allBookings.addAll(bookingQuery.getDocuments());

                                // Step 3: Sort bookings by createdAt descending
                                Collections.sort(allBookings, (o1, o2) -> {
                                    long t1 = o1.contains("createdAt") ? o1.getLong("createdAt") : 0;
                                    long t2 = o2.contains("createdAt") ? o2.getLong("createdAt") : 0;
                                    return Long.compare(t2, t1); // Descending
                                });

                                swipeRefreshLayout.setRefreshing(false);
                                loadNextBatch();
                            })
                            .addOnFailureListener(e -> {
                                showSnackbar("Failed to load bookings: " + e.getMessage());
                                swipeRefreshLayout.setRefreshing(false);
                            });

                })
                .addOnFailureListener(e -> {
                    showSnackbar("Failed to load spaces: " + e.getMessage());
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void loadNextBatch() {
        int endIndex = Math.min(currentIndex + PAGE_SIZE, allBookings.size());
        if (currentIndex >= endIndex) {
            btnLoadMore.setVisibility(View.GONE);
            return;
        }

        for (int i = currentIndex; i < endIndex; i++) {
            DocumentSnapshot bookingSnap = allBookings.get(i);
            String spaceId = bookingSnap.getString("spaceId");
            // Fetch space for this booking
            firestore.collection("spaces").document(spaceId).get()
                    .addOnSuccessListener(spaceSnap -> {
                        if (spaceSnap.exists()) {
                            addBookingRow(spaceSnap, bookingSnap);
                        }
                    });
        }

        currentIndex = endIndex;
        btnLoadMore.setVisibility(currentIndex < allBookings.size() ? View.VISIBLE : View.GONE);
    }

    private void addBookingRow(DocumentSnapshot spaceSnap, DocumentSnapshot bookingSnap) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View row = inflater.inflate(R.layout.item_bookings_overview_list, bookingsListContainer, false);

        TextView tvName = row.findViewById(R.id.tvBookingName);
        TextView tvUsername = row.findViewById(R.id.tvBookingUsername);
        TextView tvDate = row.findViewById(R.id.tvBookingDate);
        TextView tvCategory = row.findViewById(R.id.tvBookingCategory);
        TextView tvContact = row.findViewById(R.id.tvContact);

        Booking booking = bookingSnap.toObject(Booking.class);
        if (booking == null) return;

        tvName.setText(spaceSnap.contains("name") ? spaceSnap.getString("name") : "N/A");
        tvUsername.setText("Booked by: " +
                (booking.getBookedByUserName() != null ? booking.getBookedByUserName() : "N/A"));
        tvDate.setText(booking.getCreatedAt() > 0 ? sdf.format(booking.getCreatedAt()) : "N/A");
        tvCategory.setText(spaceSnap.contains("category") ? spaceSnap.getString("category") : "N/A");

        // Fetch latest contact from users collection
        String bookedByUserId = booking.getBookedBy();
        if (bookedByUserId != null) {
            firestore.collection("users")
                    .document(bookedByUserId)
                    .get()
                    .addOnSuccessListener(userDoc -> {
                        String contact = userDoc.getString("contact");
                        tvContact.setText("Contact: " + (contact != null ? contact : "N/A"));
                    })
                    .addOnFailureListener(e -> tvContact.setText("Contact: N/A"));
        } else {
            tvContact.setText("Contact: N/A");
        }

        bookingsListContainer.addView(row, bookingsListContainer.getChildCount() - 1); // above Load More button
    }

    private void clearUI() {
        bookingsListContainer.removeAllViews();
        bookingsListContainer.addView(btnLoadMore);
    }

    private void showSnackbar(String message) {
        if (getView() != null)
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
    }
}
