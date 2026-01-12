package com.manavoori.muchhatlu.utils;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.manavoori.muchhatlu.models.Post;

import java.util.HashMap;
import java.util.Map;

public final class PostInteractions {

    private PostInteractions() {
    }

    private static FirebaseFirestore firestore() {
        return FirebaseFirestore.getInstance();
    }

    private static FirebaseAuth auth() {
        return FirebaseAuth.getInstance();
    }

    public static Task<LikeResult> toggleLike(@NonNull Post post) {
        if (auth().getCurrentUser() == null) {
            return Tasks.forException(new IllegalStateException("User must be signed in to like posts."));
        }
        String uid = auth().getCurrentUser().getUid();
        DocumentReference postRef = firestore().collection("posts").document(post.getId());
        DocumentReference likeRef = postRef.collection("likes").document(uid);

        return firestore().runTransaction(transaction -> {
            DocumentSnapshot likeSnapshot = transaction.get(likeRef);
            boolean isCurrentlyLiked = likeSnapshot.exists();
            long delta = isCurrentlyLiked ? -1L : 1L;

            if (isCurrentlyLiked) {
                transaction.delete(likeRef);
            } else {
                Map<String, Object> likeData = new HashMap<>();
                likeData.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(likeRef, likeData);
            }
            transaction.update(postRef, "likeCount", FieldValue.increment(delta));
            return new LikeResult(!isCurrentlyLiked, delta);
        });
    }

    public static Task<Void> incrementViewCount(@NonNull String postId) {
        DocumentReference postRef = firestore().collection("posts").document(postId);
        return postRef.update("viewCount", FieldValue.increment(1));
    }

    public static Task<Void> incrementShareCount(@NonNull String postId) {
        DocumentReference postRef = firestore().collection("posts").document(postId);
        return postRef.update("shareCount", FieldValue.increment(1));
    }

    public static Task<Void> adjustCommentCount(@NonNull String postId, long delta) {
        DocumentReference postRef = firestore().collection("posts").document(postId);
        return postRef.update("commentCount", FieldValue.increment(delta));
    }

    public static Task<Boolean> isPostLikedByCurrentUser(@NonNull String postId) {
        if (auth().getCurrentUser() == null) {
            return Tasks.forResult(Boolean.FALSE);
        }
        String uid = auth().getCurrentUser().getUid();
        DocumentReference likeRef = firestore()
                .collection("posts")
                .document(postId)
                .collection("likes")
                .document(uid);
        return likeRef.get().continueWith(task -> task.isSuccessful() && task.getResult() != null && task.getResult().exists());
    }

    public static final class LikeResult {
        private final boolean liked;
        private final long delta;

        public LikeResult(boolean liked, long delta) {
            this.liked = liked;
            this.delta = delta;
        }

        public boolean isLiked() {
            return liked;
        }

        public long getDelta() {
            return delta;
        }
    }
}


