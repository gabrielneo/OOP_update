package com.example.dto;

import java.util.ArrayList;
import java.util.List;

public class ImageComplianceResponse {
    private boolean compliant;
    private List<String> issues = new ArrayList<>();
    private String message;

    public ImageComplianceResponse() {
        this.compliant = true;
        this.message = "Image is compliant with all standards";
    }

    public boolean isCompliant() {
        return compliant;
    }

    public void setCompliant(boolean compliant) {
        this.compliant = compliant;
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues;
    }
    
    public void addIssue(String issue) {
        this.issues.add(issue);
        this.compliant = false;
        updateMessage();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    
    private void updateMessage() {
        if (issues.isEmpty()) {
            this.message = "Image is compliant with all standards";
        } else {
            this.message = "Image has " + issues.size() + " compliance issue(s)";
        }
    }
} 