package com.example.model;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class ImageState {
    private BufferedImage currentImage;
    private BufferedImage originalImage;
    private BufferedImage referenceImage; // Image before layout changes
    private Stack<BufferedImage> history = new Stack<>();
    private Stack<BufferedImage> future = new Stack<>();
    private final Map<String, Object> metadata = new HashMap<>();
    
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
    
    public void setReferenceImage(BufferedImage referenceImage) {
        this.referenceImage = cloneImage(referenceImage);
    }
    
    public void storeReferenceImage(BufferedImage image) {
        this.referenceImage = cloneImage(image);
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
    
    public BufferedImage peekHistory() {
        return !history.isEmpty() ? history.peek() : null;
    }
    
    public BufferedImage getPreLayoutImage() {
        if (!history.isEmpty()) {
            return cloneImage(history.peek());
        }
        return cloneImage(currentImage);
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
    
    public BufferedImage undo() {
        if (!history.isEmpty()) {
            BufferedImage current = history.pop();
            if (!history.isEmpty()) {
                BufferedImage previous = history.peek();
                setCurrentImage(cloneImage(previous));
                return previous;
            }
        }
        return null;
    }
    
    // Metadata handling
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }
    
    public void clearMetadata() {
        metadata.clear();
    }
}

