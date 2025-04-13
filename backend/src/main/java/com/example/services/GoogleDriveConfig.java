package com.example.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Configuration class for Google Drive API integration.
 * This class follows the principles of inversion of control and dependency
 * injection.
 */
@Configuration
public class GoogleDriveConfig {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveConfig.class);

    @Value("${google.drive.application.name}")
    private String APPLICATION_NAME;

    @Value("${google.drive.credentials.path}")
    private String CREDENTIALS_FILE_PATH;

    @Value("${google.drive.tokens.directory}")
    private String TOKENS_DIRECTORY_PATH;

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    // Ensure consistent USER_ID across all components
    private static final String USER_ID = "user";
    
    // @Autowired
    // private GoogleAuthorizationCodeFlow authorizationCodeFlow;

    /**
     * Creates the GoogleAuthorizationCodeFlow for OAuth2 authorization.
     * This is exposed as a bean for use in the auth controller.
     * 
     * @return GoogleAuthorizationCodeFlow object
     * @throws IOException If the credentials file cannot be found
     */
    // @Bean
    public GoogleAuthorizationCodeFlow authorizationCodeFlow() throws IOException {
        // Load client secrets from classpath
        InputStream in = getClass().getResourceAsStream("/credential.json");
        if (in == null) {
            throw new IOException("Resource not found: credential.json");
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Ensure tokens directory exists
        File tokensDir = new File(TOKENS_DIRECTORY_PATH);
        if (!tokensDir.exists()) {
            tokensDir.mkdirs();
            logger.info("Created tokens directory: {}", TOKENS_DIRECTORY_PATH);
        }

        // Build and return the authorization flow
        return new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                JSON_FACTORY,
                clientSecrets,
                SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokensDir))
                .setAccessType("offline")
                .build();
    }

    /**
     * Creates an authorized Credential object.
     * This uses the stored credential if available, otherwise returns null
     * indicating authentication is needed.
     *
     * @return An authorized Credential object, or null if not authenticated.
     * @throws IOException If there's an error accessing stored credentials
     */
    public Credential getStoredCredential() throws IOException {
        // Try to get stored credential for the user
        try {
            return authorizationCodeFlow().loadCredential(USER_ID);
        } catch (Exception e) {
            logger.warn("Failed to load stored credential: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Creates a Drive service object.
     * This bean is used throughout the application to interact with Google Drive
     * API.
     * Note: This will attempt to use stored credentials. If they don't exist or are
     * invalid,
     * the service may fail until proper authentication is completed via the web
     * interface.
     * 
     * @return a Drive service object
     * @throws IOException              If the credentials.json file cannot be
     *                                  found.
     * @throws GeneralSecurityException If there is a security issue with Google
     *                                  APIs
     */
    // @Bean
    public Drive driveService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        // Try to get stored credential (may be null if not authenticated)
        Credential credential = getStoredCredential();

        if (credential == null) {
            logger.warn(
                    "No valid credential found. Google Drive API calls will fail until user authenticates via web interface.");
        }

        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}