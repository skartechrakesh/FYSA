package com.skar.fysa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class FaqFragment extends Fragment {

    private LinearLayout faqContainer;
    private String accountType = "user"; // default
    private View rootView;

    private FirebaseFirestore firestore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faq, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootView = view;
        faqContainer = view.findViewById(R.id.layoutFaqContainer);
        firestore = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showSnackbar("User not logged in.");
            return;
        }

        // Fetch accountType from Firestore
        firestore.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(this::handleUserSnapshot)
                .addOnFailureListener(e -> showSnackbar("Failed to load account type: " + e.getMessage()));
    }

    private void handleUserSnapshot(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) return;

        String type = snapshot.getString("accountType");
        accountType = type != null ? type : "user";

        loadFaqsForAccountType(accountType);
    }

    private void loadFaqsForAccountType(String accountType) {
        faqContainer.removeAllViews();

        if ("owner".equalsIgnoreCase(accountType)) {
            // Owner-specific FAQs
            addFaq("How do I add or manage my space listings?",
                    "You can add a new space by navigating to the 'Add Space' or 'List Your Space' section. To manage an existing space, go to the 'My Spaces' section on your dashboard. From there, you can view details, edit information, or unlist the space.");

            addFaq("How do I manage the availability of my space?",
                    "To set your space as available or unavailable, go to the 'My Spaces' section on your dashboard. Tap the edit icon next to your space's name to open the 'Edit Space' screen. On this screen, you can use the 'Yes/No' dropdown to set its vacancy status. Setting it to 'No' will hide your listing from other users.");

            addFaq("How do I receive booking requests for my spaces?",
                    "When a user books a visit to your space, a booking request will appear in the 'Bookings' or 'Requests' section of your dashboard. You will also receive an in-app notification to inform you of the new request.");

            addFaq("What information can I see about a booking request?",
                    "For each booking, you can see the user's details (such as their name and contact information if available), the date and time of the requested visit, and the name of the space they want to book.");

            addFaq("Can I see a user's booking history?",
                    "The dashboard provides information on the current booking request. For privacy reasons, you may not be able to see a user's complete booking history with other properties.");

            addFaq("How do I approve or decline a booking request?",
                    "The dashboard is designed to automatically handle booking requests. As the app's primary function is to facilitate visits and connections, all booking requests are considered 'Booked' unless the user cancels. You can communicate with the user via the provided contact information.");

            addFaq("A user has booked a visit but hasn't shown up. What should I do?",
                    "We recommend contacting the user directly using the provided contact details. Communication is key to confirming a visit. The platform tracks the booking but relies on direct communication to finalize the visit.");

            addFaq("How can I update my profile information, such as my name and contact details?",
                    "Go to your account or profile settings. There, you will find options to edit your personal information, including your name, email, and contact number.");

            addFaq("Can I change the location of my listed space?",
                    "The location is set at the time of listing. To change it, you must delete or unlist the existing space and create a new listing with the corrected location. This ensures the map data remains accurate for all users.");
        } else {
            // User-specific FAQs
            addFaq("How do I find a space to visit?",
                    "To find a space, first select the category you are interested in (e.g., Office, Shop, Home). The app will then automatically list all available spaces under that category within a 5 km radius of your location.");

            addFaq("How do I see a space's location?",
                    "Each space's details page includes a map view that shows its exact location. You can tap on the map to view a larger map or get directions.");

            addFaq("How do I book a visit to a space?",
                    "To book a visit, go to a space's details page and simply click the 'Book Visit' button. The owner of the space will receive a notification of your request.");

            addFaq("Do I have to pay to book a visit?",
                    "No, booking a visit through the app is free. You only pay for the space if you choose to book it after your visit.");

            addFaq("What happens after I book a visit?",
                    "Once you book a visit, the space owner will receive a notification and can view your contact information on the booking request. They will then be able to contact you to confirm the visit details.");

            addFaq("How do I cancel a booking?",
                    "You can cancel a booking from the 'My Bookings' screen by tapping the 'Cancel Visit' button next to the relevant booking.");

            addFaq("How can I contact a space owner?",
                    "The space owner's contact information is shared on the 'Space Details' screen. You can use this to contact them directly.");

            addFaq("Can I edit my profile information?",
                    "Yes, you can update your personal details, such as your name and contact information, in the 'Account Settings' or 'Edit Profile' section of the app.");
        }
    }

    private void addFaq(String question, String answer) {
        View faqItem = LayoutInflater.from(getContext())
                .inflate(R.layout.item_faq, faqContainer, false);

        TextView tvQuestion = faqItem.findViewById(R.id.tvQuestion);
        TextView tvAnswer = faqItem.findViewById(R.id.tvAnswer);

        tvQuestion.setText(question);
        tvAnswer.setText(answer);
        tvAnswer.setVisibility(View.GONE); // collapsed by default

        faqItem.setOnClickListener(v -> {
            tvAnswer.setVisibility(tvAnswer.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        });

        faqContainer.addView(faqItem);
    }

    private void showSnackbar(String message) {
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        }
    }
}
