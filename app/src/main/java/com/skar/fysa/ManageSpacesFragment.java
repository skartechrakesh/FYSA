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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ManageSpacesFragment extends Fragment {

    private LinearLayout spacesListContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

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

        firestore.collection("references")
                .document("space_ref")
                .get()
                .addOnSuccessListener(refSnap -> {
                    if (!refSnap.exists()) {
                        showSnackbar(spacesListContainer, "No Spaces Found");
                        return;
                    }

                    Map<String, Object> allRefs = refSnap.getData();
                    if (allRefs == null || allRefs.isEmpty() || !allRefs.containsKey(currentUser.getUid())) {
                        showSnackbar(spacesListContainer, "No Spaces Found");
                        return;
                    }

                    Object refValue = allRefs.get(currentUser.getUid());
                    if (!(refValue instanceof List)) {
                        showSnackbar(spacesListContainer, "Invalid reference format");
                        return;
                    }

                    List<?> spaceIds = (List<?>) refValue;
                    if (spaceIds.isEmpty()) {
                        showSnackbar(spacesListContainer, "No Spaces Found");
                        return;
                    }

                    for (Object idObj : spaceIds) {
                        if (!(idObj instanceof String)) continue;
                        String spaceId = (String) idObj;

                        firestore.collection("spaces").document(spaceId)
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
                })
                .addOnFailureListener(e ->
                        showSnackbar(spacesListContainer, "Failed to load references: " + e.getMessage()));
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
//                    .addToBackStack(null)
                    .commit();
        });

        btnDelete.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Delete Space")
                .setMessage("Are you sure you want to delete " + space.getName() + " and all its bookings?")
                .setPositiveButton("Yes", (dialog, which) -> deleteSpace(space, row))
                .setNegativeButton("No", null)
                .show());

        spacesListContainer.addView(row);
    }

    private void deleteSpace(Space space, View row) {
        String spaceId = space.getId();
        String ownerId = currentUser.getUid();

        firestore.collection("references")
                .document("space_booking_ref")
                .get()
                .addOnSuccessListener(spaceDoc -> {
                    Object bookingsObj = spaceDoc.get(spaceId);
                    List<String> bookingIds = (bookingsObj instanceof List) ? (List<String>) bookingsObj : new ArrayList<>();

                    if (bookingIds.isEmpty()) {
                        deleteSpaceDocument(spaceId, ownerId, row);
                        return;
                    }

                    deleteBookingsRecursively(bookingIds, 0, spaceId, ownerId, row);
                })
                .addOnFailureListener(e ->
                        showSnackbar(row, "Failed to fetch space bookings: " + e.getMessage()));
    }

    private void deleteBookingsRecursively(List<String> bookingIds, int index, String spaceId, String ownerId, View row) {
        if (index >= bookingIds.size()) {
            deleteSpaceDocument(spaceId, ownerId, row);
            return;
        }

        String bookingId = bookingIds.get(index);
        firestore.collection("bookings").document(bookingId)
                .get()
                .addOnSuccessListener(bookingSnap -> {
                    if (bookingSnap.exists()) {
                        String bookedBy = bookingSnap.getString("bookedBy");

                        if (bookedBy != null) {
                            firestore.collection("references")
                                    .document("booking_ref")
                                    .get()
                                    .addOnSuccessListener(refSnap -> {
                                        if (refSnap.exists() && refSnap.contains(bookedBy)) {
                                            List<String> userBookings = (List<String>) refSnap.get(bookedBy);
                                            if (userBookings != null && userBookings.contains(bookingId)) {
                                                userBookings.remove(bookingId);
                                                if (userBookings.isEmpty()) {
                                                    firestore.collection("references")
                                                            .document("booking_ref")
                                                            .update(bookedBy, FieldValue.delete());
                                                } else {
                                                    firestore.collection("references")
                                                            .document("booking_ref")
                                                            .update(bookedBy, userBookings);
                                                }
                                            }
                                        }

                                        firestore.collection("bookings").document(bookingId)
                                                .delete()
                                                .addOnSuccessListener(aVoid ->
                                                        deleteBookingsRecursively(bookingIds, index + 1, spaceId, ownerId, row))
                                                .addOnFailureListener(e ->
                                                        showSnackbar(row, "Failed to delete booking: " + e.getMessage()));
                                    });
                        } else {
                            firestore.collection("bookings").document(bookingId)
                                    .delete()
                                    .addOnSuccessListener(aVoid ->
                                            deleteBookingsRecursively(bookingIds, index + 1, spaceId, ownerId, row))
                                    .addOnFailureListener(e ->
                                            showSnackbar(row, "Failed to delete booking: " + e.getMessage()));
                        }
                    } else {
                        deleteBookingsRecursively(bookingIds, index + 1, spaceId, ownerId, row);
                    }
                })
                .addOnFailureListener(e ->
                        showSnackbar(row, "Failed to fetch booking: " + e.getMessage()));
    }

    private void deleteSpaceDocument(String spaceId, String ownerId, View row) {
        WriteBatch batch = firestore.batch();

        batch.delete(firestore.collection("spaces").document(spaceId));
        batch.update(firestore.collection("references")
                .document("space_ref"), ownerId, FieldValue.arrayRemove(spaceId));
        batch.update(firestore.collection("references")
                .document("space_booking_ref"), spaceId, FieldValue.delete());

        // Commit the batch
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    // After batch, check if owner array in space_ref is empty
                    firestore.collection("references").document("space_ref")
                            .get()
                            .addOnSuccessListener(docSnap -> {
                                if (docSnap.exists()) {
                                    Object ownerSpaces = docSnap.get(ownerId);
                                    if (ownerSpaces instanceof List) {
                                        List<?> spaceIds = (List<?>) ownerSpaces;
                                        if (spaceIds.isEmpty()) {
                                            firestore.collection("references")
                                                    .document("space_ref")
                                                    .update(ownerId, FieldValue.delete());
                                        }
                                    }
                                }
                            });

                    showSnackbar(row, "Deleted space and all its bookings");
                    loadOwnerSpaces(LayoutInflater.from(requireContext()));
                })
                .addOnFailureListener(e ->
                        showSnackbar(row, "Failed to delete space: " + e.getMessage()));
    }

    private void showSnackbar(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }
}
