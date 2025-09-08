package com.skar.fysa;

/**
 * Model class representing a booking.
 * Stored in Firebase under: bookings_by_users/<userId>/<bookingId>
 */
public class Booking {

    private String bookingId;
    private String userId;
    private String ownerId;
    private String spaceId;
    private String spaceName;
    private String spaceType;
    private String bookingDate;
    private String status;
    private String username;   // ✅ Username of the user who booked
    private long createdAt;    // ✅ New field (epoch timestamp in millis)

    // Required empty constructor for Firebase deserialization
    public Booking() {}

    public Booking(String bookingId, String userId, String ownerId,
                   String spaceId, String spaceName, String spaceType,
                   String bookingDate, String status, String username) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.ownerId = ownerId;
        this.spaceId = spaceId;
        this.spaceName = spaceName;
        this.spaceType = spaceType;
        this.bookingDate = bookingDate;
        this.status = status;
        this.username = username;
        this.createdAt = System.currentTimeMillis(); // ✅ Auto-set when created
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
    public long getCreatedAt() { return createdAt; } // ✅ Getter

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
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; } // ✅ Setter
}
