package com.skar.fysa;

import java.io.Serializable;

/**
 * Model class representing a booking.
 * Stored in Firebase under: bookings_by_users/<userId>/<bookingId>
 */
public class Booking implements Serializable { // ✅ Added Serializable interface

    private String bookingId;
    private String userId;
    private String ownerId;
    private String spaceId;
    private String spaceName;
    private String spaceType;
    private String bookingDate;
    private String status;
    private String username;
    private long createdAt;
    private double latitude; // ✅ New field
    private double longitude; // ✅ New field

    // Required empty constructor for Firebase deserialization
    public Booking() {}

    // Full constructor to match all fields
    public Booking(String bookingId, String userId, String ownerId,
                   String spaceId, String spaceName, String spaceType,
                   String bookingDate, String status, String username,
                   long createdAt, double latitude, double longitude) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.ownerId = ownerId;
        this.spaceId = spaceId;
        this.spaceName = spaceName;
        this.spaceType = spaceType;
        this.bookingDate = bookingDate;
        this.status = status;
        this.username = username;
        this.createdAt = createdAt;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // --- Getters ---
    public String getBookingId() { return bookingId; }
    public String getUserId() { return userId; }
    public String getOwnerId() { return ownerId; }
    public String getSpaceId() { return spaceId; }
    public String getSpaceName() { return spaceName; }
    public String getSpaceType() { return spaceType; }
    public String getBookingDate() { return bookingDate; }
    public String getStatus() { return status; }
    public String getUsername() { return username; }
    public long getCreatedAt() { return createdAt; }
    public double getLatitude() { return latitude; } // ✅ Getter
    public double getLongitude() { return longitude; } // ✅ Getter

    // --- Setters ---
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public void setSpaceName(String spaceName) { this.spaceName = spaceName; }
    public void setSpaceType(String spaceType) { this.spaceType = spaceType; }
    public void setBookingDate(String bookingDate) { this.bookingDate = bookingDate; }
    public void setStatus(String status) { this.status = status; }
    public void setUsername(String username) { this.username = username; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setLatitude(double latitude) { this.latitude = latitude; } // ✅ Setter
    public void setLongitude(double longitude) { this.longitude = longitude; } // ✅ Setter
}