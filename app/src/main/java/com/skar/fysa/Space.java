package com.skar.fysa;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Space implements Parcelable {

    private String id;
    private String name;
    private String category;
    private double latitude;
    private double longitude;
    private String ownerId;
    private String ownerName;
    private String address;
    private String contact;
    private String email;
    private String description;
    private String spaceFeatures; // NEW
    private Boolean active = true;

    @Exclude
    private Integer distanceMeters;

    public Space() {}

    public Space(String id, String name, String category,
                 double latitude, double longitude,
                 String ownerId, String ownerName,
                 String address, String contact,
                 String email, Boolean active,
                 String spaceFeatures) { // NEW
        this.id = id;
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.address = address;
        this.contact = contact;
        this.email = email;
        this.active = active != null ? active : true;
        this.spaceFeatures = spaceFeatures; // NEW
    }

    protected Space(Parcel in) {
        id = in.readString();
        name = in.readString();
        category = in.readString();
        latitude = in.readDouble();
        longitude = in.readDouble();
        ownerId = in.readString();
        ownerName = in.readString();
        address = in.readString();
        contact = in.readString();
        email = in.readString();
        description = in.readString();
        spaceFeatures = in.readString(); // NEW
        byte tmpActive = in.readByte();
        active = tmpActive == 0 ? null : tmpActive == 1;
        if (in.readByte() == 1) distanceMeters = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(category);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeString(ownerId);
        dest.writeString(ownerName);
        dest.writeString(address);
        dest.writeString(contact);
        dest.writeString(email);
        dest.writeString(description);
        dest.writeString(spaceFeatures); // NEW
        dest.writeByte((byte) (active == null ? 0 : active ? 1 : 2));
        if (distanceMeters != null) {
            dest.writeByte((byte) 1);
            dest.writeInt(distanceMeters);
        } else dest.writeByte((byte) 0);
    }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<Space> CREATOR = new Creator<Space>() {
        @Override
        public Space createFromParcel(Parcel in) { return new Space(in); }
        @Override
        public Space[] newArray(int size) { return new Space[size]; }
    };

    // --- Getters ---
    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public String getAddress() { return address; }
    public String getContact() { return contact; }
    public String getEmail() { return email; }
    public String getDescription() { return description; }
    public String getSpaceFeatures() { return spaceFeatures; } // NEW
    public Boolean isActive() { return active; }

    @Exclude
    public Integer getDistanceMeters() { return distanceMeters; }

    // --- Setters ---
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public void setAddress(String address) { this.address = address; }
    public void setContact(String contact) { this.contact = contact; }
    public void setEmail(String email) { this.email = email; }
    public void setDescription(String description) { this.description = description; }
    public void setSpaceFeatures(String spaceFeatures) { this.spaceFeatures = spaceFeatures; } // NEW
    public void setActive(Boolean active) { this.active = active != null ? active : true; }

    @Exclude
    public void setDistanceMeters(Integer distanceMeters) { this.distanceMeters = distanceMeters; }
}
