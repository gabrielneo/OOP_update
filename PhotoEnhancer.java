import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.File;
import javax.imageio.ImageIO;

public class PhotoEnhancer extends JFrame {
    private BufferedImage originalImage, currentImage;
    private JLabel imageLabel;
    private JSlider brightnessSlider, contrastSlider;
    private JButton loadButton, saveButton, resetButton;
    private float brightness = 1.0f, contrast = 1.0f;

    public PhotoEnhancer() {
        super("Photo Enhancer");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Image display area
        imageLabel = new JLabel("Please load an image to begin editing", JLabel.CENTER);
        add(new JScrollPane(imageLabel), BorderLayout.CENTER);

        // Control panel
        setupControlPanel();

        setVisible(true);
    }

    private void setupControlPanel() {
        JPanel controlPanel = new JPanel(new GridLayout(3, 1, 5, 5));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        loadButton = new JButton("Load Image");
        saveButton = new JButton("Save Image");
        resetButton = new JButton("Reset");
        buttonPanel.add(loadButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(resetButton);

        // Sliders
        JPanel brightnessPanel = createSliderPanel("Brightness", -100, 100, 0, 50);
        JPanel contrastPanel = createSliderPanel("Contrast", -100, 100, 0, 50);

        // Add panels to control panel
        controlPanel.add(buttonPanel);
        controlPanel.add(brightnessPanel);
        controlPanel.add(contrastPanel);
        add(controlPanel, BorderLayout.SOUTH);

        // Set up action listeners
        setupActionListeners();

        // Disable controls until image is loaded
        setControlsEnabled(false);
    }

    private JPanel createSliderPanel(String name, int min, int max, int value, int majorTick) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(name));

        JSlider slider = new JSlider(JSlider.HORIZONTAL, min, max, value);
        slider.setMajorTickSpacing(majorTick);
        slider.setMinorTickSpacing(majorTick / 5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);

        panel.add(slider);

        // Store slider in class field
        if (name.equals("Brightness"))
            brightnessSlider = slider;
        else if (name.equals("Contrast"))
            contrastSlider = slider;

        return panel;
    }

    private void setupActionListeners() {
        // Button listeners
        loadButton.addActionListener(e -> loadImage());
        saveButton.addActionListener(e -> saveImage());
        resetButton.addActionListener(e -> resetImage());

        // Slider listeners
        brightnessSlider.addChangeListener(e -> {
            if (!brightnessSlider.getValueIsAdjusting()) {
                brightness = 1.0f + (brightnessSlider.getValue() / 100.0f);
                applyFilters();
            }
        });

        contrastSlider.addChangeListener(e -> {
            if (!contrastSlider.getValueIsAdjusting()) {
                contrast = 1.0f + (contrastSlider.getValue() / 100.0f);
                applyFilters();
            }
        });
    }

    private void setControlsEnabled(boolean enabled) {
        saveButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        brightnessSlider.setEnabled(enabled);
        contrastSlider.setEnabled(enabled);
    }

    private void loadImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Image Files", "jpg", "jpeg", "png", "gif", "bmp"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                originalImage = ImageIO.read(fileChooser.getSelectedFile());
                currentImage = deepCopy(originalImage);
                displayImage(currentImage);

                setControlsEnabled(true);
                resetSliders();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading image: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveImage() {
        if (currentImage == null)
            return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".png")) {
                    file = new File(file.getAbsolutePath() + ".png");
                }

                ImageIO.write(currentImage, "png", file);
                JOptionPane.showMessageDialog(this, "Image saved successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving image: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void resetImage() {
        if (originalImage == null)
            return;

        resetSliders();
        currentImage = deepCopy(originalImage);
        displayImage(currentImage);
    }

    private void resetSliders() {
        brightnessSlider.setValue(0);
        contrastSlider.setValue(0);
        brightness = contrast = 1.0f;
    }

    private void applyFilters() {
        if (originalImage == null)
            return;

        currentImage = deepCopy(originalImage);
        applyBrightnessContrast();
        displayImage(currentImage);
    }

    private void applyBrightnessContrast() {
        float[] scales = { contrast, contrast, contrast, 1.0f };
        float[] offsets = {
                brightness * 255 - 128 * contrast + 128 - 255,
                brightness * 255 - 128 * contrast + 128 - 255,
                brightness * 255 - 128 * contrast + 128 - 255,
                0.0f
        };

        new RescaleOp(scales, offsets, null).filter(currentImage, currentImage);
    }

    private void displayImage(BufferedImage image) {
        if (image == null) {
            imageLabel.setIcon(null);
            imageLabel.setText("No image loaded");
            return;
        }

        imageLabel.setIcon(new ImageIcon(image));
        imageLabel.setText("");
        pack();
    }

    private Dimension getScaledImageDimension() {
        if (currentImage == null)
            return new Dimension(0, 0);

        int imgWidth = currentImage.getWidth();
        int imgHeight = currentImage.getHeight();
        Dimension viewSize = imageLabel.getSize();

        double scale = Math.min(
                (double) viewSize.width / imgWidth,
                (double) viewSize.height / imgHeight);

        return new Dimension((int) (imgWidth * scale), (int) (imgHeight * scale));
    }

    private BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PhotoEnhancer::new);
    }
}