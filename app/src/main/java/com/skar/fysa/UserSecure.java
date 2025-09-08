package com.skar.fysa;

public class UserSecure {
    private String uid;
    private String username;
    private String email;

    // Empty constructor required for Firebase
    public UserSecure() {
    }

    // Full constructor
    public UserSecure(String uid, String username, String email) {
        this.uid = uid;
        this.username = username;
        this.email = email;
    }

    // Getters
    public String getUid() {
        return uid;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    // Setters
    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
