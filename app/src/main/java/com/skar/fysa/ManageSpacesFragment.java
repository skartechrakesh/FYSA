package com.skar.fysa;

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

import java.util.ArrayList;
import java.util.List;

public class ManageSpacesFragment extends Fragment {

    private static final int BATCH_SIZE = 5;
    private LinearLayout spacesListContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private DatabaseReference globalSpacesRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_manage_spaces, container, false);
        spacesListContainer = root.findViewById(R.id.spacesListContainer);
        swipeRefreshLayout = root.findViewById(R.id.swipeRefreshLayout);

        globalSpacesRef = FirebaseDatabase.getInstance(
                "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("spaces");

        // Always fetch fresh data when fragment is loaded
        fetchSpacesFromFirebase(inflater);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchSpacesFromFirebase(inflater);
            swipeRefreshLayout.setRefreshing(false);
        });

        return root;
    }

    private void fetchSpacesFromFirebase(LayoutInflater inflater) {
        spacesListContainer.removeAllViews();

        globalSpacesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    showSnackbar(spacesListContainer, "No spaces found");
                    return;
                }

                List<DataSnapshot> snapshots = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) snapshots.add(snap);

                loadNextBatch(inflater, snapshots, 0);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showSnackbar(spacesListContainer, "Error loading spaces: " + error.getMessage());
            }
        });
    }

    private void loadNextBatch(LayoutInflater inflater, List<DataSnapshot> snapshots, int startIndex) {
        int endIndex = Math.min(startIndex + BATCH_SIZE, snapshots.size());

        for (int i = startIndex; i < endIndex; i++) {
            DataSnapshot snap = snapshots.get(i);
            Space space = snap.getValue(Space.class);
            if (space != null) {
                space.setId(snap.getKey());
                addSpaceRow(inflater, space);
            }
        }

        if (endIndex < snapshots.size()) {
            TextView loadMore = new TextView(requireContext());
            loadMore.setText("Load More");
            loadMore.setTextColor(getResources().getColor(R.color.linkText, null));
            loadMore.setTextSize(16f);
            loadMore.setPadding(0, 24, 0, 24);
            loadMore.setPaintFlags(loadMore.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            loadMore.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            loadMore.setLayoutParams(params);

            int nextStart = endIndex;
            loadMore.setOnClickListener(v -> {
                spacesListContainer.removeView(v);
                loadNextBatch(inflater, snapshots, nextStart);
            });

            spacesListContainer.addView(loadMore);
        }
    }

    private void addSpaceRow(LayoutInflater inflater, Space space) {
        View row = inflater.inflate(R.layout.item_manage_space_list, spacesListContainer, false);
        row.setTag(space.getId());

        TextView txtName = row.findViewById(R.id.spaceName);
        ImageButton btnEdit = row.findViewById(R.id.btnEditSpace);
        ImageButton btnDelete = row.findViewById(R.id.btnDeleteSpace);
        TextView tvVacancy = row.findViewById(R.id.vacancySwitch);

        txtName.setText(space.getName() + " (" + space.getCategory() + ")");
        if (space.isVacancy()) {
            tvVacancy.setText("Vacancy Available: Yes");
            tvVacancy.setTextColor(getResources().getColor(R.color.green_500, null));
        } else {
            tvVacancy.setText("Vacancy Available: No");
            tvVacancy.setTextColor(getResources().getColor(R.color.red_500, null));
        }

        btnEdit.setOnClickListener(v -> {
            EditSpaceFragment editFragment = new EditSpaceFragment();
            Bundle args = new Bundle();
            args.putParcelable("space", space); // ✅ use Parcelable
            editFragment.setArguments(args);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, editFragment)
                    .addToBackStack(null)
                    .commit();
        });

        btnDelete.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Delete Space")
                .setMessage("Are you sure you want to delete " + space.getName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    globalSpacesRef.child(space.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                // Remove space from user's reference
                                FirebaseDatabase.getInstance(
                                                "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/"
                                        ).getReference("users")
                                        .child(space.getOwnerId())
                                        .child("space_reference")
                                        .child(space.getId())
                                        .removeValue()
                                        .addOnCompleteListener(task -> {
                                            if (task.isSuccessful())
                                                showSnackbar(row, "Deleted " + space.getName());
                                            else
                                                showSnackbar(row, "Failed to remove space reference");

                                            // Refresh list after deletion
                                            fetchSpacesFromFirebase(inflater);
                                        });
                            });
                })
                .setNegativeButton("No", null)
                .show());

        spacesListContainer.addView(row);
    }

    private void showSnackbar(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }
}
