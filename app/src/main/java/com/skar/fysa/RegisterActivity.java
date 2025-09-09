package com.skar.fysa;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;

public class RegisterActivity extends AppCompatActivity {

    private static final String FIREBASE_DB_URL = "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private ImageButton btnThemeToggle;
    private Button btnRegisterSubmit;
    private MaterialButtonToggleGroup toggleAccountType;
    private ProgressBar progressBar;
    private com.google.android.gms.common.SignInButton btnGoogleSignIn;

    private TextInputLayout nameLayout, usernameLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    private TextInputEditText passwordEdit, confirmPasswordEdit;

    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;

    // Keep a temp chosen username when we prompt BEFORE dark-mode external OAuth flow
    private String preChosenUsernameForExternalGoogle = null;

    // Activity Result launcher for regular Google Sign-In (light mode)
    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                setLoading(false);
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            // sign into Firebase with the Google credential
                            handleGoogleAccount(account);
                        }
                    } catch (ApiException e) {
                        showSnackbar("Google Sign-In failed: " + e.getMessage());
                    }
                } else {
                    showSnackbar("Google Sign-In cancelled.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        FirebaseApp.initializeApp(this);
        FysaApp.setStatusBarColor(this);

        // init views
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnRegisterSubmit = findViewById(R.id.btnRegisterSubmit);
        TextView linkLogin = findViewById(R.id.linkLogin);
        toggleAccountType = findViewById(R.id.toggleAccountType);
        progressBar = findViewById(R.id.progressBar);
        btnGoogleSignIn = findViewById(R.id.btnGoogleRegister);

        nameLayout = findViewById(R.id.nameLayout);
        usernameLayout = findViewById(R.id.usernameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        passwordEdit = (TextInputEditText) passwordLayout.getEditText();
        confirmPasswordEdit = (TextInputEditText) confirmPasswordLayout.getEditText();

        auth = FirebaseAuth.getInstance();

        FysaApp.setupTextInputStrokes(this, nameLayout, usernameLayout, emailLayout, passwordLayout, confirmPasswordLayout);
        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
        btnThemeToggle.setOnClickListener(v -> FysaApp.toggleThemeWithAnimation(this, btnThemeToggle));

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
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        setupGoogleSignIn();
        btnGoogleSignIn.setOnClickListener(v -> handleGoogleRegisterClick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        FysaApp.setStatusBarColor(this);
        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
    }

    // ---------------- Google Sign-In setup ----------------
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    /**
     * Entry when user taps Google Register button.
     * Behavior:
     *   - In dark mode: first prompt for username, then start external OAuth-based sign-in (so username is known prior to auth).
     *   - Otherwise (light mode): start Google Sign-In intent; after sign-in, if user doesn't exist, prompt for username.
     */
    private void handleGoogleRegisterClick() {
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            // dark mode: prompt username first
            String suggested = "user" + new Random().nextInt(1000); // simple fallback suggestion
            promptForGoogleUsername(suggested, chosen -> {
                preChosenUsernameForExternalGoogle = chosen;
                startExternalGoogleOAuth();
            });
        } else {
            // light mode: regular Google sign-in intent, handle username AFTER sign-in
            setLoading(true);
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
        }
    }

    // For light-mode path: we get GoogleSignInAccount, then firebaseAuthWithGoogle(account)
    private void handleGoogleAccount(GoogleSignInAccount account) {
        setLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            setLoading(false);
            if (task.isSuccessful() && auth.getCurrentUser() != null) {
                FirebaseUser user = auth.getCurrentUser();
                checkAndRegisterGoogleUser(user, account.getDisplayName());
            } else {
                showSnackbar("Authentication failed.");
            }
        });
    }

    // ---------------- External OAuth (dark-mode) ----------------
    private void startExternalGoogleOAuth() {
        setLoading(true);
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("google.com");
        // startActivityForSignInWithProvider may open external browser / custom tab depending on system
        auth.startActivityForSignInWithProvider(this, provider.build())
                .addOnSuccessListener(authResult -> {
                    setLoading(false);
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        // We prompted for username already (preChosenUsernameForExternalGoogle)
                        if (preChosenUsernameForExternalGoogle == null || preChosenUsernameForExternalGoogle.isEmpty()) {
                            // Safety: prompt again if somehow missing
                            promptForGoogleUsername(suggestUsername(user.getDisplayName()), chosen -> {
                                registerGoogleUserWithChosenUsername(user, chosen, FysaApp.getString(this, "account_type", "user"));
                            });
                        } else {
                            registerGoogleUserWithChosenUsername(user, preChosenUsernameForExternalGoogle, FysaApp.getString(this, "account_type", "user"));
                            preChosenUsernameForExternalGoogle = null;
                        }
                    } else {
                        showSnackbar("Google sign-in succeeded but user is null.");
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar("Google sign-in failed: " + e.getMessage());
                });
    }

    // Shared: check if firebase user exists in /users; if not, prompt username then register
    private void checkAndRegisterGoogleUser(FirebaseUser user, String displayName) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL).getReference();
        dbRef.child("users").child(user.getUid()).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                showSnackbar("Failed to check user data.");
                auth.signOut();
                return;
            }
            if (!task.getResult().exists()) {
                // user doesn't exist: ask for username (suggested from display name)
                String suggested = suggestUsername(displayName);
                promptForGoogleUsername(suggested, chosenUsername -> registerGoogleUserWithChosenUsername(user, chosenUsername, FysaApp.getString(this, "account_type", "user")));
            } else {
                // user exists — proceed to redirect
                redirectUser(task.getResult().child("accountType").getValue(String.class));
            }
        });
    }

    // Called in both flows: takes firebase user(uid) and chosen username & writes username index + user data.
    private void registerGoogleUserWithChosenUsername(FirebaseUser user, String username, String accountType) {
        setLoading(true);
        DatabaseReference usernameRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                .getReference("usernames").child(username);

        // verify uniqueness first (avoid overwriting)
        usernameRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                setLoading(false);
                showSnackbar("Failed to check username. Try again.");
                return;
            }

            if (task.getResult().exists()) {
                setLoading(false);
                showSnackbar("Username already exists. Choose another one.");
                // because in dark-mode we already had pre-chosen username, ask again
                promptForGoogleUsername(suggestUsername(user.getDisplayName()), chosen -> registerGoogleUserWithChosenUsername(user, chosen, accountType));
            } else {
                usernameRef.setValue(user.getUid(), (error, ref) -> {
                    if (error != null) {
                        setLoading(false);
                        showSnackbar("Failed to save username. Try again.");
                    } else {
                        // save user_secure and users entries
                        saveUserData(user.getUid(), username, user.getEmail(), user.getDisplayName(), accountType, false);
                    }
                });
            }
        });
    }

    // ---------------- Email registration flow ----------------
    private void performEmailRegistration() {
        String name = getText(nameLayout);
        String username = getText(usernameLayout);
        String email = getText(emailLayout);
        String password = getText(passwordLayout);
        String confirmPassword = getText(confirmPasswordLayout);
        String accountType = FysaApp.getString(this, "account_type", "user");

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

        DatabaseReference usernameRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                .getReference("usernames").child(username);

        // check uniqueness then create auth user and write DB
        usernameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
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
                                saveUserData(firebaseUser.getUid(), username, email, name, accountType, true);
                            }
                        });
                    } else {
                        setLoading(false);
                        showSnackbar("Registration failed: " +
                                (task.getException() != null ? task.getException().getMessage() : ""));
                    }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                showSnackbar("Database error: " + error.getMessage());
            }
        });
    }

    // Save both user_secure and users nodes; called after username index is reserved
    private void saveUserData(String uid, String username, String email, String name, String accountType, boolean isEmailRegistration) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL).getReference();

        dbRef.child("user_secure").child(uid).setValue(new UserSecure(uid, username, email));
        dbRef.child("users").child(uid).setValue(new User(username, name, email, accountType, uid))
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        showSnackbar("Registration successful!");
                        if (isEmailRegistration) {
                            auth.signOut();
                            startActivity(new Intent(this, LoginActivity.class));
                        } else {
                            redirectUser(accountType);
                        }
                        finish();
                    } else {
                        showSnackbar("Failed to save profile info: " +
                                (task.getException() != null ? task.getException().getMessage() : ""));
                    }
                });
    }

    // ---------------- Helpers ----------------
    private boolean isValidUsername(String username) {
        return username.matches("^[a-z0-9]{5,}$");
    }

    // returns true when password is weak
    private boolean isPasswordWeak(String password) {
        return !password.matches("^(?=.*[a-z])(?=.*[0-9])(?=.*[@$!%*?&])[A-Za-z0-9@$!%*?&]{8,}$");
    }

    private void setupPasswordValidation() {
        if (passwordEdit != null) {
            passwordEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isPasswordWeak(s.toString())) {
                        passwordLayout.setError("Weak password: Min 8 chars, 1 lowercase, 1 number, 1 symbol");
                    } else passwordLayout.setError(null);
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

    private String getText(TextInputLayout layout) {
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        return (editText != null) ? Objects.requireNonNull(editText.getText()).toString().trim() : "";
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

    private String suggestUsername(String displayName) {
        String base = displayName == null ? "user" : displayName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        if (base.length() < 5) base += "user";
        return base + new Random().nextInt(1000);
    }

    // Prompt dialog that lets the user choose username; invokes listener with chosen username
    private void promptForGoogleUsername(String suggested, OnUsernameChosenListener listener) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_username_prompt, null);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.usernameInputLayout);
        TextInputEditText inputEdit = (TextInputEditText) inputLayout.getEditText();
        if (inputEdit != null) inputEdit.setText(suggested);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle("Choose a username")
                .setPositiveButton("Continue", (dialog, which) -> {
                    if (inputEdit != null) {
                        String chosen = Objects.requireNonNull(inputEdit.getText()).toString().trim();
                        if (isValidUsername(chosen)) listener.onUsernameChosen(chosen);
                        else showSnackbar("Invalid username (min 5 chars, lowercase letters & numbers only)");
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> auth.signOut())
                .setCancelable(false)
                .show();
    }

    private void redirectUser(String accountType) {
        Intent intent = "owner".equalsIgnoreCase(accountType) ?
                new Intent(this, OwnerDashboardActivity.class) :
                new Intent(this, UserDashboardActivity.class);
        startActivity(intent);
        finish();
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    private interface OnUsernameChosenListener {
        void onUsernameChosen(String username);
    }
}
