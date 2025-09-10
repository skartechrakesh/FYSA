package com.skar.fysa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ManageSpacesFragment extends Fragment {

    private LinearLayout spacesListContainer;
    private DatabaseReference userSpacesRef, globalSpacesRef;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_manage_spaces, container, false);
        spacesListContainer = root.findViewById(R.id.spacesListContainer);
        swipeRefreshLayout = root.findViewById(R.id.swipeRefreshLayout);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

            FirebaseDatabase db = FirebaseDatabase.getInstance(
                    "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/"
            );

            userSpacesRef = db.getReference("users").child(currentUserId).child("space_details");
            globalSpacesRef = db.getReference("spaces");

            loadSpaces(inflater);
        } else {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
        }

        // Pull-to-refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadSpaces(inflater);
            swipeRefreshLayout.setRefreshing(false);
        });

        return root;
    }

    private void loadSpaces(LayoutInflater inflater) {
        spacesListContainer.removeAllViews();

        userSpacesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean hasSpaces = false;

                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    String category = categorySnapshot.getKey();

                    for (DataSnapshot spaceSnapshot : categorySnapshot.getChildren()) {
                        Space space = spaceSnapshot.getValue(Space.class);
                        if (space != null) {
                            hasSpaces = true;
                            space.setId(spaceSnapshot.getKey());
                            addSpaceRow(inflater, space, spaceSnapshot, category);
                        }
                    }
                }

                if (!hasSpaces) {
                    Toast.makeText(getContext(), "No spaces found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(),
                        "Error loading spaces: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addSpaceRow(LayoutInflater inflater, Space space, DataSnapshot spaceSnapshot, String category) {
        View spaceRow = inflater.inflate(R.layout.item_manage_space, spacesListContainer, false);

        TextView txtSpaceName = spaceRow.findViewById(R.id.spaceName);
        ImageButton btnEditSpace = spaceRow.findViewById(R.id.btnEditSpace);
        ImageButton btnDeleteSpace = spaceRow.findViewById(R.id.btnDeleteSpace);
        SwitchCompat vacancySwitch = spaceRow.findViewById(R.id.vacancySwitch);

        txtSpaceName.setText(space.getName() + " (" + space.getType() + ")");

        // Load vacancy state
        vacancySwitch.setChecked(space.isVacancy());

        // Handle toggle changes
        vacancySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("vacancy", isChecked);

            // Update in user's node
            userSpacesRef.child(category).child(space.getId()).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Update in global node
                        globalSpacesRef.child(category).child(space.getId()).updateChildren(updates)
                                .addOnSuccessListener(aVoid2 ->
                                        Toast.makeText(getContext(),
                                                "Vacancy updated: " + (isChecked ? "Yes" : "No"),
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(),
                                                "Global update failed: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show());
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(),
                                    "Update failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
        });

        // Edit button
        btnEditSpace.setOnClickListener(v -> {
            EditSpaceFragment editFragment = new EditSpaceFragment();
            Bundle args = new Bundle();
            args.putSerializable("space", space);
            editFragment.setArguments(args);

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, editFragment)
                    .addToBackStack(null)
                    .commit();
        });

        // Delete button
        btnDeleteSpace.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Delete Space")
                .setMessage("Are you sure you want to delete " + space.getName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> spaceSnapshot.getRef().removeValue()
                        .addOnSuccessListener(aVoid -> globalSpacesRef.child(category).child(space.getId())
                                .removeValue()
                                .addOnSuccessListener(aVoid2 -> {
                                    Toast.makeText(getContext(),
                                            "Deleted " + space.getName(),
                                            Toast.LENGTH_SHORT).show();
                                    loadSpaces(inflater);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(),
                                                "Failed global delete: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show()))
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(),
                                        "Failed to delete: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show()))
                .setNegativeButton("No", null)
                .show());

        spacesListContainer.addView(spaceRow);
    }
}
