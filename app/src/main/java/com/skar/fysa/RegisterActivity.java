package com.skar.fysa;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Objects;

public class RegisterActivity extends AppCompatActivity {

    private static final String FIREBASE_DB_URL = "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static final String PREFS_NAME = "theme_preference";
    private static final String KEY_THEME_MODE = "theme_mode";

    private Button btnRegisterSubmit;
    private MaterialButtonToggleGroup toggleAccountType;
    private ProgressBar progressBar;
    private TextInputLayout nameLayout, usernameLayout, contactLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    private TextInputEditText passwordEdit, confirmPasswordEdit;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load the saved theme before calling super.onCreate()
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedTheme = sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        FirebaseApp.initializeApp(this);

        ImageButton btnTheme = findViewById(R.id.btnThemeToggle);
        btnRegisterSubmit = findViewById(R.id.btnRegisterSubmit);
        TextView linkLogin = findViewById(R.id.linkLogin);
        toggleAccountType = findViewById(R.id.toggleAccountType);
        progressBar = findViewById(R.id.progressBar);

        nameLayout = findViewById(R.id.nameLayout);
        usernameLayout = findViewById(R.id.usernameLayout);
        contactLayout = findViewById(R.id.contactLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        passwordEdit = (TextInputEditText) passwordLayout.getEditText();
        confirmPasswordEdit = (TextInputEditText) confirmPasswordLayout.getEditText();

        auth = FirebaseAuth.getInstance();

        setupTextInputStrokes(this, nameLayout, usernameLayout, contactLayout, emailLayout, passwordLayout, confirmPasswordLayout);
        btnTheme.setOnClickListener(v -> changeTheme());

        setupPasswordValidation();
        btnRegisterSubmit.setOnClickListener(v -> performEmailRegistration());

        linkLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
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

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_THEME_MODE, newNightMode);
        editor.apply();

        AppCompatDelegate.setDefaultNightMode(newNightMode);
        recreate();
    }

    public static void setupTextInputStrokes(Context context, TextInputLayout... inputs) {
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

    private void setupPasswordValidation() {
        if (passwordEdit != null) {
            passwordEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isPasswordWeak(s.toString())) passwordLayout.setError("Weak password: Min 8 chars, 1 lowercase, 1 number, 1 symbol");
                    else passwordLayout.setError(null);
                }
            });
        }
        if (confirmPasswordEdit != null) {
            confirmPasswordEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String pwd = passwordEdit != null ? Objects.requireNonNull(passwordEdit.getText()).toString() : "";
                    if (!s.toString().equals(pwd)) confirmPasswordLayout.setError("Passwords do not match");
                    else confirmPasswordLayout.setError(null);
                }
            });
        }
    }

    private boolean isPasswordWeak(String password) {
        return !password.matches("^(?=.*[a-z])(?=.*[0-9])(?=.*[@$!%*?&])[A-Za-z0-9@$!%*?&]{8,}$");
    }

    private void performEmailRegistration() {
        String name = getEditText(nameLayout);
        String username = getEditText(usernameLayout);
        String contact = getEditText(contactLayout);
        String email = getEditText(emailLayout);
        String password = getEditText(passwordLayout);
        String confirmPassword = getEditText(confirmPasswordLayout);

        if (toggleAccountType.getCheckedButtonId() == View.NO_ID) {
            showSnackbar("Please select an account type (User or Owner)");
            return;
        }

        String accountType;
        if (toggleAccountType.getCheckedButtonId() == R.id.btnOwner) {
            accountType = "owner";
        } else {
            accountType = "user";
        }

        if (contact.isEmpty()) {
            showSnackbar("Please enter contact number");
            return;
        }

        if (!contact.matches("^\\+?[0-9]{7,10}$")) {
            contactLayout.setError("Invalid contact number");
            return;
        } else {
            contactLayout.setError(null);
        }

        if (name.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showSnackbar("Please fill all fields");
            return;
        }

        if (!isValidUsername(username)) {
            usernameLayout.setError("Invalid username: min 5 chars, lowercase & numbers only");
            return;
        } else {
            usernameLayout.setError(null);
        }

        if (isPasswordWeak(password)) {
            passwordLayout.setError("Weak password: Min 8 chars, 1 lowercase, 1 number, 1 symbol");
            return;
        } else {
            passwordLayout.setError(null);
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Passwords do not match");
            return;
        } else {
            confirmPasswordLayout.setError(null);
        }

        setLoading(true);

        DatabaseReference usernameRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                .getReference("usernames").child(username);

        usernameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    setLoading(false);
                    usernameLayout.setError("Username already exists");
                    showSnackbar("Username already exists");
                    return;
                }

                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        FirebaseUser firebaseUser = auth.getCurrentUser();
                        usernameRef.setValue(firebaseUser.getUid(), (error, ref) -> {
                            if (error != null) {
                                firebaseUser.delete();
                                setLoading(false);
                                showSnackbar("Registration failed. Please try again.");
                            } else {
                                saveUserData(firebaseUser.getUid(), username, contact, email, name, accountType);
                            }
                        });
                    } else {
                        setLoading(false);
                        showSnackbar("Registration failed: " +
                                (task.getException() != null ? task.getException().getMessage() : ""));
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                showSnackbar("Database error: " + error.getMessage());
            }
        });
    }

    private void saveUserData(String uid, String username, String contact, String email, String name, String accountType) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL).getReference();

        dbRef.child("users").child(uid).setValue(new User(username, contact, name, email, accountType, uid))
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        showSnackbar("Registration successful!");
                        redirectUser(accountType);
                        finish();
                    } else {
                        showSnackbar("Failed to save profile info: " +
                                (task.getException() != null ? task.getException().getMessage() : ""));
                    }
                });
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    private boolean isValidUsername(String username) {
        return username.matches("^[a-z0-9]{5,}$");
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegisterSubmit.setEnabled(!loading);
    }

    private String getEditText(TextInputLayout layout) {
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        return (editText != null) ? Objects.requireNonNull(editText.getText()).toString().trim() : "";
    }

    private void redirectUser(String accountType) {
        Intent intent = "owner".equalsIgnoreCase(accountType) ?
                new Intent(this, OwnerDashboardActivity.class) :
                new Intent(this, UserDashboardActivity.class);
        startActivity(intent);
        finish();
    }
}