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
    
    // Background color constraints

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
            System.out.println("Processing image: " + file.getOriginalFilename() + 
                             ", size: " + file.getSize() + " bytes, content type: " + file.getContentType());
                             
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
            
            
            // Create an ImageComplianceResponse to store our findings
            ImageComplianceResponse response = new ImageComplianceResponse();

            // Check image dimensions and aspect ratio
            checkImageDimensions(imageState, response);
            
            // Check background compliance (focused on white background)
            checkBackgroundCompliance(imageState, response);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error in checkImageCompliance: " + e.getMessage());
            e.printStackTrace();
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
        
        // Check aspect ratio
        double aspectRatio = (double) width / height;
        double standardRatio = 3.0 / 4.0; // Common ID photo ratio (3:4)
        
        // Allow some tolerance in aspect ratio (±15%)
        if (Math.abs(aspectRatio - standardRatio) / standardRatio > 0.15) {
            response.addIssue("Image aspect ratio is not standard (should be approximately 3:4)");
        }
    }
    
    private void checkBackgroundCompliance(ImageState imageState, ImageComplianceResponse response) {
        try {
            BufferedImage originalImage = imageState.getCurrentImage();
            
            // First check for transparent background
            if (hasTransparentBackground(originalImage)) {
                response.addIssue("Transparent background detected. Please use a solid pure white background.");
                return;
            }
            
            
            // Sample small areas from top left and top right corners
            List<Color> topLeftCornerPixels = sampleTopLeftCorner(originalImage);
            List<Color> topRightCornerPixels = sampleTopRightCorner(originalImage);
            
            // Analyze corner pixels for pure white determination
            double topLeftWhitePercent = calculateWhitePercentage(topLeftCornerPixels);
            double topRightWhitePercent = calculateWhitePercentage(topRightCornerPixels);
            
            System.out.println("Top left corner white %: " + topLeftWhitePercent);
            System.out.println("Top right corner white %: " + topRightWhitePercent);
            
            // Calculate average colors of corners for detailed feedback
            Color avgTopLeftColor = calculateAverageColor(topLeftCornerPixels);
            Color avgTopRightColor = calculateAverageColor(topRightCornerPixels);
            
            System.out.println("Top left corner average color: R=" + avgTopLeftColor.getRed() + 
                             ", G=" + avgTopLeftColor.getGreen() + ", B=" + avgTopLeftColor.getBlue());
            System.out.println("Top right corner average color: R=" + avgTopRightColor.getRed() + 
                             ", G=" + avgTopRightColor.getGreen() + ", B=" + avgTopRightColor.getBlue());
            
            // STRICT RULE: Both corners must be nearly 100% pure white (98% threshold)
            boolean topLeftIsWhite = topLeftWhitePercent >= 98.0;
            boolean topRightIsWhite = topRightWhitePercent >= 98.0;
            
            if (!topLeftIsWhite || !topRightIsWhite) {
                response.addIssue("Background is not white. Please use a solid pure white background.");
            }
            
        } catch (Exception e) {
            System.err.println("Error in checkBackgroundCompliance: " + e.getMessage());
            e.printStackTrace();
            response.addIssue("Error checking background color: " + e.getMessage());
        }
    }
    
    private boolean isColorWhite(Color color) {
        // Check if color is pure white or extremely close to white
        // White should have extremely high RGB values (all very close to 255)
        int minWhiteThreshold = 253; // Extremely strict threshold for white
        
        return color.getRed() >= minWhiteThreshold && 
               color.getGreen() >= minWhiteThreshold && 
               color.getBlue() >= minWhiteThreshold;
    }
    
    private boolean isColorOffWhite(Color color) {
        // Check if color is off-white (cream, eggshell, etc.)
        // Off-white has high but not maximal RGB values
        return color.getRed() >= 240 && color.getRed() < 253 &&
               color.getGreen() >= 240 && color.getGreen() < 253 &&
               color.getBlue() >= 240 && color.getBlue() < 253;
    }
    
    private boolean isColorGray(Color color) {
        // Check if color is gray (R, G, B values very close to each other)
        int maxDiff = 5; // Maximum difference between color channels to be considered gray
        
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        
        return Math.abs(r - g) <= maxDiff && 
               Math.abs(g - b) <= maxDiff && 
               Math.abs(r - b) <= maxDiff &&
               r < 252; // Make sure it's not pure white
    }
    
    private boolean hasTransparentBackground(BufferedImage image) {
        // Skip if the image doesn't support transparency
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Sample the border regions to check for transparency
        int borderSize = Math.max(10, Math.min(width, height) / 10);
        int transparentPixelCount = 0;
        int totalSamples = 0;
        
        // Check top and bottom borders
        for (int x = 0; x < width; x += 5) {
            // Top border
            for (int y = 0; y < borderSize; y += 5) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xff;
                if (alpha < 240) { // Not fully opaque
                    transparentPixelCount++;
                }
                totalSamples++;
            }
            
            // Bottom border
            for (int y = height - borderSize; y < height; y += 5) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xff;
                if (alpha < 240) { // Not fully opaque
                    transparentPixelCount++;
                }
                totalSamples++;
            }
        }
        
        // Check left and right borders
        for (int y = borderSize; y < height - borderSize; y += 5) {
            // Left border
            for (int x = 0; x < borderSize; x += 5) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xff;
                if (alpha < 240) { // Not fully opaque
                    transparentPixelCount++;
                }
                totalSamples++;
            }
            
            // Right border
            for (int x = width - borderSize; x < width; x += 5) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xff;
                if (alpha < 240) { // Not fully opaque
                    transparentPixelCount++;
                }
                totalSamples++;
            }
        }
        
        // If more than 10% of border pixels are transparent, consider it a transparent background
        double transparentPercentage = (transparentPixelCount * 100.0) / totalSamples;
        System.out.println("Transparent pixel percentage: " + transparentPercentage + "%");
        
        return transparentPercentage > 10.0;
    }
    
    private double calculateBackgroundColorVariation(List<Color> pixels) {
        if (pixels.isEmpty()) {
            return 0.0;
        }
        
        // Calculate average RGB values
        int totalR = 0, totalG = 0, totalB = 0;
        for (Color pixel : pixels) {
            totalR += pixel.getRed();
            totalG += pixel.getGreen();
            totalB += pixel.getBlue();
        }
        
        double avgR = totalR / (double)pixels.size();
        double avgG = totalG / (double)pixels.size();
        double avgB = totalB / (double)pixels.size();
        
        // Calculate standard deviation of RGB values (color variation)
        double sumSquaredDiffR = 0, sumSquaredDiffG = 0, sumSquaredDiffB = 0;
        for (Color pixel : pixels) {
            sumSquaredDiffR += Math.pow(pixel.getRed() - avgR, 2);
            sumSquaredDiffG += Math.pow(pixel.getGreen() - avgG, 2);
            sumSquaredDiffB += Math.pow(pixel.getBlue() - avgB, 2);
        }
        
        double stdDevR = Math.sqrt(sumSquaredDiffR / pixels.size());
        double stdDevG = Math.sqrt(sumSquaredDiffG / pixels.size());
        double stdDevB = Math.sqrt(sumSquaredDiffB / pixels.size());
        
        // Return average of the three standard deviations as a measure of color variation
        return (stdDevR + stdDevG + stdDevB) / 3.0;
    }
    
    private List<Color> sampleBorderPixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Define border width (10% of the image dimensions or at least 10 pixels)
        int borderWidth = Math.max(10, width / 10);
        int borderHeight = Math.max(10, height / 10);
        
        List<Color> borderPixels = new ArrayList<>();
        
        // Sample top border
        for (int x = 0; x < width; x += 5) {
            for (int y = 0; y < borderHeight; y += 5) {
                borderPixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        // Sample bottom border
        for (int x = 0; x < width; x += 5) {
            for (int y = height - borderHeight; y < height; y += 5) {
                borderPixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        // Sample left border
        for (int y = borderHeight; y < height - borderHeight; y += 5) {
            for (int x = 0; x < borderWidth; x += 5) {
                borderPixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        // Sample right border
        for (int y = borderHeight; y < height - borderHeight; y += 5) {
            for (int x = width - borderWidth; x < width; x += 5) {
                borderPixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        return borderPixels;
    }
    
    private List<Color> sampleCornerPixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Define corner size (5% of the image dimensions or at least 20 pixels)
        int cornerSize = Math.max(20, Math.min(width, height) / 20);
        
        List<Color> cornerPixels = new ArrayList<>();
        
        // Sample top-left corner
        for (int x = 0; x < cornerSize; x += 2) {
            for (int y = 0; y < cornerSize; y += 2) {
                cornerPixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        // Sample top-right corner
        for (int x = width - cornerSize; x < width; x += 2) {
            for (int y = 0; y < cornerSize; y += 2) {
                cornerPixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        // Sample bottom-left corner
        for (int x = 0; x < cornerSize; x += 2) {
            for (int y = height - cornerSize; y < height; y += 2) {
                cornerPixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        // Sample bottom-right corner
        for (int x = width - cornerSize; x < width; x += 2) {
            for (int y = height - cornerSize; y < height; y += 2) {
                cornerPixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        return cornerPixels;
    }
    
    private Color calculateAverageColor(List<Color> pixels) {
        if (pixels.isEmpty()) {
            return Color.WHITE; // Default to white
        }
        
        long totalR = 0, totalG = 0, totalB = 0;
        for (Color pixel : pixels) {
            totalR += pixel.getRed();
            totalG += pixel.getGreen();
            totalB += pixel.getBlue();
        }
        
        int avgR = (int)(totalR / pixels.size());
        int avgG = (int)(totalG / pixels.size());
        int avgB = (int)(totalB / pixels.size());
        
        return new Color(avgR, avgG, avgB);
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
    
    private List<Color> sampleRandomBackgroundPixels(BufferedImage image) {
            int width = image.getWidth();
            int height = image.getHeight();
            
        // Assuming the central portion of the image is the subject
        // We'll sample from outside this central area
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Define the central area dimensions
        int centralWidth = width / 2;  // 50% of image width
        int centralHeight = height / 2; // 50% of image height
        
        // Calculate boundaries of the central area
        int centralLeft = centerX - (centralWidth / 2);
        int centralRight = centerX + (centralWidth / 2);
        int centralTop = centerY - (centralHeight / 2);
        int centralBottom = centerY + (centralHeight / 2);
        
        List<Color> backgroundPixels = new ArrayList<>();
        
        // Sample 500 random points outside the central area
        for (int i = 0; i < 500; i++) {
            int x, y;
            
            // Generate a point outside the central area
            do {
                x = (int) (Math.random() * width);
                y = (int) (Math.random() * height);
            } while (x >= centralLeft && x <= centralRight && 
                     y >= centralTop && y <= centralBottom);
            
            try {
                backgroundPixels.add(new Color(image.getRGB(x, y)));
                } catch (Exception e) {
                // Skip any problematic pixels
            }
        }
        
        return backgroundPixels;
    }
    
    private List<Color> sampleTopLeftCorner(BufferedImage image) {
        int size = Math.min(20, Math.min(image.getWidth(), image.getHeight()) / 10);
        List<Color> pixels = new ArrayList<>();
        
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                pixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        return pixels;
    }
    
    private List<Color> sampleTopRightCorner(BufferedImage image) {
        int size = Math.min(20, Math.min(image.getWidth(), image.getHeight()) / 10);
        int width = image.getWidth();
        List<Color> pixels = new ArrayList<>();
        
        for (int x = width - size; x < width; x++) {
            for (int y = 0; y < size; y++) {
                pixels.add(new Color(image.getRGB(x, y)));
            }
        }
        
        return pixels;
    }
    
    private double calculateWhitePercentage(List<Color> pixels) {
        if (pixels.isEmpty()) {
            return 0.0;
        }
        
        int whitePixels = 0;
        for (Color pixel : pixels) {
            if (isColorWhite(pixel)) {
                whitePixels++;
            }
        }
        
        return (whitePixels * 100.0) / pixels.size();
    }
} 