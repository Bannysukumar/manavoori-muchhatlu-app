package com.manavoori.muchhatlu.adapters;

import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.models.Post;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    public interface Listener {
        void onPostSelected(@NonNull Post post);

        void onLikeClicked(@NonNull Post post);

        void onCommentClicked(@NonNull Post post);

        void onShareClicked(@NonNull Post post);

        void onReportClicked(@NonNull Post post);

        void onMoreClicked(@NonNull Post post);
    }

    private final List<Post> posts = new ArrayList<>();
    private final Listener listener;
    private final Map<String, String> userNameCache = new HashMap<>();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public PostAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        if (position >= 0 && position < posts.size()) {
            holder.bind(posts.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public void setItems(@NonNull List<Post> newPosts, boolean append) {
        if (!append) {
            posts.clear();
        }
        posts.addAll(newPosts);
        notifyDataSetChanged();
    }

    class PostViewHolder extends RecyclerView.ViewHolder {

        private final ImageView mediaPreview;
        private final ImageView iconPlay;
        private final FrameLayout frameMedia;
        private final TextView captionText;
        private final TextView textAvatarInitial;
        private final TextView textUserName;
        private final TextView textTimestamp;
        private final ImageButton likeButton;
        private final ImageButton commentButton;
        private final ImageButton shareButton;
        private final ImageButton buttonMore;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            mediaPreview = itemView.findViewById(R.id.imageMedia);
            iconPlay = itemView.findViewById(R.id.iconPlay);
            frameMedia = itemView.findViewById(R.id.frameMedia);
            captionText = itemView.findViewById(R.id.textCaption);
            textAvatarInitial = itemView.findViewById(R.id.textAvatarInitial);
            textUserName = itemView.findViewById(R.id.textUserName);
            textTimestamp = itemView.findViewById(R.id.textTimestamp);
            likeButton = itemView.findViewById(R.id.buttonLike);
            commentButton = itemView.findViewById(R.id.buttonComment);
            shareButton = itemView.findViewById(R.id.buttonShare);
            buttonMore = itemView.findViewById(R.id.buttonMore);
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

            // Set like button state
            likeButton.setImageResource(post.isLikedByCurrentUser()
                    ? R.drawable.ic_post_like_filled
                    : R.drawable.ic_post_like_outline);

            // Handle media preview
            String mediaType = post.getMediaType();
            if ("image".equalsIgnoreCase(mediaType) || "video".equalsIgnoreCase(mediaType)) {
                frameMedia.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(post.getMediaUrl())
                        .fitCenter()
                        .placeholder(R.drawable.ic_media_placeholder)
                        .into(mediaPreview);
                
                // Show play icon for videos
                if ("video".equalsIgnoreCase(mediaType)) {
                    iconPlay.setVisibility(View.VISIBLE);
                } else {
                    iconPlay.setVisibility(View.GONE);
                }
            } else {
                frameMedia.setVisibility(View.GONE);
            }

            // Set click listeners
            itemView.setOnClickListener(v -> listener.onPostSelected(post));
            likeButton.setOnClickListener(v -> listener.onLikeClicked(post));
            commentButton.setOnClickListener(v -> listener.onCommentClicked(post));
            shareButton.setOnClickListener(v -> listener.onShareClicked(post));
            buttonMore.setOnClickListener(v -> listener.onMoreClicked(post));
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

