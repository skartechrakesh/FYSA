package com.skar.fysa;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BookingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public enum Mode { USER, OWNER }

    public interface OnCancelClickListener {
        void onCancelClick(Booking booking);
    }

    private static final int VIEW_TYPE_BOOKING = 0;
    private static final int VIEW_TYPE_AD = 1;

    private final List<Object> items; // Booking or NativeAd
    private final Mode mode;
    private final OnCancelClickListener cancelListener;

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    public BookingAdapter(List<Object> items, Mode mode, OnCancelClickListener cancelListener) {
        this.items = items;
        this.mode = mode;
        this.cancelListener = cancelListener;
    }

    @Override
    public int getItemViewType(int position) {
        return (items.get(position) instanceof Booking) ? VIEW_TYPE_BOOKING : VIEW_TYPE_AD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_BOOKING) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_booking, parent, false);
            return new BookingViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_native_ad, parent, false);
            return new AdViewHolder(view);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof BookingViewHolder) {
            Booking booking = (Booking) items.get(position);
            BookingViewHolder bookingHolder = (BookingViewHolder) holder;

            // Basic info
            bookingHolder.tvBookingName.setText(booking.getSpaceName());
            bookingHolder.tvBookingType.setText("Type: " + booking.getSpaceType());
            bookingHolder.tvBookingDate.setText("Booking Date: " + booking.getBookingDate());

            // Created at
            if (booking.getCreatedAt() > 0) {
                String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(new Date(booking.getCreatedAt()));
                bookingHolder.tvCreatedAt.setText("Created At: " + formattedDate);
                bookingHolder.tvCreatedAt.setVisibility(View.VISIBLE);
            } else {
                bookingHolder.tvCreatedAt.setVisibility(View.GONE);
            }

            // Status
            bookingHolder.tvBookingStatus.setText("Status: " + booking.getStatus());

            String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;

            // OWNER MODE
            if (mode == Mode.OWNER) {
                if ("Booked Visit".equals(booking.getStatus())
                        && booking.getOwnerId() != null
                        && booking.getOwnerId().equals(currentUserId)) {

                    bookingHolder.btnAction.setVisibility(View.VISIBLE);
                    bookingHolder.btnAction.setText("Mark as Read");

                    bookingHolder.btnAction.setOnClickListener(v -> {
                        DatabaseReference statusRef = FirebaseDatabase.getInstance(DB_URL)
                                .getReference("notifications")
                                .child(booking.getOwnerId())
                                .child(booking.getBookingId());

                        statusRef.child("status").setValue("Visit Accepted")
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(holder.itemView.getContext(),
                                            "Visit accepted", Toast.LENGTH_SHORT).show();
                                    booking.setStatus("Visit Accepted");
                                    notifyItemChanged(position);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(holder.itemView.getContext(),
                                                "Failed to update status: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show());
                    });

                } else {
                    bookingHolder.btnAction.setVisibility(View.GONE);
                }

            }
            // USER MODE
            else if (mode == Mode.USER) {
                bookingHolder.btnAction.setVisibility(View.VISIBLE);
                bookingHolder.btnAction.setText("Cancel Visit");

                bookingHolder.btnAction.setOnClickListener(v -> {
                    if (cancelListener != null) {
                        cancelListener.onCancelClick(booking);
                    }
                });
            }

        } else if (holder instanceof AdViewHolder) {
            NativeAd nativeAd = (NativeAd) items.get(position);
            AdViewHolder adHolder = (AdViewHolder) holder;
            populateNativeAdView(nativeAd, adHolder.nativeAdView);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // --- Booking ViewHolder ---
    static class BookingViewHolder extends RecyclerView.ViewHolder {
        TextView tvBookingName, tvBookingType, tvBookingDate, tvBookingStatus, tvCreatedAt;
        Button btnAction;

        BookingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBookingName = itemView.findViewById(R.id.tvBookingName);
            tvBookingType = itemView.findViewById(R.id.tvBookingType);
            tvBookingDate = itemView.findViewById(R.id.tvBookingDate);
            tvBookingStatus = itemView.findViewById(R.id.tvBookingStatus);
            tvCreatedAt = itemView.findViewById(R.id.tvCreatedAt);
            btnAction = itemView.findViewById(R.id.btnAction);
        }
    }

    // --- Ad ViewHolder ---
    static class AdViewHolder extends RecyclerView.ViewHolder {
        NativeAdView nativeAdView;

        AdViewHolder(@NonNull View itemView) {
            super(itemView);
            nativeAdView = (NativeAdView) itemView;
        }
    }

    // --- Helper: Populate Native Ad ---
    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        TextView headlineView = adView.findViewById(R.id.ad_headline);
        TextView bodyView = adView.findViewById(R.id.ad_body);
        Button ctaView = adView.findViewById(R.id.ad_call_to_action);

        adView.setHeadlineView(headlineView);
        adView.setBodyView(bodyView);
        adView.setCallToActionView(ctaView);

        // Fill data
        headlineView.setText(nativeAd.getHeadline());

        if (nativeAd.getBody() != null) {
            bodyView.setText(nativeAd.getBody());
            bodyView.setVisibility(View.VISIBLE);
        } else {
            bodyView.setVisibility(View.GONE);
        }

        if (nativeAd.getCallToAction() != null) {
            ctaView.setText(nativeAd.getCallToAction());
            ctaView.setVisibility(View.VISIBLE);
        } else {
            ctaView.setVisibility(View.GONE);
        }

        adView.setNativeAd(nativeAd);
    }
}
