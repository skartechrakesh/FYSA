package com.skar.fysa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 1500; // 1.5 seconds

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load saved theme before super.onCreate
        SharedPreferences sharedPreferences = getSharedPreferences("theme_preference", MODE_PRIVATE);
        int savedTheme = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Delay splash then check session & redirect
        new Handler(Looper.getMainLooper()).postDelayed(this::checkSessionAndRedirect, SPLASH_DELAY);
    }

    /**
     * ---------- SESSION & REDIRECT ----------
     **/
    private void checkSessionAndRedirect() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            goToMainActivity();
        } else {
            DocumentReference userRef = firestore.collection("users").document(currentUser.getUid());

            userRef.get().addOnCompleteListener(task -> {
                Intent intent;
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    DocumentSnapshot snapshot = task.getResult();
                    String accountType = snapshot.getString("accountType");

                    // If email is verified, redirect to dashboard
                    if (currentUser.isEmailVerified()) {
                        intent = "owner".equalsIgnoreCase(accountType) ?
                                new Intent(SplashActivity.this, OwnerDashboardActivity.class) :
                                new Intent(SplashActivity.this, UserDashboardActivity.class);
                    } else {
                        // Redirect to VerificationActivity if email not verified
                        intent = new Intent(SplashActivity.this, VerificationActivity.class);
                        intent.putExtra("accountType", accountType);
                    }
                } else {
                    intent = new Intent(SplashActivity.this, MainActivity.class);
                }
                launchAndFinish(intent);
            });
        }
    }

    /**
     * ---------- HELPERS ----------
     **/
    private void goToMainActivity() {
        launchAndFinish(new Intent(SplashActivity.this, MainActivity.class));
    }

    private void launchAndFinish(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
