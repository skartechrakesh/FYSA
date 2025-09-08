package com.skar.fysa;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class OwnerDashboardActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;
    private ImageButton btnHamburger, btnLogout, btnThemeToggle;
    private TextView tvWelcome, drawerUserName;

    private long backPressedTime = 0;
    private Toast backToast;

    private static final String KEY_USERNAME = "username";
    private static final String DB_URL =
            "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FysaApp.applySavedTheme(); // ✅ Apply saved theme
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_dashboard);
        FysaApp.setStatusBarColor(this);

        initViews();
        setupTopBar();          // ✅ includes theme toggle
        setupDrawerActions();
        setupBottomNavigation();
        loadUsernameFromDatabase();
        setupBackPressHandler();
        handleNotificationIntent(getIntent());

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
        btnLogout.setOnClickListener(v -> logout());

        if (btnThemeToggle != null) {
            // ✅ Update icon based on current theme
            FysaApp.updateThemeIcon(this, btnThemeToggle, false);
            btnThemeToggle.setOnClickListener(v ->
                    FysaApp.toggleThemeWithAnimation(OwnerDashboardActivity.this, btnThemeToggle)
            );
        }
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
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .addToBackStack(null)
                        .commit();
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;

            } else if (itemId == R.id.nav_faq) {
                fragment = new FaqFragment();
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .addToBackStack(null)
                        .commit();
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;

            } else if (itemId == R.id.nav_settings) {
                fragment = new SettingsFragment();
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .addToBackStack(null)
                        .commit();
                bottomNavigationView.setSelectedItemId(R.id.nav_preferences); // ✅ Update bottom nav when opened from drawer
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;

            } else if (itemId == R.id.nav_logout) {
                logout();
                return true;
            }

            if (fragment != null && itemId != R.id.nav_contact_us && itemId != R.id.nav_faq && itemId != R.id.nav_settings) {
                replaceFragment(fragment, false);
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
                replaceFragment(fragment, false);
                return true;
            }
            return false;
        });
    }

    private void replaceFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        if (addToBackStack) transaction.addToBackStack(null);
        transaction.commit();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        if (FysaApp.getAppContext() != null) {
            FysaApp.getAppContext().getSharedPreferences("theme_prefs", MODE_PRIVATE)
                    .edit().remove(KEY_USERNAME).apply();
        }
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

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

                        int currentItemId = bottomNavigationView.getSelectedItemId();
                        if (currentItemId != R.id.nav_add_new_space) {
                            bottomNavigationView.setSelectedItemId(R.id.nav_add_new_space);
                            return;
                        }

                        if (backPressedTime + 2000 > System.currentTimeMillis()) {
                            if (backToast != null) backToast.cancel();
                            finish();
                        } else {
                            backToast = Toast.makeText(OwnerDashboardActivity.this, "Press back again to exit", Toast.LENGTH_SHORT);
                            backToast.show();
                        }
                        backPressedTime = System.currentTimeMillis();
                    }
                });
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent != null && intent.hasExtra("openFragment")) {
            String fragmentToOpen = intent.getStringExtra("openFragment");
            if ("bookings_overview".equals(fragmentToOpen)) {
                bottomNavigationView.setSelectedItemId(R.id.nav_bookings);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotificationIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FysaApp.applySavedTheme(); // ✅ Ensure theme is applied on resume
        FysaApp.setStatusBarColor(this);
        FysaApp.updateThemeIcon(this, btnThemeToggle, false); // ✅ Keep icon synced
    }

    public void navigateToDefaultFragment() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_add_new_space);
        }
    }
}
