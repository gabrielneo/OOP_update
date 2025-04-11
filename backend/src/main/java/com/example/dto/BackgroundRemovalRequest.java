package com.example.dto;

public class BackgroundRemovalRequest {
    private String method; // "auto" or "manual"

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
} 