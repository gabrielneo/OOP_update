package com.example.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.model.DriveFile;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

/**
 * Service class for Google Drive operations.
 * This class implements the actual business logic for interacting with Google
 * Drive.
 */
@Service
public class GoogleDriveService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${upload.directory}")
    private String uploadDirectory;

    @Value("${google.drive.tokens.directory}")
    private String TOKENS_DIRECTORY_PATH;

    @Value("${google.drive.application.name}")
    private String APPLICATION_NAME;

    private final GoogleDriveConfig driveConfig;
    private Drive driveService;

    @Autowired
    public GoogleDriveService(GoogleDriveConfig driveConfig, Drive driveService) {
        this.driveConfig = driveConfig;
        this.driveService = driveService;
        logger.info("GoogleDriveService initialized with Drive service");
    }

    /**
     * Refreshes the Drive service with the latest credentials.
     * This should be called after authentication to ensure we have a valid service.
     * 
     * @return true if the service was successfully refreshed, false otherwise
     */
    public boolean refreshDriveService() {
        try {
            logger.info("Refreshing Drive service with updated credentials");
            Credential credential = driveConfig.getStoredCredential();

            if (credential == null) {
                logger.warn("Failed to refresh Drive service: No credentials available");
                return false;
            }

            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            this.driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            logger.info("Drive service successfully refreshed with updated credentials");
            return true;
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Error refreshing Drive service", e);
            return false;
        }
    }

    /**
     * Lists files and folders in Google Drive.
     * 
     * @param folderId Optional folder ID to list contents of a specific folder.
     *                 If null, lists root items.
     * @return FileList containing the files from Google Drive
     * @throws IOException If an error occurs while accessing the Drive API
     */
    public FileList listFiles(String folderId) throws IOException {
        // Try to refresh the drive service if needed
        try {
            refreshDriveService();
        } catch (Exception e) {
            logger.warn("Error refreshing Drive service before listing files", e);
        }

        logger.info("Listing files from Google Drive with folderId: {}", folderId);

        // Create query string based on parameters
        StringBuilder queryBuilder = new StringBuilder();

        if (folderId != null && !folderId.isEmpty()) {
            queryBuilder.append("'").append(folderId).append("' in parents");
        } else {
            queryBuilder.append("'root' in parents");
        }

        logger.info("Using query: {}", queryBuilder.toString());

        // Get files from Google Drive
        try {
            FileList result = driveService.files().list()
                    .setQ(queryBuilder.toString())
                    .setPageSize(100)
                    .setFields("files(id, name, mimeType, thumbnailLink, webViewLink, parents)")
                    .execute();

            logger.info("Retrieved {} files from Google Drive", result.getFiles().size());
            return result;
        } catch (Exception e) {
            logger.error("Error listing files from Google Drive with query: {}", queryBuilder.toString(), e);
            throw new IOException("Error listing files from Google Drive: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads a file to Google Drive.
     * 
     * @param file     The file to upload
     * @param folderId Optional folder ID to upload to. If null, uploads to root.
     * @return The created DriveFile object
     * @throws IOException If an error occurs during upload
     */
    public DriveFile uploadFile(MultipartFile file, String folderId) throws IOException {
        InputStream inputStream = file.getInputStream();
        String fileName = file.getOriginalFilename();
        String mimeType = file.getContentType();

        return uploadFile(inputStream, fileName, mimeType, folderId);
    }

    /**
     * Uploads a file to Google Drive from an input stream.
     * 
     * @param inputStream The input stream containing file data
     * @param fileName    The filename to use
     * @param mimeType    The MIME type of the file
     * @param folderId    Optional folder ID to upload to
     * @return The created DriveFile object
     * @throws IOException If an error occurs during upload
     */
    public DriveFile uploadFile(InputStream inputStream, String fileName, String mimeType, String folderId)
            throws IOException {
        // Create file metadata
        File fileMetadata = new File();
        fileMetadata.setName(fileName);

        // Set parent folder if specified
        if (folderId != null && !folderId.isEmpty()) {
            fileMetadata.setParents(Collections.singletonList(folderId));
        }

        // Create file content
        AbstractInputStreamContent fileContent = new InputStreamContent(mimeType, inputStream);

        // Upload file to Google Drive
        File uploadedFile = driveService.files().create(fileMetadata, fileContent)
                .setFields("id, name, mimeType, modifiedTime, webViewLink, size")
                .execute();

        // Convert to DriveFile model
        return new DriveFile(
                uploadedFile.getId(),
                uploadedFile.getName(),
                uploadedFile.getMimeType(),
                new Date(uploadedFile.getModifiedTime().getValue()),
                uploadedFile.getWebViewLink(),
                uploadedFile.getSize() != null ? uploadedFile.getSize() : 0);
    }

    /**
     * Uploads an existing local file to Google Drive.
     * 
     * @param filePath Path to the file on local filesystem
     * @param mimeType The MIME type of the file
     * @param folderId Optional folder ID to upload to
     * @return The created DriveFile object
     * @throws IOException If an error occurs during upload
     */
    public DriveFile uploadLocalFile(String filePath, String mimeType, String folderId) throws IOException {
        // Create file metadata
        java.io.File localFile = new java.io.File(filePath);
        File fileMetadata = new File();
        fileMetadata.setName(localFile.getName());

        // Set parent folder if specified
        if (folderId != null && !folderId.isEmpty()) {
            fileMetadata.setParents(Collections.singletonList(folderId));
        }

        // Create file content
        FileContent fileContent = new FileContent(mimeType, localFile);

        // Upload file to Google Drive
        File uploadedFile = driveService.files().create(fileMetadata, fileContent)
                .setFields("id, name, mimeType, modifiedTime, webViewLink, size")
                .execute();

        // Convert to DriveFile model
        return new DriveFile(
                uploadedFile.getId(),
                uploadedFile.getName(),
                uploadedFile.getMimeType(),
                new Date(uploadedFile.getModifiedTime().getValue()),
                uploadedFile.getWebViewLink(),
                uploadedFile.getSize() != null ? uploadedFile.getSize() : 0);
    }

    /**
     * Updates an existing file in Google Drive.
     * 
     * @param fileId     ID of the file to update
     * @param newContent New file content as a byte array
     * @param newName    Optional new filename (null to keep original)
     * @param mimeType   MIME type of the file
     * @return The updated DriveFile object
     * @throws IOException If an error occurs during update
     */
    public DriveFile updateFile(String fileId, byte[] newContent, String newName, String mimeType) throws IOException {
        try {
            logger.info("Starting to update file: {}, content size: {} bytes, mimeType: {}",
                    fileId, newContent.length, mimeType);

            // Get existing file metadata
            File file = driveService.files().get(fileId).execute();
            logger.info("Retrieved file metadata for {}: name={}, mimeType={}",
                    fileId, file.getName(), file.getMimeType());

            // Update metadata if new name provided
            if (newName != null && !newName.isEmpty()) {
                file.setName(newName);
                logger.info("Updating filename to: {}", newName);
            }

            // Create content with new data
            ByteArrayInputStream inputStream = new ByteArrayInputStream(newContent);
            AbstractInputStreamContent fileContent = new InputStreamContent(mimeType, inputStream);

            // Make the file updatable if it's a Google Document
            if (file.getMimeType() != null && file.getMimeType().startsWith("application/vnd.google-apps")) {
                logger.warn("Attempting to update a Google Apps file ({}). This may not work as expected.",
                        file.getMimeType());
            }

            // Update file on Google Drive
            logger.info("Sending update request to Google Drive API");
            File updatedFile = driveService.files().update(fileId, file, fileContent)
                    .setFields("id, name, mimeType, modifiedTime, webViewLink, size")
                    .execute();

            logger.info("File updated successfully: {}", updatedFile.getId());

            // Convert to DriveFile model
            return new DriveFile(
                    updatedFile.getId(),
                    updatedFile.getName(),
                    updatedFile.getMimeType(),
                    new Date(updatedFile.getModifiedTime().getValue()),
                    updatedFile.getWebViewLink(),
                    updatedFile.getSize() != null ? updatedFile.getSize() : 0);
        } catch (IOException e) {
            logger.error("Error updating file: " + fileId, e);
            throw e;
        }
    }

    /**
     * Creates a new folder in Google Drive.
     * 
     * @param folderName     Name of the folder to create
     * @param parentFolderId Optional parent folder ID (null for root)
     * @return The created DriveFile object representing the folder
     * @throws IOException If an error occurs during folder creation
     */
    public DriveFile createFolder(String folderName, String parentFolderId) throws IOException {
        // Create folder metadata
        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");

        // Set parent folder if specified
        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            folderMetadata.setParents(Collections.singletonList(parentFolderId));
        }

        // Create folder in Google Drive
        File folder = driveService.files().create(folderMetadata)
                .setFields("id, name, mimeType, modifiedTime, webViewLink")
                .execute();

        // Convert to DriveFile model
        return new DriveFile(
                folder.getId(),
                folder.getName(),
                folder.getMimeType(),
                new Date(folder.getModifiedTime().getValue()),
                folder.getWebViewLink(),
                0 // Folders have no size
        );
    }

    /**
     * Gets file content and metadata from Google Drive.
     * 
     * @param fileId ID of the file to get content for
     * @return Map containing file metadata and base64-encoded content
     * @throws IOException If an error occurs while accessing the file
     */
    public Map<String, Object> getFileContent(String fileId) throws IOException {
        // Get file metadata
        File file = driveService.files().get(fileId)
                .setFields("id, name, mimeType, modifiedTime, webViewLink, size")
                .execute();

        // Create result map with metadata
        Map<String, Object> result = new HashMap<>();
        result.put("id", file.getId());
        result.put("name", file.getName());
        result.put("mimeType", file.getMimeType());
        result.put("modifiedTime", new Date(file.getModifiedTime().getValue()));
        result.put("webViewLink", file.getWebViewLink());
        result.put("size", file.getSize() != null ? file.getSize() : 0);

        // Skip content download for folders
        if ("application/vnd.google-apps.folder".equals(file.getMimeType())) {
            return result;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Handle Google Docs files which need to be exported
        if (file.getMimeType().startsWith("application/vnd.google-apps")) {
            // Choose export MIME type based on the Google file type
            String exportMimeType = "application/pdf"; // Default to PDF

            if (file.getMimeType().equals("application/vnd.google-apps.document")) {
                exportMimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; // Export as
                                                                                                            // DOCX
            } else if (file.getMimeType().equals("application/vnd.google-apps.spreadsheet")) {
                exportMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; // Export as XLSX
            } else if (file.getMimeType().equals("application/vnd.google-apps.presentation")) {
                exportMimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation"; // Export
                                                                                                              // as PPTX
            }

            // Update the MIME type in result to match what we're exporting as
            result.put("mimeType", exportMimeType);

            // Export the file
            driveService.files().export(fileId, exportMimeType)
                    .executeMediaAndDownloadTo(outputStream);
        } else {
            // Download regular file content
            driveService.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream);
        }

        // Encode content as base64
        String base64Content = Base64.getEncoder().encodeToString(outputStream.toByteArray());
        result.put("content", base64Content);

        return result;
    }

    /**
     * Clears stored Google Drive authentication tokens to force re-authentication.
     * This will clear the tokens directory and make the next API call require a new
     * login.
     * 
     * @return true if successful, false otherwise
     */
    public boolean clearStoredCredentials() {
        try {
            // Get the tokens directory
            java.io.File tokensDir = new java.io.File(TOKENS_DIRECTORY_PATH);
            if (tokensDir.exists() && tokensDir.isDirectory()) {
                // Delete all files in the directory
                for (java.io.File file : tokensDir.listFiles()) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        return false;
                    }
                }

                // Force slight delay to ensure tokens are fully deleted
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Creates a copy of a file with new content
     * This is an alternative approach to updating a file that might be more
     * reliable
     * for certain file types or permission scenarios.
     * 
     * @param fileId     ID of the original file
     * @param newContent New file content as a byte array
     * @param newName    Optional new filename (null to use original)
     * @param mimeType   MIME type of the file
     * @return The created DriveFile object
     * @throws IOException If an error occurs during operation
     */
    public DriveFile createUpdatedCopy(String fileId, byte[] newContent, String newName, String mimeType)
            throws IOException {
        try {
            logger.info("Creating updated copy of file: {}, content size: {} bytes, mimeType: {}",
                    fileId, newContent.length, mimeType);

            // Get existing file metadata
            File originalFile = driveService.files().get(fileId)
                    .setFields("name,parents")
                    .execute();

            // Prepare new file metadata
            File fileMetadata = new File();
            fileMetadata.setName(newName != null && !newName.isEmpty() ? newName : originalFile.getName());

            // Set the same parent folder as the original file
            if (originalFile.getParents() != null && !originalFile.getParents().isEmpty()) {
                fileMetadata.setParents(originalFile.getParents());
            }

            // Create content with new data
            ByteArrayInputStream inputStream = new ByteArrayInputStream(newContent);
            AbstractInputStreamContent fileContent = new InputStreamContent(mimeType, inputStream);

            // Create new file on Google Drive
            logger.info("Creating new file in Google Drive with updated content");
            File createdFile = driveService.files().create(fileMetadata, fileContent)
                    .setFields("id, name, mimeType, modifiedTime, webViewLink, size")
                    .execute();

            logger.info("New file created successfully: {}", createdFile.getId());

            // Convert to DriveFile model
            return new DriveFile(
                    createdFile.getId(),
                    createdFile.getName(),
                    createdFile.getMimeType(),
                    new Date(createdFile.getModifiedTime().getValue()),
                    createdFile.getWebViewLink(),
                    createdFile.getSize() != null ? createdFile.getSize() : 0);
        } catch (IOException e) {
            logger.error("Error creating updated copy of file: " + fileId, e);
            throw e;
        }
    }

    public boolean isFolder(com.google.api.services.drive.model.File file) {
        return "application/vnd.google-apps.folder".equals(file.getMimeType());
    }

    public byte[] downloadFile(String fileId) throws IOException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            logger.error("Error downloading file from Google Drive", e);
            throw e;
        }
    }

    /**
     * Downloads a thumbnail of a file from Google Drive.
     * If the thumbnail can't be directly downloaded, this will get the file metadata
     * and either return the raw file (if it's small enough) or generate a thumbnail on the backend.
     *
     * @param fileId ID of the file to download a thumbnail for
     * @return byte array containing the thumbnail data
     * @throws IOException If an error occurs during download
     */
    public byte[] downloadThumbnail(String fileId) throws IOException {
        try {
            logger.info("Downloading thumbnail for file ID: {}", fileId);
            
            // Get file metadata first to check MIME type
            File file = driveService.files().get(fileId)
                    .setFields("id, name, mimeType, thumbnailLink, size")
                    .execute();
            
            // Check if we got thumbnail link
            if (file.getThumbnailLink() != null) {
                logger.info("Using Google-provided thumbnail for file: {}", fileId);
                // Download the thumbnail using Apache HttpClient or similar
                try {
                    // Using Java's built-in HTTP client
                    java.net.URL url = new java.net.URL(file.getThumbnailLink());
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    
                    try (java.io.InputStream in = conn.getInputStream();
                         java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        return out.toByteArray();
                    }
                } catch (Exception e) {
                    logger.error("Error downloading thumbnail from URL: {}", file.getThumbnailLink(), e);
                    // Fall back to downloading the whole file
                }
            }
            
            // If we couldn't get the thumbnail or if it failed, download the full file
            // For smaller files this is reasonable
            if (file.getSize() == null || file.getSize() < 1_000_000) { // Less than 1MB
                logger.info("Thumbnail not available, file size is small, downloading full file: {}", fileId);
                return downloadFile(fileId);
            } else {
                // For large files, return an error message as an image
                logger.warn("File too large for thumbnail and no thumbnail link available: {}", fileId);
                String errorText = "Image too large for preview";
                return errorText.getBytes();
            }
        } catch (IOException e) {
            logger.error("Error downloading thumbnail from Google Drive", e);
            throw e;
        }
    }

    /**
     * Checks if a valid Google Drive token is available.
     * 
     * @return true if a valid token is available, false otherwise
     */
    public boolean isTokenAvailable() {
        try {
            logger.info("Checking if Google Drive token is available");
            
            // Ensure we use the same user ID as the OAuth controller
            final String USER_ID = "user";
            
            // Use driveConfig to get the stored credential for this specific user
            Credential credential = driveConfig.getStoredCredential();
            
            if (credential == null) {
                logger.info("No Google Drive credential found for user: {}", USER_ID);
                return false;
            }

            if (credential.getAccessToken() == null) {
                logger.info("Google Drive credential has no access token for user: {}", USER_ID);
                return false;
            }

            // Check if token is expired and try to refresh if needed
            Long expiresIn = credential.getExpiresInSeconds();
            if (expiresIn != null && expiresIn <= 60) {
                logger.info(
                        "Google Drive token is expired or about to expire (expires in {} seconds), attempting to refresh",
                        expiresIn);
                boolean refreshed = credential.refreshToken();
                if (!refreshed) {
                    logger.warn("Failed to refresh Google Drive token");
                    return false;
                }
                logger.info("Successfully refreshed Google Drive token, new expiration: {} seconds",
                        credential.getExpiresInSeconds());
            }

            // If we don't need to refresh or refresh was successful, the token is valid
            logger.info("Token is valid for user: {}", USER_ID);
            return true;
        } catch (Exception e) {
            logger.error("Error checking token availability: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets debug information about the current credential.
     * This is used for diagnostic purposes.
     * 
     * @return Map containing credential information or null if no credential exists
     */
    public Map<String, Object> getCredentialInfo() {
        try {
            Credential credential = driveConfig.getStoredCredential();

            if (credential == null) {
                return null;
            }

            Map<String, Object> info = new HashMap<>();
            info.put("accessToken", credential.getAccessToken());
            info.put("expiresInSeconds", credential.getExpiresInSeconds());
            info.put("refreshToken", credential.getRefreshToken());

            return info;
        } catch (Exception e) {
            logger.error("Error getting credential info", e);
            return null;
        }
    }

    /**
     * Get detailed file metadata from Google Drive
     * 
     * @param fileId The ID of the file to get details for
     * @return File object with detailed metadata
     * @throws IOException If an error occurs while accessing the Drive API
     */
    public File getFileDetails(String fileId) throws IOException {
        // Try to refresh the drive service if needed
        try {
            refreshDriveService();
        } catch (Exception e) {
            logger.warn("Error refreshing Drive service before getting file details", e);
        }

        logger.info("Getting file details for fileId: {}", fileId);

        try {
            // Request more fields than the basic list endpoint
            File file = driveService.files().get(fileId)
                    .setFields("id, name, mimeType, thumbnailLink, webContentLink, webViewLink, parents, size")
                    .execute();

            logger.info("Retrieved file details for: {}", file.getName());
            return file;
        } catch (Exception e) {
            logger.error("Error getting file details for fileId: {}", fileId, e);
            throw new IOException("Error getting file details: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads an image to Google Drive from a byte array
     * 
     * @param imageData The image data as byte array
     * @param fileName The name to give the file
     * @param mimeType The MIME type of the image (e.g., "image/png")
     * @param folderId Optional folder ID to upload to (null for root)
     * @return The uploaded File object
     * @throws IOException If an error occurs during upload
     */
    public File uploadImage(byte[] imageData, String fileName, String mimeType, String folderId) throws IOException {
        // Try to refresh the drive service if needed
        try {
            refreshDriveService();
        } catch (Exception e) {
            logger.warn("Error refreshing Drive service before uploading image", e);
        }
        
        logger.info("Uploading image to Google Drive: {}, size: {} bytes", fileName, imageData.length);
        
        // Create file metadata
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        
        // Set parent folder if specified
        if (folderId != null && !folderId.isEmpty()) {
            fileMetadata.setParents(Collections.singletonList(folderId));
        }
        
        // Create file content
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData);
        AbstractInputStreamContent fileContent = new InputStreamContent(mimeType, inputStream);
        
        // Upload file to Google Drive
        try {
            File uploadedFile = driveService.files().create(fileMetadata, fileContent)
                    .setFields("id, name, mimeType, modifiedTime, webViewLink, webContentLink")
                    .execute();
            
            logger.info("Image successfully uploaded to Google Drive with ID: {}", uploadedFile.getId());
            return uploadedFile;
        } catch (Exception e) {
            logger.error("Error uploading image to Google Drive", e);
            throw new IOException("Error uploading image to Google Drive: " + e.getMessage(), e);
        }
    }
}