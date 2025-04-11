<template>
  <div class="google-drive-picker">
    <button @click="handleClick" class="drive-button" :disabled="loading">
      <i class="fas fa-cloud-upload-alt"></i>
      {{ isLoggedIn ? "Import from Google Drive" : "Login to Google Drive" }}
    </button>

    <!-- Image Picker Modal -->
    <div v-if="showModal" class="modal-overlay">
      <div class="modal-content">
        <div class="modal-header">
          <div class="header-content">
            <h2>Select Images from Google Drive</h2>
            <div class="drive-location">
              <span class="location-label">Location:</span>
              <span class="location-value">My Drive</span>
            </div>
          </div>
          <button @click="closeModal" class="close-button">&times;</button>
        </div>

        <div class="modal-body">
          <div v-if="loading" class="loading-state">
            <div class="spinner"></div>
            <p>Loading images from Google Drive...</p>
          </div>

          <div v-else-if="error" class="error-state">
            <div class="error-icon">⚠️</div>
            <p>{{ error }}</p>
            <div class="error-actions">
              <button @click="loadContent" class="retry-button">Try Again</button>
              <button v-if="error.includes('authentication') || error.includes('login') || error.includes('auth')" 
                @click="reconnect" 
                class="reconnect-button">
                Reconnect Account
              </button>
            </div>
          </div>

          <div v-else-if="images.length === 0" class="empty-state">
            <div class="empty-icon">🖼️</div>
            <p>No images found in your Google Drive</p>
            <p class="empty-help">Try uploading some images to your Google Drive, then refresh.</p>
            <button @click="loadContent" class="retry-button">Refresh</button>
          </div>

          <div v-else class="items-grid">
            <!-- Display Images -->
            <div
              v-for="item in images"
              :key="item.id"
              class="item image"
              :class="{ selected: selectedImages.includes(item.id) }"
              @click="toggleImageSelection(item)"
            >
              <div class="image-container">
                <img
                  :src="getImageUrl(item)"
                  :alt="item.name"
                  class="thumbnail"
                  @error="handleImageError($event, item)"
                  @load="handleImageLoad($event, item)"
                  crossorigin="anonymous"
                />
                <div v-if="item.isLoading" class="image-loading-overlay">
                  <div class="spinner small-spinner"></div>
                </div>
              </div>
              <div class="item-name">{{ item.name }}</div>
              <div class="file-info">{{ getFormattedType(item.mimeType) }}</div>
              <div class="select-indicator" v-if="selectedImages.includes(item.id)">✓</div>
            </div>
          </div>
        </div>

        <div class="modal-footer" v-if="images.length > 0">
          <div class="selection-info">
            {{ selectedImages.length }} image(s) selected
          </div>
          <div class="action-buttons">
            <button @click="closeModal" class="cancel-button">Cancel</button>
            <button 
              @click="importSelectedImages" 
              class="import-button"
              :disabled="selectedImages.length === 0"
            >
              Import Selected
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from "axios";
import googleApiService from "../services/google-api-service";

