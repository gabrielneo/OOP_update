<template>
  <div class="header-container">
    <div class="header">
      <div class="left-section">
        <button class="header-btn" @click="$emit('new-project')" title="Create New (Ctrl+N)">
          <svg class="icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg>
          <span>New</span>
        </button>
        <button class="header-btn" @click="$emit('save-project')" title="Save Project (Ctrl+S)">
          <svg class="icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
          <span>Save</span>
        </button>
        <button class="header-btn" @click="handleExport" title="Export as PNG (Ctrl+E)">
          <svg class="icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          <span>Export</span>
        </button>
      </div>
      
      <div class="drive-section">
        <slot name="google-drive-button"></slot>
        <slot name="google-drive-export-button"></slot>
      </div>

      <div class="right-menu">
        <button
          class="header-btn undo-redo-btn"
          @click="$emit('undo')"
          :disabled="!canUndo"
          title="Undo (Ctrl+Z)"
        >
          <svg class="icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 14 4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5v0a5.5 5.5 0 0 1-5.5 5.5H11"/></svg>
          <span>Undo</span>
        </button>
        <button
          class="header-btn undo-redo-btn"
          @click="$emit('redo')"
          :disabled="!canRedo"
          title="Redo (Ctrl+Y)"
        >
          <svg class="icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 14 5-5-5-5"/><path d="M20 9H9.5A5.5 5.5 0 0 0 4 14.5v0A5.5 5.5 0 0 0 9.5 20H13"/></svg>
          <span>Redo</span>
        </button>
        <button
          class="header-btn undo-redo-btn reset-btn"
          @click="$emit('reset')"
          :disabled="!canReset"
          title="Reset to Original"
        >
          <svg class="icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 2v6h6"/><path d="M21 12A9 9 0 0 0 6 5.3L3 8"/><path d="M21 22v-6h-6"/><path d="M3 12a9 9 0 0 0 15 6.7l3-2.7"/></svg>
          <span>Reset</span>
        </button>
      </div>
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
  },
  mounted() {
    // Add keyboard shortcuts
    document.addEventListener('keydown', this.handleKeyboardShortcuts);
  },
  beforeUnmount() {
    // Clean up event listeners
    document.removeEventListener('keydown', this.handleKeyboardShortcuts);
  }
};
</script>

<style scoped>
.header-container {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 8px; /* Reduced padding */
  background-color: #252a2e;
  border-bottom: 1px solid #3a3f45;
  height: 50px;
  width: 100%;
  overflow: visible;
  min-width: 1050px; /* Increased to match app container */
}

.left-section {
  display: flex;
  gap: 4px; /* Reduced gap */
  min-width: 160px;
  flex-shrink: 1;
}

.drive-section {
  display: flex;
  gap: 4px; /* Reduced gap */
  justify-content: center;
  min-width: 280px;
  flex-shrink: 1;
}

.right-menu {
  display: flex;
  gap: 4px; /* Reduced gap */
  padding-right: 20px;
  width: 280px; /* Further increased width to fit all button text */
  flex-shrink: 0;
  justify-content: flex-end;
}

.header-btn {
  background: transparent;
  border: none;
  color: #ffffff;
  padding: 4px 6px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px; /* Space between icon and text */
  cursor: pointer;
  transition: background-color 0.2s ease;
  white-space: nowrap;
  font-size: 12px; /* Smaller font */
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
  flex-shrink: 0;
  min-width: 16px;
}

/* Show text in buttons by default */
.header-btn span:not(.sr-only) {
  display: inline; /* Show text */
}

/* Hide text in smaller viewports */
@media (max-width: 1150px) {
  .header-btn span:not(.sr-only) {
    display: none;
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
