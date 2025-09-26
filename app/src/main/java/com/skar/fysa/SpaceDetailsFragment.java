package com.skar.fysa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SpaceDetailsFragment extends Fragment {

    private TextView tvName, tvOwnerName, tvAddress, tvContact, tvDistance, tvCategory, tvSpaceFeatures;
    private Button btnDirections, btnBook;

    private Space space;
    private String spaceId, spaceName, ownerId;
    private double latitude, longitude;
    private boolean fromMyBookings = false;

    private FirebaseFirestore firestore;
    private FusedLocationProviderClient fusedLocationClient;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    new ActivityResultCallback<Boolean>() {
                        @Override
                        public void onActivityResult(Boolean isGranted) {
                            if (isGranted) {
                                calculateDistance();
                            } else if (tvDistance != null) {
                                tvDistance.setText("Distance: Unknown");
                            }
                        }
                    });

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        if (getArguments() != null) {
            space = getArguments().getParcelable("space");
            spaceId = getArguments().getString("spaceId");
            fromMyBookings = getArguments().getBoolean("fromMyBookings", false);
        }

        if (fromMyBookings && btnBook != null) {
            btnBook.setVisibility(View.GONE);
        }

        if (space != null) {
            loadSpace();
        } else if (spaceId != null) {
            fetchSpaceFromFirestore(spaceId);
        } else {
            Snackbar.make(view, "No space data available", Snackbar.LENGTH_SHORT).show();
        }

        setupDirections();
        setupBooking();

        return view;
    }

    private void fetchSpaceFromFirestore(String spaceId) {
        firestore.collection("spaces").document(spaceId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        space = document.toObject(Space.class);
                        if (space != null) loadSpace();
                    } else {
                        Snackbar.make(requireView(), "Space not found", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Snackbar.make(requireView(), "Failed to load space: " + e.getMessage(), Snackbar.LENGTH_LONG).show()
                );
    }

    @SuppressLint("SetTextI18n")
    private void loadSpace() {
        if (space == null) return;

        spaceId = space.getId();
        spaceName = space.getName();
        ownerId = space.getOwnerId();
        latitude = space.getLatitude();
        longitude = space.getLongitude();

        tvName.setText("Space Name: " + spaceName);
        tvOwnerName.setText("Owner: " + space.getOwnerName());
        tvAddress.setText("Address: " + space.getAddress());
        tvCategory.setText("Category: " + space.getCategory());

        // Load features
        String features = space.getSpaceFeatures();
        if (features != null && !features.isEmpty()) {
            String[] featureArray = features.split(",");
            StringBuilder formattedFeatures = new StringBuilder("Features:");
            for (String f : featureArray) {
                formattedFeatures.append("\n\t• ").append(f.trim());
            }
            tvSpaceFeatures.setText(formattedFeatures.toString());
        } else {
            tvSpaceFeatures.setText("Features: N/A");
        }

        // Fetch latest owner contact from users collection
        if (ownerId != null) {
            firestore.collection("users").document(ownerId)
                    .get()
                    .addOnSuccessListener(userDoc -> {
                        String contact = userDoc.getString("contact");
                        tvContact.setText("Contact: " + (contact != null ? contact : "N/A"));
                    })
                    .addOnFailureListener(e -> tvContact.setText("Contact: N/A"));
        } else {
            tvContact.setText("Contact: N/A");
        }

        calculateDistance();
    }

    @SuppressLint("MissingPermission")
    private void calculateDistance() {
        if (latitude == 0.0 && longitude == 0.0) {
            tvDistance.setText("Distance: Unknown");
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        float[] results = new float[1];
                        Location.distanceBetween(
                                location.getLatitude(),
                                location.getLongitude(),
                                latitude,
                                longitude,
                                results
                        );
                        float distanceMeters = results[0];
                        if (distanceMeters >= 1000) {
                            tvDistance.setText(String.format(Locale.getDefault(), "Distance: %.1f km", distanceMeters / 1000.0));
                        } else {
                            tvDistance.setText(String.format(Locale.getDefault(), "Distance: %.0f m", distanceMeters));
                        }
                    } else {
                        tvDistance.setText("Distance: Unknown");
                    }
                })
                .addOnFailureListener(e -> tvDistance.setText("Distance: Unknown"));
    }

    private void setupDirections() {
        if (btnDirections == null) return;

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
                Snackbar.make(requireView(), "Google Maps app not installed.", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void setupBooking() {
        if (btnBook == null) return;

        btnBook.setOnClickListener(v -> {
            if (fromMyBookings) return;

            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Snackbar.make(requireView(), "You must be logged in to book a visit", Snackbar.LENGTH_SHORT).show();
                return;
            }

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            checkAlreadyBooked(userId);
        });
    }

    private void checkAlreadyBooked(String userId) {
        final String finalSpaceId = spaceId;
        firestore.collection("bookings")
                .whereEqualTo("bookedBy", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    boolean alreadyBooked = false;
                    for (var doc : querySnapshot.getDocuments()) {
                        String bookedSpaceId = doc.contains("spaceId") ? doc.getString("spaceId") : null;
                        if (finalSpaceId != null && finalSpaceId.equals(bookedSpaceId)) {
                            alreadyBooked = true;
                            break;
                        }
                    }

                    if (alreadyBooked) {
                        Snackbar.make(requireView(), "You have already booked this space visit.", Snackbar.LENGTH_SHORT).show();
                    } else {
                        createBooking(userId, finalSpaceId);
                    }
                })
                .addOnFailureListener(e ->
                        Snackbar.make(requireView(), "Failed to check existing bookings: " + e.getMessage(), Snackbar.LENGTH_LONG).show()
                );
    }

    private void createBooking(String userId, String finalSpaceId) {
        final long createdAt = System.currentTimeMillis();
        final String bookingId = userId + "_" + createdAt;

        firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    String userContact = userDoc.getString("contact") != null ? userDoc.getString("contact") : "";
                    String bookedByUserName = userDoc.getString("username") != null ? userDoc.getString("username") : "";

                    final String finalUserContact = userContact;
                    final String finalUserName = bookedByUserName;

                    // Update user booking reference
                    firestore.collection("references").document("booking_ref")
                            .get().addOnSuccessListener(userDocRef -> {
                                Map<String, Object> userData = userDocRef.exists() ? userDocRef.getData() : new HashMap<>();
                                if (userData == null) userData = new HashMap<>();

                                List<String> userBookingList = new ArrayList<>();
                                Object existingUserBookings = userData.get(userId);
                                if (existingUserBookings instanceof List<?>) {
                                    for (Object obj : (List<?>) existingUserBookings) userBookingList.add(obj.toString());
                                }

                                userBookingList.add(bookingId);
                                Map<String, Object> updateUserMap = new HashMap<>();
                                updateUserMap.put(userId, userBookingList);

                                firestore.collection("references").document("booking_ref")
                                        .set(updateUserMap, SetOptions.merge())
                                        .addOnSuccessListener(aVoid -> {
                                            // Update space booking reference
                                            firestore.collection("references").document("space_booking_ref")
                                                    .get().addOnSuccessListener(spaceDoc -> {
                                                        Map<String, Object> spaceData = spaceDoc.exists() ? spaceDoc.getData() : new HashMap<>();
                                                        if (spaceData == null) spaceData = new HashMap<>();

                                                        List<String> spaceBookingList = new ArrayList<>();
                                                        Object existingSpaceBookings = spaceData.get(finalSpaceId);
                                                        if (existingSpaceBookings instanceof List<?>) {
                                                            for (Object obj : (List<?>) existingSpaceBookings) spaceBookingList.add(obj.toString());
                                                        }

                                                        spaceBookingList.add(bookingId);
                                                        Map<String, Object> updateSpaceMap = new HashMap<>();
                                                        updateSpaceMap.put(finalSpaceId, spaceBookingList);

                                                        firestore.collection("references").document("space_booking_ref")
                                                                .set(updateSpaceMap, SetOptions.merge())
                                                                .addOnSuccessListener(aVoid2 -> {
                                                                    Map<String, Object> bookingRootData = new HashMap<>();
                                                                    bookingRootData.put("createdAt", createdAt);
                                                                    bookingRootData.put("bookedBy", userId);
                                                                    bookingRootData.put("spaceId", finalSpaceId);
                                                                    bookingRootData.put("userContact", finalUserContact);
                                                                    bookingRootData.put("bookedByUserName", finalUserName);

                                                                    firestore.collection("bookings").document(bookingId)
                                                                            .set(bookingRootData)
                                                                            .addOnSuccessListener(b -> Snackbar.make(requireView(),
                                                                                    "Visit booked successfully!", Snackbar.LENGTH_SHORT).show())
                                                                            .addOnFailureListener(e -> Snackbar.make(requireView(),
                                                                                    "Booking saved in references, but failed in bookings collection: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                                                                }).addOnFailureListener(e -> Snackbar.make(requireView(),
                                                                        "Booking failed in space_booking_ref: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                                                    }).addOnFailureListener(e -> Snackbar.make(requireView(),
                                                            "Booking failed: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                                        })
                                        .addOnFailureListener(e -> Snackbar.make(requireView(),
                                                "Booking failed in booking_ref: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                            }).addOnFailureListener(e -> Snackbar.make(requireView(),
                                    "Booking failed: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Snackbar.make(requireView(),
                        "Failed to fetch user data: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
    }
}
