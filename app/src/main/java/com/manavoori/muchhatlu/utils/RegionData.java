package com.manavoori.muchhatlu.utils;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegionData {

    private static final String TAG = "RegionData";
    private static final String ALL = "All";
    private static final String FIRESTORE_COLLECTION = "regions";
    private static final String FIRESTORE_DOCUMENT = "regionData";

    private static final Map<String, Map<String, Map<String, List<String>>>> REGION_TREE = new LinkedHashMap<>();
    private static final Map<String, Map<String, Map<String, List<String>>>> DEFAULT_REGION_TREE = new LinkedHashMap<>();
    
    private static boolean initialized = false;
    private static ListenerRegistration firestoreListener;
    private static Callback initializationCallback;

    static {
        initializeDefaultData();
    }

    private static void initializeDefaultData() {
        Map<String, Map<String, List<String>>> apDistricts = new LinkedHashMap<>();
        Map<String, List<String>> rajahmundryMandals = new LinkedHashMap<>();
        rajahmundryMandals.put("Rajahmundry Urban", Arrays.asList("Downtown", "Katheru", "Tadithota"));
        rajahmundryMandals.put("Rajahmundry Rural", Arrays.asList("Bommuru", "Morampudi"));
        apDistricts.put("East Godavari", rajahmundryMandals);

        Map<String, List<String>> vijayawadaMandals = new LinkedHashMap<>();
        vijayawadaMandals.put("Vijayawada Central", Arrays.asList("One Town", "Two Town", "Three Town"));
        vijayawadaMandals.put("Vijayawada Rural", Arrays.asList("Gollapudi", "Kankipadu"));
        apDistricts.put("Krishna", vijayawadaMandals);
        DEFAULT_REGION_TREE.put("Andhra Pradesh", apDistricts);

        Map<String, Map<String, List<String>>> telanganaDistricts = new LinkedHashMap<>();
        Map<String, List<String>> hyderabadMandals = new LinkedHashMap<>();
        hyderabadMandals.put("Secunderabad", Arrays.asList("Trimulgherry", "Bolarum"));
        hyderabadMandals.put("Charminar", Arrays.asList("Laad Bazaar", "Ghansi Bazaar"));
        telanganaDistricts.put("Hyderabad", hyderabadMandals);

        Map<String, List<String>> warangalMandals = new LinkedHashMap<>();
        warangalMandals.put("Hanamkonda", Arrays.asList("Kazipet", "Fathima Nagar"));
        warangalMandals.put("Warangal Rural", Arrays.asList("Dharmasagar", "Inavolu"));
        telanganaDistricts.put("Warangal", warangalMandals);
        DEFAULT_REGION_TREE.put("Telangana", telanganaDistricts);

        Map<String, Map<String, List<String>>> karnatakaDistricts = new LinkedHashMap<>();
        Map<String, List<String>> bangaloreMandals = new LinkedHashMap<>();
        bangaloreMandals.put("Bangalore North", Arrays.asList("Yelahanka", "Hebbal"));
        bangaloreMandals.put("Bangalore South", Arrays.asList("BTM Layout", "JP Nagar"));
        karnatakaDistricts.put("Bengaluru Urban", bangaloreMandals);

        Map<String, List<String>> mysuruMandals = new LinkedHashMap<>();
        mysuruMandals.put("Mysore North", Arrays.asList("KRS Road", "Mandi Mohalla"));
        mysuruMandals.put("Mysore South", Arrays.asList("Nanjangud", "Chamundipuram"));
        karnatakaDistricts.put("Mysuru", mysuruMandals);
        DEFAULT_REGION_TREE.put("Karnataka", karnatakaDistricts);

        Map<String, Map<String, List<String>>> tamilNaduDistricts = new LinkedHashMap<>();
        Map<String, List<String>> chennaiMandals = new LinkedHashMap<>();
        chennaiMandals.put("Chennai Central", Arrays.asList("Anna Salai", "T Nagar"));
        chennaiMandals.put("Chennai North", Arrays.asList("Sowcarpet", "Royapuram"));
        tamilNaduDistricts.put("Chennai", chennaiMandals);

        Map<String, List<String>> coimbatoreMandals = new LinkedHashMap<>();
        coimbatoreMandals.put("Coimbatore North", Arrays.asList("RS Puram", "Saibaba Colony"));
        coimbatoreMandals.put("Coimbatore South", Arrays.asList("Peelamedu", "Singanallur"));
        tamilNaduDistricts.put("Coimbatore", coimbatoreMandals);
        DEFAULT_REGION_TREE.put("Tamil Nadu", tamilNaduDistricts);

        // Initialize with default data
        REGION_TREE.putAll(DEFAULT_REGION_TREE);
    }

    private RegionData() {
    }

    public static void loadFromFirestore(@NonNull FirebaseFirestore firestore, @Nullable Callback callback) {
        if (initialized && firestoreListener != null) {
            if (callback != null) callback.onSuccess();
            return;
        }

        initializationCallback = callback;

        // Load initial data
        firestore.collection(FIRESTORE_COLLECTION)
                .document(FIRESTORE_DOCUMENT)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        loadFromSnapshot(documentSnapshot);
                    } else {
                        // If document doesn't exist, create it with default data
                        saveToFirestore(firestore, null);
                    }
                    initialized = true;
                    if (initializationCallback != null) {
                        initializationCallback.onSuccess();
                        initializationCallback = null;
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load region data from Firestore, using default data", e);
                    initialized = true;
                    if (initializationCallback != null) {
                        initializationCallback.onSuccess();
                        initializationCallback = null;
                    }
                });

        // Set up real-time listener
        firestoreListener = firestore.collection(FIRESTORE_COLLECTION)
                .document(FIRESTORE_DOCUMENT)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Error listening to region data updates", e);
                        return;
                    }
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        loadFromSnapshot(documentSnapshot);
                        Log.d(TAG, "Region data updated from Firestore");
                    }
                });
    }

    private static void loadFromSnapshot(@NonNull DocumentSnapshot snapshot) {
        synchronized (REGION_TREE) {
            REGION_TREE.clear();
            try {
                Map<String, Object> data = snapshot.getData();
                if (data != null && data.containsKey("regionTree")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> regionTreeMap = (Map<String, Object>) data.get("regionTree");
                    if (regionTreeMap != null) {
                        for (Map.Entry<String, Object> stateEntry : regionTreeMap.entrySet()) {
                            String state = stateEntry.getKey();
                            @SuppressWarnings("unchecked")
                            Map<String, Object> districtsMap = (Map<String, Object>) stateEntry.getValue();
                            if (districtsMap != null) {
                                Map<String, Map<String, List<String>>> districts = new LinkedHashMap<>();
                                for (Map.Entry<String, Object> districtEntry : districtsMap.entrySet()) {
                                    String district = districtEntry.getKey();
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> mandalsMap = (Map<String, Object>) districtEntry.getValue();
                                    if (mandalsMap != null) {
                                        Map<String, List<String>> mandals = new LinkedHashMap<>();
                                        for (Map.Entry<String, Object> mandalEntry : mandalsMap.entrySet()) {
                                            String mandal = mandalEntry.getKey();
                                            @SuppressWarnings("unchecked")
                                            List<String> villages = (List<String>) mandalEntry.getValue();
                                            if (villages != null) {
                                                mandals.put(mandal, new ArrayList<>(villages));
                                            }
                                        }
                                        districts.put(district, mandals);
                                    }
                                }
                                REGION_TREE.put(state, districts);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing region data from Firestore", e);
                // Fall back to default data
                REGION_TREE.clear();
                REGION_TREE.putAll(DEFAULT_REGION_TREE);
            }
        }
    }

    private static void saveToFirestore(@NonNull FirebaseFirestore firestore, @Nullable Callback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("regionTree", REGION_TREE);
        data.put("lastUpdated", System.currentTimeMillis());

        firestore.collection(FIRESTORE_COLLECTION)
                .document(FIRESTORE_DOCUMENT)
                .set(data)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Region data saved to Firestore");
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save region data to Firestore", e);
                    if (callback != null) callback.onFailure(e.getMessage());
                });
    }

    public static void addState(@NonNull FirebaseFirestore firestore, @NonNull String stateName, @NonNull Callback callback) {
        synchronized (REGION_TREE) {
            if (REGION_TREE.containsKey(stateName)) {
                callback.onFailure("State already exists");
                return;
            }
            REGION_TREE.put(stateName, new LinkedHashMap<>());
            saveToFirestore(firestore, callback);
        }
    }

    public static void addDistrict(@NonNull FirebaseFirestore firestore, @NonNull String stateName, 
                                   @NonNull String districtName, @NonNull Callback callback) {
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(stateName);
            if (districts == null) {
                callback.onFailure("State not found");
                return;
            }
            if (districts.containsKey(districtName)) {
                callback.onFailure("District already exists");
                return;
            }
            districts.put(districtName, new LinkedHashMap<>());
            saveToFirestore(firestore, callback);
        }
    }

    public static void addMandal(@NonNull FirebaseFirestore firestore, @NonNull String stateName, 
                                 @NonNull String districtName, @NonNull String mandalName, @NonNull Callback callback) {
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(stateName);
            if (districts == null) {
                callback.onFailure("State not found");
                return;
            }
            Map<String, List<String>> mandals = districts.get(districtName);
            if (mandals == null) {
                callback.onFailure("District not found");
                return;
            }
            if (mandals.containsKey(mandalName)) {
                callback.onFailure("Mandal already exists");
                return;
            }
            mandals.put(mandalName, new ArrayList<>());
            saveToFirestore(firestore, callback);
        }
    }

    public static void addVillage(@NonNull FirebaseFirestore firestore, @NonNull String stateName, 
                                  @NonNull String districtName, @NonNull String mandalName, 
                                  @NonNull String villageName, @NonNull Callback callback) {
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(stateName);
            if (districts == null) {
                callback.onFailure("State not found");
                return;
            }
            Map<String, List<String>> mandals = districts.get(districtName);
            if (mandals == null) {
                callback.onFailure("District not found");
                return;
            }
            List<String> villages = mandals.get(mandalName);
            if (villages == null) {
                callback.onFailure("Mandal not found");
                return;
            }
            if (villages.contains(villageName)) {
                callback.onFailure("Village already exists");
                return;
            }
            villages.add(villageName);
            saveToFirestore(firestore, callback);
        }
    }

    public static void deleteState(@NonNull FirebaseFirestore firestore, @NonNull String stateName, @NonNull Callback callback) {
        synchronized (REGION_TREE) {
            if (!REGION_TREE.containsKey(stateName)) {
                callback.onFailure("State not found");
                return;
            }
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(stateName);
            if (districts != null && !districts.isEmpty()) {
                callback.onFailure("Cannot delete state. It contains districts. Please delete all districts first.");
                return;
            }
            REGION_TREE.remove(stateName);
            saveToFirestore(firestore, callback);
        }
    }

    public static void deleteDistrict(@NonNull FirebaseFirestore firestore, @NonNull String stateName, 
                                     @NonNull String districtName, @NonNull Callback callback) {
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(stateName);
            if (districts == null) {
                callback.onFailure("State not found");
                return;
            }
            if (!districts.containsKey(districtName)) {
                callback.onFailure("District not found");
                return;
            }
            Map<String, List<String>> mandals = districts.get(districtName);
            if (mandals != null && !mandals.isEmpty()) {
                callback.onFailure("Cannot delete district. It contains mandals. Please delete all mandals first.");
                return;
            }
            districts.remove(districtName);
            saveToFirestore(firestore, callback);
        }
    }

    public static void deleteMandal(@NonNull FirebaseFirestore firestore, @NonNull String stateName, 
                                   @NonNull String districtName, @NonNull String mandalName, @NonNull Callback callback) {
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(stateName);
            if (districts == null) {
                callback.onFailure("State not found");
                return;
            }
            Map<String, List<String>> mandals = districts.get(districtName);
            if (mandals == null) {
                callback.onFailure("District not found");
                return;
            }
            if (!mandals.containsKey(mandalName)) {
                callback.onFailure("Mandal not found");
                return;
            }
            List<String> villages = mandals.get(mandalName);
            if (villages != null && !villages.isEmpty()) {
                callback.onFailure("Cannot delete mandal. It contains villages. Please delete all villages first.");
                return;
            }
            mandals.remove(mandalName);
            saveToFirestore(firestore, callback);
        }
    }

    public static List<String> getStates() {
        synchronized (REGION_TREE) {
            return addAllOption(new ArrayList<>(REGION_TREE.keySet()));
        }
    }

    public static List<String> getStatesForDelete() {
        synchronized (REGION_TREE) {
            return new ArrayList<>(REGION_TREE.keySet());
        }
    }

    public static List<String> getDistricts(String state) {
        if (state == null || state.isEmpty() || ALL.equals(state)) {
            return Collections.singletonList(ALL);
        }
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(state);
            if (districts == null || districts.isEmpty()) {
                return Collections.singletonList(ALL);
            }
            return addAllOption(new ArrayList<>(districts.keySet()));
        }
    }

    public static List<String> getDistrictsForDelete(String state) {
        if (state == null || state.isEmpty() || ALL.equals(state)) {
            return new ArrayList<>();
        }
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(state);
            if (districts == null || districts.isEmpty()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(districts.keySet());
        }
    }

    public static List<String> getMandals(String state, String district) {
        if (state == null || district == null || ALL.equals(state) || ALL.equals(district)) {
            return Collections.singletonList(ALL);
        }
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(state);
            if (districts == null) {
                return Collections.singletonList(ALL);
            }
            Map<String, List<String>> mandals = districts.get(district);
            if (mandals == null || mandals.isEmpty()) {
                return Collections.singletonList(ALL);
            }
            return addAllOption(new ArrayList<>(mandals.keySet()));
        }
    }

    public static List<String> getMandalsForDelete(String state, String district) {
        if (state == null || district == null || ALL.equals(state) || ALL.equals(district)) {
            return new ArrayList<>();
        }
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(state);
            if (districts == null) {
                return new ArrayList<>();
            }
            Map<String, List<String>> mandals = districts.get(district);
            if (mandals == null || mandals.isEmpty()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(mandals.keySet());
        }
    }

    public static List<String> getVillages(String state, String district, String mandal) {
        if (state == null || district == null || mandal == null || 
            ALL.equals(state) || ALL.equals(district) || ALL.equals(mandal)) {
            return Collections.singletonList(ALL);
        }
        synchronized (REGION_TREE) {
            Map<String, Map<String, List<String>>> districts = REGION_TREE.get(state);
            if (districts == null) {
                return Collections.singletonList(ALL);
            }
            Map<String, List<String>> mandals = districts.get(district);
            if (mandals == null) {
                return Collections.singletonList(ALL);
            }
            List<String> villages = mandals.get(mandal);
            if (villages == null || villages.isEmpty()) {
                return Collections.singletonList(ALL);
            }
            return addAllOption(new ArrayList<>(villages));
        }
    }

    public static String getAllLabel() {
        return ALL;
    }

    private static List<String> addAllOption(List<String> input) {
        List<String> list = new ArrayList<>();
        list.add(ALL);
        list.addAll(input);
        return list;
    }

    public interface Callback {
        void onSuccess();
        void onFailure(String error);
    }

    public static void removeListener() {
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
    }
}
