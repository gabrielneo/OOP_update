<template>
  <div class="batch-editing-page">
    <header class="p-4 bg-primary flex justify-between items-center">
      <div class="flex items-center gap-2">
        <button class="back-button" @click="goBack">
          ←
        </button>
        <h1 class="text-xl font-bold">
          Batch Image Editing
        </h1>
      </div>
      <div class="flex gap-2">
        <button class="primary" @click="selectImages" v-if="!selectionMode && images.length === 0">
          Select Images
        </button>
        <button class="danger" @click="clearSelection" v-if="images.length > 0">
          Clear Selection ({{ images.length }})
        </button>
        <button class="primary" @click="startEditing" v-if="!selectionMode && images.length > 0 && currentIndex === -1">
          Start Editing
        </button>
        <button class="secondary" v-if="currentIndex !== -1 && currentIndex < images.length - 1" @click="nextImage">
          Next Image ({{ currentIndex + 1 }}/{{ images.length }})
        </button>
        <button class="secondary" v-if="currentIndex !== -1 && currentIndex === images.length - 1" @click="finishEditing">
          Finish
        </button>
      </div>
    </header>
    
    <main class="p-4">
      <!-- Selection Mode View -->
      <div v-if="selectionMode" class="selection-container">
        <div class="card p-4 mb-4">
          <h3 class="text-lg font-bold mb-2">Select Images to Edit</h3>
          <p class="mb-4">Choose multiple images from your Google Drive to edit in sequence.</p>
          
          <div v-if="loading" class="loading-indicator">
            <p>Loading images from Google Drive...</p>
          </div>
          
          <div v-else-if="loadError" class="error-message">
            <p>{{ loadError }}</p>
            <button class="primary mt-4" @click="loadImages">
              Try Again
            </button>
          </div>
          
          <div v-else-if="driveImages.length === 0" class="empty-state">
            <p>No images found in your Google Drive.</p>
          </div>
          
          <div v-else class="image-selection-grid">
            <div 
              v-for="image in driveImages" 
              :key="image.id" 
              class="image-item" 
              :class="{ 'selected': isSelected(image.id) }"
              @click="toggleImageSelection(image)"
            >
              <div class="selection-indicator">
                <div v-if="isSelected(image.id)" class="check-icon">✓</div>
              </div>
              <div class="file-icon">🖼️</div>
              <div class="file-name">{{ image.name }}</div>
            </div>
          </div>
          
          <div class="flex justify-between mt-4">
            <button class="danger" @click="cancelSelection">
              Cancel
            </button>
            <button 
              class="primary" 
              @click="confirmSelection" 
              :disabled="selectedImageIds.length === 0"
            >
              Confirm Selection ({{ selectedImageIds.length }})
            </button>
          </div>
        </div>
      </div>
      
      <!-- Empty State -->
      <div v-else-if="images.length === 0" class="empty-state card p-4">
        <h3 class="text-lg font-bold mb-2">No Images Selected</h3>
        <p>Click "Select Images" to choose images from your Google Drive for batch editing.</p>
      </div>
      
      <!-- Batch Editing View -->
      <div v-else-if="currentIndex === -1" class="batch-summary card p-4">
        <h3 class="text-lg font-bold mb-2">Ready to Edit {{ images.length }} Images</h3>
        <p class="mb-4">You've selected {{ images.length }} images for batch editing. Click "Start Editing" to begin.</p>
        
        <div class="selected-images-list">
          <div v-for="(image, index) in images" :key="image.id" class="selected-image-item">
            <div class="file-icon">🖼️</div>
            <div class="file-name">{{ image.name }}</div>
          </div>
        </div>
      </div>
      
      <!-- Current Editing View -->
      <div v-else-if="currentImage" class="editing-view flex">
        <div class="editing-area">
          <div v-if="loading" class="loading-indicator card p-4">
            <p>{{ loadingMessage }}</p>
          </div>
          
          <div v-else-if="loadError" class="error-message card p-4">
            <p>{{ loadError }}</p>
          </div>
          
          <div v-else-if="!imageData" class="empty-state card p-4">
            <p>Failed to load image data.</p>
          </div>
          
          <div v-else class="image-container card">
            <img :src="imageData" alt="Editing image" ref="imageElement" />
            <div class="image-info">
              <p><strong>File name:</strong> {{ currentImage.name }}</p>
              <p><strong>Google Drive ID:</strong> {{ currentImage.id }}</p>
              <p><strong>Image {{ currentIndex + 1 }} of {{ images.length }}</strong></p>
            </div>
          </div>
        </div>
        
        <div class="editing-tools card p-4">
          <h3 class="text-lg font-bold mb-4">Image Editing Tools</h3>
          
          <div class="tool-section mb-4">
            <h4 class="font-bold mb-2">Brightness & Contrast</h4>
            <div class="flex items-center gap-2 mb-2">
              <label>Brightness:</label>
              <input type="range" min="-100" max="100" v-model.number="filters.brightness" @input="applyFilters" />
              <span>{{ filters.brightness }}</span>
            </div>
            <div class="flex items-center gap-2">
              <label>Contrast:</label>
              <input type="range" min="-100" max="100" v-model.number="filters.contrast" @input="applyFilters" />
              <span>{{ filters.contrast }}</span>
            </div>
          </div>
          
          <div class="tool-section mb-4">
            <h4 class="font-bold mb-2">Colors</h4>
            <div class="flex items-center gap-2 mb-2">
              <label>Saturation:</label>
              <input type="range" min="-100" max="100" v-model.number="filters.saturation" @input="applyFilters" />
              <span>{{ filters.saturation }}</span>
            </div>
            <div class="flex items-center gap-2">
              <label>Hue Rotate:</label>
              <input type="range" min="0" max="360" v-model.number="filters.hueRotate" @input="applyFilters" />
              <span>{{ filters.hueRotate }}°</span>
            </div>
          </div>
          
          <div class="tool-section mb-4">
            <h4 class="font-bold mb-2">Effects</h4>
            <div class="flex items-center gap-2 mb-2">
              <label>Blur:</label>
              <input type="range" min="0" max="10" step="0.1" v-model.number="filters.blur" @input="applyFilters" />
              <span>{{ filters.blur }}</span>
            </div>
            <div class="flex items-center gap-2">
              <label>Grayscale:</label>
              <input type="range" min="0" max="100" v-model.number="filters.grayscale" @input="applyFilters" />
              <span>{{ filters.grayscale }}%</span>
            </div>
          </div>
          
          <div class="tool-section">
            <h4 class="font-bold mb-2">Actions</h4>
            <div class="flex items-center gap-2 mb-4">
              <label>Filename:</label>
              <input type="text" v-model="currentFileName" class="filename-input" />
            </div>
            <div class="flex gap-2 mt-4">
              <button class="secondary" @click="resetFilters">
                Reset Filters
              </button>
              <button class="primary" @click="saveCurrentImage">
                Save Changes
              </button>
            </div>
            <div class="flex gap-2 mt-2">
              <button class="secondary" @click="skipImage">
                Skip Image
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import googleDriveService from '../services/google-drive-service';

