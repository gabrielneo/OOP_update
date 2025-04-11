package com.example.dto;

public class ResizeRequest {
    private int width;
    private int height;
    private boolean maintainAspectRatio;
    private boolean widthProvided;
    private boolean heightProvided;

    // Getters and setters
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isMaintainAspectRatio() {
        return maintainAspectRatio;
    }

    public void setMaintainAspectRatio(boolean maintainAspectRatio) {
        this.maintainAspectRatio = maintainAspectRatio;
    }

    public boolean isWidthProvided() {
        return widthProvided;
    }

    public void setWidthProvided(boolean widthProvided) {
        this.widthProvided = widthProvided;
    }

    public boolean isHeightProvided() {
        return heightProvided;
    }

    public void setHeightProvided(boolean heightProvided) {
        this.heightProvided = heightProvided;
    }
} 