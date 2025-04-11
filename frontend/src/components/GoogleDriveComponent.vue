<template>
  <div class="google-drive-container">
    <h2>Google Drive Integration</h2>

    <div class="drive-actions">
      <div class="upload-section">
        <input
          type="file"
          @change="handleFileUpload"
          ref="fileInput"
          accept="image/*"
        />
        <button @click="uploadFile" :disabled="!selectedFile">
          Upload to Drive
        </button>
      </div>

      <div class="files-list" v-if="files.length > 0">
        <h3>Your Files</h3>
        <ul>
          <li v-for="file in files" :key="file.id">
            {{ file.name }}
            <button @click="downloadFile(file.id)">Download</button>
            <button @click="deleteFile(file.id)">Delete</button>
          </li>
        </ul>
      </div>

      <div v-if="loading" class="loading">Loading...</div>

      <div v-if="error" class="error">
        {{ error }}
      </div>
    </div>
  </div>
</template>

<script>
import axios from "axios";

export default {
  name: "GoogleDriveComponent",
  data() {
    return {
      selectedFile: null,
      files: [],
      loading: false,
      error: null,
    };
  },
  methods: {
    handleFileUpload(event) {
      this.selectedFile = event.target.files[0];
    },
    async uploadFile() {
      if (!this.selectedFile) return;

      this.loading = true;
      this.error = null;

      try {
        const formData = new FormData();
        formData.append("file", this.selectedFile);

        const response = await axios.post("/api/drive/upload", formData, {
          headers: {
            "Content-Type": "multipart/form-data",
          },
        });

        this.$emit("file-uploaded", response.data);
        this.selectedFile = null;
        this.$refs.fileInput.value = "";
        await this.listFiles();
      } catch (error) {
        this.error = "Failed to upload file: " + error.message;
      } finally {
        this.loading = false;
      }
    },
    async listFiles() {
      this.loading = true;
      this.error = null;

      try {
        const response = await axios.get("/api/drive/files");
        this.files = response.data;
      } catch (error) {
        this.error = "Failed to list files: " + error.message;
      } finally {
        this.loading = false;
      }
    },
    async downloadFile(fileId) {
      this.loading = true;
      this.error = null;

      try {
        const response = await axios.get(`/api/drive/download/${fileId}`, {
          responseType: "blob",
        });

        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", "downloaded-file");
        document.body.appendChild(link);
        link.click();
        link.remove();
      } catch (error) {
        this.error = "Failed to download file: " + error.message;
      } finally {
        this.loading = false;
      }
    },
    async deleteFile(fileId) {
      this.loading = true;
      this.error = null;

      try {
        await axios.delete(`/api/drive/${fileId}`);
        await this.listFiles();
      } catch (error) {
        this.error = "Failed to delete file: " + error.message;
      } finally {
        this.loading = false;
      }
    },
  },
  mounted() {
    this.listFiles();
  },
};
</script>

<style scoped>
.google-drive-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.drive-actions {
  margin-top: 20px;
}

.upload-section {
  margin-bottom: 20px;
}

.files-list {
  margin-top: 20px;
}

.files-list ul {
  list-style: none;
  padding: 0;
}

.files-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px;
  border-bottom: 1px solid #eee;
}

button {
  padding: 8px 16px;
  margin-left: 10px;
  background-color: #4caf50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

button:disabled {
  background-color: #cccccc;
  cursor: not-allowed;
}

.loading {
  margin-top: 20px;
  text-align: center;
}

.error {
  margin-top: 20px;
  color: red;
  text-align: center;
}
</style>
