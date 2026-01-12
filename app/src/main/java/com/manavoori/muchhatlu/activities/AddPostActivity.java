package com.manavoori.muchhatlu.activities;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.utils.RegionData;
import com.bumptech.glide.Glide;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AddPostActivity extends AppCompatActivity {

    private static final long MAX_IMAGE_BYTES = 15 * 1024 * 1024; // 15 MB
    private static final long MAX_AUDIO_BYTES = 1024 * 1024;
    private static final long MAX_VIDEO_DURATION_MS = 90_000; // 90 seconds

    private FrameLayout frameTypeText;
    private FrameLayout frameTypeVideo;
    private FrameLayout frameTypeImage;
    private FrameLayout framePreview;
    private ImageView imagePreview;
    private VideoView videoPreview;
    private ImageView iconPlaceholder;
    private TextView selectedFileText;
    private Button chooseFileButton;
    private EditText captionInput;
    private TextView regionSummary;
    private Button selectRegionButton;
    private Button submitButton;
    private ProgressBar progressBar;

    private Uri selectedUri;
    private String selectedType = "video";
    private String userRole = "User";
    private String selectedState = RegionData.getAllLabel();
    private String selectedDistrict = RegionData.getAllLabel();
    private String selectedMandal = RegionData.getAllLabel();
    private String selectedVillage = RegionData.getAllLabel();

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;

    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_post);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        initViews();
        loadUserRole();
        
        // Check if content type was passed from HomeActivity
        String contentType = getIntent().getStringExtra("contentType");
        if (contentType != null) {
            selectedType = contentType;
        }
        
        bindTabSelection();
        bindActions();
    }

    private void initViews() {
        frameTypeText = findViewById(R.id.frameTypeText);
        frameTypeVideo = findViewById(R.id.frameTypeVideo);
        frameTypeImage = findViewById(R.id.frameTypeImage);
        framePreview = findViewById(R.id.framePreview);
        imagePreview = findViewById(R.id.imagePreview);
        videoPreview = findViewById(R.id.videoPreview);
        iconPlaceholder = findViewById(R.id.iconPlaceholder);
        selectedFileText = findViewById(R.id.textSelectedFile);
        chooseFileButton = findViewById(R.id.buttonChooseFile);
        captionInput = findViewById(R.id.editCaption);
        regionSummary = findViewById(R.id.textRegionSummary);
        selectRegionButton = findViewById(R.id.buttonSelectRegion);
        submitButton = findViewById(R.id.buttonSubmit);
        progressBar = findViewById(R.id.progress);
        updateRegionSummary();

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedUri = uri;
                selectedFileText.setText(getDisplayName(uri));
                displayMediaPreview(uri);
            }
        });
    }

    private void loadUserRole() {
        if (auth.getCurrentUser() == null) {
            return;
        }
        firestore.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        userRole = snapshot.getString("role") != null ? snapshot.getString("role") : "User";
                        selectedState = snapshot.getString("state") != null ? snapshot.getString("state") : RegionData.getAllLabel();
                        selectedDistrict = snapshot.getString("district") != null ? snapshot.getString("district") : RegionData.getAllLabel();
                        selectedMandal = snapshot.getString("mandal") != null ? snapshot.getString("mandal") : RegionData.getAllLabel();
                        selectedVillage = snapshot.getString("village") != null ? snapshot.getString("village") : RegionData.getAllLabel();
                        updateRegionSummary();
                    }
                });
    }

    private void bindTabSelection() {
        frameTypeText.setOnClickListener(v -> {
            selectedType = "text";
            selectedUri = null;
            selectedFileText.setText(R.string.label_no_file_selected);
            updateUiForType();
        });

        frameTypeVideo.setOnClickListener(v -> {
            selectedType = "video";
            updateUiForType();
        });

        frameTypeImage.setOnClickListener(v -> {
            selectedType = "image";
            updateUiForType();
        });

        // Set initial type (use passed contentType or default to video)
        if (selectedType == null || selectedType.isEmpty()) {
            selectedType = "video";
        }
        updateUiForType();
    }

    private void updatePreviewClickability() {
        // Make preview area clickable for file selection (only for non-text types)
        framePreview.setOnClickListener(v -> {
            if (!"text".equals(selectedType)) {
                String mime = selectedType + "/*";
                filePickerLauncher.launch(mime);
            }
        });
    }

    private void bindActions() {
        chooseFileButton.setOnClickListener(v -> {
            if ("text".equals(selectedType)) {
                return;
            }
            String mime = selectedType + "/*";
            filePickerLauncher.launch(mime);
        });

        selectRegionButton.setOnClickListener(v -> showRegionPicker());

        submitButton.setOnClickListener(v -> {
            if (auth.getCurrentUser() == null) {
                Toast.makeText(this, R.string.error_login_required, Toast.LENGTH_SHORT).show();
                return;
            }
            submitPost();
        });
    }

    private void submitPost() {
        String caption = captionInput.getText().toString().trim();
        if (TextUtils.isEmpty(caption)) {
            captionInput.setError(getString(R.string.error_caption_required));
            // Show prominent validation message
            Toast.makeText(this, "Please type caption", Toast.LENGTH_LONG).show();
            captionInput.requestFocus();
            return;
        }

        if (!"text".equals(selectedType) && selectedUri == null) {
            Toast.makeText(this, R.string.error_select_file, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!validateFile()) {
            return;
        }

        if (!validateRegionSelection()) {
            return;
        }

        toggleLoading(true);
        if ("text".equals(selectedType)) {
            savePost(null, caption);
        } else {
            uploadMediaAndSave(caption);
        }
    }

    private void uploadMediaAndSave(String caption) {
        StorageReference reference = storage.getReference()
                .child("posts")
                .child(auth.getCurrentUser().getUid())
                .child(UUID.randomUUID().toString());

        reference.putFile(selectedUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return reference.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> savePost(uri.toString(), caption))
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void savePost(@Nullable String mediaUrl, String caption) {
        // Check if user is registered (has a document in Firestore users collection)
        // Registered users get auto-approved, non-registered need admin approval
        firestore.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    boolean isRegistered = snapshot != null && snapshot.exists();
                    createPostDocument(mediaUrl, caption, isRegistered);
                })
                .addOnFailureListener(e -> {
                    // If check fails, assume non-registered for safety
                    createPostDocument(mediaUrl, caption, false);
                });
    }
    
    private void createPostDocument(@Nullable String mediaUrl, String caption, boolean isApproved) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", auth.getCurrentUser().getUid());
        data.put("caption", caption);
        data.put("mediaUrl", mediaUrl);
        data.put("mediaType", selectedType);
        data.put("state", normalizeRegion(selectedState));
        data.put("district", normalizeRegion(selectedDistrict));
        data.put("mandal", normalizeRegion(selectedMandal));
        data.put("village", normalizeRegion(selectedVillage));
        data.put("createdAt", System.currentTimeMillis());
        data.put("likeCount", 0);
        data.put("commentCount", 0);
        data.put("viewCount", 0);
        data.put("shareCount", 0);
        data.put("reported", false);
        // Registered users get auto-approved, non-registered need admin approval
        data.put("approved", isApproved);

        firestore.collection("posts")
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    toggleLoading(false);
                    Toast.makeText(this, R.string.message_post_submitted, Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, HomeActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                })
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void toggleLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!loading);
        chooseFileButton.setEnabled(!loading);
        selectRegionButton.setEnabled(!loading);
    }

    private void updateUiForType() {
        boolean textType = "text".equals(selectedType);
        boolean imageType = "image".equals(selectedType);
        boolean videoType = "video".equals(selectedType);

        // Show/hide caption input for text posts
        if (textType) {
            captionInput.setVisibility(View.VISIBLE);
            imagePreview.setVisibility(View.GONE);
            videoPreview.setVisibility(View.GONE);
            iconPlaceholder.setVisibility(View.GONE);
            // Request focus for text input
            captionInput.requestFocus();
        } else {
            captionInput.setVisibility(View.GONE);
            iconPlaceholder.setVisibility(View.VISIBLE);
        }

        // Update preview based on selected media
        if (selectedUri != null) {
            displayMediaPreview(selectedUri);
        } else {
            // Show placeholder if no media selected
            if (!textType) {
                imagePreview.setVisibility(View.GONE);
                videoPreview.setVisibility(View.GONE);
                iconPlaceholder.setVisibility(View.VISIBLE);
            }
        }

        // Update preview area clickability
        updatePreviewClickability();

        chooseFileButton.setEnabled(!textType);
        chooseFileButton.setAlpha(textType ? 0.5f : 1f);
        selectedFileText.setText(textType ? getString(R.string.label_no_file_required) : getString(R.string.label_no_file_selected));
    }

    private void displayMediaPreview(Uri uri) {
        if (uri == null) {
            return;
        }

        boolean imageType = "image".equals(selectedType);
        boolean videoType = "video".equals(selectedType);

        if (imageType) {
            imagePreview.setVisibility(View.VISIBLE);
            videoPreview.setVisibility(View.GONE);
            iconPlaceholder.setVisibility(View.GONE);
            Glide.with(this)
                    .load(uri)
                    .fitCenter()
                    .into(imagePreview);
        } else if (videoType) {
            imagePreview.setVisibility(View.GONE);
            videoPreview.setVisibility(View.VISIBLE);
            iconPlaceholder.setVisibility(View.GONE);
            videoPreview.setVideoURI(uri);
            videoPreview.seekTo(1); // Show first frame
            videoPreview.pause(); // Ensure video doesn't auto-play
        } else {
            // For other types (audio), show placeholder
            imagePreview.setVisibility(View.GONE);
            videoPreview.setVisibility(View.GONE);
            iconPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    private boolean validateFile() {
        if ("text".equals(selectedType) || selectedUri == null) {
            return true;
        }
        try {
            ContentResolver resolver = getContentResolver();
            switch (selectedType) {
                case "image":
                    if (getFileSize(resolver, selectedUri) > MAX_IMAGE_BYTES) {
                        Toast.makeText(this, R.string.error_image_too_large, Toast.LENGTH_LONG).show();
                        return false;
                    }
                    break;
                case "audio":
                    if (getFileSize(resolver, selectedUri) > MAX_AUDIO_BYTES) {
                        Toast.makeText(this, R.string.error_audio_too_large, Toast.LENGTH_LONG).show();
                        return false;
                    }
                    break;
                case "video":
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(this, selectedUri);
                    String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    retriever.release();
                    long durationMs = duration != null ? Long.parseLong(duration) : 0;
                    if (durationMs > MAX_VIDEO_DURATION_MS) {
                        Toast.makeText(this, R.string.error_video_too_long, Toast.LENGTH_LONG).show();
                        return false;
                    }
                    break;
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private boolean validateRegionSelection() {
        String allLabel = RegionData.getAllLabel();
        if (allLabel.equalsIgnoreCase(selectedState)) {
            Toast.makeText(this, R.string.error_select_state, Toast.LENGTH_LONG).show();
            return false;
        }
        if (allLabel.equalsIgnoreCase(selectedDistrict)) {
            Toast.makeText(this, R.string.error_select_district, Toast.LENGTH_LONG).show();
            return false;
        }
        if (allLabel.equalsIgnoreCase(selectedMandal)) {
            Toast.makeText(this, R.string.error_select_mandal, Toast.LENGTH_LONG).show();
            return false;
        }
        if (allLabel.equalsIgnoreCase(selectedVillage)) {
            Toast.makeText(this, R.string.error_select_village, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private long getFileSize(ContentResolver resolver, Uri uri) throws Exception {
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) {
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            cursor.moveToFirst();
            long size = cursor.getLong(sizeIndex);
            cursor.close();
            return size;
        }
        InputStream stream = resolver.openInputStream(uri);
        if (stream == null) {
            return 0;
        }
        int available = stream.available();
        stream.close();
        return available;
    }

    private String getDisplayName(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            cursor.moveToFirst();
            String name = cursor.getString(nameIndex);
            cursor.close();
            return name;
        }
        return uri.getLastPathSegment();
    }

    private void showRegionPicker() {
        showStateDialog();
    }

    private void showStateDialog() {
        showOptionsDialog(getString(R.string.title_select_state), RegionData.getStates(), value -> {
            selectedState = value;
            selectedDistrict = RegionData.getAllLabel();
            selectedMandal = RegionData.getAllLabel();
            selectedVillage = RegionData.getAllLabel();
            if (RegionData.getAllLabel().equals(value)) {
                updateRegionSummary();
            } else {
                showDistrictDialog();
            }
        });
    }

    private void showDistrictDialog() {
        showOptionsDialog(getString(R.string.title_select_district), RegionData.getDistricts(selectedState), value -> {
            selectedDistrict = value;
            selectedMandal = RegionData.getAllLabel();
            selectedVillage = RegionData.getAllLabel();
            if (RegionData.getAllLabel().equals(value)) {
                updateRegionSummary();
            } else {
                showMandalDialog();
            }
        });
    }

    private void showMandalDialog() {
        showOptionsDialog(getString(R.string.title_select_mandal), RegionData.getMandals(selectedState, selectedDistrict), value -> {
            selectedMandal = value;
            selectedVillage = RegionData.getAllLabel();
            if (RegionData.getAllLabel().equals(value)) {
                updateRegionSummary();
            } else {
                showVillageDialog();
            }
        });
    }

    private void showVillageDialog() {
        showOptionsDialog(getString(R.string.title_select_village), RegionData.getVillages(selectedState, selectedDistrict, selectedMandal), value -> {
            selectedVillage = value;
            updateRegionSummary();
        });
    }

    private void showOptionsDialog(String title, List<String> options, OnRegionSelected callback) {
        String[] items = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> callback.onSelected(items[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateRegionSummary() {
        if (regionSummary == null) {
            return;
        }
        if (RegionData.getAllLabel().equals(selectedState)) {
            regionSummary.setText(R.string.label_region_default);
        } else {
            regionSummary.setText(getString(R.string.label_region_format, selectedState, selectedDistrict, selectedMandal, selectedVillage));
        }
    }

    private String normalizeRegion(String value) {
        if (value == null || RegionData.getAllLabel().equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private interface OnRegionSelected {
        void onSelected(String value);
    }
}

