package com.manavoori.muchhatlu.adapters;

import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.models.Post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminPostAdapter extends RecyclerView.Adapter<AdminPostAdapter.AdminPostViewHolder> {

    public interface Listener {
        void onViewPost(@NonNull Post post);

        void onEditPost(@NonNull Post post);

        void onApprovePost(@NonNull Post post);

        void onRejectPost(@NonNull Post post);
    }

    public enum Mode {
        PENDING,
        FLAGGED,
        ALL
    }

    private final List<Post> posts = new ArrayList<>();
    private final Listener listener;
    private Mode mode = Mode.PENDING;
    private final Map<String, String> userNameCache = new HashMap<>();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public AdminPostAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public AdminPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_post, parent, false);
        return new AdminPostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AdminPostViewHolder holder, int position) {
        holder.bind(posts.get(position));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public void submitList(@NonNull List<Post> newPosts) {
        posts.clear();
        posts.addAll(newPosts);
        notifyDataSetChanged();
    }

    public void setMode(@NonNull Mode mode) {
        this.mode = mode;
        notifyDataSetChanged();
    }

    class AdminPostViewHolder extends RecyclerView.ViewHolder {
        private final TextView captionText;
        private final TextView textAvatarInitial;
        private final TextView textUserName;
        private final TextView textTimestamp;
        private final Button approveButton;
        private final Button rejectButton;

        AdminPostViewHolder(@NonNull View itemView) {
            super(itemView);
            captionText = itemView.findViewById(R.id.textCaption);
            textAvatarInitial = itemView.findViewById(R.id.textAvatarInitial);
            textUserName = itemView.findViewById(R.id.textUserName);
            textTimestamp = itemView.findViewById(R.id.textTimestamp);
            approveButton = itemView.findViewById(R.id.buttonApprove);
            rejectButton = itemView.findViewById(R.id.buttonReject);
        }

        void bind(Post post) {
            // Set caption
            if (TextUtils.isEmpty(post.getCaption())) {
                captionText.setVisibility(View.GONE);
            } else {
                captionText.setVisibility(View.VISIBLE);
                captionText.setText(post.getCaption());
            }

            // Set timestamp
            if (post.getCreatedAt() > 0) {
                CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                        post.getCreatedAt(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE);
                textTimestamp.setText(timeAgo);
            } else {
                textTimestamp.setText("");
            }

            // Load user name and avatar
            loadUserName(post.getUserId());

            // Configure buttons based on mode
            if (mode == Mode.ALL) {
                approveButton.setVisibility(View.GONE);
                rejectButton.setText(R.string.action_delete);
            } else {
                approveButton.setVisibility(View.VISIBLE);
                approveButton.setText(R.string.action_approve);
                rejectButton.setText(R.string.action_reject);
            }

            approveButton.setOnClickListener(v -> listener.onApprovePost(post));
            rejectButton.setOnClickListener(v -> listener.onRejectPost(post));

            // Make entire card clickable to view post
            itemView.setOnClickListener(v -> listener.onViewPost(post));
        }

        private void loadUserName(String userId) {
            if (TextUtils.isEmpty(userId)) {
                textUserName.setText(itemView.getContext().getString(R.string.guest_user_name));
                textAvatarInitial.setText("?");
                return;
            }

            // Check cache first
            String cachedName = userNameCache.get(userId);
            if (cachedName != null) {
                setUserNameAndAvatar(cachedName);
                return;
            }

            // Fetch from Firestore
            firestore.collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            if (!TextUtils.isEmpty(name)) {
                                userNameCache.put(userId, name);
                                setUserNameAndAvatar(name);
                            } else {
                                setUserNameAndAvatar(itemView.getContext().getString(R.string.guest_user_name));
                            }
                        } else {
                            setUserNameAndAvatar(itemView.getContext().getString(R.string.guest_user_name));
                        }
                    })
                    .addOnFailureListener(e -> {
                        setUserNameAndAvatar(itemView.getContext().getString(R.string.guest_user_name));
                    });
        }

        private void setUserNameAndAvatar(String name) {
            textUserName.setText(name);
            if (!TextUtils.isEmpty(name)) {
                String initial = name.substring(0, 1).toUpperCase();
                textAvatarInitial.setText(initial);
            } else {
                textAvatarInitial.setText("?");
            }
        }
    }
}

