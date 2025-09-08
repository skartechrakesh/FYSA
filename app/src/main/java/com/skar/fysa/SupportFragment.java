package com.skar.fysa;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SupportFragment extends Fragment {

    private ImageButton btnEmailSupport;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate fragment layout (without top bar)
        View view = inflater.inflate(R.layout.fragment_support, container, false);

        btnEmailSupport = view.findViewById(R.id.btnEmailSupport);

        btnEmailSupport.setOnClickListener(v -> openEmailSupport());

        return view;
    }

    /** Launch email app to contact support */
    private void openEmailSupport() {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:support@skar.com"));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Support Request");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Hi SKAR Support,\n\n");

        try {
            startActivity(Intent.createChooser(emailIntent, "Send Email"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "No email app found", Toast.LENGTH_SHORT).show();
        }
    }
}
