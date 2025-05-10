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
import java.io.File;

public class App {
    // Updated class labels based on provided table
    private static final String[] CLASSES = {
            "background", "hat", "hair", "sunglasses", "upper-clothes",
            "skirt", "pants", "dress", "belt", "left-shoe",
            "right-shoe", "face", "left-leg", "right-leg", "left-arm",
            "right-arm", "bag", "scarf"
    };

    // Color map for visualization (BGR format for OpenCV)
    private static final Scalar[] COLORS = {
            new Scalar(0, 0, 0), // 0 background - black
            new Scalar(255, 0, 0), // 1 hat - blue
            new Scalar(0, 255, 0), // 2 hair - green
            new Scalar(0, 0, 255), // 3 sunglasses - red
            new Scalar(255, 255, 0), // 4 upper-clothes - cyan
            new Scalar(255, 0, 255), // 5 skirt - magenta
            new Scalar(0, 255, 255), // 6 pants - yellow
            new Scalar(128, 0, 0), // 7 dress - dark blue
            new Scalar(0, 128, 0), // 8 belt - dark green
            new Scalar(0, 0, 128), // 9 left-shoe - dark red
            new Scalar(128, 128, 0), // 10 right-shoe - dark cyan
            new Scalar(128, 0, 128), // 11 face - dark magenta
            new Scalar(0, 128, 128), // 12 left-leg - dark yellow
            new Scalar(128, 128, 128), // 13 right-leg - gray
            new Scalar(64, 0, 0), // 14 left-arm - darker blue
            new Scalar(0, 64, 0), // 15 right-arm - darker green
            new Scalar(0, 0, 64), // 16 bag - darker red
            new Scalar(64, 64, 0) // 17 scarf - darker cyan
    };

