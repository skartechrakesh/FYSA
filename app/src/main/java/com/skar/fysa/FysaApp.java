package com.skar.fysa;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputLayout;

public class FysaApp extends Application {

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_IS_DARK_MODE = "is_dark_mode";

    private static Context appContext; // store global context

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext(); // keep reference
        applySavedTheme(); // apply theme globally
    }

    // ---------------- THEME HANDLING ----------------

    public static void applySavedTheme() {
        boolean isDarkMode = isDarkModeEnabled(appContext);
        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    public static void toggleTheme(@NonNull Context context) {
        boolean isDarkMode = isDarkModeEnabled(context);
        getPrefs(context).edit().putBoolean(KEY_IS_DARK_MODE, !isDarkMode).apply();

        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES
        );
    }

    public static boolean isDarkModeEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_IS_DARK_MODE, false);
    }

    // ---------------- SHARED PREFERENCES HELPERS ----------------

    private static SharedPreferences getPrefs(Context context) {
        Context ctx = (context != null) ? context : appContext;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveString(@NonNull Context context, @NonNull String key, @NonNull String value) {
        getPrefs(context).edit().putString(key, value).apply();
    }

    public static String getString(@NonNull Context context, @NonNull String key, @NonNull String defaultValue) {
        return getPrefs(context).getString(key, defaultValue);
    }

    // ---------------- THEME TOGGLE WITH ANIMATION ----------------

    public static void toggleThemeWithAnimation(@NonNull Activity activity, @NonNull ImageButton toggleButton) {
        toggleButton.animate().rotationBy(360f).alpha(0f).setDuration(500).withEndAction(() -> {
            toggleButton.setAlpha(1f);

            // Toggle theme
            toggleTheme(activity);

            // Update icon
            updateThemeIcon(activity, toggleButton, true);

            // Apply status bar color instantly
            setStatusBarColor(activity);
        }).start();
    }

    public static void updateThemeIcon(@NonNull Context context, @NonNull ImageButton toggleButton, boolean animate) {
        boolean isDarkMode = isDarkModeEnabled(context);
        toggleButton.setImageResource(isDarkMode ? R.drawable.ic_sun : R.drawable.ic_moon);

        if (animate) {
            toggleButton.setScaleX(0.7f);
            toggleButton.setScaleY(0.7f);
            toggleButton.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
    }

    // ---------------- STATUS BAR HANDLING ----------------

    public static void setStatusBarColor(Activity activity) {
        if (activity == null) return;

        int color = ContextCompat.getColor(activity, R.color.bgColor);
        activity.getWindow().setStatusBarColor(color);

        View decor = activity.getWindow().getDecorView();
        boolean isDarkMode = isDarkModeEnabled(activity);
        decor.setSystemUiVisibility(!isDarkMode ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
    }

    // ---------------- INPUT FIELD STYLING ----------------

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

    // ---------------- CONTEXT ACCESSOR ----------------

    public static Context getAppContext() {
        return appContext;
    }
}
