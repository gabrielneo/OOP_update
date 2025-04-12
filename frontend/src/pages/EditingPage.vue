<template>
  <div class="editing-page">
    <input
      type="file"
      ref="fileInput"
      @change="uploadNewImage"
      style="display: none"
      accept="image/*"
    />
    <!-- Header Component -->
    <Header
      @new-project="createNewProject"
      @save-project="saveProject"
      @export-project="exportProject"
      @undo="undoAction"
      @reset="resetAction"
      @redo="redoAction"
      :canUndo="canUndo"
      :canRedo="canRedo"
      :canReset="canReset"
      :imageDimensions="imageDimensions"
    >
      <template v-slot:google-drive-button>
        <GoogleDriveImagePicker @image-selected="handleDriveImage" class="header-google-drive-btn" />
      </template>
      <template v-slot:google-drive-export-button>
        <GoogleDriveExportButton 
          @export-success="handleExportSuccess" 
          @export-error="handleExportError"
          @auth-required="handleAuthRequired"
          class="header-google-drive-btn" 
        />
      </template>
    </Header>

    <div class="main-container">
      <!-- Feature Panel Component -->
      <FeaturePanel
        :activeFeature="activeFeature"
        @feature-selected="setActiveFeature"
      />

      <!-- Image Component -->
      <ImageComponent
        ref="imageComponent"
        :image="currentPhoto || '/assets/zhiyuan.jpg'"
        :activeFeature="activeFeature"
        :cropWidthMm="cropWidth"
        :cropHeightMm="cropHeight"
        :imageDimensions="imageDimensions"
        :maintain-aspect-ratio="true"
        v-model:hide-crop-on-apply="hideCropOnApply"
        @crop-area="cropArea = $event"
        @crop-complete="handleCropComplete"
        @resize-dimensions="handleResizeDimensions"
      />

      <!-- Control Panel Component (only shown when a feature is selected) -->
      <ControlPanel
        v-if="activeFeature"
        :feature="activeFeature"
        :cropWidth="cropWidth"
        :cropHeight="cropHeight"
        :imageDimensions="imageDimensions"
        @update:cropWidth="cropWidth = $event"
        @update:cropHeight="cropHeight = $event"
        @apply-changes="applyChanges"
        @apply-dimensions="setManualDimensions"
        @faceDetectionChange="handleFaceDetectionChange"
        @upload-background-image="handleBackgroundImageUpload"
        @enhancement-preview="handleEnhancementPreview"
        @close="closeControlPanel"
      />
    </div>
  </div>
</template>

<script>
import Header from "../components/Header.vue";
import FeaturePanel from "../components/FeaturePanel.vue";
import ImageComponent from "../components/ImageComponent.vue";
import ControlPanel from "../components/ControlPanel.vue";
import GoogleDriveImagePicker from "../components/GoogleDriveImagePicker.vue";
import GoogleDriveExportButton from "../components/GoogleDriveExportButton.vue";

