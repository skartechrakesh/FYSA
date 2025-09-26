package com.skar.fysa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EditSpaceFragment extends Fragment {

    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-6462351900381242/1252944593";
    // Test ad unit: "ca-app-pub-3940256099942544/1033173712"
    // Original ad unit: "ca-app-pub-6462351900381242/1252944593"

    private TextInputEditText inputName, inputOwnerName, inputContact, inputAddress, inputSpaceFeatures;
    private MaterialAutoCompleteTextView spinnerCategory, spinnerVacancy;
    private FloatingActionButton btnMyLocation;
    private ImageView crosshair;
    private MaterialButton btnSubmit;
    private TextInputLayout eNameLayout, eOwnerNameLayout, eContactLayout, eAddressLayout, eFeaturesLayout, eCategoryLayout, eVacancyLayout;
    private MapView mapView;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) moveToCurrentLocation();
                else Snackbar.make(requireView(), "Location permission denied", Snackbar.LENGTH_SHORT).show();
            });

    private FirebaseFirestore firestore;
    private CollectionReference spacesRef;
    private String currentUserId;
    private Space space;
    private InterstitialAd mInterstitialAd;
    private OnSpaceUpdatedListener onSpaceUpdatedListener;

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

    public void setOnSpaceUpdatedListener(OnSpaceUpdatedListener listener) {
        this.onSpaceUpdatedListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_edit_space, container, false);

        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid_prefs", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Snackbar.make(view, "You must be logged in", Snackbar.LENGTH_SHORT).show();
            return view;
        }

        currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        firestore = FirebaseFirestore.getInstance();
        spacesRef = firestore.collection("spaces");

        space = getArguments().getParcelable("space");
        if (space == null) {
            Snackbar.make(view, "Error: No space data received", Snackbar.LENGTH_LONG).show();
            return view;
        }

        bindViews(view);
        setupCategorySpinner();
        setupVacancySpinner();
        setListeners();
        fillFormWithData();
        initMap();
        // handleBackPress();
        setupTextInputStrokes(requireContext(), eNameLayout, eOwnerNameLayout, eContactLayout, eAddressLayout, eFeaturesLayout, eCategoryLayout, eVacancyLayout);
        loadInterstitialAd();

        return view;
    }

    private void bindViews(View view) {
        inputName = view.findViewById(R.id.inputName);
        inputOwnerName = view.findViewById(R.id.inputOwnerName);
        inputContact = view.findViewById(R.id.inputContact);
        inputAddress = view.findViewById(R.id.inputAddress);
        inputSpaceFeatures = view.findViewById(R.id.inputSpaceFeatures);
        spinnerCategory = view.findViewById(R.id.spinnerCategory);
        spinnerVacancy = view.findViewById(R.id.spinnerVacancy);
        btnMyLocation = view.findViewById(R.id.btnMyLocation);
        crosshair = view.findViewById(R.id.crosshair);
        btnSubmit = view.findViewById(R.id.btnSubmit);
        mapView = view.findViewById(R.id.mapView);

        eNameLayout = view.findViewById(R.id.eNameLayout);
        eOwnerNameLayout = view.findViewById(R.id.eOwnerNameLayout);
        eContactLayout = view.findViewById(R.id.eContactLayout);
        eAddressLayout = view.findViewById(R.id.eAddressLayout);
        eFeaturesLayout = view.findViewById(R.id.eFeaturesLayout);
        eCategoryLayout = view.findViewById(R.id.eCategoryLayout);
        eVacancyLayout = view.findViewById(R.id.eVacancyLayout);
    }

    private void setupCategorySpinner() {
        String[] categories = {"Hostel", "PG", "Room", "House", "Flat"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, categories);
        spinnerCategory.setAdapter(adapter);
        int position = adapter.getPosition(space.getCategory());
        if (position >= 0) spinnerCategory.setText(adapter.getItem(position), false);
    }

    private void setupVacancySpinner() {
        String[] options = {"Yes", "No"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, options);
        spinnerVacancy.setAdapter(adapter);
        String currentValue = (space.isActive() != null && space.isActive()) ? "Yes" : "No";
        spinnerVacancy.post(() -> spinnerVacancy.setText(currentValue, false));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setListeners() {
        btnMyLocation.setOnClickListener(v -> requestLocationPermission());
        btnSubmit.setOnClickListener(v -> showAdThenSave());

        mapView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

//    private void handleBackPress() {
//        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
//                new OnBackPressedCallback(true) {
//                    @Override
//                    public void handleOnBackPressed() {
//                        getParentFragmentManager().popBackStack();
//                    }
//                });
//    }

    private void fillFormWithData() {
        inputName.setText(space.getName());
        inputOwnerName.setText(space.getOwnerName());
        inputContact.setText(space.getContact());
        inputAddress.setText(space.getAddress());
        inputSpaceFeatures.setText(space.getSpaceFeatures() != null ? space.getSpaceFeatures() : "");
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

    @SuppressLint("MissingPermission")
    private void moveToCurrentLocation() {
        android.location.LocationManager lm =
                (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
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
                Snackbar.make(requireView(), "Unable to fetch last location", Snackbar.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Snackbar.make(requireView(), "Location permission missing", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showAdThenSave() {
        new Handler().postDelayed(() -> {
            if (mInterstitialAd != null) {
                mInterstitialAd.show(requireActivity());
                mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        saveUpdatedSpace();
                        loadInterstitialAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                        saveUpdatedSpace();
                    }
                });
            } else {
                saveUpdatedSpace();
            }
        }, 500);
    }

    private void saveUpdatedSpace() {
        String name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
        String ownerName = inputOwnerName.getText() != null ? inputOwnerName.getText().toString().trim() : "";
        String contact = inputContact.getText() != null ? inputContact.getText().toString().trim() : "";
        String address = inputAddress.getText() != null ? inputAddress.getText().toString().trim() : "";
        String features = inputSpaceFeatures.getText() != null ? inputSpaceFeatures.getText().toString().trim() : "";
        String category = spinnerCategory.getText() != null ? spinnerCategory.getText().toString() : "";
        String vacancyText = spinnerVacancy.getText() != null ? spinnerVacancy.getText().toString() : "No";

        if (name.isEmpty() || ownerName.isEmpty() || contact.isEmpty() || address.isEmpty() || category.isEmpty()) {
            Snackbar.make(requireView(), "Please fill all fields", Snackbar.LENGTH_SHORT).show();
            return;
        }

        boolean isActive = vacancyText.equals("Yes");
        GeoPoint center = (GeoPoint) mapView.getMapCenter();

        Map<String, Object> spaceUpdates = new HashMap<>();
        spaceUpdates.put("name", name);
        spaceUpdates.put("ownerName", ownerName);
        spaceUpdates.put("contact", contact);
        spaceUpdates.put("address", address);
        spaceUpdates.put("spaceFeatures", features);
        spaceUpdates.put("latitude", center.getLatitude());
        spaceUpdates.put("longitude", center.getLongitude());
        spaceUpdates.put("category", category);
        spaceUpdates.put("active", isActive);
        spaceUpdates.put("ownerId", currentUserId);

        spacesRef.document(space.getId())
                .update(spaceUpdates)
                .addOnSuccessListener(unused -> {
                    // Update local object & return result
                    space.setName(name);
                    space.setOwnerName(ownerName);
                    space.setContact(contact);
                    space.setAddress(address);
                    space.setSpaceFeatures(features);
                    space.setCategory(category);
                    space.setActive(isActive);

                    Bundle result = new Bundle();
                    result.putParcelable("space", space);
                    getParentFragmentManager().setFragmentResult("edit_space_result", result);

                    if (onSpaceUpdatedListener != null) onSpaceUpdatedListener.onSpaceUpdated(space);

                    Snackbar.make(requireView(), "Space updated successfully!", Snackbar.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragmentContainer, new ManageSpacesFragment())
                            .commitAllowingStateLoss();
                })
                .addOnFailureListener(e -> Snackbar.make(requireView(),
                        "Space update failed: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
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

    public interface OnSpaceUpdatedListener {
        void onSpaceUpdated(Space updatedSpace);
    }
}
