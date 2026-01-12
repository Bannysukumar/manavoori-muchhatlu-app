package com.manavoori.muchhatlu.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.manavoori.muchhatlu.models.Post;
import com.manavoori.muchhatlu.utils.RegionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PostRepository {

    private static final int PAGE_SIZE = 10;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private DocumentSnapshot lastVisible;

    public void resetPagination() {
        lastVisible = null;
    }

    public Task<QuerySnapshot> fetchNextPage(@NonNull Map<String, String> filters) {
        Query query = firestore.collection("posts")
                .whereEqualTo("approved", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        for (Map.Entry<String, String> filter : filters.entrySet()) {
            if (shouldApplyFilter(filter.getValue())) {
                query = query.whereEqualTo(filter.getKey(), filter.getValue());
            }
        }

        if (lastVisible != null) {
            query = query.startAfter(lastVisible);
        }

        return query.get().addOnSuccessListener(querySnapshot -> {
            List<DocumentSnapshot> documents = querySnapshot.getDocuments();
            if (!documents.isEmpty()) {
                lastVisible = documents.get(documents.size() - 1);
            }
        });
    }

    public List<Post> parsePosts(@Nullable QuerySnapshot snapshot) {
        List<Post> posts = new ArrayList<>();
        if (snapshot == null) {
            return posts;
        }
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            posts.add(Post.fromSnapshot(document));
        }
        return posts;
    }

    private boolean shouldApplyFilter(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if ("Select Option".equalsIgnoreCase(trimmed)) {
            return false;
        }
        return !RegionData.getAllLabel().equalsIgnoreCase(trimmed);
    }
}

