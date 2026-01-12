package com.manavoori.muchhatlu.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;
import com.manavoori.muchhatlu.R;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1800L;

    private ActivityResultLauncher<String> notificationsPermissionLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private boolean notificationPromptShown;
    private boolean locationPromptShown;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SplashScreen.installSplashScreen(this);
        setContentView(R.layout.activity_splash);

        notificationsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), result -> proceedAfterNotifications());
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> proceedAfterLocation());

        new Handler(Looper.getMainLooper()).postDelayed(this::maybeRequestNotifications, SPLASH_DELAY_MS);
    }

    private void maybeRequestNotifications() {
        if (notificationPromptShown) {
            proceedAfterNotifications();
            return;
        }
        notificationPromptShown = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        proceedAfterNotifications();
    }

    private void proceedAfterNotifications() {
        maybeRequestLocation();
    }

    private void maybeRequestLocation() {
        if (locationPromptShown) {
            proceedAfterLocation();
            return;
        }
        locationPromptShown = true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }
        proceedAfterLocation();
    }

    private void proceedAfterLocation() {
        routeNext();
    }

    private void routeNext() {
        if (!isFinishing()) {
            Intent intent;
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                intent = new Intent(this, HomeActivity.class);
            } else {
                intent = new Intent(this, LoginActivity.class);
            }
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }
    }
}

