<template>
  <div class="editing-page">
    <div class="toolbar">
      <button @click="handleNewImage">New</button>
      <button @click="handleSave">Save</button>
      <button @click="handleExport">Export</button>
      <GoogleDriveImagePicker @image-selected="handleDriveImage" />
    </div>

    <div class="editing-area">
      <img v-if="currentImage" :src="currentImage" class="preview-image" />
      <div v-else class="empty-state">Select an image to start editing</div>
    </div>

    <div class="editing-tools">
      <button @click="handleCrop">Crop</button>
      <button @click="handleResize">Resize</button>
      <button @click="handleBackgroundRemoval">Background Removal</button>
      <button @click="handleBackgroundReplace">Background Replace</button>
    </div>
  </div>
</template>

<script>
import GoogleDriveImagePicker from "./GoogleDriveImagePicker.vue";

export default {
  name: "EditingPage",
  components: {
    GoogleDriveImagePicker,
  },
  data() {
    return {
      currentImage: null,
      isLoading: false,
      error: null,
    };
  },
  methods: {
    handleNewImage() {
      // Implement new image logic
    },
    handleSave() {
      // Implement save logic
    },
    handleExport() {
      // Implement export logic
    },
    async handleDriveImage(imageData) {
      try {
        // Convert base64 to image
        this.currentImage = `data:${imageData.mimeType};base64,${imageData.content}`;

        // You can store the image ID for later use (e.g., saving back to Drive)
        this.currentImageId = imageData.id;
      } catch (error) {
        console.error("Error handling Drive image:", error);
        this.error = "Failed to load image from Google Drive";
      }
    },
    handleCrop() {
      // Implement crop logic
    },
    handleResize() {
      // Implement resize logic
    },
    handleBackgroundRemoval() {
      // Implement background removal logic
    },
    handleBackgroundReplace() {
      // Implement background replacement logic
    },
  },
};
</script>

<style scoped>
.editing-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 20px;
  gap: 20px;
}

.toolbar {
  display: flex;
  gap: 10px;
  padding: 10px;
  background: #f5f5f5;
  border-radius: 8px;
}

.editing-area {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f9f9f9;
  border-radius: 8px;
  overflow: hidden;
}

.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.empty-state {
  color: #666;
  font-size: 1.2em;
}

.editing-tools {
  display: flex;
  gap: 10px;
  padding: 10px;
  background: #f5f5f5;
  border-radius: 8px;
}

button {
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
}

button:hover {
  background: #f8f8f8;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.error-message {
  color: #dc3545;
  text-align: center;
  margin: 10px 0;
}
</style>
