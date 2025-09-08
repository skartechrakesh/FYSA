package com.skar.fysa;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for displaying Space objects (Hostel, PG, Room, House, Flat).
 */
public class SpacesAdapter extends RecyclerView.Adapter<SpacesAdapter.SpaceVH> {

    /**
     * Click listener interface to handle space item clicks.
     */
    public interface OnSpaceClick {
        void onClick(Space space);
    }

    private final List<Space> items = new ArrayList<>();
    private final OnSpaceClick onSpaceClick;

    public SpacesAdapter(List<Space> initial, OnSpaceClick onSpaceClick) {
        if (initial != null) {
            items.addAll(initial);
        }
        this.onSpaceClick = onSpaceClick;
    }

    /**
     * Updates the list of items and refreshes the adapter.
     */
    public void submitList(List<Space> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SpaceVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_find_space, parent, false);
        return new SpaceVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SpaceVH holder, int position) {
        Space s = items.get(position);
        if (s == null) return;

        // Name
        holder.tvName.setText(s.getName() != null ? s.getName() : "Unnamed");

        // Type
        holder.tvType.setText(s.getType() != null ? s.getType() : "Unknown");

        // Owner
        holder.tvOwner.setText(s.getOwnerName() != null ? "Owner: " + s.getOwnerName() : "Owner: N/A");

        // Address
        holder.tvAddress.setText(s.getAddress() != null ? "Address: " + s.getAddress() : "Address: N/A");

        // Contact
        holder.tvContact.setText(s.getContact() != null ? "Contact: " + s.getContact() : "Contact: N/A");

        // Vacancy
        holder.tvVacancy.setText(Boolean.TRUE.equals(s.isVacancy()) ? "Vacancy: Available" : "Vacancy: Not Available");

        // Distance (m or km)
        Integer distance = s.getDistanceMeters();
        if (distance != null && distance > 0) {
            if (distance >= 1000) {
                double km = distance / 1000.0;
                holder.tvDistance.setText(String.format("%.1f km away", km));
            } else {
                holder.tvDistance.setText(distance + " m away");
            }
        } else {
            holder.tvDistance.setText("Distance unknown");
        }

        // Click listener
        holder.card.setOnClickListener(v -> {
            if (onSpaceClick != null) onSpaceClick.onClick(s);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder for a Space card item.
     */
    static class SpaceVH extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvName, tvType, tvOwner, tvAddress, tvContact, tvDistance, tvVacancy;

        SpaceVH(@NonNull View itemView) {
            super(itemView);
            card       = (CardView) itemView;
            tvName     = itemView.findViewById(R.id.tvName);
            tvType     = itemView.findViewById(R.id.tvType);
            tvOwner    = itemView.findViewById(R.id.tvOwner);
            tvAddress  = itemView.findViewById(R.id.tvAddress);
            tvContact  = itemView.findViewById(R.id.tvContact);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvVacancy  = itemView.findViewById(R.id.tvVacancy);
        }
    }
}
