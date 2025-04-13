package com.example;

import com.example.dto.CropRequest;
import com.example.dto.ResizeRequest;
import com.example.dto.BackgroundRemovalRequest;
import com.example.dto.BackgroundReplaceRequest;
import com.example.dto.FaceCenteringRequest;
import com.example.dto.PhotoEnhanceRequest;
import com.example.dto.PhotoLayoutRequest;
import com.example.model.ImageState;
import com.example.services.*;
import com.example.services.memory.ImageStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:3000", "http://localhost:8080" }, allowCredentials = "true")
public class ApiController {

    private final ImageState state = new ImageState();

    // @Autowired
    // private UploadImageService uploadImageService;

    @Autowired
    private CropImageService cropImageService;

    @Autowired
    private ResizeImageService resizeImageService;
    
    @Autowired
    private BackgroundRemovalService backgroundRemovalService;
    
    @Autowired
    private BackgroundReplaceService backgroundReplaceService;
    
    @Autowired
    private FaceCenteringService faceCenteringService;
    
    @Autowired
    private PhotoEnhanceService photoEnhanceService;
    
    @Autowired
    private PhotoLayoutService photoLayoutService;

    @Autowired
    private ImageStore imageStore; // Shared image store (in memory)
    private ImageManager imageManager; // This will hold the current storage strategy

    @Autowired
    public ApiController(DiskImageManager diskImageManager) {
        this.imageManager = diskImageManager; // Start with DiskImageManager
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("files") MultipartFile[] files) {

        try {
            for (MultipartFile file : files) {
                BufferedImage uploadedImage = ImageIO.read(file.getInputStream());

                // Store image in memory using your original DiskImageManager logic
                imageManager.storeImage(file.getOriginalFilename(), uploadedImage);

            }
            // System.out.println("Test"+imageStore.getImageStore().keySet());

            // System.out.println("---- Current Images in ImageStore ----");
            // for (Map.Entry<String, ImageState> entry : imageStore.getImageStore().entrySet()) {
            //     String filename = entry.getKey();
            //     ImageState state = entry.getValue();
            //     BufferedImage image = state != null ? state.getCurrentImage() : null;

            //     System.out.println("Filename: " + filename);
            //     System.out.println("  - Image present: " + (image != null));
            //     if (image != null) {
            //         System.out.println("  - Dimensions: " + image.getWidth() + "x" + image.getHeight());
            //         System.out.println("  - ColorModel: " + image.getColorModel());
            //     }
            // }
            return ResponseEntity.ok("Images uploaded successfully");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Error uploading image(s): " + e.getMessage());
        }
    }

    @PostMapping("/crop")
    public ResponseEntity<?> crop(@RequestParam("key") String key,@RequestBody CropRequest cropRequest) {
        // // Reset background state before cropping
        // backgroundReplaceService.resetBackgroundState();
        // // Clear any stored reference image as the base image is changing
        // state.clearReferenceImage();
        // return cropImageService.crop(cropRequest, state);
        System.out.println(key+" ,test,"+ cropRequest);
        ImageState state = imageStore.getImageState(key);
        if (state == null) {
            return ResponseEntity.badRequest().body("No image found for key: " + key);
        }

        backgroundReplaceService.resetBackgroundState();
        state.clearReferenceImage();
ImageState is= imageStore.getImageState(key);
        return cropImageService.crop(cropRequest, is);
    }

    @PostMapping("/resize")
    public ResponseEntity<?> resize(@RequestBody ResizeRequest resizeRequest) {
        // Reset background state before resizing
        backgroundReplaceService.resetBackgroundState();
        // Clear any stored reference image as the base image is changing
        state.clearReferenceImage();
        return resizeImageService.resize(resizeRequest, state);
    }
    
    @PostMapping("/background-remove")
    public ResponseEntity<?> removeBackground(@RequestBody BackgroundRemovalRequest request) {
        // Reset background state before removing background
        backgroundReplaceService.resetBackgroundState();
        // Clear any stored reference image as the base image is changing
        state.clearReferenceImage();
        return backgroundRemovalService.removeBackground(request, state);
    }
    
