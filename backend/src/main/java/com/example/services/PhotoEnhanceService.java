package com.example.services;

import com.example.dto.PhotoEnhanceRequest;
import com.example.model.ImageState;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;

@Service
public class PhotoEnhanceService {

    public ResponseEntity<?> enhancePhoto(PhotoEnhanceRequest request, ImageState state) {
        try {
            BufferedImage currentImage = state.getCurrentImage();
            BufferedImage referenceImage = state.getReferenceImage();

            // If no reference image stored, use the current image
            if (referenceImage == null) {
                referenceImage = currentImage;
            }

            if (currentImage == null) {
                return ResponseEntity.badRequest().body("No image loaded");
            }

            // First save current state to history for undo
            state.pushHistory(currentImage);

            // Always apply enhancements to the reference image to prevent accumulation
            BufferedImage enhancedImage = applyBrightnessContrast(
                    state.cloneImage(referenceImage),
                    request.getBrightness(),
                    request.getContrast());

            // Update current image in state
            state.setCurrentImage(enhancedImage);

            // Clear the reference image after applying enhancements
            state.clearReferenceImage();

            return ResponseEntity.ok("Photo enhancement applied successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error enhancing photo: " + e.getMessage());
        }
    }

    private BufferedImage applyBrightnessContrast(BufferedImage image, float brightnessValue, float contrastValue) {
        // For brightness:
        // 0 is normal,
        // negative values (-100 to 0) darken the image
        // positive values (0 to 100) brighten the image
        float brightness = brightnessValue / 100.0f;

        // For contrast:
        // 0 is normal,
        // negative values (-100 to 0) decrease contrast
        // positive values (0 to 100) increase contrast
        float contrast = 1.0f + (contrastValue / 100.0f);

        if (contrastValue < 0) {
            // Scale differently for negative contrast to make it more effective
            contrast = Math.max(0.1f, 1.0f + (contrastValue / 100.0f));
        }

        // Create a compatible image type
        BufferedImage output = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        // Draw the original image to ensure type compatibility
        Graphics2D g2d = output.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        // For simplicity and reliability, use the manual method for all cases
        return manualEnhancement(output, brightness, contrast);
    }

    private BufferedImage manualEnhancement(BufferedImage image, float brightness, float contrast) {
        BufferedImage output = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        // Manually adjust each pixel
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);

                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                // Apply brightness and contrast
                red = adjustComponent(red, brightness, contrast);
                green = adjustComponent(green, brightness, contrast);
                blue = adjustComponent(blue, brightness, contrast);

                // Recombine
                rgb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                output.setRGB(x, y, rgb);
            }
        }

        return output;
    }

    private int adjustComponent(int component, float brightness, float contrast) {
        // Apply brightness adjustment first
        // For brightness < 0, multiply by (1+brightness) to darken
        // For brightness > 0, add (brightness*255) to brighten
        float adjusted = component;

        if (brightness < 0) {
            // Darken by scaling down (multiplier between 0 and 1)
            adjusted = adjusted * (1.0f + brightness);
        } else if (brightness > 0) {
            // Brighten by adding (brightness goes from 0 to 1)
            adjusted = adjusted + (brightness * (255 - adjusted));
        }

        // Now apply contrast adjustment
        // First normalize to -0.5 to 0.5 range
        adjusted = (adjusted / 255.0f) - 0.5f;

        // Apply contrast factor
        adjusted = adjusted * contrast;

        // Convert back to 0-255 range
        adjusted = (adjusted + 0.5f) * 255.0f;

        // Clamp to valid range
        return Math.max(0, Math.min(255, Math.round(adjusted)));
    }

    // Method to create a preview without changing the state
    public BufferedImage createPreview(PhotoEnhanceRequest request, BufferedImage currentImage) {
        // We always need to apply enhancements to the original image
        BufferedImage originalImage = currentImage;

        if (originalImage == null) {
            return null;
        }

        // Always apply enhancements to the original image to prevent accumulation
        return applyBrightnessContrast(
                originalImage,
                request.getBrightness(),
                request.getContrast());
    }
}