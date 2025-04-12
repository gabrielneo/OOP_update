<template>
  <div class="header">
    <div class="left-section">
      <button class="header-btn" @click="$emit('new-project')" title="Create New (Ctrl+N)">
        <i-lucide-file-plus class="icon" />
        <span>New</span>
      </button>
      <button class="header-btn" @click="$emit('save-project')" title="Save Project (Ctrl+S)">
        <i-lucide-save class="icon" />
        <span>Save</span>
      </button>
      <button class="header-btn" @click="handleExport" title="Export as PNG (Ctrl+E)">
        <i-lucide-download class="icon" />
        <span>Export</span>
      </button>
    </div>
    
    <div class="drive-section">
      <slot name="google-drive-button"></slot>
      <slot name="google-drive-export-button"></slot>
    </div>

    <div class="image-dimensions" v-if="imageDimensions">
      <span class="dimensions-label">Image Size:</span>
      <span class="dimensions-value">{{ imageDimensions.width }} × {{ imageDimensions.height }}px</span>
      <span class="dimensions-separator">|</span>
      <span class="dimensions-value">{{ pixelsToMm(imageDimensions.width).toFixed(1) }} × {{ pixelsToMm(imageDimensions.height).toFixed(1) }}mm</span>
    </div>

    <div class="right-menu">
      <button
        class="header-btn undo-redo-btn"
        @click="$emit('undo')"
        :disabled="!canUndo"
        title="Undo (Ctrl+Z)"
      >
        <i-lucide-undo-2 class="icon" />
        <span>Undo</span>
      </button>
      <button
        class="header-btn undo-redo-btn"
        @click="$emit('redo')"
        :disabled="!canRedo"
        title="Redo (Ctrl+Y)"
      >
        <i-lucide-redo-2 class="icon" />
        <span>Redo</span>
      </button>
      <button
        class="header-btn undo-redo-btn"
        @click="$emit('reset')"
        :disabled="!canReset"
        title="Reset to Original"
      >
        <i-lucide-redo-2 class="icon" />
        <span>Reset</span>
      </button>
        <span>‎ ‎ ‎ </span>
    </div>

    <!-- Export Dialog -->
    <div v-if="showExportDialog" class="export-dialog">
      <div class="export-dialog-content">
        <h3>Export Image</h3>
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
          <button class="btn cancel" @click="showExportDialog = false">Cancel</button>
          <button class="btn export" @click="confirmExport">Export</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "Header",
  props: {
    canUndo: {
      type: Boolean,
      default: false,
    },
    canRedo: {
      type: Boolean,
      default: false,
    },
    canReset: {
      type: Boolean,
      default: false,
    },
    imageDimensions: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      showExportDialog: false,
      exportFilename: "",
    };
  },
  emits: ["new-project", "save-project", "export-project", "undo", "redo", "reset"],
  mounted() {
    // Add keyboard shortcuts
    document.addEventListener('keydown', this.handleKeyboardShortcuts);
  },
  beforeUnmount() {
    // Clean up event listeners
    document.removeEventListener('keydown', this.handleKeyboardShortcuts);
  },
  methods: {
    pixelsToMm(pixels) {
      // Approximate conversion from pixels to millimeters
      // Using a standard factor of 3.78 pixels per mm (same as in the app)
      const pxPerMm = 3.78;
      return pixels / pxPerMm;
    },
    handleExport() {
      // Generate a default filename based on date and time
      const now = new Date();
      const dateStr = now.toISOString().slice(0, 10); // YYYY-MM-DD
      this.exportFilename = `id-photo-${dateStr}`;
      
      // Show the export dialog
      this.showExportDialog = true;
      
      // Focus the filename input after dialog appears
      this.$nextTick(() => {
        if (this.$refs.filenameInput) {
          this.$refs.filenameInput.focus();
          this.$refs.filenameInput.select();
        }
      });
    },
    confirmExport() {
      if (!this.exportFilename.trim()) {
        // If filename is empty, use a default
        this.exportFilename = `id-photo-${new Date().toISOString().slice(0, 10)}`;
      }
      
      // Close the dialog
      this.showExportDialog = false;
      
      // Emit the export event with the filename
      this.$emit('export-project', this.exportFilename);
    },
    handleKeyboardShortcuts(event) {
      // Only handle keyboard shortcuts when no input is focused
      if (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA') {
        return;
      }
      
      // Check for Ctrl+E (export)
      if (event.ctrlKey && event.key === 'e') {
        event.preventDefault();
        this.handleExport();
      }
    }
  }
};
</script>

