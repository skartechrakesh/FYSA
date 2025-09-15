package com.skar.fysa;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

@SuppressWarnings("deprecation")
public class LoginActivity extends AppCompatActivity {

    private ImageButton btnThemeToggle;
    private Button btnLoginSubmit;
    private ProgressBar progressBar;
    private TextInputLayout emailLayout, passwordLayout;

    private FirebaseAuth auth;
    private DatabaseReference usersRef;

    // Theme preference keys from RegisterActivity
    private static final String PREFS_NAME = "theme_preference";
    private static final String KEY_THEME_MODE = "theme_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load the saved theme before calling super.onCreate()
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedTheme = sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnLoginSubmit = findViewById(R.id.btnLoginSubmit);
        TextView linkRegister = findViewById(R.id.linkRegister);
        TextView linkForgotPassword = findViewById(R.id.linkForgotPassword);
        progressBar = findViewById(R.id.progressBar);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);

        auth = FirebaseAuth.getInstance();
        String FIREBASE_DB_URL = "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";
        usersRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL).getReference("users");

        btnThemeToggle.setOnClickListener(v -> changeTheme());

        btnLoginSubmit.setOnClickListener(v -> performLogin());
        linkRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        linkForgotPassword.setOnClickListener(v -> sendPasswordResetEmail());

        setupTextInputStrokes(this, emailLayout, passwordLayout);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            redirectToDashboard(currentUser.getUid());
        }
    }

    private void changeTheme() {
        int nightMode = AppCompatDelegate.getDefaultNightMode();
        int newNightMode;
        if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            newNightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            newNightMode = AppCompatDelegate.MODE_NIGHT_YES;
        }

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_THEME_MODE, newNightMode);
        editor.apply();

        AppCompatDelegate.setDefaultNightMode(newNightMode);
        recreate();
    }

    private void performLogin() {
        String email = emailLayout.getEditText() != null ?
                emailLayout.getEditText().getText().toString().trim() : "";
        String password = passwordLayout.getEditText() != null ?
                passwordLayout.getEditText().getText().toString().trim() : "";

        if (email.isEmpty()) {
            emailLayout.setError("Enter email");
            return;
        } else emailLayout.setError(null);

        if (password.isEmpty()) {
            passwordLayout.setError("Enter password");
            return;
        } else passwordLayout.setError(null);

        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        redirectToDashboard(auth.getCurrentUser().getUid());
                    } else {
                        setLoading(false);
                        showSnackbar("Login failed: " +
                                (task.getException() != null ? task.getException().getMessage() : ""));
                    }
                });
    }

    private void sendPasswordResetEmail() {
        String email = emailLayout.getEditText() != null ?
                emailLayout.getEditText().getText().toString().trim() : "";

        if (email.isEmpty()) {
            emailLayout.setError("Enter your email to receive a password reset link.");
            return;
        }

        setLoading(true);

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        showSnackbar("Password reset email sent to " + email);
                    } else {
                        showSnackbar("Failed to send reset email: " +
                                (task.getException() != null ? task.getException().getMessage() : ""));
                    }
                });
    }

    private void redirectToDashboard(String uid) {
        setLoading(true);
        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                setLoading(false);
                if (snapshot.exists()) {
                    String accountType = snapshot.child("accountType").getValue(String.class);
                    Intent intent = "owner".equalsIgnoreCase(accountType)
                            ? new Intent(LoginActivity.this, OwnerDashboardActivity.class)
                            : new Intent(LoginActivity.this, UserDashboardActivity.class);

                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    showSnackbar("User not found in database.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                showSnackbar("Database error: " + error.getMessage());
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLoginSubmit.setEnabled(!loading);
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    private void setupTextInputStrokes(Context context, TextInputLayout... inputs) {
        if (context == null || inputs == null) return;

        int focusedColor = ContextCompat.getColor(context, R.color.stroke_focused);
        int unfocusedColor = ContextCompat.getColor(context, R.color.stroke_unfocused);

        for (TextInputLayout input : inputs) {
            if (input == null) continue;
            ColorStateList states = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_enabled, android.R.attr.state_focused},
                            new int[]{android.R.attr.state_enabled}
                    },
                    new int[]{focusedColor, unfocusedColor}
            );
            input.setBoxStrokeColorStateList(states);
        }
    }
}