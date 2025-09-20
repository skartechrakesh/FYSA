package com.skar.fysa;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Model class representing a booking.
 * Stored in Firebase under: bookings_by_users/<userId>/<bookingId>
 */
public class Booking implements Parcelable {

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
    private double latitude;   // ✅ New field
    private double longitude;  // ✅ New field

    // Required empty constructor for Firebase deserialization
    public Booking() {
    }

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

    // --- Parcelable implementation ---
    protected Booking(Parcel in) {
        bookingId = in.readString();
        userId = in.readString();
        ownerId = in.readString();
        spaceId = in.readString();
        spaceName = in.readString();
        spaceType = in.readString();
        bookingDate = in.readString();
        status = in.readString();
        username = in.readString();
        createdAt = in.readLong();
        latitude = in.readDouble();
        longitude = in.readDouble();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(bookingId);
        dest.writeString(userId);
        dest.writeString(ownerId);
        dest.writeString(spaceId);
        dest.writeString(spaceName);
        dest.writeString(spaceType);
        dest.writeString(bookingDate);
        dest.writeString(status);
        dest.writeString(username);
        dest.writeLong(createdAt);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Booking> CREATOR = new Creator<Booking>() {
        @Override
        public Booking createFromParcel(Parcel in) {
            return new Booking(in);
        }

        @Override
        public Booking[] newArray(int size) {
            return new Booking[size];
        }
    };

    // --- Getters and Setters ---
    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public String getSpaceType() {
        return spaceType;
    }

    public void setSpaceType(String spaceType) {
        this.spaceType = spaceType;
    }

    public String getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(String bookingDate) {
        this.bookingDate = bookingDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
