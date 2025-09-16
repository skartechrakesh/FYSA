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
import android.widget.ImageView;
import android.widget.TextView;

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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
    private String username, accountType;

    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

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

        userRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(currentUser.getUid());

        loadUserData();
        updateVerificationUI();
        loadInterstitialAd();

        btnEditPassword.setOnClickListener(v -> showPasswordChangeDialog());
        btnSave.setOnClickListener(v -> {
            saveUserData();
            showInterstitialAd();
        });
        btnVerifyEmail.setOnClickListener(v -> sendVerificationEmail());
        btnDeleteAccount.setOnClickListener(v -> handleDeleteAccount());
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(requireContext(),
                "ca-app-pub-3940256099942544/1033173712",
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
        userRef.get().addOnSuccessListener(snapshot -> {
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
        userRef.child("name").setValue(etName.getText().toString().trim());
        userRef.child("username").setValue(etUsername.getText().toString().trim());
        userRef.child("email").setValue(etEmail.getText().toString().trim());
        userRef.child("contact").setValue(etContact.getText().toString().trim());
        showSnackbar("Saved successfully");
    }

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
                            .addOnFailureListener(e -> showSnackbar("Failed: " + e.getMessage())))
                    .addOnFailureListener(e -> showSnackbar("Re-auth failed: " + e.getMessage()));
        });
        builder.setNegativeButton("Cancel", (d, w) -> d.dismiss());
        builder.show();
    }

    // ---------------------- DELETE ACCOUNT ----------------------
    private void handleDeleteAccount() {
        if (currentUser == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action is irreversible.")
                .setPositiveButton("Delete", (dialog, which) -> fetchAccountTypeAndDelete())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void fetchAccountTypeAndDelete() {
        userRef.child("accountType").get().addOnSuccessListener(snapshot -> {
            String type = snapshot.getValue(String.class);
            if ("Owner".equalsIgnoreCase(type)) {
                deleteOwnerAccount();
            } else {
                deleteUserAccount();
            }
        });
    }

    private void deleteUserAccount() {
        String uid = currentUser.getUid();
        DatabaseReference spacesRef = FirebaseDatabase.getInstance(DB_URL).getReference("spaces");
        DatabaseReference userBookingRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users").child(uid).child("booking_reference");

        spacesRef.get().addOnSuccessListener(snapshot -> {
            List<DatabaseReference> bookingsToDelete = new ArrayList<>();
            for (DataSnapshot spaceSnap : snapshot.getChildren()) {
                for (DataSnapshot booking : spaceSnap.child("bookings").getChildren()) {
                    if (uid.equals(booking.child("userId").getValue(String.class))) {
                        bookingsToDelete.add(booking.getRef());
                    }
                }
            }
            deleteRefsSequentially(bookingsToDelete, () -> {
                userBookingRef.removeValue().addOnSuccessListener(aVoid -> deleteDatabaseThenAuth(uid));
            });
        });
    }

    private void deleteOwnerAccount() {
        String uid = currentUser.getUid();
        userRef.child("space_reference").get().addOnSuccessListener(snapshot -> {
            List<DatabaseReference> spacesToDelete = new ArrayList<>();
            for (DataSnapshot spaceSnap : snapshot.getChildren()) {
                String spaceId = spaceSnap.getKey();
                if (spaceId != null) {
                    spacesToDelete.add(FirebaseDatabase.getInstance(DB_URL).getReference("spaces").child(spaceId));
                }
            }
            deleteSpacesSequentially(spacesToDelete, () -> deleteDatabaseThenAuth(uid));
        });
    }

    private void deleteSpacesSequentially(List<DatabaseReference> spaces, Runnable onComplete) {
        if (spaces.isEmpty()) {
            onComplete.run();
            return;
        }
        DatabaseReference spaceRef = spaces.remove(0);
        spaceRef.child("bookings").get().addOnSuccessListener(snapshot -> {
            List<DatabaseReference> bookingRefs = new ArrayList<>();
            for (DataSnapshot bookingSnap : snapshot.getChildren()) {
                bookingRefs.add(bookingSnap.getRef());
            }
            deleteRefsSequentially(bookingRefs, () -> spaceRef.removeValue().addOnSuccessListener(aVoid ->
                    deleteSpacesSequentially(spaces, onComplete)
            ));
        });
    }

    private void deleteRefsSequentially(List<DatabaseReference> refs, Runnable onComplete) {
        if (refs.isEmpty()) {
            onComplete.run();
            return;
        }
        DatabaseReference ref = refs.remove(0);
        ref.removeValue().addOnSuccessListener(aVoid -> deleteRefsSequentially(refs, onComplete))
                .addOnFailureListener(e -> showSnackbar("Failed to delete: " + e.getMessage()));
    }

    private void deleteDatabaseThenAuth(String uid) {
        List<Task<Void>> tasks = new ArrayList<>();
        tasks.add(FirebaseDatabase.getInstance(DB_URL).getReference("users").child(uid).removeValue());
        if (username != null && !username.isEmpty()) {
            tasks.add(FirebaseDatabase.getInstance(DB_URL).getReference("usernames").child(username).removeValue());
        }

        Tasks.whenAll(tasks).addOnSuccessListener(aVoid -> deleteAuthentication())
                .addOnFailureListener(e -> showSnackbar("Failed to delete database nodes: " + e.getMessage()));
    }

    private void deleteAuthentication() {
        currentUser.delete().addOnSuccessListener(aVoid -> {
            showSnackbar("Account deleted successfully");
            Intent intent = new Intent(requireActivity(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        }).addOnFailureListener(e -> showSnackbar("Failed to delete auth: " + e.getMessage()));
    }

    private void showSnackbar(String msg) {
        if (getView() != null) {
            Snackbar.make(getView(), msg, Snackbar.LENGTH_LONG).show();
        }
    }
}
