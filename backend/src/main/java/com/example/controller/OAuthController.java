package com.example.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:3000", "http://localhost:8080" }, allowCredentials = "true")
public class OAuthController {
    private static final Logger logger = LoggerFactory.getLogger(OAuthController.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String USER_ID = "user";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    @Value("${google.drive.credentials.path}")
    private Resource credentialsPath;

    @Value("${oauth.redirect.base-url}")
    private String redirectBaseUrl;

    @Value("${oauth.callback.path}")
    private String callbackPath;

    private final ApplicationContext applicationContext;
    private GoogleAuthorizationCodeFlow flow;

    public OAuthController(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        logger.info("OAuthController initialized with redirectBaseUrl: {} and callbackPath: {}",
                redirectBaseUrl, callbackPath);

        // Ensure tokens directory exists and is writable
        File tokensDirectory = new File(TOKENS_DIRECTORY_PATH);
        if (!tokensDirectory.exists()) {
            boolean created = tokensDirectory.mkdirs();
            logger.info("Created tokens directory: {}, success: {}", tokensDirectory.getAbsolutePath(), created);
        } else {
            logger.info("Tokens directory already exists at: {}", tokensDirectory.getAbsolutePath());
        }

        // Check if directory is writable
        if (!tokensDirectory.canWrite()) {
            logger.error("TOKENS DIRECTORY IS NOT WRITABLE: {}", tokensDirectory.getAbsolutePath());
        } else {
            logger.info("Tokens directory is writable");
        }
    }

    private GoogleAuthorizationCodeFlow getFlow() throws IOException {
        if (flow == null) {
            logger.info("Initializing GoogleAuthorizationCodeFlow");
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                    new InputStreamReader(credentialsPath.getInputStream()));

            // Create tokens directory if it doesn't exist
            File tokensDirectory = new File(TOKENS_DIRECTORY_PATH);
            if (!tokensDirectory.exists()) {
                tokensDirectory.mkdirs();
            }

            flow = new GoogleAuthorizationCodeFlow.Builder(
                    new NetHttpTransport(),
                    JSON_FACTORY,
                    clientSecrets,
                    Collections.singleton(DriveScopes.DRIVE))
                    .setDataStoreFactory(new FileDataStoreFactory(tokensDirectory))
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();
            logger.info("GoogleAuthorizationCodeFlow initialized successfully");
        }
        return flow;
    }

