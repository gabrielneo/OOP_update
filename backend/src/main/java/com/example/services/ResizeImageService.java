package com.example.services;

import com.example.dto.ResizeRequest;
import com.example.model.ImageState;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

@Service
public class ResizeImageService {
    public ResponseEntity<String> resize(ResizeRequest resize, ImageState state) {
        BufferedImage img = state.getCurrentImage();
        if (img == null) return ResponseEntity.badRequest().body("No image loaded.");

        // Validate resize parameters
        if (resize.getWidth() <= 0 || resize.getHeight() <= 0) {
            return ResponseEntity.badRequest().body("Invalid resize dimensions: width and height must be positive");
        }
        
        // Save current image to history before resizing
        state.pushHistory(cloneImage(img));
        
        // Clear future stack when making a new edit
        state.clearFuture();

        try {
            // Calculate dimensions to maintain aspect ratio
            int targetWidth = resize.getWidth();
            int targetHeight = resize.getHeight();
            
            // If maintain aspect ratio is true, calculate the correct dimensions
            if (resize.isMaintainAspectRatio()) {
                double aspectRatio = (double) img.getWidth() / img.getHeight();
                
                // When width is explicitly provided, adjust height
                if (resize.isWidthProvided()) {
                    targetHeight = (int) Math.round(targetWidth / aspectRatio);
                } 
                // When height is explicitly provided, adjust width
                else if (resize.isHeightProvided()) {
                    targetWidth = (int) Math.round(targetHeight * aspectRatio);
                }
            }
            
            // Create a new buffered image with the target dimensions
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, img.getType());
            Graphics2D g = resized.createGraphics();
            
            // Set rendering hints for better quality
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            g.drawImage(img, 0, 0, targetWidth, targetHeight, null);
            g.dispose();
            
            state.setCurrentImage(resized);
            return ResponseEntity.ok("Image resized successfully to " + targetWidth + "x" + targetHeight);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to resize image: " + e.getMessage());
        }
    }

    private BufferedImage cloneImage(BufferedImage img) {
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return copy;
    }
} 