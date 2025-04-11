package com.example.services;

import com.example.dto.PhotoLayoutRequest;
import com.example.model.ImageState;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

@Service
public class PhotoLayoutService {

    public ResponseEntity<String> createLayout(PhotoLayoutRequest request, ImageState state) {
        // Use original image instead of current image to prevent recursive grid creation
        BufferedImage originalImg = state.getOriginalImage();
        if (originalImg == null) return ResponseEntity.badRequest().body("No image loaded.");
        
        try {
            // Save current image to history before applying layout
            BufferedImage currentImg = state.getCurrentImage();
            state.pushHistory(state.cloneImage(currentImg));
            
            // Clear future stack when making a new edit
            state.clearFuture();
            
            // Log the dimensions of the original image
            System.out.println("Original image dimensions: " + originalImg.getWidth() + "x" + originalImg.getHeight() + " pixels");
            
            // Convert mm to pixels for processing (using 3.779528 as mm to px conversion)
            final double mmToPx = 3.779528;
            
            // Calculate border size in pixels
            int borderSizePx = (int) Math.round(request.getBorderSize() * mmToPx);
            System.out.println("Border size: " + request.getBorderSize() + " mm (" + borderSizePx + " pixels)");
            
            // Create an image with the border
            BufferedImage imageWithBorder = addBorder(originalImg, borderSizePx, Color.WHITE);
            System.out.println("Image with border dimensions: " + imageWithBorder.getWidth() + "x" + imageWithBorder.getHeight() + " pixels");
            
            // Log the requested layout grid
            System.out.println("Requested layout: " + request.getCols() + "x" + request.getRows() + 
                             " (Width: " + request.getFinalWidth() + "mm, Height: " + request.getFinalHeight() + "mm)");
            
            // Create the final layout
            BufferedImage layoutImage = createGridLayout(
                imageWithBorder, 
                request.getRows(), 
                request.getCols(), 
                request.getFinalWidth(),
                request.getFinalHeight()
            );
            
            System.out.println("Final layout image dimensions: " + layoutImage.getWidth() + "x" + layoutImage.getHeight() + " pixels");
            
            // Update the current image
            state.setCurrentImage(layoutImage);
            
            return ResponseEntity.ok("Photo layout created successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to create layout: " + e.getMessage());
        }
    }
    
    private BufferedImage addBorder(BufferedImage source, int borderSize, Color color) {
        // If border size is 0, return the original image without a border
        if (borderSize <= 0) {
            System.out.println("No border requested, using original image dimensions");
            return source;
        }
        
        int width = source.getWidth();
        int height = source.getHeight();
        
        // Create a new image with border
        BufferedImage borderedImage = new BufferedImage(
            width + (2 * borderSize),
            height + (2 * borderSize),
            BufferedImage.TYPE_INT_RGB
        );
        
        // Create graphics and fill with border color
        Graphics2D g2d = borderedImage.createGraphics();
        g2d.setColor(color);
        g2d.fillRect(0, 0, borderedImage.getWidth(), borderedImage.getHeight());
        
        // Draw the original image in the center
        g2d.drawImage(source, borderSize, borderSize, null);
        g2d.dispose();
        
        return borderedImage;
    }
    
    private BufferedImage createGridLayout(BufferedImage sourceImage, int rows, int cols, int finalWidthMm, int finalHeightMm) throws IOException {
        // Convert mm to pixels for final size
        final double mmToPx = 3.779528;
        int finalWidthPx = (int) Math.round(finalWidthMm * mmToPx);
        int finalHeightPx = (int) Math.round(finalHeightMm * mmToPx);
        
        // Calculate individual image dimensions
        int imageWidth = sourceImage.getWidth();
        int imageHeight = sourceImage.getHeight();
        
        // Log dimensions for debugging
        System.out.println("Creating grid layout with: " + rows + " rows, " + cols + " columns");
        System.out.println("Source image dimensions: " + imageWidth + "x" + imageHeight + " pixels");
        System.out.println("Final canvas dimensions: " + finalWidthPx + "x" + finalHeightPx + " pixels");
        
        // Create a blank white canvas of the required size
        BufferedImage layoutImage = new BufferedImage(
            finalWidthPx,
            finalHeightPx,
            BufferedImage.TYPE_INT_RGB
        );
        
        // Fill the canvas with white
        Graphics2D g2d = layoutImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, finalWidthPx, finalHeightPx);
        
        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        // Draw the source image in a grid pattern
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = col * imageWidth;
                int y = row * imageHeight;
                
                // Draw the image
                g2d.drawImage(sourceImage, x, y, null);
            }
        }
        
        g2d.dispose();
        return layoutImage;
    }
}