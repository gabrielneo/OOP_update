package com.example.dto;

import org.springframework.web.multipart.MultipartFile;

public class ImageComplianceRequest {
    private MultipartFile image;

    public MultipartFile getImage() {
        return image;
    }

    public void setImage(MultipartFile image) {
        this.image = image;
    }
} 