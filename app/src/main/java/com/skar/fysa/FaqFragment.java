package com.skar.fysa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class FaqFragment extends Fragment {

    private LinearLayout faqContainer;
    private String accountType = "user"; // default
    private static final String DB_URL = "https://fysa-793f9-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faq, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        faqContainer = view.findViewById(R.id.layoutFaqContainer);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), "User not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch accountType from Firebase
        DatabaseReference userRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(currentUser.getUid());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String type = snapshot.child("accountType").getValue(String.class);
                accountType = type != null ? type : "user";
                loadFaqsForAccountType(accountType);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load account type.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadFaqsForAccountType(String accountType) {
        faqContainer.removeAllViews();

        if ("owner".equalsIgnoreCase(accountType)) {
            // Owner-specific FAQs
            addFaq("How do I list my property?",
                    "Go to 'Add Property', fill details, and submit for approval.");

            addFaq("Can I remove a space?",
                    "Yes, you can remove or mark a space as unavailable anytime.");

            addFaq("How do I receive payments?",
                    "Payments are credited to your linked bank account after booking confirmation.");

            addFaq("How do I manage bookings?",
                    "Use 'My Spaces' to view, accept, or reject bookings.");
        } else {
            // User-specific FAQs
            addFaq("How do I book a space?",
                    "Open 'Find Nearby Spaces', select a property, and tap 'Book Now'.");

            addFaq("Can I cancel a booking?",
                    "Yes, you can cancel from 'My Bookings' before the start date.");

            addFaq("What payment methods are supported?",
                    "We currently support UPI, Debit/Credit cards, and Net Banking.");

            addFaq("Is my data safe?",
                    "Yes, we use industry-standard encryption to protect your information.");
        }
    }

    private void addFaq(String question, String answer) {
        View faqItem = LayoutInflater.from(getContext())
                .inflate(R.layout.item_faq, faqContainer, false);

        TextView tvQuestion = faqItem.findViewById(R.id.tvQuestion);
        TextView tvAnswer   = faqItem.findViewById(R.id.tvAnswer);

        tvQuestion.setText(question);
        tvAnswer.setText(answer);
        tvAnswer.setVisibility(View.GONE); // collapsed by default

        faqItem.setOnClickListener(v -> {
            tvAnswer.setVisibility(tvAnswer.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        });

        faqContainer.addView(faqItem);
    }
}
