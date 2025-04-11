<template>
  <div class="home-page">
    <header class="p-4 bg-primary flex justify-between items-center">
      <h1 class="text-xl font-bold">OOPsy-daisy Google Drive Integration</h1>
    </header>
    
    <main class="container p-4">
      <div class="card mb-4">
        <h2 class="text-lg font-bold mb-4">Welcome to Google Drive Integration</h2>
        <p class="mb-4">This application allows you to:</p>
        <ul class="list-disc ml-6 mb-4">
          <li>Upload photos to your Google Drive</li>
          <li>Import photos from Google Drive for editing</li>
          <li>Organize your photos in folders</li>
          <li>Edit and save photos back to your Drive</li>
        </ul>
        
        <div class="flex gap-4 mt-4">
          <button class="primary" @click="goToDriveExplorer">
            Explore Google Drive
          </button>
          <button class="primary" @click="goToBatchEditing">
            Edit Multiple Images
          </button>
          <button class="secondary" @click="goToEditingPage">
            Edit New Image
          </button>
        </div>
      </div>
      
      <!-- API Status Section -->
      <div class="card mb-4">
        <h3 class="text-md font-bold mb-2">System Status</h3>
        
        <div class="status-item mb-2">
          <div class="status-label">Backend API:</div>
          <div class="status-value" :class="{ 'text-success': apiStatus.backendConnected, 'text-danger': !apiStatus.backendConnected }">
            {{ apiStatus.message }}
          </div>
          <div v-if="apiStatus.error" class="status-error text-danger">
            Error details: {{ apiStatus.error }}
          </div>
        </div>
        
        <div class="status-item">
          <div class="status-label">Google Authentication:</div>
          <div class="status-value" :class="{ 'text-success': authStatus.authenticated, 'text-danger': !authStatus.authenticated }">
            {{ authStatus.message }}
          </div>
          <div class="mt-2">
            <button v-if="!authStatus.authenticated" class="primary" @click="login">
              Login with Google
            </button>
            <button v-else class="danger" @click="logout">
              Logout
            </button>
          </div>
        </div>
        
        <div v-if="!apiStatus.backendConnected" class="mt-4">
          <p class="text-danger font-bold">Backend server is not connected. Please ensure it's running.</p>
          <p>The backend server should be running at http://localhost:8080.</p>
          <button class="primary mt-2" @click="checkBackendConnection">
            Retry Connection
          </button>
        </div>
      </div>
      
      <div v-if="driveStatus.filesCount !== undefined" class="card">
        <h3 class="text-md font-bold mb-2">Google Drive Files</h3>
        <p>{{ driveStatus.filesCount }} files available</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import googleDriveService from '../services/google-drive-service';
import authService from '../services/auth-service';
import apiClient from '../services/api-client';

const router = useRouter();
const driveStatus = ref({
  connected: false,
  message: 'Checking connection to Google Drive...'
});
const apiStatus = ref({
  backendConnected: false,
  message: 'Checking backend connection...',
  error: null
});
const authStatus = ref({
  authenticated: false,
  message: 'Checking authentication status...'
});

// Handle auth redirects
onMounted(async () => {
  console.log('HomePage mounted, checking API connections...');
  
  // Check for auth redirect parameters
  const authError = authService.handleAuthRedirect();
  if (authError) {
    console.error('Authentication error detected:', authError);
    // Show error message to user
    alert(`Authentication failed: ${authError.message || authError.error}`);
  }

  // Check backend connection first
  try {
    console.log('Testing backend connection...');
    await checkBackendConnection();
  } catch (error) {
    console.error('Backend connection error:', error);
    apiStatus.value = {
      backendConnected: false,
      message: 'Cannot connect to backend server',
      error: error.message
    };
  }
  
  // Check auth status if backend is available
  if (apiStatus.value.backendConnected) {
    try {
      console.log('Checking authentication status...');
      const status = await authService.getStatus();
      console.log('Auth status response:', status);
      authStatus.value = status;
      
      // Only check Google Drive if already authenticated
      if (status.authenticated) {
        await checkDriveStatus();
      } else {
        // Set drive status to not connected when not authenticated
        driveStatus.value = {
          connected: false,
          message: 'Please log in to access Google Drive'
        };
      }
    } catch (error) {
      console.error('Auth status error:', error);
      authStatus.value = {
        authenticated: false,
        message: `Error checking authentication: ${error.message}`
      };
    }
  }
});

