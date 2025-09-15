package com.skar.fysa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.cardview.widget.CardView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.*;

import java.io.Serializable;
import java.util.*;

public class FindYourSpaceFragment extends Fragment {

    private static final double MAX_DISTANCE_METERS = 5000;
    private static final String TAG = "FindYourSpaceFragment";
    private static final String NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"; // test

    private RecyclerView recyclerView;
    private SpacesAdapter adapter;
    private final List<Space> spaceList = new ArrayList<>();
    private Location userLocation;

    private TextView btnHostel, btnPG, btnRoom, btnHouse, btnFlat;
    private SwipeRefreshLayout swipeRefresh;
    private NativeAdView nativeAdView;
    private NativeAd loadedNativeAd;

    private final String DB_URL = "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private String selectedCategory = null;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    fetchUserLocationAndThenSpaces();
                } else {
                    Snackbar.make(requireView(), "Location permission denied", Snackbar.LENGTH_SHORT).show();
                }
            });

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedCategory", selectedCategory);
        outState.putSerializable("spaceList", new ArrayList<>(spaceList));
    }

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

        adapter = new SpacesAdapter(spaceList, space -> {
            SpaceDetailsFragment fragment = new SpaceDetailsFragment();
            Bundle args = new Bundle();
            args.putSerializable("space", space);
            fragment.setArguments(args);

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit();
        });

        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadNearbySpacesFromFirebase);

        View.OnClickListener filterListener = v -> {
            resetCategorySelection();
            TextView clicked = (TextView) v;
            clicked.setSelected(true);
            selectedCategory = clicked.getText().toString();
            spaceList.clear();
            loadNearbySpacesFromFirebase();
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
            List<Space> savedSpaces = (List<Space>) savedInstanceState.getSerializable("spaceList");
            if (savedSpaces != null) {
                spaceList.clear();
                spaceList.addAll(savedSpaces);
                adapter.submitList(new ArrayList<>(spaceList));
            }
        } else {
            checkLocationAndLoad();
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

    @Override
    public void onResume() {
        super.onResume();
        // Reload spaces when returning from details if category is selected
        if (selectedCategory != null) {
            loadNearbySpacesFromFirebase();
        }
    }

    private void checkLocationAndLoad() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            fetchUserLocationAndThenSpaces();
        }
    }

    private void fetchUserLocationAndThenSpaces() {
        android.location.LocationManager lm =
                (android.location.LocationManager) requireContext().getSystemService(requireContext().LOCATION_SERVICE);

        try {
            Location last = null;
            if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                last = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            }
            if (last == null && lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                last = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            }

            if (last != null) userLocation = last;

        } catch (SecurityException e) {
            Snackbar.make(requireView(), "Location permission missing", Snackbar.LENGTH_SHORT).show();
        } finally {
            loadNearbySpacesFromFirebase();
        }
    }

    private void loadNearbySpacesFromFirebase() {
        if (selectedCategory == null || !spaceList.isEmpty()) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        swipeRefresh.setRefreshing(true);
        DatabaseReference spacesRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("spaces")
                .child(selectedCategory);

        spacesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                spaceList.clear();

                for (DataSnapshot spaceSnap : snapshot.getChildren()) {
                    Space space = spaceSnap.getValue(Space.class);
                    if (space == null || !Boolean.TRUE.equals(space.isVacancy())) continue;
                    if (!spaceSnap.hasChild("latitude") || !spaceSnap.hasChild("longitude")) continue;

                    if (space.getId() == null || space.getId().isEmpty())
                        space.setId(spaceSnap.getKey());

                    space.setType(selectedCategory);

                    if (userLocation != null) {
                        float[] results = new float[1];
                        Location.distanceBetween(
                                userLocation.getLatitude(),
                                userLocation.getLongitude(),
                                space.getLatitude(),
                                space.getLongitude(),
                                results
                        );
                        if (results[0] <= MAX_DISTANCE_METERS) {
                            space.setDistanceMeters((int) results[0]);
                            spaceList.add(space);
                        }
                    } else spaceList.add(space);
                }

                Collections.sort(spaceList, Comparator.comparingInt(s ->
                        s.getDistanceMeters() != null ? s.getDistanceMeters() : Integer.MAX_VALUE));

                adapter.submitList(new ArrayList<>(spaceList));
                swipeRefresh.setRefreshing(false);

                if (spaceList.isEmpty()) {
                    Snackbar.make(requireView(), "No nearby spaces found", Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase error: " + error.getMessage());
                swipeRefresh.setRefreshing(false);
            }
        });
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
                        Log.e(TAG, "Failed to load native ad: " + adError.getMessage());
                        nativeAdView.setVisibility(View.GONE);
                    }
                }).build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        TextView headline = adView.findViewById(R.id.ad_headline);
        headline.setText(nativeAd.getHeadline());

        TextView body = adView.findViewById(R.id.ad_body);
        body.setText(nativeAd.getBody());

        Button cta = adView.findViewById(R.id.ad_call_to_action);
        cta.setText(nativeAd.getCallToAction());

        adView.setHeadlineView(headline);
        adView.setBodyView(body);
        adView.setCallToActionView(cta);
        adView.setNativeAd(nativeAd);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (loadedNativeAd != null) loadedNativeAd.destroy();
    }

    // ======= ADAPTER (no pagination) =======
    private static class SpacesAdapter extends RecyclerView.Adapter<SpacesAdapter.SpaceVH> {
        public interface OnSpaceClick {
            void onClick(Space space);
        }

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
        public int getItemCount() {
            return items.size();
        }

        static class SpaceVH extends RecyclerView.ViewHolder {
            TextView tvName, tvType, tvOwner, tvAddress, tvContact, tvDistance, tvVacancy;
            CardView card;

            SpaceVH(@NonNull View itemView) {
                super(itemView);
                card = (CardView) itemView;
                tvName = itemView.findViewById(R.id.tvName);
                tvType = itemView.findViewById(R.id.tvType);
                tvOwner = itemView.findViewById(R.id.tvOwner);
                tvAddress = itemView.findViewById(R.id.tvAddress);
                tvContact = itemView.findViewById(R.id.tvContact);
                tvDistance = itemView.findViewById(R.id.tvDistance);
                tvVacancy = itemView.findViewById(R.id.tvVacancy);
            }

            void bind(Space space, OnSpaceClick listener) {
                tvName.setText(space.getName());
                tvType.setText(space.getType());
                tvOwner.setText(space.getOwnerName());
                tvAddress.setText(space.getAddress());
                tvContact.setText(space.getContact());
                tvVacancy.setText(Boolean.TRUE.equals(space.isVacancy()) ? "Vacancy Available" : "Not Available");
                tvDistance.setText(space.getDistanceMeters() != null ? space.getDistanceMeters() + " m away" : "Unknown");

                card.setOnClickListener(v -> {
                    if (listener != null) listener.onClick(space);
                });
            }
        }
    }
}
