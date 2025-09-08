package com.skar.fysa;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ContactUsFragment extends Fragment {

    private LinearLayout layoutPhone, layoutEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contact_us, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutPhone = view.findViewById(R.id.layoutPhone);
        layoutEmail = view.findViewById(R.id.layoutEmail);

        // Tap phone -> open dialer
        layoutPhone.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:+919876543210"));
            startActivity(intent);
        });

        // Tap email -> open email app
        layoutEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@fysa.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Support Request");
            startActivity(intent);
        });

    }
}
