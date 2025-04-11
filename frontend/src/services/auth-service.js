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
   * Handle OAuth redirect if there are error parameters
   * Call this on app initialization to handle auth redirects
   */
  handleAuthRedirect() {
    const urlParams = new URLSearchParams(window.location.search);
    const error = urlParams.get('error');
    const message = urlParams.get('message');
    
    if (error) {
      console.error('Auth error:', error, message);
      // Clear the URL parameters
      window.history.replaceState({}, document.title, window.location.pathname);
      return {
        error,
        message: message || 'Authentication failed'
      };
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