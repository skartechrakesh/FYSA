package com.skar.fysa;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.io.Serializable;

/**
 * Model class representing a rental space (Hostel, PG, Room, House, Flat).
 * Stored in Firebase under: spaces/<spaceId>.
 * Each space has a "category" field for filtering.
 */
@IgnoreExtraProperties
public class Space implements Serializable {

    private String id;          // Firebase push key
    private String name;        // Space name
    private String category;    // Hostel / PG / Room / House / Flat ✅ replaces type
    private double latitude;    // Latitude
    private double longitude;   // Longitude
    private String ownerId;     // UID of the owner
    private String ownerName;   // Owner name
    private String address;     // Address
    private String contact;     // Contact (phone/email)
    private String email;       // Owner email
    private String description; // Description
    private Boolean vacancy = true; // Vacancy status (default = true)

    @Exclude
    private Integer distanceMeters; // Calculated dynamically, not stored in Firebase

    // --- Required empty constructor for Firebase ---
    public Space() {}

    // --- Full constructor ---
    public Space(String id, String name, String category,
                 double latitude, double longitude,
                 String ownerId, String ownerName,
                 String address, String contact,
                 String email, Boolean vacancy) {
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
        this.vacancy = vacancy != null ? vacancy : true;
    }

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
    public Boolean isVacancy() { return vacancy; }

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
    public void setVacancy(Boolean vacancy) { this.vacancy = vacancy != null ? vacancy : true; }

    @Exclude
    public void setDistanceMeters(Integer distanceMeters) { this.distanceMeters = distanceMeters; }
}
