package com.skar.fysa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AccountAndSecurityFragment extends Fragment {

    private ImageView imgProfile;
    private ImageButton btnEditPassword;
    private EditText etName, etUsername, etEmail, etContact;
    private TextView tvAccountType, tvEmailVerified;
    private Button btnSave, btnVerifyEmail, btnDeleteAccount;

    private DatabaseReference userRef;
    private FirebaseUser currentUser;
    private Uri selectedImageUri;
    private String username, accountType;

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private InterstitialAd mInterstitialAd;

    // Image picker launcher
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            selectedImageUri = result.getData().getData();
                            Glide.with(this).load(selectedImageUri).into(imgProfile);
                        }
                    });

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

        userRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(currentUser.getUid());

        // Load initial data
        loadUserData();
        updateVerificationUI();
        loadInterstitialAd();

        // Listeners
        btnEditPassword.setOnClickListener(v -> showPasswordChangeDialog());
        btnSave.setOnClickListener(v -> {
            saveUserData();
            showInterstitialAd();
        });
        btnVerifyEmail.setOnClickListener(v -> sendVerificationEmail());
        btnDeleteAccount.setOnClickListener(v -> handleDeleteAccount());

        imgProfile.setOnClickListener(v -> pickImage());
    }

    // Open gallery to pick profile image
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    // Load interstitial ad
    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(requireContext(),
                "ca-app-pub-3940256099942544/1033173712", // test id
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

    // Load user data from Firebase
    private void loadUserData() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                username = snapshot.child("username").getValue(String.class);
                accountType = snapshot.child("accountType").getValue(String.class);
                etName.setText(snapshot.child("name").getValue(String.class));
                etUsername.setText(username);
                etEmail.setText(snapshot.child("email").getValue(String.class));
                etContact.setText(snapshot.child("contact").getValue(String.class));
                tvAccountType.setText(String.format(Locale.getDefault(), "%s", accountType));
                String imageUrl = snapshot.child("profileImage").getValue(String.class);
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Glide.with(AccountAndSecurityFragment.this).load(imageUrl).into(imgProfile);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showSnackbar("Failed to load user data.");
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
            if (task.isSuccessful()) {
                showSnackbar("Verification email sent.");
            }
        });
    }

    // Save updates to Firebase
    private void saveUserData() {
        userRef.child("name").setValue(etName.getText().toString().trim());
        userRef.child("username").setValue(etUsername.getText().toString().trim());
        userRef.child("email").setValue(etEmail.getText().toString().trim());
        userRef.child("contact").setValue(etContact.getText().toString().trim());
        showSnackbar("Saved successfully");
    }

    // Change password dialog
    private void showPasswordChangeDialog() {
        if (currentUser == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Change Password");
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null);
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
                            .addOnFailureListener(e -> showSnackbar("Failed to update password: " + e.getMessage())))
                    .addOnFailureListener(e -> showSnackbar("Re-authentication failed: " + e.getMessage()));
        });
        builder.setNegativeButton("Cancel", (d, w) -> d.dismiss());
        builder.show();
    }

    // Handle account deletion
    private void handleDeleteAccount() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action is irreversible and will delete all your data.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (currentUser == null) return;

                    boolean isGoogleUser = false;
                    for (com.google.firebase.auth.UserInfo profile : currentUser.getProviderData()) {
                        if (profile.getProviderId().equals(GoogleAuthProvider.PROVIDER_ID)) {
                            isGoogleUser = true;
                            break;
                        }
                    }

                    if (isGoogleUser) {
                        deleteAllUserData();
                    } else {
                        showReauthDialog();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showReauthDialog() {
        AlertDialog.Builder reauthBuilder = new AlertDialog.Builder(requireContext());
        reauthBuilder.setTitle("Re-authenticate");
        reauthBuilder.setMessage("Please enter your current password to confirm deletion.");
        final EditText passwordInput = new EditText(requireContext());
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        reauthBuilder.setView(passwordInput);
        reauthBuilder.setPositiveButton("Confirm", (reauthDialog, reauthWhich) -> {
            String password = passwordInput.getText().toString().trim();
            if (password.isEmpty()) {
                showSnackbar("Password cannot be empty.");
                return;
            }
            AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);
            currentUser.reauthenticate(credential)
                    .addOnSuccessListener(unused -> deleteAllUserData())
                    .addOnFailureListener(e -> showSnackbar("Re-authentication failed: " + e.getMessage()));
        }).setNegativeButton("Cancel", (reauthDialog, reauthWhich) -> reauthDialog.dismiss());
        reauthBuilder.show();
    }

    // Delete all user-related data
    private void deleteAllUserData() {
        String currentUserId = currentUser.getUid();
        List<DatabaseReference> referencesToDelete = new ArrayList<>();

        referencesToDelete.add(FirebaseDatabase.getInstance(DB_URL).getReference("users").child(currentUserId));

        if (username != null && !username.isEmpty()) {
            referencesToDelete.add(FirebaseDatabase.getInstance(DB_URL).getReference("usernames").child(username));
        }

        if ("Owner".equalsIgnoreCase(accountType)) {
            DatabaseReference spacesRef = FirebaseDatabase.getInstance(DB_URL).getReference("spaces");
            spacesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                        for (DataSnapshot spaceSnapshot : categorySnapshot.getChildren()) {
                            Space space = spaceSnapshot.getValue(Space.class);
                            if (space != null && space.getOwnerId().equals(currentUserId)) {
                                referencesToDelete.add(spaceSnapshot.getRef());
                            }
                        }
                    }
                    referencesToDelete.add(FirebaseDatabase.getInstance(DB_URL).getReference("notifications").child(currentUserId));
                    executeDeletions(referencesToDelete);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    showSnackbar("Failed to read spaces for deletion: " + error.getMessage());
                }
            });
        } else {
            DatabaseReference allNotificationsRef = FirebaseDatabase.getInstance(DB_URL).getReference("notifications");
            allNotificationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot ownerSnapshot : snapshot.getChildren()) {
                        for (DataSnapshot notificationSnapshot : ownerSnapshot.getChildren()) {
                            NotificationModel notification = notificationSnapshot.getValue(NotificationModel.class);
                            if (notification != null && notification.getUserId().equals(currentUserId)) {
                                referencesToDelete.add(notificationSnapshot.getRef());
                            }
                        }
                    }
                    executeDeletions(referencesToDelete);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    showSnackbar("Failed to read notifications for deletion: " + error.getMessage());
                }
            });
        }
    }

    // Execute all deletion tasks
    private void executeDeletions(List<DatabaseReference> refs) {
        List<Task<Void>> tasks = new ArrayList<>();
        for (DatabaseReference ref : refs) {
            tasks.add(ref.removeValue());
        }

        Tasks.whenAll(tasks)
                .addOnSuccessListener(aVoid -> {
                    currentUser.delete()
                            .addOnSuccessListener(aVoid1 -> {
                                showSnackbar("Account and all data deleted successfully.");
                                Intent intent = new Intent(requireActivity(), LoginActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                requireActivity().finish();
                            })
                            .addOnFailureListener(e -> showSnackbar("Failed to delete user auth: " + e.getMessage()));
                })
                .addOnFailureListener(e -> showSnackbar("Failed to delete account data: " + e.getMessage()));
    }

    private void showSnackbar(String message) {
        if (getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}