<style scoped>
.header {
  display: grid;
  grid-template-columns: auto auto auto auto;
  grid-template-areas: "left drive dimensions right";
  gap: 16px;
  align-items: center;
  padding: 8px 16px;
  background-color: #252a2e;
  border-bottom: 1px solid #3a3f45;
  height: 50px;
  width: 100%;
  overflow: visible;
}

.left-section {
  display: flex;
  gap: 8px;
  grid-area: left;
}

.drive-section {
  display: flex;
  gap: 8px;
  grid-area: drive;
}

.image-dimensions {
  display: flex;
  align-items: center;
  padding: 5px 10px;
  background-color: #3a3f45;
  border-radius: 4px;
  font-size: 14px;
  grid-area: dimensions;
  min-width: 0;
  white-space: nowrap;
}

.dimensions-label {
  color: #aaaaaa;
  margin-right: 5px;
}

.dimensions-value {
  color: #ffffff;
  font-weight: bold;
  font-family: monospace;
}

.dimensions-separator {
  color: #aaaaaa;
  margin: 0 8px;
}

.right-menu {
  display: flex;
  gap: 8px;
  justify-self: end;
  grid-area: right;
  padding-right: 5px;
}

.header-btn {
  background: transparent;
  border: none;
  color: #ffffff;
  padding: 6px 8px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: background-color 0.2s ease;
  white-space: nowrap;
}

.header-btn:hover {
  background-color: #3a3f45;
}

.header-btn:disabled {
  color: #5a5f65;
  cursor: not-allowed;
}

.header-btn .icon {
  width: 16px;
  height: 16px;
}

.undo-redo-btn {
  color: #ffffff;
}

.undo-redo-btn:disabled {
  color: #5a5f65;
}

@media (max-width: 1200px) {
  .header {
    gap: 10px;
  }
  
  .dimensions-value:last-child {
    display: none; /* Hide mm dimensions on medium screens */
  }
  
  .dimensions-separator {
    display: none;
  }
}

@media (max-width: 1000px) {
  .header {
    grid-template-columns: auto auto 1fr auto;
    grid-template-areas: 
      "left drive dimensions right";
    gap: 8px;
    padding: 8px 10px;
  }
}

@media (max-width: 900px) {
  .image-dimensions {
    font-size: 12px;
    padding: 4px 8px;
  }
}

@media (max-width: 800px) {
  .header-btn {
    padding: 6px 5px;
  }
  
  .right-menu {
    gap: 5px;
  }
  
  .header {
    gap: 5px;
  }
}

@media (max-width: 750px) {
  .header {
    grid-template-columns: auto auto auto;
    grid-template-areas: 
      "left drive right"
      "dimensions dimensions dimensions";
    grid-template-rows: auto auto;
    height: auto;
    padding: 8px 10px 4px;
  }
  
  .image-dimensions {
    margin-top: 4px;
    justify-self: center;
    grid-column: 1 / -1;
  }
}

@media (max-width: 650px) {
  .header-btn span {
    display: none;
  }
  
  .image-dimensions {
    max-width: none;
    justify-self: center;
  }
}

/* Export Dialog Styles */
.export-dialog {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.export-dialog-content {
  background-color: #252a2e;
  border-radius: 8px;
  padding: 20px;
  width: 350px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  border: 1px solid #3a3f45;
}

.export-dialog h3 {
  margin-top: 0;
  margin-bottom: 15px;
  font-size: 18px;
  color: #ffffff;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
  font-size: 14px;
  color: #cccccc;
}

.form-group input {
  width: 100%;
  padding: 8px 10px;
  border-radius: 4px;
  border: 1px solid #3a3f45;
  background-color: #1e2124;
  color: #ffffff;
  font-size: 14px;
}

.dialog-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn {
  padding: 8px 16px;
  border-radius: 4px;
  border: none;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.btn.cancel {
  background-color: #3a3f45;
  color: #ffffff;
}

.btn.export {
  background-color: #4a90e2;
  color: #ffffff;
}

.btn.cancel:hover {
  background-color: #4a4f55;
}

.btn.export:hover {
  background-color: #3a80d2;
}
</style>
