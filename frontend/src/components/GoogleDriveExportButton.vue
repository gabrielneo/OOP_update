<template>
  <div class="google-drive-export">
    <button @click="handleExport" class="export-button" :disabled="loading">
      <i class="fas fa-cloud-upload-alt"></i>
      Export to Google Drive
    </button>

    <!-- Export Dialog -->
    <div v-if="showDialog" class="export-dialog">
      <div class="export-dialog-content">
        <h3>Export to Google Drive</h3>
        <div class="form-group">
          <label for="filename">Filename:</label>
          <input 
            type="text" 
            id="filename" 
            v-model="exportFilename" 
            placeholder="Enter filename (without extension)"
            @keyup.enter="confirmExport"
            ref="filenameInput"
          />
        </div>
        <div class="dialog-buttons">
          <button class="btn cancel" @click="showDialog = false">Cancel</button>
          <button class="btn export" @click="confirmExport" :disabled="loading">
            <span v-if="loading">Uploading...</span>
            <span v-else>Export</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Success Dialog -->
    <div v-if="showSuccessDialog" class="export-dialog success-dialog">
      <div class="export-dialog-content">
        <h3>Export Successful</h3>
        <p>Your image has been exported to Google Drive.</p>
        <div class="success-details">
          <div><strong>Filename:</strong> {{ successDetails.fileName }}</div>
          <div class="view-link">
            <a :href="successDetails.webViewLink" target="_blank">View in Google Drive</a>
          </div>
        </div>
        <div class="dialog-buttons">
          <button class="btn ok" @click="showSuccessDialog = false">Close</button>
        </div>
      </div>
    </div>
    
    <!-- Login Required Dialog -->
    <div v-if="showLoginDialog" class="export-dialog login-dialog">
      <div class="export-dialog-content">
        <h3>Login Required</h3>
        <p>You need to sign in to your Google Drive account first before exporting.</p>
        <div class="login-instructions">
          <p>Please login to Google Drive to continue:</p>
        </div>
        <div class="dialog-buttons">
          <button class="btn cancel" @click="showLoginDialog = false">Cancel</button>
          <button class="btn refresh" @click="refreshToken">Refresh Connection</button>
          <button class="btn login" @click="loginWithGoogleDrive">Login to Google Drive</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import googleApiService from "../services/google-api-service";