    @PostMapping("/background-replace")
    public ResponseEntity<?> replaceBackground(@RequestBody BackgroundReplaceRequest request) {
        // Clear any stored reference image as the base image is changing
        state.clearReferenceImage();
        return backgroundReplaceService.replaceBackground(request, state);
    }
    
    @PostMapping("/detect-face")
    public ResponseEntity<?> detectFace(@RequestBody FaceCenteringRequest request) {
        // Only detect faces and visualize them, without centering
        return faceCenteringService.detectFaceOnly(request, state);
    }
    
    @PostMapping("/face-center")
    public ResponseEntity<?> centerFace(@RequestBody FaceCenteringRequest request) {
        // Reset background state before face centering
        backgroundReplaceService.resetBackgroundState();
        // Clear any stored reference image as the base image is changing
        state.clearReferenceImage();
        return faceCenteringService.centerFace(request, state);
    }

    @PostMapping("/background-image/upload")
    public ResponseEntity<?> uploadBackgroundImage(@RequestParam("file") MultipartFile file) {
        return backgroundReplaceService.uploadBackgroundImage(file);
    }
    
    @PostMapping("/enhancement-preview")
    public ResponseEntity<?> enhancementPreview(@RequestBody PhotoEnhanceRequest request) {
        try {
            BufferedImage currentImage = state.getCurrentImage();
            
            if (currentImage == null) {
                return ResponseEntity.badRequest().body("No image loaded");
            }
            
            // For the first adjustment, always clear any existing reference image to ensure
            // we're using the current state of the image after any operations
            if (request.isFirstAdjustment()) {
                state.clearReferenceImage();
            }
            
            // Store a reference to the current image before any brightness/contrast changes
            // This is needed for previewing different settings without accumulating changes
            if (request.isFirstAdjustment() || state.getReferenceImage() == null) {
                // Store a clone of the current image as the reference for future adjustments
                state.storeReferenceImage(state.cloneImage(currentImage));
                request.setPreviewInProgress(true);
            }
            
            // Get the reference image (the image before any brightness/contrast adjustments)
            BufferedImage referenceImage = state.getReferenceImage();
            
            // For preview, we always apply to the reference image
            if (request.getBrightness() == 0 && request.getContrast() == 0) {
                // If values are 0, restore the reference image
                state.setCurrentImage(state.cloneImage(referenceImage));
            } else {
                // Create a temporary preview without saving to history
                BufferedImage previewImage = photoEnhanceService.createPreview(request, state.cloneImage(referenceImage));
                // Store the preview image temporarily
                state.setCurrentImage(previewImage);
            }
            
            return ResponseEntity.ok("Enhancement preview generated");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error generating enhancement preview: " + e.getMessage());
        }
    }
    
    @PostMapping("/enhance")
    public ResponseEntity<?> enhancePhoto(@RequestBody PhotoEnhanceRequest request) {
        return photoEnhanceService.enhancePhoto(request, state);
    }

    @PostMapping("/layout")
    public ResponseEntity<?> createLayout(@RequestBody PhotoLayoutRequest request) {
        // Reset background state before creating layout
        backgroundReplaceService.resetBackgroundState();
        // Clear any stored reference image as the base image is changing
        state.clearReferenceImage();
        return photoLayoutService.createLayout(request, state);
    }