export default {
  name: "GoogleDriveImagePicker",

  data() {
    return {
      showModal: false,
      loading: false,
      error: null,
      items: [],
      isLoggedIn: false,
      selectedImages: [],
    };
  },

  computed: {
    images() {
      return this.items.filter(
        (item) => item.mimeType && item.mimeType.startsWith("image/")
      );
    }
  },

  mounted() {
    this.checkLoginStatus();
  },

  methods: {
    async checkLoginStatus() {
      try {
        const status = await googleApiService.checkLoginStatus();
        this.isLoggedIn = status.isLoggedIn;
        console.log("Login status updated:", this.isLoggedIn);
      } catch (err) {
        console.error("Error checking login status:", err);
        this.isLoggedIn = false;
      }
    },

    async handleClick() {
      if (!this.isLoggedIn) {
        try {
          console.log("Not logged in, starting login flow...");
          await this.loginWithGoogleDrive();
        } catch (error) {
          console.error("Error initiating login:", error);
          this.error =
            error.message || "Failed to initiate login. Please try again.";
        }
      } else {
        console.log("Already logged in, opening picker...");
        this.openPicker();
      }
    },

    handleImageError(event, item) {
      // Handle image loading error with fallback strategies that avoid CORS
      const img = event.target;
      
      if (item) {
        console.log(`Image load error for ${item.name}, trying fallback`);
        
        // Try loading image content directly and display as base64
        this.renderImageAsBase64(item.id, img, item);
      }
    },

    async renderImageAsBase64(imageId, imgElement, item) {
      try {
        console.log(`Rendering image ${imageId} as base64`);
        
        // Get the actual file content as base64
        const response = await fetch(`/api/drive/files/${imageId}/content?thumbnail=true`);
        
        if (!response.ok) {
          throw new Error(`Failed to get image content: ${response.status}`);
        }
        
        const data = await response.json();
        if (data && data.content) {
          // If item wasn't provided, try to find it
          if (!item) {
            item = this.images.find(img => img.id === imageId);
          }
          
          // Determine content type (default to jpeg)
          const mimeType = item?.mimeType || 'image/jpeg';
          
          // Create base64 data URL and set as image source
          const dataUrl = `data:${mimeType};base64,${data.content}`;
          imgElement.src = dataUrl;
          
          // Store in the item for future reference if we found it
          if (item) {
            item.base64Content = dataUrl;
            item.loadError = false;
          }
          
          console.log(`Successfully rendered ${imageId} as base64`);
          return;
        }
        
        throw new Error("No content in response");
      } catch (err) {
        console.error(`Failed to render image ${imageId} as base64:`, err);
        
        // If item wasn't provided, try to find it
        if (!item) {
          item = this.images.find(img => img.id === imageId);
        }
        
        // Mark error state if we found the item
        if (item) {
          item.loadError = true;
        }
        
        // Last resort: show a colored placeholder with filename
        const container = imgElement.closest('.image-container');
        if (container) {
          const itemName = item?.name || 'Image';
          container.innerHTML = `
            <div style="width:100%; height:100%; display:flex; align-items:center; justify-content:center; background:#f0f0f0;">
              <div style="text-align:center; padding:10px;">
                <div style="font-size:36px; color:#999;">📷</div>
                <div style="font-size:12px; color:#666; margin-top:5px;">${itemName}</div>
              </div>
            </div>`;
        }
      }
    },
    
    getImageUrl(item) {
      // Check if we've already loaded this image as base64
      if (item.base64Content) {
        return item.base64Content;
      }
      
      // Start loading the image as base64
      this.loadImageAsBase64(item);
      
      // Return a temporary proxy endpoint while loading
      return `/api/drive/files/${item.id}/content?thumbnail=true`;
    },

    async loadImageAsBase64(item) {
      if (item.isLoading || item.base64Content) return;
      
      try {
        // Mark as loading
        item.isLoading = true;
        
        // Load image content via backend proxy
        const response = await fetch(`/api/drive/files/${item.id}/content?thumbnail=true`);
        
        if (!response.ok) {
          throw new Error(`Failed to get image content: ${response.status}`);
        }
        
        const data = await response.json();
        if (data && data.content) {
          // Create data URL and store in the item
          const mimeType = item.mimeType || 'image/jpeg';
          item.base64Content = `data:${mimeType};base64,${data.content}`;
          
          // Force update any existing image elements with this source
          this.$nextTick(() => {
            const imgElements = document.querySelectorAll(`img[alt="${item.name}"]`);
            imgElements.forEach(img => {
              img.src = item.base64Content;
            });
          });
        }
      } catch (error) {
        console.error(`Error loading image ${item.id} as base64:`, error);
      } finally {
        item.isLoading = false;
      }
    },

    getFormattedType(mimeType) {
      if (!mimeType) return '';
      
      // Extract just the subtype from the MIME type
      const parts = mimeType.split('/');
      if (parts.length === 2) {
        return parts[1].toUpperCase();
      }
      return mimeType;
    },

    async openPicker() {
      this.showModal = true;
      this.selectedImages = [];

      // First test connection before trying to load content
      await this.testConnection();
    },

    async testConnection() {
      console.log("Testing Google Drive connection...");
      this.loading = true;
      this.error = null;

      try {
        const connectionResult = await googleApiService.testDriveConnection();
        console.log("Connection test result:", connectionResult);

        if (!connectionResult.connectionSuccessful || connectionResult.loginRequired) {
          console.log("Connection test indicates authorization required");
          this.isLoggedIn = false;
          this.error = "You need to login with Google Drive first";
          this.loading = false;
          return false;
        }

        console.log("Connection test successful!");
        this.isLoggedIn = true;
        await this.loadContent();
        return true;
      } catch (err) {
        console.error("Error testing Drive connection:", err);
        this.error = "Error connecting to Google Drive: " + err.message;
        this.loading = false;
        return false;
      }
    },

    closeModal() {
      this.showModal = false;
      this.items = [];
      this.error = null;
      this.selectedImages = [];
    },

    async loadContent(folderId = null) {
      this.loading = true;
      this.error = null;
      this.items = [];
      this.currentFolder = folderId;

      try {
        // First, check connection
        const connectionResult = await googleApiService.testDriveConnection();
        console.log("Google Drive connection test:", connectionResult);

        if (!connectionResult.connectionSuccessful || connectionResult.loginRequired) {
          console.log("Connection test indicates authorization required");
          
          // Try to fix tokens before showing login dialog
          try {
            console.log("Attempting to fix tokens...");
            const fixResult = await googleApiService.fixTokens();
            console.log("Token fix result:", fixResult);
            
            if (fixResult.tokenRefreshed) {
              console.log("Tokens were refreshed successfully, retesting connection...");
              // Retest connection after token fix
              const retestResult = await googleApiService.testDriveConnection();
              if (retestResult.connectionSuccessful) {
                console.log("Connection successful after token fix, proceeding with file listing");
                // Continue since tokens are now fixed - will proceed to listing files below
              } else {
                throw new Error("Authentication required even after token refresh");
              }
            } else {
              throw new Error("Authentication required");
            }
          } catch (error) {
            console.error("Error fixing tokens:", error);
            throw new Error("Authentication required");
          }
        }

        // List files from Google Drive
        console.log("Listing files from Google Drive, folder:", folderId, "only images:", true);
        const data = await googleApiService.listDriveFiles(folderId, true);
        console.log("Files loaded:", data?.files?.length);

        if (data.files) {
          // Request additional fields for each file to get webContentLink
          for (let i = 0; i < data.files.length; i++) {
            try {
              // Try to get more details for each file
              const fileDetails = await googleApiService.getFileDetails(data.files[i].id);
              if (fileDetails) {
                data.files[i] = { ...data.files[i], ...fileDetails };
              }
            } catch (err) {
              console.warn(`Couldn't get details for file ${data.files[i].name}:`, err);
            }
          }
          
          // Preload images to ensure they display correctly
          this.preloadImages(data.files);
        }

        this.items = data.files || [];
      } catch (error) {
        console.error("Error loading Drive content:", error);
        this.error = error.message;
        this.isAuthenticated = false;
        
        if (error.message.includes("Authentication") || error.message.includes("authenticated")) {
          this.showLoginRequired = true;
        }
      } finally {
        this.loading = false;
      }
    },

    /**
     * Preload images to ensure they display properly
     */
    preloadImages(files) {
      // Only preload image files
      const imageFiles = files.filter(file => file.mimeType && file.mimeType.startsWith('image/'));
      
      // Add loading state properties to each file
      imageFiles.forEach(file => {
        // Only add these if they don't already exist
        if (file.isLoading === undefined) file.isLoading = false;
        if (file.base64Content === undefined) file.base64Content = null;
        
        // Start loading this image as base64
        this.loadImageAsBase64(file);
      });
    },

    toggleImageSelection(image) {
      const index = this.selectedImages.indexOf(image.id);
      if (index === -1) {
        this.selectedImages.push(image.id);
      } else {
        this.selectedImages.splice(index, 1);
      }
    },

    async importSelectedImages() {
      if (this.selectedImages.length === 0) return;
      
      this.loading = true;
      this.error = null;
      
      const selected = [];
      
      try {
        // Fetch content for each selected image
        for (const imageId of this.selectedImages) {
          const image = this.images.find(img => img.id === imageId);
          if (!image) continue;
          
          try {
            // If we already have the base64 content cached, use that
            if (image.base64Content) {
              console.log(`Using cached base64 content for image ${image.name}`);
              
              // Extract the base64 data from the data URL
              const base64Data = image.base64Content.split(',')[1];
              
              selected.push({
                id: image.id,
                name: image.name,
                mimeType: image.mimeType,
                content: base64Data
              });
              continue;
            }
            
            // Otherwise fetch from the API
            console.log(`Fetching content for image ${image.name} from API`);
            const response = await fetch(`/api/drive/files/${image.id}/content`);
            
            if (!response.ok) {
              throw new Error(`Failed to get image content: ${response.status}`);
            }
            
            const data = await response.json();
            if (data && data.content) {
              selected.push({
                id: image.id,
                name: image.name,
                mimeType: image.mimeType,
                content: data.content
              });
            }
          } catch (err) {
            console.error(`Error fetching content for image ${image.name}:`, err);
          }
        }
        
        // Emit selected images to parent component
        if (selected.length > 0) {
          this.$emit("image-selected", selected.length === 1 ? selected[0] : selected);
        }
        
        this.closeModal();
      } catch (err) {
        console.error("Error importing selected images:", err);
        this.error = "Error importing images: " + err.message;
      } finally {
        this.loading = false;
      }
    },

    async loginWithGoogleDrive() {
      try {
        console.log("Starting Google Drive authentication...");
        // Use direct redirect to Google Auth page
        await googleApiService.signIn();
        // Page will redirect, so no further code will execute here
      } catch (error) {
        console.error("Error during Google Drive authentication:", error);
        this.error = `Authentication error: ${error.message}`;
      }
    },

    // Add this method to provide easy reconnection
    async reconnect() {
      this.loading = true;
      this.error = null;
      
      try {
        console.log("Attempting to reconnect to Google Drive...");
        
        // Try to fix tokens first
        try {
          const fixResult = await googleApiService.fixTokens();
          console.log("Token fix result:", fixResult);
          
          if (fixResult.tokenRefreshed) {
            console.log("Tokens were refreshed successfully!");
            // Try loading content again
            await this.loadContent();
            return;
          }
        } catch (error) {
          console.error("Error fixing tokens:", error);
        }
        
        // If we get here, we need to fully reauthenticate
        console.log("Need to fully reauthenticate");
        await googleApiService.signIn();
        // This will redirect the user
      } catch (error) {
        console.error("Error reconnecting:", error);
        this.error = "Failed to reconnect: " + error.message;
      } finally {
        this.loading = false;
      }
    },

    handleImageLoad(event, item) {
      // Image loaded successfully
      if (item) {
        item.loadError = false;
        
        // If this wasn't loaded from base64 yet, and it's showing via the proxy URL,
        // we should still try to load it as base64 for future use
        if (!item.base64Content && !item.isLoading) {
          this.loadImageAsBase64(item);
        }
      }
    },
  },
};
</script>

