package com.example.dto;

import org.springframework.web.multipart.MultipartFile;

public class ImageComplianceRequest {
    private MultipartFile image;
    private boolean hasReplacedBackground;
    private String backgroundColor;

    public MultipartFile getImage() {
        return image;
    }

    public void setImage(MultipartFile image) {
        this.image = image;
    }
    
    public boolean isHasReplacedBackground() {
        return hasReplacedBackground;
    }
    
    public void setHasReplacedBackground(boolean hasReplacedBackground) {
        this.hasReplacedBackground = hasReplacedBackground;
    }
    
    public String getBackgroundColor() {
        return backgroundColor;
    }
    
    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
} 