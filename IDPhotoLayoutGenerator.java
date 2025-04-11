import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * ID Photo Layout Generator - Creates sheets with multiple copies of an ID
 * photo
 * in various layouts like 2x2, 4x6, etc.
 */
public class IDPhotoLayoutGenerator extends JFrame {
    private BufferedImage originalImage = null; // Original uploaded image
    private BufferedImage sourceImage = null; // Current working image
    private BufferedImage previewImage = null; // Preview image for display
    private JLabel imagePreview;
    private JComboBox<LayoutOption> layoutSelector;
    private JButton undoButton;
    private File lastDirectory = null;

    public IDPhotoLayoutGenerator() {
        setTitle("ID Photo Layout Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);

        // Create main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Control panel (top)
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        // Upload button
        JButton uploadButton = new JButton("Upload Photo");
        uploadButton.addActionListener(e -> uploadImage());
        controlPanel.add(uploadButton);

        // Layout selector
        layoutSelector = new JComboBox<>(LayoutOption.getStandardOptions().toArray(new LayoutOption[0]));
        layoutSelector.setEnabled(false);
        layoutSelector.addActionListener(e -> updatePreview());
        controlPanel.add(new JLabel("Layout:"));
        controlPanel.add(layoutSelector);

        // Generate button
        JButton generateButton = new JButton("Generate Layout");
        generateButton.setEnabled(false);
        generateButton.addActionListener(e -> generateAndSaveLayout());
        controlPanel.add(generateButton);

        // Undo button
        undoButton = new JButton("Undo");
        undoButton.setEnabled(false);
        undoButton.addActionListener(e -> restoreOriginalImage());
        controlPanel.add(undoButton);

        // Preview panel (center)
        imagePreview = new JLabel("Upload an image to start", JLabel.CENTER);
        imagePreview.setPreferredSize(new Dimension(800, 600));
        imagePreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JScrollPane scrollPane = new JScrollPane(imagePreview);

        // Add components to main panel
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Set panel as content pane
        setContentPane(mainPanel);

        // Update UI based on source image availability
        addPropertyChangeListener("sourceImage", evt -> {
            boolean hasImage = sourceImage != null;
            layoutSelector.setEnabled(hasImage);
            generateButton.setEnabled(hasImage);
            undoButton.setEnabled(hasImage);
            if (hasImage) {
                updatePreview();
            }
        });
    }

    private void uploadImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select ID Photo");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
                "Image files", "jpg", "jpeg", "png", "bmp", "gif"));

        if (lastDirectory != null) {
            fileChooser.setCurrentDirectory(lastDirectory);
        }

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            lastDirectory = selectedFile.getParentFile();

            try {
                // Store both original and working copy of the image
                originalImage = ImageIO.read(selectedFile);
                sourceImage = deepCopy(originalImage); // Make a deep copy

                // Display original image immediately
                displayImage(originalImage);

                firePropertyChange("sourceImage", null, sourceImage);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error loading image: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Restores the original uploaded image, discarding any layout changes
     */
    private void restoreOriginalImage() {
        if (originalImage != null) {
            sourceImage = deepCopy(originalImage);
            displayImage(originalImage);

            // Reset layout selector
            layoutSelector.setSelectedIndex(0);

            // Update status message
            JOptionPane.showMessageDialog(this,
                    "Restored to original image",
                    "Undo Complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Create a deep copy of a BufferedImage
     */
    private BufferedImage deepCopy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(), source.getHeight(), source.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    /**
     * Display an image in the preview area
     */
    private void displayImage(BufferedImage image) {
        if (image == null)
            return;

        // Scale the image to fit the display area
        int maxWidth = imagePreview.getWidth() - 20;
        int maxHeight = imagePreview.getHeight() - 20;

        // If the display area size is 0 (might happen during initialization)
        if (maxWidth <= 0)
            maxWidth = 600;
        if (maxHeight <= 0)
            maxHeight = 400;

        double widthRatio = (double) maxWidth / image.getWidth();
        double heightRatio = (double) maxHeight / image.getHeight();
        double ratio = Math.min(widthRatio, heightRatio);

        int scaledWidth = (int) (image.getWidth() * ratio);
        int scaledHeight = (int) (image.getHeight() * ratio);

        Image scaledImage = image.getScaledInstance(
                scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
        imagePreview.setIcon(new ImageIcon(scaledImage));
        imagePreview.setText("");
    }

    private void updatePreview() {
        if (sourceImage == null || layoutSelector.getSelectedItem() == null) {
            return;
        }

        LayoutOption selectedLayout = (LayoutOption) layoutSelector.getSelectedItem();
        PhotoLayout photoLayout = new PhotoLayout(sourceImage, selectedLayout);

        try {
            // Generate a scaled-down preview
            previewImage = photoLayout.generateSheet();
            displayImage(previewImage);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error creating preview: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generateAndSaveLayout() {
        if (sourceImage == null || layoutSelector.getSelectedItem() == null) {
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Layout");
        fileChooser.setFileFilter(new FileNameExtensionFilter("JPEG Image", "jpg", "jpeg"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));

        if (lastDirectory != null) {
            fileChooser.setCurrentDirectory(lastDirectory);
        }

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            lastDirectory = file.getParentFile();

            // Add extension if missing
            String name = file.getName();
            if (!name.contains(".")) {
                String extension = ((FileNameExtensionFilter) fileChooser.getFileFilter()).getExtensions()[0];
                file = new File(file.getParentFile(), name + "." + extension);
            }

            try {
                LayoutOption selectedLayout = (LayoutOption) layoutSelector.getSelectedItem();
                PhotoLayout photoLayout = new PhotoLayout(sourceImage, selectedLayout);
                photoLayout.saveSheetToFile(file);

                JOptionPane.showMessageDialog(this,
                        "Layout saved successfully to:\n" + file.getAbsolutePath(),
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error saving layout: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Set system look and feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            new IDPhotoLayoutGenerator().setVisible(true);
        });
    }
}

/**
 * Represents a layout configuration for arranging multiple ID photos on a sheet
 */
class LayoutOption {
    private String name;
    private int rows;
    private int columns;
    private double photoWidth; // in cm
    private double photoHeight; // in cm
    private double paperWidth; // in cm
    private double paperHeight; // in cm
    private double horizontalMargin; // in cm
    private double verticalMargin; // in cm
    private double horizontalSpacing; // in cm
    private double verticalSpacing; // in cm

    public LayoutOption(String name, int rows, int columns,
            double photoWidth, double photoHeight,
            double paperWidth, double paperHeight,
            double horizontalMargin, double verticalMargin,
            double horizontalSpacing, double verticalSpacing) {
        this.name = name;
        this.rows = rows;
        this.columns = columns;
        this.photoWidth = photoWidth;
        this.photoHeight = photoHeight;
        this.paperWidth = paperWidth;
        this.paperHeight = paperHeight;
        this.horizontalMargin = horizontalMargin;
        this.verticalMargin = verticalMargin;
        this.horizontalSpacing = horizontalSpacing;
        this.verticalSpacing = verticalSpacing;
    }

    // Getters
    public String getName() {
        return name;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public double getPhotoWidth() {
        return photoWidth;
    }

    public double getPhotoHeight() {
        return photoHeight;
    }

    public double getPaperWidth() {
        return paperWidth;
    }

    public double getPaperHeight() {
        return paperHeight;
    }

    public double getHorizontalMargin() {
        return horizontalMargin;
    }

    public double getVerticalMargin() {
        return verticalMargin;
    }

    public double getHorizontalSpacing() {
        return horizontalSpacing;
    }

    public double getVerticalSpacing() {
        return verticalSpacing;
    }

    /**
     * Factory method to create common layout options
     */
    public static List<LayoutOption> getStandardOptions() {
        List<LayoutOption> options = new ArrayList<>();

        // Single photo option (original)
        options.add(new LayoutOption(
                "Original (Single Photo)",
                1, 1,
                0, 0, // photo size will be original size
                0, 0, // paper size will be original size
                0, 0, // no margins
                0, 0 // no spacing
        ));

        // 2x2 layout on 4x6 inch paper
        options.add(new LayoutOption(
                "2x2 (4x6 inch)",
                2, 2,
                3.5, 4.5, // photo size in cm
                15.24, 10.16, // 6x4 inch paper in cm
                1.0, 0.5, // margins
                0.5, 0.5 // spacing
        ));

        // 4x6 layout on A4 paper
        options.add(new LayoutOption(
                "4x6 (A4)",
                4, 6,
                2.5, 3.5, // photo size in cm
                21.0, 29.7, // A4 paper in cm
                1.0, 1.0, // margins
                0.5, 0.5 // spacing
        ));

        // 2x3 layout on 5x7 inch paper
        options.add(new LayoutOption(
                "2x3 (5x7 inch)",
                2, 3,
                3.0, 4.0, // photo size in cm
                17.78, 12.7, // 7x5 inch paper in cm
                0.5, 0.5, // margins
                0.5, 0.5 // spacing
        ));

        // 6x8 passport photo layout on A4 paper
        options.add(new LayoutOption(
                "6x8 Passport (A4)",
                6, 8,
                2.0, 2.5, // passport photo size in cm
                21.0, 29.7, // A4 paper in cm
                1.5, 1.5, // margins
                0.5, 0.5 // spacing
        ));

        return options;
    }

    @Override
    public String toString() {
        return name;
    }
}

/**
 * Handles creation of photo layout sheets with multiple copies of an ID photo
 */
class PhotoLayout {
    private BufferedImage sourceImage;
    private LayoutOption layoutOption;

    public PhotoLayout(BufferedImage sourceImage, LayoutOption layoutOption) {
        this.sourceImage = sourceImage;
        this.layoutOption = layoutOption;
    }

    /**
     * Generate a sheet with multiple copies of the ID photo
     * based on the selected layout option
     */
    public BufferedImage generateSheet() {
        // Special case for original photo (1x1)
        if (layoutOption.getRows() == 1 && layoutOption.getColumns() == 1 &&
                layoutOption.getPaperWidth() == 0 && layoutOption.getPaperHeight() == 0) {
            // Just return a copy of the original image
            BufferedImage copy = new BufferedImage(
                    sourceImage.getWidth(), sourceImage.getHeight(), sourceImage.getType());
            Graphics2D g = copy.createGraphics();
            g.drawImage(sourceImage, 0, 0, null);
            g.dispose();
            return copy;
        }

        // Convert cm to pixels (assuming 300 DPI)
        int dpi = 300;
        double cmToInch = 0.393701;
        int pixelsPerCm = (int) (dpi * cmToInch);

        int paperWidthPx = (int) (layoutOption.getPaperWidth() * pixelsPerCm);
        int paperHeightPx = (int) (layoutOption.getPaperHeight() * pixelsPerCm);
        int photoWidthPx = (int) (layoutOption.getPhotoWidth() * pixelsPerCm);
        int photoHeightPx = (int) (layoutOption.getPhotoHeight() * pixelsPerCm);
        int horizontalMarginPx = (int) (layoutOption.getHorizontalMargin() * pixelsPerCm);
        int verticalMarginPx = (int) (layoutOption.getVerticalMargin() * pixelsPerCm);
        int horizontalSpacingPx = (int) (layoutOption.getHorizontalSpacing() * pixelsPerCm);
        int verticalSpacingPx = (int) (layoutOption.getVerticalSpacing() * pixelsPerCm);

        // Create a new blank image for the sheet
        BufferedImage sheetImage = new BufferedImage(
                paperWidthPx, paperHeightPx, BufferedImage.TYPE_INT_RGB);

        // Fill background with white
        Graphics2D g2d = sheetImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, paperWidthPx, paperHeightPx);

        // Resize the source image to fit the photo dimensions
        BufferedImage resizedPhoto = resizeImage(sourceImage, photoWidthPx, photoHeightPx);

        // Calculate the total width used by photos and spacing
        int totalPhotoWidth = layoutOption.getColumns() * photoWidthPx
                + (layoutOption.getColumns() - 1) * horizontalSpacingPx;

        // Calculate the total height used by photos and spacing
        int totalPhotoHeight = layoutOption.getRows() * photoHeightPx
                + (layoutOption.getRows() - 1) * verticalSpacingPx;

        // Calculate the starting X and Y coordinates to center the grid on the page
        int startX = (paperWidthPx - totalPhotoWidth) / 2;
        int startY = (paperHeightPx - totalPhotoHeight) / 2;

        // Draw each photo on the sheet
        for (int row = 0; row < layoutOption.getRows(); row++) {
            for (int col = 0; col < layoutOption.getColumns(); col++) {
                int x = startX + col * (photoWidthPx + horizontalSpacingPx);
                int y = startY + row * (photoHeightPx + verticalSpacingPx);

                g2d.drawImage(resizedPhoto, x, y, null);
            }
        }

        g2d.dispose();
        return sheetImage;
    }

    /**
     * Save the generated sheet to a file
     */
    public void saveSheetToFile(File outputFile) throws IOException {
        BufferedImage sheet = generateSheet();
        String extension = getFileExtension(outputFile.getName());
        ImageIO.write(sheet, extension, outputFile);
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1);
        }
        return "jpg"; // Default extension
    }

    /**
     * Resize an image to the specified dimensions
     */
    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return resizedImage;
    }

    // Setters and getters
    public void setLayoutOption(LayoutOption layoutOption) {
        this.layoutOption = layoutOption;
    }

    public LayoutOption getLayoutOption() {
        return layoutOption;
    }
}