<style scoped>
.google-drive-picker {
  display: inline-block;
}

.drive-button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background-color: #4285f4;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.2s;
  white-space: nowrap;
  height: 100%;
}

.drive-button:hover {
  background-color: #3367d6;
}

.drive-button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 1100px;
  height: 85vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  padding: 16px;
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

.modal-body {
  padding: 16px;
  overflow-y: auto;
  flex: 1;
}

.modal-footer {
  padding: 16px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selection-info {
  font-size: 14px;
  color: #666;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.cancel-button {
  padding: 8px 16px;
  background-color: #f5f5f5;
  color: #666;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}

.import-button {
  padding: 8px 16px;
  background-color: #4285f4;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.import-button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.header-content {
  flex-grow: 1;
}

.drive-location {
  font-size: 15px;
  font-weight: 500;
  margin-top: 4px;
  color: #333;
}

.location-label {
  margin-right: 5px;
  color: #666;
}

.items-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

.item {
  border: 1px solid #eee;
  border-radius: 4px;
  padding: 8px;
  cursor: pointer;
  transition: all 0.2s;
  overflow: hidden;
  position: relative;
}

.item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.image {
  position: relative;
  height: 240px;
  display: flex;
  flex-direction: column;
}

.image.selected {
  border: 2px solid #4285f4;
  box-shadow: 0 0 10px rgba(66, 133, 244, 0.5);
}

.image-container {
  position: relative;
  height: 160px;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #f9f9f9;
  border-radius: 4px;
  overflow: hidden;
}

.select-indicator {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  background-color: #4285f4;
  color: white;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  font-weight: bold;
  font-size: 14px;
}

.thumbnail {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
}

.item-name {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 500;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 4px;
  color: #333;
}

.file-info {
  font-size: 11px;
  color: #888;
  text-align: center;
  margin-top: 4px;
}

.loading-state,
.error-state,
.empty-state {
  text-align: center;
  padding: 32px;
  color: #666;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
}

.error-icon, 
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.spinner {
  border: 4px solid rgba(0, 0, 0, 0.1);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border-left-color: #4285f4;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

.empty-help {
  font-size: 14px;
  color: #999;
  margin-top: 8px;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.retry-button {
  margin-top: 16px;
  padding: 8px 16px;
  background-color: #4285f4;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

/* Add these new styles to improve image display */
.image-container img {
  width: 100%;
  height: 100%;
  object-fit: cover; /* Cover ensures the image fills the container */
}

.image-container:hover img {
  transform: scale(1.05);
  transition: transform 0.2s ease-in-out;
}

.error-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  justify-content: center;
}

.reconnect-button {
  background-color: #4285f4;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.2s;
}

.reconnect-button:hover {
  background-color: #3367d6;
}

.image-loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(255, 255, 255, 0.8);
  border-radius: 4px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.small-spinner {
  border: 2px solid rgba(0, 0, 0, 0.1);
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border-left-color: #4285f4;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>