// Router for navigation
const router = useRouter();

// State for image batch selection
const selectionMode = ref(false);
const driveImages = ref([]);
const selectedImageIds = ref([]);
const images = ref([]);
const loading = ref(false);
const loadingMessage = ref('');
const loadError = ref(null);

// State for the current editing session
const currentIndex = ref(-1);
const imageData = ref(null);
const imageElement = ref(null);
const currentFileName = ref('');

// Image filters state - same as in EditingPage
const filters = reactive({
  brightness: 0,
  contrast: 0,
  saturation: 0,
  hueRotate: 0,
  blur: 0,
  grayscale: 0
});

// Computed property for the current image
const currentImage = computed(() => {
  if (currentIndex.value >= 0 && currentIndex.value < images.value.length) {
    return images.value[currentIndex.value];
  }
  return null;
});

// Initialize component
onMounted(async () => {
  try {
    // Check connection to Google Drive API
    const status = await googleDriveService.getStatus();
    console.log('Google Drive connection status:', status);
    
    if (!status.connected) {
      loadError.value = `Google Drive not connected: ${status.message}`;
      console.error('Google Drive not connected:', status.message);
    }
  } catch (err) {
    console.error('Error in onMounted hook:', err);
    loadError.value = `Error initializing page: ${err.message}`;
  }
});

