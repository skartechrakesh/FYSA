package com.skar.fysa;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class UserDashboardActivity extends AppCompatActivity {

    private static final String KEY_USERNAME = "username";
    private static final String DB_URL = "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static final String PREFS_NAME = "theme_preference";
    private static final String KEY_THEME_MODE = "theme_mode";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;
    private ImageButton btnHamburger, btnLogout, btnThemeToggle;
    private TextView drawerUserName;
    private long backPressedTime = 0;
    private AdView adViewBannerUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedTheme = sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_dashboard);

        initViews();
        setupTopBar();
        setupDrawerActions();
        setupBottomNavigation();
        loadUsernameFromDatabase();
        setupBackPressHandler();
        setupBannerAd();

        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_find_space);
        }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        btnHamburger = findViewById(R.id.btnHamburger);
        btnLogout = findViewById(R.id.btnLogout);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        drawerUserName = navigationView.getHeaderView(0).findViewById(R.id.tvUserName);
        adViewBannerUser = findViewById(R.id.adViewBannerUser);
    }
    private void setupBannerAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        adViewBannerUser.loadAd(adRequest);
    }

    private void setupTopBar() {
        btnHamburger.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnLogout.setOnClickListener(v -> showLogoutSnackbar());
        btnThemeToggle.setOnClickListener(v -> toggleTheme(this));
    }

    private void setupDrawerActions() {
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // Close the drawer immediately for any selection
            drawerLayout.closeDrawer(GravityCompat.START);

            if (itemId == R.id.nav_find_space) {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
                if (currentFragment instanceof FindYourSpaceFragment) {
                    return true;
                }
                replaceFragment(new FindYourSpaceFragment());
                bottomNavigationView.setSelectedItemId(R.id.nav_find_space);
            } else if (itemId == R.id.nav_my_bookings) {
                replaceFragment(new MyBookingsFragment());
                bottomNavigationView.setSelectedItemId(R.id.nav_my_bookings);
            } else if (itemId == R.id.nav_settings) {
                replaceFragment(new SettingsFragment());
                bottomNavigationView.setSelectedItemId(R.id.nav_settings);
            } else if (itemId == R.id.nav_faq) {
                // ✅ CHANGE: This logic specifically handles the FAQ's screen
                // It replaces the current fragment without adding to the back stack
                getSupportFragmentManager().popBackStack(); // Clear any existing fragments
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new FaqFragment())
                        .addToBackStack("faq_tag") // ✅ ADDED: Add a unique tag to the back stack
                        .commit();
                return true;
            } else if (itemId == R.id.nav_logout) {
                showLogoutSnackbar();
                return true;
            }

            return true;
        });
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_SELECTED);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;

            if (item.getItemId() == R.id.nav_find_space) {
                fragment = new FindYourSpaceFragment();
            } else if (item.getItemId() == R.id.nav_my_bookings) {
                fragment = new MyBookingsFragment();
            } else if (item.getItemId() == R.id.nav_settings) {
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
        getSupportFragmentManager().popBackStack();
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
        getSharedPreferences("theme_prefs", MODE_PRIVATE).edit().remove(KEY_USERNAME).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @SuppressLint("SetTextI18n")
    private void loadUsernameFromDatabase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            drawerUserName.setText("Welcome User");
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance(DB_URL).getReference("users").child(user.getUid());

        userRef.child("name").get().addOnCompleteListener(task -> {
            String displayName = "User";
            if (task.isSuccessful() && task.getResult().exists()) {
                String nameFromDb = task.getResult().getValue(String.class);
                if (nameFromDb != null && !nameFromDb.isEmpty()) displayName = nameFromDb;
                getSharedPreferences("theme_prefs", MODE_PRIVATE)
                        .edit()
                        .putString(KEY_USERNAME, displayName)
                        .apply();
            } else {
                displayName = getSharedPreferences("theme_prefs", MODE_PRIVATE).getString(KEY_USERNAME, "User");
            }
            drawerUserName.setText("Welcome, " + displayName);
        });
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
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

                if (bottomNavigationView.getSelectedItemId() != R.id.nav_find_space) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_find_space);
                    return;
                }

                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish();
                } else {
                    Snackbar snackbar = Snackbar.make(drawerLayout, "Press back again to exit", Snackbar.LENGTH_SHORT)
                            .setAnchorView(bottomNavigationView);
                    snackbar.setBackgroundTint(ContextCompat.getColor(UserDashboardActivity.this, R.color.blue_400));
                    snackbar.setTextColor(ContextCompat.getColor(UserDashboardActivity.this, R.color.black));
                    snackbar.show();
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
    }


    private void toggleTheme(Context context) {
        int nightMode = AppCompatDelegate.getDefaultNightMode();
        int newNightMode = (nightMode == AppCompatDelegate.MODE_NIGHT_YES) ?
                AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_THEME_MODE, newNightMode);
        editor.apply();
        AppCompatDelegate.setDefaultNightMode(newNightMode);
        recreate();
    }
}