package com.example.controller;

import com.example.dto.ImageComplianceRequest;
import com.example.dto.ImageComplianceResponse;
import com.example.services.ImageComplianceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/compliance")
public class ImageComplianceController {

    private static final Logger logger = LoggerFactory.getLogger(ImageComplianceController.class);
    private final ImageComplianceService imageComplianceService;

    @Autowired
    public ImageComplianceController(ImageComplianceService imageComplianceService) {
        this.imageComplianceService = imageComplianceService;
    }

    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageComplianceResponse> checkImageCompliance(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "hasReplacedBackground", required = false, defaultValue = "false") boolean hasReplacedBackground,
            @RequestParam(value = "backgroundColor", required = false) String backgroundColor) {
        
        try {
            // Validate hasReplacedBackground flag - it should only be true if backgroundColor is also provided
            if (hasReplacedBackground && (backgroundColor == null || backgroundColor.isEmpty())) {
                logger.warn("hasReplacedBackground flag is true but no backgroundColor provided - setting to false");
                hasReplacedBackground = false;
            }
            
            logger.info("Received compliance check request for image: {}, hasReplacedBackground: {}, backgroundColor: {}", 
                     image.getOriginalFilename(), hasReplacedBackground, backgroundColor);
            
            ImageComplianceRequest request = new ImageComplianceRequest();
            request.setImage(image);
            request.setHasReplacedBackground(hasReplacedBackground);
            request.setBackgroundColor(backgroundColor);
            
            return imageComplianceService.checkImageCompliance(request);
        } catch (Exception e) {
            logger.error("Error in compliance check endpoint", e);
            
            ImageComplianceResponse errorResponse = new ImageComplianceResponse();
            errorResponse.addIssue("Server error: " + e.getMessage());
            
            return ResponseEntity.ok(errorResponse);
        }
    }
} 