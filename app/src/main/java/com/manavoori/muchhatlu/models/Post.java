package com.manavoori.muchhatlu.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Exclude;

import java.util.HashMap;
import java.util.Map;

public class Post {
    private String id;
    private String userId;
    private String caption;
    private String mediaUrl;
    private String mediaType;
    private String state;
    private String district;
    private String mandal;
    private String village;
    private long createdAt;
    private long likeCount;
    private long commentCount;
    private boolean reported;
    private boolean approved;
    private long shareCount;
    private long viewCount;
    @Exclude
    private boolean likedByCurrentUser;

    public Post() {
        // Firestore requires public no-args constructor
    }

    public static Post fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        Post post = snapshot.toObject(Post.class);
        if (post == null) {
            post = new Post();
        }
        post.id = snapshot.getId();
        return post;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("caption", caption);
        map.put("mediaUrl", mediaUrl);
        map.put("mediaType", mediaType);
        map.put("state", state);
        map.put("district", district);
        map.put("mandal", mandal);
        map.put("village", village);
        map.put("createdAt", createdAt);
        map.put("likeCount", likeCount);
        map.put("commentCount", commentCount);
        map.put("reported", reported);
        map.put("approved", approved);
        map.put("shareCount", shareCount);
        map.put("viewCount", viewCount);
        return map;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getCaption() {
        return caption;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getMediaType() {
        return mediaType;
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

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public long getShareCount() {
        return shareCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    @Exclude
    public boolean isLikedByCurrentUser() {
        return likedByCurrentUser;
    }

    public boolean isReported() {
        return reported;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
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

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public void setReported(boolean reported) {
        this.reported = reported;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public void setShareCount(long shareCount) {
        this.shareCount = shareCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public void setLikedByCurrentUser(boolean likedByCurrentUser) {
        this.likedByCurrentUser = likedByCurrentUser;
    }
}

