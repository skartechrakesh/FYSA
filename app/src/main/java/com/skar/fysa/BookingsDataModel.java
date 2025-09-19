package com.skar.fysa;

public class BookingsDataModel {
    private String userId;
    private String username;
    private String userContact; // ✅ ADDED: New field for user's contact
    private String spaceId;
    private String spaceName;
    private String spaceCategory;
    private String bookingDate;
    private long createdAt;
    private String status;
    private boolean read;
    private double latitude;
    private double longitude;

    // Empty constructor required for Firebase
    public BookingsDataModel() { }

    // ✅ UPDATED: Constructor now includes the userContact parameter
    public BookingsDataModel(String userId, String username, String userContact,
                             String spaceId, String spaceName,
                             String spaceCategory, String bookingDate,
                             boolean read, long createdAt, String status,
                             double latitude, double longitude) {
        this.userId = userId;
        this.username = username;
        this.userContact = userContact; // ✅ INITIALIZED: The new field
        this.spaceId = spaceId;
        this.spaceName = spaceName;
        this.spaceCategory = spaceCategory;
        this.bookingDate = bookingDate;
        this.read = read;
        this.createdAt = createdAt;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getUserContact() { return userContact; } // ✅ ADDED: Getter for the new field
    public String getSpaceId() { return spaceId; }
    public String getSpaceName() { return spaceName; }
    public String getSpaceCategory() { return spaceCategory; }
    public String getBookingDate() { return bookingDate; }
    public long getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public boolean isRead() { return read; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    // Setters
    public void setUserId(String userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setUserContact(String userContact) { this.userContact = userContact; } // ✅ ADDED: Setter for the new field
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public void setSpaceName(String spaceName) { this.spaceName = spaceName; }
    public void setSpaceCategory(String spaceCategory) { this.spaceCategory = spaceCategory; }
    public void setBookingDate(String bookingDate) { this.bookingDate = bookingDate; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setStatus(String status) { this.status = status; }
    public void setRead(boolean read) { this.read = read; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}