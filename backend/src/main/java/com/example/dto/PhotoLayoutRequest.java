package com.example.dto;

public class PhotoLayoutRequest {
    private int borderSize;
    private int rows;
    private int cols;
    private int originalWidth;
    private int originalHeight;
    private int finalWidth;
    private int finalHeight;

    public int getBorderSize() {
        return borderSize;
    }

    public void setBorderSize(int borderSize) {
        this.borderSize = borderSize;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getCols() {
        return cols;
    }

    public void setCols(int cols) {
        this.cols = cols;
    }

    public int getOriginalWidth() {
        return originalWidth;
    }

    public void setOriginalWidth(int originalWidth) {
        this.originalWidth = originalWidth;
    }

    public int getOriginalHeight() {
        return originalHeight;
    }

    public void setOriginalHeight(int originalHeight) {
        this.originalHeight = originalHeight;
    }

    public int getFinalWidth() {
        return finalWidth;
    }

    public void setFinalWidth(int finalWidth) {
        this.finalWidth = finalWidth;
    }

    public int getFinalHeight() {
        return finalHeight;
    }

    public void setFinalHeight(int finalHeight) {
        this.finalHeight = finalHeight;
    }
} 