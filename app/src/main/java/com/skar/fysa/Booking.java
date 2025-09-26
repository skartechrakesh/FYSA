package com.skar.fysa;

import android.os.Parcel;
import android.os.Parcelable;

public class Booking implements Parcelable {

    private String bookingId;
    private String bookedBy;           // UID of the user who booked
    private String bookedByUserName;   // ✅ Username of the user who booked
    private String ownerId;
    private String spaceId;
    private String spaceName;
    private String spaceAddress;
    private String spaceType;
    private String status;             // "Pending" or "Accepted"
    private long createdAt;
    private double latitude;
    private double longitude;
    private String userContact;        // Optional, contact of the user

    public Booking() {}

    public Booking(String bookingId, String bookedBy, String bookedByUserName,
                   String ownerId, String spaceId, String spaceName,
                   String spaceAddress, String spaceType, String status,
                   long createdAt, double latitude, double longitude,
                   String userContact) {
        this.bookingId = bookingId;
        this.bookedBy = bookedBy;
        this.bookedByUserName = bookedByUserName;
        this.ownerId = ownerId;
        this.spaceId = spaceId;
        this.spaceName = spaceName;
        this.spaceAddress = spaceAddress;
        this.spaceType = spaceType;
        this.status = status;
        this.createdAt = createdAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.userContact = userContact;
    }

    // Parcelable implementation
    protected Booking(Parcel in) {
        bookingId = in.readString();
        bookedBy = in.readString();
        bookedByUserName = in.readString();
        ownerId = in.readString();
        spaceId = in.readString();
        spaceName = in.readString();
        spaceAddress = in.readString();
        spaceType = in.readString();
        status = in.readString();
        createdAt = in.readLong();
        latitude = in.readDouble();
        longitude = in.readDouble();
        userContact = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(bookingId);
        dest.writeString(bookedBy);
        dest.writeString(bookedByUserName);
        dest.writeString(ownerId);
        dest.writeString(spaceId);
        dest.writeString(spaceName);
        dest.writeString(spaceAddress);
        dest.writeString(spaceType);
        dest.writeString(status);
        dest.writeLong(createdAt);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeString(userContact);
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

    // --- Getters & Setters ---
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getBookedBy() { return bookedBy; }
    public void setBookedBy(String bookedBy) { this.bookedBy = bookedBy; }

    public String getBookedByUserName() { return bookedByUserName; }
    public void setBookedByUserName(String bookedByUserName) { this.bookedByUserName = bookedByUserName; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getSpaceId() { return spaceId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }

    public String getSpaceName() { return spaceName; }
    public void setSpaceName(String spaceName) { this.spaceName = spaceName; }

    public String getSpaceAddress() { return spaceAddress; }
    public void setSpaceAddress(String spaceAddress) { this.spaceAddress = spaceAddress; }

    public String getSpaceType() { return spaceType; }
    public void setSpaceType(String spaceType) { this.spaceType = spaceType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getUserContact() { return userContact; }
    public void setUserContact(String userContact) { this.userContact = userContact; }
}