export default {
  name: "GoogleDriveExportButton",
  
  data() {
    return {
      loading: false,
      showDialog: false,
      showSuccessDialog: false,
      showLoginDialog: false,
      exportFilename: "",
      successDetails: {
        fileName: "",
        fileId: "",
        webViewLink: ""
      },
      error: null
    };
  },
  
  methods: {
    async handleExport() {
      // First check if user is logged in to Google Drive
      try {
        console.log("Testing Google Drive connection before export...");
        const connectionResult = await googleApiService.testDriveConnection();
        console.log("Connection test result:", connectionResult);
        
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
                console.log("Connection successful after token fix, proceeding with export");
                // Continue with export since tokens are now fixed
                this.showDialog = true;
                
                // Generate a default filename based on date and time
                const now = new Date();
                const dateStr = now.toISOString().slice(0, 10); // YYYY-MM-DD
                this.exportFilename = `id-photo-${dateStr}`;
                
                // Focus the filename input after dialog appears
                this.$nextTick(() => {
                  if (this.$refs.filenameInput) {
                    this.$refs.filenameInput.focus();
                    this.$refs.filenameInput.select();
                  }
                });
                return;
              }
            }
          } catch (error) {
            console.error("Error fixing tokens:", error);
          }
          
          // If we get here, token fix didn't work or wasn't possible
          this.showLoginDialog = true;
          this.$emit('auth-required');
          return;
        }
        
        // User is logged in, proceed with export dialog
        console.log("Connection test successful, proceeding with export dialog");
        this.showDialog = true;
        
        // Generate a default filename based on date and time
        const now = new Date();
        const dateStr = now.toISOString().slice(0, 10); // YYYY-MM-DD
        this.exportFilename = `id-photo-${dateStr}`;
        
        // Focus the filename input after dialog appears
        this.$nextTick(() => {
          if (this.$refs.filenameInput) {
            this.$refs.filenameInput.focus();
            this.$refs.filenameInput.select();
          }
        });
      } catch (error) {
        console.error("Error checking Google Drive connection:", error);
        this.$emit('export-error', "Failed to check Google Drive connection: " + error.message);
      }
    },
    
    async confirmExport() {
      if (!this.exportFilename.trim()) {
        // If filename is empty, use a default
        this.exportFilename = `id-photo-${new Date().toISOString().slice(0, 10)}`;
      }
      
      this.loading = true;
      
      try {
        // Get current image as base64
        const response = await fetch('http://localhost:8080/api/image/get');
        
        if (!response.ok) {
          throw new Error("Failed to get the current image");
        }
        
        // Convert the image to base64
        const blob = await response.blob();
        const base64 = await this.blobToBase64(blob);
        
        // Upload to Google Drive
        const uploadResponse = await fetch('/api/drive/upload', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          credentials: 'include',
          body: JSON.stringify({
            filename: this.exportFilename,
            imageData: base64
          })
        });
        
        // Check specifically for unauthorized error (401)
        if (uploadResponse.status === 401) {
          // Close the export dialog
          this.showDialog = false;
          
          // Show login required dialog
          this.showLoginDialog = true;
          
          // Emit specific auth error event
          this.$emit('auth-required');
          return;
        }
        
        if (!uploadResponse.ok) {
          const errorData = await uploadResponse.json();
          throw new Error(errorData.error || "Failed to upload to Google Drive");
        }
        
        const result = await uploadResponse.json();
        
        // Close the dialog
        this.showDialog = false;
        
        // Show success dialog
        this.successDetails = {
          fileName: result.fileName,
          fileId: result.fileId,
          webViewLink: result.webViewLink
        };
        
        this.showSuccessDialog = true;
        
        // Emit success event
        this.$emit('export-success', this.successDetails);
      } catch (error) {
        console.error("Error exporting to Google Drive:", error);
        this.error = error.message;
        
        // Close the export dialog
        this.showDialog = false;
        
        // Emit generic error event
        this.$emit('export-error', error.message);
      } finally {
        this.loading = false;
      }
    },
    
    blobToBase64(blob) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsDataURL(blob);
      });
    },

    async loginWithGoogleDrive() {
      try {
        console.log("Starting Google Drive authentication...");
        // Use the signIn method from the googleApiService
        await googleApiService.signIn();
        // Page will redirect, so no further code will execute here
      } catch (error) {
        console.error("Error logging in to Google Drive:", error);
        this.error = error.message;
        this.$emit('export-error', "Authentication failed: " + error.message);
      }
    },

    async refreshToken() {
      try {
        console.log("Refreshing Google Drive connection...");
        const refreshResult = await googleApiService.fixTokens();
        console.log("Token refresh result:", refreshResult);
        
        if (refreshResult.tokenRefreshed) {
          console.log("Tokens were refreshed successfully, retesting connection...");
          // Retest connection after token refresh
          const retestResult = await googleApiService.testDriveConnection();
          if (retestResult.connectionSuccessful) {
            console.log("Connection successful after token refresh, proceeding with export");
            // Continue with export since tokens are now fixed
            this.showDialog = true;
            
            // Generate a default filename based on date and time
            const now = new Date();
            const dateStr = now.toISOString().slice(0, 10); // YYYY-MM-DD
            this.exportFilename = `id-photo-${dateStr}`;
            
            // Focus the filename input after dialog appears
            this.$nextTick(() => {
              if (this.$refs.filenameInput) {
                this.$refs.filenameInput.focus();
                this.$refs.filenameInput.select();
              }
            });
            return;
          }
        }
      } catch (error) {
        console.error("Error refreshing Google Drive connection:", error);
        this.error = error.message;
        this.$emit('export-error', "Failed to refresh Google Drive connection: " + error.message);
      }
    }
  }
};
</script>

<style scoped>
.google-drive-export {
  display: inline-block;
}

.export-button {
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

.export-button:hover {
  background-color: #3367d6;
}

.export-button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.export-dialog {
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

.export-dialog-content {
  background: white;
  border-radius: 8px;
  padding: 24px;
  width: 90%;
  max-width: 400px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
}

.form-group input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.dialog-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.btn {
  padding: 8px 16px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  border: none;
}

.cancel {
  background-color: #f0f0f0;
  color: #333;
}

.export {
  background-color: #4285f4;
  color: white;
}

.export:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.ok {
  background-color: #4285f4;
  color: white;
}

.success-dialog .export-dialog-content {
  text-align: center;
}

.success-details {
  margin: 16px 0;
  text-align: left;
  background-color: #f8f8f8;
  padding: 12px;
  border-radius: 4px;
}

.view-link {
  margin-top: 8px;
}

.view-link a {
  color: #4285f4;
  text-decoration: none;
}

.view-link a:hover {
  text-decoration: underline;
}

.login-dialog .export-dialog-content {
  text-align: center;
}

.login-instructions {
  margin: 16px 0;
  text-align: left;
  background-color: #f8f8f8;
  padding: 12px;
  border-radius: 4px;
  color: #333;
}

.login-instructions p {
  margin: 8px 0;
  line-height: 1.4;
}

.login {
  background-color: #4285f4;
  color: white;
}

.login:hover {
  background-color: #3367d6;
}

.refresh {
  background-color: #4285f4;
  color: white;
}

.refresh:hover {
  background-color: #3367d6;
}
</style> 