package com.manavoori.muchhatlu.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.models.NotificationItem;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    public interface Listener {
        void onNotificationClicked(@NonNull NotificationItem item);
    }

    private final List<NotificationItem> notifications = new ArrayList<>();
    private final Listener listener;
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    public NotificationAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        holder.bind(notifications.get(position));
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public void submitList(@NonNull List<NotificationItem> items) {
        notifications.clear();
        notifications.addAll(items);
        notifyDataSetChanged();
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView message;
        private final TextView time;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textTitle);
            message = itemView.findViewById(R.id.textMessage);
            time = itemView.findViewById(R.id.textTime);
        }

        void bind(NotificationItem item) {
            title.setText(item.getTitle());
            message.setText(item.getMessage());
            if (item.getCreatedAt() != null) {
                time.setText(dateFormat.format(new Date(item.getCreatedAt().toDate().getTime())));
            } else {
                time.setText("");
            }
            itemView.setOnClickListener(v -> listener.onNotificationClicked(item));
        }
    }
}

