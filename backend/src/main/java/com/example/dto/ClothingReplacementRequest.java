package com.example.dto;

public class ClothingReplacementRequest {
    private String type;
    private String clothingType;

    public ClothingReplacementRequest() {
        this.type = "clothes";
        this.clothingType = "formal"; // Default to formal suit
    }
    
    public ClothingReplacementRequest(String type, String clothingType) {
        this.type = type != null ? type : "clothes";
        this.clothingType = clothingType != null ? clothingType : "formal";
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getClothingType() {
        return clothingType;
    }
    
    public void setClothingType(String clothingType) {
        this.clothingType = clothingType;
    }
}