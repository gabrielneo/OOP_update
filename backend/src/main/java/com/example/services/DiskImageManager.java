package com.example.services;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.services.memory.ImageStore;

@Service
public class DiskImageManager extends ImageManager {

    private String diskPath;

    // Constructor only needs imageStore to store images in memory
    @Autowired
    public DiskImageManager(ImageStore imageStore) {
        super(imageStore); // Inject ImageStore into the parent class (ImageManager)
    }

    // Method to dynamically set diskPath (for saving images later)
    public void setDiskPath(String diskPath) {
        this.diskPath = diskPath;
    }

    @Override
    public void saveToStorage(String key) {
        BufferedImage img = imageStore.getImage(key); // Retrieve image from shared memory
        try {
            File outputFile = new File(diskPath + "/" + key);
            ImageIO.write(img, "jpg", outputFile);
            System.out.println("Saved image to disk: " + outputFile.getPath());
        } catch (IOException e) {
            System.out.println("Error saving image to disk.");
        }
    }
}
