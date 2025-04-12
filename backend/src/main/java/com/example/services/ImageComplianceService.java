package com.example.services;

import com.example.dto.ImageComplianceRequest;
import com.example.dto.ImageComplianceResponse;
import com.example.model.ImageState;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ImageComplianceService {

    private final FaceCenteringService faceCenteringService;
    private final PhotoEnhanceService photoEnhanceService;
    private final ResizeImageService resizeImageService;
    private final BackgroundRemovalService backgroundRemovalService;
    private final BackgroundReplaceService backgroundReplaceService;

    // Predefined constraints for ID photos
    private static final int MIN_WIDTH = 300;
    private static final int MIN_HEIGHT = 400;
    private static final int MAX_WIDTH = 1200;
    private static final int MAX_HEIGHT = 1600;
    private static final float MIN_BRIGHTNESS = -40.0f;
    private static final float MAX_BRIGHTNESS = 40.0f;
    private static final float MIN_CONTRAST = -40.0f;
    private static final float MAX_CONTRAST = 40.0f;
    
    // Background color constraints
    private static final int MAX_BACKGROUND_COLOR_VARIATION = 30; // Max RGB variation in background
    private static final int MIN_BACKGROUND_BRIGHTNESS = 180; // Background should be light
    private static final double MIN_UNIFORM_BACKGROUND_PERCENTAGE = 90.0; // Percentage of background that should be uniform

    @Autowired
    public ImageComplianceService(
            FaceCenteringService faceCenteringService,
            PhotoEnhanceService photoEnhanceService,
            ResizeImageService resizeImageService,
            BackgroundRemovalService backgroundRemovalService,
            BackgroundReplaceService backgroundReplaceService) {
        this.faceCenteringService = faceCenteringService;
        this.photoEnhanceService = photoEnhanceService;
        this.resizeImageService = resizeImageService;
        this.backgroundRemovalService = backgroundRemovalService;
        this.backgroundReplaceService = backgroundReplaceService;
    }

    public ResponseEntity<ImageComplianceResponse> checkImageCompliance(ImageComplianceRequest request) {
        try {
            if (request.getImage() == null) {
                ImageComplianceResponse response = new ImageComplianceResponse();
                response.addIssue("No image provided");
                return ResponseEntity.badRequest().body(response);
            }

            // Convert multipart file to BufferedImage
            MultipartFile file = request.getImage();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            
            if (image == null) {
                ImageComplianceResponse response = new ImageComplianceResponse();
                response.addIssue("Invalid image format");
                return ResponseEntity.badRequest().body(response);
            }

            // Create a temporary ImageState to analyze the image
            ImageState imageState = new ImageState();
            imageState.setCurrentImage(image);
            imageState.setOriginalImage(image);
            
            // Store hasReplacedBackground flag in metadata
            imageState.setMetadata("hasReplacedBackground", request.isHasReplacedBackground());
            if (request.getBackgroundColor() != null && !request.getBackgroundColor().isEmpty()) {
                imageState.setMetadata("backgroundColor", request.getBackgroundColor());
            }
            
            System.out.println("Background replacement metadata: hasReplaced=" + request.isHasReplacedBackground() + 
                             ", color=" + request.getBackgroundColor());

            // Perform compliance checks
            ImageComplianceResponse response = new ImageComplianceResponse();

            // Check face positioning
            checkFacePositioning(imageState, response);
            
            // Check image dimensions
            checkImageDimensions(imageState, response);
            
            // Check image quality (brightness/contrast)
            checkImageQuality(imageState, response);
            
            // Check background compliance
            checkBackgroundCompliance(imageState, response);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ImageComplianceResponse response = new ImageComplianceResponse();
            response.addIssue("Error processing image: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private void checkFacePositioning(ImageState imageState, ImageComplianceResponse response) {
        try {
            BufferedImage image = imageState.getCurrentImage();
            
            // Initialize OpenCV if needed
            try {
                // Force load OpenCV in case it wasn't loaded by other services
                nu.pattern.OpenCV.loadLocally();
            } catch (Exception e) {
                // Log but continue - maybe we already loaded OpenCV elsewhere
                System.err.println("Warning: Could not load OpenCV: " + e.getMessage());
            }
            
            // Prepare for face detection with OpenCV
            Mat mat = bufferedImageToMat(image);
            if (mat == null) {
                response.addIssue("Could not process image data for face detection");
                return;
            }
            
            Mat grayImage = new Mat();
            Imgproc.cvtColor(mat, grayImage, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(grayImage, grayImage);
            
            // Get cascade classifier from FaceCenteringService
            CascadeClassifier faceDetector = getFaceDetector();
            
            if (faceDetector == null || faceDetector.empty()) {
                // If face detection is not available, still check at least the image dimensions
                response.addIssue("Face detection not available - face position cannot be verified");
                return;
            }
            
            // Detect faces
            MatOfRect faceDetections = new MatOfRect();
            try {
                double scaleFactor = 1.1;
                int minNeighbors = 3;
                Size minSize = new Size(image.getWidth() * 0.1, image.getHeight() * 0.1);
                
                faceDetector.detectMultiScale(
                    grayImage,
                    faceDetections,
                    scaleFactor,
                    minNeighbors,
                    0,
                    minSize,
                    new Size()
                );
            } catch (Exception e) {
                System.err.println("Face detection failed: " + e.getMessage());
                response.addIssue("Face detection failed - face position cannot be verified");
                return;
            }
            
            // Check if any faces are detected
            Rect[] faces = faceDetections.toArray();
            
            if (faces.length == 0) {
                response.addIssue("No face detected in the image");
                return;
            }
            
            if (faces.length > 1) {
                response.addIssue("Multiple faces detected in the image");
                return;
            }
            
            // Check face positioning - the face should be centered
            Rect face = faces[0];
            double centerX = face.x + face.width / 2.0;
            double centerY = face.y + face.height / 2.0;
            
            double imageWidth = image.getWidth();
            double imageHeight = image.getHeight();
            
            double horizontalOffset = Math.abs(centerX - imageWidth / 2.0) / imageWidth;
            double verticalOffset = Math.abs(centerY - imageHeight / 2.0) / imageHeight;
            
            // For ID photos, the face should be reasonably centered
            // Allow some tolerance - face center should be within 15% of image center
            if (horizontalOffset > 0.15) {
                response.addIssue("Face is not properly centered horizontally");
            }
            
            if (verticalOffset > 0.15) {
                response.addIssue("Face is not properly centered vertically");
            }
            
            // Check face size - face should occupy reasonable portion of image height
            double faceRatio = face.height / (double) image.getHeight();
            
            if (faceRatio < 0.2) {
                response.addIssue("Face is too small in the image");
            } else if (faceRatio > 0.8) {
                response.addIssue("Face is too large in the image");
            }
            
        } catch (Exception e) {
            System.err.println("Error in checkFacePositioning: " + e.getMessage());
            e.printStackTrace();
            response.addIssue("Error checking face positioning: " + e.getMessage());
        }
    }
    
    private CascadeClassifier getFaceDetector() {
        // Since we don't have direct access to the FaceCenteringService's face detector,
        // we'll create our own instance here
        try {
            // Try multiple possible cascade file locations
            String[] possiblePaths = {
                "cascades/haarcascade_frontalface_default.xml",
                "./cascades/haarcascade_frontalface_default.xml",
                "../cascades/haarcascade_frontalface_default.xml",
                "backend/cascades/haarcascade_frontalface_default.xml",
                "haarcascade_frontalface_default.xml",
                // Add OpenCV's default installation path as fallback
                System.getProperty("user.dir") + "/cascades/haarcascade_frontalface_default.xml"
            };
            
            // Try each path until we find one that works
            for (String path : possiblePaths) {
                try {
                    java.io.File file = new java.io.File(path);
                    if (file.exists()) {
                        CascadeClassifier detector = new CascadeClassifier(path);
                        if (!detector.empty()) {
                            System.out.println("Successfully loaded cascade from: " + path);
                            return detector;
                        }
                    }
                } catch (Exception e) {
                    // Continue trying other paths
                }
            }
            
            // If we reach here, we couldn't load any detectors
            // Try creating a dummy detector for basic compliance checks
            return null;
        } catch (Exception e) {
            System.err.println("Error creating face detector: " + e.getMessage());
            return null;
        }
    }
    
    private void checkImageDimensions(ImageState imageState, ImageComplianceResponse response) {
        BufferedImage image = imageState.getCurrentImage();
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        if (width < MIN_WIDTH) {
            response.addIssue("Image width is too small (minimum: " + MIN_WIDTH + "px)");
        }
        
        if (height < MIN_HEIGHT) {
            response.addIssue("Image height is too small (minimum: " + MIN_HEIGHT + "px)");
        }
        
        if (width > MAX_WIDTH) {
            response.addIssue("Image width is too large (maximum: " + MAX_WIDTH + "px)");
        }
        
        if (height > MAX_HEIGHT) {
            response.addIssue("Image height is too large (maximum: " + MAX_HEIGHT + "px)");
        }
        
        // Check aspect ratio (optional)
        double aspectRatio = (double) width / height;
        double standardRatio = 3.0 / 4.0; // Common ID photo ratio
        
        // Allow some tolerance in aspect ratio (±15%)
        if (Math.abs(aspectRatio - standardRatio) / standardRatio > 0.15) {
            response.addIssue("Image aspect ratio is not standard (should be approximately 3:4)");
        }
    }
    
    private void checkImageQuality(ImageState imageState, ImageComplianceResponse response) {
        try {
            BufferedImage image = imageState.getCurrentImage();
            
            // Calculate brightness and contrast
            float[] brightnessContrast = calculateBrightnessContrast(image);
            float brightness = brightnessContrast[0];
            float contrast = brightnessContrast[1];
            
            if (brightness < MIN_BRIGHTNESS) {
                response.addIssue("Image is too dark");
            }
            
            if (brightness > MAX_BRIGHTNESS) {
                response.addIssue("Image is too bright");
            }
            
            if (contrast < MIN_CONTRAST) {
                response.addIssue("Image has too low contrast");
            }
            
            if (contrast > MAX_CONTRAST) {
                response.addIssue("Image has too high contrast");
            }
        } catch (Exception e) {
            response.addIssue("Error checking image quality: " + e.getMessage());
        }
    }
    
    private void checkBackgroundCompliance(ImageState imageState, ImageComplianceResponse response) {
        try {
            BufferedImage originalImage = imageState.getCurrentImage();
            
            // Skip background check if OpenCV is not available
            if (!isOpenCvAvailable()) {
                response.addIssue("Background check skipped (OpenCV not available)");
                return;
            }
            
            // Check if background has been replaced
            boolean hasReplacedBackground = false;
            String backgroundColor = null;
            
            if (imageState.getMetadata() != null) {
                if (imageState.getMetadata().containsKey("hasReplacedBackground")) {
                    hasReplacedBackground = (boolean) imageState.getMetadata().get("hasReplacedBackground");
                }
                
                if (imageState.getMetadata().containsKey("backgroundColor")) {
                    backgroundColor = (String) imageState.getMetadata().get("backgroundColor");
                }
            }
            
            System.out.println("Background has been replaced: " + hasReplacedBackground + 
                             ", color: " + backgroundColor);
            
            // If we have a replaced background with a target color, use that approach
            if (hasReplacedBackground && backgroundColor != null && !backgroundColor.isEmpty()) {
                System.out.println("Using target color based background check");
                checkBackgroundAgainstTargetColor(originalImage, backgroundColor, response);
                return;
            }
            
            // If we have a replaced background but no color info, use zone analysis
            if (hasReplacedBackground) {
                System.out.println("Using flood fill background analysis for replaced background");
                checkBackgroundWithFloodFill(originalImage, response);
                return;
            }
            
            // For natural photos, use the original mask-based approach
            // 1. First, create a mask of the foreground (person) using the background removal service capabilities
            BufferedImage foregroundMask = createForegroundMask(originalImage);
            
            if (foregroundMask == null) {
                System.out.println("Mask creation failed, falling back to zone analysis");
                checkBackgroundWithZoneAnalysis(originalImage, response, false);
                return;
            }
            
            // 2. Use the mask to analyze only the background pixels
            analyzeBackground(originalImage, foregroundMask, response, hasReplacedBackground);
            
        } catch (Exception e) {
            System.err.println("Error in checkBackgroundCompliance: " + e.getMessage());
            e.printStackTrace();
            response.addIssue("Error checking background compliance: " + e.getMessage());
        }
    }
    
    private boolean isOpenCvAvailable() {
        try {
            // Check if OpenCV core class is available
            Class.forName("org.opencv.core.Core");
            // Try to use a simple OpenCV operation as a test
            Mat testMat = new Mat(10, 10, CvType.CV_8UC3);
            return testMat != null && !testMat.empty();
        } catch (Exception e) {
            return false;
        }
    }
    
    private BufferedImage createForegroundMask(BufferedImage originalImage) {
        try {
            // Convert to Mat format for OpenCV processing
            Mat originalMat = bufferedImageToMat(originalImage);
            if (originalMat == null || originalMat.empty()) {
                return null;
            }
            
            // Use algorithms similar to BackgroundRemovalService but adjusted to just return a mask
            // This is a simplified version that creates a rough mask for analysis purposes
            Mat grayImage = new Mat();
            try {
                Imgproc.cvtColor(originalMat, grayImage, Imgproc.COLOR_BGR2GRAY);
            } catch (Exception e) {
                System.err.println("Error converting to grayscale: " + e.getMessage());
                return null;
            }
            
            // Detect faces to help with segmentation
            CascadeClassifier faceDetector = getFaceDetector();
            if (faceDetector != null && !faceDetector.empty()) {
                MatOfRect faceDetections = new MatOfRect();
                try {
                    faceDetector.detectMultiScale(grayImage, faceDetections);
                    Rect[] faces = faceDetections.toArray();
                    
                    if (faces.length > 0) {
                        // Create a mask for the detected face and surrounding areas
                        Mat mask = new Mat(originalMat.size(), CvType.CV_8UC1, Scalar.all(0));
                        
                        // For each face, create a mask that's larger than the face
                        for (Rect face : faces) {
                            // Expand the face rectangle to include shoulders and body (typical ID photo)
                            int expandX = face.width / 2;
                            int expandBottom = face.height * 2;  // More expansion downward for body
                            
                            // Calculate expanded rectangle ensuring it stays within image bounds
                            int x = Math.max(0, face.x - expandX);
                            int y = Math.max(0, face.y - expandX);
                            int width = Math.min(originalMat.cols() - x, face.width + (expandX * 2));
                            int height = Math.min(originalMat.rows() - y, face.height + expandX + expandBottom);
                            
                            // Create expanded rectangle for the person
                            Rect expandedRect = new Rect(x, y, width, height);
                            
                            // Fill the mask with white in the person area
                            Imgproc.rectangle(mask, expandedRect, new Scalar(255), -1);
                        }
                        
                        // Blur the mask to create a smoother transition
                        Imgproc.GaussianBlur(mask, mask, new Size(21, 21), 0);
                        
                        // Convert to BufferedImage for further processing
                        return matToBufferedImage(mask);
                    }
                } catch (Exception e) {
                    System.err.println("Error in face detection for mask: " + e.getMessage());
                    // Continue with fallback
                }
            }
            
            // If face detection fails, return a centered oval mask as fallback
            try {
                Mat mask = new Mat(originalMat.size(), CvType.CV_8UC1, Scalar.all(0));
                org.opencv.core.Point center = new org.opencv.core.Point(originalMat.cols() / 2, originalMat.rows() / 2);
                Size axes = new Size(originalMat.cols() / 3, originalMat.rows() / 2);
                Imgproc.ellipse(mask, center, axes, 0, 0, 360, new Scalar(255), -1);
                
                // Blur the mask to create a smoother transition
                Imgproc.GaussianBlur(mask, mask, new Size(21, 21), 0);
                
                return matToBufferedImage(mask);
            } catch (Exception e) {
                System.err.println("Error creating fallback mask: " + e.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error in createForegroundMask: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private void checkBackgroundAgainstTargetColor(BufferedImage image, String targetColorHex, ImageComplianceResponse response) {
        try {
            // Check for transparent background
            boolean hasTransparency = detectTransparentBackground(image);
            if (hasTransparency) {
                System.out.println("Detected transparent background (background removed but not replaced)");
                response.addIssue("Background has been removed but not replaced. Please select a background color.");
                return;
            }
            
            // Parse hex color
            Color targetColor = Color.decode(targetColorHex);
            System.out.println("Target background color: " + targetColor);
            
            // Sample border pixels using zone-based approach
            List<Color> borderPixels = new ArrayList<>();
            
            // Create and sample from 8 zones
            Rectangle[] zones = createBorderZones(image.getWidth(), image.getHeight());
            for (Rectangle zone : zones) {
                borderPixels.addAll(sampleZonePixels(image, zone));
            }
            
            if (borderPixels.isEmpty()) {
                response.addIssue("Could not analyze background (failed to sample border pixels)");
                return;
            }
            
            // Calculate percentage of pixels close to target color
            int pixelsCloseToTarget = 0;
            int mediumDistancePixels = 0;
            
            for (Color pixel : borderPixels) {
                // Calculate color distance from target
                double distance = colorDistance(pixel, targetColor);
                
                if (distance < 50) { // Very close to target color
                    pixelsCloseToTarget++;
                } else if (distance < 100) { // Medium distance (might be transition areas)
                    mediumDistancePixels++;
                }
            }
            
            // Calculate percentages
            double closePercentage = (pixelsCloseToTarget * 100.0) / borderPixels.size();
            double mediumPercentage = (mediumDistancePixels * 100.0) / borderPixels.size();
            double totalAcceptable = closePercentage + (mediumPercentage * 0.5); // Count medium pixels as half-weight
            
            System.out.println("Close to target color: " + closePercentage + "%, Medium distance: " + 
                             mediumPercentage + "%, Total acceptable: " + totalAcceptable + "%");
            
            // Check for darkness
            double backgroundBrightness = calculateBrightness(targetColor);
            if (backgroundBrightness < MIN_BACKGROUND_BRIGHTNESS) {
                response.addIssue("Background color is too dark (should be light colored)");
            }
            
            // Check if enough pixels match target color
            if (totalAcceptable < 40.0) {
                response.addIssue("Background doesn't match the selected color");
            }
            
        } catch (Exception e) {
            System.err.println("Error checking against target color: " + e.getMessage());
            e.printStackTrace();
            response.addIssue("Error analyzing background: " + e.getMessage());
        }
    }
    
    private double colorDistance(Color c1, Color c2) {
        // Weighted Euclidean distance in RGB space
        int rDiff = c1.getRed() - c2.getRed();
        int gDiff = c1.getGreen() - c2.getGreen();
        int bDiff = c1.getBlue() - c2.getBlue();
        
        // Perceptual weights (human eye is more sensitive to green)
        return Math.sqrt(0.299*rDiff*rDiff + 0.587*gDiff*gDiff + 0.114*bDiff*bDiff);
    }
    
    private double calculateBrightness(Color color) {
        return 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
    }
    
    private void checkBackgroundWithZoneAnalysis(BufferedImage image, ImageComplianceResponse response) {
        checkBackgroundWithZoneAnalysis(image, response, false);
    }
    
    private void checkBackgroundWithZoneAnalysis(BufferedImage image, ImageComplianceResponse response, boolean isReplacedBackground) {
        try {
            // Check for transparent background
            boolean hasTransparency = detectTransparentBackground(image);
            if (hasTransparency) {
                System.out.println("Detected transparent background (background removed but not replaced)");
                response.addIssue("Background has been removed but not replaced. Please select a background color.");
                return;
            }

            // Define border zones to analyze (in pixels from edge)
            int borderWidth = Math.max(image.getWidth() / 10, 20); // At least 20px or 10% of width
            int borderHeight = Math.max(image.getHeight() / 10, 20); // At least 20px or 10% of height
            
            // Sample points from the border zones
            List<Color> borderColors = new ArrayList<>();
            
            // Top border
            for (int x = 0; x < image.getWidth(); x += 5) {
                for (int y = 0; y < borderHeight; y += 5) {
                    borderColors.add(new Color(image.getRGB(x, y)));
                }
            }
            
            // Bottom border
            for (int x = 0; x < image.getWidth(); x += 5) {
                for (int y = image.getHeight() - borderHeight; y < image.getHeight(); y += 5) {
                    borderColors.add(new Color(image.getRGB(x, y)));
                }
            }
            
            // Left border
            for (int x = 0; x < borderWidth; x += 5) {
                for (int y = borderHeight; y < image.getHeight() - borderHeight; y += 5) {
                    borderColors.add(new Color(image.getRGB(x, y)));
                }
            }
            
            // Right border
            for (int x = image.getWidth() - borderWidth; x < image.getWidth(); x += 5) {
                for (int y = borderHeight; y < image.getHeight() - borderHeight; y += 5) {
                    borderColors.add(new Color(image.getRGB(x, y)));
                }
            }
            
            if (borderColors.isEmpty()) {
                response.addIssue("Unable to analyze background zones.");
                return;
            }
            
            // Calculate average color of border
            int totalR = 0, totalG = 0, totalB = 0;
            for (Color color : borderColors) {
                totalR += color.getRed();
                totalG += color.getGreen();
                totalB += color.getBlue();
            }
            
            Color avgColor = new Color(
                totalR / borderColors.size(),
                totalG / borderColors.size(),
                totalB / borderColors.size()
            );
            
            // Check brightness
            double brightness = (0.299 * avgColor.getRed() + 0.587 * avgColor.getGreen() + 0.114 * avgColor.getBlue()) / 255.0;
            double minBrightness = isReplacedBackground ? 0.8 : 0.85; // Lower threshold for replaced backgrounds
            
            if (brightness < minBrightness) {
                response.addIssue(String.format(
                    "Background appears too dark. Brightness: %.2f (min: %.2f)", 
                    brightness, minBrightness
                ));
            }
            
            // Check color uniformity
            int uniformPixels = 0;
            int colorThreshold = isReplacedBackground ? 25 : 15; // More tolerance for replaced backgrounds
            
            for (Color c : borderColors) {
                int rDiff = Math.abs(c.getRed() - avgColor.getRed());
                int gDiff = Math.abs(c.getGreen() - avgColor.getGreen());
                int bDiff = Math.abs(c.getBlue() - avgColor.getBlue());
                
                // Pixel is uniform if within threshold of average
                if (rDiff <= colorThreshold && gDiff <= colorThreshold && bDiff <= colorThreshold) {
                    uniformPixels++;
                }
            }
            
            double uniformityPercentage = 100.0 * uniformPixels / borderColors.size();
            double minUniformity = isReplacedBackground ? 75.0 : 85.0; // Lower threshold for replaced backgrounds
            
            if (uniformityPercentage < minUniformity) {
                response.addIssue(String.format(
                    "Background is not uniform enough. Uniformity: %.1f%% (min: %.1f%%)",
                    uniformityPercentage, minUniformity
                ));
            }
            
            System.out.printf(
                "Background zone analysis: Brightness=%.2f, Uniformity=%.1f%%\n", 
                brightness, uniformityPercentage
            );
            
        } catch (Exception e) {
            System.err.println("Error in zone analysis: " + e.getMessage());
            e.printStackTrace();
            response.addIssue("Error analyzing background zones: " + e.getMessage());
        }
    }
    
    private Rectangle[] createBorderZones(int width, int height) {
        int borderWidth = Math.max(20, Math.min(width, height) / 20);
        
        Rectangle[] zones = {
            // Corners
            new Rectangle(0, 0, borderWidth * 2, borderWidth * 2),                             // Top-left
            new Rectangle(width - borderWidth * 2, 0, borderWidth * 2, borderWidth * 2),       // Top-right
            new Rectangle(0, height - borderWidth * 2, borderWidth * 2, borderWidth * 2),      // Bottom-left
            new Rectangle(width - borderWidth * 2, height - borderWidth * 2, borderWidth * 2, borderWidth * 2), // Bottom-right
            
            // Middle sections - avoid corners by starting after corner zones end
            new Rectangle(borderWidth * 2, 0, width - (borderWidth * 4), borderWidth),          // Top middle
            new Rectangle(borderWidth * 2, height - borderWidth, width - (borderWidth * 4), borderWidth), // Bottom middle
            new Rectangle(0, borderWidth * 2, borderWidth, height - (borderWidth * 4)),         // Left middle
            new Rectangle(width - borderWidth, borderWidth * 2, borderWidth, height - (borderWidth * 4))  // Right middle
        };
        
        return zones;
    }
    
    private List<Color> sampleZonePixels(BufferedImage image, Rectangle zone) {
        List<Color> pixels = new ArrayList<>();
        int sampleStep = 3; // Sample every 3rd pixel to improve performance
        
        for (int y = zone.y; y < zone.y + zone.height; y += sampleStep) {
            for (int x = zone.x; x < zone.x + zone.width; x += sampleStep) {
                try {
                    if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
                        int rgb = image.getRGB(x, y);
                        Color pixel = new Color(rgb);
                        pixels.add(pixel);
                    }
                } catch (Exception e) {
                    // Skip problematic pixels
                    continue;
                }
            }
        }
        
        return pixels;
    }
    
    private static class ZoneResult {
        public boolean isUniform;
        public double brightness;
        public double uniformPercentage;
        
        public ZoneResult(boolean isUniform, double brightness, double uniformPercentage) {
            this.isUniform = isUniform;
            this.brightness = brightness;
            this.uniformPercentage = uniformPercentage;
        }
    }
    
    private ZoneResult analyzeZone(List<Color> pixels) {
        // Calculate average color in zone
        int totalR = 0, totalG = 0, totalB = 0;
        for (Color pixel : pixels) {
            totalR += pixel.getRed();
            totalG += pixel.getGreen();
            totalB += pixel.getBlue();
        }
        
        int avgR = totalR / pixels.size();
        int avgG = totalG / pixels.size();
        int avgB = totalB / pixels.size();
        
        Color avgColor = new Color(avgR, avgG, avgB);
        double brightness = calculateBrightness(avgColor);
        
        // Count uniform pixels (within variation tolerance)
        int uniformPixels = 0;
        int tolerance = 40; // Higher tolerance for small zones
        
        for (Color pixel : pixels) {
            if (Math.abs(pixel.getRed() - avgR) <= tolerance &&
                Math.abs(pixel.getGreen() - avgG) <= tolerance &&
                Math.abs(pixel.getBlue() - avgB) <= tolerance) {
                uniformPixels++;
            }
        }
        
        // Calculate percentage
        double uniformPercentage = (uniformPixels * 100.0) / pixels.size();
        
        // This zone is uniform if at least 60% of pixels are close to average
        return new ZoneResult(uniformPercentage >= 60.0, brightness, uniformPercentage);
    }
    
    private float[] calculateBrightnessContrast(BufferedImage image) {
        // Calculate average brightness and contrast
        int width = image.getWidth();
        int height = image.getHeight();
        long sum = 0;
        List<Integer> pixelValues = new ArrayList<>();
        
        // Sample pixels (for performance, we'll sample every 5th pixel)
        for (int y = 0; y < height; y += 5) {
            for (int x = 0; x < width; x += 5) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Convert to grayscale using standard luminance formula
                int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                sum += gray;
                pixelValues.add(gray);
            }
        }
        
        float avgBrightness = sum / (float) pixelValues.size();
        
        // Normalize brightness to range -100 to 100
        // 0 is normal, 128 is middle gray
        float normalizedBrightness = ((avgBrightness - 128) / 128) * 100;
        
        // Calculate contrast (standard deviation)
        double sumSquaredDiff = 0;
        for (int value : pixelValues) {
            double diff = value - avgBrightness;
            sumSquaredDiff += diff * diff;
        }
        
        double variance = sumSquaredDiff / pixelValues.size();
        double stdDev = Math.sqrt(variance);
        
        // Normalize contrast - higher stdDev means higher contrast
        // Standard contrast would be around 50-60 for normal images
        float normalizedContrast = (float)((stdDev - 50) / 50 * 100);
        
        return new float[] { normalizedBrightness, normalizedContrast };
    }
    
    private Mat bufferedImageToMat(BufferedImage image) {
        // Convert BufferedImage to Mat
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data;
        
        if (image.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            data = ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
            Mat mat = new Mat(height, width, CvType.CV_8UC3);
            mat.put(0, 0, data);
            return mat;
        } else {
            // Convert to BGR type
            BufferedImage convertedImg = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            convertedImg.getGraphics().drawImage(image, 0, 0, null);
            data = ((java.awt.image.DataBufferByte) convertedImg.getRaster().getDataBuffer()).getData();
            Mat mat = new Mat(height, width, CvType.CV_8UC3);
            mat.put(0, 0, data);
            return mat;
        }
    }
    
    private BufferedImage matToBufferedImage(Mat mat) {
        try {
            int width = mat.cols();
            int height = mat.rows();
            int channels = mat.channels();
            byte[] sourcePixels = new byte[width * height * channels];
            mat.get(0, 0, sourcePixels);
            
            // Create new image of appropriate type
            BufferedImage image;
            if (channels == 1) {
                image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            } else {
                image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            }
            
            // Set data
            final byte[] targetPixels = ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);
            
            return image;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private void analyzeBackground(BufferedImage original, BufferedImage foregroundMask, ImageComplianceResponse response, boolean hasReplacedBackground) {
        try {
            int width = original.getWidth();
            int height = original.getHeight();
            
            // Sample background pixels (pixels where the mask is black)
            List<Color> backgroundPixels = new ArrayList<>();
            boolean isBorderSamplingFallback = false;
            
            // Sample every 5th pixel to improve performance
            for (int y = 0; y < height; y += 5) {
                for (int x = 0; x < width; x += 5) {
                    try {
                        // Get mask value (0 = background, 255 = foreground)
                        int maskRgb = foregroundMask.getRGB(x, y);
                        int maskValue = (maskRgb & 0xff); // Extract intensity from mask
                        
                        // If this is a background pixel (mask value is low)
                        if (maskValue < 50) {
                            int rgb = original.getRGB(x, y);
                            Color pixel = new Color(rgb);
                            backgroundPixels.add(pixel);
                        }
                    } catch (Exception e) {
                        // Skip problematic pixels
                        continue;
                    }
                }
            }
            
            // If no background pixels were found, switch to zone-based analysis
            if (backgroundPixels.isEmpty()) {
                System.out.println("No background pixels found using mask, switching to zone analysis");
                checkBackgroundWithZoneAnalysis(original, response, hasReplacedBackground);
                return;
            }
            
            // Calculate average background color
            int totalR = 0, totalG = 0, totalB = 0;
            for (Color pixel : backgroundPixels) {
                totalR += pixel.getRed();
                totalG += pixel.getGreen();
                totalB += pixel.getBlue();
            }
            
            int avgR = totalR / backgroundPixels.size();
            int avgG = totalG / backgroundPixels.size();
            int avgB = totalB / backgroundPixels.size();
            
            Color avgColor = new Color(avgR, avgG, avgB);
            
            // Calculate brightness of average background
            double backgroundBrightness = calculateBrightness(avgColor);
            
            // Check if background is too dark (should be light for ID photos)
            if (backgroundBrightness < MIN_BACKGROUND_BRIGHTNESS) {
                response.addIssue("Background is too dark (should be light colored)");
            }
            
            // Check for background uniformity (how many pixels are close to the average)
            int uniformPixels = 0;
            int variation = MAX_BACKGROUND_COLOR_VARIATION;
            
            for (Color pixel : backgroundPixels) {
                // Calculate color difference
                int rDiff = Math.abs(pixel.getRed() - avgR);
                int gDiff = Math.abs(pixel.getGreen() - avgG);
                int bDiff = Math.abs(pixel.getBlue() - avgB);
                
                // If the pixel is close to the average, consider it "uniform"
                if (rDiff <= variation && 
                    gDiff <= variation && 
                    bDiff <= variation) {
                    uniformPixels++;
                }
            }
            
            // Calculate percentage of uniform background pixels
            double uniformPercentage = (uniformPixels * 100.0) / backgroundPixels.size();
            
            // Use standard threshold for natural backgrounds with good mask
            System.out.println("Background uniformity (mask method): " + uniformPercentage + "% (required: " + MIN_UNIFORM_BACKGROUND_PERCENTAGE + "%)");
            
            if (uniformPercentage < MIN_UNIFORM_BACKGROUND_PERCENTAGE) {
                response.addIssue("Background is not uniform (detected pattern or multiple colors)");
            }
        } catch (Exception e) {
            System.err.println("Error in analyzeBackground: " + e.getMessage());
            e.printStackTrace();
            response.addIssue("Error analyzing background: " + e.getMessage());
        }
    }

    private void analyzeBackground(List<Color> backgroundPixels, ImageComplianceResponse response, boolean isReplacedBackground) {
        if (backgroundPixels.isEmpty()) {
            response.addIssue("No background pixels found to analyze");
            return;
        }
        
        // Calculate average color
        int totalR = 0, totalG = 0, totalB = 0;
        for (Color c : backgroundPixels) {
            totalR += c.getRed();
            totalG += c.getGreen();
            totalB += c.getBlue();
        }
        
        Color avgColor = new Color(
            totalR / backgroundPixels.size(),
            totalG / backgroundPixels.size(),
            totalB / backgroundPixels.size()
        );
        
        // Check brightness
        double brightness = (0.299 * avgColor.getRed() + 0.587 * avgColor.getGreen() + 0.114 * avgColor.getBlue()) / 255.0;
        double minBrightness = isReplacedBackground ? 0.8 : 0.85; // Lower threshold for replaced backgrounds
        
        System.out.println("Background brightness: " + brightness);
        
        if (brightness < minBrightness) {
            response.addIssue(String.format(
                "Background is too dark. Brightness: %.2f (min: %.2f)",
                brightness, minBrightness
            ));
        }
        
        // Check uniformity
        int uniformPixels = 0;
        int colorThreshold = isReplacedBackground ? 25 : 15; // More tolerance for replaced backgrounds
        
        for (Color c : backgroundPixels) {
            int rDiff = Math.abs(c.getRed() - avgColor.getRed());
            int gDiff = Math.abs(c.getGreen() - avgColor.getGreen());
            int bDiff = Math.abs(c.getBlue() - avgColor.getBlue());
            
            // Pixel is uniform if within threshold of average
            if (rDiff <= colorThreshold && gDiff <= colorThreshold && bDiff <= colorThreshold) {
                uniformPixels++;
            }
        }
        
        double uniformityPercentage = 100.0 * uniformPixels / backgroundPixels.size();
        double minUniformity = isReplacedBackground ? 75.0 : 85.0; // Lower threshold for replaced backgrounds
        
        System.out.println("Background uniformity: " + uniformityPercentage + "%");
        
        if (uniformityPercentage < minUniformity) {
            response.addIssue(String.format(
                "Background is not uniform enough. Uniformity: %.1f%% (min: %.1f%%)",
                uniformityPercentage, minUniformity
            ));
        }
    }
    
    private void checkBackgroundWithFloodFill(BufferedImage image, ImageComplianceResponse response) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            
            // Check for transparent background by sampling corners
            boolean hasTransparency = detectTransparentBackground(image);
            if (hasTransparency) {
                System.out.println("Detected transparent background (background removed but not replaced)");
                response.addIssue("Background has been removed but not replaced. Please select a background color.");
                return;
            }
            
            // We'll use several seed points from the border to flood fill
            java.awt.Point[] seedPoints = {
                new java.awt.Point(5, 5),                  // Top-left
                new java.awt.Point(width/2, 5),            // Top-center
                new java.awt.Point(width-5, 5),            // Top-right
                new java.awt.Point(5, height/2),           // Left-center
                new java.awt.Point(width-5, height/2),     // Right-center
                new java.awt.Point(5, height-5),           // Bottom-left
                new java.awt.Point(width/2, height-5),     // Bottom-center
                new java.awt.Point(width-5, height-5)      // Bottom-right
            };
            
            // Track our results from each seed point
            List<FloodFillResult> results = new ArrayList<>();
            
            // For each seed point, perform a flood fill
            for (java.awt.Point seed : seedPoints) {
                try {
                    Color seedColor = new Color(image.getRGB(seed.x, seed.y));
                    
                    // Skip if this is likely a border or non-background pixel (very dark/very saturated)
                    double brightness = calculateBrightness(seedColor) / 255.0;
                    if (brightness < 0.7) {
                        continue; // Too dark for a background
                    }
                    
                    // Perform flood fill for this seed
                    FloodFillResult result = floodFill(image, seed, seedColor);
                    results.add(result);
                    
                    System.out.println("Flood fill from point " + seed.x + "," + seed.y + 
                                     " found " + result.pixels.size() + " pixels, " +
                                     "coverage: " + (result.pixels.size() * 100.0 / (width * height)) + "%");
                } catch (Exception e) {
                    System.err.println("Error in flood fill from seed " + seed.x + "," + seed.y + ": " + e.getMessage());
                }
            }
            
            // If we couldn't do any flood fills, fall back to zone analysis
            if (results.isEmpty()) {
                System.out.println("No successful flood fills, falling back to zone analysis");
                checkBackgroundWithZoneAnalysis(image, response, true);
                return;
            }
            
            // Sort results by size (largest first)
            results.sort((a, b) -> Integer.compare(b.pixels.size(), a.pixels.size()));
            
            // The largest region is our best guess at the background
            FloodFillResult bestResult = results.get(0);
            
            // Check if this is a reasonable size for a background (at least 20% of image)
            double coverage = bestResult.pixels.size() * 100.0 / (width * height);
            if (coverage < 20.0) {
                System.out.println("Largest flood fill region too small: " + coverage + "%, falling back to zone analysis");
                checkBackgroundWithZoneAnalysis(image, response, true);
                return;
            }
            
            // Now analyze the color of this region
            analyzeBackground(bestResult.pixels, response, true);
            
        } catch (Exception e) {
            System.err.println("Error in flood fill background check: " + e.getMessage());
            e.printStackTrace();
            checkBackgroundWithZoneAnalysis(image, response, true);
        }
    }
    
    private static class FloodFillResult {
        public List<Color> pixels;
        public Color avgColor;
        
        public FloodFillResult(List<Color> pixels, Color avgColor) {
            this.pixels = pixels;
            this.avgColor = avgColor;
        }
    }
    
    private FloodFillResult floodFill(BufferedImage image, java.awt.Point start, Color seedColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Keep track of visited pixels
        boolean[][] visited = new boolean[width][height];
        
        // Keep track of pixels to process (breadth-first search)
        List<java.awt.Point> queue = new ArrayList<>();
        List<Color> region = new ArrayList<>();
        
        // Add starting point
        queue.add(start);
        
        // Color similarity threshold (higher = more tolerant)
        int tolerance = 30;
        
        // Limit the number of pixels we'll process (for performance) 
        int maxPixels = Math.min(width * height, 30000);
        
        // Process queue
        while (!queue.isEmpty() && region.size() < maxPixels) {
            java.awt.Point p = queue.remove(0);
            
            // Skip if out of bounds or already visited
            if (p.x < 0 || p.y < 0 || p.x >= width || p.y >= height || visited[p.x][p.y]) {
                continue;
            }
            
            // Mark as visited
            visited[p.x][p.y] = true;
            
            // Get color at this point
            Color color = new Color(image.getRGB(p.x, p.y));
            
            // Check if similar to seed
            if (isColorSimilar(color, seedColor, tolerance)) {
                // Add to region
                region.add(color);
                
                // Add neighbors to queue
                queue.add(new java.awt.Point(p.x + 1, p.y));
                queue.add(new java.awt.Point(p.x - 1, p.y));
                queue.add(new java.awt.Point(p.x, p.y + 1));
                queue.add(new java.awt.Point(p.x, p.y - 1));
            }
        }
        
        // Calculate average color
        if (region.isEmpty()) {
            return new FloodFillResult(region, seedColor);
        }
        
        int totalR = 0, totalG = 0, totalB = 0;
        for (Color c : region) {
            totalR += c.getRed();
            totalG += c.getGreen();
            totalB += c.getBlue();
        }
        
        Color avgColor = new Color(
            totalR / region.size(),
            totalG / region.size(),
            totalB / region.size()
        );
        
        return new FloodFillResult(region, avgColor);
    }
    
    private boolean isColorSimilar(Color c1, Color c2, int tolerance) {
        return Math.abs(c1.getRed() - c2.getRed()) <= tolerance &&
               Math.abs(c1.getGreen() - c2.getGreen()) <= tolerance &&
               Math.abs(c1.getBlue() - c2.getBlue()) <= tolerance;
    }

    /**
     * Detect if an image has a transparent background by sampling various points
     */
    private boolean detectTransparentBackground(BufferedImage image) {
        // Check if the image has an alpha channel
        if (image.getColorModel().hasAlpha()) {
            int width = image.getWidth();
            int height = image.getHeight();
            
            // Quick sample of key points
            int[][] samplePoints = {
                {0, 0},                // top-left
                {width-1, 0},          // top-right
                {0, height-1},         // bottom-left
                {width-1, height-1},   // bottom-right
                {width/2, height/2}    // center
            };
            
            int transparentCount = 0;
            int totalSampled = samplePoints.length;
            
            // Check each sample point
            for (int[] point : samplePoints) {
                int argb = image.getRGB(point[0], point[1]);
                int alpha = (argb >> 24) & 0xff;
                
                if (alpha < 128) { // less than 50% opacity
                    transparentCount++;
                }
            }
            
            // If initial points suggest transparency, do a more thorough check
            if (transparentCount >= 2) {
                // Sample more points (border areas)
                int transparentBorderPoints = 0;
                int totalBorderPoints = 0;
                
                // Sample border at 10% intervals
                for (int i = 0; i < 10; i++) {
                    // Top border
                    int x1 = (width * i) / 10;
                    int y1 = 5;
                    totalBorderPoints++;
                    if (getAlpha(image, x1, y1) < 128) transparentBorderPoints++;
                    
                    // Bottom border
                    int x2 = (width * i) / 10;
                    int y2 = height - 5;
                    totalBorderPoints++;
                    if (getAlpha(image, x2, y2) < 128) transparentBorderPoints++;
                    
                    // Left border
                    int x3 = 5;
                    int y3 = (height * i) / 10;
                    totalBorderPoints++;
                    if (getAlpha(image, x3, y3) < 128) transparentBorderPoints++;
                    
                    // Right border
                    int x4 = width - 5;
                    int y4 = (height * i) / 10;
                    totalBorderPoints++;
                    if (getAlpha(image, x4, y4) < 128) transparentBorderPoints++;
                }
                
                // If >20% of border pixels are transparent, it's likely a transparent background
                return (transparentBorderPoints > totalBorderPoints * 0.2);
            }
            
            // If multiple initial points are transparent, likely a transparent background
            return transparentCount >= 3;
        }
        
        return false;
    }
    
    private int getAlpha(BufferedImage image, int x, int y) {
        try {
            return (image.getRGB(x, y) >> 24) & 0xff;
        } catch (Exception e) {
            return 255; // Assume fully opaque if there's an error
        }
    }
} 