export default {
  components: {
    Header,
    FeaturePanel,
    ImageComponent,
    ControlPanel,
    GoogleDriveImagePicker,
    GoogleDriveExportButton,
  },
  data() {
    return {
      activeFeature: null,
      currentPhoto: null,
      canUndo: false,
      canRedo: false,
      canReset: false,
      cropArea: null,
      cropWidth: 35,
      cropHeight: 45,
      imageDimensions: null,
      imageWidth: 0,
      imageHeight: 0,
      // For redo functionality
      futureStack: [],
      // For enhancement preview throttling
      previewThrottleTimeout: null,
      isPreviewLoading: false,
      hideCropOnApply: false,
    };
  },
  watch: {
    // Watch for feature change to set appropriate defaults
    activeFeature(newVal) {
      if (newVal === "crop") {
        console.log("Feature changed to crop: Setting default crop dimensions");
        // Force updating the crop dimensions when changing to crop feature
        this.updateCropDimensions(35, 45);
      }
    },
    // Log crop dimensions changes for debugging
    cropWidth(newVal) {
      console.log(`EditingPage: cropWidth changed to ${newVal}`);
    },
    cropHeight(newVal) {
      console.log(`EditingPage: cropHeight changed to ${newVal}`);
    },
  },
  methods: {
    setActiveFeature(feature) {
      this.activeFeature = feature;
      console.log(`Active feature set to: ${feature}`);
      
      // Always fetch image dimensions when any feature is selected
      if (this.currentPhoto) {
        this.fetchImageDimensions();
      }
      
      // Special handling for crop feature
      if (feature === "crop") {
        console.log("Feature changed to crop: Setting default crop dimensions");
        this.updateCropDimensions(35, 45);
      }
    },
    closeControlPanel() {
      this.activeFeature = null;
    },
    createNewProject() {
      this.$refs.fileInput.click(); // trigger file selection
    },
    saveProject() {
      console.log("Saving project");
    },
    exportProject(filename) {
      console.log("Exporting project with filename:", filename);
      
      // Check if there's an image to export
      if (!this.currentPhoto) {
        this.showNotification("No image to export. Please upload an image first.", "warning");
        return;
      }
      
      // If no filename is provided, generate a default one
      if (!filename) {
        const now = new Date();
        const dateStr = now.toISOString().slice(0, 10); // YYYY-MM-DD
        const timeStr = now.toTimeString().slice(0, 8).replace(/:/g, "-"); // HH-MM-SS
        filename = `id-photo-${dateStr}-${timeStr}`;
      }
      
      // Create the export URL with filename
      const exportUrl = `http://localhost:8080/api/image/export?filename=${encodeURIComponent(filename)}`;
      
      // Create a link element to trigger the download
      const link = document.createElement('a');
      link.href = exportUrl;
      link.setAttribute('download', `${filename}.png`);
      link.setAttribute('target', '_blank');
      
      // Append the link, click it, and then remove it
      document.body.appendChild(link);
      link.click();
      
      // Wait a bit before removing the link
      setTimeout(() => {
        document.body.removeChild(link);
        
        // Show success notification
        this.showNotification("Image exported successfully!", "success");
      }, 500);
    },

    // Method to explicitly update crop dimensions (for debugging)
    updateCropDimensions(width, height) {
      console.log(`Manually updating crop dimensions to: ${width}x${height}`);

      // Directly set the values (no nextTick needed)
      this.cropWidth = width;
      this.cropHeight = height;

      // Force the ImageComponent to update its crop box
      if (this.$refs.imageComponent) {
        this.$refs.imageComponent.centerCropBox();
      }
    },

    // Updated undo method with actual API call
    async undoAction() {
      try {
        const response = await fetch("http://localhost:8080/api/undo");
        if (response.ok) {
          // Update the image and all related state
          await this.updateImageAfterEdit();
        } else {
          console.error("Failed to undo:", await response.text());
        }
      } catch (error) {
        console.error("Error during undo:", error);
      }
    },

    // New redo method that uses the backend's future stack
    async redoAction() {
      try {
        const response = await fetch("http://localhost:8080/api/redo");
        if (response.ok) {
          // Update the image and all related state
          await this.updateImageAfterEdit();
        } else {
          console.error("Failed to redo:", await response.text());
        }
      } catch (error) {
        console.error("Error during redo:", error);
      }
    },

    // Reset to original image
    async resetAction() {
      try {
        const response = await fetch("http://localhost:8080/api/reset");
        if (response.ok) {
          // Update the image and all related state
          await this.updateImageAfterEdit();
          // After reset, we are already at the original image
          this.canReset = false;
        } else {
          console.error("Failed to reset:", await response.text());
        }
      } catch (error) {
        console.error("Error during reset:", error);
      }
    },

    async fetchImageDimensions() {
      try {
        // Add cache-busting parameter to ensure we get fresh data
        const response = await fetch(
          `http://localhost:8080/api/image/dimensions?_t=${Date.now()}`
        );
        if (response.ok) {
          const dimensions = await response.json();
          // Only update if dimensions are valid
          if (dimensions && dimensions.width > 0 && dimensions.height > 0) {
            this.imageDimensions = dimensions;
            console.log("Image dimensions updated from server:", dimensions);
          } else {
            console.error("Invalid dimensions received:", dimensions);
            this.getImageDimensionsFromDOM();
          }
        } else {
          console.error(
            "Failed to fetch image dimensions:",
            await response.text()
          );
          this.getImageDimensionsFromDOM();
        }
      } catch (error) {
        console.error("Error fetching image dimensions:", error);
        this.getImageDimensionsFromDOM();
      }
    },
    
    // Get dimensions directly from the image element as a fallback
    getImageDimensionsFromDOM() {
      console.log("Getting image dimensions from DOM");
      this.$nextTick(() => {
        const imageComponent = this.$refs.imageComponent;
        if (imageComponent && imageComponent.$refs.photoImage) {
          const img = imageComponent.$refs.photoImage;
          if (img.complete) {
            this.imageDimensions = {
              width: img.naturalWidth,
              height: img.naturalHeight
            };
            console.log("Image dimensions from DOM:", this.imageDimensions);
          } else {
            // If image isn't loaded yet, wait for it
            img.onload = () => {
              this.imageDimensions = {
                width: img.naturalWidth,
                height: img.naturalHeight
              };
              console.log("Image dimensions from DOM onload:", this.imageDimensions);
            };
          }
        }
      });
    },

    async uploadNewImage(event) {
      const file = event.target.files[0];
      if (!file) return;

      const formData = new FormData();
      formData.append("file", file);

      try {
        const uploadResponse = await fetch("http://localhost:8080/api/upload", {
          method: "POST",
          body: formData,
        });

        if (uploadResponse.ok) {
          // Get the image and update all state
          await this.updateImageAfterEdit();

          // For a new upload, explicitly set these flags
          this.canUndo = false;
          this.canRedo = false;
          this.canReset = false;

          // Clear any crop area
          this.cropArea = null;
          if (this.$refs.imageComponent) {
            this.$refs.imageComponent.clearCropBox();
          }
        } else {
          console.error("Failed to upload image:", await uploadResponse.text());
        }
      } catch (error) {
        console.error("Error uploading image:", error);
      }
    },

    // Handle explicit dimension updates
    handleApplyDimensions({ width, height }) {
      console.log(`Explicitly applying dimensions: ${width}x${height}`);
      // Ensure values are parsed as floats to handle decimal places
      this.cropWidth = parseFloat(width);
      this.cropHeight = parseFloat(height);

      // Force the ImageComponent to update its crop box
      if (this.$refs.imageComponent) {
        this.$refs.imageComponent.centerCropBox();
      }
    },

    // Handle resize dimensions from dragging resize handles or when the image loads
    handleResizeDimensions(dimensions) {
      console.log("Received image dimensions:", dimensions);

      // Always update the displayed dimensions in the header
      if (this.imageDimensions) {
        this.imageDimensions.width = dimensions.width;
        this.imageDimensions.height = dimensions.height;
      } else {
        this.imageDimensions = {
          width: dimensions.width,
          height: dimensions.height,
        };
      }

      // Don't apply resize automatically - wait for user to click Apply button
    },

    // Add a handler for crop completion
    handleCropComplete() {
      console.log('Crop operation completed and mask hidden');
      // Can add additional logic here if needed
    },

    // Update the applyChanges method to handle resize
    async applyChanges(changes) {
      console.log("Applying changes:", changes);

      if (changes.type === "crop") {
        if (!this.cropArea && this.cropWidth && this.cropHeight) {
          const imgEl = this.$refs.imageComponent.$el.querySelector("img");
          if (!imgEl) {
            console.error("No image element found");
            return;
          }

          const rect = imgEl.getBoundingClientRect();
          const scaleX = imgEl.naturalWidth / rect.width;
          const scaleY = imgEl.naturalHeight / rect.height;

          // Ensure valid dimensions and handle decimal places
          const cropWidth = Math.max(0.1, parseFloat(this.cropWidth));
          const cropHeight = Math.max(0.1, parseFloat(this.cropHeight));

          const cropPxWidth = (cropWidth / 35) * 140;
          const cropPxHeight = (cropHeight / 45) * 180;

          const x = (rect.width / 2 - cropPxWidth / 2) * scaleX;
          const y = (rect.height / 2 - cropPxHeight / 2) * scaleY;

          this.cropArea = {
            x: Math.floor(Math.max(0, x)),
            y: Math.floor(Math.max(0, y)),
            width: Math.floor(Math.max(1, cropPxWidth * scaleX)),
            height: Math.floor(Math.max(1, cropPxHeight * scaleY)),
          };

          console.log("Calculated crop area:", this.cropArea);
        }

        // now call backend crop
        try {
          if (!this.cropArea) {
            console.error("No crop area defined");
            return;
          }

          // Ensure valid crop dimensions
          const cropRequest = {
            x: Math.max(0, this.cropArea.x),
            y: Math.max(0, this.cropArea.y),
            width: Math.max(1, this.cropArea.width),
            height: Math.max(1, this.cropArea.height),
          };

          console.log("Sending crop request:", cropRequest);

          const cropResponse = await fetch("http://localhost:8080/api/crop", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(cropRequest),
          });

          if (cropResponse.ok) {
            // Update the image and all related state
            await this.updateImageAfterEdit();

            // Clear crop related state
            this.cropArea = null;
            
            // Hide the crop mask and control panel by setting activeFeature to null
            this.activeFeature = null;
            
            // Show success notification
            this.showNotification("Image cropped successfully!", "success");
          } else {
            console.error("Failed to crop image:", await cropResponse.text());
          }
        } catch (error) {
          console.error("Error cropping image:", error);
        }
      } else if (changes.type === "resize") {
        try {
          const resizeResponse = await fetch(
            "http://localhost:8080/api/resize",
            {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(changes),
            }
          );

          if (resizeResponse.ok) {
            // Update the image and all related state
            await this.updateImageAfterEdit();

            // Reset the resize box to match the new image dimensions
            if (this.$refs.imageComponent && this.activeFeature === "resize") {
              this.$nextTick(() => {
                this.$refs.imageComponent.resetResizeBox();
              });
            }
          } else {
            console.error(
              "Failed to resize image:",
              await resizeResponse.text()
            );
          }
        } catch (error) {
          console.error("Error resizing image:", error);
        }
      } else if (changes.type === "clothes") {
        try {
          // Create loading indicator
          const loadingIndicator = document.createElement("div");
          loadingIndicator.textContent = "Applying clothing replacement...";
          loadingIndicator.style.position = "fixed";
          loadingIndicator.style.top = "50%";
          loadingIndicator.style.left = "50%";
          loadingIndicator.style.transform = "translate(-50%, -50%)";
          loadingIndicator.style.backgroundColor = "rgba(0, 0, 0, 0.8)";
          loadingIndicator.style.color = "white";
          loadingIndicator.style.padding = "15px 30px";
          loadingIndicator.style.borderRadius = "8px";
          loadingIndicator.style.zIndex = "9999";

          // Add the loading indicator
          document.body.appendChild(loadingIndicator);

          const response = await fetch(
            "http://localhost:8080/api/clothes-replace",
            {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                type: "clothes",
                clothingType: changes.clothingType || "formal"
              }),
            }
          );

          // Remove loading indicator
          if (document.body.contains(loadingIndicator)) {
            document.body.removeChild(loadingIndicator);
          }

          if (response.ok) {
            // Update the image and all related state
            await this.updateImageAfterEdit();
            
            // Show success notification
            this.showNotification(
              "Clothing replacement applied successfully!",
              "success"
            );
          } else {
            const errorText = await response.text();
            console.error("Failed to replace clothing:", errorText);
            this.showNotification(
              "Failed to replace clothing: " + errorText,
              "error"
            );
          }
        } catch (error) {
          console.error("Error replacing clothing:", error);
          this.showNotification("Error: " + error.message, "error");
        }
      } else if (changes.type === "background-remove") {
        try {
          const response = await fetch(
            "http://localhost:8080/api/background-remove",
            {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(changes),
            }
          );

          if (response.ok) {
            // Update the image and all related state
            await this.updateImageAfterEdit();
          } else {
            console.error(
              "Failed to remove background:",
              await response.text()
            );
          }
        } catch (error) {
          console.error("Error removing background:", error);
        }
      } else if (changes.type === "background-replace") {
        try {
          // Determine the request type based on the changes
          let replaceRequest;

          if (changes.color) {
            // Color replacement
            replaceRequest = {
              type: "color",
              color: changes.color,
            };
          } else if (changes.imageId) {
            // Image replacement with existing image ID
            replaceRequest = {
              type: "image",
              imageId: changes.imageId,
            };
          } else {
            console.error(
              "Invalid background replacement request: missing color or imageId"
            );
            return;
          }

          const response = await fetch(
            "http://localhost:8080/api/background-replace",
            {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(replaceRequest),
            }
          );

          if (response.ok) {
            // Update the image and all related state
            await this.updateImageAfterEdit();
          } else {
            console.error(
              "Failed to replace background:",
              await response.text()
            );
          }
        } catch (error) {
          console.error("Error replacing background:", error);
        }
      } else if (changes.type === "enhance") {
        try {
          const response = await fetch("http://localhost:8080/api/enhance", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(changes),
          });

          if (response.ok) {
            // Update the image and all related state
            await this.updateImageAfterEdit();

            // Show notification
            this.showNotification(
              "Photo enhancement applied successfully!",
              "success"
            );
          } else {
            const errorText = await response.text();
            console.error("Failed to apply enhancement:", errorText);
            this.showNotification(
              "Failed to apply enhancement: " + errorText,
              "error"
            );
          }
        } catch (error) {
          console.error("Error applying enhancement:", error);
          this.showNotification("Error: " + error.message, "error");
        }
      } else if (changes.type === "layout") {
        try {
          // Show a loading indicator for layout generation
          const loadingIndicator = document.createElement("div");
          loadingIndicator.textContent = "Generating photo layout...";
          loadingIndicator.style.position = "fixed";
          loadingIndicator.style.top = "50%";
          loadingIndicator.style.left = "50%";
          loadingIndicator.style.transform = "translate(-50%, -50%)";
          loadingIndicator.style.backgroundColor = "rgba(0, 0, 0, 0.8)";
          loadingIndicator.style.color = "white";
          loadingIndicator.style.padding = "15px 30px";
          loadingIndicator.style.borderRadius = "8px";
          loadingIndicator.style.zIndex = "9999";

          // Add the loading indicator
          document.body.appendChild(loadingIndicator);

          const response = await fetch("http://localhost:8080/api/layout", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(changes),
          });

          // Remove loading indicator
          if (document.body.contains(loadingIndicator)) {
            document.body.removeChild(loadingIndicator);
          }

          if (response.ok) {
            // Update the image and all related state
            await this.updateImageAfterEdit();

            // Show notification
            this.showNotification(
              "Photo layout created successfully!",
              "success"
            );
          } else {
            const errorText = await response.text();
            console.error("Failed to create layout:", errorText);
            this.showNotification(
              "Failed to create layout: " + errorText,
              "error"
            );
          }
        } catch (error) {
          console.error("Error creating layout:", error);
          this.showNotification("Error: " + error.message, "error");
        }
      }
    },

    // Helper method to update image, dimensions and history status after any edit
    async updateImageAfterEdit() {
      try {
        // Get the updated image with cache busting
        const cacheBuster = new Date().getTime();
        const imageResponse = await fetch(
          `http://localhost:8080/api/image/get?t=${cacheBuster}`
        );
        if (imageResponse.ok) {
          const blob = await imageResponse.blob();
          // Clean up previous object URL
          if (this.currentPhoto) {
            URL.revokeObjectURL(this.currentPhoto);
          }
          this.currentPhoto = URL.createObjectURL(blob);

          // Update dimensions
          await this.fetchImageDimensions();

          // Update history status
          const historyResponse = await fetch(
            "http://localhost:8080/api/history/status"
          );
          if (historyResponse.ok) {
            const historyStatus = await historyResponse.json();
            this.canUndo = historyStatus.hasHistory;
            this.canRedo = historyStatus.hasFuture;
            this.canReset = historyStatus.hasOriginal;
          }
        }
      } catch (error) {
        console.error("Error updating image after edit:", error);
      }
    },

    // Method to check for existing image on component mount
    async checkForExistingImage() {
      try {
        const response = await fetch("http://localhost:8080/api/image/get");
        if (response.ok) {
          await this.updateImageAfterEdit();
          console.log("Existing image loaded on component mount");
        } else {
          // Even if we don't have an image yet, try to get dimensions
          await this.fetchImageDimensions();
        }
      } catch (error) {
        console.log("No existing image found or error fetching image:", error);
        // Still try to fetch dimensions
        await this.fetchImageDimensions();
      }
    },

    // Method to upload a background image
    async uploadBackgroundImage(file) {
      console.log("uploadBackgroundImage called with file:", file.name);

      const formData = new FormData();
      formData.append("file", file);

      try {
        console.log("Sending background image upload request to server");
        const response = await fetch(
          "http://localhost:8080/api/background-image/upload",
          {
            method: "POST",
            body: formData,
          }
        );

        if (response.ok) {
          const data = await response.json();
          console.log(
            "Background image uploaded successfully, received imageId:",
            data.imageId
          );
          return data.imageId; // Return the image ID for use in background replacement
        } else {
          const errorText = await response.text();
          console.error("Failed to upload background image:", errorText);
          throw new Error(`Server error: ${errorText || response.status}`);
        }
      } catch (error) {
        console.error("Error uploading background image:", error);
        throw error; // Rethrow to allow proper handling in the calling method
      }
    },

    // Handle background image upload from ControlPanel
    async handleBackgroundImageUpload(file) {
      console.log("handleBackgroundImageUpload called with file:", file.name);

      // Create loading indicator
      const loadingIndicator = document.createElement("div");
      loadingIndicator.textContent = "Uploading background image...";
      loadingIndicator.style.position = "fixed";
      loadingIndicator.style.top = "50%";
      loadingIndicator.style.left = "50%";
      loadingIndicator.style.transform = "translate(-50%, -50%)";
      loadingIndicator.style.backgroundColor = "rgba(0, 0, 0, 0.8)";
      loadingIndicator.style.color = "white";
      loadingIndicator.style.padding = "15px 30px";
      loadingIndicator.style.borderRadius = "8px";
      loadingIndicator.style.zIndex = "9999";

      // Add the loading indicator
      document.body.appendChild(loadingIndicator);

      try {
        // Upload the image
        const imageId = await this.uploadBackgroundImage(file);

        // Remove loading indicator
        if (document.body.contains(loadingIndicator)) {
          document.body.removeChild(loadingIndicator);
        }

        if (imageId) {
          // Apply the background replacement with the uploaded image
          const changes = {
            type: "background-replace",
            imageId: imageId,
          };

          console.log(
            "Background image uploaded, applying with imageId:",
            imageId
          );
          await this.applyChanges(changes);
          this.showNotification("Background replaced successfully!", "success");
          console.log("Background image applied successfully");
        } else {
          console.error(
            "Failed to get imageId from upload, cannot apply background"
          );
          this.showNotification("Failed to upload background image.", "error");
        }
      } catch (error) {
        // Remove loading indicator
        if (document.body.contains(loadingIndicator)) {
          document.body.removeChild(loadingIndicator);
        }
        console.error("Error handling background image upload:", error);
        this.showNotification(`Error: ${error.message}`, "error");
      }
    },

    // Method to show notifications
    showNotification(message, type = "info") {
      // Create notification element
      const notification = document.createElement("div");
      notification.className = `notification ${type}`;
      notification.textContent = message;

      // Set styles based on notification type
      notification.style.position = "fixed";
      notification.style.bottom = "20px";
      notification.style.right = "20px";
      notification.style.padding = "12px 20px";
      notification.style.borderRadius = "4px";
      notification.style.fontSize = "14px";
      notification.style.zIndex = "9999";
      notification.style.boxShadow = "0 4px 12px rgba(0, 0, 0, 0.15)";
      notification.style.minWidth = "250px";
      notification.style.maxWidth = "350px";
      notification.style.textAlign = "center";

      // Set color based on type
      if (type === "success") {
        notification.style.backgroundColor = "#10B981";
        notification.style.color = "white";
      } else if (type === "error") {
        notification.style.backgroundColor = "#EF4444";
        notification.style.color = "white";
      } else if (type === "warning") {
        notification.style.backgroundColor = "#F59E0B";
        notification.style.color = "white";
      } else {
        notification.style.backgroundColor = "#3B82F6";
        notification.style.color = "white";
      }

      // Add to document
      document.body.appendChild(notification);

      // Animate entrance
      notification.animate(
        [
          { opacity: 0, transform: "translateY(20px)" },
          { opacity: 1, transform: "translateY(0)" },
        ],
        { duration: 300, easing: "ease-out" }
      );

      // Remove after timeout
      setTimeout(() => {
        notification.animate(
          [
            { opacity: 1, transform: "translateY(0)" },
            { opacity: 0, transform: "translateY(20px)" },
          ],
          { duration: 300, easing: "ease-in" }
        ).onfinish = () => {
          document.body.removeChild(notification);
        };
      }, 4000);
    },

    // Add the faceDetectionChange handler method in the methods section
    async handleFaceDetectionChange(data) {
      console.log("Handling face detection:", data);

      try {
        // Create a loading indicator
        const loadingIndicator = document.createElement("div");
        loadingIndicator.textContent = "Processing...";
        loadingIndicator.style.position = "fixed";
        loadingIndicator.style.top = "50%";
        loadingIndicator.style.left = "50%";
        loadingIndicator.style.transform = "translate(-50%, -50%)";
        loadingIndicator.style.backgroundColor = "rgba(0, 0, 0, 0.8)";
        loadingIndicator.style.color = "white";
        loadingIndicator.style.padding = "15px 30px";
        loadingIndicator.style.borderRadius = "8px";
        loadingIndicator.style.zIndex = "9999";

        // Add the loading indicator
        document.body.appendChild(loadingIndicator);

        // Process the request
        const response = await fetch("http://localhost:8080/api/face-center", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(data.options || {}),
        });

        // Remove loading indicator
        if (document.body.contains(loadingIndicator)) {
          document.body.removeChild(loadingIndicator);
        }

        if (response.ok) {
          // Update the image after processing
          await this.updateImageAfterEdit();

          // Show success notification
          this.showNotification(
            "Face detected and centered successfully!",
            "success"
          );
        } else {
          const errorMessage = await response.text();
          console.error(`Failed to process face detection: ${errorMessage}`);

          // Show appropriate error message
          if (errorMessage.includes("No faces detected")) {
            this.showNotification(
              "No faces could be detected in the image. Try a different photo.",
              "warning"
            );
          } else if (
            errorMessage.includes("faceDetector") &&
            errorMessage.includes("null")
          ) {
            this.showNotification(
              "Face detection failed. Please verify that the cascade file exists in backend/cascades folder.",
              "error"
            );
          } else {
            this.showNotification(
              `Failed to process: ${errorMessage}`,
              "error"
            );
          }
        }
      } catch (error) {
        console.error("Error in face detection process:", error);
        this.showNotification(`Error: ${error.message}`, "error");
      }
    },

    // Method to handle enhancement preview
    async handleEnhancementPreview(data) {
      console.log("Handling enhancement preview:", data);

      // Clear any existing timeout
      if (this.previewThrottleTimeout) {
        clearTimeout(this.previewThrottleTimeout);
      }

      // Set a new timeout to throttle requests
      this.previewThrottleTimeout = setTimeout(async () => {
        // Don't send another request if one is already in progress
        if (this.isPreviewLoading) return;

        this.isPreviewLoading = true;

        try {
          // Process the request without showing loading indicator for a smoother experience
          const response = await fetch(
            "http://localhost:8080/api/enhancement-preview",
            {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                brightness: data.brightness,
                contrast: data.contrast,
                firstAdjustment: data.firstAdjustment || false,
                previewInProgress: data.previewInProgress || false,
              }),
            }
          );

          if (response.ok) {
            // Get the updated image with cache busting
            const cacheBuster = new Date().getTime();
            const imageResponse = await fetch(
              `http://localhost:8080/api/image/get?t=${cacheBuster}`
            );
            if (imageResponse.ok) {
              const blob = await imageResponse.blob();
              // Clean up previous object URL
              if (this.currentPhoto) {
                URL.revokeObjectURL(this.currentPhoto);
              }
              this.currentPhoto = URL.createObjectURL(blob);
            }
          } else {
            const errorMessage = await response.text();
            console.error(
              `Failed to generate enhancement preview: ${errorMessage}`
            );
          }
        } catch (error) {
          console.error("Error in enhancement preview process:", error);
        } finally {
          this.isPreviewLoading = false;
        }
      }, 100); // Throttle to once every 100ms
    },

    // Method to handle Google Drive image selection
    async handleDriveImage(imageData) {
      try {
        console.log("Received image from Google Drive:", imageData.name);
        
        // Display the image immediately for user feedback
        this.currentPhoto = `data:${imageData.mimeType};base64,${imageData.content}`;
        
        // Reset history when loading new image
        this.futureStack = [];
        
        // Show loading notification
        this.showNotification("Processing image...", "info");
        
        // Wait for the next tick to ensure the image is rendered
        this.$nextTick(async () => {
          try {
            // Upload the image to the backend server for processing
            await this.uploadGoogleDriveImage(imageData);
            
            // Show success notification
            this.showNotification("Image imported from Google Drive successfully!", "success");
          } catch (error) {
            console.error("Error uploading image:", error);
            this.showNotification("Failed to process image: " + error.message, "error");
          }
        });
      } catch (error) {
        console.error("Error handling Google Drive image:", error);
        this.showNotification("Failed to import image from Google Drive", "error");
      }
    },
    
    // Method to upload the Google Drive image to the backend server
    async uploadGoogleDriveImage(imageData) {
      try {
        console.log("Uploading Google Drive image to backend server");
        
        // Create a form data object - the server expects multipart/form-data
        const formData = new FormData();
        
        // Convert base64 to blob
        const byteCharacters = atob(imageData.content);
        const byteArrays = [];
        
        for (let offset = 0; offset < byteCharacters.length; offset += 512) {
          const slice = byteCharacters.slice(offset, offset + 512);
          
          const byteNumbers = new Array(slice.length);
          for (let i = 0; i < slice.length; i++) {
            byteNumbers[i] = slice.charCodeAt(i);
          }
          
          const byteArray = new Uint8Array(byteNumbers);
          byteArrays.push(byteArray);
        }
        
        // Create file with proper filename and type
        const blob = new Blob(byteArrays, { type: imageData.mimeType });
        const file = new File([blob], imageData.name || "google_drive_image.jpg", { type: imageData.mimeType });
        
        // Append file to form data - this matches the expected format for backend upload
        formData.append("file", file);
        
        // Send the request using the same endpoint as regular file uploads
        const response = await fetch("http://localhost:8080/api/upload", {
          method: "POST",
          body: formData
        });
        
        if (response.ok) {
          console.log("Google Drive image uploaded to backend server successfully");
          // Update image dimensions after upload
          await this.updateImageAfterEdit();
          
          // Reset flags for the new image
          this.canUndo = false;
          this.canRedo = false;
          this.canReset = false;
        } else {
          const errorText = await response.text();
          console.error("Failed to upload Google Drive image to backend server:", errorText);
          this.showNotification("Failed to process the imported image", "error");
        }
      } catch (error) {
        console.error("Error uploading Google Drive image to backend server:", error);
        this.showNotification("Error processing image: " + error.message, "error");
      }
    },
    
    // Method to update image dimensions from the current photo element
    updateImageDimensions() {
      this.$nextTick(() => {
        const img = new Image();
        img.onload = () => {
          this.imageWidth = img.width;
          this.imageHeight = img.height;
          console.log("Image dimensions updated from client:", this.imageWidth, this.imageHeight);
        };
        img.src = this.currentPhoto;
      });
    },

    handleExportSuccess(details) {
      console.log("Export to Google Drive successful:", details);
      this.showNotification(`Image exported to Google Drive as "${details.fileName}"!`, "success");
    },
    
    handleExportError(errorMessage) {
      console.error("Export to Google Drive failed:", errorMessage);
      this.showNotification(`Failed to export to Google Drive: ${errorMessage}`, "error");
    },

    handleAuthRequired() {
      console.log("Authentication required for Google Drive");
      this.showNotification("Please log in to Google Drive first by using the 'Import from Google Drive' button", "warning");
    },
  },
  mounted() {
    // Check if an image is already available and fetch its dimensions
    this.checkForExistingImage();
    
    // Ensure we have image dimensions for display in header, even if using default image
    if (!this.imageDimensions) {
      this.fetchImageDimensions();
    }
  },
};
</script>

<style scoped>
.editing-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  background-color: #1e2124;
  color: #ffffff;
  font-family: "Inter", sans-serif;
  overflow: hidden;
  margin: 0;
  padding: 0;
}

.main-container {
  display: flex;
  flex: 1;
  overflow: hidden;
  margin-top: 0;
  padding-top: 0;
}

.header-google-drive-btn {
  display: inline-block;
}

.header-google-drive-btn :deep(.drive-button) {
  height: 34px;
  padding: 6px 10px;
  background-color: transparent;
  color: #ffffff;
  border-radius: 4px;
  border: none;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.header-google-drive-btn :deep(.drive-button:hover) {
  background-color: #3a3f45;
}

.header-google-drive-btn :deep(.drive-button:disabled) {
  color: #5a5f65;
  cursor: not-allowed;
}
</style>
