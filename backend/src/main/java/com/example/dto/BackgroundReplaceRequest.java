package com.example.dto;

public class BackgroundReplaceRequest {
    private String type; // "color" or "image"
    private String color; // Hex color code when type is "color"
    private String imageId; // ID of the background image when type is "image"

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }
} 