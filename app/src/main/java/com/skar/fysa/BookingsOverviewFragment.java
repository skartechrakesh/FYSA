package com.skar.fysa;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.res.ColorStateList;
import android.widget.Button;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;


import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BookingsOverviewFragment extends Fragment {

    private LinearLayout bookingsListContainer;
    private BarChart barChart;
    private DatabaseReference spacesRef;
    private FirebaseUser currentUser;
    private ValueEventListener bookingsListener;

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private final ArrayList<DataSnapshot> allBookings = new ArrayList<>();
    private int loadedCount = 0;
    private static final int BATCH_SIZE = 10;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_bookings_overview, container, false);
        bookingsListContainer = view.findViewById(R.id.bookingsListContainer);
        barChart = view.findViewById(R.id.bookingsBarChart);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            spacesRef = FirebaseDatabase.getInstance(DB_URL).getReference("spaces");
            loadBookings();
        } else {
            clearUI();
            showSnackbar("Please login to view bookings");
        }

        return view;
    }

    private void loadBookings() {
        if (spacesRef == null || currentUser == null) return;

        bookingsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allBookings.clear();
                Map<Integer, Integer> monthlyCounts = new HashMap<>();

                for (DataSnapshot spaceSnap : snapshot.getChildren()) {
                    String ownerId = spaceSnap.child("ownerId").getValue(String.class);
                    DataSnapshot bookingsSnap = spaceSnap.child("bookings");

                    for (DataSnapshot bookingSnap : bookingsSnap.getChildren()) {
                        BookingsDataModel booking = bookingSnap.getValue(BookingsDataModel.class);

                        if (booking == null) continue;

                        boolean isOwner = currentUser.getUid().equals(ownerId);
                        boolean isUserBooking = currentUser.getUid().equals(booking.getUserId());

                        // Only add if owner OR the one who booked
                        if (isOwner || isUserBooking) {
                            allBookings.add(bookingSnap);

                            try {
                                Date date = sdf.parse(booking.getBookingDate());
                                if (date != null) {
                                    Calendar cal = Calendar.getInstance();
                                    cal.setTime(date);
                                    int month = cal.get(Calendar.MONTH);
                                    monthlyCounts.put(month, monthlyCounts.getOrDefault(month, 0) + 1);
                                }
                            } catch (ParseException ignored) {}
                        }
                    }
                }

                // Sort latest first
                Collections.sort(allBookings, (b1, b2) -> {
                    try {
                        Date d1 = sdf.parse(b1.child("bookingDate").getValue(String.class));
                        Date d2 = sdf.parse(b2.child("bookingDate").getValue(String.class));
                        if (d1 != null && d2 != null) return d2.compareTo(d1);
                    } catch (ParseException ignored) {}
                    return 0;
                });

                bookingsListContainer.removeAllViews();
                loadedCount = 0;
                loadNextBatch(getLayoutInflater());
                updateBarChart(monthlyCounts);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showSnackbar("Failed to fetch bookings: " + error.getMessage());
            }
        };

        spacesRef.addValueEventListener(bookingsListener);
    }

    private void loadNextBatch(LayoutInflater inflater) {
        int end = Math.min(loadedCount + BATCH_SIZE, allBookings.size());

        for (int i = loadedCount; i < end; i++) {
            addBookingRow(inflater, allBookings.get(i));
        }

        loadedCount = end;

        if (loadedCount < allBookings.size()) addLoadMoreLink(inflater);
    }

    private void addBookingRow(LayoutInflater inflater, DataSnapshot bookingSnap) {
        View row = inflater.inflate(R.layout.item_bookings_overview_list, bookingsListContainer, false);

        TextView tvName = row.findViewById(R.id.tvBookingName);
        TextView tvUsername = row.findViewById(R.id.tvBookingUsername);
        TextView tvDate = row.findViewById(R.id.tvBookingDate);
        TextView tvCategory = row.findViewById(R.id.tvBookingCategory);
        TextView tvContact = row.findViewById(R.id.tvContact);
        TextView tvStatus = row.findViewById(R.id.tvBookingStatus);
        Button btnAction = row.findViewById(R.id.btnAction);

        BookingsDataModel booking = bookingSnap.getValue(BookingsDataModel.class);
        if (booking == null) return;

        tvName.setText(booking.getSpaceName());
        tvUsername.setText("Booked by: " + booking.getUsername());
        tvDate.setText(booking.getBookingDate());
        tvCategory.setText(booking.getSpaceCategory());
        tvContact.setText("Contact: " + booking.getUserContact());

        // Set initial status text
        tvStatus.setText(booking.isRead() ? "Visit Accepted" : "Visit Booked");

        // Configure button based on read status
        if (!booking.isRead()) {
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setText("Mark as Read");
            btnAction.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.red_700)));
        } else {
            btnAction.setVisibility(View.GONE);
        }

        // Button click to mark as read
        btnAction.setOnClickListener(v -> {
            DatabaseReference bookingRef = bookingSnap.getRef().child("read");
            bookingRef.setValue(true).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    tvStatus.setText("Visit Accepted");
                    btnAction.setVisibility(View.GONE);
                } else {
                    Snackbar.make(requireView(),
                            "Failed to update status: " + Objects.requireNonNull(task.getException()).getMessage(),
                            Snackbar.LENGTH_SHORT).show();
                }
            });
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
        String[] monthLabels = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (spacesRef != null && bookingsListener != null) {
            spacesRef.removeEventListener(bookingsListener);
            bookingsListener = null;
        }
    }
}
