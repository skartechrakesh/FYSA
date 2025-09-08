package com.skar.fysa;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;
import java.util.Random;

public class RegisterActivity extends AppCompatActivity {

    private ImageButton btnThemeToggle;
    private Button btnRegisterSubmit;
    private TextView linkLogin;
    private MaterialButtonToggleGroup toggleAccountType;
    private ProgressBar progressBar;

    private com.google.android.gms.common.SignInButton btnGoogleSignIn;

    private TextInputLayout nameLayout, usernameLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    private TextInputEditText usernameEdit, passwordEdit, confirmPasswordEdit;

    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    private final int RC_SIGN_IN = 1001;
    private final String FIREBASE_DB_URL = "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        FirebaseApp.initializeApp(this);

        FysaApp.setStatusBarColor(this);

        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnRegisterSubmit = findViewById(R.id.btnRegisterSubmit);
        linkLogin = findViewById(R.id.linkLogin);
        toggleAccountType = findViewById(R.id.toggleAccountType);
        progressBar = findViewById(R.id.progressBar);
        btnGoogleSignIn = findViewById(R.id.btnGoogleRegister);

        nameLayout = findViewById(R.id.nameLayout);
        usernameLayout = findViewById(R.id.usernameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        usernameEdit = (TextInputEditText) usernameLayout.getEditText();
        passwordEdit = (TextInputEditText) passwordLayout.getEditText();
        confirmPasswordEdit = (TextInputEditText) confirmPasswordLayout.getEditText();

        auth = FirebaseAuth.getInstance();

        FysaApp.setupTextInputStrokes(this, nameLayout, usernameLayout, emailLayout, passwordLayout, confirmPasswordLayout);

        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
        btnThemeToggle.setOnClickListener(v ->
                FysaApp.toggleThemeWithAnimation(RegisterActivity.this, btnThemeToggle)
        );

        loadAccountType();
        toggleAccountType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                String type = (checkedId == R.id.btnOwner) ? "owner" : "user";
                FysaApp.saveString(this, "account_type", type);
            }
        });

        setupPasswordValidation();

        btnRegisterSubmit.setOnClickListener(v -> performEmailRegistration());

        linkLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        setupGoogleSignIn();
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
    }

    @Override
    protected void onResume() {
        super.onResume();
        FysaApp.setStatusBarColor(this);
        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
    }

    // ---------------- Google Sign-In ----------------
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void signInWithGoogle() {
        setLoading(true);
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                setLoading(false);
                Toast.makeText(this, "Google Sign-In failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            setLoading(false);
            if (task.isSuccessful()) {
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    checkIfGoogleUserExists(user);
                }
            } else {
                Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkIfGoogleUserExists(FirebaseUser user) {
        String uid = user.getUid();
        DatabaseReference dbRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL).getReference();

        dbRef.child("users").child(uid).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(this, "Failed to check user data.", Toast.LENGTH_SHORT).show();
                auth.signOut();
                return;
            }

            if (!task.getResult().exists()) {
                String accountType = FysaApp.getString(this, "account_type", "user");
                String suggested = suggestUsername(user.getDisplayName());

                promptForGoogleUsername(suggested, chosenUsername -> {
                    registerGoogleUser(user, chosenUsername, accountType);
                });
            } else {
                // User already exists, check their account type from the database and redirect
                String accountType = task.getResult().child("accountType").getValue(String.class);
                redirectUser(accountType);
            }
        });
    }

    private void registerGoogleUser(FirebaseUser user, String username, String accountType) {
        setLoading(true);
        DatabaseReference usernameRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                .getReference("usernames").child(username);

        usernameRef.setValue(user.getUid(), (error, ref) -> {
            if (error != null) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Username already exists or another error occurred.", Toast.LENGTH_SHORT).show();
            } else {
                // Username is unique, proceed to save user data
                saveUserData(user.getUid(), username, user.getEmail(), user.getDisplayName(), accountType, false);
            }
        });
    }

    // ---------------- Email Registration ----------------
    private void performEmailRegistration() {
        String name = getText(nameLayout);
        String username = getText(usernameLayout);
        String email = getText(emailLayout);
        String password = getText(passwordLayout);
        String confirmPassword = getText(confirmPasswordLayout);
        String accountType = FysaApp.getString(this, "account_type", "user");

        if (name.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isValidUsername(username)) {
            usernameLayout.setError("Invalid username: min 5 chars, lowercase & numbers only");
            return;
        } else {
            usernameLayout.setError(null);
        }

        if (!isPasswordStrong(password)) {
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

        // Check for username uniqueness first
        DatabaseReference usernameRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                .getReference("usernames").child(username);

        usernameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    setLoading(false);
                    usernameLayout.setError("Username already exists");
                    Toast.makeText(RegisterActivity.this, "Username already exists", Toast.LENGTH_SHORT).show();
                    return; // Stop the process here
                }

                // If username is unique, proceed with Firebase Authentication registration
                auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                FirebaseUser firebaseUser = auth.getCurrentUser();
                                if (firebaseUser != null) {
                                    // Step 2: Try to save the username to the database index.
                                    usernameRef.setValue(firebaseUser.getUid(), (error, ref) -> {
                                        if (error != null) {
                                            // Step 2 failed: a race condition occurred. Clean up auth user.
                                            firebaseUser.delete();
                                            setLoading(false);
                                            Toast.makeText(RegisterActivity.this, "Registration failed. Please try again.", Toast.LENGTH_LONG).show();
                                        } else {
                                            // Step 2 succeeded: Proceed to save full user data.
                                            saveUserData(firebaseUser.getUid(), username, email, name, accountType, true);
                                        }
                                    });
                                } else {
                                    setLoading(false);
                                    Toast.makeText(RegisterActivity.this, "Registration failed.", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                setLoading(false);
                                Toast.makeText(RegisterActivity.this, "Registration failed: " + task.getException().getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Database error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveUserData(String uid, String username, String email, String name, String accountType, boolean isEmailRegistration) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL).getReference();

        UserSecure userSecure = new UserSecure(uid, username, email);
        dbRef.child("user_secure").child(uid).setValue(userSecure);

        User fullUser = new User(username, name, email, accountType, uid);
        dbRef.child("users").child(uid).setValue(fullUser).addOnCompleteListener(dbTask -> {
            setLoading(false);
            if (dbTask.isSuccessful()) {
                Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                if (isEmailRegistration) {
                    auth.signOut(); // Sign out the email user for them to login with credentials
                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                } else {
                    redirectUser(accountType);
                }
                finish();
            } else {
                Toast.makeText(RegisterActivity.this, "Failed to save profile info: " + dbTask.getException().getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------------- Redirection ----------------
    private void redirectUser(String accountType) {
        Intent intent;
        if ("owner".equalsIgnoreCase(accountType)) {
            intent = new Intent(RegisterActivity.this, OwnerDashboardActivity.class);
        } else {
            intent = new Intent(RegisterActivity.this, UserDashboardActivity.class);
        }
        startActivity(intent);
        finish();
    }

    // ---------------- Username Validation ----------------
    private boolean isValidUsername(String username) {
        return username.matches("^[a-z0-9]{5,}$");
    }

    // ---------------- Password Validation ----------------
    private boolean isPasswordStrong(String password) {
        String regex = "^(?=.*[a-z])(?=.*[0-9])(?=.*[@$!%*?&])[A-Za-z0-9@$!%*?&]{8,}$";
        return password.matches(regex);
    }

    private void setupPasswordValidation() {
        if (passwordEdit != null) {
            passwordEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    String pwd = s.toString();
                    if (!isPasswordStrong(pwd)) {
                        passwordLayout.setError("Weak password: Min 8 chars, 1 lowercase, 1 number, 1 symbol");
                    } else {
                        passwordLayout.setError(null);
                    }
                }
            });
        }

        if (confirmPasswordEdit != null) {
            confirmPasswordEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    String confirmPwd = s.toString();
                    String pwd = passwordEdit != null ? passwordEdit.getText().toString() : "";
                    if (!confirmPwd.equals(pwd)) {
                        confirmPasswordLayout.setError("Passwords do not match");
                    } else {
                        confirmPasswordLayout.setError(null);
                    }
                }
            });
        }
    }

    // ---------------- Utils ----------------
    private String getText(TextInputLayout layout) {
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        return (editText != null) ? editText.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegisterSubmit.setEnabled(!loading);
        btnGoogleSignIn.setEnabled(!loading);
    }

    private void loadAccountType() {
        String type = FysaApp.getString(this, "account_type", "user");
        if ("owner".equals(type)) toggleAccountType.check(R.id.btnOwner);
        else toggleAccountType.check(R.id.btnUser);
    }

    // Suggests a username from display name
    private String suggestUsername(String displayName) {
        String base = displayName == null ? "user" : displayName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        if (base.length() < 5) base = base + "user";
        return base + new Random().nextInt(1000);
    }

    // Show dialog to enter username
    private void promptForGoogleUsername(String suggested, OnUsernameChosenListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_username_prompt, null);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.usernameInputLayout);
        TextInputEditText inputEdit = (TextInputEditText) inputLayout.getEditText();

        if (inputEdit != null) inputEdit.setText(suggested);

        builder.setView(dialogView)
                .setTitle("Choose a username")
                .setPositiveButton("Continue", (dialog, which) -> {
                    if (inputEdit != null) {
                        String chosen = inputEdit.getText().toString().trim();
                        if (isValidUsername(chosen)) {
                            listener.onUsernameChosen(chosen);
                        } else {
                            Toast.makeText(this, "Invalid username", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    auth.signOut(); // Sign out the user if they cancel the process
                })
                .setCancelable(false)
                .show();
    }

    private interface OnUsernameChosenListener {
        void onUsernameChosen(String username);
    }
}