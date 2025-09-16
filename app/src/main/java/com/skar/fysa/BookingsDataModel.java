package com.skar.fysa;

public class BookingsDataModel {
    private String userId;
    private String username;
    private String spaceId;
    private String spaceName;
    private String spaceCategory;
    private String bookingDate;
    private long createdAt;     // NEW FIELD
    private String status;      // NEW FIELD
    private boolean read;

    // Empty constructor required for Firebase
    public BookingsDataModel() { }

    // Constructor with new fields (default read = false)
    public BookingsDataModel(String userId, String username,
                             String spaceId, String spaceName,
                             String bookingDate, boolean read,
                             long createdAt, String status) {
        this.userId = userId;
        this.username = username;
        this.spaceId = spaceId;
        this.spaceName = spaceName;
        this.bookingDate = bookingDate;
        this.read = read;
        this.createdAt = createdAt;
        this.status = status;
    }

    // Full constructor
    public BookingsDataModel(String userId, String username,
                             String spaceId, String spaceName,
                             String spaceCategory, String bookingDate,
                             boolean read, long createdAt, String status) {
        this.userId = userId;
        this.username = username;
        this.spaceId = spaceId;
        this.spaceName = spaceName;
        this.spaceCategory = spaceCategory;
        this.bookingDate = bookingDate;
        this.read = read;
        this.createdAt = createdAt;
        this.status = status;
    }

    // Getters
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getSpaceId() { return spaceId; }
    public String getSpaceName() { return spaceName; }
    public String getSpaceCategory() { return spaceCategory; }
    public String getBookingDate() { return bookingDate; }
    public long getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public boolean isRead() { return read; }

    // Setters
    public void setUserId(String userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public void setSpaceName(String spaceName) { this.spaceName = spaceName; }
    public void setSpaceCategory(String spaceCategory) { this.spaceCategory = spaceCategory; }
    public void setBookingDate(String bookingDate) { this.bookingDate = bookingDate; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setStatus(String status) { this.status = status; }
    public void setRead(boolean read) { this.read = read; }
}