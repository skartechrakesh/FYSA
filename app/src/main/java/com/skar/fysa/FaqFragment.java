package com.skar.fysa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FaqFragment extends Fragment {

    private LinearLayout faqContainer;
    private String source = "";

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

        if (getArguments() != null) {
            source = getArguments().getString("source", "");
        }

        loadFaqs();
    }

    private void loadFaqs() {
        addFaq("How do I book a space?",
                "Open 'Find Nearby Spaces', select a property, and tap 'Book Now'.");

        addFaq("Can I cancel a booking?",
                "Yes, you can cancel from 'My Bookings' before the start date.");

        addFaq("What payment methods are supported?",
                "We currently support UPI, Debit/Credit cards, and Net Banking.");

        addFaq("Is my data safe?",
                "Yes, we use industry-standard encryption to protect your information.");
    }

    private void addFaq(String question, String answer) {
        View faqItem = LayoutInflater.from(getContext())
                .inflate(R.layout.item_faq, faqContainer, false);

        TextView tvQuestion = faqItem.findViewById(R.id.tvQuestion);
        TextView tvAnswer   = faqItem.findViewById(R.id.tvAnswer);

        tvQuestion.setText(question);
        tvAnswer.setText(answer);

        faqItem.setOnClickListener(v -> {
            if (tvAnswer.getVisibility() == View.GONE) {
                tvAnswer.setVisibility(View.VISIBLE);
            } else {
                tvAnswer.setVisibility(View.GONE);
            }
        });

        faqContainer.addView(faqItem);
    }
}
