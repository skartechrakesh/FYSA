package com.skar.fysa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AddNewSpaceFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_add_new_space, container, false);

        // Initialize buttons
        Button btnHostel = view.findViewById(R.id.btnHostel);
        Button btnPg = view.findViewById(R.id.btnPg);
        Button btnBachelorRoom = view.findViewById(R.id.btnBachelorRoom);
        Button btnIndividualHouse = view.findViewById(R.id.btnIndividualHouse);
        Button btnFlat = view.findViewById(R.id.btnFlat);

        // Setup click listeners to open SpaceFormFragment
        btnHostel.setOnClickListener(v -> openForm("Hostel"));
        btnPg.setOnClickListener(v -> openForm("PG"));
        btnBachelorRoom.setOnClickListener(v -> openForm("Room"));
        btnIndividualHouse.setOnClickListener(v -> openForm("House"));
        btnFlat.setOnClickListener(v -> openForm("Flat"));

        return view;
    }

    /** Open SpaceFormFragment with category argument */
    private void openForm(String category) {
        if (getActivity() == null) return;

        // Create new SpaceFormFragment and pass category
        SpaceFormFragment fragment = new SpaceFormFragment();
        Bundle args = new Bundle();
        args.putString("CATEGORY", category);
        fragment.setArguments(args);

        // Replace current fragment and add to back stack
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment) // fragmentContainer is your activity's FrameLayout
                .addToBackStack(null) // ensures back button returns to AddNewSpaceFragment
                .commit();
    }
}
