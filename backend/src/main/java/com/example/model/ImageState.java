package com.example.model;

import java.awt.image.BufferedImage;
import java.util.Stack;

public class ImageState {

    private BufferedImage currentImage;
    private BufferedImage originalImage;
    private BufferedImage referenceImage; // Image before enhancement adjustments
    private final Stack<BufferedImage> history = new Stack<>();
    private final Stack<BufferedImage> future = new Stack<>();

    public BufferedImage getCurrentImage() {
        return currentImage;
    }

    public void setCurrentImage(BufferedImage image) {
        this.currentImage = image;
    }

    public BufferedImage getOriginalImage() {
        return originalImage;
    }

    public void setOriginalImage(BufferedImage image) {
        this.originalImage = cloneImage(image);
        clearHistory();
        clearFuture();
    }
    
    public BufferedImage getReferenceImage() {
        return referenceImage;
    }
    
    public void storeReferenceImage(BufferedImage image) {
        this.referenceImage = image;
    }
    
    public void clearReferenceImage() {
        this.referenceImage = null;
    }

    public void pushHistory(BufferedImage image) {
        if (image != null) {
            history.push(cloneImage(image));
        }
    }

    public BufferedImage popHistory() {
        return !history.isEmpty() ? history.pop() : null;
    }

    public boolean hasHistory() {
        return !history.isEmpty();
    }

    public void clearHistory() {
        history.clear();
    }
    
    // Future stack methods for redo functionality
    public void pushFuture(BufferedImage image) {
        if (image != null) {
            future.push(cloneImage(image));
        }
    }

    public BufferedImage popFuture() {
        return !future.isEmpty() ? future.pop() : null;
    }

    public boolean hasFuture() {
        return !future.isEmpty();
    }

    public void clearFuture() {
        future.clear();
    }

    public BufferedImage cloneImage(BufferedImage img) {
        if (img == null)
            return null;
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        copy.getGraphics().drawImage(img, 0, 0, null);
        return copy;
    }
}
