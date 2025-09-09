package com.skar.fysa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private final Handler backPressHandler = new Handler();
    private ImageButton btnThemeToggle;
    private boolean doubleBackToExitPressedOnce = false;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Apply global status bar theme
        FysaApp.setStatusBarColor(this);

        // Initialize views
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnRegister);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);

        TextView footerText = findViewById(R.id.footerText);
        int year = Calendar.getInstance().get(Calendar.YEAR);
        footerText.setText("© " + year + " SKAR. All Rights Reserved");


        // Update toggle icon initially
        FysaApp.updateThemeIcon(this, btnThemeToggle, false);

        // Navigation
        btnLogin.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LoginActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, RegisterActivity.class)));

        // Theme toggle with animation (centralized in FysaApp)
        btnThemeToggle.setOnClickListener(v -> FysaApp.toggleThemeWithAnimation(this, btnThemeToggle));

        // Double back press to exit
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (doubleBackToExitPressedOnce) {
                    finishAffinity();
                    return;
                }
                doubleBackToExitPressedOnce = true;
                Snackbar.make(findViewById(android.R.id.content), "Press again to exit", Snackbar.LENGTH_SHORT).show();
                backPressHandler.postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
        FysaApp.setStatusBarColor(this); // re-apply on resume
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backPressHandler.removeCallbacksAndMessages(null);
    }

}
