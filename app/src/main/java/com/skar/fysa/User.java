package com.skar.fysa;

public class User {
    private String username;
    private String contact;
    private String name;
    private String email;
    private String accountType; // "user" or "owner"
    private String uid; // Firebase UID for secure access

    // Empty constructor required for Firebase
    public User() {
    }

    // Full constructor
    public User(String username, String contact, String name, String email, String accountType, String uid) {
        this.username = username;
        this.contact = contact;
        this.name = name;
        this.email = email;
        this.accountType = accountType;
        this.uid = uid;
    }

    // Getters
    public String getUsername() {
        return username;
    }

    // Setters
    public void setUsername(String username) {
        this.username = username;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }
}
