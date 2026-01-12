package com.manavoori.muchhatlu.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.manavoori.muchhatlu.R;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";
    private static final String KEY_AUTO_PLAY = "auto_play";
    private static final String KEY_DATA_SAVER = "data_saver";

    private SharedPreferences preferences;
    private ExecutorService cacheExecutor;
    private Handler mainHandler;

    private TextView cacheSizeText;
    private TextView versionText;
    private MaterialButton clearCacheButton;
    private MaterialSwitch notificationsSwitch;
    private MaterialSwitch darkModeSwitch;
    private MaterialSwitch autoPlaySwitch;
    private MaterialSwitch dataSaverSwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        cacheExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        ImageButton buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        Spinner languageSpinner = findViewById(R.id.spinnerLanguage);
        notificationsSwitch = findViewById(R.id.switchNotifications);
        darkModeSwitch = findViewById(R.id.switchDarkMode);
        autoPlaySwitch = findViewById(R.id.switchAutoPlay);
        dataSaverSwitch = findViewById(R.id.switchDataSaver);
        clearCacheButton = findViewById(R.id.buttonClearCache);
        MaterialButton legalInfoButton = findViewById(R.id.buttonLegalInfo);
        MaterialButton feedbackButton = findViewById(R.id.buttonFeedback);
        MaterialButton rateAppButton = findViewById(R.id.buttonRateApp);
        MaterialButton logoutButton = findViewById(R.id.buttonLogout);
        cacheSizeText = findViewById(R.id.textCacheSize);
        versionText = findViewById(R.id.textVersion);

        setupLanguageSpinner(languageSpinner);
        setupSwitches();
        setupActions(legalInfoButton, feedbackButton, rateAppButton, logoutButton);
        loadCacheSize();
        versionText.setText(getString(R.string.label_version, resolveVersionName()));
    }

    private String resolveVersionName() {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageInfo(getPackageName(), 0);
            return info.versionName != null ? info.versionName : "-";
        } catch (PackageManager.NameNotFoundException e) {
            return "-";
        }
    }

    private void setupLanguageSpinner(Spinner languageSpinner) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.languages_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);

        String savedLanguage = preferences.getString(KEY_LANGUAGE, adapter.getItem(0).toString());
        int position = adapter.getPosition(savedLanguage);
        languageSpinner.setSelection(position >= 0 ? position : 0);

        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                preferences.edit().putString(KEY_LANGUAGE, selected).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // no-op
            }
        });
    }

    private void setupSwitches() {
        boolean notificationsEnabled = preferences.getBoolean(KEY_NOTIFICATIONS, true);
        notificationsSwitch.setChecked(notificationsEnabled);
        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply();
            if (!isChecked) {
                NotificationManagerCompat.from(this).cancelAll();
            }
        });

        boolean darkMode = preferences.getBoolean(KEY_DARK_MODE, false);
        darkModeSwitch.setChecked(darkMode);
        applyNightMode(darkMode);
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            applyNightMode(isChecked);
        });

        boolean autoPlay = preferences.getBoolean(KEY_AUTO_PLAY, true);
        autoPlaySwitch.setChecked(autoPlay);
        autoPlaySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(KEY_AUTO_PLAY, isChecked).apply());

        boolean dataSaver = preferences.getBoolean(KEY_DATA_SAVER, false);
        dataSaverSwitch.setChecked(dataSaver);
        dataSaverSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(KEY_DATA_SAVER, isChecked).apply());
    }

    private void setupActions(MaterialButton legalInfoButton,
                              MaterialButton feedbackButton,
                              MaterialButton rateAppButton,
                              MaterialButton logoutButton) {
        legalInfoButton.setOnClickListener(v -> startActivity(new Intent(this, TermsActivity.class)));

        clearCacheButton.setOnClickListener(v -> clearCachedMedia());
        feedbackButton.setOnClickListener(v -> sendFeedback());
        rateAppButton.setOnClickListener(v -> openInStore());

        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void clearCachedMedia() {
        clearCacheButton.setEnabled(false);
        cacheExecutor.execute(() -> {
            String formatted = Formatter.formatFileSize(getApplicationContext(), 0);
            try {
                clearDirectoryContents(getCacheDir());
                clearDirectoryContents(getExternalCacheDir());
                Glide.get(getApplicationContext()).clearDiskCache();

                long cacheBytes = calculateDirectorySize(getCacheDir()) + calculateDirectorySize(getExternalCacheDir());
                formatted = Formatter.formatFileSize(getApplicationContext(), cacheBytes);
            } finally {
                String cacheLabel = formatted;
                mainHandler.post(() -> {
                    Glide.get(this).clearMemory();
                    cacheSizeText.setText(getString(R.string.label_cache_size, cacheLabel));
                    Toast.makeText(this, R.string.message_cache_cleared, Toast.LENGTH_SHORT).show();
                    clearCacheButton.setEnabled(true);
                });
            }
        });
    }

    private void loadCacheSize() {
        cacheExecutor.execute(() -> {
            long cacheBytes = calculateDirectorySize(getCacheDir()) + calculateDirectorySize(getExternalCacheDir());
            String formatted = Formatter.formatFileSize(getApplicationContext(), cacheBytes);
            mainHandler.post(() -> cacheSizeText.setText(getString(R.string.label_cache_size, formatted)));
        });
    }

    private long calculateDirectorySize(@Nullable File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        long result = 0L;
        File[] children = dir.listFiles();
        if (children == null) {
            return result;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                result += calculateDirectorySize(child);
            } else {
                result += child.length();
            }
        }
        return result;
    }

    private void clearDirectoryContents(@Nullable File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                clearDirectoryContents(child);
            }
            // Ignore delete failures silently
            child.delete();
        }
    }

    private void sendFeedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@manavoorimuchhatlu.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback: Manavoori Muchhatlu");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.message_no_email_client, Toast.LENGTH_SHORT).show();
        }
    }

    private void openInStore() {
        Uri uri = Uri.parse("market://details?id=" + getPackageName());
        Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            startActivity(goToMarket);
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
        }
    }

    private void applyNightMode(boolean enabled) {
        AppCompatDelegate.setDefaultNightMode(enabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cacheExecutor.shutdownNow();
    }
}

