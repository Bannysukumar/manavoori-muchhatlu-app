package com.manavoori.muchhatlu.models;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.manavoori.muchhatlu.R;

public class AdminUser {

    private String id;
    private String name;
    private String phone;
    private String role;
    private String state;
    private String district;
    private String mandal;
    private String village;
    private boolean blocked;
    private boolean active;
    private boolean deactivated;
    private boolean banned;
    private String email;

    public static AdminUser fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        AdminUser user = new AdminUser();
        user.id = snapshot.getId();
        user.name = snapshot.getString("name");
        user.phone = snapshot.getString("phone");
        user.role = snapshot.getString("role");
        user.state = snapshot.getString("state");
        user.district = snapshot.getString("district");
        user.mandal = snapshot.getString("mandal");
        user.village = snapshot.getString("village");
        user.email = snapshot.getString("email");
        Boolean blockedValue = snapshot.getBoolean("blocked");
        user.blocked = blockedValue != null && blockedValue;
        Boolean activeValue = snapshot.getBoolean("active");
        user.active = activeValue == null || activeValue; // Default to true if not set
        Boolean deactivatedValue = snapshot.getBoolean("deactivated");
        user.deactivated = deactivatedValue != null && deactivatedValue;
        Boolean bannedValue = snapshot.getBoolean("banned");
        user.banned = bannedValue != null && bannedValue;
        return user;
    }

    @NonNull
    public String getFormattedLocation(@NonNull Context context) {
        String stateValue = firstOrDefault(state, context.getString(R.string.all_regions_label));
        String districtValue = firstOrDefault(district, context.getString(R.string.all_regions_label));
        String mandalValue = firstOrDefault(mandal, context.getString(R.string.all_regions_label));
        String villageValue = firstOrDefault(village, context.getString(R.string.all_regions_label));
        return context.getString(R.string.label_user_location, stateValue, districtValue, mandalValue, villageValue);
    }

    private String firstOrDefault(@Nullable String input, @NonNull String fallback) {
        if (TextUtils.isEmpty(input)) {
            return fallback;
        }
        return input;
    }

    public boolean isAdmin() {
        return role != null && ("Admin".equalsIgnoreCase(role) || "Super Admin".equalsIgnoreCase(role));
    }

    public boolean isSuperAdmin() {
        return role != null && "Super Admin".equalsIgnoreCase(role);
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isActive() {
        return active && !deactivated && !banned && !blocked;
    }

    public boolean isDeactivated() {
        return deactivated;
    }

    public boolean isBanned() {
        return banned;
    }

    @NonNull
    public String getStatusLabel(@NonNull Context context) {
        if (banned) {
            return context.getString(R.string.label_user_status_banned);
        } else if (deactivated) {
            return context.getString(R.string.label_user_status_deactivated);
        } else if (blocked) {
            return context.getString(R.string.label_user_status_blocked);
        } else if (active) {
            return context.getString(R.string.label_user_status_active);
        } else {
            return context.getString(R.string.label_user_status_inactive);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public String getPhone() {
        return phone != null ? phone : "-";
    }

    public String getRole() {
        return role != null ? role : "Member";
    }

    public String getState() {
        return state;
    }

    public String getDistrict() {
        return district;
    }

    public String getMandal() {
        return mandal;
    }

    public String getVillage() {
        return village;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public void setMandal(String mandal) {
        this.mandal = mandal;
    }

    public void setVillage(String village) {
        this.village = village;
    }

    public String getEmail() {
        return email != null ? email : "";
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setDeactivated(boolean deactivated) {
        this.deactivated = deactivated;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }
}


