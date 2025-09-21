package com.skar.fysa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

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
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SpaceFormFragment extends Fragment {

    private final Handler adHandler = new Handler();
    private TextInputEditText inputName, inputOwnerName, inputContact, inputAddress;
    private TextInputLayout nameLayout, ownerNameLayout, contactLayout, addressLayout;
    private Button btnSubmit;
    private TextView txtCategory;
    private FloatingActionButton btnMyLocation;
    private MapView mapView;
    private View fragmentView;

    private FirebaseFirestore firestore;
    private String category;
    private InterstitialAd mInterstitialAd;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) moveToCurrentLocation();
                else Snackbar.make(fragmentView, "Location permission denied", Snackbar.LENGTH_SHORT).show();
            });

    private static void setupTextInputStrokes(Context context, TextInputLayout... inputs) {
        if (context == null || inputs == null) return;

        int focusedColor = ContextCompat.getColor(context, R.color.stroke_focused);
        int unfocusedColor = ContextCompat.getColor(context, R.color.stroke_unfocused);

        for (TextInputLayout input : inputs) {
            if (input == null) continue;

            ColorStateList states = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_enabled, android.R.attr.state_focused},
                            new int[]{android.R.attr.state_enabled}
                    },
                    new int[]{focusedColor, unfocusedColor}
            );

            input.setBoxStrokeColorStateList(states);
        }
    }

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        fragmentView = inflater.inflate(R.layout.fragment_space_form, container, false);

        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid_prefs", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        category = getArguments() != null ? getArguments().getString("CATEGORY", "Unknown") : "Unknown";

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Snackbar.make(fragmentView, "You must be logged in", Snackbar.LENGTH_SHORT).show();
            return fragmentView;
        }
        firestore = FirebaseFirestore.getInstance();

        bindViews(fragmentView);
        setListeners();
        setupBackHandling();
        initMap();
        requestLocationPermission();

        txtCategory.setText("Category: " + category);
        setupTextInputStrokes(requireContext(), nameLayout, ownerNameLayout, contactLayout, addressLayout);

        loadInterstitialAd();
        loadDefaultUserData();

        return fragmentView;
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

    private void bindViews(View view) {
        inputName = view.findViewById(R.id.inputName);
        inputOwnerName = view.findViewById(R.id.inputOwnerName);
        inputContact = view.findViewById(R.id.inputContact);
        inputAddress = view.findViewById(R.id.inputAddress);

        nameLayout = view.findViewById(R.id.nameLayout);
        ownerNameLayout = view.findViewById(R.id.ownerNameLayout);
        contactLayout = view.findViewById(R.id.contactLayout);
        addressLayout = view.findViewById(R.id.addressLayout);

        btnSubmit = view.findViewById(R.id.btnSubmit);
        txtCategory = view.findViewById(R.id.txtCategory);
        btnMyLocation = view.findViewById(R.id.btnMyLocation);
        mapView = view.findViewById(R.id.mapView);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setListeners() {
        btnMyLocation.setOnClickListener(v -> requestLocationPermission());
        btnSubmit.setOnClickListener(v -> submitForm());

        mapView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
                v.performClick();
            }
            return false;
        });
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
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
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
        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        try {
            Location last = null;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (last == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (last != null) {
                GeoPoint me = new GeoPoint(last.getLatitude(), last.getLongitude());
                mapView.getController().setCenter(me);
            } else {
                Snackbar.make(fragmentView, "Enable GPS to fetch location.", Snackbar.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Snackbar.make(fragmentView, "Failed to fetch location: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void submitForm() {
        String name = Objects.requireNonNull(inputName.getText()).toString().trim();
        String ownerName = Objects.requireNonNull(inputOwnerName.getText()).toString().trim();
        String contact = Objects.requireNonNull(inputContact.getText()).toString().trim();
        String address = Objects.requireNonNull(inputAddress.getText()).toString().trim();

        if (name.isEmpty() || ownerName.isEmpty() || contact.isEmpty() || address.isEmpty()) {
            Snackbar.make(fragmentView, "Please fill all fields", Snackbar.LENGTH_SHORT).show();
            return;
        }

        GeoPoint center = (GeoPoint) mapView.getMapCenter();
        if (center == null) {
            Snackbar.make(fragmentView, "Map center not available", Snackbar.LENGTH_SHORT).show();
            return;
        }

        checkForExistingSpace(name, ownerName, contact, address, center);
    }

    private void checkForExistingSpace(String name, String ownerName, String contact, String address, GeoPoint center) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        firestore.collection("spaces")
                .whereEqualTo("ownerId", uid)   // only current user's spaces
                .whereEqualTo("name", name)     // check for same name
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        Snackbar.make(fragmentView, "Space name already exists for your account.", Snackbar.LENGTH_LONG).show();
                    } else {
                        showAdAndSaveSpace(name, ownerName, contact, address, center);
                    }
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(fragmentView, "Failed to check space name. Saving anyway.", Snackbar.LENGTH_SHORT).show();
                    showAdAndSaveSpace(name, ownerName, contact, address, center);
                });
    }

    private void showAdAndSaveSpace(String name, String ownerName, String contact, String address, GeoPoint center) {
        adHandler.postDelayed(() -> {
            if (mInterstitialAd != null) {
                mInterstitialAd.show(requireActivity());
                mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        saveNewSpace(name, ownerName, contact, address, center);
                        loadInterstitialAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                        saveNewSpace(name, ownerName, contact, address, center);
                    }
                });
            } else {
                saveNewSpace(name, ownerName, contact, address, center);
            }
        }, 1000);
    }

    private void saveNewSpace(String name, String ownerName, String contact, String address, GeoPoint center) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        String email = user.getEmail();
        String spaceId = firestore.collection("spaces").document().getId();

        Map<String, Object> space = new HashMap<>();
        space.put("spaceId", spaceId);
        space.put("name", name);
        space.put("category", category);
        space.put("latitude", center.getLatitude());
        space.put("longitude", center.getLongitude());
        space.put("ownerId", uid);
        space.put("ownerName", ownerName);
        space.put("address", address);
        space.put("contact", contact);
        space.put("email", email);
        space.put("active", true);

        firestore.collection("spaces").document(spaceId).set(space)
                .addOnSuccessListener(unused -> {
                    firestore.collection("users").document(uid)
                            .update("space_reference." + spaceId, true)
                            .addOnSuccessListener(unused1 -> {
                                Snackbar.make(fragmentView, category + " saved successfully!", Snackbar.LENGTH_SHORT).show();
                                getParentFragmentManager().popBackStack();
                            })
                            .addOnFailureListener(e -> Snackbar.make(fragmentView,
                                    "Failed to add space reference: " + e.getMessage(),
                                    Snackbar.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Snackbar.make(fragmentView,
                        "Save failed: " + e.getMessage(),
                        Snackbar.LENGTH_LONG).show());
    }

    private void loadInterstitialAd() {
        InterstitialAd.load(requireContext(),
                "ca-app-pub-3940256099942544/1033173712",
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

    private void loadDefaultUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        firestore.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        String name = snapshot.getString("name");
                        String contact = snapshot.getString("contact");
                        if (name != null) inputOwnerName.setText(name);
                        if (contact != null) inputContact.setText(contact);
                    }
                })
                .addOnFailureListener(e -> Snackbar.make(fragmentView,
                        "Failed to load user data: " + e.getMessage(),
                        Snackbar.LENGTH_SHORT).show());
    }
}