    @GetMapping("/image/all")
    public ResponseEntity<Map<String, byte[]>> getAllImagesAsBytes() {
        Map<String, byte[]> imageMap = new HashMap<>();

        for (Map.Entry<String, ImageState> entry : imageStore.getImageStore().entrySet()) {
            String filename = entry.getKey();
            BufferedImage image = entry.getValue().getCurrentImage(); // ❗ potential NPE here

            if (image != null) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", baos); // ❗ can throw IOException
                    imageMap.put(filename, baos.toByteArray());
                } catch (IOException e) {
                    e.printStackTrace(); // this shows in your logs
                }
            }
        }

        return ResponseEntity.ok(imageMap);
    }
    @GetMapping("/image/get")
    public ResponseEntity<?> getCurrentImage() throws IOException {
        BufferedImage img = state.getCurrentImage();
        if (img == null)
            return ResponseEntity.notFound().build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(baos.toByteArray());

        // Map<String, byte[]> imageMap = new HashMap<>();

        // for (Map.Entry<String, ImageState> entry : imageStore.getImageStore().entrySet()) {
        //     String filename = entry.getKey();
        //     BufferedImage image = entry.getValue().getCurrentImage(); // Get current image

        //     if (image != null) {
        //         try {
        //             ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //             ImageIO.write(image, "png", baos);
        //             imageMap.put(filename, baos.toByteArray()); // Add byte[] value
        //         } catch (IOException e) {
        //             e.printStackTrace(); // Optional: log it better in production
        //         }
        //     }
        // }

        // return ResponseEntity.ok(imageMap);
    }

    @GetMapping("/image/dimensions")
    public ResponseEntity<?> getImageDimensions(@RequestParam(required = false) Boolean original) {
        BufferedImage img;
        
        if (Boolean.TRUE.equals(original)) {
            img = state.getOriginalImage();
            if (img == null) {
                // If original not available, fall back to current image
                img = state.getCurrentImage();
            }
        } else {
            img = state.getCurrentImage();
        }
        
        if (img == null)
            return ResponseEntity.notFound().build();
        
        Map<String, Integer> dimensions = Map.of(
            "width", img.getWidth(),
            "height", img.getHeight()
        );
        
        return ResponseEntity.ok()
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")
            .body(dimensions);
    }

    @GetMapping("/undo")
    public ResponseEntity<?> undo() {
        if (!state.hasHistory()) {
            return ResponseEntity.badRequest().body("No history available");
        }

        BufferedImage previous = state.popHistory();
        state.pushFuture(state.getCurrentImage()); // Save current to future stack for redo
        state.setCurrentImage(previous);
        // Reset background state when undoing
        backgroundReplaceService.resetBackgroundState();
        // Clear any stored reference image when undoing
        state.clearReferenceImage();
        return ResponseEntity.ok("Undo successful");
    }

    @GetMapping("/redo")
    public ResponseEntity<?> redo() {
        if (!state.hasFuture()) {
            return ResponseEntity.badRequest().body("No future states available");
        }

        BufferedImage future = state.popFuture();
        state.pushHistory(state.getCurrentImage()); // Save current to history stack for undo
        state.setCurrentImage(future);
        // Reset background state when redoing
        backgroundReplaceService.resetBackgroundState();
        // Clear any stored reference image when redoing
        state.clearReferenceImage();
        return ResponseEntity.ok("Redo successful");
    }

    @GetMapping("/reset")
    public ResponseEntity<?> reset() {
        BufferedImage originalImage = state.getOriginalImage();
        if (originalImage == null) {
            return ResponseEntity.badRequest().body("No original image available");
        }

        state.pushHistory(state.getCurrentImage());
        state.setCurrentImage(state.cloneImage(originalImage));
        state.clearFuture(); // Clear future stack after reset
        // Reset background state when resetting to original
        backgroundReplaceService.resetBackgroundState();
        // Clear any stored reference image when resetting
        state.clearReferenceImage();
        return ResponseEntity.ok("Reset successful - restored to original image");
    }

    @GetMapping("/history/status")
    public ResponseEntity<Map<String, Boolean>> getHistoryStatus() {
        return ResponseEntity.ok(Map.of(
                "hasHistory", state.hasHistory(),
                "hasOriginal", state.getOriginalImage() != null,
                "hasFuture", state.hasFuture()));
    }

    @GetMapping("/image/export")
    public ResponseEntity<?> exportImage(@RequestParam(required = false) String filename) throws IOException {
        BufferedImage img = state.getCurrentImage();
        if (img == null)
            return ResponseEntity.notFound().build();

        // Default filename if not provided
        if (filename == null || filename.isEmpty()) {
            filename = "id-photo-export.png";
        } else if (!filename.endsWith(".png")) {
            filename = filename + ".png";
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(baos.toByteArray());
    }
}
