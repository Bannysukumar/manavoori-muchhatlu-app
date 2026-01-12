package com.manavoori.muchhatlu.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.manavoori.muchhatlu.R;
import com.manavoori.muchhatlu.utils.RegionData;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import android.content.pm.PackageManager;
import android.util.Patterns;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    public static final String EXTRA_PHONE = "extra_phone";

    private EditText nameInput;
    private EditText emailInput;
    private EditText phoneInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private TextInputLayout inputLayoutPassword;
    private TextInputLayout inputLayoutConfirmPassword;
    private TextInputEditText otpInput;
    private TextInputLayout inputLayoutOtp;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private Spinner stateSpinner;
    private Spinner districtSpinner;
    private Spinner mandalSpinner;
    private Spinner villageSpinner;
    private ImageView profileImage;
    private Button uploadButton;
    private Button saveButton;
    private Button sendOtpButton;
    private Button detectLocationButton;
    private ProgressBar progressBar;

    private Uri selectedImageUri;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseStorage firebaseStorage;
    private LocationManager locationManager;
    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    private SharedPreferences preferences;

    private static final String KEY_AUTO_FILL_LOCATION = "auto_fill_location";
    
    // Phone Auth
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);

        initViews();
        setupSpinners();
        setupPasswordToggles();
        setupPasswordWatchers();
        bindActions();
        initLocationPermissions();

        if (preferences.getBoolean(KEY_AUTO_FILL_LOCATION, false)) {
            attemptAutoFillLocation();
        }
    }

    private void initViews() {
        nameInput = findViewById(R.id.editName);
        emailInput = findViewById(R.id.editEmail);
        phoneInput = findViewById(R.id.editPhone);
        passwordInput = findViewById(R.id.editPassword);
        confirmPasswordInput = findViewById(R.id.editConfirmPassword);
        inputLayoutPassword = findViewById(R.id.inputLayoutPassword);
        inputLayoutConfirmPassword = findViewById(R.id.inputLayoutConfirmPassword);
        otpInput = findViewById(R.id.editOtp);
        inputLayoutOtp = findViewById(R.id.inputLayoutOtp);
        stateSpinner = findViewById(R.id.spinnerState);
        districtSpinner = findViewById(R.id.spinnerDistrict);
        mandalSpinner = findViewById(R.id.spinnerMandal);
        villageSpinner = findViewById(R.id.spinnerVillage);
        profileImage = findViewById(R.id.imageProfile);
        uploadButton = findViewById(R.id.buttonUploadImage);
        saveButton = findViewById(R.id.buttonSave);
        sendOtpButton = findViewById(R.id.buttonSendOtp);
        detectLocationButton = findViewById(R.id.buttonDetectLocation);
        progressBar = findViewById(R.id.progress);

        String phoneFromIntent = getIntent().getStringExtra(EXTRA_PHONE);
        if (TextUtils.isEmpty(phoneFromIntent)) {
            // Set default country code to India (+91)
            phoneInput.setText("+91");
            // Move cursor to end to allow user to type phone number
            phoneInput.setSelection(phoneInput.getText().length());
        } else {
            phoneInput.setText(phoneFromIntent);
        }

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                profileImage.setImageURI(uri);
            }
        });
    }

    private void initLocationPermissions() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> attemptAutoFillLocation());
    }

    private void attemptAutoFillLocation() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            locationPermissionLauncher.launch(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION });
            return;
        }
        Location location = null;
        if (fineGranted) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (location == null && coarseGranted) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        handleLocation(location);
    }

    private void handleLocation(@Nullable Location location) {
        if (location == null) {
            Toast.makeText(this, R.string.message_location_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses == null || addresses.isEmpty()) {
                Toast.makeText(this, R.string.message_location_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            Address address = addresses.get(0);
            String state = address.getAdminArea();
            String district = address.getSubAdminArea();
            String mandal = address.getLocality();
            String village = address.getSubLocality();
            applyDetectedLocation(state, district, mandal, village);
            preferences.edit().putBoolean(KEY_AUTO_FILL_LOCATION, true).apply();
            Toast.makeText(this, R.string.label_region_default, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.message_location_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void applyDetectedLocation(@Nullable String state,
                                       @Nullable String district,
                                       @Nullable String mandal,
                                       @Nullable String village) {
        if (!TextUtils.isEmpty(state)) {
            selectSpinnerValue(stateSpinner, RegionData.getStates(), state);
            updateDistricts((String) stateSpinner.getSelectedItem());
        }
        if (!TextUtils.isEmpty(district)) {
            selectSpinnerValue(districtSpinner, RegionData.getDistricts((String) stateSpinner.getSelectedItem()), district);
            updateMandals((String) stateSpinner.getSelectedItem(), (String) districtSpinner.getSelectedItem());
        }
        if (!TextUtils.isEmpty(mandal)) {
            selectSpinnerValue(mandalSpinner,
                    RegionData.getMandals((String) stateSpinner.getSelectedItem(), (String) districtSpinner.getSelectedItem()), mandal);
            updateVillages((String) stateSpinner.getSelectedItem(), (String) districtSpinner.getSelectedItem(), (String) mandalSpinner.getSelectedItem());
        }
        if (!TextUtils.isEmpty(village)) {
            selectSpinnerValue(villageSpinner,
                    RegionData.getVillages((String) stateSpinner.getSelectedItem(), (String) districtSpinner.getSelectedItem(), (String) mandalSpinner.getSelectedItem()),
                    village);
        }
    }

    private void selectSpinnerValue(Spinner spinner, List<String> options, String value) {
        int index = options.indexOf(value);
        if (index >= 0) {
            spinner.setSelection(index);
        }
    }

    private void setupSpinners() {
        ArrayAdapter<String> stateAdapter = createAdapter(RegionData.getStates());
        stateSpinner.setAdapter(stateAdapter);

        stateSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(selection -> updateDistricts(selection)));
        districtSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(selection -> updateMandals((String) stateSpinner.getSelectedItem(), selection)));
        mandalSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(selection -> updateVillages((String) stateSpinner.getSelectedItem(), (String) districtSpinner.getSelectedItem(), selection)));

        updateDistricts((String) stateSpinner.getSelectedItem());
    }

    private void setupPasswordToggles() {
        if (inputLayoutPassword != null) {
            inputLayoutPassword.setEndIconOnClickListener(view -> {
                isPasswordVisible = !isPasswordVisible;
                if (isPasswordVisible) {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    inputLayoutPassword.setEndIconDrawable(R.drawable.ic_eye);
                } else {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    inputLayoutPassword.setEndIconDrawable(R.drawable.ic_eye_off);
                }
                if (passwordInput.getText() != null) {
                    passwordInput.setSelection(passwordInput.getText().length());
                }
            });
        }
        
        if (inputLayoutConfirmPassword != null) {
            inputLayoutConfirmPassword.setEndIconOnClickListener(view -> {
                isConfirmPasswordVisible = !isConfirmPasswordVisible;
                if (isConfirmPasswordVisible) {
                    confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    inputLayoutConfirmPassword.setEndIconDrawable(R.drawable.ic_eye);
                } else {
                    confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    inputLayoutConfirmPassword.setEndIconDrawable(R.drawable.ic_eye_off);
                }
                if (confirmPasswordInput.getText() != null) {
                    confirmPasswordInput.setSelection(confirmPasswordInput.getText().length());
                }
            });
        }
    }
    
    private void setupPasswordWatchers() {
        // Clear password error when user starts typing
        if (passwordInput != null && inputLayoutPassword != null) {
            passwordInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // Clear error immediately when user types
                    inputLayoutPassword.setError(null);
                    // Also clear confirm password error if passwords match
                    if (confirmPasswordInput != null && inputLayoutConfirmPassword != null) {
                        String password = s != null ? s.toString() : "";
                        String confirmPassword = confirmPasswordInput.getText() != null ? confirmPasswordInput.getText().toString() : "";
                        if (!TextUtils.isEmpty(password) && !TextUtils.isEmpty(confirmPassword) && password.equals(confirmPassword)) {
                            inputLayoutConfirmPassword.setError(null);
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
        
        // Clear confirm password error when user starts typing
        if (confirmPasswordInput != null && inputLayoutConfirmPassword != null) {
            confirmPasswordInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // Clear error immediately when user types
                    inputLayoutConfirmPassword.setError(null);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void bindActions() {
        uploadButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        saveButton.setOnClickListener(v -> saveProfile());
        sendOtpButton.setOnClickListener(v -> sendOtp());
        detectLocationButton.setOnClickListener(v -> attemptAutoFillLocation());
    }
    
    private void sendOtp() {
        String phone = phoneInput.getText().toString().trim();
        
        if (TextUtils.isEmpty(phone)) {
            phoneInput.setError(getString(R.string.error_invalid_phone));
            phoneInput.requestFocus();
            return;
        }
        
        // Validate phone number format (should start with + and country code)
        if (!phone.startsWith("+")) {
            phoneInput.setError("Please enter phone number with country code (e.g., +91XXXXXXXXXX)");
            phoneInput.requestFocus();
            return;
        }
        
        if (phone.length() < 10) {
            phoneInput.setError("Please enter a valid phone number");
            phoneInput.requestFocus();
            return;
        }
        
        toggleOtpLoading(true);
        
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        // Auto-verification completed
                        toggleOtpLoading(false);
                        signInWithPhoneAuthCredential(credential);
                    }
                    
                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        toggleOtpLoading(false);
                        String errorMessage = "OTP verification failed";
                        
                        // Handle specific error codes
                        String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                        if (errorMsg.contains("INVALID_CERT_HASH") || 
                            errorMsg.contains("certificate hash") ||
                            errorMsg.contains("17093")) {
                            errorMessage = "Phone authentication setup incomplete. Please contact support or check Firebase Console settings.";
                            android.util.Log.e("RegisterActivity", "Certificate hash not registered in Firebase. Error: " + errorMsg, e);
                        } else if (errorMsg.contains("SMS") || errorMsg.contains("quota")) {
                            errorMessage = "SMS service unavailable. Please try again later.";
                        } else if (errorMsg.contains("invalid") && errorMsg.contains("phone")) {
                            errorMessage = "Invalid phone number format. Please check and try again.";
                        } else {
                            errorMessage = "Error: " + (errorMsg.isEmpty() ? "Unknown error" : errorMsg);
                        }
                        
                        Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        
                        // Log the full error for debugging
                        android.util.Log.e("RegisterActivity", "OTP verification failed", e);
                    }
                    
                    @Override
                    public void onCodeSent(@NonNull String id, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        toggleOtpLoading(false);
                        verificationId = id;
                        resendToken = token;
                        Toast.makeText(RegisterActivity.this, R.string.message_otp_sent, Toast.LENGTH_SHORT).show();
                        sendOtpButton.setText("Resend OTP");
                    }
                })
                .build();
        
        PhoneAuthProvider.verifyPhoneNumber(options);
    }
    
    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "Phone verified successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(RegisterActivity.this, "Verification failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
    
    private void toggleOtpLoading(boolean loading) {
        sendOtpButton.setEnabled(!loading);
        if (loading) {
            sendOtpButton.setText("Sending...");
        }
    }

    private void saveProfile() {
        // Check if password fields are initialized
        if (passwordInput == null || confirmPasswordInput == null) {
            Toast.makeText(this, "Password fields not initialized. Please restart the app.", Toast.LENGTH_LONG).show();
            return;
        }
        
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText() != null ? emailInput.getText().toString().trim() : "";
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";
        String confirmPassword = confirmPasswordInput.getText() != null ? confirmPasswordInput.getText().toString() : "";
        String otp = otpInput.getText() != null ? otpInput.getText().toString().trim() : "";

        // Clear previous errors
        nameInput.setError(null);
        emailInput.setError(null);
        phoneInput.setError(null);
        if (inputLayoutPassword != null) inputLayoutPassword.setError(null);
        if (inputLayoutConfirmPassword != null) inputLayoutConfirmPassword.setError(null);
        if (inputLayoutOtp != null) inputLayoutOtp.setError(null);

        // Validate inputs
        if (TextUtils.isEmpty(name)) {
            nameInput.setError(getString(R.string.error_enter_name));
            nameInput.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return;
        }
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email address");
            emailInput.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(phone)) {
            phoneInput.setError(getString(R.string.error_invalid_phone));
            phoneInput.requestFocus();
            return;
        }
        
        // Validate phone number format (should have at least 10 digits)
        String phoneDigits = phone.replaceAll("[^0-9]", "");
        if (phoneDigits.length() < 10) {
            phoneInput.setError("Please enter a valid phone number (at least 10 digits)");
            phoneInput.requestFocus();
            return;
        }
        
        // Validate password
        if (TextUtils.isEmpty(password) || password.trim().isEmpty()) {
            inputLayoutPassword.setError("Password is required");
            passwordInput.requestFocus();
            return;
        }
        
        if (password.length() < 6) {
            inputLayoutPassword.setError("Password must be at least 6 characters");
            passwordInput.requestFocus();
            return;
        }
        
        // Validate confirm password
        if (TextUtils.isEmpty(confirmPassword) || confirmPassword.trim().isEmpty()) {
            inputLayoutConfirmPassword.setError("Please confirm your password");
            confirmPasswordInput.requestFocus();
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            inputLayoutConfirmPassword.setError("Passwords do not match");
            confirmPasswordInput.requestFocus();
            return;
        }
        
        // Validate State spinner
        Object selectedState = stateSpinner.getSelectedItem();
        if (selectedState == null || TextUtils.isEmpty(selectedState.toString().trim())) {
            Toast.makeText(this, "Please select a State", Toast.LENGTH_SHORT).show();
            stateSpinner.requestFocus();
            return;
        }
        
        // Validate District spinner
        Object selectedDistrict = districtSpinner.getSelectedItem();
        if (selectedDistrict == null || TextUtils.isEmpty(selectedDistrict.toString().trim())) {
            Toast.makeText(this, "Please select a District", Toast.LENGTH_SHORT).show();
            districtSpinner.requestFocus();
            return;
        }
        
        // Validate Mandal spinner
        Object selectedMandal = mandalSpinner.getSelectedItem();
        if (selectedMandal == null || TextUtils.isEmpty(selectedMandal.toString().trim())) {
            Toast.makeText(this, "Please select a Mandal", Toast.LENGTH_SHORT).show();
            mandalSpinner.requestFocus();
            return;
        }
        
        // Validate Village spinner
        Object selectedVillage = villageSpinner.getSelectedItem();
        if (selectedVillage == null || TextUtils.isEmpty(selectedVillage.toString().trim())) {
            Toast.makeText(this, "Please select a Village", Toast.LENGTH_SHORT).show();
            villageSpinner.requestFocus();
            return;
        }
        
        // Verify OTP if it was sent (for phone verification - optional)
        if (!TextUtils.isEmpty(verificationId)) {
            if (TextUtils.isEmpty(otp)) {
                inputLayoutOtp.setError(getString(R.string.error_invalid_otp));
                otpInput.requestFocus();
                return;
            }
            
            // Verify OTP before proceeding
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
            signInWithPhoneAuthCredentialForRegistration(credential, name, email, phone, password);
            return;
        }
        
        // Create account with email and password
        toggleLoading(true);
        createAccountWithEmailPassword(name, email, phone, password);
    }
    
    private void createAccountWithEmailPassword(String name, String email, String phone, String password) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Account created successfully, now save profile
                        if (selectedImageUri != null) {
                            uploadImageAndSave(name, email, phone, null);
                        } else {
                            persistUser(name, email, phone, null);
                        }
                    } else {
                        toggleLoading(false);
                        Exception exception = task.getException();
                        if (exception != null) {
                            String errorMessage = exception.getMessage();
                            if (errorMessage != null) {
                                if (errorMessage.contains("email address is already in use") || 
                                    errorMessage.contains("ERROR_EMAIL_ALREADY_IN_USE")) {
                                    emailInput.setError("This email is already registered. Please use login instead.");
                                } else if (errorMessage.contains("invalid") && errorMessage.contains("email")) {
                                    emailInput.setError("Invalid email format");
                                } else if (errorMessage.contains("weak password") || errorMessage.contains("ERROR_WEAK_PASSWORD")) {
                                    inputLayoutPassword.setError("Password is too weak. Please use a stronger password.");
                                } else {
                                    Toast.makeText(this, "Registration failed: " + errorMessage, Toast.LENGTH_LONG).show();
                                }
                            }
                        } else {
                            Toast.makeText(this, "Registration failed. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
    
    private void signInWithPhoneAuthCredentialForRegistration(PhoneAuthCredential credential, String name, String email, String phone, String password) {
        toggleLoading(true);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // OTP verified, now create email/password account
                        createAccountWithEmailPassword(name, email, phone, password);
                    } else {
                        toggleLoading(false);
                        inputLayoutOtp.setError(getString(R.string.error_invalid_otp));
                        Toast.makeText(RegisterActivity.this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadImageAndSave(String name, String email, String phone, @Nullable String photoUrl) {
        StorageReference reference = firebaseStorage.getReference()
                .child("profiles")
                .child(firebaseAuth.getCurrentUser().getUid())
                .child(UUID.randomUUID().toString() + ".jpg");
        reference.putFile(selectedImageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return reference.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> persistUser(name, email, phone, uri.toString()))
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    Toast.makeText(RegisterActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void persistUser(String name, String email, String phone, @Nullable String photoUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("email", email);
        data.put("phone", phone);
        data.put("state", stateSpinner.getSelectedItem().toString());
        data.put("district", districtSpinner.getSelectedItem().toString());
        data.put("mandal", mandalSpinner.getSelectedItem().toString());
        data.put("village", villageSpinner.getSelectedItem().toString());
        data.put("photoUrl", photoUrl);
        data.put("role", "User");

        firestore.collection("users")
                .document(firebaseAuth.getCurrentUser().getUid())
                .set(data)
                .addOnCompleteListener(task -> {
                    toggleLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, R.string.message_registration_success, Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(RegisterActivity.this, R.string.error_registration_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void toggleLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!loading);
        uploadButton.setEnabled(!loading);
    }

    private ArrayAdapter<String> createAdapter(List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item_register, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_register);
        return adapter;
    }

    private void updateDistricts(String state) {
        ArrayAdapter<String> districtAdapter = createAdapter(RegionData.getDistricts(state));
        districtSpinner.setAdapter(districtAdapter);
        updateMandals(state, (String) districtSpinner.getSelectedItem());
    }

    private void updateMandals(String state, String district) {
        ArrayAdapter<String> mandalAdapter = createAdapter(RegionData.getMandals(state, district));
        mandalSpinner.setAdapter(mandalAdapter);
        updateVillages(state, district, (String) mandalSpinner.getSelectedItem());
    }

    private void updateVillages(String state, String district, String mandal) {
        ArrayAdapter<String> villageAdapter = createAdapter(RegionData.getVillages(state, district, mandal));
        villageSpinner.setAdapter(villageAdapter);
    }

    private static class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {

        private final OnSelectionChanged onSelectionChanged;

        SimpleItemSelectedListener(OnSelectionChanged onSelectionChanged) {
            this.onSelectionChanged = onSelectionChanged;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            Object item = parent.getItemAtPosition(position);
            if (item != null) {
                onSelectionChanged.onChanged(item.toString());
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // no-op
        }
    }

    private interface OnSelectionChanged {
        void onChanged(String selection);
    }
}

