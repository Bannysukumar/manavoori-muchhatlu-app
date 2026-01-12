package com.manavoori.muchhatlu.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.models.AdminUser;

import java.util.ArrayList;
import java.util.List;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.AdminUserViewHolder> {

    public interface Listener {
        void onPromote(@NonNull AdminUser user);

        void onDemote(@NonNull AdminUser user);

        void onDelete(@NonNull AdminUser user);

        void onEdit(@NonNull AdminUser user);

        void onToggleBlock(@NonNull AdminUser user);

        void onViewProfile(@NonNull AdminUser user);

        void onAssignRole(@NonNull AdminUser user);

        void onViewActivity(@NonNull AdminUser user);

        void onResetPassword(@NonNull AdminUser user);

        void onToggleStatus(@NonNull AdminUser user);
    }

    private final List<AdminUser> users = new ArrayList<>();
    private final Listener listener;

    public AdminUserAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public AdminUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new AdminUserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AdminUserViewHolder holder, int position) {
        holder.bind(users.get(position));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public void submitList(@NonNull List<AdminUser> newUsers) {
        users.clear();
        users.addAll(newUsers);
        notifyDataSetChanged();
    }

    class AdminUserViewHolder extends RecyclerView.ViewHolder {

        private final TextView nameText;
        private final TextView phoneText;
        private final TextView roleText;
        private final TextView locationText;
        private final TextView statusText;
        private final TextView avatarInitial;
        private final Button editButton;
        private final Button blockButton;
        private final Button deleteButton;
        private final Button viewProfileButton;
        private final Button assignRoleButton;
        private final Button viewActivityButton;
        private final Button resetPasswordButton;
        private final Button statusButton;

        AdminUserViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.textName);
            phoneText = itemView.findViewById(R.id.textPhone);
            roleText = itemView.findViewById(R.id.textRole);
            locationText = itemView.findViewById(R.id.textLocation);
            statusText = itemView.findViewById(R.id.textStatus);
            avatarInitial = itemView.findViewById(R.id.textAvatarInitial);
            editButton = itemView.findViewById(R.id.buttonEdit);
            blockButton = itemView.findViewById(R.id.buttonBlock);
            deleteButton = itemView.findViewById(R.id.buttonDelete);
            viewProfileButton = itemView.findViewById(R.id.buttonViewProfile);
            assignRoleButton = itemView.findViewById(R.id.buttonAssignRole);
            viewActivityButton = itemView.findViewById(R.id.buttonViewActivity);
            resetPasswordButton = itemView.findViewById(R.id.buttonResetPassword);
            statusButton = itemView.findViewById(R.id.buttonStatus);
        }

        void bind(AdminUser user) {
            String name = user.getName();
            nameText.setText(name);
            if (name != null && !name.isEmpty()) {
                avatarInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
            }
            phoneText.setText(itemView.getContext().getString(R.string.label_user_phone, user.getPhone()));
            roleText.setText(itemView.getContext().getString(R.string.label_user_role, user.getRole()));
            locationText.setText(user.getFormattedLocation(itemView.getContext()));
            statusText.setText(user.getStatusLabel(itemView.getContext()));

            // Update status button text based on current status
            if (statusButton != null) {
                if (user.isBanned()) {
                    statusButton.setText(itemView.getContext().getString(R.string.action_unban));
                } else if (user.isDeactivated()) {
                    statusButton.setText(itemView.getContext().getString(R.string.action_activate));
                } else if (user.isBlocked()) {
                    statusButton.setText(itemView.getContext().getString(R.string.action_unblock_user));
                } else if (!user.isActive()) {
                    statusButton.setText(itemView.getContext().getString(R.string.action_activate));
                } else {
                    statusButton.setText(itemView.getContext().getString(R.string.action_deactivate));
                }
            }

            if (blockButton != null) {
                blockButton.setText(itemView.getContext().getString(
                        user.isBlocked() ? R.string.action_unblock_user : R.string.action_block_user));
            }

            if (editButton != null) editButton.setOnClickListener(v -> listener.onEdit(user));
            if (blockButton != null) blockButton.setOnClickListener(v -> listener.onToggleBlock(user));
            if (deleteButton != null) deleteButton.setOnClickListener(v -> listener.onDelete(user));
            if (viewProfileButton != null) viewProfileButton.setOnClickListener(v -> listener.onViewProfile(user));
            if (assignRoleButton != null) assignRoleButton.setOnClickListener(v -> listener.onAssignRole(user));
            if (viewActivityButton != null) viewActivityButton.setOnClickListener(v -> listener.onViewActivity(user));
            if (resetPasswordButton != null) resetPasswordButton.setOnClickListener(v -> listener.onResetPassword(user));
            if (statusButton != null) statusButton.setOnClickListener(v -> listener.onToggleStatus(user));
        }
    }
}


