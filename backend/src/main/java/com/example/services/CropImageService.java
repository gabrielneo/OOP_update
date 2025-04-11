package com.example.services;

import com.example.dto.CropRequest;
import com.example.model.ImageState;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

@Service
public class CropImageService {
    public ResponseEntity<String> crop(CropRequest crop, ImageState state) {
        BufferedImage img = state.getCurrentImage();
        if (img == null) return ResponseEntity.badRequest().body("No image loaded.");

        // Validate crop parameters
        if (crop.getWidth() <= 0 || crop.getHeight() <= 0) {
            return ResponseEntity.badRequest().body("Invalid crop dimensions: width and height must be positive");
        }
        
        // Save current image to history before cropping
        state.pushHistory(cloneImage(img));
        
        // Clear future stack when making a new edit
        state.clearFuture();

        // Ensure coordinates and dimensions are valid
        int x = Math.max(0, crop.getX());
        int y = Math.max(0, crop.getY());
        int width = Math.min(img.getWidth() - x, Math.max(1, crop.getWidth()));
        int height = Math.min(img.getHeight() - y, Math.max(1, crop.getHeight()));
        
        // Additional safety check to prevent RasterFormatException
        if (x + width > img.getWidth()) {
            width = img.getWidth() - x;
        }
        if (y + height > img.getHeight()) {
            height = img.getHeight() - y;
        }
        
        try {
            BufferedImage cropped = img.getSubimage(x, y, width, height);
            state.setCurrentImage(cropped);
            return ResponseEntity.ok("Image cropped successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to crop image: " + e.getMessage());
        }
    }

    private BufferedImage cloneImage(BufferedImage img) {
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics g = copy.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return copy;
    }
}