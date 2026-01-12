package com.manavoori.muchhatlu.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.manavoori.muchhatlu.R;

import java.util.List;

public class ReferralActivity extends AppCompatActivity {

    private TextView codeText;
    private TextView countText;
    private TextView tierText;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    private List<String> referredUsers;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_referral);

        ImageButton buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        codeText = findViewById(R.id.textReferralCode);
        countText = findViewById(R.id.textReferralCount);
        tierText = findViewById(R.id.textTier);
        Button shareButton = findViewById(R.id.buttonShare);
        Button viewButton = findViewById(R.id.buttonViewReferred);

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.error_login_required, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String code = auth.getCurrentUser().getUid();
        codeText.setText(code);

        shareButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.referral_share_message, code));
            startActivity(Intent.createChooser(intent, getString(R.string.action_share_code)));
        });

        viewButton.setOnClickListener(v -> showReferredUsers());

        loadReferralData();
    }

    private void loadReferralData() {
        firestore.collection("referrals")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    long count = snapshot.contains("count") ? snapshot.getLong("count") : 0;
                    String tier = snapshot.getString("tier") != null ? snapshot.getString("tier") : "Bronze";
                    referredUsers = (List<String>) snapshot.get("users");
                    countText.setText(getString(R.string.referral_count_format, count));
                    tierText.setText(getString(R.string.referral_tier_format, tier));
                });
    }

    private void showReferredUsers() {
        if (referredUsers == null || referredUsers.isEmpty()) {
            Toast.makeText(this, R.string.message_no_referred_users, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] items = referredUsers.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_referral_users)
                .setItems(items, null)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}

