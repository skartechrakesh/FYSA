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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class SpaceDetailsFragment extends Fragment {

    private TextView tvName, tvType, tvOwnerName, tvAddress, tvContact, tvDistance;
    private Button btnDirections, btnBook;

    private Space space;
    private String spaceId, spaceName, spaceType, ownerId;
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
        tvType = view.findViewById(R.id.tvType);
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
        spaceType = space.getType();
        ownerId = space.getOwnerId();
        latitude = space.getLatitude();
        longitude = space.getLongitude();

        tvName.setText("Space Name: " + spaceName);
        tvType.setText("Type: " + spaceType);
        tvOwnerName.setText("Owner: " + space.getOwnerName());
        tvAddress.setText("Address: " + space.getAddress());
        tvContact.setText("Contact: " + space.getContact());

        int distance = space.getDistanceMeters();
        tvDistance.setText(distance > 0
                ? (distance >= 1000
                ? String.format(Locale.getDefault(), "Distance: %.1f km", distance / 1000.0)
                : "Distance: " + distance + " m")
                : "Distance: Unknown");
    }

    private void setupDirections() {
        btnDirections.setOnClickListener(v -> {
            if (latitude == 0 && longitude == 0) {
                Toast.makeText(requireContext(), "Location unavailable", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(requireContext(), "You must be logged in to book a visit", Toast.LENGTH_SHORT).show();
                return;
            }

            final String userId = auth.getCurrentUser().getUid();

            DatabaseReference ownerNotificationsRef = FirebaseDatabase.getInstance(DB_URL)
                    .getReference("notifications")
                    .child(ownerId);

            // Check if this user already booked this space
            ownerNotificationsRef.orderByChild("userId").equalTo(userId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean alreadyBooked = false;
                            for (DataSnapshot notificationSnap : snapshot.getChildren()) {
                                NotificationModel notification = notificationSnap.getValue(NotificationModel.class);
                                if (notification != null && spaceId.equals(notification.getSpaceId())) {
                                    alreadyBooked = true;
                                    break;
                                }
                            }

                            if (alreadyBooked) {
                                Toast.makeText(requireContext(),
                                        "You have already booked this space.", Toast.LENGTH_SHORT).show();
                            } else {
                                createNewBooking(userId);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(requireContext(),
                                    "Failed to check bookings: " + error.getMessage(),
                                    Toast.LENGTH_LONG).show();
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

            DatabaseReference notifRef = FirebaseDatabase.getInstance(DB_URL)
                    .getReference("notifications")
                    .child(ownerId)
                    .push();

            NotificationModel notification = new NotificationModel(
                    userId,
                    usernameFromDb,
                    spaceId,
                    spaceName,
                    date,
                    false,
                    createdAt,
                    "Booked Visit"
            );
            notification.setSpaceType(spaceType);

            notifRef.setValue(notification)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(requireContext(),
                                    "Visit booked successfully!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(),
                                    "Booking failed: " + Objects.requireNonNull(task.getException()).getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });

        }).addOnFailureListener(e -> Toast.makeText(requireContext(),
                "Failed to fetch username: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
