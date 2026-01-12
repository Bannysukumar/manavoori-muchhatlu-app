package com.manavoori.muchhatlu;

import android.app.Application;

import com.google.firebase.firestore.FirebaseFirestore;
import com.manavoori.muchhatlu.utils.RegionData;

public class ManavooriApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize RegionData from Firestore
        RegionData.loadFromFirestore(FirebaseFirestore.getInstance(), null);
    }
}

