<template>
  <div class="google-drive-login">
    <button
      @click="loginToGoogleDrive"
      class="google-drive-button"
      :disabled="isLoading"
    >
      <img
        src="https://www.gstatic.com/images/branding/product/2x/drive_2020q4_48dp.png"
        alt="Google Drive"
        class="drive-icon"
      />
      <span>{{ buttonText }}</span>
    </button>
    
    <button 
      v-if="showForceLoginButton"
      @click="forceNewGoogleLogin" 
      class="force-login-button"
      :disabled="isLoading"
    >
      <span>Force New Login</span>
      <small>Use different Google account</small>
    </button>
    
    <div v-if="error" class="error-message">
      {{ error }}
    </div>
  </div>
</template>

<script>
import googleApiService from '../services/google-api-service';

export default {
  name: "GoogleDriveLogin",
  data() {
    return {
      isLoading: false,
      error: null,
      buttonText: "Login with Google Drive",
      isLoggedIn: false,
      showForceLoginButton: false
    };
  },
  mounted() {
    // Check for login success or error parameters in URL
    const urlParams = new URLSearchParams(window.location.search);
    const error = urlParams.get("error");
    const message = urlParams.get("message");
    const success = urlParams.get("success");
    
    if (error) {
      this.error = message || "Authentication failed";
    } else if (success) {
      this.buttonText = "Connected to Google Drive";
      this.isLoggedIn = true;
      this.showForceLoginButton = true;
      this.$emit('login-success');
    }
    
    // Check if already logged in
    this.checkLoginStatus();
    
    // Check if this was a forced login attempt
    if (localStorage.getItem('force_login_attempt')) {
      localStorage.removeItem('force_login_attempt');
      console.log('Completed forced login attempt');
    }
  },
  methods: {
    async checkLoginStatus() {
      try {
        const status = await googleApiService.checkLoginStatus();
        if (status.isLoggedIn) {
          this.buttonText = "Connected to Google Drive";
          this.isLoggedIn = true;
          this.showForceLoginButton = true;
        }
      } catch (error) {
        console.error("Failed to check login status:", error);
      }
    },
    
    async checkBackendAvailability() {
      try {
        const response = await fetch("/api/auth/status");
        return response.ok;
      } catch (error) {
        console.error("Backend check failed:", error);
        return false;
      }
    },
    
    async loginToGoogleDrive() {
      this.isLoading = true;
      this.error = null;
      this.buttonText = "Connecting...";

      try {
        // First check if our backend is available
        const isBackendAvailable = await this.checkBackendAvailability();

        if (!isBackendAvailable) {
          throw new Error(
            "Backend server is not available. Please make sure the server is running."
          );
        }

        // Use the sign-in method from our service that handles redirect
        await googleApiService.signIn();
        // Note: The page will redirect, so no further code will execute here
        
      } catch (error) {
        console.error("Error during Google Drive login:", error);
        this.error = error.message || "Failed to connect to authentication server";
        this.buttonText = "Login with Google Drive";
        this.isLoading = false;
      }
    },
    
    async forceNewGoogleLogin() {
      this.isLoading = true;
      this.error = null;
      this.buttonText = "Preparing fresh login...";

      try {
        // First check if our backend is available
        const isBackendAvailable = await this.checkBackendAvailability();

        if (!isBackendAvailable) {
          throw new Error(
            "Backend server is not available. Please make sure the server is running."
          );
        }

        // Use the force-new-login method that completely wipes credentials
        await googleApiService.forceNewLogin();
        // Note: The page will redirect, so no further code will execute here
        
      } catch (error) {
        console.error("Error during forced new Google login:", error);
        this.error = error.message || "Failed to initiate new login";
        this.buttonText = this.isLoggedIn ? "Connected to Google Drive" : "Login with Google Drive";
        this.isLoading = false;
      }
    }
  }
};
</script>

<style scoped>
.google-drive-login {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin: 20px 0;
}

.google-drive-button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  background-color: #fff;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.google-drive-button:hover {
  background-color: #f8f8f8;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.google-drive-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.force-login-button {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 16px;
  background-color: #f5f5f5;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  margin-top: 8px;
}

.force-login-button small {
  font-size: 11px;
  color: #666;
  margin-top: 3px;
}

.force-login-button:hover {
  background-color: #e8e8e8;
}

.force-login-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.drive-icon {
  width: 24px;
  height: 24px;
}

.error-message {
  color: #e53935;
  font-size: 14px;
  margin-top: 10px;
}
</style>
