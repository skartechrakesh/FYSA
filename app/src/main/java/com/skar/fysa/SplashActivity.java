package com.skar.fysa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 1000; // 1 second

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        setStatusBarBlack();

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase
                .getInstance("https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users");

        // Delay for splash branding, then redirect
        new Handler(Looper.getMainLooper()).postDelayed(this::checkSessionAndRedirect, SPLASH_DELAY);
    }

    /**
     * ---------- STATUS BAR ----------
     **/
    private void setStatusBarBlack() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
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
        usersRef.child(uid).child("accountType")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Intent intent;
                        if (snapshot.exists()) {
                            String accountType = snapshot.getValue(String.class);
                            if ("owner".equalsIgnoreCase(accountType)) {
                                intent = new Intent(SplashActivity.this, OwnerDashboardActivity.class);
                            } else {
                                intent = new Intent(SplashActivity.this, UserDashboardActivity.class);
                            }
                        } else {
                            intent = new Intent(SplashActivity.this, MainActivity.class);
                        }
                        launchAndFinish(intent);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        goToMainActivity();
                    }
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
