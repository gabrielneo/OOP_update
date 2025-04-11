/**
 * Service for Google Drive API integration
 */

// Your Google OAuth client ID from credential.json
const CLIENT_ID = '589680581930-6bej4avcnbru7ggppr5ur47q2a8n602h.apps.googleusercontent.com';

/**
 * Check if the user is signed in to Google
 */
export const checkLoginStatus = async () => {
  try {
    const response = await fetch("/api/auth/status", {
      method: 'GET',
      credentials: "include",
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (!response.ok) {
      return { isLoggedIn: false };
    }
    
    const data = await response.json();
    return { 
      isLoggedIn: data.isLoggedIn || false,
      userData: data.userData || null
    };
  } catch (error) {
    console.error('Error checking login status:', error);
    return { isLoggedIn: false };
  }
};

/**
 * Sign in to Google and authorize the app
 */
export const signIn = async () => {
  try {
    // Clear any existing tokens
    await fetch("/api/auth/clear-tokens", {
      method: 'GET',
      credentials: "include",
      headers: {
        'Accept': 'application/json'
      }
    });
    
    // Get the authorization URL
    const response = await fetch("/api/auth/url", {
      method: 'GET',
      credentials: "include",
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (!response.ok) {
      throw new Error("Failed to get authorization URL");
    }
    
    const data = await response.json();
    
    if (!data.url) {
      throw new Error("Invalid authorization URL response");
    }
    
    // Redirect to Google's auth page
    window.location.href = data.url;
    
    return true;
  } catch (error) {
    console.error('Error during sign in:', error);
    throw error;
  }
};

/**
 * Sign out from Google
 */
export const signOut = async () => {
  try {
    const response = await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (!response.ok) {
      throw new Error('Failed to logout');
    }
    
    return await response.json();
  } catch (error) {
    console.error('Error signing out:', error);
    throw error;
  }
};

/**
 * Test the connection to Google Drive
 */
export const testDriveConnection = async () => {
  try {
    const response = await fetch('/api/drive/test-connection', {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (response.status === 401) {
      return { connectionSuccessful: false, loginRequired: true };
    }
    
    if (!response.ok) {
      throw new Error('Failed to test connection');
    }
    
    return await response.json();
  } catch (error) {
    console.error('Error testing drive connection:', error);
    throw error;
  }
};

/**
 * List files from Google Drive
 */
export const listDriveFiles = async (folderId = null, onlyImages = false) => {
  try {
    let url = '/api/drive/files';
    const params = new URLSearchParams();
    
    if (folderId) {
      params.append('folderId', folderId);
    }
    
    if (onlyImages) {
      params.append('imagesOnly', 'true');
    }
    
    if (params.toString()) {
      url += '?' + params.toString();
    }
    
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (response.status === 401) {
      throw new Error('Not authenticated. Please login first.');
    }
    
    if (!response.ok) {
      throw new Error('Failed to list files');
    }
    
    return await response.json();
  } catch (error) {
    console.error('Error listing drive files:', error);
    throw error;
  }
};

/**
 * Get file content from Google Drive
 */
export const getFileContent = async (fileId) => {
  try {
    const response = await fetch(`/api/drive/files/${fileId}/content`, {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (response.status === 401) {
      throw new Error('Not authenticated. Please login first.');
    }
    
    if (!response.ok) {
      throw new Error('Failed to get file content');
    }
    
    return await response.json();
  } catch (error) {
    console.error('Error getting file content:', error);
    throw error;
  }
};

/**
 * Get detailed file metadata from Google Drive
 */
export const getFileDetails = async (fileId) => {
  try {
    const response = await fetch(`/api/drive/files/${fileId}/details`, {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (response.status === 401) {
      throw new Error('Not authenticated. Please login first.');
    }
    
    if (!response.ok) {
      throw new Error('Failed to get file details');
    }
    
    return await response.json();
  } catch (error) {
    console.error('Error getting file details:', error);
    throw error;
  }
};

/**
 * Attempt to fix token issues by forcing a refresh
 */
export const fixTokens = async () => {
  try {
    console.log('Attempting to fix token issues...');
    const response = await fetch('/api/auth/fix-tokens', {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (!response.ok) {
      throw new Error('Failed to fix tokens');
    }
    
    const result = await response.json();
    console.log('Token fix result:', result);
    return result;
  } catch (error) {
    console.error('Error fixing tokens:', error);
    throw error;
  }
};

export default {
  checkLoginStatus,
  signIn,
  signOut,
  testDriveConnection,
  listDriveFiles,
  getFileContent,
  getFileDetails,
  fixTokens
}; 