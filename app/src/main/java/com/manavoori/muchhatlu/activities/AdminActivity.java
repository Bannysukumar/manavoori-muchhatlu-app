package com.manavoori.muchhatlu.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.adapters.AdminPostAdapter;
import com.manavoori.muchhatlu.adapters.AdminUserAdapter;
import com.manavoori.muchhatlu.adapters.ArticleAdapter;
import com.manavoori.muchhatlu.adapters.CategoryAdapter;
import com.manavoori.muchhatlu.models.AdminUser;
import com.manavoori.muchhatlu.models.Post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminActivity extends AppCompatActivity implements AdminPostAdapter.Listener, AdminUserAdapter.Listener {

    private static final int TAB_PENDING = 0;
    private static final int TAB_FLAGGED = 1;
    private static final int TAB_ALL = 2;
    private static final int TAB_USERS = 3;
    private static final int TAB_ANALYTICS = 4;
    private static final int TAB_SETTINGS = 5;
    private static final int TAB_CONTENT_MANAGEMENT = 6;
    private static final int TAB_REGION_MANAGEMENT = 7;

    private AdminPostAdapter postAdapter;
    private AdminUserAdapter userAdapter;
    private ArticleAdapter articleAdapter;
    private CategoryAdapter categoryAdapter;
    private View progressView;
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    private TextView placeholderText;
    private int currentTab = TAB_PENDING;
    private AdminPostAdapter.Mode currentPostMode = AdminPostAdapter.Mode.PENDING;

    // Dashboard statistics TextViews
    private TextView textTotalUsers;
    private TextView textTotalPosts;
    private TextView textPendingPosts;
    private TextView textReports;

    // Search components
    private TextInputLayout layoutSearchUsers;
    private TextInputEditText inputSearchUsers;
    private TextView textSectionTitle;

    // User data for filtering
    private List<AdminUser> allUsers = new ArrayList<>();

    // Real-time listeners
    private ListenerRegistration usersListener;
    private ListenerRegistration postsListener;

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        progressView = findViewById(R.id.progress);
        placeholderText = findViewById(R.id.textPlaceholder);
        recyclerView = findViewById(R.id.recyclerAdmin);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        postAdapter = new AdminPostAdapter(this);
        userAdapter = new AdminUserAdapter(this);
        articleAdapter = new ArticleAdapter(new ArticleAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Map<String, Object> article) {
                showEditArticleDialog(article);
            }

            @Override
            public void onDelete(@NonNull Map<String, Object> article) {
                confirmDeleteArticle((String) article.get("id"), (String) article.get("title"));
            }
        });
        categoryAdapter = new CategoryAdapter(new CategoryAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Map<String, Object> category) {
                showEditCategoryDialog(category);
            }

            @Override
            public void onToggleEnabled(@NonNull Map<String, Object> category, boolean enabled) {
                updateCategoryEnabled((String) category.get("id"), enabled);
            }
        });
        recyclerView.setAdapter(postAdapter);

        // Initialize dashboard statistics TextViews
        textTotalUsers = findViewById(R.id.textTotalUsers);
        textTotalPosts = findViewById(R.id.textTotalPosts);
        textPendingPosts = findViewById(R.id.textPendingPosts);
        textReports = findViewById(R.id.textReports);

        // Initialize search components
        layoutSearchUsers = findViewById(R.id.layoutSearchUsers);
        inputSearchUsers = findViewById(R.id.inputSearchUsers);
        textSectionTitle = findViewById(R.id.textSectionTitle);

        // Setup sidebar navigation
        View sidebar = findViewById(R.id.sidebar);
        View navDashboard = findViewById(R.id.navDashboard);
        View navUsers = findViewById(R.id.navUsers);
        View navPosts = findViewById(R.id.navPosts);
        View navContentManagement = findViewById(R.id.navContentManagement);
        View navRegionManagement = findViewById(R.id.navRegionManagement);
        View navSettings = findViewById(R.id.navSettings);
        ImageButton buttonMenu = findViewById(R.id.buttonMenu);
        ImageButton buttonCloseSidebar = findViewById(R.id.buttonCloseSidebar);
        ImageButton buttonShowMenu = findViewById(R.id.buttonShowMenu);

        // Close sidebar button
        if (buttonCloseSidebar != null) {
            buttonCloseSidebar.setOnClickListener(v -> hideSidebar(sidebar, buttonShowMenu));
        }

        // Menu button in sidebar to show menu (if hidden)
        if (buttonMenu != null) {
            buttonMenu.setOnClickListener(v -> {
                if (sidebar != null && sidebar.getVisibility() != View.VISIBLE) {
                    showSidebar(sidebar, buttonShowMenu);
                }
            });
        }

        // Menu button in main content to show sidebar
        if (buttonShowMenu != null) {
            buttonShowMenu.setOnClickListener(v -> showSidebar(sidebar, buttonShowMenu));
        }

        if (navDashboard != null) {
            navDashboard.setOnClickListener(v -> {
                loadDataForTab(TAB_PENDING);
                updateNavSelection(navDashboard);
            });
        }
        if (navUsers != null) {
            navUsers.setOnClickListener(v -> {
                loadDataForTab(TAB_USERS);
                updateNavSelection(navUsers);
            });
        }
        if (navPosts != null) {
            navPosts.setOnClickListener(v -> {
                loadDataForTab(TAB_ALL);
                updateNavSelection(navPosts);
            });
        }
        if (navContentManagement != null) {
            navContentManagement.setOnClickListener(v -> {
                loadDataForTab(TAB_CONTENT_MANAGEMENT);
                updateNavSelection(navContentManagement);
            });
        }
        if (navRegionManagement != null) {
            navRegionManagement.setOnClickListener(v -> {
                loadDataForTab(TAB_REGION_MANAGEMENT);
                updateNavSelection(navRegionManagement);
            });
        }
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> {
                loadDataForTab(TAB_SETTINGS);
                updateNavSelection(navSettings);
            });
        }

        // Setup search functionality
        if (inputSearchUsers != null) {
            inputSearchUsers.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterUsers(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Initialize with dashboard view
        if (navDashboard != null) {
            updateNavSelection(navDashboard);
        }
        
        // Load real-time dashboard statistics
        loadDashboardStatistics();
        loadDataForTab(TAB_PENDING);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove listeners to prevent memory leaks
        if (usersListener != null) {
            usersListener.remove();
        }
        if (postsListener != null) {
            postsListener.remove();
        }
    }

    private void updateNavSelection(View selectedView) {
        // Reset all navigation items
        View navDashboard = findViewById(R.id.navDashboard);
        View navUsers = findViewById(R.id.navUsers);
        View navPosts = findViewById(R.id.navPosts);
        View navContentManagement = findViewById(R.id.navContentManagement);
        View navRegionManagement = findViewById(R.id.navRegionManagement);
        View navSettings = findViewById(R.id.navSettings);

        if (navDashboard != null) navDashboard.setSelected(false);
        if (navUsers != null) navUsers.setSelected(false);
        if (navPosts != null) navPosts.setSelected(false);
        if (navContentManagement != null) navContentManagement.setSelected(false);
        if (navRegionManagement != null) navRegionManagement.setSelected(false);
        if (navSettings != null) navSettings.setSelected(false);

        // Set selected state
        if (selectedView != null) {
            selectedView.setSelected(true);
        }
    }

    private void loadSettings() {
        View viewSettings = findViewById(R.id.viewSettings);
        if (viewSettings == null) return;

        // Initialize settings views
        com.google.android.material.textfield.TextInputEditText inputAppName = viewSettings.findViewById(R.id.inputAppName);
        com.google.android.material.textfield.TextInputEditText inputPrimaryColor = viewSettings.findViewById(R.id.inputPrimaryColor);
        com.google.android.material.textfield.TextInputEditText inputSecondaryColor = viewSettings.findViewById(R.id.inputSecondaryColor);
        com.google.android.material.textfield.TextInputEditText inputDefaultLanguage = viewSettings.findViewById(R.id.inputDefaultLanguage);
        com.google.android.material.textfield.TextInputEditText inputTimezone = viewSettings.findViewById(R.id.inputTimezone);
        com.google.android.material.textfield.TextInputEditText inputMetaTitle = viewSettings.findViewById(R.id.inputMetaTitle);
        com.google.android.material.textfield.TextInputEditText inputMetaDescription = viewSettings.findViewById(R.id.inputMetaDescription);
        com.google.android.material.textfield.TextInputEditText inputPrivacyPolicyUrl = viewSettings.findViewById(R.id.inputPrivacyPolicyUrl);
        com.google.android.material.textfield.TextInputEditText inputTermsUrl = viewSettings.findViewById(R.id.inputTermsUrl);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeaturePosts = viewSettings.findViewById(R.id.switchFeaturePosts);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeatureComments = viewSettings.findViewById(R.id.switchFeatureComments);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeatureSharing = viewSettings.findViewById(R.id.switchFeatureSharing);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeatureNotifications = viewSettings.findViewById(R.id.switchFeatureNotifications);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeatureGuest = viewSettings.findViewById(R.id.switchFeatureGuest);
        Button buttonSaveSettings = viewSettings.findViewById(R.id.buttonSaveSettings);
        Button buttonUploadLogo = viewSettings.findViewById(R.id.buttonUploadLogo);
        Button buttonUploadFavicon = viewSettings.findViewById(R.id.buttonUploadFavicon);
        Button buttonUploadSocialImage = viewSettings.findViewById(R.id.buttonUploadSocialImage);

        // Load settings from Firestore
        showLoading(true);
        firestore.collection("app_settings")
                .document("global")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        if (inputAppName != null) {
                            inputAppName.setText(documentSnapshot.getString("appName"));
                        }
                        if (inputPrimaryColor != null) {
                            String primaryColorValue = documentSnapshot.getString("primaryColor");
                            inputPrimaryColor.setText(primaryColorValue);
                            updateColorPreview(viewSettings.findViewById(R.id.viewPrimaryColor), primaryColorValue);
                        }
                        if (inputSecondaryColor != null) {
                            String secondaryColorValue = documentSnapshot.getString("secondaryColor");
                            inputSecondaryColor.setText(secondaryColorValue);
                            updateColorPreview(viewSettings.findViewById(R.id.viewSecondaryColor), secondaryColorValue);
                        }
                        if (inputDefaultLanguage != null) {
                            inputDefaultLanguage.setText(documentSnapshot.getString("defaultLanguage"));
                        }
                        if (inputTimezone != null) {
                            inputTimezone.setText(documentSnapshot.getString("timezone"));
                        }
                        if (inputMetaTitle != null) {
                            inputMetaTitle.setText(documentSnapshot.getString("metaTitle"));
                        }
                        if (inputMetaDescription != null) {
                            inputMetaDescription.setText(documentSnapshot.getString("metaDescription"));
                        }
                        if (inputPrivacyPolicyUrl != null) {
                            inputPrivacyPolicyUrl.setText(documentSnapshot.getString("privacyPolicyUrl"));
                        }
                        if (inputTermsUrl != null) {
                            inputTermsUrl.setText(documentSnapshot.getString("termsUrl"));
                        }
                        if (switchFeaturePosts != null) {
                            switchFeaturePosts.setChecked(documentSnapshot.getBoolean("featurePosts") != null && documentSnapshot.getBoolean("featurePosts"));
                        }
                        if (switchFeatureComments != null) {
                            switchFeatureComments.setChecked(documentSnapshot.getBoolean("featureComments") != null && documentSnapshot.getBoolean("featureComments"));
                        }
                        if (switchFeatureSharing != null) {
                            switchFeatureSharing.setChecked(documentSnapshot.getBoolean("featureSharing") != null && documentSnapshot.getBoolean("featureSharing"));
                        }
                        if (switchFeatureNotifications != null) {
                            switchFeatureNotifications.setChecked(documentSnapshot.getBoolean("featureNotifications") != null && documentSnapshot.getBoolean("featureNotifications"));
                        }
                        if (switchFeatureGuest != null) {
                            switchFeatureGuest.setChecked(documentSnapshot.getBoolean("featureGuest") != null && documentSnapshot.getBoolean("featureGuest"));
                        }
                    } else {
                        // Set default values
                        if (inputAppName != null) {
                            inputAppName.setText(getString(R.string.app_name));
                        }
                        if (inputPrimaryColor != null) {
                            String defaultPrimary = "#1A5C6B";
                            inputPrimaryColor.setText(defaultPrimary);
                            updateColorPreview(viewSettings.findViewById(R.id.viewPrimaryColor), defaultPrimary);
                        }
                        if (inputSecondaryColor != null) {
                            String defaultSecondary = "#B8734A";
                            inputSecondaryColor.setText(defaultSecondary);
                            updateColorPreview(viewSettings.findViewById(R.id.viewSecondaryColor), defaultSecondary);
                        }
                        if (inputDefaultLanguage != null) {
                            inputDefaultLanguage.setText("en");
                        }
                        if (inputTimezone != null) {
                            inputTimezone.setText("Asia/Kolkata");
                        }
                        if (switchFeaturePosts != null) switchFeaturePosts.setChecked(true);
                        if (switchFeatureComments != null) switchFeatureComments.setChecked(true);
                        if (switchFeatureSharing != null) switchFeatureSharing.setChecked(true);
                        if (switchFeatureNotifications != null) switchFeatureNotifications.setChecked(true);
                        if (switchFeatureGuest != null) switchFeatureGuest.setChecked(false);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to load settings: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });

        // Set up save button
        if (buttonSaveSettings != null) {
            buttonSaveSettings.setOnClickListener(v -> saveSettings());
        }

        // Set up color preview updates
        if (inputPrimaryColor != null) {
            inputPrimaryColor.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateColorPreview(viewSettings.findViewById(R.id.viewPrimaryColor), s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
        if (inputSecondaryColor != null) {
            inputSecondaryColor.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateColorPreview(viewSettings.findViewById(R.id.viewSecondaryColor), s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Set up image upload buttons (placeholder - implement image picker)
        if (buttonUploadLogo != null) {
            buttonUploadLogo.setOnClickListener(v -> {
                // TODO: Implement image picker for logo
                Toast.makeText(this, "Logo upload functionality coming soon", Toast.LENGTH_SHORT).show();
            });
        }
        if (buttonUploadFavicon != null) {
            buttonUploadFavicon.setOnClickListener(v -> {
                // TODO: Implement image picker for favicon
                Toast.makeText(this, "Favicon upload functionality coming soon", Toast.LENGTH_SHORT).show();
            });
        }
        if (buttonUploadSocialImage != null) {
            buttonUploadSocialImage.setOnClickListener(v -> {
                // TODO: Implement image picker for social image
                Toast.makeText(this, "Social image upload functionality coming soon", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void updateColorPreview(View colorView, String colorHex) {
        if (colorView == null || colorHex == null || colorHex.trim().isEmpty()) {
            return;
        }
        try {
            String hex = colorHex.trim();
            if (!hex.startsWith("#")) {
                hex = "#" + hex;
            }
            if (hex.length() == 7 && hex.matches("^#[0-9A-Fa-f]{6}$")) {
                int color = Color.parseColor(hex);
                colorView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            }
        } catch (IllegalArgumentException e) {
            // Invalid color format, ignore
        }
    }

    private void saveSettings() {
        View viewSettings = findViewById(R.id.viewSettings);
        if (viewSettings == null) return;

        com.google.android.material.textfield.TextInputEditText inputAppName = viewSettings.findViewById(R.id.inputAppName);
        com.google.android.material.textfield.TextInputEditText inputPrimaryColor = viewSettings.findViewById(R.id.inputPrimaryColor);
        com.google.android.material.textfield.TextInputEditText inputSecondaryColor = viewSettings.findViewById(R.id.inputSecondaryColor);
        com.google.android.material.textfield.TextInputEditText inputDefaultLanguage = viewSettings.findViewById(R.id.inputDefaultLanguage);
        com.google.android.material.textfield.TextInputEditText inputTimezone = viewSettings.findViewById(R.id.inputTimezone);
        com.google.android.material.textfield.TextInputEditText inputMetaTitle = viewSettings.findViewById(R.id.inputMetaTitle);
        com.google.android.material.textfield.TextInputEditText inputMetaDescription = viewSettings.findViewById(R.id.inputMetaDescription);
        com.google.android.material.textfield.TextInputEditText inputPrivacyPolicyUrl = viewSettings.findViewById(R.id.inputPrivacyPolicyUrl);
        com.google.android.material.textfield.TextInputEditText inputTermsUrl = viewSettings.findViewById(R.id.inputTermsUrl);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeaturePosts = viewSettings.findViewById(R.id.switchFeaturePosts);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeatureComments = viewSettings.findViewById(R.id.switchFeatureComments);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeatureSharing = viewSettings.findViewById(R.id.switchFeatureSharing);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeatureNotifications = viewSettings.findViewById(R.id.switchFeatureNotifications);
        com.google.android.material.switchmaterial.SwitchMaterial switchFeatureGuest = viewSettings.findViewById(R.id.switchFeatureGuest);

        // Validate color formats
        String primaryColor = inputPrimaryColor != null && inputPrimaryColor.getText() != null ? inputPrimaryColor.getText().toString().trim() : "";
        String secondaryColor = inputSecondaryColor != null && inputSecondaryColor.getText() != null ? inputSecondaryColor.getText().toString().trim() : "";

        if (!primaryColor.isEmpty() && !primaryColor.matches("^#[0-9A-Fa-f]{6}$")) {
            Toast.makeText(this, R.string.error_invalid_color, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!secondaryColor.isEmpty() && !secondaryColor.matches("^#[0-9A-Fa-f]{6}$")) {
            Toast.makeText(this, R.string.error_invalid_color, Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate URLs
        String privacyUrl = inputPrivacyPolicyUrl != null && inputPrivacyPolicyUrl.getText() != null ? inputPrivacyPolicyUrl.getText().toString().trim() : "";
        String termsUrl = inputTermsUrl != null && inputTermsUrl.getText() != null ? inputTermsUrl.getText().toString().trim() : "";

        if (!privacyUrl.isEmpty() && !android.util.Patterns.WEB_URL.matcher(privacyUrl).matches()) {
            Toast.makeText(this, R.string.error_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!termsUrl.isEmpty() && !android.util.Patterns.WEB_URL.matcher(termsUrl).matches()) {
            Toast.makeText(this, R.string.error_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepare settings data
        Map<String, Object> settings = new HashMap<>();
        if (inputAppName != null && inputAppName.getText() != null) {
            settings.put("appName", inputAppName.getText().toString().trim());
        }
        if (!primaryColor.isEmpty()) {
            settings.put("primaryColor", primaryColor);
        }
        if (!secondaryColor.isEmpty()) {
            settings.put("secondaryColor", secondaryColor);
        }
        if (inputDefaultLanguage != null && inputDefaultLanguage.getText() != null) {
            settings.put("defaultLanguage", inputDefaultLanguage.getText().toString().trim());
        }
        if (inputTimezone != null && inputTimezone.getText() != null) {
            settings.put("timezone", inputTimezone.getText().toString().trim());
        }
        if (inputMetaTitle != null && inputMetaTitle.getText() != null) {
            settings.put("metaTitle", inputMetaTitle.getText().toString().trim());
        }
        if (inputMetaDescription != null && inputMetaDescription.getText() != null) {
            settings.put("metaDescription", inputMetaDescription.getText().toString().trim());
        }
        if (!privacyUrl.isEmpty()) {
            settings.put("privacyPolicyUrl", privacyUrl);
        }
        if (!termsUrl.isEmpty()) {
            settings.put("termsUrl", termsUrl);
        }
        if (switchFeaturePosts != null) {
            settings.put("featurePosts", switchFeaturePosts.isChecked());
        }
        if (switchFeatureComments != null) {
            settings.put("featureComments", switchFeatureComments.isChecked());
        }
        if (switchFeatureSharing != null) {
            settings.put("featureSharing", switchFeatureSharing.isChecked());
        }
        if (switchFeatureNotifications != null) {
            settings.put("featureNotifications", switchFeatureNotifications.isChecked());
        }
        if (switchFeatureGuest != null) {
            settings.put("featureGuest", switchFeatureGuest.isChecked());
        }
        settings.put("updatedAt", System.currentTimeMillis());

        // Save to Firestore
        showLoading(true);
        firestore.collection("app_settings")
                .document("global")
                .set(settings, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_settings_saved, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.error_settings_save_failed + ": " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void toggleSidebar(View sidebar, ImageButton buttonShowMenu) {
        if (sidebar == null) return;
        if (sidebar.getVisibility() == View.VISIBLE) {
            hideSidebar(sidebar, buttonShowMenu);
        } else {
            showSidebar(sidebar, buttonShowMenu);
        }
    }

    private void showSidebar(View sidebar, ImageButton buttonShowMenu) {
        if (sidebar == null) return;
        sidebar.setVisibility(View.VISIBLE);
        if (buttonShowMenu != null) {
            buttonShowMenu.setVisibility(View.GONE);
        }
        // Update main content constraint
        androidx.constraintlayout.widget.ConstraintLayout parent = (androidx.constraintlayout.widget.ConstraintLayout) sidebar.getParent();
        if (parent != null) {
            View scrollMain = findViewById(R.id.scrollMain);
            if (scrollMain != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) scrollMain.getLayoutParams();
                params.startToEnd = R.id.sidebar;
                params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
                scrollMain.setLayoutParams(params);
            }
        }
    }

    private void hideSidebar(View sidebar, ImageButton buttonShowMenu) {
        if (sidebar == null) return;
        sidebar.setVisibility(View.GONE);
        if (buttonShowMenu != null) {
            buttonShowMenu.setVisibility(View.VISIBLE);
        }
        // Update main content constraint to span full width
        androidx.constraintlayout.widget.ConstraintLayout parent = (androidx.constraintlayout.widget.ConstraintLayout) sidebar.getParent();
        if (parent != null) {
            View scrollMain = findViewById(R.id.scrollMain);
            if (scrollMain != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) scrollMain.getLayoutParams();
                params.startToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
                params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
                scrollMain.setLayoutParams(params);
            }
        }
    }

    private void loadDataForTab(int position) {
        currentTab = position;
        updateFabForTab(position);
        
        // Show/hide dashboard view
        View viewDashboard = findViewById(R.id.viewDashboard);
        if (viewDashboard != null) {
            viewDashboard.setVisibility(position == TAB_PENDING ? View.VISIBLE : View.GONE);
        }

        // Show/hide legacy dashboard statistics (hidden when new dashboard is shown)
        View gridDashboard = findViewById(R.id.gridDashboard);
        if (gridDashboard != null) {
            gridDashboard.setVisibility(View.GONE);
        }

        // Show/hide search field based on selected tab
        if (layoutSearchUsers != null) {
            layoutSearchUsers.setVisibility(position == TAB_USERS ? View.VISIBLE : View.GONE);
        }

        // Show/hide settings view
        View viewSettings = findViewById(R.id.viewSettings);
        if (viewSettings != null) {
            viewSettings.setVisibility(position == TAB_SETTINGS ? View.VISIBLE : View.GONE);
        }

        // Show/hide content management view
        View viewContentManagement = findViewById(R.id.viewContentManagement);
        if (viewContentManagement != null) {
            viewContentManagement.setVisibility(position == TAB_CONTENT_MANAGEMENT ? View.VISIBLE : View.GONE);
        }

        // Show/hide region management view
        View viewRegionManagement = findViewById(R.id.viewRegionManagement);
        if (viewRegionManagement != null) {
            viewRegionManagement.setVisibility(position == TAB_REGION_MANAGEMENT ? View.VISIBLE : View.GONE);
        } else if (position == TAB_REGION_MANAGEMENT) {
            loadRegionManagement();
        }

        // Show/hide RecyclerView and section title
        if (recyclerView != null) {
            recyclerView.setVisibility((position == TAB_SETTINGS || position == TAB_PENDING || position == TAB_CONTENT_MANAGEMENT || position == TAB_REGION_MANAGEMENT) ? View.GONE : View.VISIBLE);
        }
        if (textSectionTitle != null) {
            textSectionTitle.setVisibility(position == TAB_SETTINGS || position == TAB_PENDING || position == TAB_CONTENT_MANAGEMENT || position == TAB_REGION_MANAGEMENT ? View.GONE : View.VISIBLE);
        }

        // Update section title
        if (textSectionTitle != null) {
            switch (position) {
                case TAB_PENDING:
                    textSectionTitle.setText("Pending Posts");
                    break;
                case TAB_FLAGGED:
                    textSectionTitle.setText("Flagged Posts");
                    break;
                case TAB_ALL:
                    textSectionTitle.setText("All Posts");
                    break;
                case TAB_USERS:
                    textSectionTitle.setText("All Users");
                    break;
                case TAB_ANALYTICS:
                    textSectionTitle.setText("Analytics");
                    break;
                case TAB_SETTINGS:
                    textSectionTitle.setText("Settings");
                    break;
                case TAB_CONTENT_MANAGEMENT:
                    textSectionTitle.setText("Content Management");
                    break;
                case TAB_REGION_MANAGEMENT:
                    textSectionTitle.setText("Region Management");
                    break;
                default:
                    textSectionTitle.setText("Dashboard");
                    break;
            }
        }

        // Clear search when switching tabs
        if (inputSearchUsers != null && position != TAB_USERS) {
            inputSearchUsers.setText("");
        }
        
        switch (position) {
            case TAB_PENDING:
                loadDashboardData();
                break;
            case TAB_FLAGGED:
                loadPosts(firestore.collection("posts")
                        .whereEqualTo("reported", true)
                        .orderBy("createdAt", Query.Direction.DESCENDING), AdminPostAdapter.Mode.FLAGGED);
                break;
            case TAB_ALL:
                loadPosts(firestore.collection("posts")
                        .orderBy("createdAt", Query.Direction.DESCENDING), AdminPostAdapter.Mode.ALL);
                break;
            case TAB_USERS:
                loadUsers();
                break;
            case TAB_ANALYTICS:
                loadAnalytics();
                break;
            case TAB_SETTINGS:
                loadSettings();
                break;
            case TAB_CONTENT_MANAGEMENT:
                loadContentManagement();
                break;
            case TAB_REGION_MANAGEMENT:
                loadRegionManagement();
                break;
        }
    }

    private void loadContentManagement() {
        View viewContentManagement = findViewById(R.id.viewContentManagement);
        if (viewContentManagement == null) return;

        // Initialize tab layout and views
        com.google.android.material.tabs.TabLayout tabLayout = viewContentManagement.findViewById(R.id.tabContentManagement);
        LinearLayout layoutArticles = viewContentManagement.findViewById(R.id.layoutArticles);
        LinearLayout layoutCategories = viewContentManagement.findViewById(R.id.layoutCategories);
        LinearLayout layoutBreakingNews = viewContentManagement.findViewById(R.id.layoutBreakingNews);
        androidx.recyclerview.widget.RecyclerView recyclerArticles = viewContentManagement.findViewById(R.id.recyclerArticles);
        androidx.recyclerview.widget.RecyclerView recyclerCategories = viewContentManagement.findViewById(R.id.recyclerCategories);
        androidx.recyclerview.widget.RecyclerView recyclerBreakingNews = viewContentManagement.findViewById(R.id.recyclerBreakingNews);
        Button buttonCreateArticle = viewContentManagement.findViewById(R.id.buttonCreateArticle);
        Button buttonCreateCategory = viewContentManagement.findViewById(R.id.buttonCreateCategory);
        Button buttonReorderCategories = viewContentManagement.findViewById(R.id.buttonReorderCategories);

        // Setup tab selection
        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    int position = tab.getPosition();
                    if (layoutArticles != null) {
                        layoutArticles.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                    }
                    if (layoutCategories != null) {
                        layoutCategories.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                    }
                    if (layoutBreakingNews != null) {
                        layoutBreakingNews.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                    }

                    // Load data for selected tab
                    if (position == 0) {
                        loadArticles(recyclerArticles);
                    } else if (position == 1) {
                        loadCategories(recyclerCategories);
                    } else if (position == 2) {
                        loadBreakingNews(recyclerBreakingNews);
                    }
                }

                @Override
                public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            });
        }

        // Setup button listeners
        if (buttonCreateArticle != null) {
            buttonCreateArticle.setOnClickListener(v -> showCreateArticleDialog());
        }
        if (buttonCreateCategory != null) {
            buttonCreateCategory.setOnClickListener(v -> showCreateCategoryDialog());
        }
        if (buttonReorderCategories != null) {
            buttonReorderCategories.setOnClickListener(v -> {
                Toast.makeText(this, "Reorder categories functionality coming soon", Toast.LENGTH_SHORT).show();
            });
        }

        // Load initial data (Articles tab)
        loadArticles(recyclerArticles);
    }

    private void loadArticles(androidx.recyclerview.widget.RecyclerView recyclerView) {
        if (recyclerView == null) return;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        showLoading(true);
        
        firestore.collection("articles")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    showLoading(false);
                    List<Map<String, Object>> articles = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Map<String, Object> article = new HashMap<>();
                        article.put("id", doc.getId());
                        article.putAll(doc.getData());
                        // Load author name
                        String authorId = doc.getString("authorId");
                        if (authorId != null) {
                            loadAuthorName(authorId, article);
                        }
                        articles.add(article);
                    }
                    recyclerView.setAdapter(articleAdapter);
                    articleAdapter.submitList(articles);
                    if (articles.isEmpty()) {
                        Toast.makeText(this, "No articles found. Create your first article!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    recyclerView.setAdapter(articleAdapter);
                    articleAdapter.submitList(new ArrayList<>());
                });
    }

    private void loadAuthorName(String authorId, Map<String, Object> article) {
        firestore.collection("users")
                .document(authorId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        article.put("authorName", name != null ? name : "Admin");
                        articleAdapter.notifyDataSetChanged();
                    } else {
                        article.put("authorName", "Admin");
                    }
                });
    }

    private void loadCategories(androidx.recyclerview.widget.RecyclerView recyclerView) {
        if (recyclerView == null) return;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        showLoading(true);
        
        firestore.collection("categories")
                .orderBy("priority", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    showLoading(false);
                    List<Map<String, Object>> categories = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Map<String, Object> category = new HashMap<>();
                        category.put("id", doc.getId());
                        category.putAll(doc.getData());
                        // Load post count for category
                        loadCategoryPostCount(doc.getId(), category);
                        categories.add(category);
                    }
                    recyclerView.setAdapter(categoryAdapter);
                    categoryAdapter.submitList(categories);
                    if (categories.isEmpty()) {
                        Toast.makeText(this, "No categories found. Create your first category!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    recyclerView.setAdapter(categoryAdapter);
                    categoryAdapter.submitList(new ArrayList<>());
                });
    }

    private void loadCategoryPostCount(String categoryId, Map<String, Object> category) {
        firestore.collection("articles")
                .whereEqualTo("category", category.get("name"))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    category.put("postCount", querySnapshot.size());
                    categoryAdapter.notifyDataSetChanged();
                });
    }

    private void loadBreakingNews(androidx.recyclerview.widget.RecyclerView recyclerView) {
        if (recyclerView == null) return;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        showLoading(true);
        
        long now = System.currentTimeMillis();
        firestore.collection("articles")
                .whereEqualTo("breaking", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    showLoading(false);
                    List<Post> posts = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Long expiryAt = doc.getLong("breakingExpiryAt");
                        // Only show if not expired
                        if (expiryAt == null || expiryAt > now) {
                            Post post = new Post();
                            post.setId(doc.getId());
                            post.setCaption(doc.getString("title"));
                            post.setCreatedAt(doc.getLong("createdAt") != null ? doc.getLong("createdAt") : 0);
                            posts.add(post);
                        }
                    }
                    recyclerView.setAdapter(postAdapter);
                    postAdapter.submitList(posts);
                    if (posts.isEmpty()) {
                        Toast.makeText(this, "No active breaking news", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    recyclerView.setAdapter(postAdapter);
                    postAdapter.submitList(new ArrayList<>());
                });
    }

    private void showCreateArticleDialog() {
        showCreateArticleDialog(null);
    }

    private void showCreateArticleDialog(@Nullable Post existingPost) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_create_article, null);
        
        TextInputLayout layoutTitle = view.findViewById(R.id.layoutTitle);
        TextInputLayout layoutContent = view.findViewById(R.id.layoutContent);
        TextInputLayout layoutExcerpt = view.findViewById(R.id.layoutExcerpt);
        TextInputLayout layoutCategory = view.findViewById(R.id.layoutCategory);
        TextInputLayout layoutTags = view.findViewById(R.id.layoutTags);
        TextInputLayout layoutScheduleDate = view.findViewById(R.id.layoutScheduleDate);
        TextInputLayout layoutBreakingExpiry = view.findViewById(R.id.layoutBreakingExpiry);
        TextInputLayout layoutMetaTitle = view.findViewById(R.id.layoutMetaTitle);
        TextInputLayout layoutMetaDescription = view.findViewById(R.id.layoutMetaDescription);
        
        TextInputEditText inputTitle = view.findViewById(R.id.inputTitle);
        TextInputEditText inputContent = view.findViewById(R.id.inputContent);
        TextInputEditText inputExcerpt = view.findViewById(R.id.inputExcerpt);
        TextInputEditText inputCategory = view.findViewById(R.id.inputCategory);
        TextInputEditText inputTags = view.findViewById(R.id.inputTags);
        TextInputEditText inputScheduleDate = view.findViewById(R.id.inputScheduleDate);
        TextInputEditText inputBreakingExpiry = view.findViewById(R.id.inputBreakingExpiry);
        TextInputEditText inputMetaTitle = view.findViewById(R.id.inputMetaTitle);
        TextInputEditText inputMetaDescription = view.findViewById(R.id.inputMetaDescription);
        
        com.google.android.material.button.MaterialButtonToggleGroup toggleGroupStatus = view.findViewById(R.id.toggleGroupStatus);
        com.google.android.material.button.MaterialButton buttonDraft = view.findViewById(R.id.buttonDraft);
        com.google.android.material.button.MaterialButton buttonPublished = view.findViewById(R.id.buttonPublished);
        com.google.android.material.button.MaterialButton buttonScheduled = view.findViewById(R.id.buttonScheduled);
        
        com.google.android.material.switchmaterial.SwitchMaterial switchFeatured = view.findViewById(R.id.switchFeatured);
        com.google.android.material.switchmaterial.SwitchMaterial switchPinned = view.findViewById(R.id.switchPinned);
        com.google.android.material.switchmaterial.SwitchMaterial switchBreaking = view.findViewById(R.id.switchBreaking);
        
        Button buttonAddImage = view.findViewById(R.id.buttonAddImage);
        Button buttonAddVideo = view.findViewById(R.id.buttonAddVideo);
        Button buttonAddGallery = view.findViewById(R.id.buttonAddGallery);

        // Load categories for selection
        loadCategoriesForSelection(inputCategory);

        // Handle status toggle
        toggleGroupStatus.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                layoutScheduleDate.setVisibility(checkedId == buttonScheduled.getId() ? View.VISIBLE : View.GONE);
            }
        });

        // Handle breaking news toggle
        switchBreaking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutBreakingExpiry.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Handle schedule date picker
        inputScheduleDate.setOnClickListener(v -> showDateTimePicker(inputScheduleDate, "Schedule Date"));

        // Handle breaking expiry picker
        inputBreakingExpiry.setOnClickListener(v -> showDateTimePicker(inputBreakingExpiry, "Breaking News Expiry"));

        // Handle media buttons
        buttonAddImage.setOnClickListener(v -> {
            // TODO: Open image picker
            Toast.makeText(this, "Image picker coming soon", Toast.LENGTH_SHORT).show();
        });

        buttonAddVideo.setOnClickListener(v -> {
            // TODO: Open video picker
            Toast.makeText(this, "Video picker coming soon", Toast.LENGTH_SHORT).show();
        });

        buttonAddGallery.setOnClickListener(v -> {
            // TODO: Open gallery picker
            Toast.makeText(this, "Gallery picker coming soon", Toast.LENGTH_SHORT).show();
        });

        // Pre-fill if editing
        if (existingPost != null && existingPost.getId() != null) {
            firestore.collection("articles")
                    .document(existingPost.getId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            inputTitle.setText(documentSnapshot.getString("title"));
                            inputContent.setText(documentSnapshot.getString("content"));
                            inputExcerpt.setText(documentSnapshot.getString("excerpt"));
                            inputCategory.setText(documentSnapshot.getString("category"));
                            
                            List<String> tagsList = (List<String>) documentSnapshot.get("tags");
                            if (tagsList != null && !tagsList.isEmpty()) {
                                inputTags.setText(TextUtils.join(", ", tagsList));
                            }
                            
                            String status = documentSnapshot.getString("status");
                            if ("published".equals(status)) {
                                buttonPublished.setChecked(true);
                            } else if ("scheduled".equals(status)) {
                                buttonScheduled.setChecked(true);
                                layoutScheduleDate.setVisibility(View.VISIBLE);
                                Long scheduledAt = documentSnapshot.getLong("scheduledAt");
                                if (scheduledAt != null) {
                                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                                    inputScheduleDate.setText(sdf.format(new java.util.Date(scheduledAt)));
                                    inputScheduleDate.setTag(scheduledAt);
                                }
                            } else {
                                buttonDraft.setChecked(true);
                            }
                            
                            switchFeatured.setChecked(documentSnapshot.getBoolean("featured") != null && documentSnapshot.getBoolean("featured"));
                            switchPinned.setChecked(documentSnapshot.getBoolean("pinned") != null && documentSnapshot.getBoolean("pinned"));
                            switchBreaking.setChecked(documentSnapshot.getBoolean("breaking") != null && documentSnapshot.getBoolean("breaking"));
                            
                            if (switchBreaking.isChecked()) {
                                layoutBreakingExpiry.setVisibility(View.VISIBLE);
                                Long breakingExpiryAt = documentSnapshot.getLong("breakingExpiryAt");
                                if (breakingExpiryAt != null) {
                                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                                    inputBreakingExpiry.setText(sdf.format(new java.util.Date(breakingExpiryAt)));
                                    inputBreakingExpiry.setTag(breakingExpiryAt);
                                }
                            }
                            
                            inputMetaTitle.setText(documentSnapshot.getString("metaTitle"));
                            inputMetaDescription.setText(documentSnapshot.getString("metaDescription"));
                        }
                    });
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(existingPost == null ? R.string.title_create_article : R.string.title_edit_article)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save_article, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                layoutTitle.setError(null);
                layoutContent.setError(null);

                String title = inputTitle.getText() != null ? inputTitle.getText().toString().trim() : "";
                String content = inputContent.getText() != null ? inputContent.getText().toString().trim() : "";
                String excerpt = inputExcerpt.getText() != null ? inputExcerpt.getText().toString().trim() : "";
                String category = inputCategory.getText() != null ? inputCategory.getText().toString().trim() : "";
                String tags = inputTags.getText() != null ? inputTags.getText().toString().trim() : "";
                String metaTitle = inputMetaTitle.getText() != null ? inputMetaTitle.getText().toString().trim() : "";
                String metaDescription = inputMetaDescription.getText() != null ? inputMetaDescription.getText().toString().trim() : "";

                if (TextUtils.isEmpty(title)) {
                    layoutTitle.setError(getString(R.string.error_article_title_required));
                    return;
                }
                if (TextUtils.isEmpty(content)) {
                    layoutContent.setError(getString(R.string.error_article_content_required));
                    return;
                }

                // Get selected status
                int selectedButtonId = toggleGroupStatus.getCheckedButtonId();
                String status = "draft";
                if (selectedButtonId == buttonPublished.getId()) {
                    status = "published";
                } else if (selectedButtonId == buttonScheduled.getId()) {
                    status = "scheduled";
                }

                saveArticle(existingPost != null ? existingPost.getId() : null, title, content, excerpt, category, tags,
                        status, switchFeatured.isChecked(), switchPinned.isChecked(), switchBreaking.isChecked(),
                        metaTitle, metaDescription, inputScheduleDate, inputBreakingExpiry);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void loadCategoriesForSelection(TextInputEditText inputCategory) {
        firestore.collection("categories")
                .whereEqualTo("enabled", true)
                .orderBy("priority", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> categoryNames = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String name = doc.getString("name");
                        if (name != null) {
                            categoryNames.add(name);
                        }
                    }
                    // TODO: Show category selection dialog or spinner
                    inputCategory.setOnClickListener(v -> {
                        showCategorySelectionDialog(inputCategory, categoryNames);
                    });
                })
                .addOnFailureListener(e -> {
                    // If no categories exist, allow manual entry
                    inputCategory.setFocusable(true);
                    inputCategory.setClickable(false);
                });
    }

    private void showCategorySelectionDialog(TextInputEditText inputCategory, List<String> categories) {
        String[] categoryArray = categories.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.hint_article_category)
                .setItems(categoryArray, (dialog, which) -> {
                    inputCategory.setText(categoryArray[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveArticle(@Nullable String articleId, String title, String content, String excerpt, String category,
                            String tags, String status, boolean featured, boolean pinned, boolean breaking,
                            String metaTitle, String metaDescription, TextInputEditText inputScheduleDate, TextInputEditText inputBreakingExpiry) {
        showLoading(true);
        
        Map<String, Object> articleData = new HashMap<>();
        articleData.put("title", title);
        articleData.put("content", content);
        articleData.put("excerpt", excerpt);
        articleData.put("category", category);
        if (!TextUtils.isEmpty(tags)) {
            articleData.put("tags", java.util.Arrays.asList(tags.split(",")));
        }
        articleData.put("status", status);
        articleData.put("featured", featured);
        articleData.put("pinned", pinned);
        articleData.put("breaking", breaking);
        articleData.put("metaTitle", metaTitle);
        articleData.put("metaDescription", metaDescription);
        
        // Preserve existing counts if editing
        if (articleId == null) {
            articleData.put("viewCount", 0);
            articleData.put("likeCount", 0);
            articleData.put("commentCount", 0);
            articleData.put("shareCount", 0);
            articleData.put("createdAt", System.currentTimeMillis());
        }
        
        if (inputScheduleDate != null && inputScheduleDate.getText() != null && !TextUtils.isEmpty(inputScheduleDate.getText().toString())) {
            Object scheduleTimestamp = inputScheduleDate.getTag();
            if (scheduleTimestamp instanceof Long) {
                articleData.put("scheduledAt", scheduleTimestamp);
            } else {
                // Try to parse the date string
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                    java.util.Date date = sdf.parse(inputScheduleDate.getText().toString());
                    if (date != null) {
                        articleData.put("scheduledAt", date.getTime());
                    }
                } catch (Exception e) {
                    // If parsing fails, use placeholder
                    articleData.put("scheduledAt", System.currentTimeMillis() + (24 * 60 * 60 * 1000));
                }
            }
        }
        
        if (inputBreakingExpiry != null && inputBreakingExpiry.getText() != null && !TextUtils.isEmpty(inputBreakingExpiry.getText().toString())) {
            Object expiryTimestamp = inputBreakingExpiry.getTag();
            if (expiryTimestamp instanceof Long) {
                articleData.put("breakingExpiryAt", expiryTimestamp);
            } else {
                // Try to parse the date string
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                    java.util.Date date = sdf.parse(inputBreakingExpiry.getText().toString());
                    if (date != null) {
                        articleData.put("breakingExpiryAt", date.getTime());
                    }
                } catch (Exception e) {
                    // If parsing fails, use placeholder
                    articleData.put("breakingExpiryAt", System.currentTimeMillis() + (2 * 60 * 60 * 1000));
                }
            }
        }
        
        articleData.put("updatedAt", System.currentTimeMillis());
        if (articleId == null) {
            articleData.put("createdAt", System.currentTimeMillis());
            articleData.put("authorId", com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid());
        }

        if (articleId == null) {
            // Create new article
            firestore.collection("articles")
                    .add(articleData)
                    .addOnSuccessListener(documentReference -> {
                        showLoading(false);
                        Toast.makeText(this, R.string.message_article_saved, Toast.LENGTH_SHORT).show();
                        refreshContentManagement();
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(this, "Failed to save article: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            // Update existing article
            articleData.put("updatedAt", System.currentTimeMillis());
            firestore.collection("articles")
                    .document(articleId)
                    .set(articleData, SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        showLoading(false);
                        Toast.makeText(this, R.string.message_article_saved, Toast.LENGTH_SHORT).show();
                        refreshContentManagement();
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(this, "Failed to update article: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void showCreateCategoryDialog() {
        showCreateCategoryDialog(null, null);
    }

    private void showCreateCategoryDialog(@Nullable String categoryId, @Nullable String existingName) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_create_category, null);
        
        TextInputLayout layoutName = view.findViewById(R.id.layoutName);
        TextInputLayout layoutDescription = view.findViewById(R.id.layoutDescription);
        TextInputLayout layoutPriority = view.findViewById(R.id.layoutPriority);
        
        TextInputEditText inputName = view.findViewById(R.id.inputName);
        TextInputEditText inputDescription = view.findViewById(R.id.inputDescription);
        TextInputEditText inputPriority = view.findViewById(R.id.inputPriority);
        TextInputEditText inputEditor = view.findViewById(R.id.inputEditor);
        
        com.google.android.material.switchmaterial.SwitchMaterial switchEnabled = view.findViewById(R.id.switchEnabled);

        // Pre-fill if editing
        if (categoryId != null) {
            firestore.collection("categories")
                    .document(categoryId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            inputName.setText(documentSnapshot.getString("name"));
                            inputDescription.setText(documentSnapshot.getString("description"));
                            Long priority = documentSnapshot.getLong("priority");
                            if (priority != null) {
                                inputPriority.setText(String.valueOf(priority.intValue()));
                            }
                            switchEnabled.setChecked(documentSnapshot.getBoolean("enabled") != null && documentSnapshot.getBoolean("enabled"));
                            String editorId = documentSnapshot.getString("editorId");
                            if (editorId != null && !editorId.isEmpty()) {
                                inputEditor.setText(editorId);
                            }
                        }
                    });
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(categoryId == null ? R.string.title_create_category : R.string.title_edit_category)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                layoutName.setError(null);

                String name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
                String description = inputDescription.getText() != null ? inputDescription.getText().toString().trim() : "";
                String priorityStr = inputPriority.getText() != null ? inputPriority.getText().toString().trim() : "50";
                String editor = inputEditor.getText() != null ? inputEditor.getText().toString().trim() : "";

                if (TextUtils.isEmpty(name)) {
                    layoutName.setError(getString(R.string.error_category_name_required));
                    return;
                }

                int priority = 50;
                try {
                    priority = Integer.parseInt(priorityStr);
                    if (priority < 0 || priority > 100) {
                        priority = 50;
                    }
                } catch (NumberFormatException e) {
                    priority = 50;
                }

                saveCategory(categoryId, name, description, priority, switchEnabled.isChecked(), editor);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void saveCategory(@Nullable String categoryId, String name, String description, int priority,
                             boolean enabled, String editor) {
        showLoading(true);
        
        Map<String, Object> categoryData = new HashMap<>();
        categoryData.put("name", name);
        categoryData.put("description", description);
        categoryData.put("priority", priority);
        categoryData.put("enabled", enabled);
        if (!TextUtils.isEmpty(editor)) {
            categoryData.put("editorId", editor);
        }
        categoryData.put("updatedAt", System.currentTimeMillis());
        if (categoryId == null) {
            categoryData.put("createdAt", System.currentTimeMillis());
            // Create new category
            firestore.collection("categories")
                    .add(categoryData)
                    .addOnSuccessListener(documentReference -> {
                        showLoading(false);
                        Toast.makeText(this, R.string.message_category_saved, Toast.LENGTH_SHORT).show();
                        refreshContentManagement();
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(this, "Failed to save category: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            // Update existing category
            categoryData.put("updatedAt", System.currentTimeMillis());
            firestore.collection("categories")
                    .document(categoryId)
                    .set(categoryData, SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        showLoading(false);
                        Toast.makeText(this, R.string.message_category_saved, Toast.LENGTH_SHORT).show();
                        refreshContentManagement();
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(this, "Failed to update category: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void refreshContentManagement() {
        View viewContentManagement = findViewById(R.id.viewContentManagement);
        if (viewContentManagement == null) return;
        
        com.google.android.material.tabs.TabLayout tabLayout = viewContentManagement.findViewById(R.id.tabContentManagement);
        if (tabLayout != null) {
            int selectedTab = tabLayout.getSelectedTabPosition();
            androidx.recyclerview.widget.RecyclerView recyclerView = null;
            if (selectedTab == 0) {
                recyclerView = viewContentManagement.findViewById(R.id.recyclerArticles);
                if (recyclerView != null) loadArticles(recyclerView);
            } else if (selectedTab == 1) {
                recyclerView = viewContentManagement.findViewById(R.id.recyclerCategories);
                if (recyclerView != null) loadCategories(recyclerView);
            } else if (selectedTab == 2) {
                recyclerView = viewContentManagement.findViewById(R.id.recyclerBreakingNews);
                if (recyclerView != null) loadBreakingNews(recyclerView);
            }
        }
    }

    private void loadRegionManagement() {
        View viewRegionManagement = findViewById(R.id.viewRegionManagement);
        if (viewRegionManagement == null) return;

        // Initialize views
        com.google.android.material.textfield.TextInputEditText inputStateName = viewRegionManagement.findViewById(R.id.inputStateName);
        com.google.android.material.textfield.TextInputEditText inputDistrictName = viewRegionManagement.findViewById(R.id.inputDistrictName);
        com.google.android.material.textfield.TextInputEditText inputMandalName = viewRegionManagement.findViewById(R.id.inputMandalName);
        com.google.android.material.textfield.TextInputEditText inputVillageName = viewRegionManagement.findViewById(R.id.inputVillageName);
        
        Spinner spinnerStateForDistrict = viewRegionManagement.findViewById(R.id.spinnerStateForDistrict);
        Spinner spinnerStateForMandal = viewRegionManagement.findViewById(R.id.spinnerStateForMandal);
        Spinner spinnerStateForVillage = viewRegionManagement.findViewById(R.id.spinnerStateForVillage);
        Spinner spinnerDistrictForMandal = viewRegionManagement.findViewById(R.id.spinnerDistrictForMandal);
        Spinner spinnerDistrictForVillage = viewRegionManagement.findViewById(R.id.spinnerDistrictForVillage);
        Spinner spinnerMandalForVillage = viewRegionManagement.findViewById(R.id.spinnerMandalForVillage);

        // Delete spinners
        Spinner spinnerDeleteState = viewRegionManagement.findViewById(R.id.spinnerDeleteState);
        Spinner spinnerStateForDeleteDistrict = viewRegionManagement.findViewById(R.id.spinnerStateForDeleteDistrict);
        Spinner spinnerDeleteDistrict = viewRegionManagement.findViewById(R.id.spinnerDeleteDistrict);
        Spinner spinnerStateForDeleteMandal = viewRegionManagement.findViewById(R.id.spinnerStateForDeleteMandal);
        Spinner spinnerDistrictForDeleteMandal = viewRegionManagement.findViewById(R.id.spinnerDistrictForDeleteMandal);
        Spinner spinnerDeleteMandal = viewRegionManagement.findViewById(R.id.spinnerDeleteMandal);

        Button buttonAddState = viewRegionManagement.findViewById(R.id.buttonAddState);
        Button buttonAddDistrict = viewRegionManagement.findViewById(R.id.buttonAddDistrict);
        Button buttonAddMandal = viewRegionManagement.findViewById(R.id.buttonAddMandal);
        Button buttonAddVillage = viewRegionManagement.findViewById(R.id.buttonAddVillage);

        // Delete buttons
        Button buttonDeleteState = viewRegionManagement.findViewById(R.id.buttonDeleteState);
        Button buttonDeleteDistrict = viewRegionManagement.findViewById(R.id.buttonDeleteDistrict);
        Button buttonDeleteMandal = viewRegionManagement.findViewById(R.id.buttonDeleteMandal);

        // Load states for spinners
        com.manavoori.muchhatlu.utils.RegionData.loadFromFirestore(firestore, new com.manavoori.muchhatlu.utils.RegionData.Callback() {
            @Override
            public void onSuccess() {
                // Update spinners after data is loaded
                updateStateSpinners(spinnerStateForDistrict, spinnerStateForMandal, spinnerStateForVillage,
                        spinnerStateForDeleteDistrict, spinnerStateForDeleteMandal);
                // Update delete state spinner (without "All" option)
                updateStateSpinnerForDelete(spinnerDeleteState);
            }

            @Override
            public void onFailure(String error) {
                // Handle error if needed
                android.util.Log.e("AdminActivity", "Failed to load region data: " + error);
            }
        });

        // Setup state spinner listeners to update district spinners
        if (spinnerStateForDistrict != null) {
            spinnerStateForDistrict.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String selectedState = (String) parent.getItemAtPosition(position);
                    if (!com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedState)) {
                        updateDistrictSpinner(spinnerDistrictForMandal, selectedState);
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        if (spinnerStateForMandal != null) {
            spinnerStateForMandal.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String selectedState = (String) parent.getItemAtPosition(position);
                    if (!com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedState)) {
                        updateDistrictSpinner(spinnerDistrictForMandal, selectedState);
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        if (spinnerStateForVillage != null) {
            spinnerStateForVillage.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String selectedState = (String) parent.getItemAtPosition(position);
                    if (!com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedState)) {
                        updateDistrictSpinner(spinnerDistrictForVillage, selectedState);
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // Setup delete state spinner listener
        if (spinnerStateForDeleteDistrict != null) {
            spinnerStateForDeleteDistrict.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String selectedState = (String) parent.getItemAtPosition(position);
                    if (selectedState != null && !com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedState)) {
                        updateDistrictSpinnerForDelete(spinnerDeleteDistrict, selectedState);
                    } else {
                        updateDistrictSpinnerForDelete(spinnerDeleteDistrict, "");
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // Setup delete mandal state spinner listener
        if (spinnerStateForDeleteMandal != null) {
            spinnerStateForDeleteMandal.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String selectedState = (String) parent.getItemAtPosition(position);
                    if (selectedState != null && !com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedState)) {
                        updateDistrictSpinnerForDelete(spinnerDistrictForDeleteMandal, selectedState);
                    } else {
                        updateDistrictSpinnerForDelete(spinnerDistrictForDeleteMandal, "");
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // Setup delete mandal district spinner listener
        if (spinnerDistrictForDeleteMandal != null) {
            spinnerDistrictForDeleteMandal.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String selectedState = (String) spinnerStateForDeleteMandal.getSelectedItem();
                    String selectedDistrict = (String) parent.getItemAtPosition(position);
                    if (selectedState != null && selectedDistrict != null &&
                        !com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedState) &&
                        !com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedDistrict)) {
                        updateMandalSpinnerForDelete(spinnerDeleteMandal, selectedState, selectedDistrict);
                    } else {
                        updateMandalSpinnerForDelete(spinnerDeleteMandal, "", "");
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // Setup district spinner listeners to update mandal spinners
        if (spinnerDistrictForMandal != null) {
            spinnerDistrictForMandal.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String selectedState = (String) spinnerStateForMandal.getSelectedItem();
                    String selectedDistrict = (String) parent.getItemAtPosition(position);
                    if (!com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedState) &&
                        !com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedDistrict)) {
                        updateMandalSpinner(spinnerMandalForVillage, selectedState, selectedDistrict);
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        if (spinnerDistrictForVillage != null) {
            spinnerDistrictForVillage.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String selectedState = (String) spinnerStateForVillage.getSelectedItem();
                    String selectedDistrict = (String) parent.getItemAtPosition(position);
                    if (!com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedState) &&
                        !com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(selectedDistrict)) {
                        updateMandalSpinner(spinnerMandalForVillage, selectedState, selectedDistrict);
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // Setup add button listeners
        if (buttonAddState != null && inputStateName != null) {
            buttonAddState.setOnClickListener(v -> {
                String stateName = inputStateName.getText().toString().trim();
                if (TextUtils.isEmpty(stateName)) {
                    Toast.makeText(this, "Please enter a state name", Toast.LENGTH_SHORT).show();
                    return;
                }
                addState(stateName);
                inputStateName.setText("");
            });
        }

        if (buttonAddDistrict != null && inputDistrictName != null && spinnerStateForDistrict != null) {
            buttonAddDistrict.setOnClickListener(v -> {
                String stateName = (String) spinnerStateForDistrict.getSelectedItem();
                String districtName = inputDistrictName.getText().toString().trim();
                if (TextUtils.isEmpty(districtName) || com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(stateName)) {
                    Toast.makeText(this, "Please select a state and enter a district name", Toast.LENGTH_SHORT).show();
                    return;
                }
                addDistrict(stateName, districtName);
                inputDistrictName.setText("");
            });
        }

        if (buttonAddMandal != null && inputMandalName != null && spinnerStateForMandal != null && spinnerDistrictForMandal != null) {
            buttonAddMandal.setOnClickListener(v -> {
                String stateName = (String) spinnerStateForMandal.getSelectedItem();
                String districtName = (String) spinnerDistrictForMandal.getSelectedItem();
                String mandalName = inputMandalName.getText().toString().trim();
                if (TextUtils.isEmpty(mandalName) || 
                    com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(stateName) ||
                    com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(districtName)) {
                    Toast.makeText(this, "Please select state and district, and enter a mandal name", Toast.LENGTH_SHORT).show();
                    return;
                }
                addMandal(stateName, districtName, mandalName);
                inputMandalName.setText("");
            });
        }

        if (buttonAddVillage != null && inputVillageName != null && spinnerStateForVillage != null && 
            spinnerDistrictForVillage != null && spinnerMandalForVillage != null) {
            buttonAddVillage.setOnClickListener(v -> {
                String stateName = (String) spinnerStateForVillage.getSelectedItem();
                String districtName = (String) spinnerDistrictForVillage.getSelectedItem();
                String mandalName = (String) spinnerMandalForVillage.getSelectedItem();
                String villageName = inputVillageName.getText().toString().trim();
                if (TextUtils.isEmpty(villageName) || 
                    com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(stateName) ||
                    com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(districtName) ||
                    com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(mandalName)) {
                    Toast.makeText(this, "Please select state, district, and mandal, and enter a village name", Toast.LENGTH_SHORT).show();
                    return;
                }
                addVillage(stateName, districtName, mandalName, villageName);
                inputVillageName.setText("");
            });
        }

        // Setup delete button listeners
        if (buttonDeleteState != null && spinnerDeleteState != null) {
            buttonDeleteState.setOnClickListener(v -> {
                String stateName = (String) spinnerDeleteState.getSelectedItem();
                if (stateName == null || stateName.isEmpty()) {
                    Toast.makeText(this, "Please select a state to delete", Toast.LENGTH_SHORT).show();
                    return;
                }
                showDeleteConfirmation("State", stateName, () -> deleteState(stateName));
            });
        }

        if (buttonDeleteDistrict != null && spinnerStateForDeleteDistrict != null && spinnerDeleteDistrict != null) {
            buttonDeleteDistrict.setOnClickListener(v -> {
                String stateName = (String) spinnerStateForDeleteDistrict.getSelectedItem();
                String districtName = (String) spinnerDeleteDistrict.getSelectedItem();
                if (stateName == null || districtName == null ||
                    com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(stateName) ||
                    districtName.isEmpty()) {
                    Toast.makeText(this, "Please select state and district to delete", Toast.LENGTH_SHORT).show();
                    return;
                }
                showDeleteConfirmation("District", districtName + " (" + stateName + ")", () -> deleteDistrict(stateName, districtName));
            });
        }

        if (buttonDeleteMandal != null && spinnerStateForDeleteMandal != null && 
            spinnerDistrictForDeleteMandal != null && spinnerDeleteMandal != null) {
            buttonDeleteMandal.setOnClickListener(v -> {
                String stateName = (String) spinnerStateForDeleteMandal.getSelectedItem();
                String districtName = (String) spinnerDistrictForDeleteMandal.getSelectedItem();
                String mandalName = (String) spinnerDeleteMandal.getSelectedItem();
                if (stateName == null || districtName == null || mandalName == null ||
                    com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(stateName) ||
                    com.manavoori.muchhatlu.utils.RegionData.getAllLabel().equals(districtName) ||
                    mandalName.isEmpty()) {
                    Toast.makeText(this, "Please select state, district, and mandal to delete", Toast.LENGTH_SHORT).show();
                    return;
                }
                showDeleteConfirmation("Mandal", mandalName + " (" + districtName + ", " + stateName + ")", 
                    () -> deleteMandal(stateName, districtName, mandalName));
            });
        }
    }

    private void showDeleteConfirmation(String type, String name, Runnable onConfirm) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete " + type)
                .setMessage("Are you sure you want to delete \"" + name + "\"? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> onConfirm.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStateSpinners(Spinner... spinners) {
        List<String> states = com.manavoori.muchhatlu.utils.RegionData.getStates();
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, states);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for (Spinner spinner : spinners) {
            if (spinner != null) {
                spinner.setAdapter(adapter);
            }
        }
    }

    private void updateStateSpinnerForDelete(Spinner spinner) {
        if (spinner == null) return;
        List<String> states = com.manavoori.muchhatlu.utils.RegionData.getStatesForDelete();
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, states);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void updateDistrictSpinner(Spinner spinner, String state) {
        if (spinner == null) return;
        List<String> districts = com.manavoori.muchhatlu.utils.RegionData.getDistricts(state);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, districts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void updateDistrictSpinnerForDelete(Spinner spinner, String state) {
        if (spinner == null) return;
        List<String> districts = com.manavoori.muchhatlu.utils.RegionData.getDistrictsForDelete(state);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, districts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void updateMandalSpinner(Spinner spinner, String state, String district) {
        if (spinner == null) return;
        List<String> mandals = com.manavoori.muchhatlu.utils.RegionData.getMandals(state, district);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mandals);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void updateMandalSpinnerForDelete(Spinner spinner, String state, String district) {
        if (spinner == null) return;
        List<String> mandals = com.manavoori.muchhatlu.utils.RegionData.getMandalsForDelete(state, district);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mandals);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void addState(String stateName) {
        showLoading(true);
        com.manavoori.muchhatlu.utils.RegionData.addState(firestore, stateName, new com.manavoori.muchhatlu.utils.RegionData.Callback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "State added successfully", Toast.LENGTH_SHORT).show();
                loadRegionManagement(); // Refresh UI
            }

            @Override
            public void onFailure(String error) {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Failed to add state: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addDistrict(String stateName, String districtName) {
        showLoading(true);
        com.manavoori.muchhatlu.utils.RegionData.addDistrict(firestore, stateName, districtName, new com.manavoori.muchhatlu.utils.RegionData.Callback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "District added successfully", Toast.LENGTH_SHORT).show();
                loadRegionManagement(); // Refresh UI
            }

            @Override
            public void onFailure(String error) {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Failed to add district: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addMandal(String stateName, String districtName, String mandalName) {
        showLoading(true);
        com.manavoori.muchhatlu.utils.RegionData.addMandal(firestore, stateName, districtName, mandalName, new com.manavoori.muchhatlu.utils.RegionData.Callback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Mandal added successfully", Toast.LENGTH_SHORT).show();
                loadRegionManagement(); // Refresh UI
            }

            @Override
            public void onFailure(String error) {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Failed to add mandal: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addVillage(String stateName, String districtName, String mandalName, String villageName) {
        showLoading(true);
        com.manavoori.muchhatlu.utils.RegionData.addVillage(firestore, stateName, districtName, mandalName, villageName, new com.manavoori.muchhatlu.utils.RegionData.Callback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Village added successfully", Toast.LENGTH_SHORT).show();
                loadRegionManagement(); // Refresh UI
            }

            @Override
            public void onFailure(String error) {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Failed to add village: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteState(String stateName) {
        showLoading(true);
        com.manavoori.muchhatlu.utils.RegionData.deleteState(firestore, stateName, new com.manavoori.muchhatlu.utils.RegionData.Callback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "State deleted successfully", Toast.LENGTH_SHORT).show();
                loadRegionManagement(); // Refresh UI
            }

            @Override
            public void onFailure(String error) {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Failed to delete state: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteDistrict(String stateName, String districtName) {
        showLoading(true);
        com.manavoori.muchhatlu.utils.RegionData.deleteDistrict(firestore, stateName, districtName, new com.manavoori.muchhatlu.utils.RegionData.Callback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "District deleted successfully", Toast.LENGTH_SHORT).show();
                loadRegionManagement(); // Refresh UI
            }

            @Override
            public void onFailure(String error) {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Failed to delete district: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteMandal(String stateName, String districtName, String mandalName) {
        showLoading(true);
        com.manavoori.muchhatlu.utils.RegionData.deleteMandal(firestore, stateName, districtName, mandalName, new com.manavoori.muchhatlu.utils.RegionData.Callback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Mandal deleted successfully", Toast.LENGTH_SHORT).show();
                loadRegionManagement(); // Refresh UI
            }

            @Override
            public void onFailure(String error) {
                showLoading(false);
                Toast.makeText(AdminActivity.this, "Failed to delete mandal: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDateTimePicker(TextInputEditText inputField, String title) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    calendar.set(selectedYear, selectedMonth, selectedDay);
                    int hour = calendar.get(Calendar.HOUR_OF_DAY);
                    int minute = calendar.get(Calendar.MINUTE);

                    TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                            (timeView, selectedHour, selectedMinute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
                                calendar.set(Calendar.MINUTE, selectedMinute);
                                long timestamp = calendar.getTimeInMillis();
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                                inputField.setText(sdf.format(calendar.getTime()));
                                inputField.setTag(timestamp); // Store timestamp for saving
                            }, hour, minute, false);
                    timePickerDialog.setTitle(title);
                    timePickerDialog.show();
                }, year, month, day);
        datePickerDialog.setTitle(title);
        datePickerDialog.show();
    }

    private void showEditArticleDialog(Map<String, Object> article) {
        // Load full article data and show edit dialog
        String articleId = (String) article.get("id");
        firestore.collection("articles")
                .document(articleId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Post post = new Post();
                        post.setId(articleId);
                        post.setCaption(documentSnapshot.getString("title"));
                        showCreateArticleDialog(post);
                    }
                });
    }

    private void showEditCategoryDialog(Map<String, Object> category) {
        String categoryId = (String) category.get("id");
        String name = (String) category.get("name");
        showCreateCategoryDialog(categoryId, name);
    }

    private void confirmDeleteArticle(String articleId, String title) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_delete_post)
                .setMessage("Are you sure you want to delete this article: " + (title != null ? title : "Untitled") + "?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteArticle(articleId))
                .show();
    }

    private void deleteArticle(String articleId) {
        showLoading(true);
        firestore.collection("articles")
                .document(articleId)
                .delete()
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, "Article deleted successfully", Toast.LENGTH_SHORT).show();
                    refreshContentManagement();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to delete article: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateCategoryEnabled(String categoryId, boolean enabled) {
        firestore.collection("categories")
                .document(categoryId)
                .update("enabled", enabled)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Category " + (enabled ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                    refreshContentManagement();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update category: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    refreshContentManagement(); // Refresh to revert UI state
                });
    }

    private void loadPosts(Query query, AdminPostAdapter.Mode mode) {
        recyclerView.setAdapter(postAdapter);
        currentPostMode = mode;
        postAdapter.setMode(mode);
        showLoading(true);
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Post> posts = new ArrayList<>();
                    queryDocumentSnapshots.forEach(documentSnapshot -> posts.add(Post.fromSnapshot(documentSnapshot)));
                    postAdapter.submitList(posts);
                    showLoading(false);
                    togglePlaceholder(posts.isEmpty(), getString(R.string.message_admin_no_posts));
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void loadUsers() {
        recyclerView.setAdapter(userAdapter);
        showLoading(true);
        firestore.collection("users")
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allUsers.clear();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        allUsers.add(AdminUser.fromSnapshot(snapshot));
                    }
                    // Apply current search filter if any
                    String searchQuery = inputSearchUsers != null && inputSearchUsers.getText() != null
                            ? inputSearchUsers.getText().toString() : "";
                    filterUsers(searchQuery);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    togglePlaceholder(true, e.getLocalizedMessage());
                });
    }

    private void filterUsers(String query) {
        if (currentTab != TAB_USERS) {
            return;
        }

        List<AdminUser> filteredUsers = new ArrayList<>();
        
        if (TextUtils.isEmpty(query)) {
            filteredUsers.addAll(allUsers);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (AdminUser user : allUsers) {
                // Search in name
                if (user.getName() != null && user.getName().toLowerCase().contains(lowerQuery)) {
                    filteredUsers.add(user);
                    continue;
                }
                // Search in phone
                if (user.getPhone() != null && user.getPhone().toLowerCase().contains(lowerQuery)) {
                    filteredUsers.add(user);
                    continue;
                }
                // Search in location fields
                if (user.getState() != null && user.getState().toLowerCase().contains(lowerQuery)) {
                    filteredUsers.add(user);
                    continue;
                }
                if (user.getDistrict() != null && user.getDistrict().toLowerCase().contains(lowerQuery)) {
                    filteredUsers.add(user);
                    continue;
                }
                if (user.getMandal() != null && user.getMandal().toLowerCase().contains(lowerQuery)) {
                    filteredUsers.add(user);
                    continue;
                }
                if (user.getVillage() != null && user.getVillage().toLowerCase().contains(lowerQuery)) {
                    filteredUsers.add(user);
                    continue;
                }
                // Search in role
                if (user.getRole() != null && user.getRole().toLowerCase().contains(lowerQuery)) {
                    filteredUsers.add(user);
                }
            }
        }

        userAdapter.submitList(filteredUsers);
        boolean empty = filteredUsers.isEmpty();
        togglePlaceholder(empty, empty
                ? getString(R.string.message_admin_no_users)
                : getString(R.string.admin_users_header, filteredUsers.size()));
    }

    private void loadAnalytics() {
        recyclerView.setAdapter(postAdapter);
        postAdapter.submitList(new ArrayList<>());
        showLoading(true);
        firestore.collection("posts")
                .get()
                .addOnSuccessListener(postsSnapshot -> firestore.collection("users")
                        .get()
                        .addOnSuccessListener(usersSnapshot -> {
                            showLoading(false);
                            int pending = 0;
                            int flagged = 0;
                            for (DocumentSnapshot doc : postsSnapshot.getDocuments()) {
                                Boolean approved = doc.getBoolean("approved");
                                Boolean reported = doc.getBoolean("reported");
                                if (approved == null || !approved) {
                                    pending++;
                                }
                                if (reported != null && reported) {
                                    flagged++;
                                }
                            }
                            String analytics = getString(R.string.admin_analytics_format,
                                    postsSnapshot.size(),
                                    usersSnapshot.size(),
                                    pending,
                                    flagged);
                            togglePlaceholder(true, analytics);
                        }))
                .addOnFailureListener(e -> {
                    showLoading(false);
                    togglePlaceholder(true, e.getLocalizedMessage());
                });
    }

    private void loadDashboardStatistics() {
        // Remove existing listeners
        if (usersListener != null) {
            usersListener.remove();
        }
        if (postsListener != null) {
            postsListener.remove();
        }

        // Set up real-time listener for users count
        usersListener = firestore.collection("users")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        if (textTotalUsers != null) {
                            textTotalUsers.setText("0");
                        }
                        return;
                    }

                    if (querySnapshot != null && textTotalUsers != null) {
                        int totalUsers = querySnapshot.size();
                        textTotalUsers.setText(formatNumber(totalUsers));
                    }
                });

        // Set up real-time listener for posts count
        postsListener = firestore.collection("posts")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        if (textTotalPosts != null) {
                            textTotalPosts.setText("0");
                        }
                        if (textPendingPosts != null) {
                            textPendingPosts.setText("0");
                        }
                        if (textReports != null) {
                            textReports.setText("0");
                        }
                        return;
                    }

                    if (querySnapshot != null) {
                        int totalPosts = querySnapshot.size();
                        int pendingPosts = 0;
                        int reports = 0;

                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Boolean approved = doc.getBoolean("approved");
                            Boolean reported = doc.getBoolean("reported");

                            // Count pending posts (not approved)
                            if (approved == null || !approved) {
                                pendingPosts++;
                            }

                            // Count reported posts
                            if (reported != null && reported) {
                                reports++;
                            }
                        }

                        if (textTotalPosts != null) {
                            textTotalPosts.setText(formatNumber(totalPosts));
                        }
                        if (textPendingPosts != null) {
                            textPendingPosts.setText(formatNumber(pendingPosts));
                        }
                        if (textReports != null) {
                            textReports.setText(formatNumber(reports));
                        }
                    }
                });
    }

    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        } else {
            return String.valueOf(number);
        }
    }

    private void loadDashboardData() {
        View viewDashboard = findViewById(R.id.viewDashboard);
        if (viewDashboard == null) return;

        // Initialize dashboard views
        TextView textPublishedPosts = viewDashboard.findViewById(R.id.textPublishedPosts);
        TextView textPendingPosts = viewDashboard.findViewById(R.id.textPendingPosts);
        TextView textRejectedPosts = viewDashboard.findViewById(R.id.textRejectedPosts);
        TextView textTotalUsers = viewDashboard.findViewById(R.id.textTotalUsers);
        TextView textDailyActiveUsers = viewDashboard.findViewById(R.id.textDailyActiveUsers);
        TextView textWeeklyActiveUsers = viewDashboard.findViewById(R.id.textWeeklyActiveUsers);
        androidx.recyclerview.widget.RecyclerView recyclerMostViewed = viewDashboard.findViewById(R.id.recyclerMostViewed);
        androidx.recyclerview.widget.RecyclerView recyclerTrendingCategories = viewDashboard.findViewById(R.id.recyclerTrendingCategories);
        androidx.recyclerview.widget.RecyclerView recyclerTrendingTags = viewDashboard.findViewById(R.id.recyclerTrendingTags);
        androidx.recyclerview.widget.RecyclerView recyclerRecentActions = viewDashboard.findViewById(R.id.recyclerRecentActions);
        androidx.recyclerview.widget.RecyclerView recyclerNotifications = viewDashboard.findViewById(R.id.recyclerNotifications);

        // Load posts statistics
        firestore.collection("posts")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int published = 0;
                    int pending = 0;
                    int rejected = 0;

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Boolean approved = doc.getBoolean("approved");
                        if (approved != null && approved) {
                            published++;
                        } else if (approved == null || !approved) {
                            pending++;
                        }
                        // Rejected posts are those that were deleted or explicitly rejected
                        // For now, we'll count non-approved as pending
                    }

                    if (textPublishedPosts != null) {
                        textPublishedPosts.setText(formatNumber(published));
                    }
                    if (textPendingPosts != null) {
                        textPendingPosts.setText(formatNumber(pending));
                    }
                    if (textRejectedPosts != null) {
                        textRejectedPosts.setText(formatNumber(rejected));
                    }

                    // Load most viewed posts
                    loadMostViewedPosts(recyclerMostViewed, querySnapshot);
                    // Load trending categories
                    loadTrendingCategories(recyclerTrendingCategories, querySnapshot);
                    // Load trending tags (using media types as tags)
                    loadTrendingTags(recyclerTrendingTags, querySnapshot);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load posts: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });

        // Load user statistics
        long now = System.currentTimeMillis();
        long oneDayAgo = now - (24 * 60 * 60 * 1000);
        long oneWeekAgo = now - (7 * 24 * 60 * 60 * 1000);

        firestore.collection("users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int totalUsers = querySnapshot.size();
                    int dailyActive = 0;
                    int weeklyActive = 0;

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Long lastActive = doc.getLong("lastActiveAt");
                        if (lastActive != null) {
                            if (lastActive >= oneDayAgo) {
                                dailyActive++;
                            }
                            if (lastActive >= oneWeekAgo) {
                                weeklyActive++;
                            }
                        }
                    }

                    if (textTotalUsers != null) {
                        textTotalUsers.setText(formatNumber(totalUsers));
                    }
                    if (textDailyActiveUsers != null) {
                        textDailyActiveUsers.setText(formatNumber(dailyActive));
                    }
                    if (textWeeklyActiveUsers != null) {
                        textWeeklyActiveUsers.setText(formatNumber(weeklyActive));
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load users: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });

        // Load recent admin actions (placeholder - can be stored in Firestore)
        loadRecentActions(recyclerRecentActions);

        // Load notifications (placeholder - can be stored in Firestore)
        loadNotifications(recyclerNotifications);
    }

    private void loadMostViewedPosts(androidx.recyclerview.widget.RecyclerView recyclerView, com.google.firebase.firestore.QuerySnapshot querySnapshot) {
        if (recyclerView == null) return;

        List<Post> posts = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            Post post = Post.fromSnapshot(doc);
            if (post.getViewCount() > 0) {
                posts.add(post);
            }
        }

        // Sort by view count descending
        posts.sort((a, b) -> Long.compare(b.getViewCount(), a.getViewCount()));

        // Take top 5
        if (posts.size() > 5) {
            posts = posts.subList(0, 5);
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Create a simple adapter for most viewed posts
        // For now, we'll use a placeholder adapter
        if (posts.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            // TODO: Create MostViewedPostAdapter
        }
    }

    private void loadTrendingCategories(androidx.recyclerview.widget.RecyclerView recyclerView, com.google.firebase.firestore.QuerySnapshot querySnapshot) {
        if (recyclerView == null) return;

        // Count posts by state (as categories)
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            String state = doc.getString("state");
            if (state != null && !state.isEmpty()) {
                categoryCounts.put(state, categoryCounts.getOrDefault(state, 0) + 1);
            }
        }

        // Sort by count and take top 5
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(categoryCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        if (sorted.size() > 5) {
            sorted = sorted.subList(0, 5);
        }

        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        // TODO: Create TrendingCategoryAdapter
        if (sorted.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void loadTrendingTags(androidx.recyclerview.widget.RecyclerView recyclerView, com.google.firebase.firestore.QuerySnapshot querySnapshot) {
        if (recyclerView == null) return;

        // Count posts by media type (as tags)
        Map<String, Integer> tagCounts = new HashMap<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            String mediaType = doc.getString("mediaType");
            if (mediaType != null && !mediaType.isEmpty()) {
                tagCounts.put(mediaType, tagCounts.getOrDefault(mediaType, 0) + 1);
            }
        }

        // Sort by count and take top 5
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(tagCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        if (sorted.size() > 5) {
            sorted = sorted.subList(0, 5);
        }

        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        // TODO: Create TrendingTagAdapter
        if (sorted.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void loadRecentActions(androidx.recyclerview.widget.RecyclerView recyclerView) {
        if (recyclerView == null) return;
        // TODO: Load from Firestore admin_actions collection
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // TODO: Create AdminActionAdapter
        recyclerView.setVisibility(View.GONE); // Hide until implemented
    }

    private void loadNotifications(androidx.recyclerview.widget.RecyclerView recyclerView) {
        if (recyclerView == null) return;
        // TODO: Load from Firestore notifications collection
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // TODO: Create AdminNotificationAdapter
        recyclerView.setVisibility(View.GONE); // Hide until implemented
    }

    private void showLoading(boolean loading) {
        progressView.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void togglePlaceholder(boolean show, String message) {
        placeholderText.setVisibility(show ? View.VISIBLE : View.GONE);
        placeholderText.setText(message);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onViewPost(@NonNull Post post) {
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
        startActivity(intent);
    }

    @Override
    public void onEditPost(@NonNull Post post) {
        showEditPostDialog(post);
    }

    @Override
    public void onApprovePost(@NonNull Post post) {
        if (currentPostMode == AdminPostAdapter.Mode.ALL) {
            return;
        }
        firestore.collection("posts")
                .document(post.getId())
                .update("approved", true, "reported", false)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.message_post_approved, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public void onRejectPost(@NonNull Post post) {
        if (currentPostMode == AdminPostAdapter.Mode.ALL) {
            confirmDeletePost(post);
        } else {
            firestore.collection("posts")
                    .document(post.getId())
                    .delete()
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, R.string.message_post_rejected, Toast.LENGTH_SHORT).show();
                        refreshCurrentTab();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
        }
    }

    @Override
    public void onPromote(@NonNull AdminUser user) {
        confirmRoleChange(user, true);
    }

    @Override
    public void onDemote(@NonNull AdminUser user) {
        confirmRoleChange(user, false);
    }

    @Override
    public void onDelete(@NonNull AdminUser user) {
        confirmDeleteUser(user);
    }

    @Override
    public void onEdit(@NonNull AdminUser user) {
        showEditUserDialog(user);
    }

    @Override
    public void onToggleBlock(@NonNull AdminUser user) {
        confirmBlockToggle(user);
    }

    @Override
    public void onViewProfile(@NonNull AdminUser user) {
        showUserProfileDialog(user);
    }

    @Override
    public void onAssignRole(@NonNull AdminUser user) {
        showRoleSelectionDialog(user);
    }

    @Override
    public void onViewActivity(@NonNull AdminUser user) {
        showUserActivityDialog(user);
    }

    @Override
    public void onResetPassword(@NonNull AdminUser user) {
        confirmResetPassword(user);
    }

    @Override
    public void onToggleStatus(@NonNull AdminUser user) {
        if (user.isBanned()) {
            confirmUnbanUser(user);
        } else if (user.isDeactivated() || !user.isActive()) {
            confirmActivateUser(user);
        } else {
            // Show options: Deactivate or Ban
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Change Status")
                    .setItems(new String[]{
                            getString(R.string.action_deactivate),
                            getString(R.string.action_ban)
                    }, (dialog, which) -> {
                        if (which == 0) {
                            confirmDeactivateUser(user);
                        } else {
                            confirmBanUser(user);
                        }
                    })
                    .show();
        }
    }

    private void refreshCurrentTab() {
        loadDataForTab(currentTab);
    }

    private void updateFabForTab(int position) {
        // FAB removed from layout, functionality can be added via other UI elements if needed
    }

    private void handleFabAction() {
        if (currentTab == TAB_USERS) {
            showCreateUserDialog();
        } else if (currentTab <= TAB_ALL) {
            startActivity(new Intent(this, AddPostActivity.class));
        }
    }

    private void confirmDeletePost(@NonNull Post post) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_delete_post)
                .setMessage(R.string.dialog_message_delete_post)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deletePost(post))
                .show();
    }

    private void deletePost(@NonNull Post post) {
        firestore.collection("posts")
                .document(post.getId())
                .delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.message_post_deleted, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
    }

    private void confirmRoleChange(@NonNull AdminUser user, boolean promote) {
        int title = promote ? R.string.dialog_title_make_admin : R.string.dialog_title_remove_admin;
        int message = promote ? R.string.dialog_message_make_admin : R.string.dialog_message_remove_admin;
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> updateRole(user, promote))
                .show();
    }

    private void updateRole(@NonNull AdminUser user, boolean promote) {
        // Legacy method - now redirects to role selection dialog
        showRoleSelectionDialog(user);
    }

    private void confirmDeleteUser(@NonNull AdminUser user) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_delete_user)
                .setMessage(R.string.dialog_message_delete_user)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteUser(user))
                .show();
    }

    private void deleteUser(@NonNull AdminUser user) {
        showLoading(true);
        firestore.collection("users")
                .document(user.getId())
                .delete()
                .addOnSuccessListener(unused -> deletePostsForUser(user))
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void deletePostsForUser(@NonNull AdminUser user) {
        firestore.collection("posts")
                .whereEqualTo("userId", user.getId())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Task<Void>> deletions = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        deletions.add(firestore.collection("posts").document(snapshot.getId()).delete());
                    }
                    Tasks.whenAllComplete(deletions)
                            .addOnCompleteListener(task -> {
                                showLoading(false);
                                Toast.makeText(this, R.string.message_admin_user_deleted, Toast.LENGTH_SHORT).show();
                                refreshCurrentTab();
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showCreateUserDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_create_user, null);
        TextInputLayout uidLayout = view.findViewById(R.id.layoutUid);
        TextInputLayout nameLayout = view.findViewById(R.id.layoutName);
        TextInputLayout phoneLayout = view.findViewById(R.id.layoutPhone);
        TextInputEditText uidInput = view.findViewById(R.id.inputUid);
        TextInputEditText nameInput = view.findViewById(R.id.inputName);
        TextInputEditText phoneInput = view.findViewById(R.id.inputPhone);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_create_user)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                uidLayout.setError(null);
                nameLayout.setError(null);
                phoneLayout.setError(null);

                String uid = uidInput.getText() != null ? uidInput.getText().toString().trim() : "";
                String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                String phone = phoneInput.getText() != null ? phoneInput.getText().toString().trim() : "";

                if (TextUtils.isEmpty(uid)) {
                    uidLayout.setError(getString(R.string.error_admin_user_uid));
                    return;
                }
                if (TextUtils.isEmpty(name)) {
                    nameLayout.setError(getString(R.string.error_admin_user_name));
                    return;
                }
                if (TextUtils.isEmpty(phone)) {
                    phoneLayout.setError(getString(R.string.error_admin_user_phone));
                    return;
                }

                createUser(uid, name, phone);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void createUser(@NonNull String uid, @NonNull String name, @NonNull String phone) {
        showLoading(true);
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("phone", phone);
        userData.put("role", "Member");
        userData.put("state", null);
        userData.put("district", null);
        userData.put("mandal", null);
        userData.put("village", null);
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("createdByAdmin", true);
        userData.put("blocked", false);

        firestore.collection("users")
                .document(uid)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_admin_user_created, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showEditPostDialog(@NonNull Post post) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_edit_post, null);
        TextInputLayout captionLayout = view.findViewById(R.id.layoutCaption);
        TextInputLayout stateLayout = view.findViewById(R.id.layoutState);
        TextInputLayout districtLayout = view.findViewById(R.id.layoutDistrict);
        TextInputLayout mandalLayout = view.findViewById(R.id.layoutMandal);
        TextInputLayout villageLayout = view.findViewById(R.id.layoutVillage);

        TextInputEditText captionInput = view.findViewById(R.id.inputCaption);
        TextInputEditText stateInput = view.findViewById(R.id.inputState);
        TextInputEditText districtInput = view.findViewById(R.id.inputDistrict);
        TextInputEditText mandalInput = view.findViewById(R.id.inputMandal);
        TextInputEditText villageInput = view.findViewById(R.id.inputVillage);

        captionInput.setText(post.getCaption());
        if (post.getState() != null) {
            stateInput.setText(post.getState());
        }
        if (post.getDistrict() != null) {
            districtInput.setText(post.getDistrict());
        }
        if (post.getMandal() != null) {
            mandalInput.setText(post.getMandal());
        }
        if (post.getVillage() != null) {
            villageInput.setText(post.getVillage());
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_edit_post)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                captionLayout.setError(null);

                String caption = textValue(captionInput);
                String state = textValue(stateInput);
                String district = textValue(districtInput);
                String mandal = textValue(mandalInput);
                String village = textValue(villageInput);

                if (TextUtils.isEmpty(caption)) {
                    captionLayout.setError(getString(R.string.error_caption_required));
                    return;
                }

                updatePost(post.getId(), caption, state, district, mandal, village);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void updatePost(@NonNull String postId,
                            @NonNull String caption,
                            @Nullable String state,
                            @Nullable String district,
                            @Nullable String mandal,
                            @Nullable String village) {
        showLoading(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("caption", caption);
        updates.put("state", normalizeNullable(state));
        updates.put("district", normalizeNullable(district));
        updates.put("mandal", normalizeNullable(mandal));
        updates.put("village", normalizeNullable(village));
        updates.put("updatedAt", System.currentTimeMillis());

        firestore.collection("posts")
                .document(postId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_admin_post_updated, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showEditUserDialog(@NonNull AdminUser user) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_edit_user, null);
        TextInputLayout nameLayout = view.findViewById(R.id.layoutName);
        TextInputLayout phoneLayout = view.findViewById(R.id.layoutPhone);
        TextInputLayout stateLayout = view.findViewById(R.id.layoutState);
        TextInputLayout districtLayout = view.findViewById(R.id.layoutDistrict);
        TextInputLayout mandalLayout = view.findViewById(R.id.layoutMandal);
        TextInputLayout villageLayout = view.findViewById(R.id.layoutVillage);

        TextInputEditText nameInput = view.findViewById(R.id.inputName);
        TextInputEditText phoneInput = view.findViewById(R.id.inputPhone);
        TextInputEditText stateInput = view.findViewById(R.id.inputState);
        TextInputEditText districtInput = view.findViewById(R.id.inputDistrict);
        TextInputEditText mandalInput = view.findViewById(R.id.inputMandal);
        TextInputEditText villageInput = view.findViewById(R.id.inputVillage);

        nameInput.setText(user.getName());
        phoneInput.setText(user.getPhone());
        if (user.getState() != null) {
            stateInput.setText(user.getState());
        }
        if (user.getDistrict() != null) {
            districtInput.setText(user.getDistrict());
        }
        if (user.getMandal() != null) {
            mandalInput.setText(user.getMandal());
        }
        if (user.getVillage() != null) {
            villageInput.setText(user.getVillage());
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_edit_user)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                nameLayout.setError(null);
                phoneLayout.setError(null);

                String name = textValue(nameInput);
                String phone = textValue(phoneInput);
                String state = textValue(stateInput);
                String district = textValue(districtInput);
                String mandal = textValue(mandalInput);
                String village = textValue(villageInput);

                if (TextUtils.isEmpty(name)) {
                    nameLayout.setError(getString(R.string.error_admin_user_name));
                    return;
                }
                if (TextUtils.isEmpty(phone)) {
                    phoneLayout.setError(getString(R.string.error_admin_user_phone));
                    return;
                }

                updateUserProfile(user.getId(), name, phone, state, district, mandal, village);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void updateUserProfile(@NonNull String userId,
                                   @NonNull String name,
                                   @NonNull String phone,
                                   @Nullable String state,
                                   @Nullable String district,
                                   @Nullable String mandal,
                                   @Nullable String village) {
        showLoading(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("state", normalizeNullable(state));
        updates.put("district", normalizeNullable(district));
        updates.put("mandal", normalizeNullable(mandal));
        updates.put("village", normalizeNullable(village));

        firestore.collection("users")
                .document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_admin_user_updated, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void confirmBlockToggle(@NonNull AdminUser user) {
        boolean currentlyBlocked = user.isBlocked();
        int title = currentlyBlocked ? R.string.dialog_title_unblock_user : R.string.dialog_title_block_user;
        int message = currentlyBlocked ? R.string.dialog_message_unblock_user : R.string.dialog_message_block_user;
        int actionText = currentlyBlocked ? R.string.action_unblock_user : R.string.action_block_user;

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(actionText, (dialog, which) -> updateBlockStatus(user, !currentlyBlocked))
                .show();
    }

    private void updateBlockStatus(@NonNull AdminUser user, boolean blocked) {
        showLoading(true);
        firestore.collection("users")
                .document(user.getId())
                .update("blocked", blocked)
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this,
                            blocked ? R.string.message_admin_user_blocked : R.string.message_admin_user_unblocked,
                            Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String textValue(@Nullable TextInputEditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
    }

    @Nullable
    private Object normalizeNullable(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // New User Management Methods

    private void showRoleSelectionDialog(@NonNull AdminUser user) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_select_role, null);
        Spinner roleSpinner = view.findViewById(R.id.spinnerRole);

        String[] roles = {
                getString(R.string.role_super_admin),
                getString(R.string.role_admin),
                getString(R.string.role_editor),
                getString(R.string.role_reporter),
                getString(R.string.role_moderator),
                getString(R.string.role_reader)
        };

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roleSpinner.setAdapter(adapter);

        // Set current role as selected
        String currentRole = user.getRole();
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equals(currentRole)) {
                roleSpinner.setSelection(i);
                break;
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_assign_role)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            okButton.setOnClickListener(v -> {
                String selectedRole = roles[roleSpinner.getSelectedItemPosition()];
                updateUserRole(user, selectedRole);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void updateUserRole(@NonNull AdminUser user, @NonNull String newRole) {
        showLoading(true);
        firestore.collection("users")
                .document(user.getId())
                .update("role", newRole)
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_role_updated, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showUserProfileDialog(@NonNull AdminUser user) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_user_profile, null);
        TextView avatarInitial = view.findViewById(R.id.textAvatarInitial);
        TextView nameText = view.findViewById(R.id.textName);
        TextView roleText = view.findViewById(R.id.textRole);
        TextView phoneText = view.findViewById(R.id.textPhone);
        TextView emailText = view.findViewById(R.id.textEmail);
        TextView locationText = view.findViewById(R.id.textLocation);
        TextView statusText = view.findViewById(R.id.textStatus);
        TextView joinedText = view.findViewById(R.id.textJoined);
        TextView postsCountText = view.findViewById(R.id.textPostsCount);
        TextView commentsCountText = view.findViewById(R.id.textCommentsCount);
        TextView reportsCountText = view.findViewById(R.id.textReportsCount);
        TextView reportedCountText = view.findViewById(R.id.textReportedCount);

        String name = user.getName();
        if (name != null && !name.isEmpty()) {
            avatarInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
        }
        nameText.setText(name);
        roleText.setText(getString(R.string.label_user_role, user.getRole()));
        phoneText.setText(getString(R.string.label_user_phone, user.getPhone()));
        emailText.setText(getString(R.string.label_user_email, user.getEmail().isEmpty() ? "-" : user.getEmail()));
        locationText.setText(user.getFormattedLocation(this));
        statusText.setText(getString(R.string.label_user_status, user.getStatusLabel(this)));

        // Load user document for additional info
        firestore.collection("users")
                .document(user.getId())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Long createdAt = snapshot.getLong("createdAt");
                        if (createdAt != null) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
                            joinedText.setText(getString(R.string.label_user_joined, sdf.format(new java.util.Date(createdAt))));
                        }
                    }
                });

        // Load statistics
        loadUserStatistics(user.getId(), postsCountText, commentsCountText, reportsCountText, reportedCountText);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_user_profile)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void loadUserStatistics(@NonNull String userId, TextView postsCount, TextView commentsCount,
                                   TextView reportsCount, TextView reportedCount) {
        // Posts count
        firestore.collection("posts")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    postsCount.setText(getString(R.string.label_user_posts_count, snapshots.size()));
                });

        // Comments count
        firestore.collectionGroup("comments")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    commentsCount.setText(getString(R.string.label_user_comments_count, snapshots.size()));
                });

        // Reports count (posts reported by this user)
        firestore.collection("posts")
                .whereEqualTo("reportedBy", userId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    reportsCount.setText(getString(R.string.label_user_reports_count, snapshots.size()));
                });

        // Reported count (posts reported by others)
        firestore.collection("posts")
                .whereEqualTo("userId", userId)
                .whereEqualTo("reported", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    reportedCount.setText(getString(R.string.label_user_reported_count, snapshots.size()));
                });
    }

    private void showUserActivityDialog(@NonNull AdminUser user) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_user_activity, null);
        com.google.android.material.tabs.TabLayout tabLayout = view.findViewById(R.id.tabLayout);
        androidx.recyclerview.widget.RecyclerView recyclerView = view.findViewById(R.id.recyclerActivity);
        TextView noActivityText = view.findViewById(R.id.textNoActivity);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Simple adapter for comments and reports
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>());
        // Note: In a real implementation, you'd create proper adapters for comments and reports

        tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    loadUserComments(user.getId(), recyclerView, noActivityText);
                } else {
                    loadUserReports(user.getId(), recyclerView, noActivityText);
                }
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });

        // Load initial tab
        loadUserComments(user.getId(), recyclerView, noActivityText);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_user_activity)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void loadUserComments(@NonNull String userId, androidx.recyclerview.widget.RecyclerView recyclerView,
                                 TextView noActivityText) {
        firestore.collectionGroup("comments")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        noActivityText.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        noActivityText.setVisibility(View.GONE);
                        // In a real implementation, use a proper adapter
                        // For now, just show count
                    }
                });
    }

    private void loadUserReports(@NonNull String userId, androidx.recyclerview.widget.RecyclerView recyclerView,
                                TextView noActivityText) {
        firestore.collection("posts")
                .whereEqualTo("reportedBy", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        noActivityText.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        noActivityText.setVisibility(View.GONE);
                        // In a real implementation, use a proper adapter
                    }
                });
    }

    private void confirmResetPassword(@NonNull AdminUser user) {
        if (user.getEmail().isEmpty()) {
            Toast.makeText(this, R.string.error_no_email_for_reset, Toast.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_reset_password)
                .setMessage(R.string.dialog_message_reset_password)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> resetPassword(user))
                .show();
    }

    private void resetPassword(@NonNull AdminUser user) {
        String email = user.getEmail();
        if (email.isEmpty()) {
            Toast.makeText(this, R.string.error_no_email_for_reset, Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_password_reset_sent, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.error_reset_password_failed + ": " + e.getLocalizedMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void confirmActivateUser(@NonNull AdminUser user) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_activate_user)
                .setMessage(R.string.dialog_message_activate_user)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_activate, (dialog, which) -> activateUser(user))
                .show();
    }

    private void activateUser(@NonNull AdminUser user) {
        showLoading(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("active", true);
        updates.put("deactivated", false);
        updates.put("banned", false);
        updates.put("blocked", false);

        firestore.collection("users")
                .document(user.getId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_user_activated, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void confirmDeactivateUser(@NonNull AdminUser user) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_deactivate_user)
                .setMessage(R.string.dialog_message_deactivate_user)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_deactivate, (dialog, which) -> deactivateUser(user))
                .show();
    }

    private void deactivateUser(@NonNull AdminUser user) {
        showLoading(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("active", false);
        updates.put("deactivated", true);

        firestore.collection("users")
                .document(user.getId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_user_deactivated, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void confirmUnbanUser(@NonNull AdminUser user) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_unban_user)
                .setMessage(R.string.dialog_message_unban_user)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_unban, (dialog, which) -> unbanUser(user))
                .show();
    }

    private void unbanUser(@NonNull AdminUser user) {
        showLoading(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("banned", false);
        updates.put("active", true);
        updates.put("deactivated", false);

        firestore.collection("users")
                .document(user.getId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_user_unbanned, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void confirmBanUser(@NonNull AdminUser user) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_ban_user)
                .setMessage(R.string.dialog_message_ban_user)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_ban, (dialog, which) -> banUser(user))
                .show();
    }

    private void banUser(@NonNull AdminUser user) {
        showLoading(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("banned", true);
        updates.put("active", false);
        updates.put("deactivated", false);
        updates.put("blocked", true);

        firestore.collection("users")
                .document(user.getId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.message_user_banned, Toast.LENGTH_SHORT).show();
                    refreshCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }
}

