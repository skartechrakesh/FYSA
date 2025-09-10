package com.skar.fysa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.Objects;

public class SpaceFormFragment extends Fragment {

    private TextInputEditText inputName, inputOwnerName, inputContact, inputAddress;
    private Button btnSubmit;
    private MapView mapView;
    private TextView txtCategory;
    private TextInputLayout nameLayout, ownerNameLayout, contactLayout, addressLayout;
    private FloatingActionButton btnMyLocation;
    private ImageView crosshair;

    private DatabaseReference userSpacesRef, globalSpacesRef;
    private String category;

    private InterstitialAd mInterstitialAd; // Interstitial Ad
    private final Handler adHandler = new Handler();

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) moveToCurrentLocation();
                else Toast.makeText(getContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_space_form, container, false);

        // OSMDroid setup
        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid_prefs", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        // Get category argument
        category = getArguments() != null ? getArguments().getString("CATEGORY", "Unknown") : "Unknown";

        // Firebase references
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "You must be logged in", Toast.LENGTH_SHORT).show();
            return view;
        }

        String uid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        FirebaseDatabase db = FirebaseDatabase.getInstance(
                "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/"
        );
        userSpacesRef = db.getReference("users").child(uid).child("space_details");
        globalSpacesRef = db.getReference("spaces");

        bindViews(view);
        setListeners();
        setupBackHandling();
        initMap();
        requestLocationPermission();

        txtCategory.setText("Category: " + category);
        FysaApp.setupTextInputStrokes(requireContext(), nameLayout, ownerNameLayout, contactLayout, addressLayout);

        // Load interstitial ad
        loadInterstitialAd();

        return view;
    }

    private void bindViews(View view) {
        inputName = view.findViewById(R.id.inputName);
        inputOwnerName = view.findViewById(R.id.inputOwnerName);
        inputContact = view.findViewById(R.id.inputContact);
        inputAddress = view.findViewById(R.id.inputAddress);
        btnSubmit = view.findViewById(R.id.btnSubmit);
        mapView = view.findViewById(R.id.mapView);
        txtCategory = view.findViewById(R.id.txtCategory);
        btnMyLocation = view.findViewById(R.id.btnMyLocation);
        crosshair = view.findViewById(R.id.crosshair);
        nameLayout = view.findViewById(R.id.nameLayout);
        ownerNameLayout = view.findViewById(R.id.ownerNameLayout);
        contactLayout = view.findViewById(R.id.contactLayout);
        addressLayout = view.findViewById(R.id.addressLayout);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setListeners() {
        btnMyLocation.setOnClickListener(v -> requestLocationPermission());

        btnSubmit.setOnClickListener(v -> {
            // Show interstitial after 1000 ms, then submit form
            adHandler.postDelayed(() -> {
                if (mInterstitialAd != null) {
                    mInterstitialAd.show(requireActivity());
                    mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            submitForm();
                            loadInterstitialAd(); // Load next ad
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                            submitForm(); // Submit even if ad fails
                        }
                    });
                } else {
                    submitForm(); // If ad not loaded, submit immediately
                }
            }, 1000);
        });

        // Smooth map dragging inside scrollable parent
        mapView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
                v.performClick();
            }
            return false;
        });

        mapView.setOnClickListener(v -> {});
    }

    private void setupBackHandling() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                            getParentFragmentManager().popBackStack();
                        } else {
                            setEnabled(false);
                            requireActivity().onBackPressed();
                        }
                    }
                });
    }

    private void initMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(19.0);
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentLocation();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void moveToCurrentLocation() {
        LocationManager lm = (LocationManager) requireContext().getSystemService(getContext().LOCATION_SERVICE);
        try {
            Location last = null;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (last != null) {
                GeoPoint me = new GeoPoint(last.getLatitude(), last.getLongitude());
                mapView.getController().setCenter(me);
            } else {
                Toast.makeText(getContext(), "Unable to fetch last location", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(getContext(), "Location permission missing", Toast.LENGTH_SHORT).show();
        }
    }

    private void submitForm() {
        String name = Objects.requireNonNull(inputName.getText()).toString().trim();
        String ownerName = Objects.requireNonNull(inputOwnerName.getText()).toString().trim();
        String contact = Objects.requireNonNull(inputContact.getText()).toString().trim();
        String address = Objects.requireNonNull(inputAddress.getText()).toString().trim();

        if (name.isEmpty() || ownerName.isEmpty() || contact.isEmpty() || address.isEmpty()) {
            Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        GeoPoint center = (GeoPoint) mapView.getMapCenter();
        if (center == null) {
            Toast.makeText(getContext(), "Map center not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        String spaceId = globalSpacesRef.child(category).push().getKey();
        if (spaceId == null) {
            Toast.makeText(getContext(), "Failed to create ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Space space = new Space(
                spaceId,
                name,
                category,
                center.getLatitude(),
                center.getLongitude(),
                uid,
                ownerName,
                address,
                contact,
                email,
                true
        );

        globalSpacesRef.child(category).child(spaceId).setValue(space).addOnCompleteListener(globalTask -> {
            if (globalTask.isSuccessful()) {
                userSpacesRef.child(category).child(spaceId).setValue(space).addOnCompleteListener(userTask -> {
                    if (userTask.isSuccessful()) {
                        Toast.makeText(getContext(), category + " saved successfully!", Toast.LENGTH_SHORT).show();
                        getParentFragmentManager().popBackStack();
                    } else {
                        Toast.makeText(getContext(), "User save failed: " + Objects.requireNonNull(userTask.getException()).getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                Toast.makeText(getContext(), "Global save failed: " + Objects.requireNonNull(globalTask.getException()).getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadInterstitialAd() {
        InterstitialAd.load(requireContext(),
                "ca-app-pub-3940256099942544/1033173712", // Replace with your Ad Unit ID
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mInterstitialAd = null;
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
}
