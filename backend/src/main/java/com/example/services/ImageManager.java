package com.example.services;

import java.util.ArrayDeque;
import java.awt.image.BufferedImage;
import java.util.Map;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import com.example.services.memory.ImageStore;

@Service
public abstract class ImageManager {
    // Reference to ImageStore (we're using composition here, not inheritance)
    // This means both DiskImageManager and CloudImageManager can use
    // ImageStore to store and retrieve images.
    protected ImageStore imageStore; // Composition: "has-a" ImageStore

    // Constructor that initializes ImageStore (via dependency injection)
    public ImageManager(ImageStore imageStore) {
        this.imageStore = imageStore;
    }

    // Shared method to store images in memory
    public void storeImage(String key, BufferedImage image) {
        imageStore.storeImage(key, image); // Store the image using ImageStore
    }

    // Abstract method for saving to storage (disk or cloud)
    public abstract void saveToStorage(String key);
}
