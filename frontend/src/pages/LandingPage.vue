<template>
    <div class="landing-page">
        <div class="container">
            <!-- Upload Panel -->
            <div class="panel upload-panel" :class="{ 'drag-over': isDragging }" @dragover.prevent="onDragOver"
                @dragleave.prevent="onDragLeave" @drop.prevent="onDrop">
                <h1 class="title">Advanced Photo Editor & Design Maker</h1>
                <div class="subtitle">
                    Welcome to the free advanced photo editor by Pixlr. Start editing by clicking
                    on the open photo button, drag n' drop a file, paste from the clipboard (ctrl+v)
                    or select one of our pre-made templates below.
                </div>

                <div class="action-buttons">
                    <button class="primary-btn" @click="openFileDialog">
                        Open Image
                    </button>
                    <button class="secondary-btn" @click="connectToGoogleDrive">
                        Connect to Google Drive
                    </button>
                    <input type="file" ref="fileInput" @change="handleFileSelect" accept=".jpg,.jpeg,.png" multiple
                        class="hidden-input" />
                </div>

                <div class="additional-options">
                    <span>Load url</span>
                    <span class="separator">or</span>
                    <span>Start design</span>
                </div>
            </div>

            <!-- Recent Projects Panel -->
            <div class="panel projects-panel">
                <div class="section-header">
                    <h2>Latest projects</h2>
                    <a href="#" class="view-all">View All</a>
                </div>
                <div class="section-subtitle">
                    From the local temporary cache, to save a project long term save it as PXZ (Pixlr document)
                </div>

                <div class="projects-grid">
                    <!-- Project thumbnails would go here -->
                </div>
            </div>
        </div>

        <!-- Image confirmation modal -->
        <div class="modal" v-if="showModal">
            <div class="modal-content">
                <div class="modal-header">
                    <h3>Confirm Image Selection</h3>
                    <button class="close-btn" @click="closeModal">×</button>
                </div>
                <div class="modal-body">
                    <div v-if="selectedImages.length > 0" class="selected-images">
                        <div v-for="(image, index) in selectedImages" :key="index" class="image-preview">
                            <img :src="image.preview" :alt="image.name" />
                            <div class="image-name">{{ image.name }}</div>
                        </div>
                    </div>
                    <div v-else class="no-images">
                        No valid images selected. Please select .jpg, .jpeg, or .png files.
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="secondary-btn" @click="closeModal">Cancel</button>
                    <button class="primary-btn" @click="confirmImages" :disabled="selectedImages.length === 0">
                        Confirm
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import axios from "axios";
export default {
    data() {
        return {
            isDragging: false,
            showModal: false,
            selectedImages: [],
            allowedFileTypes: ['image/jpeg', 'image/jpg', 'image/png']
        };
    },
    methods: {
        openFileDialog() {
            this.$refs.fileInput.click();
        },
        connectToGoogleDrive() {
            // Implement Google Drive connection logic
            console.log('Connecting to Google Drive...');
        },
        handleFileSelect(event) {
            const files = event.target.files;
            if (files.length > 0) {
                this.processFiles(files);
            }
        },
        onDragOver(event) {
            this.isDragging = true;
            // Add animation class for visual feedback
            event.dataTransfer.dropEffect = 'copy';
        },
        onDragLeave() {
            this.isDragging = false;
        },
        onDrop(event) {
            this.isDragging = false;
            const files = event.dataTransfer.files;
            if (files.length > 0) {
                this.processFiles(files);
            }
        },
        processFiles(files) {
            // Process the selected files
            this.selectedImages = [];
            let validFilesCount = 0;

            for (let i = 0; i < files.length; i++) {
                const file = files[i];

                // Check if file type is allowed
                if (this.allowedFileTypes.includes(file.type)) {
                    validFilesCount++;
                    const reader = new FileReader();
                    reader.onload = (e) => {
                        this.selectedImages.push({
                            name: file.name,
                            preview: e.target.result,
                            file: file
                        });

                        // If this is the last valid file, show the modal
                        if (this.selectedImages.length === validFilesCount) {
                            this.showModal = true;
                        }
                    };
                    reader.readAsDataURL(file);
                }
            }

            // If no valid files were selected, still show the modal with a message
            if (validFilesCount === 0) {
                this.showModal = true;
            }
        },
        closeModal() {
            this.showModal = false;
            this.selectedImages = [];
            // Reset file input
            this.$refs.fileInput.value = '';
        },
        confirmImages() {
            if (this.selectedImages.length === 0) {
                return;
            }

            const formData = new FormData();

            this.selectedImages.forEach((imageObj) => {
                let file = imageObj.file;

                // Case 1: Already a File object (from file input)
                if (file instanceof File) {
                    formData.append('files', file);
                }

                // Case 2: If base64 from Google Drive (we tagged it in `imageObj.isBase64`)
                else if (imageObj.isBase64 && imageObj.content && imageObj.mimeType) {
                    // Convert base64 to Blob → File
                    const byteCharacters = atob(imageObj.content);
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

                    const blob = new Blob(byteArrays, { type: imageObj.mimeType });
                    file = new File([blob], imageObj.name || "google_drive_image.jpg", { type: imageObj.mimeType });
                    formData.append("files", file);
                }
            });

            formData.append("description", "ID Photo upload");

            axios.post("http://localhost:8080/api/upload", formData, {
                headers: {
                    "Content-Type": "multipart/form-data",
                },
            })
                .then((response) => {
                    console.log("Upload successful", response);
                    this.$router.push("EditingPage");
                })
                .catch((error) => {
                    console.error("Upload failed", error);
                });

            this.closeModal();
        }


        

    }
}
</script>

