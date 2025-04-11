package com.example.services;

import com.example.dto.FaceCenteringRequest;
import com.example.model.ImageState;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

@Service
public class FaceCenteringService {

    private static final String CASCADE_FILE = "cascades/haarcascade_frontalface_default.xml";
    private static final String ALT_CASCADE_FILE = "cascades/haarcascade_frontalface_alt.xml";
    private static final String PROFILE_CASCADE_FILE = "cascades/haarcascade_profileface.xml";
    private CascadeClassifier faceDetector;
    private CascadeClassifier altFaceDetector;  // Alternative detector for fallback
    private CascadeClassifier profileFaceDetector; // For profile/side faces
    
    // Static initialization block to load OpenCV library
    static {
        try {
            OpenCV.loadLocally();
            System.out.println("OpenCV loaded successfully: " + Core.VERSION);
        } catch (Exception e) {
            System.err.println("Failed to load OpenCV: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public FaceCenteringService() {
        try {
            // Create cascades directory if it doesn't exist
            Path cascadesDir = Paths.get("cascades");
            if (!Files.exists(cascadesDir)) {
                Files.createDirectories(cascadesDir);
                System.out.println("Created cascades directory at: " + cascadesDir.toAbsolutePath());
            }
            
            // Use direct file path
            Path cascadePath = Paths.get("cascades/haarcascade_frontalface_default.xml");
            
            if (!Files.exists(cascadePath)) {
                System.err.println("ERROR: Cascade file not found at: " + cascadePath.toAbsolutePath());
                throw new IOException("Cascade file not found at: " + cascadePath.toAbsolutePath());
            } else {
                System.out.println("Found cascade file at: " + cascadePath.toAbsolutePath());
                System.out.println("File size: " + Files.size(cascadePath) + " bytes");
            }
            
            // Initialize face detector
            try {
                System.out.println("Initializing face detector with: " + cascadePath.toAbsolutePath());
                faceDetector = new CascadeClassifier(cascadePath.toAbsolutePath().toString());
                
                if (faceDetector.empty()) {
                    System.err.println("ERROR: Cascade classifier is empty after loading");
                    throw new IOException("Failed to load cascade classifier. Classifier is empty.");
                }
                
                System.out.println("Face detector initialized successfully: " + (faceDetector != null ? "instance created" : "null"));
            } catch (Exception e) {
                System.err.println("ERROR loading cascade classifier: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            
            // Initialize alternative face detector (for fallback)
            try {
                Path altCascadePath = Paths.get(ALT_CASCADE_FILE);
                
                // Only try to initialize if file exists
                if (Files.exists(altCascadePath)) {
                    System.out.println("Initializing alternative face detector with: " + altCascadePath.toAbsolutePath());
                    altFaceDetector = new CascadeClassifier(altCascadePath.toAbsolutePath().toString());
                    
                    if (altFaceDetector.empty()) {
                        System.err.println("WARNING: Alternative cascade classifier is empty after loading");
                        altFaceDetector = null;
                    } else {
                        System.out.println("Alternative face detector initialized successfully");
                    }
                } else {
                    System.out.println("Alternative cascade file not found. Only primary detector will be used.");
                    altFaceDetector = null;
                }
            } catch (Exception e) {
                System.err.println("Error initializing alternative face detector: " + e.getMessage());
                e.printStackTrace();
                altFaceDetector = null;
            }
            
            // Initialize profile face detector
            try {
                Path profileCascadePath = Paths.get(PROFILE_CASCADE_FILE);
                
                // Only try to initialize if file exists
                if (Files.exists(profileCascadePath)) {
                    System.out.println("Initializing profile face detector with: " + profileCascadePath.toAbsolutePath());
                    profileFaceDetector = new CascadeClassifier(profileCascadePath.toAbsolutePath().toString());
                    
                    if (profileFaceDetector.empty()) {
                        System.err.println("WARNING: Profile cascade classifier is empty after loading");
                        profileFaceDetector = null;
                    } else {
                        System.out.println("Profile face detector initialized successfully");
                    }
                } else {
                    System.out.println("Profile cascade file not found. Side faces may not be detected.");
                    profileFaceDetector = null;
                }
            } catch (Exception e) {
                System.err.println("Error initializing profile face detector: " + e.getMessage());
                e.printStackTrace();
                profileFaceDetector = null;
            }
        } catch (IOException e) {
            System.err.println("Error initializing face centering service: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public ResponseEntity<String> centerFace(FaceCenteringRequest request, ImageState state) {
        BufferedImage img = state.getCurrentImage();
        if (img == null) return ResponseEntity.badRequest().body("No image loaded.");
        
        try {
            // Check if face detector is properly initialized
            if (faceDetector == null || faceDetector.empty()) {
                System.err.println("Face detector is not properly initialized. Trying to reinitialize...");
                
                // Try to reinitialize
                Path cascadePath = Paths.get("cascades/haarcascade_frontalface_default.xml");
                if (Files.exists(cascadePath)) {
                    faceDetector = new CascadeClassifier(cascadePath.toAbsolutePath().toString());
                    
                    if (faceDetector.empty()) {
                        String error = "Failed to reinitialize face detector. Cascade file may be corrupted.";
                        System.err.println(error);
                        return ResponseEntity.badRequest().body(error);
                    }
                } else {
                    String error = "Cascade file not found at: " + cascadePath.toAbsolutePath();
                    System.err.println(error);
                    return ResponseEntity.badRequest().body(error);
                }
            }
            
            // Save current image to history before centering
            state.pushHistory(state.cloneImage(img));
            
            // Clear future stack when making a new edit
            state.clearFuture();
            
            // Convert BufferedImage to OpenCV Mat
            Mat image = bufferedImageToMat(img);
            
            // Apply face centering
            System.out.println("Starting face centering process for image: " + img.getWidth() + "x" + img.getHeight());
            BufferedImage centeredImage = centerPortraitOnFaces(image, img.getWidth(), img.getHeight());
            
            // Update the current image
            state.setCurrentImage(centeredImage);
            
            System.out.println("Face centering completed successfully");
            return ResponseEntity.ok("Face centered successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to center face: " + e.getMessage());
        }
    }
    
    private BufferedImage centerPortraitOnFaces(Mat image, int targetWidth, int targetHeight) throws IOException {
        // Convert to grayscale for face detection
        Mat grayImage = new Mat();
        Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
        
        // Improve contrast for better detection
        Imgproc.equalizeHist(grayImage, grayImage);
        
        System.out.println("Detecting faces in image...");
        
        // Detect faces with improved parameters
        MatOfRect faceDetections = new MatOfRect();
        // Parameters: image, faces, scaleFactor, minNeighbors, flags, minSize, maxSize
        // Adjust parameters for better detection
        double scaleFactor = 1.1;
        int minNeighbors = 3;
        Size minSize = new Size(image.width() * 0.1, image.height() * 0.1); // Min 10% of image
        
        // Use alternative detector if requested
        boolean useAltDetector = false;
        boolean useProfileDetector = false;
        try {
            faceDetector.detectMultiScale(
                grayImage,
                faceDetections,
                scaleFactor,
                minNeighbors,
                0,  // flags
                minSize,
                new Size() // max size (empty = no limit)
            );
        } catch (Exception e) {
            System.err.println("Error with primary face detector: " + e.getMessage());
            if (altFaceDetector != null && !altFaceDetector.empty()) {
                System.out.println("Falling back to alternative face detector");
                useAltDetector = true;
            }
        }
        
        // Try alternative detector if primary failed or found no faces
        if ((faceDetections.toArray().length == 0 || useAltDetector) && altFaceDetector != null && !altFaceDetector.empty()) {
            System.out.println("Using alternative face detector");
            try {
                faceDetections = new MatOfRect();
                altFaceDetector.detectMultiScale(
                    grayImage,
                    faceDetections,
                    1.05,   // Lower scale factor for more sensitivity
                    2,      // Lower min neighbors
                    0,
                    minSize,
                    new Size()
                );
            } catch (Exception e) {
                System.err.println("Error with alternative face detector: " + e.getMessage());
            }
        }
        
        Rect[] faces = faceDetections.toArray();
        System.out.println("Detected " + faces.length + " faces");
        
        if (faces.length == 0 && profileFaceDetector != null && !profileFaceDetector.empty()) {
            System.out.println("No faces detected with frontal detectors - trying profile face detector");
            try {
                // Detect profile faces
                faceDetections = new MatOfRect();
                profileFaceDetector.detectMultiScale(
                    grayImage,
                    faceDetections,
                    1.05,   // Lower scale factor for more sensitivity
                    2,      // Lower min neighbors
                    0,
                    new Size(image.width() * 0.1, image.height() * 0.1),
                    new Size()
                );
                
                faces = faceDetections.toArray();
                System.out.println("Profile detector found " + faces.length + " faces");
            } catch (Exception e) {
                System.err.println("Error during profile face detection: " + e.getMessage());
            }
        }
        
        if (faces.length == 0) {
            System.out.println("No faces detected. Centering the entire image instead.");
            // Fall back to centering the entire image if no faces are detected
            return centerEntireImage(image, targetWidth, targetHeight);
        }
        
        // Calculate the center of all faces
        int faceCenterX = 0;
        int faceCenterY = 0;
        
        // Prioritize larger faces (they're likely the main subject)
        // and give more weight to faces closer to the center
        if (faces.length > 1) {
            System.out.println("Multiple faces detected - prioritizing by size and position");
            
            // Sort faces by area (largest first)
            Arrays.sort(faces, (a, b) -> Integer.compare(b.width * b.height, a.width * a.height));
            
            // Get total weight and weighted positions
            double totalWeight = 0;
            double weightedX = 0;
            double weightedY = 0;
            
            for (int i = 0; i < faces.length; i++) {
                Rect face = faces[i];
                double area = face.width * face.height;
                double weight = area; // Base weight is area
                
                // Calculate center of this face
                int faceX = face.x + face.width / 2;
                int faceY = face.y + face.height / 2;
                
                // Add to weighted position sums
                weightedX += faceX * weight;
                weightedY += faceY * weight;
                totalWeight += weight;
                
                System.out.println("Face " + (i+1) + ": x=" + face.x + ", y=" + face.y + 
                                   ", width=" + face.width + ", height=" + face.height +
                                   ", area=" + area + ", weight=" + weight);
            }
            
            // Calculate weighted center
            faceCenterX = (int)(weightedX / totalWeight);
            faceCenterY = (int)(weightedY / totalWeight);
            System.out.println("Using weighted average of " + faces.length + " faces");
        } else {
            // Just use the single face center
            Rect face = faces[0];
            faceCenterX = face.x + face.width / 2;
            faceCenterY = face.y + face.height / 2;
            System.out.println("Face detected at: x=" + face.x + ", y=" + face.y + 
                               ", width=" + face.width + ", height=" + face.height);
        }
        
        // Calculate the image center
        int imageCenterX = image.width() / 2;
        int imageCenterY = image.height() / 2;
        
        // Calculate the offset to move the face center to the image center
        // Only adjust horizontally, keep vertical position the same
        int offsetX = imageCenterX - faceCenterX;
        int offsetY = 0; // Set to 0 to keep vertical position unchanged
        
        System.out.println("Image center: (" + imageCenterX + "," + imageCenterY + ")");
        System.out.println("Face center: (" + faceCenterX + "," + faceCenterY + ")");
        System.out.println("Horizontal offset to center face: " + offsetX);
        
        // Create a transformation matrix for translation
        Mat translationMatrix = new Mat(2, 3, CvType.CV_32F);
        translationMatrix.put(0, 0, 1, 0, offsetX, 0, 1, offsetY);
        
        // Apply the translation
        Mat centeredImage = new Mat();
        Imgproc.warpAffine(image, centeredImage, translationMatrix, image.size(), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(255, 255, 255));
        
        System.out.println("Horizontal centering applied successfully");
        
        // Convert the OpenCV Mat to a BufferedImage
        BufferedImage bufferedImage = matToBufferedImage(centeredImage);
        
        // Create a new blank image with the target dimensions
        BufferedImage outputImage = new BufferedImage(
            targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        
        // Create graphics context and fill with white background
        Graphics2D g2d = outputImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, targetWidth, targetHeight);
        
        // Calculate position to center the image
        int x = (targetWidth - bufferedImage.getWidth()) / 2;
        int y = (targetHeight - bufferedImage.getHeight()) / 2;
        
        // Draw the centered image
        g2d.drawImage(bufferedImage, x, y, null);
        g2d.dispose();
        
        System.out.println("Face centering (horizontal only) completed");
        return outputImage;
    }
    
    private BufferedImage centerEntireImage(Mat image, int targetWidth, int targetHeight) throws IOException {
        BufferedImage inputImage = matToBufferedImage(image);
        
        BufferedImage outputImage = new BufferedImage(
            targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = outputImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, targetWidth, targetHeight);
        
        int x = (targetWidth - inputImage.getWidth()) / 2;
        int y = (targetHeight - inputImage.getHeight()) / 2;
        
        g2d.drawImage(inputImage, x, y, null);
        g2d.dispose();
        
        return outputImage;
    }
    
    private Mat bufferedImageToMat(BufferedImage image) {
        // Convert BufferedImage to Mat
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        
        // Convert to BGR first (OpenCV format)
        BufferedImage bgr = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = bgr.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        
        // Get byte array from image
        byte[] data = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        
        return mat;
    }
    
    private BufferedImage matToBufferedImage(Mat mat) throws IOException {
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, matOfByte);
        byte[] byteArray = matOfByte.toArray();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
        return ImageIO.read(byteArrayInputStream);
    }

    public ResponseEntity<String> detectFaceOnly(FaceCenteringRequest request, ImageState state) {
        BufferedImage img = state.getCurrentImage();
        if (img == null) return ResponseEntity.badRequest().body("No image loaded.");
        
        try {
            // Check if face detector is properly initialized
            if (faceDetector == null || faceDetector.empty()) {
                System.err.println("Face detector is not properly initialized for detection only.");
                return ResponseEntity.badRequest().body("Face detector is not properly initialized.");
            }
            
            // Convert BufferedImage to OpenCV Mat
            Mat image = bufferedImageToMat(img);
            
            // Convert to grayscale for face detection
            Mat grayImage = new Mat();
            Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
            
            // Improve contrast for better detection
            Imgproc.equalizeHist(grayImage, grayImage);
            
            System.out.println("Detecting faces in image for visualization...");
            
            // Detect faces with improved parameters
            MatOfRect faceDetections = new MatOfRect();
            // Parameters: image, faces, scaleFactor, minNeighbors, flags, minSize, maxSize
            // Adjust parameters for better detection
            double scaleFactor = 1.1;
            int minNeighbors = 3;
            Size minSize = new Size(image.width() * 0.1, image.height() * 0.1); // Min 10% of image
            
            faceDetector.detectMultiScale(
                grayImage,
                faceDetections,
                scaleFactor,
                minNeighbors,
                0,  // flags
                minSize,
                new Size() // max size (empty = no limit)
            );
            
            Rect[] faces = faceDetections.toArray();
            
            System.out.println("Detected " + faces.length + " faces in the image");
            
            if (faces.length == 0) {
                return ResponseEntity.badRequest().body("No faces detected in the image.");
            }
            
            // Save the original image
            BufferedImage originalImage = state.cloneImage(img);
            
            // Create a temporary copy to draw rectangles on
            BufferedImage tempImage = state.cloneImage(img);
            Graphics2D g2d = tempImage.createGraphics();
            g2d.setColor(Color.GREEN);
            
            // Calculate the center of all faces
            int faceCenterX = 0;
            int faceCenterY = 0;
            
            // Draw rectangles around detected faces
            for (Rect face : faces) {
                g2d.drawRect(face.x, face.y, face.width, face.height);
                
                // Thicker border for visibility
                g2d.drawRect(face.x+1, face.y+1, face.width-2, face.height-2);
                g2d.drawRect(face.x+2, face.y+2, face.width-4, face.height-4);
                
                faceCenterX += face.x + face.width / 2;
                faceCenterY += face.y + face.height / 2;
                
                System.out.println("Face detected at: x=" + face.x + ", y=" + face.y + 
                                   ", width=" + face.width + ", height=" + face.height);
            }
            
            faceCenterX /= faces.length;
            faceCenterY /= faces.length;
            
            // Calculate the image center
            int imageCenterX = image.width() / 2;
            int imageCenterY = image.height() / 2;
            
            // Draw the face center
            g2d.setColor(Color.RED);
            g2d.fillOval(faceCenterX-5, faceCenterY-5, 10, 10);
            
            // Draw the image center
            g2d.setColor(Color.BLUE);
            g2d.fillOval(imageCenterX-5, imageCenterY-5, 10, 10);
            
            // Draw a line connecting centers
            g2d.setColor(Color.YELLOW);
            g2d.drawLine(faceCenterX, faceCenterY, imageCenterX, imageCenterY);
            
            g2d.dispose();
            
            // Return the image with face rectangles as the current image
            state.setCurrentImage(tempImage);
            
            // Construct detailed response
            StringBuilder response = new StringBuilder();
            response.append("Face detection completed. ");
            response.append("Found ").append(faces.length).append(" faces. ");
            response.append("Face center: (").append(faceCenterX).append(",").append(faceCenterY).append("). ");
            response.append("Image center: (").append(imageCenterX).append(",").append(imageCenterY).append("). ");
            response.append("GREEN rectangles: detected faces. ");
            response.append("RED dot: face center. ");
            response.append("BLUE dot: image center. ");
            response.append("YELLOW line: distance to be corrected by centering.");
            
            // Return success with detailed info
            return ResponseEntity.ok(response.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to detect faces: " + e.getMessage());
        }
    }
} 