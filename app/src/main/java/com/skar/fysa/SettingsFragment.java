package com.skar.fysa;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;

public class SettingsFragment extends Fragment {

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

        // Menu item click listeners
        view.findViewById(R.id.itemAccountSecurity).setOnClickListener(v -> {
            AccountAndSecurityFragment fragment = new AccountAndSecurityFragment();
            Bundle args = new Bundle();
            args.putString("source", "settings");
            fragment.setArguments(args);
            openFragment(fragment);
        });

        view.findViewById(R.id.itemPrivacyPolicy).setOnClickListener(v -> {
            String url = "https://sites.google.com/view/fysa-privacy-policy";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(url));
            startActivity(intent);
        });

        view.findViewById(R.id.itemHelp).setOnClickListener(v -> {
            SupportFragment fragment = new SupportFragment();
            Bundle args = new Bundle();
            args.putString("source", "settings");
            fragment.setArguments(args);
            openFragment(fragment);
        });

        view.findViewById(R.id.itemFaq).setOnClickListener(v -> {
            FaqFragment fragment = new FaqFragment();
            Bundle args = new Bundle();
            args.putString("source", "settings");
            fragment.setArguments(args);
            openFragment(fragment);
        });

        view.findViewById(R.id.itemTermsOfService).setOnClickListener(v -> {
            String url = "https://sites.google.com/view/fysa-terms-of-service";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(url));
            startActivity(intent);
        });

        view.findViewById(R.id.itemAbout).setOnClickListener(v -> {
            AboutAppFragment fragment = new AboutAppFragment();
            Bundle args = new Bundle();
            args.putString("source", "settings");
            fragment.setArguments(args);
            openFragment(fragment);
        });

        view.findViewById(R.id.itemLogout).setOnClickListener(v -> logout());
    }

    // Helper method to open a fragment
    private void openFragment(Fragment fragment) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null) // keeps Settings in back stack
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
