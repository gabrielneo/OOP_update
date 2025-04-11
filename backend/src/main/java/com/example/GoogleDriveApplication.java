package com.example;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GoogleDriveApplication {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveApplication.class);

    @Value("${google.drive.tokens.directory}")
    private String tokensDirectory;

    public static void main(String[] args) {
        SpringApplication.run(GoogleDriveApplication.class, args);
    }

    /**
     * Clear any stored Google credentials on startup to ensure a fresh login flow
     */
    @Bean
    public CommandLineRunner clearGoogleTokensOnStartup() {
        return args -> {
            logger.info("Checking for existing Google tokens to clear...");
            File tokensDir = new File(tokensDirectory);

            if (tokensDir.exists() && tokensDir.isDirectory()) {
                File[] tokenFiles = tokensDir.listFiles();
                if (tokenFiles != null && tokenFiles.length > 0) {
                    logger.info("Found {} token files, deleting...", tokenFiles.length);
                    for (File tokenFile : tokenFiles) {
                        if (tokenFile.delete()) {
                            logger.info("Successfully deleted token file: {}", tokenFile.getName());
                        } else {
                            logger.warn("Failed to delete token file: {}", tokenFile.getName());
                        }
                    }
                } else {
                    logger.info("No token files found to delete");
                }
            } else {
                logger.info("Tokens directory not found or not a directory: {}", tokensDirectory);
                tokensDir.mkdirs();
                logger.info("Created tokens directory: {}", tokensDirectory);
            }
        };
    }
}