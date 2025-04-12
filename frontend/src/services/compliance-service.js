import apiClient from './api-client';

/**
 * Service for checking image compliance
 */
class ComplianceService {
  /**
   * Check if the current image meets compliance standards
   * @param {File|Blob} imageBlob - The image blob to check
   * @param {object} options - Additional options for the compliance check
   * @returns {Promise} Promise resolving to compliance check result
   */
  async checkImageCompliance(imageBlob, options = {}) {
    try {
      // Validate input
      if (!imageBlob) {
        console.error('No image blob provided for compliance check');
        return {
          compliant: false,
          issues: ['No image provided for compliance check'],
          message: 'Compliance check failed'
        };
      }
      
      console.log('Checking compliance with image blob:', {
        type: imageBlob.type,
        size: imageBlob.size,
        lastModified: imageBlob.lastModified,
        options
      });
      
      // Create form data to send the image
      const formData = new FormData();
      formData.append('image', imageBlob, 'image.jpg');
      
      // Add background replacement information if available
      if (options.hasReplacedBackground) {
        formData.append('hasReplacedBackground', 'true');
        if (options.backgroundColor) {
          formData.append('backgroundColor', options.backgroundColor);
        }
      }
      
      console.log('Sending compliance check request to server...');
      
      try {
        // Make API call to compliance service with direct URL and credentials
        const response = await fetch('http://localhost:8080/api/compliance/check', {
          method: 'POST',
          body: formData,
          credentials: 'include', // Include cookies if needed for session
          mode: 'cors', // Explicitly enable CORS
          // Don't set Content-Type for FormData - browser sets it automatically with boundary
        });
        
        console.log('Received response from server:', response.status);
        
        // Check if the response is OK (status in the range 200-299)
        if (!response.ok) {
          console.error('Server returned error status:', response.status);
          return {
            compliant: false,
            issues: [`Server error: ${response.status} ${response.statusText}`],
            message: 'Compliance check failed'
          };
        }
        
        // Parse the JSON response
        try {
          const data = await response.json();
          console.log('Response data:', data);
          
          // Check if response has expected structure
          if (data && typeof data === 'object') {
            return data;
          } else {
            console.error('Unexpected response format:', data);
            return {
              compliant: false,
              issues: ['Server returned an unexpected response format'],
              message: 'Compliance check error'
            };
          }
        } catch (parseError) {
          console.error('Error parsing server response:', parseError);
          return {
            compliant: false,
            issues: ['Could not parse server response'],
            message: 'Compliance check error'
          };
        }
      } catch (fetchError) {
        console.error('Network error during compliance check:', fetchError);
        return {
          compliant: false,
          issues: ['Cannot connect to the server. Make sure your backend is running at http://localhost:8080'],
          message: 'Server connection failed'
        };
      }
    } catch (error) {
      console.error('Compliance check failed:', error);
      
      // Provide structured error even if API fails
      return {
        compliant: false,
        issues: [`Request error: ${error.message || 'Unknown error'}`],
        message: 'Compliance check failed'
      };
    }
  }
  
