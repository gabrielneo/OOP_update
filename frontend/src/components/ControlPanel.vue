<template>
  <div class="control-panel">
    <div class="panel-header">
      <button class="close-btn" @click="closePanel">
        <!-- <i-lucide-x class="icon" /> -->

        <div>
          <img src="/assets/app-icons/cancel.png" style="width: 100%" />
        </div>
      </button>
      <h3>{{ getFeatureTitle(feature) }}</h3>
    </div>

    <div class="panel-content">
      <!-- Crop controls -->
      <div v-if="feature === 'crop'" class="control-group">
        <div class="control-item">
          <label>Aspect Ratio</label>
          <div class="ratio-options">
            <button
              v-for="ratio in aspectRatios"
              :key="ratio.value"
              :class="[
                'control-btn',
                { active: selectedRatio === ratio.value },
              ]"
              @click="setAspectRatio(ratio.value)"
            >
              {{ ratio.label }}
            </button>
          </div>
        </div>
        <div class="control-item">
          <label>Current Crop ratio</label>
          <div class="size-inputs">
            <input
              type="number"
              :value="localCropWidth"
              @input="updateWidth($event)"
              @change="applyWidth()"
              :disabled="selectedRatio !== 'custom'"
              class="size-input"
              :class="{ disabled: selectedRatio !== 'custom' }"
              step="0.1"
              min="0.1"
            />
            ×
            <input
              type="number"
              :value="localCropHeight"
              @input="updateHeight($event)"
              @change="applyHeight()"
              :disabled="selectedRatio !== 'custom'"
              class="size-input"
              :class="{ disabled: selectedRatio !== 'custom' }"
              step="0.1"
              min="0.1"
            />
          </div>
          <!-- <button
            class="action-btn apply-dimensions-btn"
            @click="applyDimensions"
          >
            Apply Dimensions
          </button> -->
        </div>
        <div class="control-actions">
          <button class="action-btn" @click="applyChanges">Crop</button>
          <button class="action-btn secondary" @click="resetControls">
            Reset inputs
          </button>
        </div>
      </div>

      <!-- Resize controls -->
      <div v-if="feature === 'resize'" class="control-group">
        <div class="control-item">
          <label>Current Dimensions:</label>
          <div class="size-display" v-if="imageDimensions" style="color: white;">
            {{ Math.round(imageDimensions.width * 0.264583) }}mm ×
            {{ Math.round(imageDimensions.height * 0.264583) }}mm
            ({{ imageDimensions.width }}px × {{ imageDimensions.height }}px)
          </div>
        </div>
        
        <!-- Dimensions in millimeters -->
        <div class="control-item">
          <label>New Dimensions (mm):</label>
          <div class="size-inputs ">
            <input
              type="number"
              v-model.number="resizeWidthMm"
              @change="calculateHeightFromWidthMm"
              class="size-input"
              min="1"
              step="1"
            />
            <span style="color: rgb(74, 144, 226);">×</span>
            <input
              type="number"
              v-model.number="resizeHeightMm"
              @change="calculateWidthFromHeightMm"
              class="size-input"
              min="1"
              step="1"
            />
          </div>
        </div>
        
        <!-- Dimensions in pixels -->
        <div class="control-item">
          <label>New Dimensions (px):</label>
          <div class="size-inputs">
            <input
              type="number"
              v-model.number="resizeWidth"
              @change="calculateHeightFromWidth"
              class="size-input"
              min="1"
              step="1"
            />
            <span style="color: rgb(74, 144, 226);">×</span>
            <input
              type="number"
              v-model.number="resizeHeight"
              @change="calculateWidthFromHeight"
              class="size-input"
              min="1"
              step="1"
            />
          </div>
        </div>
        
        <!-- Options -->
        <div class="control-item">
          <div class="resize-options">
            <div class="option-item">
              <input
                type="checkbox"
                id="maintainAspectRatio"
                v-model="maintainAspectRatio"
              />
              <label for="maintainAspectRatio">Maintain aspect ratio</label>
            </div>
            <div class="option-item">
              <input
                type="checkbox"
                id="useExactDimensions"
                v-model="useExactDimensions"
              />
              <label for="useExactDimensions">Use exact dimensions</label>
            </div>
            
            <div class="preset-buttons" v-if="useExactDimensions">
              <button class="preset-btn" @click="setComplianceDimensions">Set to 400×514 px</button>
            </div>
          </div>
          
          <div class="aspect-ratio-info" v-if="maintainAspectRatio && !useExactDimensions">
            <i-lucide-lock class="lock-icon" />
            <span>Current aspect ratio maintained. <br> Use 'Crop' tool to change aspect ratio</span>
          </div>
          <div class="resize-tip">
            <i-lucide-info class="info-icon" />
            <span
              >Drag the edges or corners of the image to resize, then click
              Apply</span
            >
          </div>
        </div>
        <div class="control-actions">
          <button class="action-btn primary" @click="applyChanges">
            Resize
          </button>
          <button class="action-btn secondary" @click="resetResizeControls">
            Reset
          </button>
        </div>
      </div>

      <!-- Background removal controls -->
      <div v-if="feature === 'background-remove'" class="control-group">
        <div class="control-item">
          <label>Removal Method</label>
          <div class="method-options">
            <button
              :class="['upload-bg-btn', { active: removalMethod === 'auto' }]"
              @click="setRemovalMethod('auto')"
            >
              Automatic Mode (selected)
            </button>
            <!-- <button
              :class="['control-btn', { active: removalMethod === 'manual' }]"
              @click="setRemovalMethod('manual')"
            >
              Manual Selection
            </button> -->
          </div>
        </div>
        <div class="control-actions">
          <button class="action-btn" @click="applyChanges">Remove Background</button>
        </div>
      </div>

      <!-- Background replacement controls -->
      <div v-if="feature === 'background-replace'" class="control-group">
        <div class="control-item">
          <label>Background Color</label>
          <div class="color-picker">
            <div
              v-for="color in backgroundColors"
              :key="color"
              :class="['color-option', { active: selectedBgColor === color }]"
              :style="{ backgroundColor: color }"
              @click="setBackgroundColor(color)"
            ></div>
            <div class="color-option custom">
              <input
                type="color"
                v-model="customBgColor"
                @change="setBackgroundColor(customBgColor)"
              />
            </div>
          </div>
        </div>
        <div class="control-item">
          <label>Or Upload Image</label>
          <input
            type="file"
            ref="bgImageInput"
            @change="uploadBackgroundImage"
            style="display: none"
            accept="image/*"
          />
          <button class="upload-bg-btn" @click="$refs.bgImageInput.click()">
            <i-lucide-upload class="icon" /> Upload Background
          </button>
          <div v-if="selectedBackgroundImage" class="selected-bg-image">
            <span>Selected: {{ selectedBackgroundImage }}</span>
            <button class="clear-btn" @click="clearBackgroundImage">×</button>
          </div>
        </div>
        <div class="control-actions">
          <button class="action-btn" @click="applyChanges">Apply</button>
        </div>
      </div>

      <!-- Clothes replacement controls -->
      <div v-if="feature === 'clothes'" class="control-group">
      <div class="control-item">
        <label>Select Clothing Template</label>
        <div class="template-options">
          <div 
            class="template-option" 
            :class="{ active: selectedClothingType === 'male' }"
            @click="setClothingType('male')"
          >
            <img src='/assets/MaleSuit.png' alt=''>
            <span style="color: white;">Formal Suit</span>
            <span style="color: white;">(Male)</span>
          </div>
          <div 
            class="template-option" 
            :class="{ active: selectedClothingType === 'female' }"
            @click="setClothingType('female')"
          >
          <img src='/assets/FemaleSuit.png' alt=''>
          <span style="color: white;">Formal Suit</span>
          <span style="color: white;">(Female)</span>
          </div>
        </div>
      </div>
      <div class="control-actions">
        <button class="action-btn" @click="applyChanges">Apply</button>
      </div>
    </div>

      <!-- Face centering controls -->
      <div v-if="feature === 'face'" class="control-group">
        <div class="control-item mt-3">
          <button class="action-btn w-full mb-2" @click="detectFace">
            Detect & Center Face
          </button>

          <div class="detection-options mt-3">
            <h4 class="text-sm font-semibold mb-2" style="color: white;">Advanced Options</h4>
            <div class="option-item">
              <input
                type="checkbox"
                id="useAltDetector"
                v-model="useAltDetector"
              />
              <label for="useAltDetector">Use alternative detector</label>
            </div>
          </div>
        </div>
      </div>

      <!-- Layout controls -->
      <div v-if="feature === 'layout'" class="control-group">
        <div class="control-item">
          <label>Current Image Dimensions:</label>
          <div class="size-display" v-if="imageDimensions" style="color: white;">
            {{ imageDimensions.width }}px × {{ imageDimensions.height }}px
          </div>
        </div>
        <div class="control-item">
          <label>Border Size (mm):</label>
          <div class="size-inputs">
            <input
              type="number"
              v-model.number="borderSize"
              class="size-input"
              min="0"
              max="20"
              @change="calculateLayoutDimensions"
            />
          </div>
        </div>
        <div class="control-item">
          <label>Grid Layout:</label>
          <div class="grid-layout-options">
            <button
              v-for="layout in gridLayouts"
              :key="layout.value"
              :class="[
                'control-btn',
                { active: selectedLayout === layout.value },
              ]"
              @click="setGridLayout(layout.value)"
            >
              {{ layout.label }}
            </button>
            <button
              :class="['control-btn', { active: selectedLayout === 'custom' }]"
              @click="setGridLayout('custom')"
            >
              Custom
            </button>
          </div>
        </div>

        <!-- Custom row/column inputs -->
        <div class="control-item" v-if="selectedLayout === 'custom'">
          <label>Custom Grid:</label>
          <div class="custom-grid-inputs">
            <div class="custom-grid-row">
              <span>Rows:</span>
              <input
                type="number"
                v-model.number="customRows"
                @change="updateCustomLayout"
                class="size-input"
                min="1"
                max="10"
              />
            </div>
            <div class="custom-grid-row">
              <span>Columns:</span>
              <input
                type="number"
                v-model.number="customCols"
                @change="updateCustomLayout"
                class="size-input"
                min="1"
                max="10"
              />
            </div>
          </div>
        </div>

        <div class="layout-preview" v-if="selectedLayout">
          <label>Layout Preview:</label>
          <div class="preview-container">
            <div class="preview-box">
              <div class="preview-grid" :style="gridPreviewStyle">
                <div
                  v-for="cell in totalCells"
                  :key="cell"
                  class="preview-cell"
                ></div>
              </div>
            </div>
            <div class="layout-info">
              <p v-if="borderSize === 0">
                No border will be applied to the images.
              </p>
              <p v-else>
                Original with border: {{ originalWithBorderWidth }}mm ×
                {{ originalWithBorderHeight }}mm
              </p>
              <p style="color: white;">Final layout size: {{ finalWidth }}mm × {{ finalHeight }}mm</p>
            </div>
          </div>
        </div>

        <div class="control-actions">
          <button
            class="action-btn"
            @click="applyChanges"
            :disabled="!selectedLayout"
          >
            Apply
          </button>
        </div>
      </div>

      <!-- Enhancement controls -->
      <div v-if="feature === 'enhance'" class="control-group">
        <div class="control-item slider">
          <label>Brightness</label>
          <input
            type="range"
            min="-100"
            max="100"
            v-model="previewBrightness"
            class="slider-input"
          />
          <span class="slider-value" style="color: rgb(74, 144, 226)">{{ previewBrightness }}</span>
        </div>
        <div class="control-item slider">
          <label>Contrast</label>
          <input
            type="range"
            min="-100"
            max="100"
            v-model="previewContrast"
            class="slider-input"
          />
          <span style="color: rgb(74, 144, 226)" class="slider-value">{{ previewContrast }}</span>
        </div>
        <div class="enhancement-info" v-if="enhancementActive">
          <div class="info-text">
            Current settings: Brightness {{ brightness }}, Contrast
            {{ contrast }}
          </div>
        </div>
        <div class="control-actions">
          <button class="action-btn" @click="applyChanges">Apply</button>
          <button class="action-btn secondary" @click="resetControls">
            Reset
          </button>
        </div>
      </div>

      <!-- Compliance Check controls -->
      <div v-if="feature === 'compliance-check'" class="control-group">
        <div class="control-item">
          <p class="helper-text">Check if your photo meets the standard ID photo requirements.</p>
          
          <div v-if="complianceCheck.loading" class="compliance-loading">
            <div class="spinner"></div>
            <p>Checking compliance...</p>
          </div>
          
          <div v-else-if="complianceCheck.result" class="compliance-results">
            <div class="compliance-status" :class="{ 'compliant': complianceCheck.result.compliant, 'non-compliant': !complianceCheck.result.compliant }">
              <div class="status-icon">
                <span v-if="complianceCheck.result.compliant">✓</span>
                <span v-else>!</span>
              </div>
              <h4>{{ complianceCheck.result.message }}</h4>
            </div>
            
            <div v-if="!complianceCheck.result.compliant && complianceCheck.result.issues && complianceCheck.result.issues.length > 0" class="error-messages">
              <div v-for="(issue, index) in complianceCheck.result.issues" :key="index" class="error-message">
                {{ issue }}
              </div>
            </div>
          </div>
          
          <button class="action-btn primary" @click="checkCompliance" style="margin-top: 20px;">
            Check Again
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import ComplianceService from '../services/compliance-service';

