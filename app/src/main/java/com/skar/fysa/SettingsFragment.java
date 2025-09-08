package com.skar.fysa;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;

public class SettingsFragment extends Fragment {

    private static final String USER_PREFS = "user_prefs";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Handle back press: child fragments first, then default fragment
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (requireActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                            requireActivity().getSupportFragmentManager().popBackStack();
                        } else {
                            if (requireActivity() instanceof UserDashboardActivity) {
                                ((UserDashboardActivity) requireActivity()).navigateToDefaultFragment();
                            } else if (requireActivity() instanceof OwnerDashboardActivity) {
                                ((OwnerDashboardActivity) requireActivity()).navigateToDefaultFragment();
                            }
                        }
                    }
                });

        // Menu item click listeners
        view.findViewById(R.id.itemAccountSecurity).setOnClickListener(v -> {
            AccountAndSecurityFragment accountAndSecurityFragment = new AccountAndSecurityFragment();
            Bundle args = new Bundle();
            args.putString("source", "settings");
            accountAndSecurityFragment.setArguments(args);
            openFragment(accountAndSecurityFragment);
        });

        view.findViewById(R.id.itemPrivacyPolicy).setOnClickListener(v -> {
            PrivacyPolicyFragment privacyPolicyFragment = new PrivacyPolicyFragment();
            Bundle args = new Bundle();
            args.putString("source", "settings");
            privacyPolicyFragment.setArguments(args);
            openFragment(privacyPolicyFragment);
        });

        view.findViewById(R.id.itemHelp).setOnClickListener(v -> {
            SupportFragment supportFragment = new SupportFragment();
            Bundle args = new Bundle();
            args.putString("source", "settings");
            supportFragment.setArguments(args);
            openFragment(supportFragment);
        });

        view.findViewById(R.id.itemFaq).setOnClickListener(v -> {
            FaqFragment faqFragment = new FaqFragment();
            Bundle args = new Bundle();
            args.putString("source", "settings");
            faqFragment.setArguments(args);
            openFragment(faqFragment);
        });

        view.findViewById(R.id.itemAbout).setOnClickListener(v -> {
            AboutAppFragment aboutAppFragment = new AboutAppFragment();
            Bundle args = new Bundle();
            args.putString("source", "settings");
            aboutAppFragment.setArguments(args);
            openFragment(new AboutAppFragment());
        });

        view.findViewById(R.id.itemLogout).setOnClickListener(v -> logout());
    }

    private void openFragment(Fragment fragment) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null) // ensures back navigates back to Settings
                .commit();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finishAffinity();
    }
}
