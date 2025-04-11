package com.example.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.json.gson.GsonFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.example.services.GoogleDriveService;

/**
 * Controller for handling Google OAuth2 authentication.
 * This controller provides endpoints for initiating the OAuth flow
 * and handling the callback from Google.
 */
@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:3000",
        "http://localhost:8080" }, allowCredentials = "true")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Value("${google.drive.credentials.path}")
    private String CREDENTIALS_FILE_PATH;

    @Value("${google.drive.tokens.directory}")
    private String TOKENS_DIRECTORY_PATH;

    @Value("${google.drive.application.name}")
    private String APPLICATION_NAME;

    private static final String CALLBACK_URI = "/auth/google/callback";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final GoogleAuthorizationCodeFlow authorizationFlow;
    private final GoogleDriveService driveService;

    @Autowired
    public AuthController(GoogleAuthorizationCodeFlow authorizationFlow, GoogleDriveService driveService) {
        this.authorizationFlow = authorizationFlow;
        this.driveService = driveService;
        logger.info("AuthController initialized with authorization flow");
    }

    /**
     * Root endpoint for auth API to check if it's available
     * 
     * @return Basic info about available auth endpoints
     */
    @GetMapping("")
    public ResponseEntity<?> authRoot() {
        Map<String, Object> info = new HashMap<>();
        info.put("message", "Google Auth API is available");
        info.put("endpoints", new String[] { "/google", "/google/callback", "/status", "/logout" });
        return ResponseEntity.ok(info);
    }

    /**
     * Initiates the Google OAuth2 authorization flow.
     * Redirects the user to Google's consent page.
     * 
     * @param request The HTTP request
     * @return A redirect to Google's authorization page
     */
    @GetMapping("/google")
    public RedirectView googleAuth(HttpServletRequest request) throws IOException {
        logger.info("Starting Google Auth flow");

        String baseUrl = String.format("%s://%s:%s",
                request.getScheme(),
                request.getServerName(),
                request.getServerPort());

        // Use the injected authorization flow
        GoogleAuthorizationCodeRequestUrl url = authorizationFlow.newAuthorizationUrl();
        String redirectUrl = url.setRedirectUri(baseUrl + CALLBACK_URI)
                .setAccessType("offline")
                .set("prompt", "select_account consent") // Force account selection
                .build();

        logger.info("Redirecting to Google Auth URL: {}", redirectUrl);
        return new RedirectView(redirectUrl);
    }

    /**
     * Handles the callback from Google's OAuth2 server.
     * Exchanges the authorization code for an access token.
     * 
     * @param code     The authorization code from Google
     * @param session  The HTTP session
     * @param response The HTTP response
     * @return A redirect to the application home page
     */
    @GetMapping("/google/callback-disabled")
    public String googleCallback(@RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {
        // ... existing method body ...
        return null; // Placeholder return, actual implementation needed
    }

    /**
     * Returns the authentication status.
     * 
     * @return Authentication status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getAuthStatus() {
        Map<String, Object> status = new HashMap<>();
        try {
            // Use a simple mechanism to check if we're authenticated
            // In a more complex app, you'd check the session or tokens
            java.io.File tokenDir = new java.io.File(TOKENS_DIRECTORY_PATH);
            java.io.File[] tokenFiles = tokenDir.listFiles();

            if (tokenFiles != null && tokenFiles.length > 0) {
                status.put("authenticated", true);
                status.put("message", "User is authenticated with Google");
            } else {
                status.put("authenticated", false);
                status.put("message", "User is not authenticated with Google");
            }

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error checking authentication status", e);
            status.put("authenticated", false);
            status.put("message", "Error checking authentication: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(status);
        }
    }

    /**
     * Logs out the user by clearing stored credentials.
     * 
     * @return Logout status
     */
    @GetMapping("/logout")
    public ResponseEntity<?> logout() {
        Map<String, Object> status = new HashMap<>();
        try {
            // Delete token files to "log out"
            java.io.File tokenDir = new java.io.File(TOKENS_DIRECTORY_PATH);
            java.io.File[] tokenFiles = tokenDir.listFiles();

            boolean allDeleted = true;
            if (tokenFiles != null) {
                for (java.io.File file : tokenFiles) {
                    if (!file.delete()) {
                        allDeleted = false;
                    }
                }
            }

            status.put("success", allDeleted);
            status.put("message", allDeleted ? "Logged out successfully" : "Some credentials couldn't be removed");

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error during logout", e);
            status.put("success", false);
            status.put("message", "Error during logout: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(status);
        }
    }

    // Helper method to determine the frontend URL for redirects
    private String getFrontendUrl(HttpServletRequest request) {
        // Use the configured frontend URL from application.properties
        return "http://localhost:5173";
    }
}