// Start selection mode and load images from Drive
async function selectImages() {
  selectionMode.value = true;
  await loadImages();
}

// Load images from Google Drive
async function loadImages() {
  loading.value = true;
  loadError.value = null;
  
  try {
    // Load only image files from Drive
    driveImages.value = await googleDriveService.listFiles(null, true);
  } catch (err) {
    loadError.value = `Error loading images: ${err.message}`;
    console.error(err);
  } finally {
    loading.value = false;
  }
}

// Check if an image is selected
function isSelected(imageId) {
  return selectedImageIds.value.includes(imageId);
}

// Toggle selection of an image
function toggleImageSelection(image) {
  const index = selectedImageIds.value.indexOf(image.id);
  if (index === -1) {
    selectedImageIds.value.push(image.id);
  } else {
    selectedImageIds.value.splice(index, 1);
  }
}

// Confirm the current selection
function confirmSelection() {
  if (selectedImageIds.value.length === 0) return;
  
  images.value = driveImages.value.filter(image => 
    selectedImageIds.value.includes(image.id)
  );
  
  selectionMode.value = false;
}

// Cancel selection mode
function cancelSelection() {
  selectionMode.value = false;
  selectedImageIds.value = [];
}

// Clear the current batch selection
function clearSelection() {
  images.value = [];
  currentIndex.value = -1;
  imageData.value = null;
  resetFilters();
}

// Start the batch editing process
function startEditing() {
  if (images.value.length === 0) return;
  
  currentIndex.value = 0;
  loadCurrentImage();
}

// Load the current image data
async function loadCurrentImage() {
  if (!currentImage.value) return;
  
  loading.value = true;
  loadingMessage.value = `Loading image ${currentIndex.value + 1} of ${images.value.length}...`;
  loadError.value = null;
  
  try {
    // Get file details and content from the Google Drive API
    const fileData = await googleDriveService.getFileContent(currentImage.value.id);
    
    if (fileData) {
      imageData.value = `data:${fileData.mimeType};base64,${fileData.content}`;
      // Set the current filename from the loaded image
      currentFileName.value = currentImage.value.name;
      resetFilters();
    } else {
      throw new Error('Could not load image content');
    }
  } catch (err) {
    loadError.value = `Error loading image: ${err.message}`;
    console.error(err);
  } finally {
    loading.value = false;
  }
}

// Move to the next image
function nextImage() {
  if (currentIndex.value < images.value.length - 1) {
    currentIndex.value++;
    imageData.value = null;
    resetFilters();
    loadCurrentImage();
  }
}

// Skip the current image without saving
function skipImage() {
  nextImage();
}

// Finish editing all images
function finishEditing() {
  currentIndex.value = -1;
  imageData.value = null;
  resetFilters();
}

// Apply filters to the image (same as in EditingPage)
function applyFilters() {
  if (!imageElement.value) return;
  
  const { brightness, contrast, saturation, hueRotate, blur, grayscale } = filters;
  
  imageElement.value.style.filter = `
    brightness(${1 + brightness / 100})
    contrast(${1 + contrast / 100})
    saturate(${1 + saturation / 100})
    hue-rotate(${hueRotate}deg)
    blur(${blur}px)
    grayscale(${grayscale}%)
  `;
}

// Reset filters to default values (same as in EditingPage)
function resetFilters() {
  Object.assign(filters, {
    brightness: 0,
    contrast: 0,
    saturation: 0,
    hueRotate: 0,
    blur: 0,
    grayscale: 0
  });
  
  if (imageElement.value) {
    imageElement.value.style.filter = '';
  }
}

