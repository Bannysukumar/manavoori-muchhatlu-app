package com.manavoori.muchhatlu.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.adapters.PostAdapter;
import com.manavoori.muchhatlu.models.Post;
import com.manavoori.muchhatlu.utils.PostInteractions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MyPostsActivity extends AppCompatActivity implements PostAdapter.Listener {

    private PostAdapter adapter;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView emptyView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_posts);

        ImageButton buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(this::loadPosts);

        recyclerView = findViewById(R.id.recyclerPosts);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PostAdapter(this);
        recyclerView.setAdapter(adapter);

        emptyView = findViewById(R.id.textEmpty);

        loadPosts();
    }

    private void loadPosts() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        swipeRefreshLayout.setRefreshing(true);
        FirebaseFirestore.getInstance()
                .collection("posts")
                .whereEqualTo("userId", FirebaseAuth.getInstance().getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Post> posts = new ArrayList<>();
                    queryDocumentSnapshots.forEach(documentSnapshot -> posts.add(Post.fromSnapshot(documentSnapshot)));
                    posts.sort(Comparator.comparingLong(Post::getCreatedAt).reversed());
                    decoratePosts(posts);
                    adapter.setItems(posts, false);
                    toggleEmptyState(posts.isEmpty());
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    android.widget.Toast.makeText(this, e.getLocalizedMessage(), android.widget.Toast.LENGTH_LONG).show();
                    toggleEmptyState(adapter.getItemCount() == 0);
                });
    }

    private void toggleEmptyState(boolean showEmpty) {
        if (emptyView == null || recyclerView == null) {
            return;
        }
        emptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onPostSelected(@NonNull Post post) {
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
        startActivity(intent);
    }

    @Override
    public void onLikeClicked(@NonNull Post post) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        PostInteractions.toggleLike(post)
                .addOnSuccessListener(result -> {
                    long updatedCount = Math.max(0, post.getLikeCount() + result.getDelta());
                    post.setLikeCount(updatedCount);
                    post.setLikedByCurrentUser(result.isLiked());
                    adapter.notifyDataSetChanged();
                });
    }

    @Override
    public void onCommentClicked(@NonNull Post post) {
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
        intent.putExtra(PostDetailActivity.EXTRA_SCROLL_TO_COMMENTS, true);
        startActivity(intent);
    }

    @Override
    public void onShareClicked(@NonNull Post post) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, post.getCaption() + "\n" + post.getMediaUrl());
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)));
        PostInteractions.incrementShareCount(post.getId())
                .addOnSuccessListener(unused -> {
                    post.setShareCount(post.getShareCount() + 1);
                    adapter.notifyDataSetChanged();
                });
    }

    @Override
    public void onMoreClicked(@NonNull Post post) {
        // Show a popup menu with options
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(this, recyclerView);
        popupMenu.getMenu().add(0, 0, 0, getString(R.string.action_report));
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                onReportClicked(post);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    @Override
    public void onReportClicked(@NonNull Post post) {
        FirebaseFirestore.getInstance()
                .collection("posts")
                .document(post.getId())
                .update("reported", true);
    }

    private void decoratePosts(@NonNull List<Post> posts) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null || posts.isEmpty()) {
            return;
        }
        List<Task<Boolean>> tasks = new ArrayList<>();
        for (Post post : posts) {
            Task<Boolean> task = PostInteractions.isPostLikedByCurrentUser(post.getId())
                    .addOnSuccessListener(isLiked -> post.setLikedByCurrentUser(isLiked));
            tasks.add(task);
        }
        Tasks.whenAllComplete(tasks).addOnSuccessListener(results -> adapter.notifyDataSetChanged());
    }
}

