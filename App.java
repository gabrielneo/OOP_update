package com.remover;

import nu.pattern.OpenCV;
import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App {
    public static void main(String[] args) {
        // OpenCV import check
        OpenCV.loadLocally();
        System.out.println("OpenCV version: " + Core.VERSION);

        // ONNX import check
        try (OrtEnvironment env = OrtEnvironment.getEnvironment()) {
            System.out.println("ONNX Runtime version: " + env.getVersion());
        } catch (Exception e) {
            System.err.println("Failed to initialize ONNX Runtime: " + e.getMessage());
        }

        try {
            // Call the method to run the model and remove the background 
            String imagePath = "C:/Users/shahu/OneDrive/Pictures/java Proj test/pic1.jpg"; //simplest with solid white bg
            // String imagePath = "C:/Users/shahu/OneDrive/Pictures/java Proj test/simpleBG1Person.jpg"; //simple  blue bg with person (light gradient)
            // String imagePath = "C:/Users/shahu/OneDrive/Pictures/java Proj test/person with background.webp"; // complex bg and complex hair
            String modelPath = "models/u2net.onnx";
            String outputPath = "output.png";
            
            removeBackground(imagePath, modelPath, outputPath);
            System.out.println("Background removal completed successfully. Output saved to: " + outputPath);
            
        } catch (Exception e) {
            System.out.println("Error during model execution: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void removeBackground(String imagePath, String modelPath, String outputPath) throws Exception {
        // Load image
        Mat originalImage = Imgcodecs.imread(imagePath);
        if (originalImage.empty()) {
            throw new Exception("Could not load image: " + imagePath);
        }
        
        // Store original dimensions
        int origHeight = originalImage.rows();
        int origWidth = originalImage.cols();
        
        // Resize image to 320x320 as required by U2Net
        // Using a slightly higher resolution can improve detail
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
        
        // Create 3 channels (using ArrayList instead of array)
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
        OrtSession session = env.createSession(modelPath, sessionOptions);
        
        // Prepare input tensor (NCHW format)
        long[] shape = {1, 3, 320, 320};  // Batch, Channels, Height, Width
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);
        
        // Create input map
        Map<String, OnnxTensor> inputMap = new HashMap<>();
        inputMap.put("input.1", inputTensor);
        
        // Run inference
        OrtSession.Result result = session.run(inputMap);
        
        // Get output mask (first output is the predicted mask)
        // Fixed ONNX value extraction
        OnnxTensor outputTensor = (OnnxTensor) result.get(0);
        float[][][][] outputMask = (float[][][][]) outputTensor.getValue();
        
        // Process mask to create alpha channel
        Mat mask = new Mat(320, 320, CvType.CV_32FC1);
        for (int y = 0; y < 320; y++) {
            for (int x = 0; x < 320; x++) {
                // Get sigmoid of prediction value (output is in logits)
                float val = outputMask[0][0][y][x];
                float sigmoidVal = 1.0f / (1.0f + (float) Math.exp(-val));
                mask.put(y, x, sigmoidVal);
            }
        }
        
        // Apply image processing to improve mask quality
        // Step 1: Normalize the mask to full range [0,1]
        Core.normalize(mask, mask, 0, 1, Core.NORM_MINMAX);
        
        // Step 2: Apply contrast enhancement to make edges sharper
        // Power function to sharpen transition edges (gamma correction)
        Mat enhancedMask = new Mat(mask.size(), mask.type());
        Core.pow(mask, 0.5, enhancedMask);  // Use power < 1 to sharpen

        // Step 3: Apply adaptive threshold to get better edges
        Mat binaryMask = new Mat();
        double threshold = 0.2;  // Lower threshold to capture more of the person
        Imgproc.threshold(enhancedMask, binaryMask, threshold, 1.0, Imgproc.THRESH_BINARY);
        
        // Step 4: Clean up noise with morphological operations
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Mat cleanedMask = new Mat();
        
        // Close operation to fill small holes
        Imgproc.morphologyEx(binaryMask, cleanedMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Step 5: Apply small amount of blur to smooth edges
        Mat smoothedMask = new Mat();
        Imgproc.GaussianBlur(cleanedMask, smoothedMask, new Size(3, 3), 0);
        
        // Resize mask to original image size with better interpolation
        Mat resizedMask = new Mat();
        Imgproc.resize(smoothedMask, resizedMask, new Size(origWidth, origHeight), 0, 0, Imgproc.INTER_CUBIC);
        
        // Convert back to 8-bit
        resizedMask.convertTo(resizedMask, CvType.CV_8UC1, 255);
        
        // Create transparent image (BGRA)
        Mat transparentImg = new Mat();
        Imgproc.cvtColor(originalImage, transparentImg, Imgproc.COLOR_BGR2BGRA);
        
        // Set alpha channel based on mask
        List<Mat> bgra = new ArrayList<>();
        Core.split(transparentImg, bgra);
        
        // Make sure we have 4 channels (BGRA)
        if (bgra.size() < 4) {
            bgra.add(new Mat(originalImage.size(), CvType.CV_8UC1));
        }
        
        // Set the alpha channel
        resizedMask.copyTo(bgra.get(3));
        
        // Merge back
        Core.merge(bgra, transparentImg);
        
        // Save result
        Imgcodecs.imwrite(outputPath, transparentImg);
        
        // For debugging: save the mask itself to verify
        Imgcodecs.imwrite("mask_" + outputPath, resizedMask);
        
        // Clean up
        inputTensor.close();
        result.close();
        session.close();
        env.close();
    }
}