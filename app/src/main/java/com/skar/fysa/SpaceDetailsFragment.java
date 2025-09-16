package com.skar.fysa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class SpaceDetailsFragment extends Fragment {

    private TextView tvName, tvOwnerName, tvAddress, tvContact, tvDistance;
    private Button btnDirections, btnBook;

    private Space space;
    private String spaceId, spaceName, ownerId;
    private double latitude, longitude;

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_space_details, container, false);

        tvName = view.findViewById(R.id.tvName);
        tvOwnerName = view.findViewById(R.id.tvOwnerName);
        tvAddress = view.findViewById(R.id.tvAddress);
        tvContact = view.findViewById(R.id.tvContact);
        tvDistance = view.findViewById(R.id.tvDistance);
        btnDirections = view.findViewById(R.id.btnDirections);
        btnBook = view.findViewById(R.id.btnBook);

        if (getArguments() != null) {
            space = (Space) getArguments().getSerializable("space");
            if (space != null) loadSpace();
        }

        setupDirections();
        setupBooking();

        return view;
    }

    @SuppressLint("SetTextI18n")
    private void loadSpace() {
        spaceId = space.getId();
        spaceName = space.getName();
        ownerId = space.getOwnerId();
        latitude = space.getLatitude();
        longitude = space.getLongitude();

        tvName.setText("Space Name: " + spaceName);
        tvOwnerName.setText("Owner: " + space.getOwnerName());
        tvAddress.setText("Address: " + space.getAddress());
        tvContact.setText("Contact: " + space.getContact());

        Integer distance = space.getDistanceMeters();
        if (distance != null && distance > 0) {
            tvDistance.setText(distance >= 1000
                    ? String.format(Locale.getDefault(), "Distance: %.1f km", distance / 1000.0)
                    : "Distance: " + distance + " m");
        } else {
            tvDistance.setText("Distance: Unknown");
        }
    }

    private void setupDirections() {
        btnDirections.setOnClickListener(v -> {
            if (latitude == 0 && longitude == 0) {
                Snackbar.make(requireView(), "Location unavailable", Snackbar.LENGTH_SHORT).show();
                return;
            }
            String uri = "https://www.google.com/maps/dir/?api=1&destination=" + latitude + "," + longitude;
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        });
    }

    private void setupBooking() {
        btnBook.setOnClickListener(v -> {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Snackbar.make(requireView(), "You must be logged in to book a visit", Snackbar.LENGTH_SHORT).show();
                return;
            }

            final String userId = auth.getCurrentUser().getUid();

            DatabaseReference bookingsRef = FirebaseDatabase.getInstance(DB_URL)
                    .getReference("spaces")
                    .child(spaceId)
                    .child("bookings");

            // Check if user already booked this space
            bookingsRef.orderByChild("userId").equalTo(userId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean alreadyBooked = false;
                            for (DataSnapshot bookingSnap : snapshot.getChildren()) {
                                BookingsDataModel booking = bookingSnap.getValue(BookingsDataModel.class);
                                if (booking != null && spaceId.equals(booking.getSpaceId())) {
                                    alreadyBooked = true;
                                    break;
                                }
                            }

                            if (alreadyBooked) {
                                Snackbar.make(requireView(),
                                        "You have already booked this space.", Snackbar.LENGTH_SHORT).show();
                            } else {
                                createNewBooking(userId);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Snackbar.make(requireView(),
                                    "Failed to check bookings: " + error.getMessage(),
                                    Snackbar.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void createNewBooking(String userId) {
        final String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        final long createdAt = System.currentTimeMillis();

        DatabaseReference userRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(userId)
                .child("username");

        userRef.get().addOnSuccessListener(userSnapshot -> {
            final String usernameFromDb = userSnapshot.exists()
                    ? userSnapshot.getValue(String.class)
                    : "User";

            // Add spaceCategory here
            BookingsDataModel booking = new BookingsDataModel(
                    userId,
                    usernameFromDb,
                    spaceId,
                    spaceName,
                    space.getCategory(),  // ✅ store the category
                    date,
                    false,
                    createdAt,
                    "Booked Visit"
            );

            DatabaseReference bookingDataRef = FirebaseDatabase.getInstance(DB_URL)
                    .getReference("spaces")
                    .child(spaceId)
                    .child("bookings")
                    .push();

            String bookingId = bookingDataRef.getKey(); // unique booking id

            bookingDataRef.setValue(booking)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Save booking reference in user's node
                            DatabaseReference userBookingRef = FirebaseDatabase.getInstance(DB_URL)
                                    .getReference("users")
                                    .child(userId)
                                    .child("booking_reference")
                                    .child(bookingId);

                            userBookingRef.setValue(true)
                                    .addOnCompleteListener(refTask -> {
                                        if (refTask.isSuccessful()) {
                                            Snackbar.make(requireView(),
                                                    "Visit booked successfully!",
                                                    Snackbar.LENGTH_SHORT).show();
                                        } else {
                                            Snackbar.make(requireView(),
                                                    "Booking saved but failed to update user: "
                                                            + Objects.requireNonNull(refTask.getException()).getMessage(),
                                                    Snackbar.LENGTH_LONG).show();
                                        }
                                    });

                        } else {
                            Snackbar.make(requireView(),
                                    "Booking failed: " + Objects.requireNonNull(task.getException()).getMessage(),
                                    Snackbar.LENGTH_LONG).show();
                        }
                    });

        }).addOnFailureListener(e -> Snackbar.make(requireView(),
                "Failed to fetch username: " + e.getMessage(), Snackbar.LENGTH_SHORT).show());
    }
}