export default {
  props: {
    feature: {
      type: String,
      required: true,
    },
    cropWidth: {
      type: Number,
      default: 35,
    },
    cropHeight: {
      type: Number,
      default: 45,
    },
    imageDimensions: {
      type: Object,
      default: null,
    },
    image: {
      type: String,
      default: null,
    },
  },
  data() {
    return {
      // Local data properties for two-way binding
      localCropWidth: this.cropWidth || 35,
      localCropHeight: this.cropHeight || 45,

      // Crop state
      selectedRatio: "35:45",
      aspectRatios: [
        { label: "35×45", value: "35:45" },
        { label: "50×50", value: "50:50" },
        { label: "25×35", value: "25:35" },
        { label: "Custom", value: "custom" },
      ],

      // Resize state
      resizeWidth: 0,
      resizeHeight: 0,
      resizeWidthMm: 0,
      resizeHeightMm: 0,
      originalAspectRatio: 1,
      maintainAspectRatio: true,
      useExactDimensions: false,

      // Background removal state
      removalMethod: "auto",

      // Background replacement state
      selectedBgColor: "#FFFFFF",
      customBgColor: "#FFFFFF",
      backgroundColors: ["#FFFFFF", "#0000FF", "#FF0000", "#00FF00", "#DDDDDD"],

      selectedBackgroundImage: null,
      backgroundImageFile: null,
      useAltDetector: false,
      previewBrightness: 0,
      previewContrast: 0,
      brightness: 0,
      contrast: 0,
      enhancementActive: false,
      isFirstAdjustment: true,
      borderSize: 5,
      selectedLayout: null,
      gridLayouts: [
        { label: "1×1", value: "1x1" },
        { label: "2×2", value: "2x2" },
        { label: "2×3", value: "2x3" },
        { label: "3×3", value: "3x3" },
        { label: "4×4", value: "4x4" },
        { label: "2×6", value: "2x6" },
        { label: "3×4", value: "3x4" },
      ],
      showLayoutInfo: false,
      finalWidth: 0,
      finalHeight: 0,
      totalCells: 0,
      gridPreviewStyle: {},
      customRows: 2,
      customCols: 2,
      originalWithBorderWidth: 0,
      originalWithBorderHeight: 0,

      // clothes
      selectedClothingType: "formal",

      complianceCheck: {
        loading: false,
        result: null
      },
    };
  },
  watch: {
    cropWidth: {
      immediate: true,
      handler(val) {
        this.localCropWidth = val || 35;
      },
    },
    cropHeight: {
      immediate: true,
      handler(val) {
        this.localCropHeight = val || 45;
      },
    },
    imageDimensions: {
      immediate: true,
      handler(val) {
        if (val) {
          this.resizeWidth = val.width;
          this.resizeHeight = val.height;
          this.resizeWidthMm = Math.round(val.width * 0.264583);
          this.resizeHeightMm = Math.round(val.height * 0.264583);
          this.originalAspectRatio = val.width / val.height;
        }
      },
    },
    // Update resize dimensions when imageDimensions changes
    "imageDimensions.width": function (newWidth) {
      if (this.feature === "resize" && newWidth) {
        this.resizeWidth = newWidth;
        this.originalAspectRatio =
          this.imageDimensions.width / this.imageDimensions.height;
      }
    },
    "imageDimensions.height": function (newHeight) {
      if (this.feature === "resize" && newHeight) {
        this.resizeHeight = newHeight;
      }
    },
    // Watch for changes in preview values and emit preview event
    previewBrightness(val) {
      this.updateEnhancementPreview();
    },
    previewContrast(val) {
      this.updateEnhancementPreview();
    },
    // Watch for feature change to initialize enhancement values
    feature: {
      immediate: true,
      handler(val) {
        if (val === "enhance") {
          this.initializeEnhancement();
        }
      },
    },
  },
  methods: {
    applyDimensions() {
      const width = Math.max(0.1, parseFloat(this.localCropWidth) || 35);
      const height = Math.max(0.1, parseFloat(this.localCropHeight) || 45);

      this.localCropWidth = parseFloat(width.toFixed(1)); // Keep one decimal place
      this.localCropHeight = parseFloat(height.toFixed(1)); // Keep one decimal place

      this.$emit("update:cropWidth", this.localCropWidth);
      this.$emit("update:cropHeight", this.localCropHeight);

      this.$emit("apply-dimensions", {
        width: this.localCropWidth,
        height: this.localCropHeight,
      });
    },
    getFeatureTitle(feature) {
      const titles = {
        crop: "Crop",
        resize: "Resize",
        "background-remove": "Background Removal",
        "background-replace": "Background Replacement",
        clothes: "Clothes Replacement",
        face: "Face Centering",
        enhance: "Enhance",
        layout: "Layout",
        "compliance-check": "Compliance Check",
        "ai-optimize": "AI Optimization"
      };
      return titles[feature] || "Edit Photo";
    },
    setAspectRatio(ratio) {
      this.selectedRatio = ratio;
      if (ratio !== "custom") {
        const [width, height] = ratio.split(":");
        const parsedWidth = parseFloat(width);
        const parsedHeight = parseFloat(height);

        this.localCropWidth = parsedWidth;
        this.localCropHeight = parsedHeight;

        this.$emit("update:cropWidth", parsedWidth);
        this.$emit("update:cropHeight", parsedHeight);
      }
    },
    setRemovalMethod(method) {
      this.removalMethod = method;
    },
    setBackgroundColor(color) {
      this.selectedBgColor = color;
      // When selecting a color, clear any selected image
      this.selectedBackgroundImage = null;
    },
    detectFace() {
      // Emit event to apply face detection changes
      this.$emit("faceDetectionChange", {
        show: true,
        options: {
          useAltDetector: this.useAltDetector,
        },
      });
      console.log("Face detection requested with options:", {
        useAltDetector: this.useAltDetector,
      });
    },
    setClothingType(type) {
      this.selectedClothingType = type;
    },
    resetControls() {
      if (this.feature === "crop") {
        this.selectedRatio = "35:45";
        this.localCropWidth = 35;
        this.localCropHeight = 45;

        this.applyDimensions();
      } else if (this.feature === "background-remove") {
        this.removalMethod = "auto";
      } else if (this.feature === "background-replace") {
        this.selectedBgColor = "#FFFFFF";
        this.customBgColor = "#FFFFFF";
        this.selectedBackgroundImage = null;
        this.backgroundImageFile = null;
      } else if (this.feature === "face") {
        this.useAltDetector = false;
      } else if (this.feature === "enhance") {
        // Only reset the values that have been changed
        if (this.previewBrightness !== 0) {
          this.previewBrightness = 0;
          this.brightness = 0;
        }
        if (this.previewContrast !== 0) {
          this.previewContrast = 0;
          this.contrast = 0;
        }

        // When resetting, send a reset request to clear any enhancements
        this.$emit("enhancement-preview", {
          brightness: this.previewBrightness,
          contrast: this.previewContrast,
          firstAdjustment: true,
          previewInProgress: false,
        });
      } else if (this.feature === "layout") {
        this.borderSize = 5;
        this.selectedLayout = null;
        this.customRows = 2;
        this.customCols = 2;
        this.resetLayoutCalculations();
      }
    },
    applyChanges() {
      console.log("Applying changes for feature:", this.feature);

      let changes = {};

      if (this.feature === "crop") {
        changes = {
          type: "crop",
          width: this.localCropWidth,
          height: this.localCropHeight,
          ratio: this.selectedRatio,
          unit: "mm",
        };
      } else if (this.feature === "resize") {
        changes = {
          type: "resize",
          width: this.resizeWidth,
          height: this.resizeHeight,
          maintainAspectRatio: !this.useExactDimensions && this.maintainAspectRatio,
          widthProvided: true,
          heightProvided: true,
          useExactDimensions: this.useExactDimensions
        };

        console.log(
          "Sending resize request with dimensions:",
          this.resizeWidth,
          "x",
          this.resizeHeight,
          "useExactDimensions:",
          this.useExactDimensions
        );
      } else if (this.feature === "clothes") {
        changes = {
          type: "clothes",
          clothingType: this.selectedClothingType,
        };
      } else if (this.feature === "background-remove") {
        changes = {
          type: "background-remove",
          method: this.removalMethod,
        };
      } else if (this.feature === "background-replace") {
        console.log("Handling background replace with:", {
          selectedBgColor: this.selectedBgColor,
          hasBackgroundFile: !!this.backgroundImageFile,
          backgroundFileName: this.backgroundImageFile
            ? this.backgroundImageFile.name
            : "none",
        });

        if (this.selectedBgColor) {
          // Color replacement
          changes = {
            type: "background-replace",
            color: this.selectedBgColor,
          };
        } else if (this.backgroundImageFile) {
          // No need to handle the file upload here, as it's now handled in uploadBackgroundImage method
          console.log(
            "Background image already handled in uploadBackgroundImage"
          );
          return;
        } else {
          console.error("No background color or image selected");
          return;
        }
      } else if (this.feature === "face") {
        this.$emit("faceDetectionChange", {
          show: true,
          options: {
            useAltDetector: this.useAltDetector,
          },
        });
        return;
      } else if (this.feature === "enhance") {
        changes = {
          type: "enhance",
          brightness: this.previewBrightness,
          contrast: this.previewContrast,
          firstAdjustment: false,
          previewInProgress: false,
        };
        this.brightness = this.previewBrightness;
        this.contrast = this.previewContrast;
        this.enhancementActive = true;

        // Reset the first adjustment flag so next time enhancement is selected, it starts fresh
        this.isFirstAdjustment = true;
      } else if (this.feature === "layout") {
        // Get rows and columns based on selected layout
        let rows, cols;

        if (this.selectedLayout === "custom") {
          rows = this.customRows;
          cols = this.customCols;
        } else {
          [cols, rows] = this.selectedLayout.split("x").map(Number);
        }

        changes = {
          type: "layout",
          borderSize: this.borderSize,
          rows: rows,
          cols: cols,
          originalWidth: this.originalWithBorderWidth,
          originalHeight: this.originalWithBorderHeight,
          finalWidth: this.finalWidth,
          finalHeight: this.finalHeight,
          useOriginalImage: true, // Explicitly tell backend to use original image
        };

        // Reset selections after applying to prevent recursive layouts
        setTimeout(() => {
          this.resetControls();
        }, 500);
      }

      console.log("Emitting apply-changes with:", changes);
      this.$emit("apply-changes", changes);
    },
    updateWidth(event) {
      const value = parseFloat(event.target.value) || 1;
      this.localCropWidth = value;
    },
    updateHeight(event) {
      const value = parseFloat(event.target.value) || 1;
      this.localCropHeight = value;
    },
    applyWidth() {
      const value = Math.max(0.1, this.localCropWidth);
      this.localCropWidth = parseFloat(value.toFixed(1)); // Keep one decimal place
      this.$emit("update:cropWidth", this.localCropWidth);
    },
    applyHeight() {
      const value = Math.max(0.1, this.localCropHeight);
      this.localCropHeight = parseFloat(value.toFixed(1)); // Keep one decimal place
      this.$emit("update:cropHeight", this.localCropHeight);
    },
    calculateHeightFromWidthMm() {
      if (this.originalAspectRatio && this.resizeWidthMm > 0) {
        this.resizeHeightMm = Math.round(
          this.resizeWidthMm / this.originalAspectRatio
        );
        // Convert back to pixels for the actual resize operation
        this.resizeWidth = Math.round(this.resizeWidthMm / 0.264583);
        this.resizeHeight = Math.round(this.resizeHeightMm / 0.264583);
      }
    },

    calculateWidthFromHeightMm() {
      if (this.originalAspectRatio && this.resizeHeightMm > 0) {
        this.resizeWidthMm = Math.round(
          this.resizeHeightMm * this.originalAspectRatio
        );
        // Convert back to pixels for the actual resize operation
        this.resizeWidth = Math.round(this.resizeWidthMm / 0.264583);
        this.resizeHeight = Math.round(this.resizeHeightMm / 0.264583);
      }
    },
    
    calculateHeightFromWidth() {
      if (this.maintainAspectRatio && this.originalAspectRatio && this.resizeWidth > 0) {
        this.resizeHeight = Math.round(this.resizeWidth / this.originalAspectRatio);
        // Update mm values
        this.resizeWidthMm = Math.round(this.resizeWidth * 0.264583);
        this.resizeHeightMm = Math.round(this.resizeHeight * 0.264583);
      }
    },
    
    calculateWidthFromHeight() {
      if (this.maintainAspectRatio && this.originalAspectRatio && this.resizeHeight > 0) {
        this.resizeWidth = Math.round(this.resizeHeight * this.originalAspectRatio);
        // Update mm values
        this.resizeWidthMm = Math.round(this.resizeWidth * 0.264583);
        this.resizeHeightMm = Math.round(this.resizeHeight * 0.264583);
      }
    },
    
    setComplianceDimensions() {
      // Set to standard compliance dimensions (400x514 pixels)
      this.resizeWidth = 400;
      this.resizeHeight = 514;
      // Update mm values
      this.resizeWidthMm = Math.round(this.resizeWidth * 0.264583);
      this.resizeHeightMm = Math.round(this.resizeHeight * 0.264583);
    },

    resetResizeControls() {
      if (this.imageDimensions) {
        this.resizeWidth = this.imageDimensions.width;
        this.resizeHeight = this.imageDimensions.height;
        this.resizeWidthMm = Math.round(this.imageDimensions.width * 0.264583);
        this.resizeHeightMm = Math.round(
          this.imageDimensions.height * 0.264583
        );
        this.maintainAspectRatio = true;
        this.useExactDimensions = false;
        
        // Emit a custom event to reset the resize mask in the image component
        this.$emit("reset-resize-mask");
      }
    },
    async uploadBackgroundImage(event) {
      console.log("Upload background image triggered", event);

      const file = event.target.files[0];
      if (file) {
        console.log(
          "File selected:",
          file.name,
          "size:",
          file.size,
          "type:",
          file.type
        );

        // Store the file for later upload during apply
        this.backgroundImageFile = file;

        // For preview purposes
        const reader = new FileReader();
        reader.onload = (e) => {
          this.selectedBackgroundImage = file.name;
          console.log("File read successful, preview updated");
        };
        reader.readAsDataURL(file);

        // Clear any selected color
        this.selectedBgColor = null;

        // Log successful file selection for debugging
        console.log("Background image selected:", file.name);

        // Directly emit the upload-background-image event instead of calling applyChanges
        this.$emit("upload-background-image", file);
      } else {
        console.error("No file selected in the upload event");
      }
    },
    clearBackgroundImage() {
      console.log("Clearing background image selection");
      this.selectedBackgroundImage = null;
      this.backgroundImageFile = null;
    },
    closePanel() {
      this.$emit("close");
    },
    // Initialize enhancement values when the feature is selected
    initializeEnhancement() {
      // If enhancement is active, set the preview values to the current values
      if (this.enhancementActive) {
        this.previewBrightness = this.brightness;
        this.previewContrast = this.contrast;
      } else {
        // Otherwise, set all to 0
        this.previewBrightness = 0;
        this.previewContrast = 0;
        this.brightness = 0;
        this.contrast = 0;
      }

      // Reset first adjustment tracking when feature is selected
      this.isFirstAdjustment = true;
    },

    // Update the enhancement preview in real-time
    updateEnhancementPreview() {
      if (this.feature === "enhance") {
        this.$emit("enhancement-preview", {
          brightness: this.previewBrightness,
          contrast: this.previewContrast,
          firstAdjustment: this.isFirstAdjustment,
          previewInProgress: true,
        });

        // After first adjustment, set flag to false
        if (this.isFirstAdjustment) {
          this.isFirstAdjustment = false;
        }
      }
    },
    async setGridLayout(layout) {
      // Clear any previous selection to avoid interference
      this.selectedLayout = null;
      // Reset calculation state
      this.resetLayoutCalculations();

      // Set the new layout
      this.selectedLayout = layout;

      if (layout === "custom") {
        // Initialize with default values
        this.customRows = 2;
        this.customCols = 2;
      } else if (layout) {
        // Parse rows and columns from layout string (e.g., "2x3")
        const [cols, rows] = layout.split("x").map(Number);
        this.customRows = rows;
        this.customCols = cols;
      }

      // Calculate dimensions asynchronously
      await this.calculateLayoutDimensions();
    },
    resetLayoutCalculations() {
      // Reset any previously calculated dimensions to ensure we start fresh
      this.originalWithBorderWidth = 0;
      this.originalWithBorderHeight = 0;
      this.finalWidth = 0;
      this.finalHeight = 0;
      this.showLayoutInfo = false;
    },
    async calculateLayoutDimensions() {
      if (!this.imageDimensions || !this.selectedLayout) return;

      try {
        // Fetch fresh image dimensions directly from the server to ensure we have the original size
        const response = await fetch(
          `http://localhost:8080/api/image/dimensions?original=true&t=${Date.now()}`
        );

        let originalWidthMm, originalHeightMm;

        if (response.ok) {
          const originalDimensions = await response.json();
          console.log(
            "Original image dimensions for layout:",
            originalDimensions
          );

          // Calculate original dimensions in mm (using 0.264583 as px to mm conversion)
          const pxToMm = 0.264583;
          originalWidthMm = Math.round(originalDimensions.width * pxToMm);
          originalHeightMm = Math.round(originalDimensions.height * pxToMm);
        } else {
          // Fallback to current dimensions if fetch fails
          console.error(
            "Failed to fetch original dimensions, using current dimensions"
          );
          const pxToMm = 0.264583;
          originalWidthMm = Math.round(this.imageDimensions.width * pxToMm);
          originalHeightMm = Math.round(this.imageDimensions.height * pxToMm);
        }

        // Calculate dimensions with border
        this.originalWithBorderWidth = originalWidthMm + this.borderSize * 2;
        this.originalWithBorderHeight = originalHeightMm + this.borderSize * 2;

        // Get rows and columns based on selected layout
        let rows, cols;

        if (this.selectedLayout === "custom") {
          rows = this.customRows;
          cols = this.customCols;
        } else {
          [cols, rows] = this.selectedLayout.split("x").map(Number);
        }

        // Calculate final dimensions
        this.finalWidth = this.originalWithBorderWidth * cols;
        this.finalHeight = this.originalWithBorderHeight * rows;

        // Calculate total cells for preview
        this.totalCells = rows * cols;

        // Update grid preview style
        this.gridPreviewStyle = {
          display: "grid",
          gridTemplateColumns: `repeat(${cols}, 1fr)`,
          gridTemplateRows: `repeat(${rows}, 1fr)`,
          gap: "2px",
          width: "100%",
          height: "100%",
        };

        this.showLayoutInfo = true;
      } catch (error) {
        console.error("Error calculating layout dimensions:", error);
      }
    },
    async updateCustomLayout() {
      // Ensure values are valid
      this.customRows = Math.max(1, Math.min(10, this.customRows));
      this.customCols = Math.max(1, Math.min(10, this.customCols));

      // Reset and recalculate
      this.resetLayoutCalculations();
      await this.calculateLayoutDimensions();
    },
    async checkCompliance() {
      console.log("Starting compliance check");
      this.complianceCheck.loading = true;
      this.complianceCheck.result = null;
      
      try {
        // Check if image is available
        if (!this.image) {
          // If no image prop, emit an event to request the current image
          console.log("No image available, requesting from parent component");
          this.$emit('request-image-for-compliance');
          
          // Add a delay to allow the parent component to provide the image
          await new Promise(resolve => setTimeout(resolve, 500));
          
          // If still no image, show error
          if (!this.image) {
            console.error("No image is currently loaded");
            this.complianceCheck.result = {
              compliant: false,
              issues: ['No image is currently loaded. Please load an image first.'],
              message: 'No image to check'
            };
            this.complianceCheck.loading = false;
            return;
          }
        }
        
        console.log("Processing image for compliance check:", 
          typeof this.image === 'string' 
            ? `String (${this.image.substring(0, 30)}...)` 
            : `Blob (${this.image instanceof Blob ? this.image.size + ' bytes' : 'not a blob'})`
        );
        
        // Process the image (handles both blob URLs and base64 strings)
        const imageBlob = await ComplianceService.processImageForCompliance(this.image);
        
        if (!imageBlob) {
          console.error("Failed to process image");
          this.complianceCheck.result = {
            compliant: false,
            issues: ['Could not process image data. The image may be invalid or corrupted.'],
            message: 'Image processing error'
          };
          this.complianceCheck.loading = false;
          return;
        }
        
        console.log("Image processed successfully, size:", imageBlob.size, "bytes, type:", imageBlob.type);
        
        // Get background status from Vue store or local state
        // If selectedBgColor or backgroundImageFile exists, background was replaced
        const hasReplacedBackground = !!this.selectedBgColor || !!this.backgroundImageFile;
        
        // Send to compliance service with background information
        const result = await ComplianceService.checkImageCompliance(imageBlob, {
          hasReplacedBackground,
          backgroundColor: this.selectedBgColor
        });
        
        console.log("Received compliance result:", result);
        
        // Update the result
        this.complianceCheck.result = result;
      } catch (error) {
        console.error('Error during compliance check:', error);
        this.complianceCheck.result = {
          compliant: false,
          issues: [(error.message || 'An unexpected error occurred during compliance check')],
          message: 'Compliance check failed'
        };
      } finally {
        console.log("Compliance check complete");
        this.complianceCheck.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.control-panel {
  width: 280px;
  background-color: #2a2e32;
  border-left: 1px solid #3a3f45;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-left: 0;
}

.panel-header {
  padding: 15px 15px 15px 10px;
  border-bottom: 1px solid #3a3f45;
  display: flex;
  align-items: center;
}

.close-btn {
  background: transparent;
  border: none;
  color: #cccccc;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  margin-right: 10px;
  flex-shrink: 0;
}

.close-btn:hover {
  background-color: #3a3f45;
  color: #ffffff;
}

.panel-header h3 {
  color: rgb(74, 144, 226);
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 15px 15px 15px 0;
}

.control-group {
  display: flex;
  flex-direction: column;
  gap: 15px;
  padding-left: 15px;
}

.control-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.control-item label {
  font-size: 14px;
  color: #cccccc;
}

.ratio-options,
.method-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.control-btn {
  background-color: #3a3f45;
  border: none;
  color: white;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}

.control-btn:hover {
  background-color: #4a4f55;
}

.control-btn.active {
  background-color: #4a90e2;
}

.size-inputs {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.size-input {
  width: 60px;
  padding: 6px;
  background-color: #3a3f45;
  border: 1px solid #4a4f55;
  border-radius: 4px;
  color: white;
  font-size: 14px;
}

.color-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.color-option {
  width: 30px;
  height: 30px;
  border-radius: 4px;
  cursor: pointer;
  border: 2px solid transparent;
}

.color-option.active {
  border-color: #4a90e2;
}

.color-option.custom {
  background: linear-gradient(45deg, #ff0000, #00ff00, #0000ff);
  overflow: hidden;
  position: relative;
}

.color-option.custom input {
  position: absolute;
  width: 150%;
  height: 150%;
  top: -25%;
  left: -25%;
  cursor: pointer;
  opacity: 0;
}

.control-item.slider {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  gap: 8px;
}

.control-item.slider label {
  grid-column: 1;
  grid-row: 1;
}

.control-item.slider .slider-value {
  grid-column: 2;
  grid-row: 1;
  text-align: right;
  font-size: 14px;
}

.slider-input {
  grid-column: 1 / span 2;
  grid-row: 2;
  -webkit-appearance: none;
  width: 100%;
  height: 6px;
  background: #3a3f45;
  border-radius: 3px;
  outline: none;
}

.slider-input::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #4a90e2;
  cursor: pointer;
}

.template-options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.template-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  padding: 5px;
  border-radius: 4px;
  border: 2px solid transparent;
}

.template-option:hover {
  background-color: #3a3f45;
}

.template-option img {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
}

.template-option span {
  color: rgb(74, 144, 226);
  font-size: 12px;
  text-align: center;
}

.upload-bg-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background-color: #3a3f45;
  border: none;
  color: white;
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
  width: 100%;
}

.upload-bg-btn:hover {
  background-color: #4a4f55;
}

.control-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.action-btn {
  flex: 1;
  background-color: #4a90e2;
  border: none;
  color: white;
  padding: 8px 0;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
}

.action-btn:hover {
  background-color: #3a80d2;
}

.action-btn.secondary {
  background-color: #3a3f45;
}

.action-btn.secondary:hover {
  background-color: #4a4f55;
}

.icon {
  width: 18px;
  height: 18px;
}

.apply-dimensions-btn {
  margin-top: 8px;
  width: 100%;
  background-color: #3a80d2;
}

.apply-dimensions-btn:hover {
  background-color: #2970c2;
}

.size-input.disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background-color: #2a2e32;
}

.aspect-ratio-info {
  display: flex;
  align-items: center;
  margin-top: 8px;
  user-select: none;
  color: #88aaff;
  font-size: 13px;
}

.aspect-ratio-info .lock-icon {
  width: 16px;
  height: 16px;
  margin-right: 6px;
}

.size-display {
  background-color: #3a3f45;
  padding: 6px 10px;
  border-radius: 4px;
  margin-top: 5px;
  text-align: center;
  font-family: monospace;
}

.selected-bg-image {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background-color: #3a3f45;
  border-radius: 4px;
  margin-top: 8px;
}

.clear-btn {
  background: transparent;
  border: none;
  color: #cccccc;
  cursor: pointer;
}

.clear-btn:hover {
  color: #ffffff;
}

.helper-text {
  font-size: 12px;
  color: #88aaff;
}

.secondary-button:hover {
  background-color: #f94f4f4f;
}

.cancel-button {
  background-color: #383a3e;
  color: #cf3c3c;
  border: 1px solid #cf3c3c;
  border-radius: 4px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s ease;
  width: 100%;
  text-align: center;
}

.cancel-button:hover {
  background-color: #4a3434;
}

.w-full {
  width: 100%;
}

.enhancement-info {
  margin-top: 10px;
  padding: 10px;
  background-color: #3a3f45;
  border-radius: 4px;
}

.info-text {
  font-size: 12px;
  color: #88aaff;
}

.grid-layout-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.layout-preview {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 5px;
}

.preview-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-box {
  width: 200px;
  height: 200px;
  border: 1px solid #4a4f55;
  border-radius: 4px;
  position: relative;
  margin: 0 auto;
  background-color: #1e2124;
  overflow: hidden;
}

.preview-grid {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.preview-cell {
  background-color: #4a90e2;
  border: 2px solid #ffffff;
  opacity: 0.7;
}

.layout-info {
  margin-top: 10px;
  padding: 10px;
  background-color: #3a3f45;
  border-radius: 4px;
  font-size: 12px;
}

.layout-info p {
  margin: 5px 0;
}

.custom-grid-inputs {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 5px;
}

.custom-grid-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.custom-grid-row span {
  font-size: 14px;
  color: #cccccc;
}

.resize-tip {
  display: flex;
  align-items: center;
  font-size: 0.85rem;
  color: #666;
  margin-top: 8px;
}

.info-icon {
  width: 16px;
  height: 16px;
  margin-right: 6px;
  color: #007bff;
}

.primary {
  background-color: #007bff;
  color: white;
  font-weight: bold;
}

.primary:hover {
  background-color: #0069d9;
}

.template-option.active {
  border: 2px solid #4a90e2;
  background-color: rgba(74, 144, 226, 0.2);
}

.compliance-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
  text-align: center;
}

.spinner {
  border: 3px solid #f3f3f3;
  border-top: 3px solid #3498db;
  border-radius: 50%;
  width: 30px;
  height: 30px;
  animation: spin 1s linear infinite;
  margin-bottom: 15px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.compliance-results {
  margin-top: 15px;
  padding: 15px;
  background-color: #3a3f45;
  border-radius: 6px;
}

.compliance-status {
  display: flex;
  align-items: center;
  padding: 15px;
  border-radius: 6px;
  margin: 20px 0;
}

.compliant {
  background-color: rgba(46, 204, 113, 0.1);
  border: 1px solid rgba(46, 204, 113, 0.3);
}

.non-compliant {
  background-color: rgba(231, 76, 60, 0.1);
  border: 1px solid rgba(231, 76, 60, 0.3);
}

.status-icon {
  margin-right: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  font-size: 18px;
  font-weight: bold;
}

.compliant .status-icon {
  background-color: rgba(46, 204, 113, 0.2);
  color: #27ae60;
}

.non-compliant .status-icon {
  background-color: rgba(231, 76, 60, 0.2);
  color: #e74c3c;
}

.compliance-status h4 {
  margin: 0;
  font-size: 16px;
}

.compliant h4 {
  color: #27ae60;
}

.non-compliant h4 {
  color: #e74c3c;
}

.error-messages {
  margin-top: 15px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.error-message {
  padding: 12px 16px;
  background-color: rgba(231, 76, 60, 0.1);
  border: 1px solid rgba(231, 76, 60, 0.3);
  border-radius: 4px;
  color: #e74c3c;
  font-size: 15px;
}

.resize-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-item label {
  font-size: 13px;
  cursor: pointer;
}

.preset-buttons {
  margin-top: 8px;
}

.preset-btn {
  background-color: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  width: 100%;
}

.preset-btn:hover {
  background-color: #3a80d2;
}
</style>
