package com.skar.fysa;

import android.annotation.SuppressLint;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ManageSpacesFragment extends Fragment {

    private LinearLayout spacesListContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;
    private static final int BATCH_SIZE = 5;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_manage_spaces, container, false);
        spacesListContainer = root.findViewById(R.id.spacesListContainer);
        swipeRefreshLayout = root.findViewById(R.id.swipeRefreshLayout);

        firestore = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return root;

        loadOwnerSpaces(inflater);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadOwnerSpaces(inflater);
            swipeRefreshLayout.setRefreshing(false);
        });

        return root;
    }

    private void loadOwnerSpaces(LayoutInflater inflater) {
        spacesListContainer.removeAllViews();

        firestore.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(userSnap -> {
                    if (!userSnap.exists()) return;

                    Map<String, Object> spaceRefs = (Map<String, Object>) userSnap.get("space_reference");
                    if (spaceRefs == null || spaceRefs.isEmpty()) {
                        showSnackbar(spacesListContainer, "No Spaces Found");
                        return;
                    }

                    List<String> spaceIds = new ArrayList<>(spaceRefs.keySet());
                    loadSpacesInBatches(inflater, spaceIds, 0);
                })
                .addOnFailureListener(e ->
                        showSnackbar(spacesListContainer, "Failed to load spaces: " + e.getMessage()));
    }

    private void loadSpacesInBatches(LayoutInflater inflater, List<String> spaceIds, int startIndex) {
        int endIndex = Math.min(startIndex + BATCH_SIZE, spaceIds.size());

        for (int i = startIndex; i < endIndex; i++) {
            String spaceId = spaceIds.get(i);
            firestore.collection("spaces")
                    .document(spaceId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Space space = doc.toObject(Space.class);
                            if (space != null) {
                                space.setId(doc.getId());
                                addSpaceRow(inflater, space);
                            }
                        }
                    })
                    .addOnFailureListener(e ->
                            showSnackbar(spacesListContainer, "Failed to load space: " + e.getMessage()));
        }

        if (endIndex < spaceIds.size()) {
            TextView loadMore = new TextView(requireContext());
            loadMore.setText("Load More");
            loadMore.setTextColor(getResources().getColor(R.color.linkText, null));
            loadMore.setTextSize(16f);
            loadMore.setPadding(0, 24, 0, 24);
            loadMore.setPaintFlags(loadMore.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            loadMore.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

            int nextStart = endIndex;
            loadMore.setOnClickListener(v -> {
                spacesListContainer.removeView(v);
                loadSpacesInBatches(inflater, spaceIds, nextStart);
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
        if (space.isActive()) {
            tvVacancy.setText("Vacancy Available: Yes");
            tvVacancy.setTextColor(getResources().getColor(R.color.green_500, null));
        } else {
            tvVacancy.setText("Vacancy Available: No");
            tvVacancy.setTextColor(getResources().getColor(R.color.red_500, null));
        }

        btnEdit.setOnClickListener(v -> {
            EditSpaceFragment editFragment = new EditSpaceFragment();
            Bundle args = new Bundle();
            args.putParcelable("space", space);
            editFragment.setArguments(args);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, editFragment)
                    .addToBackStack(null)
                    .commit();
        });

        btnDelete.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Delete Space")
                .setMessage("Are you sure you want to delete " + space.getName() + " and all its bookings?")
                .setPositiveButton("Yes", (dialog, which) -> deleteSpaceWithBookings(space, row))
                .setNegativeButton("No", null)
                .show());

        spacesListContainer.addView(row);
    }

    private void deleteSpaceWithBookings(Space space, View row) {
        // Delete all bookings first
        firestore.collection("spaces")
                .document(space.getId())
                .collection("bookings")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        var batch = firestore.batch();
                        for (DocumentSnapshot bookingSnap : querySnapshot.getDocuments()) {
                            batch.delete(bookingSnap.getReference());
                        }
                        batch.commit()
                                .addOnSuccessListener(aVoid -> deleteSpaceDoc(space, row))
                                .addOnFailureListener(e ->
                                        showSnackbar(row, "Failed to delete bookings: " + e.getMessage()));
                    } else {
                        // No bookings, delete space directly
                        deleteSpaceDoc(space, row);
                    }
                })
                .addOnFailureListener(e ->
                        showSnackbar(row, "Failed to fetch bookings: " + e.getMessage()));
    }

    private void deleteSpaceDoc(Space space, View row) {
        firestore.collection("spaces")
                .document(space.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Remove from owner's space_reference correctly
                    firestore.collection("users")
                            .document(currentUser.getUid())
                            .update("space_reference." + space.getId(), FieldValue.delete())
                            .addOnFailureListener(e ->
                                    showSnackbar(row, "Failed to remove space reference: " + e.getMessage()));

                    showSnackbar(row, "Deleted " + space.getName() + " and its bookings");

                    // Refresh the fragment list
                    if (getView() != null) {
                        loadOwnerSpaces(LayoutInflater.from(requireContext()));
                    }
                })
                .addOnFailureListener(e ->
                        showSnackbar(row, "Failed to delete space: " + e.getMessage()));
    }

    private void showSnackbar(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }
}
