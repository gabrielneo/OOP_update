<template>
  <div class="drive-explorer">
    <header class="p-4 bg-primary flex justify-between items-center">
      <div class="flex items-center gap-2">
        <button class="back-button" @click="goBack">
          ←
        </button>
        <h1 class="text-xl font-bold">Google Drive Explorer</h1>
      </div>
      <div class="flex gap-2">
        <button class="primary" @click="refreshFiles">
          Refresh
        </button>
        <button class="secondary" @click="showNewFolderModal = true">
          New Folder
        </button>
        <input type="file" ref="fileInput" style="display: none" @change="handleFileUpload" />
        <button class="secondary" @click="$refs.fileInput.click()">
          Upload
        </button>
      </div>
    </header>
    
    <div class="breadcrumbs p-2 bg-light">
      <button @click="navigateToRoot" class="breadcrumb-item">
        Root
      </button>
      <span v-for="(folder, index) in breadcrumbs" :key="folder.id" class="breadcrumb-path">
        / 
        <button @click="navigateToFolder(folder.id)" class="breadcrumb-item">
          {{ folder.name }}
        </button>
      </span>
    </div>
    
    <main class="p-4 flex-col">
      <div v-if="loading" class="loading-indicator">
        Loading files...
      </div>
      
      <div v-else-if="error" class="error-message card p-4">
        <p>{{ error }}</p>
        <button class="primary mt-4" @click="refreshFiles">
          Try Again
        </button>
      </div>
      
      <div v-else-if="files.length === 0" class="empty-state card p-4">
        <p>No files found in this folder.</p>
        <div class="flex gap-2 mt-4">
          <button class="secondary" @click="showNewFolderModal = true">
            Create Folder
          </button>
          <button class="secondary" @click="$refs.fileInput.click()">
            Upload File
          </button>
        </div>
      </div>
      
      <div v-else class="file-grid">
        <div 
          v-for="file in files" 
          :key="file.id" 
          class="file-item card" 
          @click="handleFileClick(file)"
          :class="{ 'folder': file.isFolder, 'image': file.isImage }"
        >
          <div class="file-icon">
            {{ file.isFolder ? '📁' : file.isImage ? '🖼️' : '📄' }}
          </div>
          <div class="file-name">{{ file.name }}</div>
          <div class="file-date">{{ formatDate(file.modifiedTime) }}</div>
        </div>
      </div>
    </main>
    
    <!-- New Folder Modal -->
    <div v-if="showNewFolderModal" class="modal-overlay">
      <div class="modal card">
        <h3 class="text-lg font-bold mb-4">Create New Folder</h3>
        <input 
          v-model="newFolderName" 
          placeholder="Folder name" 
          class="folder-input mb-4" 
          @keyup.enter="createFolder"
        />
        <div class="flex justify-between">
          <button class="danger" @click="showNewFolderModal = false">
            Cancel
          </button>
          <button 
            class="primary" 
            @click="createFolder" 
            :disabled="!newFolderName.trim()"
          >
            Create
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import googleDriveService from '../services/google-drive-service';

// Router for navigation
const router = useRouter();

// Component state
const loading = ref(false);
const error = ref(null);
const files = ref([]);
const currentFolderId = ref(null);
const breadcrumbs = ref([]);
const showNewFolderModal = ref(false);
const newFolderName = ref('');
const fileInput = ref(null);

// Load files when component mounts
onMounted(() => {
  refreshFiles();
});

// Fetch files from Google Drive
async function refreshFiles() {
  loading.value = true;
  error.value = null;
  
  try {
    files.value = await googleDriveService.listFiles(currentFolderId.value);
  } catch (err) {
    error.value = `Error loading files: ${err.message}`;
    console.error(err);
  } finally {
    loading.value = false;
  }
}

// Handle click on a file or folder
function handleFileClick(file) {
  if (file.isFolder) {
    navigateToFolder(file.id, file.name);
  } else if (file.isImage) {
    router.push(`/edit/${file.id}`);
  } else {
    // For non-image files, just show the web view link if available
    if (file.webViewLink) {
      window.open(file.webViewLink, '_blank');
    }
  }
}

// Navigate to a specific folder
function navigateToFolder(folderId, folderName) {
  if (folderName) {
    breadcrumbs.value.push({ id: folderId, name: folderName });
  }
  currentFolderId.value = folderId;
  refreshFiles();
}

// Navigate back to the root folder
function navigateToRoot() {
  breadcrumbs.value = [];
  currentFolderId.value = null;
  refreshFiles();
}

// Create a new folder
async function createFolder() {
  if (!newFolderName.value.trim()) return;
  
  try {
    loading.value = true;
    await googleDriveService.createFolder(newFolderName.value, currentFolderId.value);
    showNewFolderModal.value = false;
    newFolderName.value = '';
    await refreshFiles();
  } catch (err) {
    error.value = `Error creating folder: ${err.message}`;
    console.error(err);
  } finally {
    loading.value = false;
  }
}

// Handle file upload
async function handleFileUpload(event) {
  const file = event.target.files[0];
  if (!file) return;
  
  try {
    loading.value = true;
    await googleDriveService.uploadFile(file, currentFolderId.value);
    await refreshFiles();
    // Clear the input so the same file can be uploaded again if needed
    fileInput.value.value = '';
  } catch (err) {
    error.value = `Error uploading file: ${err.message}`;
    console.error(err);
  } finally {
    loading.value = false;
  }
}

// Format date for display
function formatDate(dateStr) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString();
}

// Navigation method
function goBack() {
  router.push('/');
}
</script>

<style scoped>
.drive-explorer {
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

.breadcrumbs {
  background-color: var(--light-bg);
  padding: 8px 16px;
  display: flex;
  align-items: center;
}

.breadcrumb-item {
  background: none;
  border: none;
  color: var(--primary-color);
  cursor: pointer;
  padding: 0;
}

.breadcrumb-path {
  margin: 0 4px;
}

main {
  flex: 1;
  overflow: auto;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.file-item {
  padding: 12px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: all 0.2s;
}

.file-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.file-icon {
  font-size: 2rem;
  margin-bottom: 8px;
}

.file-name {
  font-weight: bold;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
}

.file-date {
  font-size: 0.8rem;
  color: #666;
  margin-top: 4px;
}

.folder-input {
  width: 100%;
  padding: 8px;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  width: 400px;
  max-width: 90%;
  background-color: white;
  padding: 24px;
  border-radius: 8px;
}

.bg-light {
  background-color: var(--light-bg);
}

.loading-indicator, .empty-state, .error-message {
  text-align: center;
  padding: 32px;
  margin: 32px 0;
}
</style> 