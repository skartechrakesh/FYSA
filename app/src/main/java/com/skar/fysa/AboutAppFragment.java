package com.skar.fysa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AboutAppFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about_app, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvAppName = view.findViewById(R.id.tvAppName);
        TextView tvTitleDescription = view.findViewById(R.id.tvTitleDescription);
        TextView tvVersion = view.findViewById(R.id.tvVersion);
        TextView tvDeveloper = view.findViewById(R.id.tvDeveloper);
        TextView tvDescription = view.findViewById(R.id.tvDescription);

        tvAppName.setText("FYSA");
        tvTitleDescription.setText("(Find Your Space Anywhere)");
        tvVersion.setText("Version: 1.0.0");
        tvDeveloper.setText("Developer: Rakesh Reddy Battula");
        tvDescription.setText("Fysa is your ultimate app to find, manage, and book spaces easily.");
    }
}
