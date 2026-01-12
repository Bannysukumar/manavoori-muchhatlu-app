package com.manavoori.muchhatlu.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.manavoori.muchhatlu.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface Listener {
        void onEdit(@NonNull Map<String, Object> category);
        void onToggleEnabled(@NonNull Map<String, Object> category, boolean enabled);
    }

    private final List<Map<String, Object>> categories = new ArrayList<>();
    private final Listener listener;

    public CategoryAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(categories.get(position));
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public void submitList(@NonNull List<Map<String, Object>> newCategories) {
        categories.clear();
        categories.addAll(newCategories);
        notifyDataSetChanged();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView textCategoryName;
        private final TextView textCategoryDescription;
        private final TextView textPriority;
        private final TextView textPostCount;
        private final SwitchMaterial switchEnabled;
        private final Button buttonEdit;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            textCategoryName = itemView.findViewById(R.id.textCategoryName);
            textCategoryDescription = itemView.findViewById(R.id.textCategoryDescription);
            textPriority = itemView.findViewById(R.id.textPriority);
            textPostCount = itemView.findViewById(R.id.textPostCount);
            switchEnabled = itemView.findViewById(R.id.switchEnabled);
            buttonEdit = itemView.findViewById(R.id.buttonEdit);
        }

        void bind(Map<String, Object> category) {
            textCategoryName.setText((String) category.get("name"));
            
            String description = (String) category.get("description");
            if (description != null && !description.isEmpty()) {
                textCategoryDescription.setText(description);
                textCategoryDescription.setVisibility(View.VISIBLE);
            } else {
                textCategoryDescription.setVisibility(View.GONE);
            }

            Integer priority = category.get("priority") != null ? ((Number) category.get("priority")).intValue() : 50;
            textPriority.setText("Priority: " + priority);

            Integer postCount = category.get("postCount") != null ? ((Number) category.get("postCount")).intValue() : 0;
            textPostCount.setText(postCount + " posts");

            Boolean enabled = category.get("enabled") != null ? (Boolean) category.get("enabled") : true;
            switchEnabled.setChecked(enabled != null && enabled);

            switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                listener.onToggleEnabled(category, isChecked);
            });

            buttonEdit.setOnClickListener(v -> listener.onEdit(category));
        }
    }
}

