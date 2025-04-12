import { createApiClient } from './axios-config';

// Create API client with baseURL preset to /api
const apiClient = createApiClient();

/**
 * Service for handling authentication with Google.
 * Provides methods for login, logout, and checking auth status.
 */
export default {
  /**
   * Get the authentication status
   * @returns {Promise<Object>} Auth status and message
   */
  async getStatus() {
    try {
      console.log('Checking authentication status...');
      // Use the proxy URL to avoid CORS issues
      const response = await fetch(`/api/auth/status`, {
        method: 'GET',
        credentials: 'include',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
        }
      });
      
      if (!response.ok) {
        throw new Error(`Status check failed: ${response.status} ${response.statusText}`);
      }
      
      const data = await response.json();
      console.log('Auth status response:', data);
      return data;
    } catch (error) {
      console.error('Error checking auth status:', error);
      // Log more detailed error information
      if (error.response) {
        console.error('Response error data:', error.response.data);
        console.error('Response status:', error.response.status);
      } else if (error.request) {
        console.error('No response received:', error.request);
      }
      return {
        authenticated: false,
        message: `Error: ${error.message}`
      };
    }
  },

  /**
   * Start the Google OAuth flow
   * Opens the Google authorization page in a new window
   */
  async initiateLogin() {
    try {
      // Use the correct endpoint to get the authorization URL
      const response = await fetch(`/api/auth/url`, {
        method: 'GET',
        credentials: 'include',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to get authorization URL: ${response.status} ${response.statusText}`);
      }
      
      const data = await response.json();
      
      if (!data.url) {
        throw new Error('Invalid authorization URL response');
      }
      
      console.log('Redirecting to Google auth URL:', data.url);
      // Store the current URL to redirect back to after auth
      localStorage.setItem('auth_redirect', window.location.href);
      
      // Redirect to the Google authorization URL
      window.location.href = data.url;
    } catch (error) {
      console.error('Error initiating Google login:', error);
      return {
        success: false,
        message: `Error: ${error.message}`
      };
    }
  },
  
  /**
   * Force a completely new login by clearing all existing credentials
   * and starting a fresh Google account selection flow
   * @returns {Promise<Object>} Login result
   */
  async forceNewLogin() {
    try {
      console.log('Forcing new login by wiping all credentials...');
      // First, clear all existing credentials
      const response = await fetch(`/api/auth/force-new-login`, {
        method: 'GET',
        credentials: 'include',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
        }
      });
      
      if (!response.ok) {
        throw new Error(`Force new login failed: ${response.status} ${response.statusText}`);
      }
      
      const data = await response.json();
      
      if (!data.url) {
        throw new Error('Invalid authorization URL response from force-new-login');
      }
      
      console.log('Force new login succeeded, redirecting to Google auth URL');
      // Store the current URL to redirect back to after auth
      localStorage.setItem('auth_redirect', window.location.href);
      localStorage.setItem('force_new_login', 'true');
      
      // Redirect to the Google authorization URL with force approval
      window.location.href = data.url;
      return data;
    } catch (error) {
      console.error('Error forcing new login:', error);
      return {
        success: false,
        message: `Error: ${error.message}`
      };
    }
  },

  /**
   * Handle OAuth redirect if there are error parameters
   * Call this on app initialization to handle auth redirects
   */
  handleAuthRedirect() {
    const urlParams = new URLSearchParams(window.location.search);
    const error = urlParams.get('error');
    const message = urlParams.get('message');
    const success = urlParams.get('success');
    
    // Remove the force_new_login flag if it exists
    if (localStorage.getItem('force_new_login')) {
      console.log('Force new login completed');
      localStorage.removeItem('force_new_login');
    }
    
    if (error) {
      console.error('Auth error:', error, message);
      // Clear the URL parameters
      window.history.replaceState({}, document.title, window.location.pathname);
      return {
        error,
        message: message || 'Authentication failed'
      };
    }
    
    if (success === 'true') {
      console.log('Authentication successful');
      // Clear the URL parameters
      window.history.replaceState({}, document.title, window.location.pathname);
      
      // Redirect to the stored redirect URL if available
      const redirectUrl = localStorage.getItem('auth_redirect');
      if (redirectUrl) {
        localStorage.removeItem('auth_redirect');
        if (redirectUrl !== window.location.href) {
          window.location.href = redirectUrl;
        }
      }
      
      return { success: true };
    }
    
    return null;
  },

  /**
   * Logout the user
   * @returns {Promise<Object>} Logout result
   */
  async logout() {
    try {
      console.log('Sending logout request...');
      const response = await fetch(`/api/auth/logout`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
        }
      });
      
      if (!response.ok) {
        throw new Error(`Logout failed: ${response.status} ${response.statusText}`);
      }
      
      const data = await response.json();
      console.log('Logout response:', data);
      return data;
    } catch (error) {
      console.error('Error during logout:', error);
      // Log more detailed error information
      if (error.response) {
        console.error('Response error data:', error.response.data);
        console.error('Response status:', error.response.status);
      }
      return {
        success: false,
        message: `Error: ${error.message}`
      };
    }
  }
}; 