<style scoped>
.landing-page {
    min-height: 100vh;
    background-color: #1e2124;
    color: #ffffff;
    font-family: 'Inter', sans-serif;
    padding: 40px 20px;
    display: flex;
    justify-content: center;
    align-items: flex-start;
}

.container {
    width: 100%;
    max-width: 1000px;
    display: flex;
    flex-direction: column;
    gap: 30px;
}

.panel {
    background-color: #252a2e;
    border-radius: 12px;
    padding: 40px;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
}

.upload-panel {
    text-align: center;
    transition: all 0.3s ease;
}

.upload-panel.drag-over {
    background-color: #2c3238;
    box-shadow: 0 0 0 2px #4ecdc4, 0 10px 25px rgba(0, 0, 0, 0.3);
    transform: scale(1.01);
    animation: shake 0.5s ease-in-out;
}

@keyframes shake {

    0%,
    100% {
        transform: scale(1.01) translateX(0);
    }

    20% {
        transform: scale(1.01) translateX(-10px);
    }

    40% {
        transform: scale(1.01) translateX(10px);
    }

    60% {
        transform: scale(1.01) translateX(-5px);
    }

    80% {
        transform: scale(1.01) translateX(5px);
    }
}

.title {
    font-size: 28px;
    font-weight: 500;
    margin-bottom: 10px;
    position: relative;
    display: inline-block;
}

.title::after {
    content: '';
    position: absolute;
    bottom: -10px;
    left: 50%;
    transform: translateX(-50%);
    width: 40px;
    height: 3px;
    background-color: #4ecdc4;
}

.subtitle {
    max-width: 600px;
    margin: 30px auto 40px;
    color: #aaaaaa;
    line-height: 1.6;
}

.action-buttons {
    display: flex;
    justify-content: center;
    gap: 15px;
    margin-bottom: 20px;
}

.primary-btn {
    background-color: #4ecdc4;
    color: #1e2124;
    border: none;
    padding: 12px 24px;
    border-radius: 4px;
    font-size: 16px;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.2s;
}

.primary-btn:hover {
    background-color: #3dbdb5;
}

.primary-btn:disabled {
    background-color: #2a8a84;
    opacity: 0.7;
    cursor: not-allowed;
}

.secondary-btn {
    background-color: transparent;
    color: #ffffff;
    border: 1px solid #4a4f55;
    padding: 12px 24px;
    border-radius: 4px;
    font-size: 16px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
}

.secondary-btn:hover {
    background-color: rgba(255, 255, 255, 0.1);
    border-color: #ffffff;
}

.additional-options {
    display: flex;
    justify-content: center;
    gap: 15px;
    color: #aaaaaa;
    font-size: 14px;
}

.additional-options span {
    cursor: pointer;
}

.additional-options span:hover {
    color: #ffffff;
    text-decoration: underline;
}

.separator {
    color: #555;
    cursor: default;
}

.separator:hover {
    color: #555;
    text-decoration: none;
}

.projects-panel {
    min-height: 200px;
}

.section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
}

.section-header h2 {
    font-size: 20px;
    font-weight: 500;
    margin: 0;
    position: relative;
    display: inline-block;
}

.section-header h2::after {
    content: '';
    position: absolute;
    bottom: -5px;
    left: 0;
    width: 30px;
    height: 2px;
    background-color: #4ecdc4;
}

.view-all {
    color: #aaaaaa;
    text-decoration: none;
    font-size: 14px;
}

.view-all:hover {
    color: #ffffff;
    text-decoration: underline;
}

.section-subtitle {
    color: #aaaaaa;
    font-size: 14px;
    margin-bottom: 20px;
}

.projects-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 20px;
}

.hidden-input {
    display: none;
}

/* Modal styles */
.modal {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.7);
    display: flex;
    justify-content: center;
    align-items: center;
    z-index: 1000;
}

.modal-content {
    background-color: #252a2e;
    border-radius: 8px;
    width: 90%;
    max-width: 600px;
    max-height: 80vh;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    box-shadow: 0 15px 30px rgba(0, 0, 0, 0.3);
}

.modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 15px 20px;
    border-bottom: 1px solid #3a3f45;
}

.modal-header h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 500;
}

.close-btn {
    background: transparent;
    border: none;
    color: #aaaaaa;
    font-size: 24px;
    cursor: pointer;
    line-height: 1;
}

.close-btn:hover {
    color: #ffffff;
}

.modal-body {
    padding: 20px;
    overflow-y: auto;
    flex: 1;
}

.selected-images {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 15px;
}

.image-preview {
    display: flex;
    flex-direction: column;
    align-items: center;
}

.image-preview img {
    width: 100%;
    height: 120px;
    object-fit: cover;
    border-radius: 4px;
    margin-bottom: 5px;
}

.image-name {
    font-size: 12px;
    color: #aaaaaa;
    text-align: center;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    width: 100%;
}

.no-images {
    text-align: center;
    color: #ff6b6b;
    padding: 20px;
}

.modal-footer {
    padding: 15px 20px;
    border-top: 1px solid #3a3f45;
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}

/* Responsive adjustments */
@media (max-width: 768px) {
    .landing-page {
        padding: 20px 10px;
    }

    .panel {
        padding: 30px 20px;
    }

    .title {
        font-size: 24px;
    }

    .action-buttons {
        flex-direction: column;
        align-items: center;
    }

    .primary-btn,
    .secondary-btn {
        width: 100%;
        max-width: 300px;
    }
}
</style>