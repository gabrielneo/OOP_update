package com.example.controller;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.model.DriveFile;
import com.example.services.GoogleDriveService;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/drive")
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:3000", "http://localhost:8080" }, allowCredentials = "true")
public class DriveController {
    private final GoogleDriveService driveService;
    private static final Logger logger = LoggerFactory.getLogger(DriveController.class);
    private static final String USER_ID = "user";

    public DriveController(GoogleDriveService driveService) {
        this.driveService = driveService;
    }

    @GetMapping("/files")
    public ResponseEntity<?> listFiles(
            @RequestParam(required = false) String folderId,
            @RequestParam(required = false, defaultValue = "false") boolean imagesOnly) {
        try {
            logger.info("Listing Google Drive files with folderId: {}, imagesOnly: {}", folderId, imagesOnly);

            // Check if token exists
            if (!driveService.isTokenAvailable()) {
                logger.error("No valid Google Drive token available. User must authenticate first.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("User must authenticate with Google Drive first");
            }

            FileList result = driveService.listFiles(folderId);
            List<File> files = result.getFiles();

            if (imagesOnly) {
                logger.info("Filtering for image files only");
                files = files.stream()
                        .filter(file -> file.getMimeType() != null &&
                                (file.getMimeType().startsWith("image/") ||
                                        driveService.isFolder(file)))
                        .collect(Collectors.toList());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("files", files);

            logger.info("Successfully retrieved {} files from Google Drive", files.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error listing files from Google Drive", e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to list files: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    @GetMapping("/files/{fileId}/content")
    public ResponseEntity<?> getFileContent(
            @PathVariable String fileId,
            @RequestParam(required = false, defaultValue = "false") boolean thumbnail) {
        try {
            logger.info("Getting file content for ID: {}, thumbnail: {}", fileId, thumbnail);
            
            // Check if token exists
            if (!driveService.isTokenAvailable()) {
                logger.error("No valid Google Drive token available. User must authenticate first.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("User must authenticate with Google Drive first");
            }
            
            byte[] content;
            if (thumbnail) {
                // Get thumbnail instead of full file content
                content = driveService.downloadThumbnail(fileId);
            } else {
                // Get full file content
                content = driveService.downloadFile(fileId);
            }
            
            String base64Content = Base64.getEncoder().encodeToString(content);

            Map<String, String> response = new HashMap<>();
            response.put("content", base64Content);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting file content", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to get file content: " + e.getMessage());
        }
    }

    @GetMapping("/files/{fileId}/details")
    public ResponseEntity<?> getFileDetails(@PathVariable String fileId) {
        try {
            logger.info("Getting detailed file metadata for file ID: {}", fileId);
            
            // Check if token exists
            if (!driveService.isTokenAvailable()) {
                logger.error("No valid Google Drive token available. User must authenticate first.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("User must authenticate with Google Drive first");
            }
            
            File file = driveService.getFileDetails(fileId);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            
            // If it's an image file, generate a direct access link
            if (file.getMimeType() != null && file.getMimeType().startsWith("image/")) {
                // Create a webContentLink if it doesn't exist
                if (file.getWebContentLink() == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", file.getId());
                    response.put("name", file.getName());
                    response.put("mimeType", file.getMimeType());
                    response.put("webContentLink", "https://drive.google.com/uc?export=view&id=" + file.getId());
                    response.put("thumbnailLink", file.getThumbnailLink());
                    
                    return ResponseEntity.ok(response);
                }
            }
            
            return ResponseEntity.ok(file);
        } catch (Exception e) {
            logger.error("Error getting file details", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to get file details: " + e.getMessage());
        }
    }

    @GetMapping("/refresh")
    public ResponseEntity<?> refreshDriveService() {
        try {
            logger.info("Manually refreshing Drive service");

            // First clear any existing credentials
            driveService.clearStoredCredentials();
            logger.info("Cleared existing tokens");

            // Force recreate the flow and credentials
            boolean refreshed = driveService.refreshDriveService();

            Map<String, Object> response = new HashMap<>();
            response.put("success", refreshed);
            response.put("message",
                    refreshed ? "Successfully refreshed Drive service" : "Failed to refresh Drive service");

            logger.info("Drive service refresh result: {}", refreshed);

            if (!refreshed) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(response);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error refreshing Drive service", e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to refresh Drive service: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    @GetMapping("/token-status")
    public ResponseEntity<?> getTokenStatus() {
        try {
            logger.info("Checking token status");
            boolean isTokenAvailable = driveService.isTokenAvailable();

            Map<String, Object> response = new HashMap<>();
            response.put("isTokenAvailable", isTokenAvailable);

            if (isTokenAvailable) {
                logger.info("Token is available and valid");
                response.put("message", "Token is available and valid");
            } else {
                logger.info("No valid token available");
                response.put("message", "No valid token available");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error checking token status", e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to check token status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    @GetMapping("/token-debug")
    public ResponseEntity<?> getTokenDebug() {
        try {
            logger.info("Debug token information");
            Map<String, Object> response = new HashMap<>();

            try {
                // Get credential information
                Map<String, Object> credential = driveService.getCredentialInfo();
                if (credential != null) {
                    response.put("hasCredential", true);
                    response.put("hasAccessToken", credential.get("accessToken") != null);
                    response.put("expiresInSeconds", credential.get("expiresInSeconds"));
                    response.put("hasRefreshToken", credential.get("refreshToken") != null);
                } else {
                    response.put("hasCredential", false);
                }
            } catch (Exception e) {
                response.put("credentialError", e.getMessage());
            }

            // Also check token availability
            response.put("isTokenAvailable", driveService.isTokenAvailable());

            // Check tokens directory
            java.io.File tokensDir = new java.io.File("./tokens");
            response.put("tokensDirectoryExists", tokensDir.exists());
            response.put("tokensDirectoryPath", tokensDir.getAbsolutePath());
            if (tokensDir.exists()) {
                response.put("tokenFiles", tokensDir.list());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in token debug endpoint", e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error in token debug: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    @GetMapping("/test-connection")
    public ResponseEntity<?> testDriveConnection() {
        try {
            logger.info("Testing Google Drive connection and token validity");
            Map<String, Object> response = new HashMap<>();

            // Step 1: Check if token is available - use stored credential directly
            boolean isTokenAvailable = driveService.isTokenAvailable();
            response.put("tokenAvailable", isTokenAvailable);
            logger.info("Token availability check: {}", isTokenAvailable);

            if (!isTokenAvailable) {
                response.put("error", "No valid token available");
                response.put("loginRequired", true);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Step 2: Try to make a simple Drive API call
            try {
                logger.info("Attempting to list files (limited to 1) as connection test");
                // Use the listFiles method instead of accessing private driveService field
                FileList result = driveService.listFiles(null);

                if (result != null && result.getFiles() != null) {
                    int fileCount = result.getFiles().size();
                    logger.info("Connection test successful - retrieved {} files", fileCount);

                    response.put("connectionSuccessful", true);
                    response.put("fileCount", fileCount);

                    // Include first file name for verification if available
                    if (fileCount > 0) {
                        response.put("sampleFileName", result.getFiles().get(0).getName());
                    }
                } else {
                    response.put("connectionSuccessful", true);
                    response.put("fileCount", 0);
                    response.put("message", "No files found but connection successful");
                }

                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Connection test failed with error", e);
                response.put("connectionSuccessful", false);
                response.put("connectionError", e.getMessage());

                // Try refreshing the service
                boolean refreshed = driveService.refreshDriveService();
                response.put("refreshAttempted", true);
                response.put("refreshSuccessful", refreshed);

                if (refreshed) {
                    try {
                        // Try again after refresh
                        FileList result = driveService.listFiles(null);

                        if (result != null && result.getFiles() != null) {
                            response.put("retrySuccessful", true);
                            response.put("fileCount", result.getFiles().size());
                        } else {
                            response.put("retrySuccessful", true);
                            response.put("fileCount", 0);
                        }
                        return ResponseEntity.ok(response);
                    } catch (Exception retryEx) {
                        response.put("retrySuccessful", false);
                        response.put("retryError", retryEx.getMessage());
                    }
                }

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (Exception e) {
            logger.error("Error testing Drive connection", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to test connection: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    @PostMapping("/upload")
    public ResponseEntity<?> uploadToDrive(@RequestBody Map<String, String> request) {
        try {
            logger.info("Uploading image to Google Drive");
            
            // Check if token exists
            if (!driveService.isTokenAvailable()) {
                logger.error("No valid Google Drive token available. User must authenticate first.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("User must authenticate with Google Drive first");
            }
            
            String filename = request.get("filename");
            String imageData = request.get("imageData");
            
            if (filename == null || filename.isEmpty()) {
                filename = "id-photo-" + java.time.LocalDateTime.now().toString().replace(":", "-");
            }
            
            // Ensure filename has a proper extension
            if (!filename.toLowerCase().endsWith(".png") && !filename.toLowerCase().endsWith(".jpg") && 
                !filename.toLowerCase().endsWith(".jpeg")) {
                filename += ".png";
            }
            
            // Remove "data:image/xxx;base64," part if present
            if (imageData != null && imageData.contains(";base64,")) {
                imageData = imageData.substring(imageData.indexOf(";base64,") + 8);
            }
            
            // Convert base64 to bytes
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            
            // Upload to Google Drive
            String mimeType = "image/png";
            if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            }
            
            File uploadedFile = driveService.uploadImage(imageBytes, filename, mimeType, null);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileId", uploadedFile.getId());
            response.put("fileName", uploadedFile.getName());
            response.put("webViewLink", uploadedFile.getWebViewLink());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error uploading to Google Drive", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to upload to Google Drive: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }
}