package com.manavoori.muchhatlu.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.adapters.NotificationAdapter;
import com.manavoori.muchhatlu.models.NotificationItem;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity implements NotificationAdapter.Listener {

    private NotificationAdapter adapter;
    private View emptyView;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        ImageButton buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        emptyView = findViewById(R.id.textEmpty);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(this::loadNotifications);

        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recyclerNotifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this);
        recyclerView.setAdapter(adapter);

        loadNotifications();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadNotifications() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        swipeRefreshLayout.setRefreshing(true);
        FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .collection("items")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    swipeRefreshLayout.setRefreshing(false);
                    List<NotificationItem> items = new ArrayList<>();
                    queryDocumentSnapshots.forEach(documentSnapshot -> {
                        NotificationItem item = documentSnapshot.toObject(NotificationItem.class);
                        item.setId(documentSnapshot.getId());
                        items.add(item);
                    });
                    adapter.submitList(items);
                    emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> swipeRefreshLayout.setRefreshing(false));
    }

    @Override
    public void onNotificationClicked(@NonNull NotificationItem item) {
        if (item.getPostId() != null) {
            Intent intent = new Intent(this, PostDetailActivity.class);
            intent.putExtra(PostDetailActivity.EXTRA_POST_ID, item.getPostId());
            startActivity(intent);
        }
    }
}

