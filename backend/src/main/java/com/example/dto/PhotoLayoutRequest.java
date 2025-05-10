package com.example.dto;

public class PhotoLayoutRequest {
    private int borderSize;
    private int rows;
    private int cols;
    private int originalWidth;
    private int originalHeight;
    private double finalWidth;
    private double finalHeight;
    private boolean useOriginalImage;
    private boolean isFirstLayout;
    private int layoutCount;

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

    public double getFinalWidth() {
        return finalWidth;
    }

    public void setFinalWidth(double finalWidth) {
        this.finalWidth = finalWidth;
    }

    public double getFinalHeight() {
        return finalHeight;
    }

    public void setFinalHeight(double finalHeight) {
        this.finalHeight = finalHeight;
    }

    public boolean isFirstLayout() {
        return isFirstLayout;
    }

    public void setFirstLayout(boolean firstLayout) {
        isFirstLayout = firstLayout;
    }

    public int getLayoutCount() {
        return layoutCount;
    }

    public void setLayoutCount(int layoutCount) {
        this.layoutCount = layoutCount;
    }
}