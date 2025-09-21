package com.skar.fysa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;


@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 1500; // 1.5 seconds

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load the saved theme before calling super.onCreate()
        SharedPreferences sharedPreferences = getSharedPreferences("theme_preference", MODE_PRIVATE);
        int savedTheme = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Delay for splash branding, then check session & redirect
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
            redirectUser(currentUser.getUid());
        }
    }

    private void redirectUser(String uid) {
        DocumentReference userRef = firestore.collection("users").document(uid);

        userRef.get().addOnCompleteListener(task -> {
            Intent intent;
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                DocumentSnapshot snapshot = task.getResult();
                String accountType = snapshot.getString("accountType");
                if ("owner".equalsIgnoreCase(accountType)) {
                    intent = new Intent(SplashActivity.this, OwnerDashboardActivity.class);
                } else {
                    intent = new Intent(SplashActivity.this, UserDashboardActivity.class);
                }
            } else {
                intent = new Intent(SplashActivity.this, MainActivity.class);
            }
            launchAndFinish(intent);
        });
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