package com.skar.fysa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FindYourSpaceFragment extends Fragment {

    private static final double MAX_DISTANCE_METERS = 5000;
    private static final int PAGE_SIZE = 10; // Pagination size
    private static final String TAG = "FindYourSpaceFragment";
    private static final String NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"; // Original Unit ID: "ca-app-pub-6462351900381242/4181472219"

    private final List<Space> spaceList = new ArrayList<>();

    private RecyclerView recyclerView;
    private SpacesAdapter adapter;
    private Location userLocation;
    private TextView btnHostel, btnPG, btnRoom, btnHouse, btnFlat;
    private SwipeRefreshLayout swipeRefresh;
    private NativeAdView nativeAdView;
    private NativeAd loadedNativeAd;
    private String selectedCategory = null;

    private boolean isLoading = false;
    private boolean isLastPage = false;
    private DocumentSnapshot lastVisible = null;

    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore firestore;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    fetchUserLocationAndThenSpaces(true);
                } else {
                    Snackbar.make(requireView(), "Location permission denied", Snackbar.LENGTH_SHORT).show();
                }
            });

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedCategory", selectedCategory);
        outState.putParcelableArrayList("spaceList", new ArrayList<>(spaceList));
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

        // Pagination scroll listener
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (!isLoading && !isLastPage && lm != null &&
                        lm.findLastVisibleItemPosition() >= spaceList.size() - 2) {
                    loadMoreSpaces();
                }
            }
        });

        swipeRefresh.setOnRefreshListener(() -> {
            if (selectedCategory != null) {
                lastVisible = null;
                isLastPage = false;
                fetchUserLocationAndThenSpaces(true);
            } else {
                swipeRefresh.setRefreshing(false);
                Snackbar.make(requireView(), "Please select a category first", Snackbar.LENGTH_SHORT).show();
            }
        });

        View.OnClickListener filterListener = v -> {
            resetCategorySelection();
            TextView clicked = (TextView) v;
            clicked.setSelected(true);
            selectedCategory = clicked.getText().toString();

            spaceList.clear();
            adapter.submitList(new ArrayList<>(spaceList));

            lastVisible = null;
            isLastPage = false;

            fetchUserLocationAndThenSpaces(true);
        };

        btnHostel.setOnClickListener(filterListener);
        btnPG.setOnClickListener(filterListener);
        btnRoom.setOnClickListener(filterListener);
        btnHouse.setOnClickListener(filterListener);
        btnFlat.setOnClickListener(filterListener);

        MobileAds.initialize(requireContext(), initializationStatus -> loadNativeAd());

        if (savedInstanceState != null) {
            selectedCategory = savedInstanceState.getString("selectedCategory");
            ArrayList<Space> savedSpaces = savedInstanceState.getParcelableArrayList("spaceList");
            if (savedSpaces != null) {
                spaceList.clear();
                spaceList.addAll(savedSpaces);
                adapter.submitList(new ArrayList<>(spaceList));
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

    @Override
    public void onResume() {
        super.onResume();
        if (selectedCategory != null && spaceList.isEmpty()) {
            fetchUserLocationAndThenSpaces(true);
        }
    }

    private void fetchUserLocationAndThenSpaces(boolean isFirstPage) {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) userLocation = location;
                    else fusedLocationClient.getLastLocation().addOnSuccessListener(last -> userLocation = last);
                    loadNearbySpacesFromFirestore(isFirstPage);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get location: " + e.getMessage());
                    loadNearbySpacesFromFirestore(isFirstPage);
                });
    }

    private void loadNearbySpacesFromFirestore(boolean isFirstPage) {
        if (selectedCategory == null) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        swipeRefresh.setRefreshing(true);
        isLoading = true;

        var query = firestore.collection("spaces")
                .whereEqualTo("active", true)
                .whereEqualTo("category", selectedCategory)
                .limit(PAGE_SIZE);

        if (lastVisible != null && !isFirstPage) {
            query = query.startAfter(lastVisible);
        }

        query.get()
                .addOnSuccessListener(snapshots -> processSpaces(snapshots, isFirstPage))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore error: " + e.getMessage());
                    swipeRefresh.setRefreshing(false);
                    isLoading = false;
                });
    }

    private void loadMoreSpaces() {
        if (lastVisible == null || isLastPage) return;
        loadNearbySpacesFromFirestore(false);
    }

    private void processSpaces(QuerySnapshot snapshots, boolean isFirstPage) {
        if (isFirstPage) spaceList.clear();

        if (snapshots == null || snapshots.isEmpty()) {
            adapter.submitList(new ArrayList<>(spaceList));
            isLastPage = true;
            swipeRefresh.setRefreshing(false);
            isLoading = false;
            return;
        }

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
                space.setDistanceMeters(distanceMeters);

                if (distanceMeters <= MAX_DISTANCE_METERS) spaceList.add(space);
            } else {
                space.setDistanceMeters(-1);
                spaceList.add(space);
            }
        }

        lastVisible = snapshots.getDocuments().get(snapshots.size() - 1);

        // Sort by distance
        spaceList.sort(Comparator.comparingInt(s ->
                (s.getDistanceMeters() != null && s.getDistanceMeters() >= 0)
                        ? s.getDistanceMeters()
                        : Integer.MAX_VALUE));

        adapter.submitList(new ArrayList<>(spaceList));

        if (snapshots.size() < PAGE_SIZE) isLastPage = true;

        swipeRefresh.setRefreshing(false);
        isLoading = false;

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

    // ===== ADAPTER =====
    private static class SpacesAdapter extends RecyclerView.Adapter<SpacesAdapter.SpaceVH> {
        private final List<Space> items = new ArrayList<>();
        private final OnSpaceClick onSpaceClick;

        public SpacesAdapter(List<Space> initial, OnSpaceClick onSpaceClick) {
            if (initial != null) items.addAll(initial);
            this.onSpaceClick = onSpaceClick;
        }

        @SuppressLint("NotifyDataSetChanged")
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

        public interface OnSpaceClick {
            void onClick(Space space);
        }

        static class SpaceVH extends RecyclerView.ViewHolder {
            TextView tvName, tvCategory, tvOwner, tvAddress, tvDistance, tvVacancy;
            CardView card;

            SpaceVH(@NonNull View itemView) {
                super(itemView);
                card = (CardView) itemView;
                tvName = itemView.findViewById(R.id.tvName);
                tvCategory = itemView.findViewById(R.id.tvCategory);
                tvOwner = itemView.findViewById(R.id.tvOwner);
                tvAddress = itemView.findViewById(R.id.tvAddress);
                tvDistance = itemView.findViewById(R.id.tvDistance);
                tvVacancy = itemView.findViewById(R.id.tvVacancy);
            }

            @SuppressLint("SetTextI18n")
            void bind(Space space, OnSpaceClick listener) {
                tvName.setText(space.getName());
                tvCategory.setText(space.getCategory());
                tvOwner.setText(space.getOwnerName());
                tvAddress.setText(space.getAddress());
                tvVacancy.setText(Boolean.TRUE.equals(space.isActive()) ? "Vacancy Available" : "Not Available");

                if (space.getDistanceMeters() != null && space.getDistanceMeters() >= 0)
                    tvDistance.setText(space.getDistanceMeters() + " m away");
                else tvDistance.setText("Distance unavailable");

                card.setOnClickListener(v -> {
                    if (listener != null) listener.onClick(space);
                });
            }
        }
    }
}