// Test the backend connection
async function checkBackendConnection() {
  try {
    console.log('Sending API ping request...');
    
    // Try a few different endpoints that might exist
    let response;
    try {
      response = await apiClient.get('/status', { timeout: 3000 });
    } catch (e) {
      console.log('Trying /api/status endpoint failed, trying /api/drive/status...');
      response = await apiClient.get('/drive/status', { timeout: 3000 });
    }
    
    console.log('Backend response:', response.data);
    apiStatus.value = {
      backendConnected: true,
      message: 'Backend server connected',
      error: null
    };
    return true;
  } catch (error) {
    apiStatus.value = {
      backendConnected: false,
      message: 'Cannot connect to backend server',
      error: error.message
    };
    throw error;
  }
}

// Check Google Drive status
async function checkDriveStatus() {
  try {
    console.log('Checking Google Drive status...');
    const status = await googleDriveService.getStatus();
    console.log('Drive status response:', status);
    driveStatus.value = status;
  } catch (error) {
    console.error('Google Drive status error:', error);
    driveStatus.value = {
      connected: false,
      message: `Error checking connection: ${error.message}`
    };
  }
}

// Authentication methods
function login() {
  authService.initiateLogin();
}

// Update login function to check Drive status after successful login
async function handleLoginSuccess() {
  try {
    console.log('Checking authentication status after login...');
    const authResult = await authService.getStatus();
    console.log('Auth result after login:', authResult);
    authStatus.value = authResult;
    
    if (authResult.authenticated) {
      await checkDriveStatus();
    }
  } catch (error) {
    console.error('Error checking status after login:', error);
  }
}

async function logout() {
  try {
    // First, clear Google Drive credentials
    try {
      const driveLogoutResult = await googleDriveService.logout();
      console.log('Google Drive logout result:', driveLogoutResult);
    } catch (driveError) {
      console.error('Google Drive logout error:', driveError);
      // Continue with auth logout even if Drive logout fails
    }

    // Then clear auth cookies
    const result = await authService.logout();
    
    if (result.success) {
      authStatus.value = {
        authenticated: false,
        message: 'Logged out successfully'
      };
      // Also update drive status since we're no longer authenticated
      driveStatus.value = {
        connected: false,
        message: 'Not connected to Google Drive after logout'
      };
      
      // Refresh the page to ensure all state is cleared
      setTimeout(() => {
        window.location.reload();
      }, 1000);
    } else {
      console.error('Logout failed:', result.message);
    }
  } catch (error) {
    console.error('Logout error:', error);
  }
}

// Navigation methods
function goToDriveExplorer() {
  router.push('/drive');
}

function goToBatchEditing() {
  router.push('/batch-edit');
}

function goToEditingPage() {
  router.push('/edit');
}
</script>

<style scoped>
.home-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

main {
  flex: 1;
}

header {
  background-color: var(--primary-color);
  color: white;
}

.connection-status {
  padding: 4px 8px;
  border-radius: 12px;
  background-color: var(--danger-color);
  color: white;
  font-size: 12px;
}

.connection-status.connected {
  background-color: var(--secondary-color);
}

.text-xl {
  font-size: 1.5rem;
}

.text-lg {
  font-size: 1.25rem;
}

.text-md {
  font-size: 1rem;
}

.font-bold {
  font-weight: 700;
}

.bg-primary {
  background-color: var(--primary-color);
}

.list-disc {
  list-style-type: disc;
}

.ml-6 {
  margin-left: 1.5rem;
}

/* Status display styles */
.status-item {
  display: flex;
  flex-direction: column;
  margin-bottom: 8px;
}

.status-label {
  font-weight: bold;
  margin-right: 8px;
}

.status-value {
  flex: 1;
}

.status-error {
  font-size: 0.9rem;
  margin-top: 4px;
}

.text-success {
  color: #28a745;
}

.text-danger {
  color: #dc3545;
}
</style> 