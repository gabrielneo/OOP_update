package com.example.model;

import java.util.Date;

/**
 * Represents a Google Drive file or folder.
 * This is a POJO (Plain Old Java Object) used for transferring file information
 * between the service layer and the controller.
 */
public class DriveFile {
    private String id;
    private String name;
    private String mimeType;
    private Date modifiedTime;
    private String webViewLink;
    private long size;

    // Default constructor required for serialization/deserialization
    public DriveFile() {
    }

    public DriveFile(String id, String name, String mimeType, Date modifiedTime, String webViewLink, long size) {
        this.id = id;
        this.name = name;
        this.mimeType = mimeType;
        this.modifiedTime = modifiedTime;
        this.webViewLink = webViewLink;
        this.size = size;
    }

    // Getters and setters (encapsulation)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Date getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(Date modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public String getWebViewLink() {
        return webViewLink;
    }

    public void setWebViewLink(String webViewLink) {
        this.webViewLink = webViewLink;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    // Helper method to check if the file is a folder
    public boolean isFolder() {
        return "application/vnd.google-apps.folder".equals(mimeType);
    }

    // Helper method to check if the file is an image
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
} 