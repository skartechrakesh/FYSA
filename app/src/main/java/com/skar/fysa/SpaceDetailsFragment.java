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

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SpaceDetailsFragment extends Fragment {

    private TextView tvName, tvOwnerName, tvAddress, tvContact, tvDistance, tvCategory, tvSpaceFeatures;
    private Button btnDirections, btnBook;
    private Space space;
    private String spaceId, spaceName, ownerId;
    private double latitude, longitude;

    private FirebaseFirestore firestore;

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
        tvCategory = view.findViewById(R.id.tvCategory);
        tvSpaceFeatures = view.findViewById(R.id.tvSpaceFeatures);

        btnDirections = view.findViewById(R.id.btnDirections);
        btnBook = view.findViewById(R.id.btnBook);

        firestore = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            space = getArguments().getParcelable("space");
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
        tvCategory.setText("Category: " + space.getCategory());

        // --- Display space features ---
        // --- Display space features ---
        String features = space.getSpaceFeatures();
        if (features != null && !features.isEmpty()) {
            // Split by comma, trim each feature, then join with newline + tab
            String[] featureArray = features.split(",");
            StringBuilder formattedFeatures = new StringBuilder();
            for (String f : featureArray) {
                formattedFeatures.append("\n\t").append(f.trim());
            }
            tvSpaceFeatures.setText("Features:" + formattedFeatures.toString());
        } else {
            tvSpaceFeatures.setText("Features: N/A");
        }


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
            if (latitude == 0.0 && longitude == 0.0) {
                Snackbar.make(requireView(), "Location unavailable", Snackbar.LENGTH_SHORT).show();
                return;
            }
            String uri = "http://maps.google.com/maps?q=loc:" + latitude + "," + longitude;
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            mapIntent.setPackage("com.google.android.apps.maps");

            if (mapIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                Toast.makeText(requireContext(), "Google Maps app not installed.", Toast.LENGTH_SHORT).show();
            }
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
            CollectionReference bookingsRef = firestore.collection("spaces")
                    .document(spaceId)
                    .collection("bookings");

            // Check if user already booked
            bookingsRef.whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        boolean alreadyBooked = false;
                        for (DocumentSnapshot snap : queryDocumentSnapshots.getDocuments()) {
                            BookingsDataModel booking = snap.toObject(BookingsDataModel.class);
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
                    })
                    .addOnFailureListener(e -> Snackbar.make(requireView(),
                            "Failed to check bookings: " + e.getMessage(),
                            Snackbar.LENGTH_LONG).show());
        });
    }

    private void createNewBooking(String userId) {
        final String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        final long createdAt = System.currentTimeMillis();

        firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(userSnapshot -> {
                    final String usernameFromDb = userSnapshot.contains("username")
                            ? userSnapshot.getString("username")
                            : "User";
                    final String userContactFromDb = userSnapshot.contains("contact")
                            ? userSnapshot.getString("contact")
                            : "No Contact Provided";

                    BookingsDataModel booking = new BookingsDataModel(
                            userId,
                            usernameFromDb,
                            userContactFromDb,
                            spaceId,
                            spaceName,
                            space.getCategory(),
                            date,
                            false,
                            createdAt,
                            "Booked Visit",
                            latitude,
                            longitude
                    );

                    // Add booking to space's bookings collection
                    firestore.collection("spaces")
                            .document(spaceId)
                            .collection("bookings")
                            .add(booking)
                            .addOnSuccessListener(docRef -> {
                                String bookingId = docRef.getId();

                                // --- ADD BOOKING REFERENCE AS MAP IN USER DOCUMENT ---
                                firestore.collection("users")
                                        .document(userId)
                                        .update("booking_reference." + bookingId, true)
                                        .addOnSuccessListener(aVoid -> Snackbar.make(requireView(),
                                                "Visit booked successfully!",
                                                Snackbar.LENGTH_SHORT).show())
                                        .addOnFailureListener(e -> Snackbar.make(requireView(),
                                                "Booking saved but failed to update user reference: " + e.getMessage(),
                                                Snackbar.LENGTH_LONG).show());
                            })
                            .addOnFailureListener(e -> Snackbar.make(requireView(),
                                    "Booking failed: " + e.getMessage(),
                                    Snackbar.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Snackbar.make(requireView(),
                        "Failed to fetch user data: " + e.getMessage(), Snackbar.LENGTH_SHORT).show());
    }
}
