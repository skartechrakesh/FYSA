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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    public enum Mode { USER, OWNER }

    public interface OnCancelClickListener {
        void onCancelClick(Booking booking);
    }

    private final List<Booking> bookings;
    private final Mode mode;
    private final OnCancelClickListener cancelListener;
    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    public BookingAdapter(List<Booking> bookings, Mode mode, OnCancelClickListener cancelListener) {
        this.bookings = bookings;
        this.mode = mode;
        this.cancelListener = cancelListener;
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_booking, parent, false);
        return new BookingViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        Booking booking = bookings.get(position);

        holder.tvBookingName.setText(booking.getSpaceName());
        holder.tvBookingType.setText("Type: " + booking.getSpaceType());
        holder.tvBookingDate.setText("Booking Date: " + booking.getBookingDate());

        // Format createdAt timestamp (if available)
        if (booking.getCreatedAt() > 0) {
            String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(booking.getCreatedAt()));
            holder.tvCreatedAt.setText("Created At: " + formattedDate);
            holder.tvCreatedAt.setVisibility(View.VISIBLE);
        } else {
            holder.tvCreatedAt.setVisibility(View.GONE);
        }

        // Show status
        holder.tvBookingStatus.setText("Status: " + booking.getStatus());

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (mode == Mode.OWNER) {
            // Owner: "Mark as Read"
            if ("Booked Visit".equals(booking.getStatus())
                    && booking.getOwnerId() != null
                    && booking.getOwnerId().equals(currentUserId)) {

                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("Mark as Read");

                holder.btnAction.setOnClickListener(v -> {
                    // Update the notification status in the database
                    DatabaseReference statusRef = FirebaseDatabase.getInstance(DB_URL)
                            .getReference("notifications")
                            .child(booking.getOwnerId())
                            .child(booking.getBookingId());

                    statusRef.child("status").setValue("Visit Accepted")
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(holder.itemView.getContext(),
                                        "Visit accepted", Toast.LENGTH_SHORT).show();
                                // Manually update the local list item for immediate UI feedback
                                booking.setStatus("Visit Accepted");
                                notifyItemChanged(position);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(holder.itemView.getContext(),
                                            "Failed to update status: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                });

            } else {
                holder.btnAction.setVisibility(View.GONE);
            }

        } else if (mode == Mode.USER) {
            // User: Allow cancel
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnAction.setText("Cancel Visit");

            holder.btnAction.setOnClickListener(v -> {
                if (cancelListener != null) {
                    cancelListener.onCancelClick(booking); // Delegate cancel logic to Fragment
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

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
}