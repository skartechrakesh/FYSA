package com.skar.fysa;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnLogin, btnRegister;
    private ImageButton btnThemeToggle;

    private boolean doubleBackToExitPressedOnce = false;
    private final Handler backPressHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Apply global status bar theme
        FysaApp.setStatusBarColor(this);

        // Initialize views
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);

        // Update toggle icon initially
        FysaApp.updateThemeIcon(this, btnThemeToggle, false);

        // Navigation
        btnLogin.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, LoginActivity.class))
        );
        btnRegister.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, RegisterActivity.class))
        );

        // Theme toggle with animation (centralized in FysaApp)
        btnThemeToggle.setOnClickListener(v ->
                FysaApp.toggleThemeWithAnimation(this, btnThemeToggle)
        );

        // Double back press to exit
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (doubleBackToExitPressedOnce) {
                    finishAffinity();
                    return;
                }
                doubleBackToExitPressedOnce = true;
                Toast.makeText(MainActivity.this, "Press again to exit", Toast.LENGTH_SHORT).show();
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
}
