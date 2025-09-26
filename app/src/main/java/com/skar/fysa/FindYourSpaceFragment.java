package com.skar.fysa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FindYourSpaceFragment extends Fragment {

    private static final double MAX_DISTANCE_METERS = 5000;
    private static final String NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110";

    private final List<Space> spaceList = new ArrayList<>();

    private RecyclerView recyclerView;
    private SpacesAdapter adapter;
    private Location userLocation;
    private TextView btnHostel, btnPG, btnRoom, btnHouse, btnFlat;
    private SwipeRefreshLayout swipeRefresh;
    private NativeAdView nativeAdView;
    private NativeAd loadedNativeAd;
    private String selectedCategory = null;

    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore firestore;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    fetchUserLocationAndSpaces();
                } else {
                    Snackbar.make(requireView(), "Location permission denied", Snackbar.LENGTH_SHORT).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_find_your_space, container, false);

        recyclerView = view.findViewById(R.id.recyclerSpaces);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        btnHostel = view.findViewById(R.id.btnHostel);
        btnPG = view.findViewById(R.id.btnPG);
        btnRoom = view.findViewById(R.id.btnRoom);
        btnHouse = view.findViewById(R.id.btnHouse);
        btnFlat = view.findViewById(R.id.btnFlat);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        nativeAdView = view.findViewById(R.id.native_ad_view);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        firestore = FirebaseFirestore.getInstance();

        adapter = new SpacesAdapter(spaceList, space -> {
            SpaceDetailsFragment fragment = new SpaceDetailsFragment();
            Bundle args = new Bundle();
            args.putParcelable("space", space);
            fragment.setArguments(args);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit();
        });

        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::fetchUserLocationAndSpaces);

        View.OnClickListener filterListener = v -> {
            resetCategorySelection();
            TextView clicked = (TextView) v;
            clicked.setSelected(true);
            selectedCategory = clicked.getText().toString();
            fetchUserLocationAndSpaces();
        };

        btnHostel.setOnClickListener(filterListener);
        btnPG.setOnClickListener(filterListener);
        btnRoom.setOnClickListener(filterListener);
        btnHouse.setOnClickListener(filterListener);
        btnFlat.setOnClickListener(filterListener);

        MobileAds.initialize(requireContext(), initializationStatus -> loadNativeAd());

        // Restore state after theme change
        if (savedInstanceState != null) {
            selectedCategory = savedInstanceState.getString("selectedCategory");
            if (selectedCategory != null) {
                restoreCategorySelection(selectedCategory);
                // Also refresh list
                if (spaceList.isEmpty()) {
                    fetchUserLocationAndSpaces();
                } else {
                    adapter.submitList(new ArrayList<>(spaceList));
                }
            }
        }

        return view;
    }

    private void resetCategorySelection() {
        btnHostel.setSelected(false);
        btnPG.setSelected(false);
        btnRoom.setSelected(false);
        btnHouse.setSelected(false);
        btnFlat.setSelected(false);
    }

    private void restoreCategorySelection(String category) {
        resetCategorySelection();
        if (category.equals(btnHostel.getText().toString())) btnHostel.setSelected(true);
        else if (category.equals(btnPG.getText().toString())) btnPG.setSelected(true);
        else if (category.equals(btnRoom.getText().toString())) btnRoom.setSelected(true);
        else if (category.equals(btnHouse.getText().toString())) btnHouse.setSelected(true);
        else if (category.equals(btnFlat.getText().toString())) btnFlat.setSelected(true);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedCategory", selectedCategory);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (selectedCategory != null) {
            if (spaceList.isEmpty()) {
                fetchUserLocationAndSpaces();
            } else {
                adapter.submitList(new ArrayList<>(spaceList));
            }
        }
    }

    private void fetchUserLocationAndSpaces() {
        if (selectedCategory == null) {
            swipeRefresh.setRefreshing(false);
            Snackbar.make(requireView(), "Please select a category first", Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (getContext() == null) return;

        if (getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                getActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }

        swipeRefresh.setRefreshing(true);

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) userLocation = location;
                    else fusedLocationClient.getLastLocation().addOnSuccessListener(last -> userLocation = last);
                    fetchSpacesByCategory();
                })
                .addOnFailureListener(e -> fetchSpacesByCategory());
    }

    private void fetchSpacesByCategory() {
        firestore.collection("spaces")
                .whereEqualTo("active", true)
                .whereEqualTo("category", selectedCategory)
                .get()
                .addOnSuccessListener(this::filterSpacesByDistance)
                .addOnFailureListener(e -> {
                    swipeRefresh.setRefreshing(false);
                    Snackbar.make(requireView(), "Failed to load spaces: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                });
    }

    private void filterSpacesByDistance(QuerySnapshot snapshots) {
        spaceList.clear();

        for (QueryDocumentSnapshot doc : snapshots) {
            Space space = doc.toObject(Space.class);
            space.setId(doc.getId());

            if (userLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(
                        userLocation.getLatitude(),
                        userLocation.getLongitude(),
                        space.getLatitude(),
                        space.getLongitude(),
                        results
                );
                int distanceMeters = (int) results[0];
                if (distanceMeters <= MAX_DISTANCE_METERS) {
                    space.setDistanceMeters(distanceMeters);
                    spaceList.add(space);
                }
            } else {
                space.setDistanceMeters(-1);
                spaceList.add(space);
            }
        }

        spaceList.sort(Comparator.comparingInt(s -> s.getDistanceMeters() != null ? s.getDistanceMeters() : Integer.MAX_VALUE));
        adapter.submitList(new ArrayList<>(spaceList));

        swipeRefresh.setRefreshing(false);

        if (spaceList.isEmpty())
            Snackbar.make(requireView(), "No nearby spaces found", Snackbar.LENGTH_SHORT).show();
    }

    private void loadNativeAd() {
        AdLoader adLoader = new AdLoader.Builder(requireContext(), NATIVE_AD_UNIT_ID)
                .forNativeAd(nativeAd -> {
                    if (loadedNativeAd != null) loadedNativeAd.destroy();
                    loadedNativeAd = nativeAd;
                    nativeAdView.setVisibility(View.VISIBLE);
                    populateNativeAdView(nativeAd, nativeAdView);
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull com.google.android.gms.ads.LoadAdError adError) {
                        nativeAdView.setVisibility(View.GONE);
                    }
                }).build();
        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));

        ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
        ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
        ((TextView) adView.getCallToActionView()).setText(nativeAd.getCallToAction());

        adView.setNativeAd(nativeAd);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (loadedNativeAd != null) loadedNativeAd.destroy();
    }

    // ===== Adapter =====
    private static class SpacesAdapter extends RecyclerView.Adapter<SpacesAdapter.SpaceVH> {
        private final List<Space> items = new ArrayList<>();
        private final OnSpaceClick onSpaceClick;

        public SpacesAdapter(List<Space> initial, OnSpaceClick onSpaceClick) {
            if (initial != null) items.addAll(initial);
            this.onSpaceClick = onSpaceClick;
        }

        public void submitList(List<Space> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SpaceVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_find_space, parent, false);
            return new SpaceVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SpaceVH holder, int position) {
            holder.bind(items.get(position), onSpaceClick);
        }

        @Override
        public int getItemCount() { return items.size(); }

        public interface OnSpaceClick { void onClick(Space space); }

        static class SpaceVH extends RecyclerView.ViewHolder {
            TextView tvName, tvCategory, tvOwner, tvAddress, tvDistance, tvVacancy;

            SpaceVH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvCategory = itemView.findViewById(R.id.tvCategory);
                tvOwner = itemView.findViewById(R.id.tvOwner);
                tvAddress = itemView.findViewById(R.id.tvAddress);
                tvDistance = itemView.findViewById(R.id.tvDistance);
                tvVacancy = itemView.findViewById(R.id.tvVacancy);
            }

            void bind(Space space, OnSpaceClick listener) {
                tvName.setText(space.getName());
                tvCategory.setText(space.getCategory());
                tvOwner.setText(space.getOwnerName());
                tvAddress.setText(space.getAddress());
                tvVacancy.setText(Boolean.TRUE.equals(space.isActive()) ? "Vacancy Available" : "Not Available");
                tvDistance.setText(space.getDistanceMeters() != null && space.getDistanceMeters() >= 0
                        ? space.getDistanceMeters() + " m away"
                        : "Distance unavailable");

                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onClick(space);
                });
            }
        }
    }
}
