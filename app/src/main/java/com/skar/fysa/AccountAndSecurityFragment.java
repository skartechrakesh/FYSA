package com.skar.fysa;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class AccountAndSecurityFragment extends Fragment {

    private ImageButton btnEditPassword;
    private TextView tvAccountType, tvEmailVerified;
    private EditText etName, etUsername, etEmail, etContact;
    private Button btnSave, btnDeleteAccount;

    private FirebaseUser currentUser;
    private FirebaseFirestore firestore;
    private DocumentReference userDocRef;
    private String username, accountType;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account_and_security, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnEditPassword = view.findViewById(R.id.btnEditPassword);
        etName = view.findViewById(R.id.etName);
        etUsername = view.findViewById(R.id.etUsername);
        etEmail = view.findViewById(R.id.etEmail);
        etContact = view.findViewById(R.id.etContact);
        tvAccountType = view.findViewById(R.id.tvAccountType);
        tvEmailVerified = view.findViewById(R.id.tvEmailVerified);
        btnSave = view.findViewById(R.id.btnSave);
        btnDeleteAccount = view.findViewById(R.id.btnDeleteAccount);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        firestore = FirebaseFirestore.getInstance();
        userDocRef = firestore.collection("users").document(currentUser.getUid());

        loadUserData();

        tvEmailVerified.setText("Verified ✅");
        tvEmailVerified.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_500));

        btnSave.setOnClickListener(v -> saveUserData());
        btnDeleteAccount.setOnClickListener(v -> showPasswordDialogAndDelete());
    }

    private void loadUserData() {
        userDocRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) return;
            username = snapshot.getString("username");
            accountType = snapshot.getString("accountType");
            etName.setText(snapshot.getString("name"));
            etUsername.setText(username);
            etEmail.setText(snapshot.getString("email"));
            etContact.setText(snapshot.getString("contact"));
            tvAccountType.setText(accountType);
        });
    }

    private void saveUserData() {
        userDocRef.update(
                        "name", etName.getText().toString().trim(),
                        "username", etUsername.getText().toString().trim(),
                        "email", etEmail.getText().toString().trim(),
                        "contact", etContact.getText().toString().trim()
                ).addOnSuccessListener(aVoid -> showSnackbar("Saved successfully"))
                .addOnFailureListener(e -> showSnackbar("Failed to save: " + e.getMessage()));
    }

    private void showPasswordDialogAndDelete() {
        EditText passwordInput = new EditText(requireContext());
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(requireContext())
                .setTitle("Confirm Password")
                .setMessage("Please enter your password to delete your account:")
                .setView(passwordInput)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String password = passwordInput.getText().toString().trim();
                    if (password.isEmpty()) {
                        showSnackbar("Password cannot be empty");
                        return;
                    }
                    reauthenticateAndDelete(password);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reauthenticateAndDelete(String password) {
        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);
        currentUser.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> deleteAccount())
                .addOnFailureListener(e -> showSnackbar("Incorrect password: " + e.getMessage()));
    }

    private void deleteAccount() {
        userDocRef.get().addOnSuccessListener(userSnap -> {
            if (!userSnap.exists()) return;

            accountType = userSnap.getString("accountType");
            username = userSnap.getString("username");

            if ("owner".equals(accountType)) {
                deleteOwnerAccount();
            } else {
                deleteUserAccount();
            }
        });
    }

    private void deleteOwnerAccount() {
        String ownerId = currentUser.getUid();

        firestore.collection("references")
                .document("space_ref")
                .get()
                .addOnSuccessListener(spaceRefDoc -> {
                    Object spacesObj = spaceRefDoc.get(ownerId);
                    List<String> spaceIds = (spacesObj instanceof List) ? (List<String>) spacesObj : null;

                    if (spaceIds != null) {
                        for (String spaceId : spaceIds) {
                            deleteSpaceAndItsBookings(spaceId);
                        }
                    }

                    firestore.collection("references")
                            .document("space_ref")
                            .update(ownerId, FieldValue.delete());

                    finalizeUserDeletion(ownerId);
                });
    }

    private void deleteSpaceAndItsBookings(String spaceId) {
        firestore.collection("references")
                .document("space_booking_ref")
                .get()
                .addOnSuccessListener(spaceBookingDoc -> {
                    Object bookingsObj = spaceBookingDoc.get(spaceId);
                    List<String> bookingIds = (bookingsObj instanceof List) ? (List<String>) bookingsObj : null;

                    if (bookingIds != null) {
                        for (String bookingId : bookingIds) {
                            firestore.collection("bookings").document(bookingId)
                                    .get().addOnSuccessListener(bookingSnap -> {
                                        if (bookingSnap.exists()) {
                                            String bookedBy = bookingSnap.getString("bookedBy");
                                            if (bookedBy != null) {
                                                firestore.collection("references")
                                                        .document("booking_ref")
                                                        .update(bookedBy, FieldValue.arrayRemove(bookingId));
                                            }
                                            firestore.collection("bookings").document(bookingId).delete();
                                        }
                                    });
                        }
                    }

                    firestore.collection("references")
                            .document("space_booking_ref")
                            .update(spaceId, FieldValue.delete());

                    firestore.collection("spaces").document(spaceId).delete();
                });
    }

    private void deleteUserAccount() {
        String userId = currentUser.getUid();

        firestore.collection("references")
                .document("booking_ref")
                .get()
                .addOnSuccessListener(refDoc -> {
                    Object bookingsObj = refDoc.get(userId);
                    List<String> bookingIds = (bookingsObj instanceof List) ? (List<String>) bookingsObj : null;

                    if (bookingIds != null) {
                        for (String bookingId : bookingIds) {
                            firestore.collection("bookings").document(bookingId)
                                    .get().addOnSuccessListener(bookingSnap -> {
                                        if (bookingSnap.exists()) {
                                            String spaceId = bookingSnap.getString("spaceId");
                                            if (spaceId != null) {
                                                firestore.collection("references")
                                                        .document("space_booking_ref")
                                                        .update(spaceId, FieldValue.arrayRemove(bookingId));
                                            }
                                            firestore.collection("bookings").document(bookingId).delete();
                                        }
                                    });
                        }
                    }

                    firestore.collection("references")
                            .document("booking_ref")
                            .update(userId, FieldValue.delete());

                    finalizeUserDeletion(userId);
                });
    }

    private void finalizeUserDeletion(String userId) {
        userDocRef.get().addOnSuccessListener(userSnap -> {
            String username = userSnap.getString("username");

            firestore.collection("users").document(userId).delete()
                    .addOnSuccessListener(aVoid -> {
                        if (username != null && !username.isEmpty()) {
                            firestore.collection("usernames").document(username).delete();
                        }

                        currentUser.delete().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                redirectToLogin();

                                // 🔹 Cleanup empty arrays in space_ref AFTER signing out
                                cleanupEmptySpaceRef();
                                cleanupBookingRef();
                            } else {
                                showSnackbar("Failed to delete auth: " + task.getException().getMessage());
                            }
                        });
                    })
                    .addOnFailureListener(e -> showSnackbar("Failed to delete user profile: " + e.getMessage()));
        });
    }

    private void cleanupEmptySpaceRef() {
        firestore.collection("references")
                .document("space_ref")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    for (String key : doc.getData().keySet()) {
                        Object val = doc.get(key);
                        if (val instanceof List && ((List<?>) val).isEmpty()) {
                            firestore.collection("references")
                                    .document("space_ref")
                                    .update(key, FieldValue.delete());
                        }
                    }
                });
    }

    private void cleanupBookingRef() {
        firestore.collection("references")
                .document("booking_ref")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    for (String key : doc.getData().keySet()) {
                        Object val = doc.get(key);
                        if (val instanceof List && ((List<?>) val).isEmpty()) {
                            firestore.collection("references")
                                    .document("booking_ref")
                                    .update(key, FieldValue.delete());
                        }
                    }
                });
    }


    private void redirectToLogin() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showSnackbar(String msg) {
        if (getView() != null) {
            Snackbar.make(getView(), msg, Snackbar.LENGTH_LONG).show();
        }
    }
}
