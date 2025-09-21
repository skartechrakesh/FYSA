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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserDashboardActivity extends AppCompatActivity {

    private static final String KEY_USERNAME = "username";
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
        drawerUserName = navigationView.getHeaderView(0).findViewById(R.id.tvOUName);
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
            Fragment fragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_find_space) {
                fragment = new FindYourSpaceFragment();
                bottomNavigationView.setSelectedItemId(R.id.nav_find_space);
            } else if (itemId == R.id.nav_my_bookings) {
                fragment = new MyBookingsFragment();
                replaceFragment(fragment);
                bottomNavigationView.setSelectedItemId(R.id.nav_my_bookings);
            } else if (itemId == R.id.nav_faq) {
                fragment = new FaqFragment();
                openFragment(fragment);
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (itemId == R.id.nav_settings2) {
                fragment = new SettingsFragment();
                replaceFragment(fragment);
                bottomNavigationView.setSelectedItemId(R.id.nav_settings1);
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (itemId == R.id.nav_logout) {
                showLogoutSnackbar();
                return true;
            }

            if (fragment != null && itemId != R.id.nav_faq && itemId != R.id.nav_settings1) {
                replaceFragment(fragment);
            }
            drawerLayout.closeDrawer(GravityCompat.START);
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
            } else if (item.getItemId() == R.id.nav_settings1) {
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

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        firestore.collection("users").document(user.getUid()).get()
                .addOnCompleteListener(task -> {
                    String displayName = "User";

                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        DocumentSnapshot snapshot = task.getResult();
                        String nameFromDb = snapshot.getString("name");
                        if (nameFromDb != null && !nameFromDb.isEmpty()) displayName = nameFromDb;

                        getSharedPreferences("theme_prefs", MODE_PRIVATE)
                                .edit()
                                .putString(KEY_USERNAME, displayName)
                                .apply();
                    } else {
                        displayName = getSharedPreferences("theme_prefs", MODE_PRIVATE)
                                .getString(KEY_USERNAME, "User");
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
