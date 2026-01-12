package com.manavoori.muchhatlu.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.QuerySnapshot;
import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.adapters.PostAdapter;
import com.manavoori.muchhatlu.firebase.PostRepository;
import com.manavoori.muchhatlu.models.Post;
import com.manavoori.muchhatlu.utils.PostInteractions;
import com.manavoori.muchhatlu.utils.RegionData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity implements PostAdapter.Listener {

    private static final int PREFETCH_THRESHOLD = 3;
    private static final String KEY_AUTO_FILL_LOCATION = "auto_fill_location";

    private RecyclerView recyclerView;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private PostAdapter adapter;
    private final PostRepository postRepository = new PostRepository();
    private final Map<String, String> filters = new HashMap<>();
    private boolean isLoading = false;
    private boolean endReached = false;
    private String selectedState = RegionData.getAllLabel();
    private String selectedDistrict = RegionData.getAllLabel();
    private String selectedMandal = RegionData.getAllLabel();
    private String selectedVillage = RegionData.getAllLabel();

    private MaterialButton stateButton;
    private MaterialButton districtButton;
    private MaterialButton mandalButton;
    private MaterialButton villageButton;

    private EditText editPostInput;
    private ImageButton buttonTextImage;
    private ImageButton buttonVideo;
    private ImageButton buttonAudio;
    private Button buttonPost;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private LocationManager locationManager;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        initLocationPermissionLauncher();

        initViews();
        initBottomNav();
        initRecycler();
        bindFilters();
        bindPostCreation();

        swipeRefreshLayout.setOnRefreshListener(this::refreshFeed);

        if (preferences.getBoolean(KEY_AUTO_FILL_LOCATION, false)) {
            attemptAutoDetectLocation(false);
        }

        refreshFeed();
    }

    private void initLocationPermissionLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> attemptAutoDetectLocation(true));
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerPosts);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        editPostInput = findViewById(R.id.editPostInput);
        buttonTextImage = findViewById(R.id.buttonTextImage);
        buttonVideo = findViewById(R.id.buttonVideo);
        buttonAudio = findViewById(R.id.buttonAudio);
        buttonPost = findViewById(R.id.buttonPost);
    }

    private void initRecycler() {
        adapter = new PostAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) {
                    return;
                }
                int totalItemCount = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (!isLoading && !endReached && totalItemCount <= (lastVisible + PREFETCH_THRESHOLD)) {
                    loadMorePosts();
                }
            }
        });
    }

    private void initBottomNav() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.navigation_home);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_home) {
                return true;
            } else if (id == R.id.navigation_add) {
                startActivity(new Intent(this, AddPostActivity.class));
            } else if (id == R.id.navigation_notifications) {
                startActivity(new Intent(this, NotificationsActivity.class));
            } else if (id == R.id.navigation_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
            }
            return true;
        });
    }

    private void bindPostCreation() {
        // Make the input field clickable to open AddPostActivity
        editPostInput.setOnClickListener(v -> openAddPostActivity(null));

        // Text/Image button - open AddPostActivity with text type
        buttonTextImage.setOnClickListener(v -> openAddPostActivity("text"));

        // Video button - open AddPostActivity with video type
        buttonVideo.setOnClickListener(v -> openAddPostActivity("video"));

        // Audio button - open AddPostActivity (audio type not yet supported, opens with default)
        buttonAudio.setOnClickListener(v -> openAddPostActivity(null));

        // POST button - open AddPostActivity
        buttonPost.setOnClickListener(v -> openAddPostActivity(null));
    }

    private void openAddPostActivity(@Nullable String contentType) {
        Intent intent = new Intent(this, AddPostActivity.class);
        if (contentType != null) {
            intent.putExtra("contentType", contentType);
        }
        startActivity(intent);
    }

    private void bindFilters() {
        stateButton = findViewById(R.id.buttonFilterState);
        districtButton = findViewById(R.id.buttonFilterDistrict);
        mandalButton = findViewById(R.id.buttonFilterMandal);
        villageButton = findViewById(R.id.buttonFilterVillage);

        stateButton.setOnClickListener(v -> showFilterDialog(getString(R.string.filter_state), RegionData.getStates(), value -> {
            selectedState = value;
            selectedDistrict = RegionData.getAllLabel();
            selectedMandal = RegionData.getAllLabel();
            selectedVillage = RegionData.getAllLabel();
            stateButton.setText(value);
            districtButton.setText(getString(R.string.filter_district));
            mandalButton.setText(getString(R.string.filter_mandal));
            villageButton.setText(getString(R.string.filter_village));
            updateFilter("state", value);
            updateFilter("district", null);
            updateFilter("mandal", null);
            updateFilter("village", null);
            refreshFeed();
        }));

        districtButton.setOnClickListener(v -> {
            if (RegionData.getAllLabel().equals(selectedState)) {
                Toast.makeText(this, R.string.error_select_state_first, Toast.LENGTH_SHORT).show();
                return;
            }
            showFilterDialog(getString(R.string.filter_district), RegionData.getDistricts(selectedState), value -> {
                selectedDistrict = value;
                selectedMandal = RegionData.getAllLabel();
                selectedVillage = RegionData.getAllLabel();
                districtButton.setText(value);
                mandalButton.setText(getString(R.string.filter_mandal));
                villageButton.setText(getString(R.string.filter_village));
                updateFilter("district", value);
                updateFilter("mandal", null);
                updateFilter("village", null);
                refreshFeed();
            });
        });

        mandalButton.setOnClickListener(v -> {
            if (RegionData.getAllLabel().equals(selectedState)) {
                Toast.makeText(this, R.string.error_select_state_first, Toast.LENGTH_SHORT).show();
                return;
            }
            showFilterDialog(getString(R.string.filter_mandal), RegionData.getMandals(selectedState, selectedDistrict), value -> {
                selectedMandal = value;
                selectedVillage = RegionData.getAllLabel();
                mandalButton.setText(value);
                villageButton.setText(getString(R.string.filter_village));
                updateFilter("mandal", value);
                updateFilter("village", null);
                refreshFeed();
            });
        });

        villageButton.setOnClickListener(v -> {
            if (RegionData.getAllLabel().equals(selectedState)) {
                Toast.makeText(this, R.string.error_select_state_first, Toast.LENGTH_SHORT).show();
                return;
            }
            showFilterDialog(getString(R.string.filter_village), RegionData.getVillages(selectedState, selectedDistrict, selectedMandal), value -> {
                selectedVillage = value;
                villageButton.setText(value);
                updateFilter("village", value);
                refreshFeed();
            });
        });
    }

    private void attemptAutoDetectLocation(boolean fromUser) {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            if (fromUser) {
                locationPermissionLauncher.launch(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION });
            }
            return;
        }
        Location location = null;
        if (fineGranted) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (location == null && coarseGranted) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        handleDetectedLocation(location, fromUser);
    }

    private void handleDetectedLocation(@Nullable Location location, boolean fromUser) {
        if (location == null) {
            if (fromUser) {
                Toast.makeText(this, R.string.message_location_unavailable, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses == null || addresses.isEmpty()) {
                if (fromUser) {
                    Toast.makeText(this, R.string.message_location_unavailable, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            Address address = addresses.get(0);
            applyDetectedLocation(address.getAdminArea(), address.getSubAdminArea(), address.getLocality(), address.getSubLocality());
        } catch (IOException e) {
            if (fromUser) {
                Toast.makeText(this, R.string.message_location_unavailable, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void applyDetectedLocation(@Nullable String state,
                                       @Nullable String district,
                                       @Nullable String mandal,
                                       @Nullable String village) {
        String matchedState = matchOption(RegionData.getStates(), state);
        if (matchedState == null) {
            return;
        }
        selectedState = matchedState;

        String matchedDistrict = matchOption(RegionData.getDistricts(selectedState), district);
        selectedDistrict = matchedDistrict != null ? matchedDistrict : RegionData.getAllLabel();

        String matchedMandal = matchOption(RegionData.getMandals(selectedState, selectedDistrict), mandal);
        selectedMandal = matchedMandal != null ? matchedMandal : RegionData.getAllLabel();

        String matchedVillage = matchOption(RegionData.getVillages(selectedState, selectedDistrict, selectedMandal), village);
        selectedVillage = matchedVillage != null ? matchedVillage : RegionData.getAllLabel();

        updateFilterButtons();
        updateFilter("state", selectedState);
        updateFilter("district", selectedDistrict);
        updateFilter("mandal", selectedMandal);
        updateFilter("village", selectedVillage);

        preferences.edit().putBoolean(KEY_AUTO_FILL_LOCATION, true).apply();
        refreshFeed();
        Toast.makeText(this, R.string.message_location_applied, Toast.LENGTH_SHORT).show();
    }

    private void updateFilterButtons() {
        if (stateButton != null) {
            stateButton.setText(displayValue(selectedState, R.string.filter_state));
        }
        if (districtButton != null) {
            districtButton.setText(displayValue(selectedDistrict, R.string.filter_district));
        }
        if (mandalButton != null) {
            mandalButton.setText(displayValue(selectedMandal, R.string.filter_mandal));
        }
        if (villageButton != null) {
            villageButton.setText(displayValue(selectedVillage, R.string.filter_village));
        }
    }

    private String displayValue(String value, int fallbackRes) {
        if (TextUtils.isEmpty(value) || RegionData.getAllLabel().equalsIgnoreCase(value)) {
            return getString(fallbackRes);
        }
        return value;
    }

    private String matchOption(List<String> options, @Nullable String candidate) {
        if (candidate == null) {
            return null;
        }
        String normalizedCandidate = normalizeRegionName(candidate);
        if (normalizedCandidate.isEmpty()) {
            return null;
        }
        for (String option : options) {
            if (RegionData.getAllLabel().equalsIgnoreCase(option)) {
                continue;
            }
            if (normalizeRegionName(option).equals(normalizedCandidate)) {
                return option;
            }
        }
        for (String option : options) {
            if (RegionData.getAllLabel().equalsIgnoreCase(option)) {
                continue;
            }
            String normalizedOption = normalizeRegionName(option);
            if (normalizedCandidate.contains(normalizedOption) || normalizedOption.contains(normalizedCandidate)) {
                return option;
            }
        }
        return null;
    }

    private String normalizeRegionName(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.toLowerCase(Locale.ENGLISH)
                .replace("district", "")
                .replace("mandal", "")
                .replace("tehsil", "")
                .replace("urban", "")
                .replace("rural", "")
                .replace("municipality", "")
                .replace("city", "")
                .replace("town", "")
                .replaceAll("[^a-z]", "");
        return normalized.trim();
    }

    private void showFilterDialog(String title, List<String> options, OnFilterSelected callback) {
        String[] values = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(values, (dialog, which) -> callback.onSelected(values[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateFilter(String key, String value) {
        if (value == null) {
            filters.remove(key);
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || RegionData.getAllLabel().equalsIgnoreCase(trimmed) || "Select Option".equalsIgnoreCase(trimmed)) {
            filters.remove(key);
            return;
        }
        filters.put(key, trimmed);
    }

    private void refreshFeed() {
        endReached = false;
        postRepository.resetPagination();
        adapter.setItems(new java.util.ArrayList<>(), false);
        loadMorePosts();
    }

    private void loadMorePosts() {
        if (isLoading) {
            return;
        }
        isLoading = true;
        swipeRefreshLayout.setRefreshing(true);
        postRepository.fetchNextPage(filters)
                .addOnCompleteListener(task -> {
                    swipeRefreshLayout.setRefreshing(false);
                    isLoading = false;
                    if (task.isSuccessful()) {
                        QuerySnapshot snapshot = task.getResult();
                        List<Post> fetched = postRepository.parsePosts(snapshot);
                        if (fetched.isEmpty()) {
                            endReached = true;
                        }
                        adapter.setItems(fetched, true);
                        hydratePostsWithUserContext(fetched);
                    } else if (task.getException() != null) {
                        Snackbar.make(recyclerView, task.getException().getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void onPostSelected(@NonNull Post post) {
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
        startActivity(intent);
    }

    @Override
    public void onLikeClicked(@NonNull Post post) {
        toggleLike(post);
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
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.error_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("posts")
                .document(post.getId())
                .update("reported", true)
                .addOnSuccessListener(unused -> Snackbar.make(recyclerView, R.string.message_report_submitted, Snackbar.LENGTH_LONG).show())
                .addOnFailureListener(e -> Snackbar.make(recyclerView, e.getLocalizedMessage(), Snackbar.LENGTH_LONG).show());
    }

    private void toggleLike(Post post) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.error_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        PostInteractions.toggleLike(post)
                .addOnSuccessListener(result -> {
                    long updatedCount = Math.max(0, post.getLikeCount() + result.getDelta());
                    post.setLikeCount(updatedCount);
                    post.setLikedByCurrentUser(result.isLiked());
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
    }

    private void hydratePostsWithUserContext(@NonNull List<Post> posts) {
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

    private interface OnFilterSelected {
        void onSelected(String value);
    }
}