  /**
   * Convert a base64 image to Blob
   * @param {string} base64Image - Base64 encoded image data
   * @returns {Blob} Blob representation of the image
   */
  async base64ToBlob(base64Image) {
    try {
      if (!base64Image) {
        console.error('No base64 image data provided');
        return null;
      }
      
      // Add debug information about the provided image
      console.log('Image data type:', typeof base64Image);
      console.log('Image data length:', base64Image.length);
      console.log('Image data starts with:', base64Image.substring(0, 50) + '...');
      
      // Handle data URLs that don't contain the expected format
      if (typeof base64Image !== 'string') {
        console.error('Invalid base64Image type:', typeof base64Image);
        return null;
      }
      
      // Remove the data URL prefix if it exists
      const base64Data = base64Image.includes(',') 
        ? base64Image.split(',')[1] 
        : base64Image;
      
      try {
        // Decode base64
        const byteCharacters = atob(base64Data);
        console.log('Successfully decoded base64, length:', byteCharacters.length);
        
        const byteArrays = [];
        
        for (let offset = 0; offset < byteCharacters.length; offset += 512) {
          const slice = byteCharacters.slice(offset, offset + 512);
          
          const byteNumbers = new Array(slice.length);
          for (let i = 0; i < slice.length; i++) {
            byteNumbers[i] = slice.charCodeAt(i);
          }
          
          const byteArray = new Uint8Array(byteNumbers);
          byteArrays.push(byteArray);
        }
        
        const blob = new Blob(byteArrays, { type: 'image/jpeg' });
        console.log('Successfully created blob, size:', blob.size);
        return blob;
      } catch (e) {
        console.error('Error in base64 conversion:', e);
        console.error('Error details:', e.stack);
        
        // Fall back to creating a simple image blob if we have something that looks like a data URL
        if (base64Image.startsWith('data:image')) {
          try {
            console.log('Trying alternative blob creation method');
            const response = await fetch(base64Image);
            const blob = await response.blob();
            return blob;
          } catch (fetchError) {
            console.error('Alternative blob creation failed:', fetchError);
            return null;
          }
        }
        
        return null;
      }
    } catch (error) {
      console.error('Error converting base64 to blob:', error);
      console.error('Error stack:', error.stack);
      return null;
    }
  }

  /**
   * Process image for compliance check - handles both base64 strings and blob URLs
   * @param {string|Blob} imageSource - Either a base64 string, blob URL, or Blob object
   * @returns {Promise<Blob>} - A blob representation of the image
   */
  async processImageForCompliance(imageSource) {
    try {
      console.log('Processing image source type:', typeof imageSource);
      
      // If already a blob, just return it
      if (imageSource instanceof Blob) {
        console.log('Image is already a Blob, size:', imageSource.size);
        return imageSource;
      }
      
      // If it's a string, check if it's a blob URL or base64
      if (typeof imageSource === 'string') {
        // Check if it's a blob URL (starts with blob:)
        if (imageSource.startsWith('blob:') || imageSource.startsWith('http')) {
          console.log('Image is a URL, fetching as blob');
          try {
            const response = await fetch(imageSource);
            if (!response.ok) {
              throw new Error(`Failed to fetch image: ${response.status} ${response.statusText}`);
            }
            const blob = await response.blob();
            console.log('Successfully fetched blob from URL, size:', blob.size);
            return blob;
          } catch (fetchError) {
            console.error('Error fetching image from URL:', fetchError);
            throw fetchError;
          }
        } 
        // If it's a base64 string
        else if (imageSource.includes('base64,') || this.isBase64(imageSource)) {
          console.log('Image is a base64 string, converting to blob');
          return await this.base64ToBlob(imageSource);
        }
      }
      
      console.error('Unsupported image source format');
      throw new Error('Unsupported image source format');
    } catch (error) {
      console.error('Error processing image for compliance:', error);
      throw error;
    }
  }

  /**
   * Check if a string is base64 encoded
   * @param {string} str - The string to check
   * @returns {boolean} - True if the string is base64 encoded
   */
  isBase64(str) {
    try {
      // Simple check if the string could be base64
      return btoa(atob(str)) === str;
    } catch (e) {
      return false;
    }
  }

  /**
   * Test if the backend server is accessible
   * @returns {Promise<boolean>} True if the backend is accessible
   */
  async testBackendConnection() {
    try {
      console.log('Testing direct connection to backend...');
      const response = await fetch('http://localhost:8080/api/ping', {
        method: 'GET',
        mode: 'cors'
      });
      
      if (response.ok) {
        console.log('Direct backend connection successful!');
        return true;
      } else {
        console.error('Backend server responded with error:', response.status);
        return false;
      }
    } catch (error) {
      console.error('Could not connect to backend server:', error);
      return false;
    }
  }
}

export default new ComplianceService(); 