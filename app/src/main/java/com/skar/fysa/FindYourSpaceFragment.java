package com.skar.fysa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FindYourSpaceFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final double MAX_DISTANCE_METERS = 5000; // 5 km
    private static final String TAG = "FindYourSpaceFragment";

    private RecyclerView recyclerView;
    private SpacesAdapter adapter;
    private final List<Space> spaceList = new ArrayList<>();
    private Location userLocation;

    private CheckBox cbHostel, cbPG, cbRoom, cbHouse, cbFlat;
    private SwipeRefreshLayout swipeRefresh;

    private final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_find_your_space, container, false);

        recyclerView = view.findViewById(R.id.recyclerSpaces);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::reloadForCurrentFilters);

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

        cbHostel = view.findViewById(R.id.cbHostel);
        cbPG     = view.findViewById(R.id.cbPG);
        cbRoom   = view.findViewById(R.id.cbRoom);
        cbHouse  = view.findViewById(R.id.cbHouse);
        cbFlat   = view.findViewById(R.id.cbFlat);

        View.OnClickListener filterListener = v -> reloadForCurrentFilters();
        cbHostel.setOnClickListener(filterListener);
        cbPG.setOnClickListener(filterListener);
        cbRoom.setOnClickListener(filterListener);
        cbHouse.setOnClickListener(filterListener);
        cbFlat.setOnClickListener(filterListener);

        checkLocationAndLoad();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadForCurrentFilters();
    }

    private void checkLocationAndLoad() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST);
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

            if (last != null) {
                userLocation = last;
                Log.d(TAG, "User location: " + userLocation.getLatitude() + ", " + userLocation.getLongitude());
            }
        } catch (SecurityException e) {
            Toast.makeText(requireContext(), "Location permission missing", Toast.LENGTH_SHORT).show();
        } finally {
            reloadForCurrentFilters();
        }
    }

    private void reloadForCurrentFilters() {
        swipeRefresh.setRefreshing(true);

        List<String> selected = getSelectedCategories();
        if (selected.isEmpty()) {
            spaceList.clear();
            adapter.submitList(new ArrayList<>(spaceList));
            swipeRefresh.setRefreshing(false);
            return;
        }

        loadNearbySpacesFromFirebase(selected);
    }

    private List<String> getSelectedCategories() {
        List<String> res = new ArrayList<>();
        if (cbHostel != null && cbHostel.isChecked()) res.add("Hostel");
        if (cbPG     != null && cbPG.isChecked())     res.add("PG");
        if (cbRoom   != null && cbRoom.isChecked())   res.add("Room");
        if (cbHouse  != null && cbHouse.isChecked())  res.add("House");
        if (cbFlat   != null && cbFlat.isChecked())   res.add("Flat");
        return res;
    }

    private void loadNearbySpacesFromFirebase(List<String> categories) {
        DatabaseReference spacesRef = FirebaseDatabase.getInstance(DB_URL).getReference("spaces");
        List<Space> merged = new ArrayList<>();
        final int[] remaining = {categories.size()};

        for (String category : categories) {
            spacesRef.child(category).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot spaceSnapshot : snapshot.getChildren()) {
                        Space space = spaceSnapshot.getValue(Space.class);
                        if (space == null) continue;

                        // ✅ Only load spaces with vacancy == true
                        if (!Boolean.TRUE.equals(space.isVacancy())) {
                            continue;
                        }

                        if (!spaceSnapshot.hasChild("latitude") || !spaceSnapshot.hasChild("longitude"))
                            continue;

                        if (space.getId() == null || space.getId().isEmpty()) {
                            space.setId(spaceSnapshot.getKey());
                        }

                        space.setType(category);

                        if (userLocation != null) {
                            float[] results = new float[1];
                            Location.distanceBetween(
                                    userLocation.getLatitude(),
                                    userLocation.getLongitude(),
                                    space.getLatitude(),
                                    space.getLongitude(),
                                    results
                            );
                            float distanceMeters = results[0];
                            if (distanceMeters <= MAX_DISTANCE_METERS) {
                                space.setDistanceMeters((int) distanceMeters);
                                merged.add(space);
                            }
                        } else {
                            merged.add(space);
                        }
                    }

                    remaining[0]--;
                    if (remaining[0] == 0) {
                        // ✅ Sort by distance if available
                        Collections.sort(merged, Comparator.comparingInt(space ->
                                space.getDistanceMeters() != null ? space.getDistanceMeters() : Integer.MAX_VALUE
                        ));

                        spaceList.clear();
                        spaceList.addAll(merged);
                        adapter.submitList(new ArrayList<>(spaceList));
                        swipeRefresh.setRefreshing(false);

                        if (spaceList.isEmpty()) {
                            Toast.makeText(requireContext(),
                                    "No nearby spaces found", Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Firebase read cancelled: " + error.getMessage());
                    Toast.makeText(requireContext(),
                            "Failed to load spaces: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    remaining[0]--;
                    if (remaining[0] == 0) swipeRefresh.setRefreshing(false);
                }
            });
        }
    }
}
