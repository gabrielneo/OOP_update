package com.example.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.model.ImageState;
import java.awt.image.BufferedImage;
import com.example.model.ImageState;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

@Service
public class UploadImageService {

    public String saveImage(MultipartFile file, ImageState state) throws IOException {
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) {
                throw new IOException("Unsupported or corrupt image file.");
            }
            state.setCurrentImage(img);
            state.clearHistory();
            return "Image uploaded and stored in memory.";
        } catch (IOException e) {
            System.out.println("Error loading image: " + e.getMessage());
            throw e;
        }
    }
}
