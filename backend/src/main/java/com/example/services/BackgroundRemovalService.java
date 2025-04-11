package com.example.services;

import com.example.dto.BackgroundRemovalRequest;
import com.example.model.ImageState;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import nu.pattern.OpenCV;
import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class BackgroundRemovalService {
    
    // Load OpenCV library statically
    static {
        try {
            OpenCV.loadLocally();
            System.out.println("OpenCV loaded successfully: " + Core.VERSION);
        } catch (Exception e) {
            System.err.println("Failed to load OpenCV: " + e.getMessage());
        }
    }
    
    private final String MODEL_PATH = "models/u2net.onnx";
    private Resource modelResource;
    
    public BackgroundRemovalService() {
        try {
            // Create models directory if it doesn't exist
            Path modelsDir = Paths.get("models");
            if (!Files.exists(modelsDir)) {
                Files.createDirectories(modelsDir);
            }
            
            // Extract model file from classpath if it doesn't exist
            Path modelPath = Paths.get(MODEL_PATH);
            if (!Files.exists(modelPath)) {
                try {
                    modelResource = new ClassPathResource("models/u2net.onnx");
                    Files.copy(modelResource.getInputStream(), modelPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("U2Net model extracted to: " + modelPath.toAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Failed to extract model file: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error setting up model directory: " + e.getMessage());
        }
    }
    
    public ResponseEntity<String> removeBackground(BackgroundRemovalRequest request, ImageState state) {
        BufferedImage img = state.getCurrentImage();
        if (img == null) return ResponseEntity.badRequest().body("No image loaded.");
        
        try {
            // Save current image to history before removing background
            state.pushHistory(state.cloneImage(img));
            
            // Clear future stack when making a new edit
            state.clearFuture();
            
            // Convert to OpenCV Mat
            Mat originalImage = bufferedImageToMat(img);
            
            // Remove background
            BufferedImage resultImage = processImageWithU2Net(originalImage, img);
            
            // Update current image
            state.setCurrentImage(resultImage);
            
            return ResponseEntity.ok("Background removed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to remove background: " + e.getMessage());
        }
    }
    
    private BufferedImage processImageWithU2Net(Mat originalImage, BufferedImage originalBufferedImage) throws Exception {
        // Store original dimensions
        int origHeight = originalImage.rows();
        int origWidth = originalImage.cols();
        
        // Resize image to 320x320 as required by U2Net
        Mat resizedImage = new Mat();
        Size modelInputSize = new Size(320, 320);
        Imgproc.resize(originalImage, resizedImage, modelInputSize, 0, 0, Imgproc.INTER_AREA);
        
        // Convert to RGB if needed (OpenCV loads as BGR)
        Mat rgbImage = new Mat();
        Imgproc.cvtColor(resizedImage, rgbImage, Imgproc.COLOR_BGR2RGB);
        
        // Normalize image to range [0,1] and prepare model input
        rgbImage.convertTo(rgbImage, CvType.CV_32FC3, 1.0/255.0);
        
        // Extract channels and prepare input tensor
        float[] inputData = new float[320 * 320 * 3];
        float[] meanValues = new float[]{0.485f, 0.456f, 0.406f};
        float[] stdValues = new float[]{0.229f, 0.224f, 0.225f};
        
        // Create 3 channels
        List<Mat> channels = new ArrayList<>();
        Core.split(rgbImage, channels);
        
        // Copy data with normalization
        int index = 0;
        for (int c = 0; c < 3; c++) {  // RGB
            for (int h = 0; h < 320; h++) {
                for (int w = 0; w < 320; w++) {
                    float pixelValue = (float) channels.get(c).get(h, w)[0];
                    // Normalize using ImageNet mean and std
                    inputData[index++] = (pixelValue - meanValues[c]) / stdValues[c];
                }
            }
        }
        
        // Run the ONNX model
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        
        // Create session
        String modelPath = MODEL_PATH;
        OrtSession session = env.createSession(modelPath, sessionOptions);
        
        // Prepare input tensor (NCHW format)
        long[] shape = {1, 3, 320, 320};  // Batch, Channels, Height, Width
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);
        
        // Create input map
        Map<String, OnnxTensor> inputMap = new HashMap<>();
        inputMap.put("input.1", inputTensor);
        
        // Run inference
        OrtSession.Result result = session.run(inputMap);
        
        // Get output mask
        OnnxTensor outputTensor = (OnnxTensor) result.get(0);
        float[][][][] outputMask = (float[][][][]) outputTensor.getValue();
        
        // Process mask to create alpha channel
        Mat mask = new Mat(320, 320, CvType.CV_32FC1);
        for (int y = 0; y < 320; y++) {
            for (int x = 0; x < 320; x++) {
                // Get sigmoid of prediction value
                float val = outputMask[0][0][y][x];
                float sigmoidVal = 1.0f / (1.0f + (float) Math.exp(-val));
                mask.put(y, x, sigmoidVal);
            }
        }
        
        // Apply image processing to improve mask quality
        Core.normalize(mask, mask, 0, 1, Core.NORM_MINMAX);
        
        // Apply contrast enhancement
        Mat enhancedMask = new Mat(mask.size(), mask.type());
        Core.pow(mask, 0.5, enhancedMask);  // Use power < 1 to sharpen

        // Apply threshold
        Mat binaryMask = new Mat();
        double threshold = 0.2;
        Imgproc.threshold(enhancedMask, binaryMask, threshold, 1.0, Imgproc.THRESH_BINARY);
        
        // Clean up noise with morphological operations
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Mat cleanedMask = new Mat();
        
        // Close operation to fill small holes
        Imgproc.morphologyEx(binaryMask, cleanedMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Apply small amount of blur to smooth edges
        Mat smoothedMask = new Mat();
        Imgproc.GaussianBlur(cleanedMask, smoothedMask, new Size(3, 3), 0);
        
        // Resize mask to original image size
        Mat resizedMask = new Mat();
        Imgproc.resize(smoothedMask, resizedMask, new Size(origWidth, origHeight), 0, 0, Imgproc.INTER_CUBIC);
        
        // Convert back to 8-bit
        resizedMask.convertTo(resizedMask, CvType.CV_8UC1, 255);
        
        // Convert to BufferedImage with alpha channel
        return createTransparentImage(originalBufferedImage, resizedMask);
        
        // Clean up
        /* 
        inputTensor.close();
        result.close();
        session.close();
        env.close();
        */
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
    
    private BufferedImage createTransparentImage(BufferedImage original, Mat mask) {
        // Create new BufferedImage with alpha channel
        BufferedImage result = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        
        // Get byte array from mask
        byte[] maskData = new byte[mask.rows() * mask.cols()];
        mask.get(0, 0, maskData);
        
        // Copy original image pixels with alpha from mask
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int index = y * original.getWidth() + x;
                int rgb = original.getRGB(x, y);
                
                // Get alpha value from mask (0-255)
                int alpha = index < maskData.length ? maskData[index] & 0xFF : 0;
                
                // Combine original RGB with new alpha
                int rgba = (alpha << 24) | (rgb & 0x00FFFFFF);
                result.setRGB(x, y, rgba);
            }
        }
        
        return result;
    }
} 