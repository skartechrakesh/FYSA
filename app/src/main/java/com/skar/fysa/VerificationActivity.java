package com.skar.fysa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;

public class VerificationActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "theme_preference";
    private static final String KEY_THEME_MODE = "theme_mode";

    private Button btnResend, btnContinue, btnLogout;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private FirebaseFirestore firestore;
    private String accountType; // "user" or "owner"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load saved theme
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedTheme = sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        btnResend = findViewById(R.id.btnResend);
        btnContinue = findViewById(R.id.btnContinue);
        btnLogout = findViewById(R.id.btnLogout);
        progressBar = findViewById(R.id.progressBar);

        if (currentUser == null) {
            logout();
            return;
        }

        loadAccountType();

        // Auto check on load
        checkEmailVerificationOnLoad();

        btnResend.setOnClickListener(v -> resendVerificationEmail());
        btnContinue.setOnClickListener(v -> checkEmailVerification());
        btnLogout.setOnClickListener(v -> logout());
    }

    /**
     * Load account type to determine which dashboard to redirect
     */
    private void loadAccountType() {
        firestore.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        accountType = documentSnapshot.getString("accountType");
                    } else {
                        accountType = "user"; // fallback
                    }
                })
                .addOnFailureListener(e -> accountType = "user");
    }

    /**
     * Check email verification immediately on activity start
     */
    private void checkEmailVerificationOnLoad() {
        currentUser.reload().addOnCompleteListener(task -> {
            if (currentUser.isEmailVerified()) {
                navigateToDashboard();
            }
        });
    }

    /**
     * Resend verification email
     */
    private void resendVerificationEmail() {
        if (currentUser == null) return;
        setLoading(true);
        currentUser.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        showSnackbar("Verification email sent!");
                    } else {
                        showSnackbar("Failed to send email: " + (task.getException() != null ? task.getException().getMessage() : ""));
                    }
                });
    }

    /**
     * Manual verification check
     */
    private void checkEmailVerification() {
        if (currentUser == null) return;
        setLoading(true);
        currentUser.reload().addOnCompleteListener(task -> {
            setLoading(false);
            if (currentUser.isEmailVerified()) {
                showSnackbar("Email verified successfully!");
                navigateToDashboard();
            } else {
                showSnackbar("Email not verified yet. Please check your inbox.");
            }
        });
    }

    /**
     * Redirect user to the correct dashboard
     */
    private void navigateToDashboard() {
        Intent intent;
        if ("owner".equalsIgnoreCase(accountType)) {
            intent = new Intent(this, OwnerDashboardActivity.class);
        } else {
            intent = new Intent(this, UserDashboardActivity.class);
        }
        startActivity(intent);
        finish();
    }

    /**
     * Logout user
     */
    private void logout() {
        auth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    /**
     * Display Snackbar
     */
    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Set progress/loading state
     */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnResend.setEnabled(!loading);
        btnContinue.setEnabled(!loading);
        btnLogout.setEnabled(!loading);
    }

    /**
     * Optional helper to delete unverified users after 3 days.
     * Call this from SplashActivity or LoginActivity during auto-check.
     */
    public void deleteUnverifiedUserIfExpired(long timestampMillis) {
        long currentMillis = System.currentTimeMillis();
        long diff = currentMillis - timestampMillis;
        if (diff >= TimeUnit.DAYS.toMillis(3)) {
            // Delete Firestore documents first
            firestore.collection("users").document(currentUser.getUid()).delete();
            firestore.collection("usernames").document(/*username placeholder*/).delete();
            // Delete Firebase Auth user
            currentUser.delete();
        }
    }
}
