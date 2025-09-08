package com.skar.fysa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PrivacyPolicyFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_privacy_policy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvPrivacyPolicy = view.findViewById(R.id.tvPrivacyPolicy);

        // Replace below text with your actual privacy policy
        String privacyText = "\n" + "1. We respect your privacy and do not share your personal information.\n\n" +
                "2. All user data is stored securely in Firebase.\n\n" +
                "3. You can contact us for any data-related requests.\n\n" +
                "4. By using this app, you agree to our terms and privacy policy.";

        tvPrivacyPolicy.setText(privacyText);
    }
}
