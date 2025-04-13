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
        try {
            BufferedImage originalImage = state.getOriginalImage();
            BufferedImage currentImage = state.getCurrentImage();

            if (originalImage == null || currentImage == null) {
                return ResponseEntity.badRequest().body("No image found.");
            }

            // For the first layout application, always use the current edited image
            if (request.isFirstLayout()) {
                // Use the current edited image directly
                BufferedImage imageToUse = state.cloneImage(currentImage);
                System.out.println("First layout application - using latest edited image: " +
                        imageToUse.getWidth() + "x" + imageToUse.getHeight() + " pixels");

                // Process the layout with the latest edited image
                return processLayout(imageToUse, request, state);
            }

            // For subsequent layouts, check if we have a previous layout in history
            if (state.hasHistory()) {
                // Get the dimensions of the current image
                int currentWidth = currentImage.getWidth();
                int currentHeight = currentImage.getHeight();

                // If the current image is significantly larger than the original, it's likely a
                // grid layout
                boolean isGridLayout = currentWidth > originalImage.getWidth() * 1.5 ||
                        currentHeight > originalImage.getHeight() * 1.5;

                if (isGridLayout) {
                    return ResponseEntity.badRequest()
                            .body("You already have a grid layout applied. Please click the undo button to revert to single image before applying a new grid layout.");
                }
            }

            // Use the current edited single image for the layout
            BufferedImage imageToUse = state.cloneImage(currentImage);
            return processLayout(imageToUse, request, state);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to create layout: " + e.getMessage());
        }
    }

    private ResponseEntity<String> processLayout(BufferedImage imageToUse, PhotoLayoutRequest request,
            ImageState state) {
        try {
            // Log the dimensions of the image being used
            System.out.println(
                    "Using current edited image: " + imageToUse.getWidth() + "x" + imageToUse.getHeight() + " pixels");

            // Convert mm to pixels for processing (using 3.779528 as mm to px conversion)
            final double mmToPx = 3.779528;

            // Calculate border size in pixels
            int borderSizePx = (int) Math.round(request.getBorderSize() * mmToPx);
            System.out.println("Border size: " + request.getBorderSize() + " mm (" + borderSizePx + " pixels)");

            // Create an image with the border
            BufferedImage imageWithBorder = addBorder(imageToUse, borderSizePx, Color.WHITE);
            System.out.println("Image with border dimensions: " + imageWithBorder.getWidth() + "x"
                    + imageWithBorder.getHeight() + " pixels");

            // Log the requested layout grid
            System.out.println("Creating new layout: " + request.getCols() + "x" + request.getRows() +
                    " (Width: " + request.getFinalWidth() + "mm, Height: " + request.getFinalHeight() + "mm)");

            // Create the final layout
            BufferedImage layoutImage = createGridLayout(
                    imageWithBorder,
                    request.getRows(),
                    request.getCols(),
                    request.getFinalWidth(),
                    request.getFinalHeight());

            System.out.println("Final layout image dimensions: " + layoutImage.getWidth() + "x"
                    + layoutImage.getHeight() + " pixels");

            // Save the pre-layout state to history
            state.pushHistory(imageToUse);

            // Clear future stack when making a new edit
            state.clearFuture();

            // Update with the new layout
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
                BufferedImage.TYPE_INT_RGB);

        // Create graphics and fill with border color
        Graphics2D g2d = borderedImage.createGraphics();
        g2d.setColor(color);
        g2d.fillRect(0, 0, borderedImage.getWidth(), borderedImage.getHeight());

        // Draw the original image in the center
        g2d.drawImage(source, borderSize, borderSize, null);
        g2d.dispose();

        return borderedImage;
    }

    private BufferedImage createGridLayout(BufferedImage image, int rows, int cols, double finalWidth,
            double finalHeight) {
        // Convert mm to pixels for processing (using 3.779528 as mm to px conversion)
        final double mmToPx = 3.779528;

        // Calculate final dimensions in pixels
        int finalWidthPx = (int) Math.round(finalWidth * mmToPx);
        int finalHeightPx = (int) Math.round(finalHeight * mmToPx);

        // Create the final image
        BufferedImage finalImage = new BufferedImage(finalWidthPx, finalHeightPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = finalImage.createGraphics();

        // Set background to white
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, finalWidthPx, finalHeightPx);

        // Calculate cell dimensions
        int cellWidth = finalWidthPx / cols;
        int cellHeight = finalHeightPx / rows;

        // Draw each cell
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = col * cellWidth;
                int y = row * cellHeight;
                g2d.drawImage(image, x, y, cellWidth, cellHeight, null);
            }
        }

        g2d.dispose();
        return finalImage;
    }

    // Add method to reset layout state
    public void resetLayoutState(ImageState state) {
        // Get back to the pre-layout state by undoing once
        if (state.hasHistory()) {
            System.out.println("Restoring to pre-layout state");
            state.undo();
        }
    }
}