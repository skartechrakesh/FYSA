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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RegisterActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "theme_preference";
    private static final String KEY_THEME_MODE = "theme_mode";

    private Button btnRegisterSubmit;
    private MaterialButtonToggleGroup toggleAccountType;
    private ProgressBar progressBar;
    private TextInputLayout nameLayout, usernameLayout, contactLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    private TextInputEditText passwordEdit, confirmPasswordEdit;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load saved theme
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedTheme = sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

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
        firestore = FirebaseFirestore.getInstance();

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
        int newNightMode = (nightMode == AppCompatDelegate.MODE_NIGHT_YES) ?
                AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(KEY_THEME_MODE, newNightMode);
        editor.apply();

        AppCompatDelegate.setDefaultNightMode(newNightMode);
        recreate();
    }

    private void setupPasswordValidation() {
        if (passwordEdit != null) {
            passwordEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isPasswordWeak(s.toString()))
                        passwordLayout.setError("Weak password: Min 8 chars, 1 lowercase, 1 number, 1 symbol");
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
                    if (!s.toString().equals(pwd))
                        confirmPasswordLayout.setError("Passwords do not match");
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

        String accountType = (toggleAccountType.getCheckedButtonId() == R.id.btnOwner) ? "owner" : "user";

        if (contact.isEmpty()) {
            showSnackbar("Please enter contact number");
            return;
        }
        if (!contact.matches("^\\+?[0-9]{7,10}$")) {
            contactLayout.setError("Invalid contact number");
            return;
        } else contactLayout.setError(null);

        if (name.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showSnackbar("Please fill all fields");
            return;
        }
        if (!isValidUsername(username)) {
            usernameLayout.setError("Invalid username: min 5 chars, lowercase & numbers only");
            return;
        } else usernameLayout.setError(null);

        if (isPasswordWeak(password)) {
            passwordLayout.setError("Weak password: Min 8 chars, 1 lowercase, 1 number, 1 symbol");
            return;
        } else passwordLayout.setError(null);

        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Passwords do not match");
            return;
        } else confirmPasswordLayout.setError(null);

        setLoading(true);

        // Check username availability in Firestore
        DocumentReference usernameRef = firestore.collection("usernames").document(username);
        usernameRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                setLoading(false);
                usernameLayout.setError("Username already exists");
                showSnackbar("Username already exists");
            } else {
                createFirebaseUser(email, password, username, contact, name, accountType);
            }
        });
    }

    private void createFirebaseUser(String email, String password, String username,
                                    String contact, String name, String accountType) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful() && auth.getCurrentUser() != null) {
                FirebaseUser firebaseUser = auth.getCurrentUser();

                // Send verification email
                firebaseUser.sendEmailVerification()
                        .addOnSuccessListener(aVoid -> {
                            // Save user data in Firestore only after sending email
                            saveUserDataToFirestore(firebaseUser.getUid(), username, contact, email, name, accountType);
                        })
                        .addOnFailureListener(e -> {
                            setLoading(false);
                            showSnackbar("Failed to send verification email: " + e.getMessage());
                        });

            } else {
                setLoading(false);
                showSnackbar("Registration failed: " + (task.getException() != null ? task.getException().getMessage() : ""));
            }
        });
    }

    private void saveUserDataToFirestore(String uid, String username, String contact,
                                         String email, String name, String accountType) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", username);
        userMap.put("contact", contact);
        userMap.put("name", name);
        userMap.put("email", email);
        userMap.put("accountType", accountType);
        userMap.put("uid", uid);
        userMap.put("createdAt", System.currentTimeMillis()); // for auto-delete

        firestore.collection("users").document(uid)
                .set(userMap)
                .addOnSuccessListener(aVoid -> {
                    firestore.collection("usernames").document(username)
                            .set(Collections.singletonMap("uid", uid))
                            .addOnSuccessListener(aVoid2 -> {
                                setLoading(false);
                                showSnackbar("Registration successful... Please verify your email within 3 days!");
                                redirectToVerification(accountType);
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                showSnackbar("Failed to save username reference: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar("Failed to save user data: " + e.getMessage());
                });
    }

    private void redirectToVerification(String accountType) {
        Intent intent = new Intent(this, VerificationActivity.class);
        intent.putExtra("accountType", accountType);
        startActivity(intent);
        finish();
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
}
