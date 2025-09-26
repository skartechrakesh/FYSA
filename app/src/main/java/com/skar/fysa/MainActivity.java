package com.skar.fysa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "theme_preference";
    private static final String KEY_THEME_MODE = "theme_mode";
    private final Handler backPressHandler = new Handler(Looper.getMainLooper());
    private boolean doubleBackToExitPressedOnce = false;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load the saved theme before calling super.onCreate()
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedTheme = sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnRegister);
        ImageButton btnTheme = findViewById(R.id.btnThemeToggle);

        TextView footerText = findViewById(R.id.footerText);
        int year = Calendar.getInstance().get(Calendar.YEAR);
        footerText.setText("© " + year + " SKAR. All Rights Reserved");

        // Navigation
        btnLogin.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LoginActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, RegisterActivity.class)));

        // Change theme logic
        btnTheme.setOnClickListener(v -> changeTheme());

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

    private void changeTheme() {
        int nightMode = AppCompatDelegate.getDefaultNightMode();
        int newNightMode;
        if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            newNightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            newNightMode = AppCompatDelegate.MODE_NIGHT_YES;
        }

        // Save the new theme preference
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_THEME_MODE, newNightMode);
        editor.apply();

        // Recreate the activity to apply the new theme
        AppCompatDelegate.setDefaultNightMode(newNightMode);
        recreate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backPressHandler.removeCallbacksAndMessages(null);
    }
}