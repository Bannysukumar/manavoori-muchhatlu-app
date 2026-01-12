package com.manavoori.muchhatlu.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.manavoori.muchhatlu.R;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText mobileInput;
    private TextInputEditText passwordInput;
    private TextInputLayout inputLayoutMobile;
    private TextInputLayout inputLayoutPassword;
    private Button loginButton;
    private Button registerButton;
    private ProgressBar progressBar;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        initViews();
        bindActions();
        setupPasswordToggle();
    }

    private void initViews() {
        inputLayoutMobile = findViewById(R.id.inputLayoutMobile);
        inputLayoutPassword = findViewById(R.id.inputLayoutPassword);
        mobileInput = findViewById(R.id.editMobile);
        passwordInput = findViewById(R.id.editPassword);
        loginButton = findViewById(R.id.buttonLogin);
        registerButton = findViewById(R.id.buttonRegister);
        progressBar = findViewById(R.id.progress);
        
        android.widget.TextView textForgotPassword = findViewById(R.id.textForgotPassword);
        if (textForgotPassword != null) {
            textForgotPassword.setOnClickListener(view -> showForgotPasswordDialog());
        }
    }

    private void bindActions() {
        loginButton.setOnClickListener(view -> attemptLogin());
        registerButton.setOnClickListener(view -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            String mobileOrEmail = mobileInput.getText() != null ? mobileInput.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(mobileOrEmail)) {
                intent.putExtra(RegisterActivity.EXTRA_PHONE, mobileOrEmail);
            }
            startActivity(intent);
        });
    }

    private void setupPasswordToggle() {
        inputLayoutPassword.setEndIconOnClickListener(view -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                inputLayoutPassword.setEndIconDrawable(R.drawable.ic_eye);
            } else {
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                inputLayoutPassword.setEndIconDrawable(R.drawable.ic_eye_off);
            }
            // Move cursor to end
            if (passwordInput.getText() != null) {
                passwordInput.setSelection(passwordInput.getText().length());
            }
        });
    }

    private void attemptLogin() {
        String mobileOrEmail = mobileInput.getText() != null ? mobileInput.getText().toString().trim() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";

        // Clear previous errors
        inputLayoutMobile.setError(null);
        inputLayoutPassword.setError(null);

        // Validate input
        if (TextUtils.isEmpty(mobileOrEmail)) {
            inputLayoutMobile.setError(getString(R.string.error_invalid_phone));
            mobileInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            inputLayoutPassword.setError("Password is required");
            passwordInput.requestFocus();
            return;
        }

        if (password.length() < 6) {
            inputLayoutPassword.setError("Password must be at least 6 characters");
            passwordInput.requestFocus();
            return;
        }

        toggleLoading(true);

        // Determine if input is email or phone number
        // Check if it's a valid email format
        boolean isEmailFormat = Patterns.EMAIL_ADDRESS.matcher(mobileOrEmail).matches();
        
        if (isEmailFormat) {
            // It's an email - try direct email/password authentication
            signInWithEmailPassword(mobileOrEmail, password);
        } else {
            // Not a valid email format - could be phone number or invalid input
            // Try to find user by phone number in Firestore and get their email
            signInWithPhoneOrEmail(mobileOrEmail, password);
        }
    }

    private void signInWithEmailPassword(String email, String password) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        onLoginSuccess();
                    } else {
                        toggleLoading(false);
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthException) {
                            FirebaseAuthException authException = (FirebaseAuthException) exception;
                            String errorCode = authException.getErrorCode();
                            String errorMessage = authException.getLocalizedMessage();
                            
                            if ("ERROR_USER_NOT_FOUND".equals(errorCode)) {
                                inputLayoutMobile.setError("No account found with this email");
                                inputLayoutPassword.setError(null);
                            } else if ("ERROR_WRONG_PASSWORD".equals(errorCode) || 
                                      "ERROR_INVALID_CREDENTIAL".equals(errorCode)) {
                                inputLayoutPassword.setError("Incorrect password");
                                inputLayoutMobile.setError(null);
                            } else if ("ERROR_INVALID_EMAIL".equals(errorCode)) {
                                inputLayoutMobile.setError("Invalid email format");
                                inputLayoutPassword.setError(null);
                            } else if ("ERROR_USER_DISABLED".equals(errorCode)) {
                                Toast.makeText(LoginActivity.this, "This account has been disabled. Please contact support.", Toast.LENGTH_LONG).show();
                            } else if ("ERROR_TOO_MANY_REQUESTS".equals(errorCode)) {
                                Toast.makeText(LoginActivity.this, "Too many failed login attempts. Please try again later.", Toast.LENGTH_LONG).show();
                            } else {
                                // Show a user-friendly message for other errors
                                String friendlyMessage = errorMessage != null && !errorMessage.isEmpty() 
                                    ? errorMessage 
                                    : "Login failed. Please check your credentials and try again.";
                                Toast.makeText(LoginActivity.this, friendlyMessage, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, exception != null ? exception.getLocalizedMessage() : getString(R.string.error_login_failed), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void signInWithPhoneOrEmail(String mobileOrEmail, String password) {
        // Try to find user by phone in Firestore, get their email, and authenticate
        firestore.collection("users")
                .whereEqualTo("phone", mobileOrEmail)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // User found by phone number, get their email
                        com.google.firebase.firestore.QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            com.google.firebase.firestore.DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                            String email = document.getString("email");
                            if (email != null && !email.isEmpty()) {
                                // Found email, try to authenticate with email/password
                                signInWithEmailPassword(email, password);
                                return;
                            }
                        }
                    }
                    
                    // If not found by phone, also try to find by email (in case user entered email but it didn't match pattern)
                    firestore.collection("users")
                            .whereEqualTo("email", mobileOrEmail)
                            .limit(1)
                            .get()
                            .addOnCompleteListener(emailTask -> {
                                toggleLoading(false);
                                if (emailTask.isSuccessful() && !emailTask.getResult().isEmpty()) {
                                    // Email exists in Firestore but login failed - might be wrong password
                                    inputLayoutPassword.setError("Incorrect password");
                                    inputLayoutMobile.setError(null);
                                } else {
                                    // No account found
                                    inputLayoutMobile.setError("No account found with this email or phone. Please register first.");
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    android.util.Log.e("LoginActivity", "Error checking user in Firestore", e);
                    inputLayoutMobile.setError("Unable to verify account. Please check your email/phone and try again.");
                });
    }

    private void onLoginSuccess() {
        if (firebaseAuth.getCurrentUser() == null) {
            toggleLoading(false);
            return;
        }
        firestore.collection("users")
                .document(firebaseAuth.getCurrentUser().getUid())
                .get()
                .addOnCompleteListener(task -> {
                    toggleLoading(false);
                    if (task.isSuccessful()) {
                        DocumentSnapshot snapshot = task.getResult();
                        if (snapshot != null && snapshot.exists()) {
                            goToHome();
                        } else {
                            goToRegister();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, R.string.error_fetch_profile, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        String mobileOrEmail = mobileInput.getText() != null ? mobileInput.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(mobileOrEmail)) {
            intent.putExtra(RegisterActivity.EXTRA_PHONE, mobileOrEmail);
        }
        startActivity(intent);
        finish();
    }

    private void goToHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void toggleLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        registerButton.setEnabled(!loading);
        mobileInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
    }

    private void showForgotPasswordDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.action_forgot_password);

        // Create input field
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint(getString(R.string.hint_mobile_or_email));
        
        // Pre-fill with email if available
        String currentEmail = mobileInput.getText() != null ? mobileInput.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(currentEmail) && Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
            input.setText(currentEmail);
        }

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        container.addView(input);

        builder.setView(container);
        builder.setPositiveButton(R.string.action_send, (dialog, which) -> {
            String email = input.getText() != null ? input.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(email)) {
                sendPasswordResetEmail(email);
            } else {
                Toast.makeText(LoginActivity.this, "Please enter your email address", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void sendPasswordResetEmail(String email) {
        // Validate email format
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        // First, check if user exists in Firestore (since users might have registered with email)
        toggleLoading(true);
        
        // Check Firestore for the email
        firestore.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnCompleteListener(firestoreTask -> {
                    if (firestoreTask.isSuccessful() && !firestoreTask.getResult().isEmpty()) {
                        // User exists in Firestore, now send reset email
                        sendFirebasePasswordResetEmail(email);
                    } else {
                        // User not found in Firestore, but still try Firebase Auth (in case email exists in Auth but not in Firestore)
                        sendFirebasePasswordResetEmail(email);
                    }
                })
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    android.util.Log.e("LoginActivity", "Error checking user in Firestore", e);
                    // Still try to send reset email even if Firestore check fails
                    sendFirebasePasswordResetEmail(email);
                });
    }
    
    private void sendFirebasePasswordResetEmail(String email) {
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    toggleLoading(false);
                    if (task.isSuccessful()) {
                        android.util.Log.d("LoginActivity", "Password reset email sent successfully to: " + email);
                        // Show success message with more details
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                        builder.setTitle("Password Reset Email Sent");
                        builder.setMessage("A password reset email has been sent to:\n\n" + email + "\n\n" +
                                "Please check:\n" +
                                "• Your inbox\n" +
                                "• Spam/Junk folder\n" +
                                "• Wait 2-5 minutes for delivery\n\n" +
                                "If you don't receive the email, please:\n" +
                                "• Verify the email address is correct\n" +
                                "• Check if the email is registered in the app\n" +
                                "• Try again after a few minutes");
                        builder.setPositiveButton("OK", null);
                        builder.setIcon(android.R.drawable.ic_dialog_info);
                        builder.show();
                    } else {
                        Exception exception = task.getException();
                        android.util.Log.e("LoginActivity", "Failed to send password reset email", exception);
                        
                        if (exception instanceof FirebaseAuthException) {
                            FirebaseAuthException authException = (FirebaseAuthException) exception;
                            String errorCode = authException.getErrorCode();
                            String errorMessage = authException.getLocalizedMessage();
                            
                            android.util.Log.e("LoginActivity", "Error code: " + errorCode + ", Message: " + errorMessage);
                            
                            if ("ERROR_USER_NOT_FOUND".equals(errorCode)) {
                                Toast.makeText(LoginActivity.this, "No account found with this email address. Please check your email or register first.", Toast.LENGTH_LONG).show();
                            } else if ("ERROR_INVALID_EMAIL".equals(errorCode)) {
                                Toast.makeText(LoginActivity.this, "Invalid email address format", Toast.LENGTH_SHORT).show();
                            } else if ("ERROR_TOO_MANY_REQUESTS".equals(errorCode)) {
                                Toast.makeText(LoginActivity.this, "Too many requests. Please wait a few minutes and try again.", Toast.LENGTH_LONG).show();
                            } else {
                                // Show detailed error message
                                String message = errorMessage != null && !errorMessage.isEmpty() 
                                    ? errorMessage 
                                    : "Failed to send password reset email. Please try again later.";
                                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            String errorMsg = exception != null ? exception.getMessage() : "Unknown error";
                            android.util.Log.e("LoginActivity", "Exception: " + errorMsg);
                            Toast.makeText(LoginActivity.this, "Failed to send password reset email: " + errorMsg, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    android.util.Log.e("LoginActivity", "Exception sending password reset email", e);
                    Toast.makeText(LoginActivity.this, "Network error. Please check your internet connection and try again.", Toast.LENGTH_LONG).show();
                });
    }
}

