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

public class UserDashboardActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;
    private ImageButton btnHamburger, btnLogout, btnThemeToggle;
    private TextView tvWelcome, drawerUserName;

    private long backPressedTime = 0;
    private Toast backToast;

    private static final String KEY_USERNAME = "username";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FysaApp.applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_dashboard);

        FysaApp.setStatusBarColor(this);

        initViews();
        setupTopBar();
        setupDrawerActions();
        setupBottomNavigation();
        loadUsernameFromDatabase();
        setupBackPressHandler();
        handleNotificationIntent(getIntent());

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
        tvWelcome = findViewById(R.id.tvWelcome);
        drawerUserName = navigationView.getHeaderView(0).findViewById(R.id.tvUserName);
    }

    private void setupTopBar() {
        btnHamburger.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnLogout.setOnClickListener(v -> logout());

        if (btnThemeToggle != null) {
            FysaApp.updateThemeIcon(this, btnThemeToggle, false);
            btnThemeToggle.setOnClickListener(v ->
                    FysaApp.toggleThemeWithAnimation(UserDashboardActivity.this, btnThemeToggle)
            );
        }
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
                bottomNavigationView.setSelectedItemId(R.id.nav_my_bookings);

            } else if (itemId == R.id.nav_settings) {
                fragment = new SettingsFragment();
                bottomNavigationView.setSelectedItemId(R.id.nav_settings);

            } else if (itemId == R.id.nav_faq) {
                fragment = new FaqFragment();
                // add to back stack so back goes to previous fragment
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .addToBackStack(null)
                        .commit();
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;

            } else if (itemId == R.id.nav_contact_us) {
                fragment = new ContactUsFragment();
                // ✅ Add to back stack so back returns to previous fragment
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .addToBackStack(null)
                        .commit();
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;

            } else if (itemId == R.id.nav_logout) {
                logout();
            }

            if (fragment != null) {
                replaceFragment(fragment);
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_find_space) {
                fragment = new FindYourSpaceFragment();
            } else if (itemId == R.id.nav_my_bookings) {
                fragment = new MyBookingsFragment();
            } else if (itemId == R.id.nav_settings) {
                fragment = new SettingsFragment();
            }

            if (fragment != null) {
                replaceFragment(fragment, false); // bottom nav should not add to back stack
                return true;
            }
            return false;
        });
    }

    private void replaceFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void replaceFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        if (addToBackStack) transaction.addToBackStack(null);
        transaction.commit();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        FysaApp.getAppContext()
                .getSharedPreferences("theme_prefs", MODE_PRIVATE)
                .edit()
                .remove(KEY_USERNAME)
                .apply();

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

        String uid = user.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance(
                "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("users").child(uid);

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
                        handleCustomBackPressed();
                    }
                });
    }

    private void handleCustomBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }

        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return;
        }

        int currentItemId = bottomNavigationView.getSelectedItemId();
        if (currentItemId != R.id.nav_find_space) {
            bottomNavigationView.setSelectedItemId(R.id.nav_find_space);
            return;
        }

        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            if (backToast != null) backToast.cancel();
            finish();
        } else {
            backToast = Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT);
            backToast.show();
        }
        backPressedTime = System.currentTimeMillis();
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent != null && intent.hasExtra("open_fragment")) {
            String fragmentToOpen = intent.getStringExtra("open_fragment");
            if ("my_bookings".equals(fragmentToOpen)) {
                bottomNavigationView.setSelectedItemId(R.id.nav_my_bookings);
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
        FysaApp.applySavedTheme();
        FysaApp.setStatusBarColor(this);
        FysaApp.updateThemeIcon(this, btnThemeToggle, false);
    }

    // ✅ Allow fragments to navigate to default fragment
    public void navigateToDefaultFragment() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_find_space);
        }
    }
}
