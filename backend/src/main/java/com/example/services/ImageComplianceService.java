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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
            
            // 1. First, create a mask of the foreground (person) using the background removal service capabilities
            BufferedImage foregroundMask = createForegroundMask(originalImage);
            
            if (foregroundMask == null) {
                response.addIssue("Could not analyze background (background segmentation failed)");
                return;
            }
            
            // 2. Use the mask to analyze only the background pixels
            analyzeBackground(originalImage, foregroundMask, response);
            
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
                Point center = new Point(originalMat.cols() / 2, originalMat.rows() / 2);
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
    
    private void analyzeBackground(BufferedImage original, BufferedImage foregroundMask, ImageComplianceResponse response) {
        try {
            int width = original.getWidth();
            int height = original.getHeight();
            
            // Sample background pixels (pixels where the mask is black)
            List<Color> backgroundPixels = new ArrayList<>();
            
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
            
            // If no background pixels were found
            if (backgroundPixels.isEmpty()) {
                response.addIssue("Could not analyze background (no background detected)");
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
            double backgroundBrightness = 0.299 * avgR + 0.587 * avgG + 0.114 * avgB;
            
            // Check if background is too dark (should be light for ID photos)
            if (backgroundBrightness < MIN_BACKGROUND_BRIGHTNESS) {
                response.addIssue("Background is too dark (should be light colored)");
            }
            
            // Check for background uniformity (how many pixels are close to the average)
            int uniformPixels = 0;
            for (Color pixel : backgroundPixels) {
                // Calculate color difference
                int rDiff = Math.abs(pixel.getRed() - avgR);
                int gDiff = Math.abs(pixel.getGreen() - avgG);
                int bDiff = Math.abs(pixel.getBlue() - avgB);
                
                // If the pixel is close to the average, consider it "uniform"
                if (rDiff <= MAX_BACKGROUND_COLOR_VARIATION && 
                    gDiff <= MAX_BACKGROUND_COLOR_VARIATION && 
                    bDiff <= MAX_BACKGROUND_COLOR_VARIATION) {
                    uniformPixels++;
                }
            }
            
            // Calculate percentage of uniform background pixels
            double uniformPercentage = (uniformPixels * 100.0) / backgroundPixels.size();
            
            // Check if background is uniform enough
            if (uniformPercentage < MIN_UNIFORM_BACKGROUND_PERCENTAGE) {
                response.addIssue("Background is not uniform (detected pattern or multiple colors)");
            }
        } catch (Exception e) {
            System.err.println("Error in analyzeBackground: " + e.getMessage());
            e.printStackTrace();
            response.addIssue("Error analyzing background: " + e.getMessage());
        }
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
} 