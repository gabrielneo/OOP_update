package com.example.dto;

public class PhotoEnhanceRequest {
    private float brightness;
    private float contrast;
    private boolean firstAdjustment = true;
    private boolean previewInProgress = false;

    public PhotoEnhanceRequest() {
        // Default values (no change)
        this.brightness = 0.0f;
        this.contrast = 0.0f;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public float getContrast() {
        return contrast;
    }

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }
    
    public boolean isFirstAdjustment() {
        return firstAdjustment;
    }
    
    public void setFirstAdjustment(boolean firstAdjustment) {
        this.firstAdjustment = firstAdjustment;
    }
    
    public boolean isPreviewInProgress() {
        return previewInProgress;
    }
    
    public void setPreviewInProgress(boolean previewInProgress) {
        this.previewInProgress = previewInProgress;
    }
} 