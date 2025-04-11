package com.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/image-service")
@CrossOrigin(origins = { "http://localhost:5173" }, allowCredentials = "true")
public class ImageController {
    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    @Value("${upload.directory}")
    private String uploadDir;

    private final ResourceLoader resourceLoader;

    public ImageController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @GetMapping("/get")
    public ResponseEntity<?> getImage() {
        try {
            // Define the default image path
            Path imagePath = Paths.get(uploadDir, "current_image.jpg");
            File imageFile = imagePath.toFile();

            // Check if the image exists
            if (!imageFile.exists() || !imageFile.isFile()) {
                logger.info("No current image found at: {}", imagePath);
                return ResponseEntity.notFound().build();
            }

            // Read the image bytes
            byte[] imageBytes = Files.readAllBytes(imagePath);
            String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);

            Map<String, String> response = new HashMap<>();
            response.put("image", base64Image);

            logger.info("Successfully retrieved image of size: {} bytes", imageBytes.length);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving image:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving image: " + e.getMessage());
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            // Create directory if it doesn't exist
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Save the file
            Path targetPath = Paths.get(uploadDir, "current_image.jpg");
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            Map<String, String> response = new HashMap<>();
            response.put("path", targetPath.toString());
            response.put("message", "Image uploaded successfully");

            logger.info("Image uploaded successfully to: {}", targetPath);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error uploading image:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading image: " + e.getMessage());
        }
    }
}