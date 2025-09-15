package com.skar.fysa;

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
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.material.snackbar.Snackbar;
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
    private LinearLayout nativeAdContainer;
    private BarChart barChart;
    private DatabaseReference notifRef;
    private FirebaseUser currentUser;
    private ValueEventListener notifListener;

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private NativeAd loadedNativeAd;
    private final ArrayList<DataSnapshot> allNotifications = new ArrayList<>();
    private int loadedCount = 0;
    private static final int BATCH_SIZE = 5;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_bookings_overview, container, false);

        bookingsListContainer = view.findViewById(R.id.bookingsListContainer);
        barChart = view.findViewById(R.id.bookingsBarChart);
        nativeAdContainer = view.findViewById(R.id.nativeAdContainer);

        loadNativeAd();

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            notifRef = FirebaseDatabase.getInstance(DB_URL)
                    .getReference("notifications")
                    .child(currentUser.getUid());
            loadNotifications();
        } else {
            clearUI();
            showSnackbar("Please login to view bookings");
        }

        return view;
    }

    private void loadNativeAd() {
        AdLoader adLoader = new AdLoader.Builder(requireContext(),
                "ca-app-pub-3940256099942544/2247696110") // Test Ad Unit ID
                .forNativeAd(nativeAd -> {
                    if (getContext() == null) return;

                    if (loadedNativeAd != null) loadedNativeAd.destroy();
                    loadedNativeAd = nativeAd;

                    NativeAdView adView = (NativeAdView) LayoutInflater.from(getContext())
                            .inflate(R.layout.item_native_ad, nativeAdContainer, false);

                    TextView headline = adView.findViewById(R.id.ad_headline);
                    TextView body = adView.findViewById(R.id.ad_body);
                    Button cta = adView.findViewById(R.id.ad_call_to_action);

                    adView.setHeadlineView(headline);
                    adView.setBodyView(body);
                    adView.setCallToActionView(cta);

                    headline.setText(nativeAd.getHeadline());
                    if (nativeAd.getBody() != null) {
                        body.setText(nativeAd.getBody());
                        body.setVisibility(View.VISIBLE);
                    } else body.setVisibility(View.GONE);

                    if (nativeAd.getCallToAction() != null) {
                        cta.setText(nativeAd.getCallToAction());
                        cta.setVisibility(View.VISIBLE);
                    } else cta.setVisibility(View.GONE);

                    adView.setNativeAd(nativeAd);
                    nativeAdContainer.removeAllViews();
                    nativeAdContainer.addView(adView);
                }).build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void loadNotifications() {
        if (notifRef == null) return;

        notifListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allNotifications.clear();
                Map<Integer, Integer> monthlyCounts = new HashMap<>();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    allNotifications.add(ds);

                    String dateStr = ds.child("bookingDate").getValue(String.class);
                    if (dateStr != null) {
                        try {
                            Date date = sdf.parse(dateStr);
                            if (date != null) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(date);
                                int month = cal.get(Calendar.MONTH);
                                monthlyCounts.put(month, monthlyCounts.getOrDefault(month, 0) + 1);
                            }
                        } catch (ParseException ignored) {}
                    }
                }

                // Sort latest first
                Collections.sort(allNotifications, (n1, n2) -> {
                    try {
                        Date d1 = sdf.parse(n1.child("bookingDate").getValue(String.class));
                        Date d2 = sdf.parse(n2.child("bookingDate").getValue(String.class));
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
                showSnackbar("Failed to fetch notifications: " + error.getMessage());
            }
        };

        notifRef.addValueEventListener(notifListener);
    }

    private void loadNextBatch(LayoutInflater inflater) {
        int end = Math.min(loadedCount + BATCH_SIZE, allNotifications.size());

        for (int i = loadedCount; i < end; i++) {
            addBookingRow(inflater, allNotifications.get(i));
        }

        loadedCount = end;

        if (loadedCount < allNotifications.size()) addLoadMoreLink(inflater);
    }

    private void addBookingRow(LayoutInflater inflater, DataSnapshot notifSnap) {
        View row = inflater.inflate(R.layout.item_bookings_overview_list, bookingsListContainer, false);

        TextView tvName = row.findViewById(R.id.tvBookingName);
        TextView tvUsername = row.findViewById(R.id.tvBookingUsername);
        TextView tvDate = row.findViewById(R.id.tvBookingDate);
        TextView tvType = row.findViewById(R.id.tvBookingType);
        TextView tvStatus = row.findViewById(R.id.tvBookingStatus);
        Button btnMarkRead = row.findViewById(R.id.btnAction);

        tvName.setText(notifSnap.child("spaceName").getValue(String.class));
        tvUsername.setText("Booked by: " + notifSnap.child("username").getValue(String.class));
        tvDate.setText(notifSnap.child("bookingDate").getValue(String.class));
        tvType.setText(notifSnap.child("spaceType").getValue(String.class));

        Boolean read = notifSnap.child("read").getValue(Boolean.class);
        if (read == null) read = false;

        if (!read) {
            tvStatus.setText("Visit Booked");
            btnMarkRead.setVisibility(View.VISIBLE);
            btnMarkRead.setText("Mark as Read");
            btnMarkRead.setOnClickListener(v ->
                    notifSnap.getRef().child("read").setValue(true)
                            .addOnSuccessListener(aVoid -> {
                                tvStatus.setText("Visit Accepted");
                                btnMarkRead.setVisibility(View.GONE);
                            }));
        } else {
            tvStatus.setText("Visit Accepted");
            btnMarkRead.setVisibility(View.GONE);
        }

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
        if (notifRef != null && notifListener != null) {
            notifRef.removeEventListener(notifListener);
            notifListener = null;
        }
        if (loadedNativeAd != null) loadedNativeAd.destroy();
    }
}
