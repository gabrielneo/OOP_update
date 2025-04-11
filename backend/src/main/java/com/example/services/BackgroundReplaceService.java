package com.example.services;

import com.example.dto.BackgroundReplaceRequest;
import com.example.model.ImageState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BackgroundReplaceService {
    
    private final static String BACKGROUNDS_DIR = "uploaded_backgrounds";
    private final Map<String, Path> backgroundImages = new HashMap<>();
    
    // Track whether the background has been replaced
    private boolean hasReplacedBackground = false;
    // Store the original image with transparency for subsequent replacements
    private BufferedImage originalTransparentImage = null;
    
    @Autowired
    private ResourceLoader resourceLoader;

    public ResponseEntity<String> replaceBackground(BackgroundReplaceRequest request, ImageState state) {
        BufferedImage img = state.getCurrentImage();
        if (img == null) return ResponseEntity.badRequest().body("No image loaded.");
        
        try {
            // Save current image to history before replacing background
            state.pushHistory(state.cloneImage(img));
            
            // Clear future stack when making a new edit
            state.clearFuture();
            
            // Create a new image with the same dimensions that will contain the result
            BufferedImage result;
            
            // If we're replacing an already-replaced background, use the original transparent image
            // if possible, otherwise just use the current image
            BufferedImage sourceImage = img;
            
            if (hasReplacedBackground && originalTransparentImage != null) {
                sourceImage = originalTransparentImage;
            } else {
                // If this is the first replacement, store the current image as the original with transparency
                originalTransparentImage = cloneImageWithTransparency(img);
                hasReplacedBackground = true;
            }
            
            // Process according to request type
            if ("color".equals(request.getType())) {
                result = replaceWithColor(sourceImage, request.getColor());
            } else if ("image".equals(request.getType())) {
                if (request.getImageId() == null) {
                    return ResponseEntity.badRequest().body("Background image ID is required");
                }
                
                result = replaceWithImage(sourceImage, request.getImageId());
                if (result == null) {
                    return ResponseEntity.badRequest().body("Background image not found");
                }
            } else {
                return ResponseEntity.badRequest().body("Invalid background replacement type");
            }
            
            // Update the current image
            state.setCurrentImage(result);
            
            return ResponseEntity.ok("Background replaced successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to replace background: " + e.getMessage());
        }
    }
    
    // Method to clone an image, preserving transparency
    private BufferedImage cloneImageWithTransparency(BufferedImage source) {
        BufferedImage clone = new BufferedImage(
                source.getWidth(), 
                source.getHeight(), 
                BufferedImage.TYPE_INT_ARGB);
                
        Graphics2D g = clone.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        
        return clone;
    }
    
    // Method to reset the background state when performing operations that might change the base image
    public void resetBackgroundState() {
        hasReplacedBackground = false;
        originalTransparentImage = null;
    }
    
    // Method to handle uploading a background image
    public ResponseEntity<Map<String, String>> uploadBackgroundImage(MultipartFile file) {
        try {
            // Create directory if it doesn't exist
            Path directory = Paths.get(BACKGROUNDS_DIR);
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
            
            // Generate a unique ID for the image
            String imageId = UUID.randomUUID().toString();
            
            // Generate path with file extension
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null ? 
                originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            Path imagePath = directory.resolve(imageId + extension);
            
            // Save the uploaded file
            Files.copy(file.getInputStream(), imagePath);
            
            // Store the image path for future reference
            backgroundImages.put(imageId, imagePath);
            
            // Return the image ID
            Map<String, String> response = new HashMap<>();
            response.put("imageId", imageId);
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to upload background image: " + e.getMessage()));
        }
    }
    
    private BufferedImage replaceWithColor(BufferedImage image, String colorHex) {
        // Create a new image with the same dimensions and ARGB type
        BufferedImage result = new BufferedImage(
                image.getWidth(), 
                image.getHeight(), 
                BufferedImage.TYPE_INT_ARGB);
        
        // Parse the hex color
        Color backgroundColor = parseColor(colorHex);
        
        // Create a graphics context
        Graphics2D g = result.createGraphics();
        
        // First fill the entire image with the background color
        g.setColor(backgroundColor);
        g.fillRect(0, 0, result.getWidth(), result.getHeight());
        
        // Then draw the original image on top
        g.setComposite(AlphaComposite.SrcAtop);
        g.drawImage(image, 0, 0, null);
        
        g.dispose();
        
        return result;
    }
    
    private BufferedImage replaceWithImage(BufferedImage image, String imageId) {
        try {
            // Look up the background image by ID
            Path imagePath = backgroundImages.get(imageId);
            if (imagePath == null) {
                // Try to find in the file system in case it was uploaded in a previous session
                Path directory = Paths.get(BACKGROUNDS_DIR);
                File[] files = directory.toFile().listFiles((dir, name) -> name.startsWith(imageId));
                if (files != null && files.length > 0) {
                    imagePath = files[0].toPath();
                    backgroundImages.put(imageId, imagePath);
                } else {
                    return null;
                }
            }
            
            // Load the background image
            BufferedImage bgImage = ImageIO.read(imagePath.toFile());
            if (bgImage == null) {
                return null;
            }
            
            // Create a new image with the same dimensions and ARGB type
            BufferedImage result = new BufferedImage(
                    image.getWidth(), 
                    image.getHeight(), 
                    BufferedImage.TYPE_INT_ARGB);
            
            // Create a graphics context
            Graphics2D g = result.createGraphics();
            
            // Set rendering hints for better quality
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            
            // Scale and draw the background image to fit the canvas
            g.drawImage(bgImage, 0, 0, result.getWidth(), result.getHeight(), null);
            
            // Then draw the original image on top
            g.setComposite(AlphaComposite.SrcAtop);
            g.drawImage(image, 0, 0, null);
            
            g.dispose();
            
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private Color parseColor(String colorHex) {
        // Default color (white) if parsing fails
        Color color = Color.WHITE;
        
        try {
            // Remove # if present
            if (colorHex.startsWith("#")) {
                colorHex = colorHex.substring(1);
            }
            
            // Parse hex color
            Pattern pattern = Pattern.compile("([0-9a-fA-F]{6})");
            Matcher matcher = pattern.matcher(colorHex);
            
            if (matcher.matches()) {
                int r = Integer.parseInt(colorHex.substring(0, 2), 16);
                int g = Integer.parseInt(colorHex.substring(2, 4), 16);
                int b = Integer.parseInt(colorHex.substring(4, 6), 16);
                color = new Color(r, g, b);
            }
        } catch (Exception e) {
            System.err.println("Invalid color format: " + e.getMessage());
            // Use default color (white)
        }
        
        return color;
    }
} 