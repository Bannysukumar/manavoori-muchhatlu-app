package com.manavoori.muchhatlu.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.manavoori.muchhatlu.R;

public class TermsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms);

        ImageButton buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        CheckBox checkAgree = findViewById(R.id.checkAgree);
        Button agreeButton = findViewById(R.id.buttonAgree);

        checkAgree.setOnCheckedChangeListener((buttonView, isChecked) -> agreeButton.setEnabled(isChecked));
        agreeButton.setOnClickListener(v -> finish());
    }
}

