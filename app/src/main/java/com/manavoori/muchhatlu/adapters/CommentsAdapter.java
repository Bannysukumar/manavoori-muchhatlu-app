package com.manavoori.muchhatlu.adapters;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.PopupMenu;

import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.models.Comment;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    public interface Listener {
        void onEditComment(@NonNull Comment comment);

        void onDeleteComment(@NonNull Comment comment);
    }

    private final List<Comment> comments = new ArrayList<>();
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    @Nullable
    private final Listener listener;
    @Nullable
    private String currentUserId;
    @Nullable
    private String postOwnerId;

    public CommentsAdapter(@Nullable String currentUserId, @Nullable Listener listener) {
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        holder.bind(comments.get(position), currentUserId, postOwnerId, listener, dateFormat);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void submitList(@NonNull List<Comment> list) {
        comments.clear();
        comments.addAll(list);
        notifyDataSetChanged();
    }

    public void addComment(@NonNull Comment comment) {
        comments.add(0, comment);
        notifyItemInserted(0);
    }

    public void replaceComment(@NonNull Comment updated) {
        for (int i = 0; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            if (comment.getId() != null && comment.getId().equals(updated.getId())) {
                comments.set(i, updated);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void removeComment(@NonNull String commentId) {
        for (int i = 0; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            if (comment.getId() != null && comment.getId().equals(commentId)) {
                comments.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void setPostOwnerId(@Nullable String postOwnerId) {
        this.postOwnerId = postOwnerId;
        notifyDataSetChanged();
    }

    public void setCurrentUserId(@Nullable String currentUserId) {
        this.currentUserId = currentUserId;
        notifyDataSetChanged();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        private final TextView message;
        private final TextView timestamp;
        private final ImageButton optionsButton;
        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.textComment);
            timestamp = itemView.findViewById(R.id.textTimestamp);
            optionsButton = itemView.findViewById(R.id.buttonOptions);
        }

        void bind(Comment comment,
                  @Nullable String currentUserId,
                  @Nullable String postOwnerId,
                  @Nullable Listener listener,
                  DateFormat dateFormat) {
            message.setText(comment.getText());
            if (comment.getCreatedAt() != null) {
                timestamp.setText(dateFormat.format(new Date(comment.getCreatedAt().toDate().getTime())));
            } else {
                timestamp.setText("");
            }
            if (listener == null) {
                optionsButton.setVisibility(View.GONE);
                return;
            }

            boolean isAuthor = currentUserId != null && currentUserId.equals(comment.getUserId());
            boolean isOwner = postOwnerId != null && postOwnerId.equals(currentUserId);
            boolean canDelete = isAuthor || isOwner;
            boolean canEdit = isAuthor;

            if (!canDelete && !canEdit) {
                optionsButton.setVisibility(View.GONE);
                optionsButton.setOnClickListener(null);
                return;
            }

            optionsButton.setVisibility(View.VISIBLE);
            optionsButton.setOnClickListener(v -> showOptionsMenu(v, comment, canEdit, canDelete, listener));
        }

        private void showOptionsMenu(View anchor,
                                     Comment comment,
                                     boolean canEdit,
                                     boolean canDelete,
                                     @Nullable Listener listener) {
            PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor);
            if (canEdit) {
                popupMenu.getMenu().add(0, R.id.menu_edit_comment, 0, R.string.action_edit);
            }
            if (canDelete) {
                popupMenu.getMenu().add(0, R.id.menu_delete_comment, 1, R.string.action_delete);
            }
            popupMenu.setOnMenuItemClickListener(item -> handleMenuItemClick(item, comment, listener));
            popupMenu.show();
        }

        private boolean handleMenuItemClick(MenuItem item,
                                            Comment comment,
                                            @Nullable Listener listener) {
            if (listener == null) {
                return false;
            }
            int itemId = item.getItemId();
            if (itemId == R.id.menu_edit_comment) {
                listener.onEditComment(comment);
                return true;
            } else if (itemId == R.id.menu_delete_comment) {
                listener.onDeleteComment(comment);
                return true;
            }
            return false;
        }
    }
}

