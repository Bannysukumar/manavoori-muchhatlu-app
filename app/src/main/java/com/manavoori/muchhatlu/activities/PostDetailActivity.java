package com.manavoori.muchhatlu.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.adapters.CommentsAdapter;
import com.manavoori.muchhatlu.models.Comment;
import com.manavoori.muchhatlu.models.Post;
import com.manavoori.muchhatlu.utils.PostInteractions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostDetailActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "extra_post_id";
    public static final String EXTRA_SCROLL_TO_COMMENTS = "extra_scroll_to_comments";

    private ImageView imageMedia;
    private VideoView videoMedia;
    private TextView audioPlaceholder;
    private TextView textContent;
    private TextView captionText;
    private TextView statsText;
    private EditText commentInput;
    private View rootView;
    private ImageButton likeButton;
    private ImageButton shareButton;

    private CommentsAdapter commentsAdapter;
    private Post currentPost;
    private String postId;
    private boolean viewCountRecorded = false;

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        ImageButton buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        imageMedia = findViewById(R.id.imageMedia);
        videoMedia = findViewById(R.id.videoMedia);
        audioPlaceholder = findViewById(R.id.audioPlaceholder);
        textContent = findViewById(R.id.textContent);
        captionText = findViewById(R.id.textCaption);
        statsText = findViewById(R.id.textStats);
        commentInput = findViewById(R.id.editComment);
        rootView = findViewById(R.id.scrollView);
        likeButton = findViewById(R.id.buttonLike);
        ImageButton commentButton = findViewById(R.id.buttonComment);
        shareButton = findViewById(R.id.buttonShare);
        ImageButton reportButton = findViewById(R.id.buttonReport);
        Button sendCommentButton = findViewById(R.id.buttonSendComment);

        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recyclerComments);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        commentsAdapter = new CommentsAdapter(getCurrentUserId(), new CommentsAdapter.Listener() {
            @Override
            public void onEditComment(@NonNull Comment comment) {
                showEditCommentDialog(comment);
            }

            @Override
            public void onDeleteComment(@NonNull Comment comment) {
                confirmDeleteComment(comment);
            }
        });
        recyclerView.setAdapter(commentsAdapter);

        likeButton.setOnClickListener(v -> toggleLike());
        commentButton.setOnClickListener(v -> commentInput.requestFocus());
        shareButton.setOnClickListener(v -> sharePost());
        reportButton.setOnClickListener(v -> reportPost());
        sendCommentButton.setOnClickListener(v -> submitComment());


        postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (TextUtils.isEmpty(postId)) {
            finish();
            return;
        }

        loadPost();
        loadComments();
    }

    private void loadPost() {
        firestore.collection("posts")
                .document(postId)
                .get()
                .addOnSuccessListener(this::bindPost)
                .addOnFailureListener(e -> Snackbar.make(rootView, e.getLocalizedMessage(), Snackbar.LENGTH_LONG).show());
    }

    private void bindPost(DocumentSnapshot snapshot) {
        currentPost = Post.fromSnapshot(snapshot);
        captionText.setText(currentPost.getCaption());
        commentsAdapter.setPostOwnerId(currentPost.getUserId());
        bindMedia();
        refreshStats();
        fetchLikeState();
        recordViewCount();
    }

    private void bindMedia() {
        imageMedia.setVisibility(View.GONE);
        videoMedia.setVisibility(View.GONE);
        audioPlaceholder.setVisibility(View.GONE);
        textContent.setVisibility(View.GONE);

        if (currentPost == null) {
            return;
        }

        String type = currentPost.getMediaType();
        if ("image".equalsIgnoreCase(type)) {
            imageMedia.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(currentPost.getMediaUrl())
                    .placeholder(R.drawable.ic_media_placeholder)
                    .into(imageMedia);
        } else if ("video".equalsIgnoreCase(type)) {
            videoMedia.setVisibility(View.VISIBLE);
            String videoUrl = currentPost.getMediaUrl();
            if (!TextUtils.isEmpty(videoUrl)) {
                try {
                    Uri videoUri = Uri.parse(videoUrl);
                    videoMedia.setVideoURI(videoUri);
                    videoMedia.setOnPreparedListener(MediaPlayer::start);
                    videoMedia.setOnErrorListener((mp, what, extra) -> {
                        Toast.makeText(this, R.string.error_video_playback, Toast.LENGTH_SHORT).show();
                        return true;
                    });
                } catch (Exception e) {
                    Toast.makeText(this, R.string.error_video_playback, Toast.LENGTH_SHORT).show();
                }
            }
        } else if ("audio".equalsIgnoreCase(type)) {
            audioPlaceholder.setVisibility(View.VISIBLE);
            audioPlaceholder.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentPost.getMediaUrl()));
                startActivity(intent);
            });
        } else if ("text".equalsIgnoreCase(type)) {
            textContent.setVisibility(View.VISIBLE);
            textContent.setText(currentPost.getCaption());
        }
    }

    private void loadComments() {
        commentsAdapter.setCurrentUserId(getCurrentUserId());
        firestore.collection("posts")
                .document(postId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Comment> comments = new ArrayList<>();
                    queryDocumentSnapshots.forEach(documentSnapshot -> {
                        Comment comment = documentSnapshot.toObject(Comment.class);
                        comment.setId(documentSnapshot.getId());
                        comments.add(comment);
                    });
                    commentsAdapter.submitList(comments);
                    if (getIntent().getBooleanExtra(EXTRA_SCROLL_TO_COMMENTS, false)) {
                        rootView.postDelayed(() -> rootView.scrollTo(0, rootView.getBottom()), 200);
                    }
                });
    }

    private void submitComment() {
        String text = commentInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            Toast.makeText(this, R.string.error_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        Timestamp now = Timestamp.now();
        Map<String, Object> comment = new HashMap<>();
        comment.put("userId", currentUserId);
        comment.put("text", text);
        comment.put("createdAt", now);
        firestore.collection("posts")
                .document(postId)
                .collection("comments")
                .add(comment)
                .addOnSuccessListener(documentReference -> {
                    commentInput.setText("");
                    Comment model = new Comment();
                    model.setId(documentReference.getId());
                    model.setUserId(currentUserId);
                    model.setText(text);
                    model.setCreatedAt(now);
                    commentsAdapter.addComment(model);
                    PostInteractions.adjustCommentCount(postId, 1);
                    if (currentPost != null) {
                        currentPost.setCommentCount(currentPost.getCommentCount() + 1);
                        refreshStats();
                    }
                });
    }

    private void toggleLike() {
        if (currentPost == null) {
            return;
        }
        if (getCurrentUserId() == null) {
            Toast.makeText(this, R.string.error_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        PostInteractions.toggleLike(currentPost)
                .addOnSuccessListener(result -> {
                    long updatedCount = Math.max(0, currentPost.getLikeCount() + result.getDelta());
                    currentPost.setLikeCount(updatedCount);
                    currentPost.setLikedByCurrentUser(result.isLiked());
                    refreshStats();
                })
                .addOnFailureListener(e -> Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
    }

    private void sharePost() {
        if (currentPost == null) {
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, currentPost.getCaption() + "\n" + currentPost.getMediaUrl());
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)));
        PostInteractions.incrementShareCount(postId)
                .addOnSuccessListener(unused -> {
                    if (currentPost != null) {
                        currentPost.setShareCount(currentPost.getShareCount() + 1);
                        refreshStats();
                    }
                });
    }

    private void reportPost() {
        if (getCurrentUserId() == null) {
            Toast.makeText(this, R.string.error_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        firestore.collection("posts")
                .document(postId)
                .update("reported", true)
                .addOnSuccessListener(unused -> Snackbar.make(rootView, R.string.message_report_submitted, Snackbar.LENGTH_LONG).show());
    }

    private void refreshStats() {
        if (currentPost == null) {
            return;
        }
        statsText.setText(getString(R.string.post_stats_format,
                currentPost.getViewCount(),
                currentPost.getLikeCount(),
                currentPost.getCommentCount(),
                currentPost.getShareCount()));
        // Update like button icon based on liked state
        if (currentPost.isLikedByCurrentUser()) {
            likeButton.setImageResource(R.drawable.ic_post_like_filled);
            likeButton.setColorFilter(getResources().getColor(R.color.md_theme_light_primary, getTheme()));
            likeButton.setContentDescription(getString(R.string.action_unlike));
        } else {
            likeButton.setImageResource(R.drawable.ic_post_like_outline);
            likeButton.setColorFilter(getResources().getColor(R.color.home_text_secondary, getTheme()));
            likeButton.setContentDescription(getString(R.string.action_like));
        }
    }

    private void fetchLikeState() {
        if (currentPost == null || getCurrentUserId() == null) {
            refreshStats();
            return;
        }
        PostInteractions.isPostLikedByCurrentUser(postId)
                .addOnSuccessListener(isLiked -> {
                    if (currentPost != null) {
                        currentPost.setLikedByCurrentUser(isLiked);
                        refreshStats();
                    }
                });
    }

    private void recordViewCount() {
        if (viewCountRecorded || currentPost == null) {
            return;
        }
        viewCountRecorded = true;
        PostInteractions.incrementViewCount(postId)
                .addOnSuccessListener(unused -> {
                    if (currentPost != null) {
                        currentPost.setViewCount(currentPost.getViewCount() + 1);
                        refreshStats();
                    }
                });
    }

    private void showEditCommentDialog(@NonNull Comment comment) {
        if (!canEditComment(comment)) {
            return;
        }
        final EditText input = new EditText(this);
        input.setText(comment.getText());
        input.setSelection(input.getText().length());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_edit_comment)
                .setView(container)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String updated = input.getText().toString().trim();
                    if (TextUtils.isEmpty(updated)) {
                        Toast.makeText(this, R.string.error_empty_field, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (comment.getId() == null) {
                        return;
                    }
                    firestore.collection("posts")
                            .document(postId)
                            .collection("comments")
                            .document(comment.getId())
                            .update("text", updated)
                            .addOnSuccessListener(unused -> {
                                comment.setText(updated);
                                commentsAdapter.replaceComment(comment);
                                Snackbar.make(rootView, R.string.message_comment_updated, Snackbar.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteComment(@NonNull Comment comment) {
        if (!canDeleteComment(comment)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_delete_comment)
                .setMessage(R.string.dialog_message_delete_comment)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteComment(comment))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteComment(@NonNull Comment comment) {
        if (comment.getId() == null) {
            return;
        }
        firestore.collection("posts")
                .document(postId)
                .collection("comments")
                .document(comment.getId())
                .delete()
                .addOnSuccessListener(unused -> {
                    commentsAdapter.removeComment(comment.getId());
                    PostInteractions.adjustCommentCount(postId, -1);
                    if (currentPost != null) {
                        currentPost.setCommentCount(Math.max(0, currentPost.getCommentCount() - 1));
                        refreshStats();
                    }
                    Snackbar.make(rootView, R.string.message_comment_deleted, Snackbar.LENGTH_SHORT).show();
                });
    }

    private boolean canEditComment(@NonNull Comment comment) {
        String currentUserId = getCurrentUserId();
        return currentUserId != null && currentUserId.equals(comment.getUserId());
    }

    private boolean canDeleteComment(@NonNull Comment comment) {
        if (canEditComment(comment)) {
            return true;
        }
        String currentUserId = getCurrentUserId();
        return currentUserId != null && currentPost != null && currentUserId.equals(currentPost.getUserId());
    }

    @Nullable
    private String getCurrentUserId() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
}


