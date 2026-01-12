package com.manavoori.muchhatlu.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.manavoori.muchhatlu.R;

public class ProfileActivity extends AppCompatActivity {

    private ImageView profileImage;
    private TextView nameText;
    private TextView locationText;
    private TextView statsText;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        ImageButton buttonMenu = findViewById(R.id.buttonMenu);
        buttonMenu.setOnClickListener(v -> drawerLayout.openDrawer(navigationView));

        profileImage = findViewById(R.id.imageProfile);
        nameText = findViewById(R.id.textName);
        locationText = findViewById(R.id.textLocation);
        statsText = findViewById(R.id.textStats);

        Button editProfileButton = findViewById(R.id.buttonEditProfile);
        Button myPostsButton = findViewById(R.id.buttonMyPosts);

        editProfileButton.setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));
        myPostsButton.setOnClickListener(v -> startActivity(new Intent(this, MyPostsActivity.class)));

        // Setup navigation menu
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_referral) {
                startActivity(new Intent(this, ReferralActivity.class));
            } else if (id == R.id.menu_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.menu_admin) {
                startActivity(new Intent(this, AdminActivity.class));
            } else if (id == R.id.menu_logout) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
            drawerLayout.closeDrawer(navigationView);
            return true;
        });

        // Update navigation header
        updateNavigationHeader();

        loadProfile();
        loadStats();
    }

    private void updateNavigationHeader() {
        View headerView = navigationView.getHeaderView(0);
        if (headerView != null && auth.getCurrentUser() != null) {
            ImageView headerImage = headerView.findViewById(R.id.imageHeaderProfile);
            TextView headerName = headerView.findViewById(R.id.textHeaderName);
            TextView headerEmail = headerView.findViewById(R.id.textHeaderEmail);

            if (headerName != null) {
                firestore.collection("users")
                        .document(auth.getCurrentUser().getUid())
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String name = documentSnapshot.getString("name");
                                if (name != null && headerName != null) {
                                    headerName.setText(name);
                                }
                                String photoUrl = documentSnapshot.getString("photoUrl");
                                if (photoUrl != null && headerImage != null) {
                                    Glide.with(this)
                                            .load(photoUrl)
                                            .placeholder(R.drawable.ic_person_placeholder)
                                            .into(headerImage);
                                }
                            }
                        });
            }

            if (headerEmail != null && auth.getCurrentUser().getEmail() != null) {
                headerEmail.setText(auth.getCurrentUser().getEmail());
            }
        }
    }

    private void loadProfile() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.error_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        firestore.collection("users")
                .document(auth.getCurrentUser().getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null || !value.exists()) {
                        return;
                    }
                    nameText.setText(value.getString("name"));
                    String location = String.format("%s, %s, %s, %s",
                            safe(value.getString("village")),
                            safe(value.getString("mandal")),
                            safe(value.getString("district")),
                            safe(value.getString("state")));
                    locationText.setText(location);
                    String photo = value.getString("photoUrl");
                    if (photo != null) {
                        Glide.with(ProfileActivity.this)
                                .load(photo)
                                .placeholder(R.drawable.ic_person_placeholder)
                                .into(profileImage);
                    } else {
                        profileImage.setImageResource(R.drawable.ic_person_placeholder);
                    }
                    // Show/hide admin menu item based on role
                    String role = value.getString("role");
                    MenuItem adminMenuItem = navigationView.getMenu().findItem(R.id.menu_admin);
                    if (adminMenuItem != null) {
                        if (role != null && ("Admin".equalsIgnoreCase(role) || "Super Admin".equalsIgnoreCase(role))) {
                            adminMenuItem.setVisible(true);
                        } else {
                            adminMenuItem.setVisible(false);
                        }
                    }
                });
    }

    private void loadStats() {
        if (auth.getCurrentUser() == null) {
            return;
        }
        firestore.collection("posts")
                .whereEqualTo("userId", auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    statsText.setText(getString(R.string.profile_stats_format, count));
                });
    }

    private String safe(String value) {
        return value != null ? value : "-";
    }
}