    public static void main(String[] args) {
        // Load OpenCV
        OpenCV.loadLocally();
        System.out.println("OpenCV version: " + Core.VERSION);

        // Check ONNX Runtime
        try (OrtEnvironment env = OrtEnvironment.getEnvironment()) {
            System.out.println("ONNX Runtime version: " + env.getVersion());
        } catch (Exception e) {
            System.err.println("Failed to initialize ONNX Runtime: " + e.getMessage());
            return;
        }

        try {
            // Default paths - customize as needed
            String imagePath = "C:/Users/shahu/OneDrive/Pictures/java Proj test/test2.jpg";
            String modelPath = "models/segformer_b2_clothes.onnx";
            String replacementImagePath = "C:/Users/shahu/OneDrive/Pictures/java Proj test/77c691902d0ac18e9f9fd1d83625c2c6-removebg-preview(1).png"; // Path
                                                                                                                                                      // to
                                                                                                                                                      // the
                                                                                                                                                      // suit
                                                                                                                                                      // image
            String outputMaskPath = "output_mask.png";
            String outputColoredPath = "output_colored.png";
            String outputBlendedPath = "output_blended.png";
            String outputReplacedPath = "output_replaced.png"; // New output for the replacement

            // Process arguments if provided
            if (args.length >= 3) {
                imagePath = args[0];
                modelPath = args[1];
                replacementImagePath = args[2];
            }
            if (args.length >= 7) {
                outputMaskPath = args[3];
                outputColoredPath = args[4];
                outputBlendedPath = args[5];
                outputReplacedPath = args[6];
            }

            // Create output directory if needed and handle null parent
            createDirectoryIfNeeded(outputMaskPath);
            createDirectoryIfNeeded(outputColoredPath);
            createDirectoryIfNeeded(outputBlendedPath);
            createDirectoryIfNeeded(outputReplacedPath);

            // Run segmentation and replacement
            segmentAndReplaceClothes(imagePath, modelPath, replacementImagePath,
                    outputMaskPath, outputColoredPath, outputBlendedPath, outputReplacedPath);

            System.out.println("Processing completed successfully.");
            System.out.println("Outputs saved to:");
            System.out.println("- Mask: " + outputMaskPath);
            System.out.println("- Colored: " + outputColoredPath);
            System.out.println("- Blended: " + outputBlendedPath);
            System.out.println("- Replaced: " + outputReplacedPath);

        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Helper method to create a directory for an output file if needed
     */
    private static void createDirectoryIfNeeded(String filePath) {
        if (filePath == null)
            return;

        File file = new File(filePath);
        String parent = file.getParent();

        // If the file is in the current directory, parent will be null
        if (parent != null) {
            File parentDir = new File(parent);
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
        }
    }

    public static void segmentAndReplaceClothes(String imagePath, String modelPath, String replacementImagePath,
            String outputMaskPath, String outputColoredPath,
            String outputBlendedPath, String outputReplacedPath) throws Exception {
        // Load image
        Mat originalImage = Imgcodecs.imread(imagePath);
        if (originalImage.empty()) {
            throw new Exception("Could not load image: " + imagePath);
        }

        // Load replacement image (suit)
        Mat replacementImage = Imgcodecs.imread(replacementImagePath, Imgcodecs.IMREAD_UNCHANGED);
        if (replacementImage.empty()) {
            throw new Exception("Could not load replacement image: " + replacementImagePath);
        }

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

        // Store original dimensions
        int origHeight = originalImage.rows();
        int origWidth = originalImage.cols();

        System.out.println("Processing image: " + imagePath);
        System.out.println("Image dimensions: " + origWidth + "x" + origHeight);
        System.out.println("Replacement image: " + replacementImagePath);
        System.out.println("Replacement dimensions: " + replacementImage.cols() + "x" + replacementImage.rows());

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
        // Run the ONNX model
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();

        // Create session
        OrtSession session = null;
        Mat segmentationMask = null;

        try {
            // Check if model file exists
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                throw new Exception("Model file not found: " + modelPath);
            }

            System.out.println("Loading model from: " + modelPath);
            session = env.createSession(modelPath, sessionOptions);

            // Prepare input tensor (NCHW format)
            long[] shape = { 1, 3, 512, 512 }; // Batch, Channels, Height, Width
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

            // Print available inputs for model
            session.getInputInfo().keySet().forEach(name -> System.out.println("Available input: " + name));

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

            // Debug: Print the shape of the output
            long[] outputShape = outputTensor.getInfo().getShape();
            System.out.println("Output tensor shape: " + Arrays.toString(outputShape));

            // Get the output data
            float[][][][] outputData = (float[][][][]) outputTensor.getValue();
            System.out.println("Output data dimensions: [" +
                    outputData.length + ", " +
                    outputData[0].length + ", " +
                    outputData[0][0].length + ", " +
                    outputData[0][0][0].length + "]");

            // Get the actual output dimensions from the model
            int outputHeight = outputData[0][0].length;
            int outputWidth = outputData[0][0][0].length;
            System.out.println("Output dimensions: " + outputWidth + "x" + outputHeight);

            System.out.println("Post-processing results...");

            // Get number of classes from the output tensor
            int numClasses = outputData[0].length;
            System.out.println("Number of output classes: " + numClasses);

            // Ensure we have a valid number of classes
            int maxClassIndex = Math.min(numClasses, CLASSES.length);
            System.out.println("Using " + maxClassIndex + " classes for processing");

            // Process the output to get segmentation mask using the actual dimensions
            segmentationMask = new Mat(outputHeight, outputWidth, CvType.CV_8UC1);

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

            // Store a copy of the original mask before any smoothing
            Mat originalMask = segmentationMask.clone();

            // Apply post-processing to improve edge quality
            System.out.println("Applying edge smoothing...");

            // Upscale with better interpolation before final resize
            Mat upscaledMask = new Mat();
            int upscaleFactor = 4;
            Size upscaledSize = new Size(outputWidth * upscaleFactor, outputHeight * upscaleFactor);
            Imgproc.resize(segmentationMask, upscaledMask, upscaledSize, 0, 0, Imgproc.INTER_NEAREST);

            // Create a kernel for morphological operations
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));

            // Apply morphological operations to smooth edges but preserve class identity
            Mat processedMask = new Mat();

            // Process each class separately to preserve class identity
            List<Mat> classMasks = new ArrayList<>();
            for (int classIdx = 0; classIdx < maxClassIndex; classIdx++) {
                Mat classMask = new Mat();
                Core.compare(upscaledMask, new Scalar(classIdx), classMask, Core.CMP_EQ);

                // Apply smoothing operations
                Mat dilated = new Mat();
                Mat eroded = new Mat();
                Mat smoothed = new Mat();

                // Apply a small dilation and erosion to smooth the edges
                Imgproc.dilate(classMask, dilated, kernel, new Point(-1, -1), 1);
                Imgproc.erode(dilated, smoothed, kernel, new Point(-1, -1), 1);

                classMasks.add(smoothed);
            }

            // Recombine class masks ensuring each pixel has exactly one class
            Mat smoothedMask = new Mat(upscaledMask.size(), CvType.CV_8UC1, new Scalar(0));
            for (int classIdx = 0; classIdx < classMasks.size(); classIdx++) {
                // For each class, set the corresponding pixels in the final mask
                Mat temp = new Mat(smoothedMask.size(), smoothedMask.type(), new Scalar(classIdx));
                temp.copyTo(smoothedMask, classMasks.get(classIdx));
            }

            // Resize to original image size with good interpolation
            Mat resizedMask = new Mat();
            Imgproc.resize(smoothedMask, resizedMask, new Size(origWidth, origHeight),
                    0, 0, Imgproc.INTER_NEAREST); // Use nearest neighbor to preserve exact class values

            // Convert to ensure we have an 8-bit single-channel image for the mask
            resizedMask.convertTo(resizedMask, CvType.CV_8UC1);

            // Create a properly colored visualization
            Mat coloredMask = new Mat(resizedMask.size(), CvType.CV_8UC3, new Scalar(0, 0, 0));

            // Debug - print unique values in the mask
            Mat uniqueValues = new Mat();
            Core.MinMaxLocResult minMax = Core.minMaxLoc(resizedMask);
            System.out.println("Mask min value: " + minMax.minVal + " max value: " + minMax.maxVal);

            // Apply colors to each class mask
            for (int classIdx = 0; classIdx < maxClassIndex; classIdx++) {
                Mat classMask = new Mat();
                Core.compare(resizedMask, new Scalar(classIdx), classMask, Core.CMP_EQ);

                // Check if this class exists in the mask
                double maxVal = Core.countNonZero(classMask);
                System.out.println("Class " + classIdx + " (" +
                        (classIdx < CLASSES.length ? CLASSES[classIdx] : "unknown") +
                        ") pixel count: " + maxVal);

                if (maxVal > 0) {
                    coloredMask.setTo(COLORS[classIdx], classMask);
                }
            }

            // Create blended result
            Mat blendedResult = new Mat();
            Core.addWeighted(originalImage, 0.7, coloredMask, 0.3, 0, blendedResult);

            // Save the standard outputs
            System.out.println("Saving standard output files...");
            Imgcodecs.imwrite(outputMaskPath, resizedMask);
            Imgcodecs.imwrite(outputColoredPath, coloredMask);
            Imgcodecs.imwrite(outputBlendedPath, blendedResult);

            // Clean up resources
            inputTensor.close();
            result.close();

        } finally {
            // Ensure resources are closed even if an exception occurs
            if (session != null) {
                session.close();
            }
            env.close();
        }

