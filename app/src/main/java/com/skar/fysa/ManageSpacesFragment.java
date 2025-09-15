package com.skar.fysa;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ManageSpacesFragment extends Fragment {

    private LinearLayout spacesListContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private DatabaseReference globalSpacesRef;

    private static final int BATCH_SIZE = 5;
    private int loadedCount = 0;
    private final List<SpaceWrapper> allSpaces = new ArrayList<>();

    private static final String PREFS_NAME = "spaces_cache";
    private static final String KEY_SPACES = "all_spaces";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_manage_spaces, container, false);

        spacesListContainer = root.findViewById(R.id.spacesListContainer);
        swipeRefreshLayout = root.findViewById(R.id.swipeRefreshLayout);

        FirebaseDatabase db = FirebaseDatabase.getInstance(
                "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/"
        );
        globalSpacesRef = db.getReference("spaces");

        // Load from cache first
        List<SpaceWrapper> cachedSpaces = getSpacesFromCache();
        if (!cachedSpaces.isEmpty()) {
            allSpaces.addAll(cachedSpaces);
            loadNextBatch(inflater);
        } else {
            fetchSpacesFromFirebase(inflater);
        }

        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchSpacesFromFirebase(inflater);
            swipeRefreshLayout.setRefreshing(false);
        });

        // Listen for edited spaces from EditSpaceFragment
        getParentFragmentManager().setFragmentResultListener("edit_space_result", this,
                (requestKey, bundle) -> {
                    Space editedSpace = (Space) bundle.getSerializable("space");
                    String category = bundle.getString("category");

                    // Update cached object
                    boolean updated = false;
                    for (SpaceWrapper wrapper : allSpaces) {
                        if (wrapper.space.getId().equals(editedSpace.getId()) &&
                                wrapper.category.equals(category)) {
                            wrapper.space = editedSpace;
                            updated = true;
                            break;
                        }
                    }
                    if (!updated) allSpaces.add(new SpaceWrapper(category, editedSpace));

                    updateCache(editedSpace, category);

                    // Update UI row
                    String tag = editedSpace.getId() + "_" + category;
                    View rowToUpdate = spacesListContainer.findViewWithTag(tag);
                    if (rowToUpdate != null) {
                        TextView txtSpaceName = rowToUpdate.findViewById(R.id.spaceName);
                        TextView tvVacancy = rowToUpdate.findViewById(R.id.vacancySwitch);

                        txtSpaceName.setText(editedSpace.getName() + " (" + editedSpace.getType() + ")");
                        if (editedSpace.isVacancy()) {
                            tvVacancy.setText("Vacancy Available: Yes");
                            tvVacancy.setTextColor(getResources().getColor(R.color.green_500, null));
                        } else {
                            tvVacancy.setText("Vacancy Available: No");
                            tvVacancy.setTextColor(getResources().getColor(R.color.red_500, null));
                        }
                    } else {
                        // Row not found (maybe not yet loaded), add it
                        addSpaceRow(getLayoutInflater(), editedSpace, category);
                    }
                });

        return root;
    }

    // ------------------- SharedPreferences helpers -------------------
    private void saveSpacesToCache(List<SpaceWrapper> spaces) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SPACES, new Gson().toJson(spaces)).apply();
    }

    private List<SpaceWrapper> getSpacesFromCache() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_SPACES, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<SpaceWrapper>>(){}.getType();
        return new Gson().fromJson(json, type);
    }

    private void updateCache(Space space, String category) {
        List<SpaceWrapper> cachedSpaces = getSpacesFromCache();
        for (SpaceWrapper wrapper : cachedSpaces) {
            if (wrapper.space.getId().equals(space.getId()) && wrapper.category.equals(category)) {
                wrapper.space = space;
                break;
            }
        }
        saveSpacesToCache(cachedSpaces);
    }

    private void deleteFromCache(Space space, String category) {
        List<SpaceWrapper> cachedSpaces = getSpacesFromCache();
        cachedSpaces.removeIf(wrapper -> wrapper.space.getId().equals(space.getId())
                && wrapper.category.equals(category));
        saveSpacesToCache(cachedSpaces);
    }

    // ------------------- Firebase Loading -------------------
    private void fetchSpacesFromFirebase(LayoutInflater inflater) {
        spacesListContainer.removeAllViews();
        loadedCount = 0;
        allSpaces.clear();

        globalSpacesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SpaceWrapper> spacesToCache = new ArrayList<>();
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    String category = categorySnapshot.getKey();
                    for (DataSnapshot spaceSnapshot : categorySnapshot.getChildren()) {
                        Space space = spaceSnapshot.getValue(Space.class);
                        if (space != null) {
                            space.setId(spaceSnapshot.getKey());
                            SpaceWrapper wrapper = new SpaceWrapper(category, space);
                            allSpaces.add(wrapper);
                            spacesToCache.add(wrapper);
                        }
                    }
                }
                saveSpacesToCache(spacesToCache);

                if (allSpaces.isEmpty()) showSnackbar(spacesListContainer, "No spaces found");
                else loadNextBatch(inflater);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showSnackbar(spacesListContainer, "Error loading spaces: " + error.getMessage());
            }
        });
    }

    private void loadNextBatch(LayoutInflater inflater) {
        int end = Math.min(loadedCount + BATCH_SIZE, allSpaces.size());

        for (int i = loadedCount; i < end; i++) {
            SpaceWrapper wrapper = allSpaces.get(i);
            addSpaceRow(inflater, wrapper.space, wrapper.category);
        }

        loadedCount = end;

        if (loadedCount < allSpaces.size()) addLoadMoreLink(inflater);
    }

    private void addLoadMoreLink(LayoutInflater inflater) {
        View existing = spacesListContainer.findViewWithTag("load_more_link");
        if (existing != null) spacesListContainer.removeView(existing);

        TextView loadMoreLink = new TextView(requireContext());
        loadMoreLink.setText("Load More");
        loadMoreLink.setTag("load_more_link");
        loadMoreLink.setTextColor(getResources().getColor(R.color.linkText, null));
        loadMoreLink.setTextSize(16f);
        loadMoreLink.setPadding(0, 24, 0, 24);
        loadMoreLink.setPaintFlags(loadMoreLink.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        loadMoreLink.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        loadMoreLink.setLayoutParams(params);

        loadMoreLink.setOnClickListener(v -> {
            spacesListContainer.removeView(v);
            loadNextBatch(inflater);
        });

        spacesListContainer.addView(loadMoreLink);
    }

    private void addSpaceRow(LayoutInflater inflater, Space space, String category) {
        View spaceRow = inflater.inflate(R.layout.item_manage_space_list, spacesListContainer, false);

        // Tag row with unique identifier
        spaceRow.setTag(space.getId() + "_" + category);

        TextView txtSpaceName = spaceRow.findViewById(R.id.spaceName);
        ImageButton btnEditSpace = spaceRow.findViewById(R.id.btnEditSpace);
        ImageButton btnDeleteSpace = spaceRow.findViewById(R.id.btnDeleteSpace);
        TextView tvVacancy = spaceRow.findViewById(R.id.vacancySwitch);

        txtSpaceName.setText(space.getName() + " (" + space.getType() + ")");
        if (space.isVacancy()) {
            tvVacancy.setText("Vacancy Available: Yes");
            tvVacancy.setTextColor(getResources().getColor(R.color.green_500, null));
        } else {
            tvVacancy.setText("Vacancy Available: No");
            tvVacancy.setTextColor(getResources().getColor(R.color.red_500, null));
        }

        btnEditSpace.setOnClickListener(v -> {
            EditSpaceFragment editFragment = new EditSpaceFragment();
            Bundle args = new Bundle();
            args.putSerializable("space", space);
            args.putString("category", category);
            editFragment.setArguments(args);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, editFragment)
                    .addToBackStack(null)
                    .commit();
        });

        btnDeleteSpace.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Delete Space")
                .setMessage("Are you sure you want to delete " + space.getName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    globalSpacesRef.child(category).child(space.getId())
                            .removeValue()
                            .addOnSuccessListener(aVoid -> {
                                showSnackbar(spaceRow, "Deleted " + space.getName());
                                allSpaces.removeIf(wrapper ->
                                        wrapper.space.getId().equals(space.getId()) &&
                                                wrapper.category.equals(category));
                                deleteFromCache(space, category);
                                spacesListContainer.removeAllViews();
                                loadedCount = 0;
                                loadNextBatch(inflater);
                            });
                })
                .setNegativeButton("No", null)
                .show());

        spacesListContainer.addView(spaceRow);
    }

    private void showSnackbar(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }

    public static class SpaceWrapper {
        public String category;
        public Space space;

        public SpaceWrapper(String category, Space space) {
            this.category = category;
            this.space = space;
        }
    }
}
