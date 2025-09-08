package com.skar.fysa;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
    private DatabaseReference notifRef;
    private FirebaseUser currentUser;
    private ValueEventListener notifListener;

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public BookingsOverviewFragment() {}

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
            String ownerId = currentUser.getUid();
            notifRef = FirebaseDatabase.getInstance(DB_URL)
                    .getReference("notifications")
                    .child(ownerId);
            loadNotifications();
        } else {
            clearUI();
            Toast.makeText(getContext(), "Please login to view bookings", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    private void loadNotifications() {
        if (notifRef == null) return;

        notifListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                bookingsListContainer.removeAllViews();
                ArrayList<NotificationModel> notifications = new ArrayList<>();
                Map<Integer, Integer> monthlyCounts = new HashMap<>();

                for (DataSnapshot notifSnap : snapshot.getChildren()) {
                    NotificationModel notif = notifSnap.getValue(NotificationModel.class);
                    if (notif == null) continue;

                    notifications.add(notif);

                    // Count per month
                    try {
                        Date date = sdf.parse(notif.getBookingDate());
                        if (date != null) {
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(date);
                            int month = cal.get(Calendar.MONTH);
                            monthlyCounts.put(month, monthlyCounts.getOrDefault(month, 0) + 1);
                        }
                    } catch (ParseException ignored) {}

                    // Inflate row
                    View row = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_booking_row, bookingsListContainer, false);

                    TextView tvName = row.findViewById(R.id.tvBookingName);
                    TextView tvUsername = row.findViewById(R.id.tvBookingUsername);
                    TextView tvDate = row.findViewById(R.id.tvBookingDate);
                    TextView tvType = row.findViewById(R.id.tvBookingType);
                    TextView tvStatus = row.findViewById(R.id.tvBookingStatus);
                    Button btnMarkRead = row.findViewById(R.id.btnAction);

                    tvName.setText(notif.getSpaceName());
                    tvUsername.setText("Booked by: " + notif.getUsername());
                    tvDate.setText(notif.getBookingDate());
                    tvType.setText(notif.getSpaceType() != null ? notif.getSpaceType() : "N/A");

                    // Show proper status and button without auto-changing
                    if (!notif.isRead()) {
                        tvStatus.setText("Visit Booked");
                        btnMarkRead.setVisibility(View.VISIBLE);
                        btnMarkRead.setText("Mark as Read");

                        btnMarkRead.setOnClickListener(v ->
                                notifSnap.getRef().child("read").setValue(true)
                                        .addOnSuccessListener(aVoid -> {
                                            tvStatus.setText("Visit Accepted");
                                            btnMarkRead.setVisibility(View.GONE);
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(getContext(),
                                                        "Failed to mark as read: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show()
                                        )
                        );
                    } else {
                        tvStatus.setText("Visit Accepted");
                        btnMarkRead.setVisibility(View.GONE);
                    }

                    bookingsListContainer.addView(row);
                }

                // Sort notifications by date descending
                Collections.sort(notifications, (n1, n2) -> {
                    try {
                        Date d1 = sdf.parse(n1.getBookingDate());
                        Date d2 = sdf.parse(n2.getBookingDate());
                        if (d1 != null && d2 != null) return d2.compareTo(d1);
                    } catch (ParseException ignored) {}
                    return 0;
                });

                updateBarChart(monthlyCounts);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    Toast.makeText(getContext(),
                            "Failed to fetch notifications: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        };

        notifRef.addValueEventListener(notifListener);
    }

    private void updateBarChart(Map<Integer, Integer> monthlyCounts) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        String[] monthLabels = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

        for (int i = 0; i < 12; i++) {
            entries.add(new BarEntry(i, monthlyCounts.getOrDefault(i, 0)));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Bookings per Month");
        int textColor = requireContext().getColor(R.color.textPrimary);
        dataSet.setColor(Color.parseColor("#3F51B5"));
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notifRef != null && notifListener != null) {
            notifRef.removeEventListener(notifListener);
            notifListener = null;
        }
    }
}
