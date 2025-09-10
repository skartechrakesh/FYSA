package com.skar.fysa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.Objects;

public class EditSpaceFragment extends Fragment {

    private TextInputEditText inputName, inputOwnerName, inputContact, inputAddress;
    private MaterialAutoCompleteTextView spinnerCategory;
    private FloatingActionButton btnMyLocation;
    private ImageView crosshair;
    private MaterialButton btnSubmit;
    private TextInputLayout eNameLayout, eOwnerNameLayout, eContactLayout, eAddressLayout, eCategoryLayout;
    private MapView mapView;

    private DatabaseReference userSpacesRef, globalSpacesRef;
    private String currentUserId;
    private Space space;

    private InterstitialAd mInterstitialAd;
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"; // Test Ad
    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

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

        View view = inflater.inflate(R.layout.fragment_edit_space, container, false);

        // OSMDroid setup
        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid_prefs", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "You must be logged in", Toast.LENGTH_SHORT).show();
            return view;
        }

        currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        userSpacesRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users").child(currentUserId).child("space_details");
        globalSpacesRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("spaces");

        space = (Space) getArguments().getSerializable("space");
        if (space == null) {
            Toast.makeText(getContext(), "Error: No space data received", Toast.LENGTH_LONG).show();
            return view;
        }

        bindViews(view);
        setupCategorySpinner();
        setListeners();
        fillFormWithData();
        initMap();
        handleBackPress();

        // Apply stroke helper for inputs
        FysaApp.setupTextInputStrokes(requireContext(), eNameLayout, eOwnerNameLayout, eContactLayout, eAddressLayout, eCategoryLayout);

        loadInterstitialAd(); // Load interstitial ad

        return view;
    }

    private void bindViews(View view) {
        inputName = view.findViewById(R.id.inputName);
        inputOwnerName = view.findViewById(R.id.inputOwnerName);
        inputContact = view.findViewById(R.id.inputContact);
        inputAddress = view.findViewById(R.id.inputAddress);
        spinnerCategory = view.findViewById(R.id.spinnerCategory);
        btnMyLocation = view.findViewById(R.id.btnMyLocation);
        crosshair = view.findViewById(R.id.crosshair);
        btnSubmit = view.findViewById(R.id.btnSubmit);
        mapView = view.findViewById(R.id.mapView);
        eNameLayout = view.findViewById(R.id.eNameLayout);
        eOwnerNameLayout = view.findViewById(R.id.eOwnerNameLayout);
        eContactLayout = view.findViewById(R.id.eContactLayout);
        eAddressLayout = view.findViewById(R.id.eAddressLayout);
        eCategoryLayout = view.findViewById(R.id.eCategoryLayout);
    }

    private void setupCategorySpinner() {
        String[] categories = {"Hostel", "PG", "Room", "House", "Flat"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, categories);
        spinnerCategory.setAdapter(adapter);

        // Preselect value
        int position = adapter.getPosition(space.getType());
        if (position >= 0) spinnerCategory.setText(adapter.getItem(position), false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setListeners() {
        btnMyLocation.setOnClickListener(v -> requestLocationPermission());

        btnSubmit.setOnClickListener(v -> {
            new Handler().postDelayed(() -> {
                if (mInterstitialAd != null) {
                    mInterstitialAd.show(requireActivity());
                    mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            updateSpace();
                            loadInterstitialAd();
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                            updateSpace();
                        }
                    });
                } else {
                    updateSpace();
                }
            }, 1000);
        });

        mapView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    private void handleBackPress() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        getParentFragmentManager().popBackStack();
                    }
                });
    }

    private void fillFormWithData() {
        inputName.setText(space.getName());
        inputOwnerName.setText(space.getOwnerName());
        inputContact.setText(space.getContact());
        inputAddress.setText(space.getAddress());
    }

    private void initMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(19.0);

        if (space.getLatitude() != 0 && space.getLongitude() != 0) {
            mapView.getController().setCenter(new GeoPoint(space.getLatitude(), space.getLongitude()));
        } else {
            mapView.getController().setCenter(new GeoPoint(0.0, 0.0));
        }
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentLocation();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void moveToCurrentLocation() {
        android.location.LocationManager lm =
                (android.location.LocationManager) requireContext().getSystemService(getContext().LOCATION_SERVICE);
        try {
            Location last = null;
            if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
                last = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            if (last == null && lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER))
                last = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);

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

    private void updateSpace() {
        String name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
        String ownerName = inputOwnerName.getText() != null ? inputOwnerName.getText().toString().trim() : "";
        String contact = inputContact.getText() != null ? inputContact.getText().toString().trim() : "";
        String address = inputAddress.getText() != null ? inputAddress.getText().toString().trim() : "";
        String category = spinnerCategory.getText() != null ? spinnerCategory.getText().toString() : "";

        if (name.isEmpty() || ownerName.isEmpty() || contact.isEmpty() || address.isEmpty() || category.isEmpty()) {
            Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        GeoPoint center = (GeoPoint) mapView.getMapCenter();

        space.setName(name);
        space.setOwnerName(ownerName);
        space.setContact(contact);
        space.setAddress(address);
        space.setLatitude(center.getLatitude());
        space.setLongitude(center.getLongitude());
        space.setType(category);

        globalSpacesRef.child(category).child(space.getId())
                .setValue(space)
                .addOnCompleteListener(taskGlobal -> {
                    if (taskGlobal.isSuccessful()) {
                        userSpacesRef.child(category).child(space.getId())
                                .setValue(space)
                                .addOnCompleteListener(taskUser -> {
                                    if (taskUser.isSuccessful()) {
                                        Toast.makeText(getContext(), "Space updated successfully!", Toast.LENGTH_SHORT).show();
                                        requireActivity().getSupportFragmentManager().popBackStack();
                                    } else {
                                        Toast.makeText(getContext(),
                                                "User update failed: " + Objects.requireNonNull(taskUser.getException()).getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                    } else {
                        Toast.makeText(getContext(),
                                "Global update failed: " + Objects.requireNonNull(taskGlobal.getException()).getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(requireContext(), INTERSTITIAL_AD_UNIT_ID, adRequest,
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
