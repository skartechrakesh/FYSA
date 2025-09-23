package com.skar.fysa;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AccountAndSecurityFragment extends Fragment {

    private ImageView imgProfile;
    private ImageButton btnEditPassword;
    private EditText etName, etUsername, etEmail, etContact;
    private TextView tvAccountType, tvEmailVerified;
    private Button btnSave, btnVerifyEmail, btnDeleteAccount;

    private FirebaseUser currentUser;
    private FirebaseFirestore firestore;
    private DocumentReference userDocRef;
    private String username, accountType;
    private InterstitialAd mInterstitialAd;

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

        imgProfile = view.findViewById(R.id.imgProfile);
        btnEditPassword = view.findViewById(R.id.btnEditPassword);
        etName = view.findViewById(R.id.etName);
        etUsername = view.findViewById(R.id.etUsername);
        etEmail = view.findViewById(R.id.etEmail);
        etContact = view.findViewById(R.id.etContact);
        tvAccountType = view.findViewById(R.id.tvAccountType);
        btnSave = view.findViewById(R.id.btnSave);
        btnVerifyEmail = view.findViewById(R.id.btnVerifyEmail);
        tvEmailVerified = view.findViewById(R.id.tvEmailVerified);
        btnDeleteAccount = view.findViewById(R.id.btnDeleteAccount);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        firestore = FirebaseFirestore.getInstance();
        userDocRef = firestore.collection("users").document(currentUser.getUid());

        loadUserData();
        updateVerificationUI();
        loadInterstitialAd();

        btnEditPassword.setOnClickListener(v -> showPasswordChangeDialog());
        btnSave.setOnClickListener(v -> {
            saveUserData();
            showInterstitialAd();
        });
        btnVerifyEmail.setOnClickListener(v -> sendVerificationEmail());
        btnDeleteAccount.setOnClickListener(v -> promptPasswordBeforeDelete());
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(requireContext(),
                "ca-app-pub-3940256099942544/1033173712", // Original Unit ID: "ca-app-pub-6462351900381242/5824644804"
                adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mInterstitialAd = null;
                    }
                });
    }

    private void showInterstitialAd() {
        if (mInterstitialAd != null) {
            mInterstitialAd.show(requireActivity());
            mInterstitialAd = null;
            loadInterstitialAd();
        }
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
            tvAccountType.setText(String.format(Locale.getDefault(), "%s", accountType));

            String imageUrl = snapshot.getString("profileImage");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(AccountAndSecurityFragment.this).load(imageUrl).into(imgProfile);
            }
        });
    }

    private void updateVerificationUI() {
        if (currentUser == null) return;
        if (currentUser.isEmailVerified()) {
            tvEmailVerified.setText("Verified ✅");
            tvEmailVerified.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_500));
            btnVerifyEmail.setVisibility(View.GONE);
        } else {
            tvEmailVerified.setText("Not Verified ❌");
            tvEmailVerified.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_500));
            btnVerifyEmail.setVisibility(View.VISIBLE);
        }
    }

    private void sendVerificationEmail() {
        if (currentUser == null) return;
        currentUser.sendEmailVerification().addOnCompleteListener(task -> {
            if (task.isSuccessful()) showSnackbar("Verification email sent.");
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

    private void showPasswordChangeDialog() {
        if (currentUser == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Change Password");
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null);
        EditText etCurrentPassword = dialogView.findViewById(R.id.etCurrentPassword);
        EditText etNewPassword = dialogView.findViewById(R.id.etNewPassword);
        etCurrentPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etNewPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(dialogView);
        builder.setPositiveButton("Change", (d, w) -> {
            String currentPass = etCurrentPassword.getText().toString().trim();
            String newPass = etNewPassword.getText().toString().trim();
            if (currentPass.isEmpty() || newPass.isEmpty()) {
                showSnackbar("Fill all fields");
                return;
            }
            AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPass);
            currentUser.reauthenticate(credential)
                    .addOnSuccessListener(unused -> currentUser.updatePassword(newPass)
                            .addOnSuccessListener(aVoid -> showSnackbar("Password updated"))
                            .addOnFailureListener(e -> showSnackbar("Failed: " + e.getMessage())))
                    .addOnFailureListener(e -> showSnackbar("Re-auth failed: " + e.getMessage()));
        });
        builder.setNegativeButton("Cancel", (d, w) -> d.dismiss());
        builder.show();
    }

    // ---------------------- DELETE ACCOUNT ----------------------
    private void promptPasswordBeforeDelete() {
        if (currentUser == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Confirm Password");
        builder.setMessage("Please enter your password to delete your account.");

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_confirm_password, null);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(dialogView);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            String password = etPassword.getText().toString().trim();
            if (password.isEmpty()) {
                showSnackbar("Password required!");
                return;
            }
            reauthenticateAndDelete(password);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void reauthenticateAndDelete(String password) {
        if (currentUser == null) return;
        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);
        currentUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> deleteUserDataAndAuth())
                .addOnFailureListener(e -> showSnackbar("Re-auth failed: " + e.getMessage()));
    }

    private void deleteUserDataAndAuth() {
        if (currentUser == null) return;
        String uid = currentUser.getUid();
        userDocRef.get().addOnSuccessListener(snapshot -> {
            String type = snapshot.getString("accountType");
            if ("Owner".equalsIgnoreCase(type)) {
                deleteOwnerAccountData();
            } else {
                deleteNormalUserData();
            }
        });
    }

    private void deleteNormalUserData() {
        String uid = currentUser.getUid();
        CollectionReference spacesCol = firestore.collection("spaces");

        spacesCol.get().addOnSuccessListener(snapshot -> {
            List<Task<Void>> deleteTasks = new ArrayList<>();
            for (DocumentSnapshot spaceSnap : snapshot.getDocuments()) {
                Task<Void> t = spaceSnap.getReference().collection("bookings")
                        .whereEqualTo("userId", uid).get()
                        .continueWithTask(task -> {
                            List<Task<Void>> bookingDeletes = new ArrayList<>();
                            if (task.isSuccessful() && task.getResult() != null) {
                                for (DocumentSnapshot bookingSnap : task.getResult().getDocuments()) {
                                    bookingDeletes.add(bookingSnap.getReference().delete());
                                }
                            }
                            return Tasks.whenAll(bookingDeletes);
                        });
                deleteTasks.add(t);
            }

            if (username != null && !username.isEmpty()) {
                deleteTasks.add(firestore.collection("usernames").document(username).delete());
            }
            deleteTasks.add(userDocRef.delete());

            Tasks.whenAll(deleteTasks).addOnSuccessListener(aVoid -> deleteAuthAccount())
                    .addOnFailureListener(e -> showSnackbar("Failed to delete user data: " + e.getMessage()));
        });
    }

    private void deleteOwnerAccountData() {
        userDocRef.get().addOnSuccessListener(snapshot -> {
            Object spaceRefObj = snapshot.get("space_reference");
            if (!(spaceRefObj instanceof Map)) {
                deleteAuthAccount();
                return;
            }

            Map<String, Object> spaceMap = (Map<String, Object>) spaceRefObj;
            if (spaceMap.isEmpty()) {
                deleteAuthAccount();
                return;
            }

            List<Task<Void>> spaceTasks = new ArrayList<>();

            for (String spaceId : spaceMap.keySet()) {
                DocumentReference spaceRef = firestore.collection("spaces").document(spaceId);

                Task<Void> deleteBookingsTask = spaceRef.collection("bookings").get()
                        .continueWithTask(bookingsTask -> {
                            List<Task<Void>> bookingDeletes = new ArrayList<>();
                            if (bookingsTask.isSuccessful() && bookingsTask.getResult() != null) {
                                for (DocumentSnapshot bookingSnap : bookingsTask.getResult().getDocuments()) {
                                    String bookingId = bookingSnap.getId();
                                    String userId = bookingSnap.getString("userId");
                                    bookingDeletes.add(bookingSnap.getReference().delete());

                                    if (userId != null) {
                                        DocumentReference bookingRef = firestore
                                                .collection("users")
                                                .document(userId)
                                                .collection("booking_reference")
                                                .document(bookingId);
                                        bookingDeletes.add(bookingRef.delete());
                                    }
                                }
                            }
                            return Tasks.whenAll(bookingDeletes);
                        }).continueWithTask(t -> spaceRef.delete());

                Task<Void> deleteSpaceRefTask = userDocRef.collection("space_reference")
                        .document(spaceId)
                        .delete();

                spaceTasks.add(Tasks.whenAll(deleteBookingsTask, deleteSpaceRefTask));
            }

            Tasks.whenAll(spaceTasks).addOnSuccessListener(aVoid -> {
                List<Task<Void>> finalTasks = new ArrayList<>();
                if (username != null && !username.isEmpty()) {
                    finalTasks.add(firestore.collection("usernames").document(username).delete());
                }
                finalTasks.add(userDocRef.delete());

                Tasks.whenAll(finalTasks).addOnSuccessListener(aVoid2 -> deleteAuthAccount())
                        .addOnFailureListener(e -> showSnackbar("Failed final cleanup: " + e.getMessage()));
            }).addOnFailureListener(e -> showSnackbar("Failed to delete spaces: " + e.getMessage()));
        }).addOnFailureListener(e -> showSnackbar("Failed to load user document: " + e.getMessage()));
    }

    private void deleteAuthAccount() {
        if (currentUser == null) return;
        currentUser.delete()
                .addOnSuccessListener(aVoid -> {
                    showSnackbar("Account deleted successfully");
                    Intent intent = new Intent(requireActivity(), LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .addOnFailureListener(e -> showSnackbar("Failed to delete auth account: " + e.getMessage()));
    }

    private void showSnackbar(String msg) {
        if (getView() != null) {
            Snackbar.make(getView(), msg, Snackbar.LENGTH_LONG).show();
        }
    }
}
