<template>
    <div class="image-selector-panel">
        <div class="panel-header">
            <!-- <h3>Images</h3>
            <button class="add-image-btn" @click="$emit('add-image')">
                <i-lucide-plus class="icon" />
                Add Image
            </button> -->
        </div>
        <div class="thumbnails-container">
            <div v-for="(imageData, filename) in images" :key="filename" class="thumbnail-item"
                :class="{ 'selected': selectedImage === filename }" @click="selectImage(filename)">
                <div class="thumbnail-wrapper">
                    <img :src="'data:image/png;base64,' + imageData" :alt="filename" class="thumbnail-image" />
                </div>
                <div class="thumbnail-name">{{ formatImageName(filename) }}</div>
            </div>
        </div>
    </div>
</template>

<script>
export default {
    props: {
        images: {
            type: Object,
            required: true
        },
        selectedImage: {
            type: String,
            default: null
        }
    },
    methods: {
        selectImage(filename) {
            this.$emit('select-image', filename);
        },
        formatImageName(name) {
            // Truncate long names and remove file extension
            const baseName = name.split('.').slice(0, -1).join('.');
            return baseName.length > 15 ? baseName.substring(0, 12) + '...' : baseName;
        }
    }
}
</script>

<style scoped>
.image-selector-panel {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    background-color: #252a2e;
    border-top: 1px solid #3a3f45;
    padding: 10px 15px;
    height: 140px;
    display: flex;
    flex-direction: column;
    z-index: 50;
    /* Lower z-index so other panels can overlap it */
}

.panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
}

.panel-header h3 {
    margin: 0;
    font-size: 14px;
    font-weight: 500;
    color: #ffffff;
}

.add-image-btn {
    background-color: transparent;
    border: 1px solid #4a4f55;
    color: #ffffff;
    border-radius: 4px;
    padding: 4px 8px;
    font-size: 12px;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 4px;
}

.add-image-btn:hover {
    background-color: rgba(255, 255, 255, 0.1);
}

.add-image-btn .icon {
    width: 14px;
    height: 14px;
}

.thumbnails-container {
    display: flex;
    gap: 20px;
    /* Increased spacing between thumbnails */
    overflow-x: auto;
    padding: 5px 0;
    flex: 1;
    justify-content: center;
    /* Center the thumbnails */
    align-items: center;
}

.thumbnail-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    cursor: pointer;
    width: 80px;
    transition: transform 0.2s;
    flex-shrink: 0;
    /* Prevent thumbnails from shrinking */
}

.thumbnail-item:hover {
    transform: translateY(-2px);
}

.thumbnail-wrapper {
    width: 80px;
    height: 60px;
    border-radius: 4px;
    overflow: hidden;
    border: 2px solid transparent;
    transition: all 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #1a1d20;
}

.thumbnail-item.selected .thumbnail-wrapper {
    border-color: #4a90e2;
    box-shadow: 0 0 12px 3px #0077ffb3;
    /* Enhanced glow effect for selected items */
}

.thumbnail-image {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
    /* Ensures the image fits without cropping */
}

.thumbnail-name {
    margin-top: 5px;
    font-size: 11px;
    color: #aaaaaa;
    text-align: center;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    width: 100%;
}

/* Scrollbar styling */
.thumbnails-container::-webkit-scrollbar {
    height: 6px;
}

.thumbnails-container::-webkit-scrollbar-track {
    background: #1e2124;
    border-radius: 3px;
}

.thumbnails-container::-webkit-scrollbar-thumb {
    background: #3a3f45;
    border-radius: 3px;
}

.thumbnails-container::-webkit-scrollbar-thumb:hover {
    background: #4a4f55;
}
</style>