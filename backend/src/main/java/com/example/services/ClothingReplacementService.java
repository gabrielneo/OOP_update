package com.example.services;

import com.example.dto.ClothingReplacementRequest;
import com.example.model.ImageState;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import nu.pattern.OpenCV;
import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClothingReplacementService {
    
    // Detailed logging for OpenCV initialization
    static {
        try {
            OpenCV.loadLocally();
            System.out.println("OpenCV Initialization Details:");
            System.out.println("Version: " + Core.VERSION);
            System.out.println("Native Library Path: " + System.getProperty("java.library.path"));
            System.out.println("Loaded successfully with parallel backends:");
            
            try {
                System.out.println("Enabled backends: ONETBB, TBB, OPENMP");
            } catch (Exception backendLoggingError) {
                System.err.println("Could not log parallel backends: " + backendLoggingError.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Critical Error: OpenCV Failed to Load");
            e.printStackTrace();
        }
    }

    // Class labels for segmentation with detailed logging
    private static final String[] CLASSES = {
        "background", "hat", "hair", "sunglasses", "upper-clothes",
        "skirt", "pants", "dress", "belt", "left-shoe",
        "right-shoe", "face", "left-leg", "right-leg", "left-arm",
        "right-arm", "bag", "scarf"
    };

    // Color map for visualization (BGR format for OpenCV)
    private static final Scalar[] COLORS = {
        new Scalar(0, 0, 0),     // 0 background - black
        new Scalar(255, 0, 0),   // 1 hat - blue
        new Scalar(0, 255, 0),   // 2 hair - green
        new Scalar(0, 0, 255),   // 3 sunglasses - red
        new Scalar(255, 255, 0), // 4 upper-clothes - cyan
        new Scalar(255, 0, 255), // 5 skirt - magenta
        new Scalar(0, 255, 255), // 6 pants - yellow
        new Scalar(128, 0, 0),   // 7 dress - dark blue
        // ... rest of the colors remain the same
    };

    // Consistent clothing image paths
    private final Map<String, String> CLOTHING_IMAGES = Map.of(
        "formal", "Clothe images/Suit.png",
        "business", "Clothe images/BusinessCasual.png", 
        "dress", "Clothe images/FormalDress.png"
    );
    
    private final String MODEL_PATH = "models/segformer_b2_clothes.onnx";
    private Resource modelResource;
    private Map<String, Resource> clothingResources = new HashMap<>();
    private OrtEnvironment environment;
    private ConcurrentHashMap<String, OrtSession> sessionCache = new ConcurrentHashMap<>();
    
    public ClothingReplacementService() {
        try {
            System.out.println("Initializing ClothingReplacementService...");
            
            // Initialize ONNX Runtime environment with detailed logging
            try {
                environment = OrtEnvironment.getEnvironment();
                System.out.println("ONNX Runtime Environment initialized successfully");
            } catch (Exception e) {
                System.err.println("Failed to initialize ONNX Runtime Environment");
                e.printStackTrace();
            }
            
            // Create directories with comprehensive logging
            createDirectoriesWithLogging();
            
            // Extract resources with enhanced error handling
            extractResourcesWithDetailedLogging();
            
            // Pre-load model with comprehensive error handling
            try {
                OrtSession initialSession = getOrtSession();
                System.out.println("Model pre-loaded successfully. Input info: " + 
                    initialSession.getInputInfo().keySet());
            } catch (Exception e) {
                System.err.println("Failed to pre-load ONNX model");
                e.printStackTrace();
            }
            
            System.out.println("ClothingReplacementService initialization complete");
        } catch (Exception e) {
            System.err.println("Critical initialization error in ClothingReplacementService");
            e.printStackTrace();
        }
    }
    
    // Enhanced directory creation with logging
    private void createDirectoriesWithLogging() {
        try {
            Path[] directoriesToCreate = {
                Paths.get("models"),
                Paths.get("Clothe images")
            };
            
            for (Path dir : directoriesToCreate) {
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                    System.out.println("Created directory: " + dir.toAbsolutePath());
                } else {
                    System.out.println("Directory already exists: " + dir.toAbsolutePath());
                }
            }
        } catch (IOException e) {
            System.err.println("Error creating necessary directories");
            e.printStackTrace();
        }
    }
    
    // Comprehensive resource extraction method
    private void extractResourcesWithDetailedLogging() {
        // Extract ONNX model
        extractSingleResourceWithLogging(MODEL_PATH, "ONNX Model");
        
        // Extract clothing images
        for (Map.Entry<String, String> entry : CLOTHING_IMAGES.entrySet()) {
            extractSingleResourceWithLogging(entry.getValue(), entry.getKey() + " Clothing Image");
        }
    }
    
    // Single resource extraction with detailed logging
    private void extractSingleResourceWithLogging(String sourcePath, String resourceName) {
        try {
            Resource resource = new ClassPathResource(sourcePath);
            
            // Detailed resource existence check
            if (!resource.exists()) {
                System.err.println("Resource not found: " + sourcePath + " (" + resourceName + ")");
                return;
            }
            
            // Destination path
            Path destinationPath = Paths.get(sourcePath);
            
            // Ensure parent directory exists
            Files.createDirectories(destinationPath.getParent());
            
            // Copy resource
            Files.copy(resource.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            
            System.out.println("Successfully extracted " + resourceName + " from: " + sourcePath);
            System.out.println("Destination: " + destinationPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to extract " + resourceName);
            System.err.println("Source path: " + sourcePath);
            e.printStackTrace();
        }
    }
    
    // Get ONNX Session with comprehensive error handling
    private OrtSession getOrtSession() throws Exception {
        return sessionCache.computeIfAbsent(MODEL_PATH, key -> {
            try {
                // Check if model file exists
                File modelFile = new File(MODEL_PATH);
                if (!modelFile.exists()) {
                    throw new RuntimeException("Model file not found: " + MODEL_PATH);
                }
                
                OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
                sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
                sessionOptions.setIntraOpNumThreads(2);
                sessionOptions.setInterOpNumThreads(2);
                
                return environment.createSession(MODEL_PATH, sessionOptions);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create ONNX session: " + e.getMessage(), e);
            }
        });
    }
    
    // Main clothing replacement method
    public ResponseEntity<String> replaceClothing(ClothingReplacementRequest request, ImageState state) {
        BufferedImage img = state.getCurrentImage();
        if (img == null) return ResponseEntity.badRequest().body("No image loaded.");
        
        try {
            // Save current image to history before processing
            state.pushHistory(state.cloneImage(img));
            
            // Clear future stack when making a new edit
            state.clearFuture();
            
            // Get clothing type from request, defaulting to "formal" if not specified
            String clothingType = request.getClothingType() != null ? request.getClothingType() : "formal";
            
            // Convert to OpenCV Mat
            Mat originalImage = bufferedImageToMat(img);
            
            // Replace clothing with selected type
            BufferedImage resultImage = processImageWithSegFormer(originalImage, img, clothingType);
            
            // Update current image
            state.setCurrentImage(resultImage);
            
            return ResponseEntity.ok("Clothing replaced successfully with " + clothingType + " style.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to replace clothing: " + e.getMessage());
        }
    }
    
    // Complex segmentation and clothing replacement method
    private BufferedImage processImageWithSegFormer(Mat originalImage, BufferedImage originalBufferedImage, String clothingType) throws Exception {
        // Detailed logging for image processing
        System.out.println("Processing clothing replacement for type: " + clothingType);
        System.out.println("Original Image Dimensions: " + originalImage.rows() + "x" + originalImage.cols());
        
        try {
            // Store original dimensions
            int origHeight = originalImage.rows();
            int origWidth = originalImage.cols();
            
            // Load clothing image
            String clothingPath = CLOTHING_IMAGES.getOrDefault(clothingType, "Clothe images/Suit.png");
            Mat replacementImage = Imgcodecs.imread(clothingPath, Imgcodecs.IMREAD_UNCHANGED);
            
            if (replacementImage.empty()) {
                throw new Exception("Failed to load clothing image: " + clothingPath);
            }
            System.out.println("Replacement image dimensions: " + replacementImage.cols() + "x" + replacementImage.rows());
            
            // Check for alpha channel in replacement image and handle it
            Mat processedReplacement = replacementImage.clone();
            Mat alphaMask = null;
            
            if (replacementImage.channels() == 4) {
                System.out.println("Replacement image has alpha channel. Processing...");
                List<Mat> channels = new ArrayList<>();
                Core.split(replacementImage, channels);
                
                // Create a 3-channel BGR image
                Mat rgbImage = new Mat();
                List<Mat> rgbChannels = new ArrayList<>();
                rgbChannels.add(channels.get(0)); // B
                rgbChannels.add(channels.get(1)); // G
                rgbChannels.add(channels.get(2)); // R
                Core.merge(rgbChannels, rgbImage);
                
                // Create a binary mask from the alpha channel
                alphaMask = new Mat();
                Imgproc.threshold(channels.get(3), alphaMask, 1, 255, Imgproc.THRESH_BINARY);
                
                // Save the processed replacement image
                processedReplacement = rgbImage;
            }
            
            // Resize image to 512x512 as commonly used for SegFormer
            Mat resizedImage = new Mat();
            Size modelInputSize = new Size(512, 512);
            Imgproc.resize(originalImage, resizedImage, modelInputSize, 0, 0, Imgproc.INTER_AREA);
            
            // Convert to RGB (OpenCV loads as BGR)
            Mat rgbImage = new Mat();
            Imgproc.cvtColor(resizedImage, rgbImage, Imgproc.COLOR_BGR2RGB);
            
            // Normalize image to range [0,1]
            rgbImage.convertTo(rgbImage, CvType.CV_32FC3, 1.0 / 255.0);
            
            // Extract channels and prepare input tensor
            float[] inputData = new float[512 * 512 * 3];
            float[] meanValues = new float[] { 0.485f, 0.456f, 0.406f }; // ImageNet mean
            float[] stdValues = new float[] { 0.229f, 0.224f, 0.225f }; // ImageNet std
            
            // Create 3 channels
            List<Mat> channels = new ArrayList<>();
            Core.split(rgbImage, channels);
            
            // Copy data with normalization (NCHW format)
            int index = 0;
            for (int c = 0; c < 3; c++) { // RGB
                for (int h = 0; h < 512; h++) {
                    for (int w = 0; w < 512; w++) {
                        float pixelValue = (float) channels.get(c).get(h, w)[0];
                        // Normalize using ImageNet mean and std
                        inputData[index++] = (pixelValue - meanValues[c]) / stdValues[c];
                    }
                }
            }
            
            System.out.println("Running model inference...");
            OrtSession session = getOrtSession();
            
            // Prepare input tensor (NCHW format)
            long[] shape = { 1, 3, 512, 512 }; // Batch, Channels, Height, Width
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(inputData), shape);
            
            // Create input map - using the first input name from the model
            Map<String, OnnxTensor> inputMap = new HashMap<>();
            String inputName = session.getInputInfo().keySet().iterator().next();
            System.out.println("Using input name: " + inputName);
            inputMap.put(inputName, inputTensor);
            
            // Run inference
            System.out.println("Running inference...");
            OrtSession.Result result = session.run(inputMap);
            
            // Get output tensor
            OnnxTensor outputTensor = (OnnxTensor) result.get(0);
            
            // Get the output data
            float[][][][] outputData = (float[][][][]) outputTensor.getValue();
            
            // Get number of classes from the output tensor
            int numClasses = outputData[0].length;
            
            // Ensure we have a valid number of classes
            int maxClassIndex = Math.min(numClasses, CLASSES.length);
            
            // Process the output to get segmentation mask
            int outputHeight = outputData[0][0].length;
            int outputWidth = outputData[0][0][0].length;
            Mat segmentationMask = new Mat(outputHeight, outputWidth, CvType.CV_8UC1);
            
            // For each pixel, find the class with the highest probability
            for (int h = 0; h < outputHeight; h++) {
                for (int w = 0; w < outputWidth; w++) {
                    float maxVal = Float.NEGATIVE_INFINITY;
                    int maxClass = 0;
                    
                    // Find class with highest probability
                    for (int c = 0; c < maxClassIndex; c++) {
                        float val = outputData[0][c][h][w];
                        if (val > maxVal) {
                            maxVal = val;
                            maxClass = c;
                        }
                    }
                    
                    // Set pixel value to class index
                    segmentationMask.put(h, w, maxClass);
                }
            }
            
            // Now handle the clothing replacement part with proper transparency handling
            System.out.println("Performing clothing replacement with transparency handling...");
            
            // Resize the segmentation mask to the original image size
            Mat resizedMask = new Mat();
            Imgproc.resize(segmentationMask, resizedMask, new Size(origWidth, origHeight), 0, 0, Imgproc.INTER_LINEAR);
            
            // Create a mask for the "upper-clothes" class (index 4) and "dress" class (index 7)
            Mat upperBodyMask = new Mat();
            Mat dressMask = new Mat();
            Core.compare(resizedMask, new Scalar(4), upperBodyMask, Core.CMP_EQ);
            Core.compare(resizedMask, new Scalar(7), dressMask, Core.CMP_EQ);
            
            // Combine the masks to get all clothing regions
            Mat clothingMask = new Mat();
            Core.bitwise_or(upperBodyMask, dressMask, clothingMask);
            
            // Check if we have any clothing pixels in the image
            int clothingPixelCount = Core.countNonZero(clothingMask);
            
            // Create result image from original
            Mat resultMat = originalImage.clone();
            
            if (clothingPixelCount > 0) {
                System.out.println("Found " + clothingPixelCount + " pixels belonging to clothing");
                
                // Advanced edge smoothing for straighter edges
                // Step 1: Apply a strong open operation first to remove noise and small artifacts
                Mat kernel1 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7));
                Mat openedMask = new Mat();
                Imgproc.morphologyEx(clothingMask, openedMask, Imgproc.MORPH_OPEN, kernel1);
                
                // Step 2: Apply a substantial dilation to expand the mask outward
                Mat kernel2 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(9, 9));
                Mat dilatedMask = new Mat();
                Imgproc.dilate(openedMask, dilatedMask, kernel2);
                
                // Step 3: Apply rectangular closing to create straighter edges
                Mat kernel3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(15, 15));
                Mat straightenedMask = new Mat();
                Imgproc.morphologyEx(dilatedMask, straightenedMask, Imgproc.MORPH_CLOSE, kernel3);
                
                // Step 4: Apply a moderate Gaussian blur for edge softening
                Mat blurredMask = new Mat();
                Imgproc.GaussianBlur(straightenedMask, blurredMask, new Size(11, 11), 5);
                
                // Step 5: Threshold to get clean binary mask with smooth edges
                Mat finalMask = new Mat();
                Imgproc.threshold(blurredMask, finalMask, 127, 255, Imgproc.THRESH_BINARY);
                
                // Step 6: Find contours to further smooth the shape
                List<MatOfPoint> contours = new ArrayList<>();
                Mat hierarchy = new Mat();
                Imgproc.findContours(finalMask.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL,
                        Imgproc.CHAIN_APPROX_SIMPLE);
                
                // If contours were found, use the largest one
                if (!contours.isEmpty()) {
                    // Find the largest contour by area
                    int largestContourIdx = 0;
                    double maxArea = 0;
                    for (int i = 0; i < contours.size(); i++) {
                        double area = Imgproc.contourArea(contours.get(i));
                        if (area > maxArea) {
                            maxArea = area;
                            largestContourIdx = i;
                        }
                    }
                    
                    // Create a smooth approximation of the contour
                    MatOfPoint2f contour2f = new MatOfPoint2f(contours.get(largestContourIdx).toArray());
                    double epsilon = 0.01 * Imgproc.arcLength(contour2f, true); // 1% of perimeter
                    MatOfPoint2f approxCurve = new MatOfPoint2f();
                    Imgproc.approxPolyDP(contour2f, approxCurve, epsilon, true);
                    
                    // Convert back to MatOfPoint for drawing
                    MatOfPoint approxContour = new MatOfPoint();
                    approxCurve.convertTo(approxContour, CvType.CV_32S);
                    
                    // Draw the smoothed contour on a new mask
                    Mat contourMask = Mat.zeros(finalMask.size(), CvType.CV_8UC1);
                    Imgproc.drawContours(contourMask, Arrays.asList(approxContour), 0, new Scalar(255), -1);
                    
                    // Apply a final Gaussian blur for edge softening
                    Imgproc.GaussianBlur(contourMask, contourMask, new Size(21, 21), 7);
                    Imgproc.threshold(contourMask, contourMask, 127, 255, Imgproc.THRESH_BINARY);
                    
                    // Use this contour mask for the replacement
                    finalMask = contourMask;
                }
                
                // Get the bounding box of the clothing region
                Mat nonZeroCoordinates = new Mat();
                Core.findNonZero(finalMask, nonZeroCoordinates);
                Rect boundingBox = Imgproc.boundingRect(nonZeroCoordinates);
                
                System.out.println("Smoothed clothing bounding box: " + boundingBox);
                
                // Resize the replacement to fit the clothing area
                Mat resizedReplacement = new Mat();
                Imgproc.resize(processedReplacement, resizedReplacement,
                        new Size(boundingBox.width, boundingBox.height),
                        0, 0, Imgproc.INTER_CUBIC);
                
                // If we have an alpha mask, resize it too
                Mat resizedAlphaMask = null;
                if (alphaMask != null) {
                    resizedAlphaMask = new Mat();
                    Imgproc.resize(alphaMask, resizedAlphaMask,
                            new Size(boundingBox.width, boundingBox.height),
                            0, 0, Imgproc.INTER_NEAREST);
                }
                
                // Extract the region of interest from the mask
                Mat maskRoi = finalMask.submat(boundingBox);
                
                // Create submat of the result for the region of interest
                Mat resultRoi = resultMat.submat(boundingBox);
                
                // Handle the case where we have an alpha mask
                if (resizedAlphaMask != null) {
                    // Create a combined mask that respects both the segmentation and the alpha transparency
                    Mat combinedMask = new Mat();
                    
                    // Ensure the alpha mask has the same size and type as maskRoi
                    Mat resizedAlphaMaskRoi = resizedAlphaMask.submat(new Rect(0, 0,
                            Math.min(resizedAlphaMask.cols(), maskRoi.cols()),
                            Math.min(resizedAlphaMask.rows(), maskRoi.rows())));
                    
                    // Create a mask that combines the clothing region with the alpha mask
                    Core.bitwise_and(maskRoi, resizedAlphaMaskRoi, combinedMask);
                    
                    // Copy the replacement over the clothing area using the combined mask
                    resizedReplacement.copyTo(resultRoi, combinedMask);
                } else {
                    // If no alpha mask, just use the clothing mask
                    resizedReplacement.copyTo(resultRoi, maskRoi);
                }
                
                System.out.println("Clothing replacement completed successfully");
            } else {
                System.out.println("No clothing regions found in the image. Using original image as result.");
            }
            
            // Clean up resources
            inputTensor.close();
            
            // Convert the result back to BufferedImage
            return matToBufferedImage(resultMat);
            
        } catch (Exception e) {
            System.err.println("Error in clothing replacement: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    // Utility method to convert BufferedImage to OpenCV Mat
    private Mat bufferedImageToMat(BufferedImage image) {
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
    
    // Utility method to convert Mat back to BufferedImage
    private BufferedImage matToBufferedImage(Mat mat) {
        // Create a new BufferedImage with the correct type
        int width = mat.cols();
        int height = mat.rows();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        
        // Get byte array from Mat (OpenCV is BGR)
        byte[] data = new byte[width * height * (int)mat.elemSize()];
        mat.get(0, 0, data);
        
        // Set the data in the BufferedImage directly without color conversion
        if (image.getRaster().getDataBuffer() instanceof DataBufferByte) {
            byte[] targetData = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
            System.arraycopy(data, 0, targetData, 0, data.length);
        }
        
        return image;
    }
    
    // Cleanup method for resources
    public void cleanup() {
        try {
            // Close all sessions in the cache
            for (OrtSession session : sessionCache.values()) {
                session.close();
            }
            sessionCache.clear();
            
            // Close the environment
            if (environment != null) {
                environment.close();
            }
        } catch (Exception e) {
            System.err.println("Error cleaning up resources: " + e.getMessage());
        }
    }
}