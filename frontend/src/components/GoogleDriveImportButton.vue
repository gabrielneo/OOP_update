<template>
  <div class="google-drive-import">
    <button
      @click="openImagePicker"
      class="import-button"
      :disabled="!isLoggedIn"
    >
      <i class="fas fa-cloud-upload-alt"></i>
      Import from Google Drive
    </button>

    <!-- Image Picker Modal -->
    <div v-if="showPicker" class="modal">
      <div class="modal-content">
        <div class="modal-header">
          <h2>Select Images from Google Drive</h2>
          <button @click="closePicker" class="close-button">&times;</button>
        </div>

        <div class="image-grid">
          <div v-for="file in driveFiles" :key="file.id" class="image-item">
            <img
              :src="file.thumbnailLink"
              :alt="file.name"
              @click="selectImage(file)"
              :class="{ selected: selectedImages.includes(file.id) }"
            />
            <div class="image-name">{{ file.name }}</div>
          </div>
        </div>

        <div class="modal-footer">
          <button
            @click="importSelected"
            :disabled="selectedImages.length === 0"
            class="import-selected-button"
          >
            Import Selected ({{ selectedImages.length }})
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from "axios";

export default {
  name: "GoogleDriveImportButton",

  data() {
    return {
      isLoggedIn: false,
      showPicker: false,
      driveFiles: [],
      selectedImages: [],
      loading: false,
    };
  },

  mounted() {
    // Check login status when component mounts
    this.checkLoginStatus();
  },

  methods: {
    async checkLoginStatus() {
      try {
        const response = await axios.get("/api/auth/status");
        this.isLoggedIn = response.data.isLoggedIn;
      } catch (error) {
        console.error("Failed to check login status:", error);
        this.isLoggedIn = false;
      }
    },

    async openImagePicker() {
      if (!this.isLoggedIn) {
        // Trigger login flow if not logged in
        this.$emit("login-required");
        return;
      }

      this.showPicker = true;
      await this.loadDriveImages();
    },

    closePicker() {
      this.showPicker = false;
      this.selectedImages = [];
    },

    async loadDriveImages() {
      try {
        this.loading = true;
        const response = await axios.get("/api/drive/files", {
          params: {
            imagesOnly: true,
          },
        });
        this.driveFiles = response.data.files;
      } catch (error) {
        console.error("Failed to load images:", error);
      } finally {
        this.loading = false;
      }
    },

    selectImage(file) {
      const index = this.selectedImages.indexOf(file.id);
      if (index === -1) {
        this.selectedImages.push(file.id);
      } else {
        this.selectedImages.splice(index, 1);
      }
    },

    async importSelected() {
      try {
        this.loading = true;
        const importedImages = [];

        for (const fileId of this.selectedImages) {
          const response = await axios.get(
            `/api/drive/files/${fileId}/content`
          );
          importedImages.push({
            id: fileId,
            content: response.data.content,
            name: this.driveFiles.find((f) => f.id === fileId).name,
          });
        }

        this.$emit("images-imported", importedImages);
        this.closePicker();
      } catch (error) {
        console.error("Failed to import images:", error);
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.google-drive-import {
  display: inline-block;
}

.import-button {
  background-color: #4285f4;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.import-button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.import-button i {
  font-size: 16px;
}

.modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background-color: white;
  border-radius: 8px;
  width: 80%;
  max-width: 1000px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  padding: 20px;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.close-button {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
  padding: 20px;
  overflow-y: auto;
}

.image-item {
  position: relative;
  cursor: pointer;
}

.image-item img {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 4px;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}

.image-item img.selected {
  border-color: #4285f4;
}

.image-name {
  margin-top: 8px;
  font-size: 12px;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-footer {
  padding: 20px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: flex-end;
}

.import-selected-button {
  background-color: #4285f4;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
}

.import-selected-button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}
</style>
