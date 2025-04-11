package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Web configuration class for CORS settings and MVC configuration.
 */
@Configuration
public class WebConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    @Value("${oauth.redirect.base-url:http://localhost:5173/editingPage}")
    private String redirectUrl;

    @Value("${oauth.allowed-origins:http://localhost:5173}")
    private String allowedOrigin;

    /**
     * Configure CORS and other web settings for the application.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                logger.info("Configuring CORS with frontend URL: {}", allowedOrigin);
                logger.info("Configuring redirect URL: {}", redirectUrl);

                // Configure CORS for API endpoints
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigin)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);

                // Configure CORS for auth endpoints
                registry.addMapping("/auth/**")
                        .allowedOrigins(allowedOrigin)
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);

                // Configure CORS for OAuth endpoints
                registry.addMapping("/oauth2/**")
                        .allowedOrigins(allowedOrigin)
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);

                logger.info("CORS configuration complete");
            }

            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                // Forward requests to index page for SPA support
                registry.addViewController("/").setViewName("forward:/index.html");
                registry.addViewController("/editingPage").setViewName("forward:/index.html");
                registry.addViewController("/auth").setViewName("forward:/index.html");
                registry.addViewController("/auth/").setViewName("forward:/index.html");
                registry.addViewController("/oauth2/callback").setViewName("forward:/index.html");
                registry.addViewController("/{spring:(?!api|auth|oauth2).*}/**")
                        .setViewName("forward:/index.html");
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/**")
                        .addResourceLocations("classpath:/static/");
            }
        };
    }

    /**
     * Custom error controller to handle errors gracefully
     */
    @RestController
    public static class CustomErrorController implements ErrorController {

        private static final Logger errorLogger = LoggerFactory.getLogger(CustomErrorController.class);

        @RequestMapping("/error")
        public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
            Object status = request.getAttribute("javax.servlet.error.status_code");
            Exception exception = (Exception) request.getAttribute("javax.servlet.error.exception");

            Map<String, Object> response = new HashMap<>();
            response.put("status", status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("error", exception != null ? exception.getMessage() : "An unexpected error occurred");

            errorLogger.error("Error occurred: {}", response);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}