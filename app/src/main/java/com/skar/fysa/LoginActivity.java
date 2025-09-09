package com.skar.fysa;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
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

public class LoginActivity extends AppCompatActivity {

    private ImageButton btnThemeToggle;
    private Button btnLoginSubmit;
    private TextView linkRegister, linkForgotPassword;
    private ProgressBar progressBar;
    private TextInputLayout emailLayout, passwordLayout;
    private SignInButton btnGoogleSignIn;

    private FirebaseAuth auth;
    private DatabaseReference usersRef;
    private GoogleSignInClient googleSignInClient;

    private final String FIREBASE_DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                            try {
                                GoogleSignInAccount account = task.getResult(ApiException.class);
                                if (account != null) firebaseAuthWithGoogle(account.getIdToken());
                            } catch (ApiException e) {
                                setLoading(false);
                                showSnackbar("Google Sign-In failed: " + e.getMessage());
                            }
                        } else {
                            setLoading(false);
                            showSnackbar("Google Sign-In cancelled.");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        FysaApp.setStatusBarColor(this);

        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnLoginSubmit = findViewById(R.id.btnLoginSubmit);
        linkRegister = findViewById(R.id.linkRegister);
        linkForgotPassword = findViewById(R.id.linkForgotPassword);
        progressBar = findViewById(R.id.progressBar);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);

        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL).getReference("users");

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
        btnThemeToggle.setOnClickListener(v -> FysaApp.toggleThemeWithAnimation(this, btnThemeToggle));

        btnLoginSubmit.setOnClickListener(v -> performLogin());
        linkRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        linkForgotPassword.setOnClickListener(v -> sendPasswordResetEmail());
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());

        FysaApp.setupTextInputStrokes(this, emailLayout, passwordLayout);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            redirectToDashboard(currentUser.getUid());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
        FysaApp.setStatusBarColor(this);
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

    private void signInWithGoogle() {
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            // Dark mode → external browser sign-in
            signInWithGoogleExternal();
        } else {
            // Light mode → default Google sign-in intent
            setLoading(true);
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
        }
    }

    private void signInWithGoogleExternal() {
        setLoading(true);

        OAuthProvider.Builder provider = OAuthProvider.newBuilder("google.com");
        auth.startActivityForSignInWithProvider(this, provider.build())
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        redirectToDashboard(user.getUid());
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar("Google sign-in failed: " + e.getMessage());
                });
    }



    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        FirebaseUser user = auth.getCurrentUser();
                        usersRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    redirectToDashboard(user.getUid());
                                } else {
                                    setLoading(false);
                                    auth.signOut();
                                    showSnackbar("Please register first with this account.");
                                }
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                setLoading(false);
                                showSnackbar("Database Error: " + error.getMessage());
                            }
                        });
                    } else {
                        setLoading(false);
                        showSnackbar("Firebase Authentication failed: " +
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
}