        // Modify the segmentAndReplaceClothes method to include dress replacement
        // Replace the existing upper-clothes replacement code (around line 400) with
        // this:

        // Now handle the clothing replacement part with proper transparency handling
        if (segmentationMask != null) {
            System.out.println("Performing upper-clothes and dress replacement with transparency handling...");

            // Resize the segmentation mask to the original image size
            Mat resizedMask = new Mat();
            Imgproc.resize(segmentationMask, resizedMask, new Size(origWidth, origHeight),
                    0, 0, Imgproc.INTER_LINEAR);

            // Create a mask for the "upper-clothes" class (index 4) and "dress" class
            // (index 7)
            Mat upperBodyMask = new Mat();
            Mat dressMask = new Mat();
            Core.compare(resizedMask, new Scalar(4), upperBodyMask, Core.CMP_EQ);
            Core.compare(resizedMask, new Scalar(7), dressMask, Core.CMP_EQ);

            // Combine the masks to get all clothing regions
            Mat clothingMask = new Mat();
            Core.bitwise_or(upperBodyMask, dressMask, clothingMask);

            // Check if we have any clothing pixels in the image
            int clothingPixelCount = Core.countNonZero(clothingMask);

            if (clothingPixelCount > 0) {
                System.out.println(
                        "Found " + clothingPixelCount + " pixels belonging to clothing (upper-clothes and dress)");

                // Advanced edge smoothing for straighter edges

                // Step 1: Apply a strong open operation first to remove noise and small
                // artifacts
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

                // Create a result image
                Mat result = originalImage.clone();

                // Extract the region of interest from the mask
                Mat maskRoi = finalMask.submat(boundingBox);

                // Create submat of the result for the region of interest
                Mat resultRoi = result.submat(boundingBox);

                // Handle the case where we have an alpha mask
                if (resizedAlphaMask != null) {
                    // Create a combined mask that respects both the segmentation and the alpha
                    // transparency
                    Mat combinedMask = new Mat();

                    // Ensure the alpha mask has the same size and type as maskRoi
                    Mat resizedAlphaMaskRoi = resizedAlphaMask.submat(new Rect(0, 0,
                            Math.min(resizedAlphaMask.cols(), maskRoi.cols()),
                            Math.min(resizedAlphaMask.rows(), maskRoi.rows())));

                    // Create a mask that combines the clothing region with the alpha mask
                    // Only copy pixels that are both within the clothing region AND are not
                    // transparent
                    Core.bitwise_and(maskRoi, resizedAlphaMaskRoi, combinedMask);

                    // Copy the replacement over the clothing area using the combined mask
                    resizedReplacement.copyTo(resultRoi, combinedMask);
                } else {
                    // If no alpha mask, just use the clothing mask
                    resizedReplacement.copyTo(resultRoi, maskRoi);
                }

                // Save the final result
                System.out.println(
                        "Saving clothing replacement with proper transparency handling to: " + outputReplacedPath);
                Imgcodecs.imwrite(outputReplacedPath, result);
            } else {
                System.out.println("No clothing regions found in the image. Saving original image as result.");
                Imgcodecs.imwrite(outputReplacedPath, originalImage);
            }
        }
    }
}