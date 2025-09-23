package com.skar.fysa;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BookingsOverviewFragment extends Fragment {

    private static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private static final int BATCH_SIZE = 10;

    private final ArrayList<DocumentSnapshot> allBookings = new ArrayList<>();
    private LinearLayout bookingsListContainer;
    private BarChart barChart;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;
    private int loadedCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_bookings_overview, container, false);
        bookingsListContainer = view.findViewById(R.id.bookingsListContainer);
        barChart = view.findViewById(R.id.bookingsBarChart);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();

        if (currentUser != null) {
            loadBookings();
        } else {
            clearUI();
            showSnackbar("Please login to view bookings");
        }

        return view;
    }

    private void loadBookings() {
        allBookings.clear();
        loadedCount = 0;

        firestore.collection("spaces")
                .orderBy("name")
                .get()
                .addOnSuccessListener(spacesSnapshot -> {
                    Map<Integer, Integer> monthlyCounts = new HashMap<>();
                    ArrayList<DocumentSnapshot> bookingDocs = new ArrayList<>();

                    for (DocumentSnapshot spaceSnap : spacesSnapshot.getDocuments()) {
                        String ownerId = spaceSnap.getString("ownerId");
                        Query bookingsQuery = firestore.collection("spaces")
                                .document(spaceSnap.getId())
                                .collection("bookings");

                        bookingsQuery.get().addOnSuccessListener(bookingsSnapshot -> {
                            for (DocumentSnapshot bookingSnap : bookingsSnapshot.getDocuments()) {
                                BookingsDataModel booking = bookingSnap.toObject(BookingsDataModel.class);
                                if (booking == null) continue;

                                boolean isOwner = currentUser.getUid().equals(ownerId);
                                boolean isUserBooking = currentUser.getUid().equals(booking.getUserId());

                                if (isOwner || isUserBooking) {
                                    bookingDocs.add(bookingSnap);

                                    // For bar chart, count by month using bookingDate
                                    try {
                                        Calendar cal = Calendar.getInstance();
                                        cal.setTime(sdf.parse(booking.getBookingDate()));
                                        int month = cal.get(Calendar.MONTH);
                                        monthlyCounts.put(month, monthlyCounts.getOrDefault(month, 0) + 1);
                                    } catch (ParseException ignored) {}
                                }
                            }

                            // Sort by createdAt descending
                            bookingDocs.sort((b1, b2) -> {
                                Long c1 = b1.getLong("createdAt");
                                Long c2 = b2.getLong("createdAt");
                                if (c1 == null) c1 = 0L;
                                if (c2 == null) c2 = 0L;
                                return c2.compareTo(c1); // latest first
                            });

                            allBookings.clear();
                            allBookings.addAll(bookingDocs);

                            bookingsListContainer.removeAllViews();
                            loadedCount = 0;
                            loadNextBatch(getLayoutInflater());
                            updateBarChart(monthlyCounts);
                        });
                    }
                })
                .addOnFailureListener(e -> showSnackbar("Failed to fetch bookings: " + e.getMessage()));
    }

    private void loadNextBatch(LayoutInflater inflater) {
        int end = Math.min(loadedCount + BATCH_SIZE, allBookings.size());
        for (int i = loadedCount; i < end; i++) {
            addBookingRow(inflater, allBookings.get(i));
        }
        loadedCount = end;

        if (loadedCount < allBookings.size()) addLoadMoreLink(inflater);
    }

    private void addBookingRow(LayoutInflater inflater, DocumentSnapshot bookingSnap) {
        View row = inflater.inflate(R.layout.item_bookings_overview_list, bookingsListContainer, false);

        TextView tvName = row.findViewById(R.id.tvBookingName);
        TextView tvUsername = row.findViewById(R.id.tvBookingUsername);
        TextView tvDate = row.findViewById(R.id.tvBookingDate);
        TextView tvCategory = row.findViewById(R.id.tvBookingCategory);
        TextView tvContact = row.findViewById(R.id.tvContact);
        TextView tvStatus = row.findViewById(R.id.tvBookingStatus);
        Button btnAction = row.findViewById(R.id.btnAction);

        BookingsDataModel booking = bookingSnap.toObject(BookingsDataModel.class);
        if (booking == null) return;

        tvName.setText(booking.getSpaceName());
        tvUsername.setText("Booked by: " + booking.getUsername());
        tvDate.setText(booking.getBookingDate());
        tvCategory.setText(booking.getSpaceCategory());
        tvContact.setText("Contact: " + booking.getUserContact());
        tvStatus.setText(booking.isRead() ? "Visit Accepted" : "Visit Booked");

        if (!booking.isRead()) {
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setText("Mark as Read");
            btnAction.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.red_700)));
        } else {
            btnAction.setVisibility(View.GONE);
        }

        btnAction.setOnClickListener(v -> {
            bookingSnap.getReference().update("read", true)
                    .addOnSuccessListener(aVoid -> {
                        tvStatus.setText("Visit Accepted");
                        btnAction.setVisibility(View.GONE);
                    })
                    .addOnFailureListener(e -> showSnackbar("Failed to update status: " + e.getMessage()));
        });

        bookingsListContainer.addView(row);
    }

    private void addLoadMoreLink(LayoutInflater inflater) {
        View existing = bookingsListContainer.findViewWithTag("load_more_link");
        if (existing != null) bookingsListContainer.removeView(existing);

        TextView loadMore = new TextView(requireContext());
        loadMore.setText("Load More");
        loadMore.setTag("load_more_link");
        loadMore.setTextSize(16f);
        loadMore.setPadding(0, 24, 0, 24);
        loadMore.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        loadMore.setTextColor(getResources().getColor(R.color.linkText, null));

        loadMore.setOnClickListener(v -> {
            bookingsListContainer.removeView(v);
            loadNextBatch(inflater);
        });

        bookingsListContainer.addView(loadMore);
    }

    private void updateBarChart(Map<Integer, Integer> monthlyCounts) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        String[] monthLabels = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        for (int i = 0; i < 12; i++)
            entries.add(new BarEntry(i, monthlyCounts.getOrDefault(i, 0)));

        BarDataSet dataSet = new BarDataSet(entries, "Bookings per Month");
        dataSet.setColor(ContextCompat.getColor(requireContext(), R.color.linkText));

        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(R.attr.colorOnSurface, typedValue, true);
        int textColor = typedValue.data;

        dataSet.setValueTextColor(textColor);
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        barChart.setData(data);
        barChart.setFitBars(true);
        barChart.getDescription().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setGranularity(1f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(textColor);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                return (index >= 0 && index < monthLabels.length) ? monthLabels[index] : "";
            }
        });

        barChart.getAxisLeft().setTextColor(textColor);
        barChart.getAxisRight().setTextColor(textColor);
        barChart.getLegend().setTextColor(textColor);
        barChart.invalidate();
    }

    private void clearUI() {
        bookingsListContainer.removeAllViews();
        barChart.clear();
        barChart.invalidate();
    }

    private void showSnackbar(String message) {
        if (getView() != null)
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
    }
}