// Save the current image with applied filters
async function saveCurrentImage() {
  if (!imageData.value || !currentImage.value) {
    loadError.value = "No image loaded to save";
    return;
  }
  
  loading.value = true;
  loadingMessage.value = 'Saving image to Google Drive...';
  
  try {
    // Create a temporary image to get dimensions if needed
    const img = new Image();
    img.src = imageData.value;
    
    // Wait for the temporary image to load
    await new Promise((resolve, reject) => {
      img.onload = resolve;
      img.onerror = () => reject(new Error('Failed to load image for processing'));
      // Add a timeout just in case
      setTimeout(resolve, 1000);
    });
    
    // Create a canvas to apply the filters
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    
    // Set canvas dimensions from the loaded image
    canvas.width = img.width || 800; // Fallback width if not available
    canvas.height = img.height || 600; // Fallback height if not available
    
    console.log(`Creating canvas with dimensions: ${canvas.width}x${canvas.height}`);
    
    // Apply filters directly using the filter values
    const { brightness, contrast, saturation, hueRotate, blur, grayscale } = filters;
    ctx.filter = `brightness(${1 + brightness / 100}) contrast(${1 + contrast / 100}) saturate(${1 + saturation / 100}) hue-rotate(${hueRotate}deg) blur(${blur}px) grayscale(${grayscale}%)`;
    
    console.log(`Applied filter: ${ctx.filter}`);
    
    // Draw the image onto the canvas
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    
    // Convert to blob for uploading
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.9));
    
    if (!blob) {
      throw new Error('Failed to create image blob for upload');
    }
    
    // Use the user-specified filename or fall back to the original name
    const finalFileName = currentFileName.value || currentImage.value.name;
    
    // Create a file object from the blob
    const file = new File([blob], finalFileName, { type: 'image/jpeg' });
    
    // Update existing file in Google Drive
    console.log(`Updating file in Drive: ${currentImage.value.id}, new name: ${finalFileName}`);
    const updatedFile = await googleDriveService.updateFile(currentImage.value.id, file, finalFileName);
    
    // Update the current image in our list with the new name
    if (updatedFile && updatedFile.name) {
      currentImage.value.name = updatedFile.name;
    }
    
    // Move to the next image automatically after saving
    alert('Image saved successfully!');
    
    if (currentIndex.value < images.value.length - 1) {
      nextImage();
    } else {
      // We're at the last image
      finishEditing();
    }
  } catch (err) {
    loadError.value = `Error saving image: ${err.message}`;
    console.error('Error saving to Google Drive:', err);
  } finally {
    loading.value = false;
  }
}

// Navigation method
function goBack() {
  router.push('/');
}
</script>

<style scoped>
.batch-editing-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

header {
  background-color: var(--primary-color);
  color: white;
}

.back-button {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  cursor: pointer;
}

main {
  flex: 1;
  overflow: auto;
}

/* Selection mode styles */
.image-selection-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 16px;
  margin: 16px 0;
  max-height: 60vh;
  overflow-y: auto;
}

.image-item {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.image-item:hover {
  background-color: #f5f5f5;
}

.image-item.selected {
  background-color: rgba(0, 123, 255, 0.1);
  border-color: #007bff;
}

.selection-indicator {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.8);
  border: 1px solid #ccc;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-item.selected .selection-indicator {
  background-color: #007bff;
  color: white;
  border: none;
}

.file-icon {
  font-size: 2rem;
  margin-bottom: 8px;
}

.file-name {
  font-size: 0.9rem;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
  white-space: nowrap;
}

/* Selected images list */
.selected-images-list {
  margin-top: 12px;
  max-height: 300px;
  overflow-y: auto;
}

.selected-image-item {
  display: flex;
  align-items: center;
  padding: 8px;
  border-bottom: 1px solid #eee;
}

.selected-image-item .file-icon {
  font-size: 1.2rem;
  margin-right: 8px;
  margin-bottom: 0;
}

/* Editing view styles - similar to EditingPage */
.editing-view {
  display: flex;
}

.editing-area {
  flex: 1;
  padding-right: 16px;
}

.editing-tools {
  width: 300px;
}

.image-container {
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.image-container img {
  max-width: 100%;
  max-height: 60vh;
  object-fit: contain;
}

.image-info {
  margin-top: 16px;
  width: 100%;
}

.tool-section {
  border-bottom: 1px solid #eee;
  padding-bottom: 16px;
}

/* Status and message styles */
.loading-indicator, .empty-state, .error-message {
  text-align: center;
  padding: 32px;
  margin: 32px 0;
}

.filename-input {
  flex: 1;
  padding: 4px 8px;
  border: 1px solid #ccc;
  border-radius: 4px;
}
</style> 