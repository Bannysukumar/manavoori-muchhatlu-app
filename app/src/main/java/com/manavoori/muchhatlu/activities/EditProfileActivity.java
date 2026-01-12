package com.manavoori.muchhatlu.activities;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.manavoori.muchhatlu.R;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView profileImage;
    private EditText nameInput;
    private EditText stateInput;
    private EditText districtInput;
    private EditText mandalInput;
    private EditText villageInput;
    private ProgressBar progressBar;

    private Uri selectedImageUri;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseStorage storage = FirebaseStorage.getInstance();

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        ImageButton buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        profileImage = findViewById(R.id.imageProfile);
        nameInput = findViewById(R.id.editName);
        stateInput = findViewById(R.id.editState);
        districtInput = findViewById(R.id.editDistrict);
        mandalInput = findViewById(R.id.editMandal);
        villageInput = findViewById(R.id.editVillage);
        progressBar = findViewById(R.id.progress);
        Button changePhotoButton = findViewById(R.id.buttonChangePhoto);
        Button saveButton = findViewById(R.id.buttonSave);

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                profileImage.setImageURI(uri);
            }
        });

        changePhotoButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        saveButton.setOnClickListener(v -> updateProfile());

        loadProfile();
    }

    private void loadProfile() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.error_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        firestore.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    nameInput.setText(snapshot.getString("name"));
                    stateInput.setText(snapshot.getString("state"));
                    districtInput.setText(snapshot.getString("district"));
                    mandalInput.setText(snapshot.getString("mandal"));
                    villageInput.setText(snapshot.getString("village"));
                    String photo = snapshot.getString("photoUrl");
                    if (photo != null) {
                        Glide.with(this)
                                .load(photo)
                                .placeholder(R.drawable.ic_person_placeholder)
                                .into(profileImage);
                    }
                });
    }

    private void updateProfile() {
        if (auth.getCurrentUser() == null) {
            return;
        }
        String name = nameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            nameInput.setError(getString(R.string.error_enter_name));
            return;
        }
        toggleLoading(true);
        if (selectedImageUri != null) {
            uploadPhotoAndSave(name);
        } else {
            persistProfile(name, null);
        }
    }

    private void uploadPhotoAndSave(String name) {
        StorageReference reference = storage.getReference()
                .child("profiles")
                .child(auth.getCurrentUser().getUid())
                .child(UUID.randomUUID().toString() + ".jpg");
        reference.putFile(selectedImageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return reference.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> persistProfile(name, uri.toString()))
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void persistProfile(String name, @Nullable String photoUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("state", stateInput.getText().toString().trim());
        data.put("district", districtInput.getText().toString().trim());
        data.put("mandal", mandalInput.getText().toString().trim());
        data.put("village", villageInput.getText().toString().trim());
        if (photoUrl != null) {
            data.put("photoUrl", photoUrl);
        }
        firestore.collection("users")
                .document(auth.getCurrentUser().getUid())
                .update(data)
                .addOnSuccessListener(unused -> {
                    toggleLoading(false);
                    Toast.makeText(this, R.string.message_profile_updated, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void toggleLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}

