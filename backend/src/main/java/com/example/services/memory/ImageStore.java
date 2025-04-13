package com.example.services.memory;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.model.ImageState;

// Centralize imageStore in a Service: Create a separate service that manages the in-memory image store (imageStore).
// Inject the imageStore into both DiskImageManager and CloudImageManager.
// Switch the storage strategy dynamically while keeping the image data intact
@Service
public class ImageStore {
    private Map<String, ImageState> imageStore = new HashMap<>();

    // Get image from store
    public ImageState getImageState(String key) {
        return imageStore.get(key);
    }

    // Get just the current BufferedImage
    public BufferedImage getImage(String key) {
        ImageState state = imageStore.get(key);
        return state != null ? state.getCurrentImage() : null;
    }

    // Store image in memory
    public void storeImage(String key, BufferedImage image) {
        // imageStore.put(key, image);
        ImageState state = new ImageState();
        state.setOriginalImage(image);
        state.setCurrentImage(state.cloneImage(image));
        imageStore.put(key, state);
    }

    // Store an entire ImageState directly
    public void storeImageState(String key, ImageState state) {
        imageStore.put(key, state);
    }

    public Map<String, ImageState> getImageStore() {
        return imageStore;
    }
}
// ImageStore holds the imageStore, which is shared between both storage
// strategies (DiskImageManager and CloudImageManager).
// storeImage and getImage allow both storage strategies to interact with the
// same image data.

// This approach allows you to keep the image data in memory while providing
// flexibility in how you store it (disk or cloud).
// Dont need to change the imageStore in either DiskImageManager or
// CloudImageManager, as they will both use the same ImageStore instance to
// access the image data.