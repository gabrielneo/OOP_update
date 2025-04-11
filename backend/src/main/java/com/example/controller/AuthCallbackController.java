package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

import com.example.services.GoogleDriveService;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to handle the direct OAuth callback from Google.
 * This matches the exact redirect URI in the credential.json file.
 */
@Controller
@RequestMapping("/auth/google")
public class AuthCallbackController {
    private static final Logger logger = LoggerFactory.getLogger(AuthCallbackController.class);
    private static final String USER_ID = "user"; // Must match OAuthController's USER_ID

    private final OAuthController oAuthController;
    private final GoogleDriveService driveService;

    @Autowired
    public AuthCallbackController(OAuthController oAuthController, GoogleDriveService driveService) {
        this.oAuthController = oAuthController;
        this.driveService = driveService;
    }

    /**
     * Handles the OAuth callback from Google.
     * This is the exact path specified in the credential.json file.
     */
    @GetMapping("/callback")
    public RedirectView handleCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {

        logger.info("Direct callback received at /auth/google/callback with code (first 5 chars): {}",
                code.substring(0, Math.min(code.length(), 5)));

        // Delegate the credential handling to the main OAuthController
        try {
            ResponseEntity<?> result = oAuthController.handleCallback(code, state);
            logger.info("Callback processing completed with status: {}", result.getStatusCode());

            // Check if token was properly stored
            if (driveService.isTokenAvailable()) {
                logger.info("Token verification successful - token is available");
            } else {
                logger.warn("Token verification failed - token is NOT available after authentication");
            }

            // Always refresh the drive service
            boolean refreshed = driveService.refreshDriveService();
            logger.info("Drive service refreshed: {}", refreshed);

            // Redirect to the editing page or frontend
            return new RedirectView("http://localhost:5173/editingPage");
        } catch (Exception e) {
            logger.error("Error processing callback", e);
            // Redirect to an error page
            return new RedirectView("http://localhost:5173/error?message=" + e.getMessage());
        }
    }
}