    @GetMapping("/url")
    public ResponseEntity<Map<String, String>> getAuthorizationUrl(HttpSession session) {
        try {
            // Use the exact redirect URI as registered in Google Cloud Console
            // credential.json
            // This is hardcoded to match the specific URI string in credential.json
            String redirectUri = "http://localhost:8080/auth/google/callback";
            logger.info("Using hardcoded redirect URI: {}", redirectUri);

            AuthorizationCodeRequestUrl authorizationUrl = getFlow()
                    .newAuthorizationUrl()
                    .setRedirectUri(redirectUri)
                    .setState("state-token");

            String url = authorizationUrl.build();
            logger.info("Generated authorization URL: {}", url);

            Map<String, String> response = new HashMap<>();
            response.put("url", url);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error generating authorization URL", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {
        try {
            // Use the exact redirect URI as registered in Google Cloud Console
            // credential.json
            String redirectUri = "http://localhost:8080/auth/google/callback";
            logger.info("Handling callback with code: {} (first 5 chars)",
                    code.substring(0, Math.min(code.length(), 5)));
            logger.info("State parameter: {}", state);
            logger.info("Using hardcoded redirect URI: {}", redirectUri);

            // Get the flow and create token request
            TokenResponse tokenResponse = getFlow()
                    .newTokenRequest(code)
                    .setRedirectUri(redirectUri)
                    .execute();

            // Log token information (partially redacted for security)
            logger.info("Received TokenResponse - access token exists: {}", tokenResponse.getAccessToken() != null);
            logger.info("Received TokenResponse - refresh token exists: {}", tokenResponse.getRefreshToken() != null);

            // Store the credential with detailed logging
            logger.info("About to store credential for user: {}", USER_ID);
            try {
                getFlow().createAndStoreCredential(tokenResponse, USER_ID);
                logger.info("Successfully stored credential");

                // Verify the credential was actually stored
                var storedCred = getFlow().loadCredential(USER_ID);
                logger.info("Verification - stored credential exists: {}", storedCred != null);
                logger.info("Verification - stored credential has access token: {}",
                        storedCred != null && storedCred.getAccessToken() != null);
                logger.info("Verification - stored credential has refresh token: {}",
                        storedCred != null && storedCred.getRefreshToken() != null);
            } catch (Exception e) {
                logger.error("Failed to store credential", e);
                throw e;
            }

            // Refresh the Drive service to use the new credentials
            Object driveService = applicationContext.getBean("googleDriveService");
            boolean refreshed = false;
            try {
                // Use reflection to call refreshDriveService method
                java.lang.reflect.Method refreshMethod = driveService.getClass().getMethod("refreshDriveService");
                refreshed = (Boolean) refreshMethod.invoke(driveService);
            } catch (Exception e) {
                logger.error("Error refreshing drive service", e);
            }
            logger.info("Drive service refresh status: {}", refreshed);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully authenticated with Google Drive");
            response.put("redirectUrl", "http://localhost:5173/editingPage");

            logger.info("Successfully authenticated user with Google Drive");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error handling OAuth callback", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("success", false);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus() {
        try {
            var credential = getFlow().loadCredential(USER_ID);
            boolean isLoggedIn = credential != null && credential.getAccessToken() != null;

            Map<String, Object> response = new HashMap<>();
            response.put("isLoggedIn", isLoggedIn);

            logger.info("Checked auth status: {}", isLoggedIn);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error checking auth status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        try {
            getFlow().getCredentialDataStore().delete(USER_ID);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully logged out");

            logger.info("User logged out successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during logout", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/token-debug")
    public ResponseEntity<Map<String, Object>> tokenDebug() {
        try {
            Map<String, Object> result = new HashMap<>();

            // Check tokens directory
            File tokensDirectory = new File(TOKENS_DIRECTORY_PATH);
            result.put("tokensDirectoryExists", tokensDirectory.exists());
            result.put("tokensDirectoryPath", tokensDirectory.getAbsolutePath());
            result.put("tokensDirectoryCanWrite", tokensDirectory.canWrite());

            if (tokensDirectory.exists()) {
                String[] fileList = tokensDirectory.list();
                result.put("tokenFiles", fileList);
                result.put("tokenFilesCount", fileList != null ? fileList.length : 0);
            }

            // Check stored credential
            try {
                var credential = getFlow().loadCredential(USER_ID);
                result.put("credentialExists", credential != null);

                if (credential != null) {
                    result.put("hasAccessToken", credential.getAccessToken() != null);
                    result.put("accessTokenExpiry", credential.getExpiresInSeconds());
                    result.put("hasRefreshToken", credential.getRefreshToken() != null);
                }
            } catch (Exception e) {
                result.put("credentialError", e.getMessage());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error in token debug endpoint", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/clear-tokens")
    public ResponseEntity<Map<String, Object>> clearTokens() {
        try {
            logger.info("Clearing all authentication tokens");

            // Delete all token files in the tokens directory
            File tokensDirectory = new File(TOKENS_DIRECTORY_PATH);
            if (tokensDirectory.exists() && tokensDirectory.isDirectory()) {
                String[] files = tokensDirectory.list();
                boolean allDeleted = true;

                if (files != null) {
                    for (String file : files) {
                        File tokenFile = new File(tokensDirectory, file);
                        boolean deleted = tokenFile.delete();
                        logger.info("Deleting token file: {} - {}", file, deleted ? "success" : "failed");
                        if (!deleted) {
                            allDeleted = false;
                        }
                    }
                }

                // Try to clear the credential store directly
                try {
                    getFlow().getCredentialDataStore().clear();
                    logger.info("Cleared credential data store");
                } catch (Exception e) {
                    logger.error("Error clearing credential data store", e);
                }

                Map<String, Object> response = new HashMap<>();
                response.put("success", allDeleted);
                response.put("message", "Token files cleared");

                // Check if the clear worked
                try {
                    var cred = getFlow().loadCredential(USER_ID);
                    response.put("credentialStillExists", cred != null);
                } catch (Exception e) {
                    response.put("checkError", e.getMessage());
                }

                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Tokens directory does not exist");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("Error clearing tokens", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/fix-tokens")
    public ResponseEntity<Map<String, Object>> fixTokens() {
        try {
            logger.info("Attempting to fix token issues");
            Map<String, Object> response = new HashMap<>();
            
            // 1. Check if credential exists
            var credential = getFlow().loadCredential(USER_ID);
            response.put("credentialExists", credential != null);
            
            if (credential == null) {
                response.put("message", "No credential found - user must authenticate");
                return ResponseEntity.ok(response);
            }
            
            // 2. Check and log token details
            response.put("hasAccessToken", credential.getAccessToken() != null);
            response.put("hasRefreshToken", credential.getRefreshToken() != null);
            
            if (credential.getRefreshToken() == null) {
                response.put("message", "No refresh token - user must re-authenticate with access_type=offline");
                return ResponseEntity.ok(response);
            }
            
            // 3. Attempt to refresh token
            boolean refreshed = credential.refreshToken();
            response.put("tokenRefreshed", refreshed);
            
            if (refreshed) {
                // 4. Store the refreshed credential
                getFlow().createAndStoreCredential(new TokenResponse()
                    .setAccessToken(credential.getAccessToken())
                    .setRefreshToken(credential.getRefreshToken())
                    .setExpiresInSeconds(credential.getExpiresInSeconds()), USER_ID);
                logger.info("Successfully refreshed and stored token");
                
                // 5. Update the Drive service
                Object driveService = applicationContext.getBean("googleDriveService");
                boolean serviceRefreshed = false;
                try {
                    // Use reflection to call refreshDriveService method
                    java.lang.reflect.Method refreshMethod = driveService.getClass().getMethod("refreshDriveService");
                    serviceRefreshed = (Boolean) refreshMethod.invoke(driveService);
                } catch (Exception e) {
                    logger.error("Error refreshing drive service", e);
                }
                response.put("driveServiceRefreshed", serviceRefreshed);
                
                response.put("message", "Successfully fixed token issues");
            } else {
                response.put("message", "Failed to refresh token - user may need to re-authenticate");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fixing tokens", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}