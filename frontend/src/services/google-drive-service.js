import { createApiClient } from './axios-config';

// Create API client with baseURL preset to /api
const apiClient = createApiClient();

/**
 * Service for interacting with Google Drive API through our backend.
 * Uses the Facade pattern to provide a simple interface to the complex API.
 */
export default {
  /**
   * Get the connection status to Google Drive
   * @returns {Promise<Object>} Connection status and message
   */
  async getStatus() {
    try {
      const response = await apiClient.get('/drive/status');
      return response.data;
    } catch (error) {
      console.error('Error checking drive status:', error);
      return {
        connected: false,
        message: `Error: ${error.message}`
      };
    }
  },

  /**
   * List files and folders in Google Drive
   * @param {string} folderId - Optional folder ID to list contents of
   * @param {boolean} onlyImages - If true, only returns image files
   * @returns {Promise<Array>} List of drive files
   */
  async listFiles(folderId = null, onlyImages = false) {
    try {
      const params = {};
      if (folderId) params.folderId = folderId;
      if (onlyImages) params.onlyImages = true;

      const response = await apiClient.get('/drive/files', { params });
      return response.data;
    } catch (error) {
      console.error('Error listing files:', error);
      throw error;
    }
  },

  /**
   * Upload a file to Google Drive
   * @param {File} file - File to upload
   * @param {string} folderId - Optional folder ID to upload to
   * @returns {Promise<Object>} Uploaded file details
   */
  async uploadFile(file, folderId = null) {
    try {
      const formData = new FormData();
      formData.append('file', file);
      if (folderId) formData.append('folderId', folderId);

      const response = await apiClient.post('/drive/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });
      return response.data;
    } catch (error) {
      console.error('Error uploading file:', error);
      throw error;
    }
  },

  /**
   * Update an existing file in Google Drive
   * @param {string} fileId - ID of the file to update
   * @param {File} file - New file content
   * @param {string} newName - Optional new filename
   * @returns {Promise<Object>} Updated file details
   */
  async updateFile(fileId, file, newName = null) {
    try {
      const formData = new FormData();
      formData.append('file', file);
      if (newName) formData.append('newName', newName);

      const response = await apiClient.put(`/drive/files/${fileId}`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });
      
      // Check if we got a new file as a fallback
      if (response.data && response.data.isNewFile) {
        console.log('Created new file instead of updating:', response.data.message);
        return response.data.file;
      }
      
      return response.data;
    } catch (error) {
      console.error('Error updating file:', error);
      throw error;
    }
  },

  /**
   * Create a new folder in Google Drive
   * @param {string} folderName - Name of the folder to create
   * @param {string} parentFolderId - Optional parent folder ID
   * @returns {Promise<Object>} Created folder details
   */
  async createFolder(folderName, parentFolderId = null) {
    try {
      const params = { folderName };
      if (parentFolderId) params.parentFolderId = parentFolderId;

      const response = await apiClient.post('/drive/folders', null, { params });
      return response.data;
    } catch (error) {
      console.error('Error creating folder:', error);
      throw error;
    }
  },

  /**
   * Get file content from Google Drive
   * @param {string} fileId - ID of the file to get content for
   * @returns {Promise<Object>} File content and metadata
   */
  async getFileContent(fileId) {
    try {
      const response = await apiClient.get(`/drive/files/${fileId}/content`);
      return response.data;
    } catch (error) {
      console.error('Error getting file content:', error);
      throw error;
    }
  },

  /**
   * Logout from Google Drive and clear stored credentials
   * @returns {Promise<Object>} Logout result status
   */
  async logout() {
    try {
      const response = await apiClient.post('/drive/logout');
      return response.data;
    } catch (error) {
      console.error('Error logging out from Drive:', error);
      throw error;
    }
  }
}; 