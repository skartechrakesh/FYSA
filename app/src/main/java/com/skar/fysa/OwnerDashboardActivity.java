package com.skar.fysa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class OwnerDashboardActivity extends AppCompatActivity {

    private static final String KEY_USERNAME = "username";
    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;
    private ImageButton btnHamburger, btnLogout, btnThemeToggle;
    private TextView tvWelcome, drawerUserName;
    private long backPressedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FysaApp.applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_dashboard);
        FysaApp.setStatusBarColor(this);

        initViews();
        setupTopBar();
        setupDrawerActions();
        setupBottomNavigation();
        loadUsernameFromDatabase();
        setupBackPressHandler();

        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_add_new_space);
        }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        btnHamburger = findViewById(R.id.btnHamburger);
        btnLogout = findViewById(R.id.btnLogout);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        tvWelcome = findViewById(R.id.tvWelcome);
        drawerUserName = navigationView.getHeaderView(0).findViewById(R.id.tvOwnerName);
    }

    private void setupTopBar() {
        btnHamburger.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnLogout.setOnClickListener(v -> showLogoutSnackbar());

        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
        btnThemeToggle.setOnClickListener(v ->
                FysaApp.toggleThemeWithAnimation(OwnerDashboardActivity.this, btnThemeToggle)
        );
    }

    private void setupDrawerActions() {
        navigationView.setNavigationItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_add_new_space0) {
                fragment = new AddNewSpaceFragment();
                bottomNavigationView.setSelectedItemId(R.id.nav_add_new_space);

            } else if (itemId == R.id.nav_manage_spaces) {
                fragment = new ManageSpacesFragment();
                bottomNavigationView.setSelectedItemId(R.id.nav_manage_spaces);

            } else if (itemId == R.id.nav_bookings_dashboard) {
                fragment = new BookingsOverviewFragment();
                bottomNavigationView.setSelectedItemId(R.id.nav_bookings);

            } else if (itemId == R.id.nav_contact_us) {
                fragment = new ContactUsFragment();
                openFragment(fragment);
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;

            } else if (itemId == R.id.nav_faq) {
                fragment = new FaqFragment();
                openFragment(fragment);
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;

            } else if (itemId == R.id.nav_settings) {
                fragment = new SettingsFragment();
                openFragment(fragment);
                bottomNavigationView.setSelectedItemId(R.id.nav_preferences);
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;

            } else if (itemId == R.id.nav_logout) {
                showLogoutSnackbar();
                return true;
            }

            if (fragment != null && itemId != R.id.nav_contact_us
                    && itemId != R.id.nav_faq && itemId != R.id.nav_preferences) {
                replaceFragment(fragment);
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;

            if (item.getItemId() == R.id.nav_manage_spaces) {
                fragment = new ManageSpacesFragment();
            } else if (item.getItemId() == R.id.nav_add_new_space) {
                fragment = new AddNewSpaceFragment();
            } else if (item.getItemId() == R.id.nav_bookings) {
                fragment = new BookingsOverviewFragment();
            } else if (item.getItemId() == R.id.nav_preferences) {
                fragment = new SettingsFragment();
            }

            if (fragment != null) {
                replaceFragment(fragment);
                return true;
            }
            return false;
        });
    }

    private void replaceFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    private void openFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void showLogoutSnackbar() {
        Snackbar snackbar = Snackbar.make(drawerLayout, "Are you sure you want to logout?", Snackbar.LENGTH_LONG)
                .setAnchorView(bottomNavigationView)
                .setAction("Logout", v -> performLogout());
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.red_800));
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.white));
        snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.white));
        snackbar.show();
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        if (FysaApp.getAppContext() != null) {
            FysaApp.getAppContext().getSharedPreferences("theme_prefs", MODE_PRIVATE)
                    .edit().remove(KEY_USERNAME).apply();
        }
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @SuppressLint("SetTextI18n")
    private void loadUsernameFromDatabase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvWelcome.setText("Hi, User");
            drawerUserName.setText("Welcome User");
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users").child(user.getUid());

        userRef.child("name").get().addOnCompleteListener(task -> {
            String displayName = "User";

            if (task.isSuccessful() && task.getResult().exists()) {
                String nameFromDb = task.getResult().getValue(String.class);
                if (nameFromDb != null && !nameFromDb.isEmpty()) displayName = nameFromDb;

                FysaApp.getAppContext()
                        .getSharedPreferences("theme_prefs", MODE_PRIVATE)
                        .edit()
                        .putString(KEY_USERNAME, displayName)
                        .apply();
            } else {
                displayName = FysaApp.getAppContext()
                        .getSharedPreferences("theme_prefs", MODE_PRIVATE)
                        .getString(KEY_USERNAME, "User");
            }

            tvWelcome.setText("Hi, " + displayName);
            drawerUserName.setText(displayName);
        });
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                            drawerLayout.closeDrawer(GravityCompat.START);
                            return;
                        }

                        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                            getSupportFragmentManager().popBackStack();
                            return;
                        }

                        if (bottomNavigationView.getSelectedItemId() != R.id.nav_add_new_space) {
                            bottomNavigationView.setSelectedItemId(R.id.nav_add_new_space);
                            return;
                        }

                        if (backPressedTime + 2000 > System.currentTimeMillis()) {
                            finish();
                        } else {
                            Snackbar snackbar = Snackbar.make(drawerLayout, "Press back again to exit", Snackbar.LENGTH_SHORT)
                                    .setAnchorView(bottomNavigationView);
                            snackbar.setBackgroundTint(ContextCompat.getColor(OwnerDashboardActivity.this, R.color.blue_400));
                            snackbar.setTextColor(ContextCompat.getColor(OwnerDashboardActivity.this, R.color.black));
                            snackbar.show();
                        }
                        backPressedTime = System.currentTimeMillis();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FysaApp.applySavedTheme();
        FysaApp.setStatusBarColor(this);
        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
    }

    public void navigateToDefaultFragment() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_add_new_space);
        }
    }
}
