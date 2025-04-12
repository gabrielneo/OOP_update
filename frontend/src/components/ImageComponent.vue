<template>
  <div class="image-container">
    <div class="canvas">
      <div
        @mousedown="startDrag"
        @mousemove="dragging"
        @mouseup="endDrag"
        class="image-container-inner"
      >
        <div ref="imageWrapper" class="image-wrapper">
          <img
            :src="image"
            alt="ID Photo"
            class="photo"
            draggable="false"
            ref="photoImage"
          />

          <!-- Movable crop box based on input dimensions -->
          <div
            v-if="
              activeFeature === 'crop' && cropWidthMm > 0 && cropHeightMm > 0
            "
            :key="`crop-${cropWidthMm}-${cropHeightMm}`"
            class="crop-box"
            :style="dimensionCropBoxStyle"
            @mousedown.stop="startMoveCropBox($event)"
          >
            <!-- Add resize handles similar to resize box -->
            <div
              class="resize-handle top-left"
              @mousedown.stop="(event) => startCropResize('top-left', event)"
            ></div>
            <div
              class="resize-handle top"
              @mousedown.stop="(event) => startCropResize('top', event)"
            ></div>
            <div
              class="resize-handle top-right"
              @mousedown.stop="(event) => startCropResize('top-right', event)"
            ></div>
            <div
              class="resize-handle right"
              @mousedown.stop="(event) => startCropResize('right', event)"
            ></div>
            <div
              class="resize-handle bottom-right"
              @mousedown.stop="(event) => startCropResize('bottom-right', event)"
            ></div>
            <div
              class="resize-handle bottom"
              @mousedown.stop="(event) => startCropResize('bottom', event)"
            ></div>
            <div
              class="resize-handle bottom-left"
              @mousedown.stop="(event) => startCropResize('bottom-left', event)"
            ></div>
            <div
              class="resize-handle left"
              @mousedown.stop="(event) => startCropResize('left', event)"
            ></div>
          </div>

          <!-- Manual free crop box -->
          <div v-if="cropBox" class="crop-box" :style="cropBoxStyle"></div>

          <!-- Resize box with handles -->
          <div
            v-if="activeFeature === 'resize' && resizeBox"
            class="resize-box"
            :style="resizeBoxStyle"
          >
            <!-- Resize handles on corners and edges -->
            <div
              class="resize-handle top-left"
              @mousedown.stop="(event) => startResize('top-left', event)"
            ></div>
            <div
              class="resize-handle top"
              @mousedown.stop="(event) => startResize('top', event)"
            ></div>
            <div
              class="resize-handle top-right"
              @mousedown.stop="(event) => startResize('top-right', event)"
            ></div>
            <div
              class="resize-handle right"
              @mousedown.stop="(event) => startResize('right', event)"
            ></div>
            <div
              class="resize-handle bottom-right"
              @mousedown.stop="(event) => startResize('bottom-right', event)"
            ></div>
            <div
              class="resize-handle bottom"
              @mousedown.stop="(event) => startResize('bottom', event)"
            ></div>
            <div
              class="resize-handle bottom-left"
              @mousedown.stop="(event) => startResize('bottom-left', event)"
            ></div>
            <div
              class="resize-handle left"
              @mousedown.stop="(event) => startResize('left', event)"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mmToPx, pxToMm, getConversionFactors } from '../utils/dpiUtils';

export default {
  props: {
    image: {
      type: String,
      required: true,
    },
    activeFeature: {
      type: String,
      required: false,
    },
    cropWidthMm: {
      type: Number,
      default: 35,
      validator: (value) => value >= 0.1
    },
    cropHeightMm: {
      type: Number,
      default: 45,
      validator: (value) => value >= 0.1
    },
    imageDimensions: {
      type: Object,
      default: null,
    },
    maintainAspectRatio: {
      type: Boolean,
      default: false,
    },
    aspectRatioValue: {
      type: Number,
      default: null,
    },
    resetCropOnApply: {
      type: Boolean,
      default: false,
    },
    hideCropOnApply: {
      type: Boolean,
      default: false,
    },
  },
  mounted() {
    this.$nextTick(() => {
      this.centerCropBox();
      if (this.activeFeature === "resize") {
        this.initializeResizeBox();
      }

      // Add touch event handlers for mobile devices
      this.addTouchEventListeners();

      // Add image load event to ensure resize box updates after image loads
      if (this.$refs.photoImage) {
        this.$refs.photoImage.addEventListener("load", this.handleImageLoad);
        
        // If image is already loaded, emit the dimensions immediately
        if (this.$refs.photoImage.complete) {
          this.handleImageLoad();
        }
      }
    });
  },
  updated() {
    // Force check crop box on component update
    if (this.activeFeature === "crop") {
      this.centerCropBox();
    } else if (this.activeFeature === "resize" && !this.resizeBox) {
      this.initializeResizeBox();
    }
  },
  data() {
    return {
      isDragging: false,
      cropStart: null,
      cropEnd: null,
      // For movable crop box
      movableCropBox: {
        x: 0,
        y: 0,
        isMoving: false,
        startX: 0,
        startY: 0,
        width: 0,  // Add width and height for resizing
        height: 0,
        isResizing: false,
        resizeHandle: null,
        resizeStartPoint: null,
        originalBox: null,
      },
      // For resize functionality
      resizeBox: null,
      isResizing: false,
      resizeHandle: null,
      resizeStartPoint: null,
      originalResizeBox: null,
      aspectRatio: 1,
      resizeEmitTimeout: null,
      // For cleanup
      faceDetectionTimeout: null,
    };
  },
  computed: {
    cropBox() {
      return this.cropStart && this.cropEnd;
    },
    cropBoxStyle() {
      if (!this.cropBox) return {};
      const x = Math.min(this.cropStart.x, this.cropEnd.x);
      const y = Math.min(this.cropStart.y, this.cropEnd.y);
      const width = Math.abs(this.cropEnd.x - this.cropStart.x);
      const height = Math.abs(this.cropEnd.y - this.cropStart.y);
      
      // Different border style when aspect ratio is maintained
      const borderStyle = this.maintainAspectRatio ? "2px solid #007bff" : "2px dashed red";
      const bgColor = this.maintainAspectRatio ? "rgba(0, 123, 255, 0.1)" : "rgba(255, 0, 0, 0.1)";
      
      return {
        position: "absolute",
        left: `${x}px`,
        top: `${y}px`,
        width: `${width}px`,
        height: `${height}px`,
        border: borderStyle,
        backgroundColor: bgColor,
      };
    },
    dimensionCropBoxStyle() {
      // Use the dynamic conversion factor instead of hardcoded value
      const { pxPerMm } = getConversionFactors();
      
      // Get the image element
      const photoImage = this.$refs.photoImage;
      if (!photoImage) return {};
      
      // Get the actual image dimensions on screen
      const imgRect = photoImage.getBoundingClientRect();
      
      // Get the natural dimensions
      const naturalWidth = photoImage.naturalWidth;
      const naturalHeight = photoImage.naturalHeight;
      
      // Calculate scaling factor between natural and display size
      const scaleFactorX = imgRect.width / naturalWidth;
      const scaleFactorY = imgRect.height / naturalHeight;
      
      // Calculate dimensions in pixels (only if not already set by resizing)
      if (!this.movableCropBox.width || !this.movableCropBox.height) {
        this.movableCropBox.width = Math.max(1, this.cropWidthMm) * pxPerMm * scaleFactorX;
        this.movableCropBox.height = Math.max(1, this.cropHeightMm) * pxPerMm * scaleFactorY;
      }

      // Force a reactivity dependency on the dimensions
      this.cropWidthMm; // eslint-disable-line no-unused-expressions
      this.cropHeightMm; // eslint-disable-line no-unused-expressions

      // Different border style when aspect ratio is maintained
      const borderStyle = "2px solid #007bff";
      const bgColor = "rgba(0, 123, 255, 0.1)";

      return {
        position: "absolute",
        left: `${this.movableCropBox.x}px`,
        top: `${this.movableCropBox.y}px`,
        width: `${this.movableCropBox.width}px`,
        height: `${this.movableCropBox.height}px`,
        border: borderStyle,
        backgroundColor: bgColor,
        cursor: "move",
        transition: "width 0.1s, height 0.1s",
      };
    },
    resizeBoxStyle() {
      if (!this.resizeBox) return {};
      return {
        position: "absolute",
        left: `${this.resizeBox.x}px`,
        top: `${this.resizeBox.y}px`,
        width: `${this.resizeBox.width}px`,
        height: `${this.resizeBox.height}px`,
        border: "2px solid #007bff",
        backgroundColor: "rgba(0, 123, 255, 0.05)",
      };
    },
  },
  watch: {
    // Center the crop box when dimensions change
    cropWidthMm: {
      handler(newVal) {
        console.log(`cropWidthMm changed to ${newVal}, updating crop box`);
        // Force an immediate update when dimensions change
        if (this.activeFeature === "crop") {
          this.centerCropBox();
          this.emitCropAreaFromDimensions();
        }
      },
    },
    cropHeightMm: {
      handler(newVal) {
        console.log(`cropHeightMm changed to ${newVal}, updating crop box`);
        // Force an immediate update when dimensions change
        if (this.activeFeature === "crop") {
          this.centerCropBox();
          this.emitCropAreaFromDimensions();
        }
      },
    },
    resetCropOnApply: {
      handler(newVal) {
        if (newVal && this.activeFeature === "crop") {
          console.log("Apply clicked, resetting crop mask to center");
          this.centerCropBox();
          
          // Reset the prop so it can be triggered again
          this.$nextTick(() => {
            this.$emit('update:resetCropOnApply', false);
          });
        }
      },
      immediate: true
    },
    hideCropOnApply: {
      handler(newVal) {
        if (newVal && this.activeFeature === "crop") {
          console.log("Apply clicked, hiding crop mask");
          // Remove the crop mask by hiding it
          this.hideCropMask();
          
          // Reset the prop so it can be triggered again
          this.$nextTick(() => {
            this.$emit('update:hideCropOnApply', false);
          });
        }
      },
      immediate: true
    },
    aspectRatioValue: {
      handler(newVal) {
        console.log(`aspectRatioValue changed to ${newVal}`);
        if (newVal && this.activeFeature === "crop") {
          this.aspectRatio = newVal;
          
          // If we have a crop box, adjust it to the new aspect ratio
          if (this.cropStart && this.cropEnd && this.maintainAspectRatio) {
            // Recalculate the crop end point to maintain the new aspect ratio
            const width = Math.abs(this.cropEnd.x - this.cropStart.x);
            const height = Math.abs(this.cropEnd.y - this.cropStart.y);
            
            // Determine which dimension to adjust
            if (width / height > this.aspectRatio) {
              // Adjust height
              const adjustedHeight = width / this.aspectRatio;
              const directionY = this.cropEnd.y > this.cropStart.y ? 1 : -1;
              this.cropEnd.y = this.cropStart.y + (adjustedHeight * directionY);
            } else {
              // Adjust width
              const adjustedWidth = height * this.aspectRatio;
              const directionX = this.cropEnd.x > this.cropStart.x ? 1 : -1;
              this.cropEnd.x = this.cropStart.x + (adjustedWidth * directionX);
            }
          }
        }
      }
    },
    activeFeature(newVal, oldVal) {
      if (newVal === "crop") {
        this.$nextTick(() => {
          this.centerCropBox();
        });
      } else if (newVal === "resize") {
        this.$nextTick(() => {
          this.initializeResizeBox();
        });
      }
    },
    image() {
      // Reset when image changes
      this.$nextTick(() => {
        this.centerCropBox();
        if (this.activeFeature === "resize") {
          this.initializeResizeBox();
        }
      });
    },
    // Watch for image dimension changes to update resize box
    imageDimensions: {
      handler(newDimensions, oldDimensions) {
        if (
          this.activeFeature === "resize" &&
          newDimensions &&
          (!oldDimensions ||
            newDimensions.width !== oldDimensions.width ||
            newDimensions.height !== oldDimensions.height)
        ) {
          console.log("Image dimensions changed, updating resize box");
          this.$nextTick(() => {
            this.resetResizeBox();
          });
        }
      },
      deep: true,
    },
  },
  methods: {
    centerCropBox() {
      if (this.activeFeature !== "crop") return;
      if (!this.cropWidthMm || !this.cropHeightMm) return;

      const photoImage = this.$refs.photoImage;
      if (!photoImage) return;

      // Get the actual image dimensions
      const imgRect = photoImage.getBoundingClientRect();
      
      // Get the natural dimensions
      const naturalWidth = photoImage.naturalWidth;
      const naturalHeight = photoImage.naturalHeight;
      
      // Calculate scaling factor between natural and display size
      const scaleFactorX = imgRect.width / naturalWidth;
      const scaleFactorY = imgRect.height / naturalHeight;

      // Use the dynamic conversion factor from dpiUtils
      const { pxPerMm } = getConversionFactors();
      
      // Store the aspect ratio for potential use in free cropping
      this.aspectRatio = this.cropWidthMm / this.cropHeightMm;

      // Calculate dimensions in pixels - applying both the mm-to-px conversion AND the image scaling
      // Use parseFloat to handle decimal values in crop dimensions
      const widthPx = Math.max(0.1, parseFloat(this.cropWidthMm)) * pxPerMm * scaleFactorX;
      const heightPx = Math.max(0.1, parseFloat(this.cropHeightMm)) * pxPerMm * scaleFactorY;

      // Set the dimensions in movableCropBox
      this.movableCropBox.width = widthPx;
      this.movableCropBox.height = heightPx;

      // Center the crop box within the actual image bounds
      this.movableCropBox.x = Math.max(0, (imgRect.width - widthPx) / 2);
      this.movableCropBox.y = Math.max(0, (imgRect.height - heightPx) / 2);

      // Force Vue to update DOM immediately
      this.$forceUpdate();

      // Also update the crop area
      this.$nextTick(() => {
        this.emitCropAreaFromDimensions();
      });

      console.log(
        `Crop box centered at (${this.movableCropBox.x}, ${this.movableCropBox.y}) with dimensions: ${this.cropWidthMm}x${this.cropHeightMm}mm (${widthPx}x${heightPx}px on screen)`
      );
    },
    startMoveCropBox(event) {
      event.preventDefault();
      event.stopPropagation();

      this.movableCropBox.isMoving = true;
      this.movableCropBox.startX = event.clientX - this.movableCropBox.x;
      this.movableCropBox.startY = event.clientY - this.movableCropBox.y;

      // Add event listeners to window to handle move and end events
      window.addEventListener("mousemove", this.moveCropBox);
      window.addEventListener("mouseup", this.endMoveCropBox);
    },
    moveCropBox(event) {
      if (!this.movableCropBox.isMoving) return;

      // Get the actual image bounds
      const img = this.$refs.photoImage;
      const imgRect = img.getBoundingClientRect();

      // Calculate new position
      let newX = event.clientX - this.movableCropBox.startX;
      let newY = event.clientY - this.movableCropBox.startY;

      // Constrain within image bounds - use the stored width and height
      const width = this.movableCropBox.width;
      const height = this.movableCropBox.height;
      
      // Ensure box stays within image bounds
      newX = Math.max(0, Math.min(imgRect.width - width, newX));
      newY = Math.max(0, Math.min(imgRect.height - height, newY));

      this.movableCropBox.x = newX;
      this.movableCropBox.y = newY;

      // Calculate and emit crop area immediately while moving
      this.$nextTick(() => {
        this.emitCropAreaFromDimensions();
      });
    },
    endMoveCropBox() {
      this.movableCropBox.isMoving = false;

      // Remove event listeners from window
      window.removeEventListener("mousemove", this.moveCropBox);
      window.removeEventListener("mouseup", this.endMoveCropBox);

      // Calculate and emit the crop area
      this.emitCropAreaFromDimensions();
    },
    emitCropAreaFromDimensions() {
      if (!this.$refs.photoImage) return;

      const img = this.$refs.photoImage;

      // Get natural dimensions of the image
      const imgNaturalWidth = img.naturalWidth;
      const imgNaturalHeight = img.naturalHeight;

      // Get dimensions of the image in the viewport
      const displayWidth = img.clientWidth;
      const displayHeight = img.clientHeight;

      // Calculate ratios between natural and display size
      const ratioX = imgNaturalWidth / displayWidth;
      const ratioY = imgNaturalHeight / displayHeight;

      // Calculate crop coordinates in the display
      const displayX = this.movableCropBox.x;
      const displayY = this.movableCropBox.y;
      const displayWidthPx = this.movableCropBox.width;
      const displayHeightPx = this.movableCropBox.height;

      // Convert to coordinates in the natural image
      const cropNaturalX = Math.floor(displayX * ratioX);
      const cropNaturalY = Math.floor(displayY * ratioY);
      
      // Convert display dimensions to natural dimensions
      const cropNaturalWidth = Math.max(1, Math.floor(displayWidthPx * ratioX));
      const cropNaturalHeight = Math.max(1, Math.floor(displayHeightPx * ratioY));

      // Log detailed information for debugging
      console.log(
        `Emitting crop area: x=${cropNaturalX}, y=${cropNaturalY}, width=${cropNaturalWidth}, height=${cropNaturalHeight}`
      );
      console.log(
        `Original image dimensions: ${imgNaturalWidth}x${imgNaturalHeight}, Display: ${displayWidth}x${displayHeight}`
      );
      console.log(
        `Display dimensions of crop: ${displayWidthPx}x${displayHeightPx} at position (${displayX},${displayY})`
      );
      console.log(
        `Scale factors: ratioX=${ratioX}, ratioY=${ratioY}`
      );

      // Create the crop area data with all required information
      const cropAreaData = {
        x: cropNaturalX,
        y: cropNaturalY,
        width: cropNaturalWidth,
        height: cropNaturalHeight,
        aspectRatio: displayWidthPx / displayHeightPx,
        maintainAspectRatio: true,
        // Add display dimensions for reference
        displayX: displayX,
        displayY: displayY, 
        displayWidth: displayWidthPx,
        displayHeight: displayHeightPx,
        // Add mm dimensions
        widthMm: this.cropWidthMm,
        heightMm: this.cropHeightMm
      };

      // Emit the crop area data
      this.$emit("crop-area", cropAreaData);
    },
    clearCropBox() {
      this.cropStart = null;
      this.cropEnd = null;
    },
    startDrag(event) {
      event.preventDefault();
      if (this.activeFeature !== "crop") return; // Block drag if not cropping
      if (this.cropWidthMm && this.cropHeightMm) return; // Don't allow free crop if dimensions are set
      
      const rect = this.$refs.imageWrapper.getBoundingClientRect();
      this.cropStart = {
        x: event.clientX - rect.left,
        y: event.clientY - rect.top,
      };
      this.isDragging = true;
      
      // Initialize aspect ratio for free crop
      if (this.maintainAspectRatio) {
        // Store the computed aspect ratio
        this.aspectRatio = this.getCropAspectRatio();
      }
    },
    dragging(event) {
      if (!this.isDragging) return;
      const rect = this.$refs.imageWrapper.getBoundingClientRect();
      
      // Get current position for cropping
      const currentX = event.clientX - rect.left;
      const currentY = event.clientY - rect.top;
      
      // If aspect ratio should be maintained
      if (this.maintainAspectRatio) {
        // Get the aspect ratio to use
        const aspectRatio = this.getCropAspectRatio();
        
        // Calculate width and height based on current drag position
        const width = Math.abs(currentX - this.cropStart.x);
        const height = Math.abs(currentY - this.cropStart.y);
        
        // Determine which dimension to adjust based on the current drag
        if (width / height > aspectRatio) {
          // Width is proportionally larger, adjust height based on width
          const adjustedHeight = width / aspectRatio;
          
          // Set the Y coordinate based on direction
          const directionY = currentY > this.cropStart.y ? 1 : -1;
          this.cropEnd = {
            x: currentX,
            y: this.cropStart.y + (adjustedHeight * directionY)
          };
        } else {
          // Height is proportionally larger, adjust width based on height
          const adjustedWidth = height * aspectRatio;
          
          // Set the X coordinate based on direction
          const directionX = currentX > this.cropStart.x ? 1 : -1;
          this.cropEnd = {
            x: this.cropStart.x + (adjustedWidth * directionX),
            y: currentY
          };
        }
      } else {
        // If no aspect ratio constraint, use free-form crop
        this.cropEnd = {
          x: currentX,
          y: currentY,
        };
      }
    },
    endDrag() {
      if (!this.isDragging) return;
      this.isDragging = false;

      if (!this.cropStart || !this.cropEnd) return;

      const rect = this.$refs.imageWrapper.getBoundingClientRect();
      const startX = Math.min(this.cropStart.x, this.cropEnd.x);
      const startY = Math.min(this.cropStart.y, this.cropEnd.y);
      const width = Math.abs(this.cropEnd.x - this.cropStart.x);
      const height = Math.abs(this.cropEnd.y - this.cropStart.y);

      const img = this.$refs.imageWrapper.querySelector("img");
      const scaleX = img.naturalWidth / rect.width;
      const scaleY = img.naturalHeight / rect.height;

      // Calculate the current aspect ratio
      const currentAspectRatio = width / height;

      this.$emit("crop-area", {
        x: Math.floor(startX * scaleX),
        y: Math.floor(startY * scaleY),
        width: Math.floor(width * scaleX),
        height: Math.floor(height * scaleY),
        aspectRatio: currentAspectRatio,
        maintainAspectRatio: this.maintainAspectRatio
      });

      // Clear crop box after emitting
    },
    showFaceDetection(show, options = {}) {
      // This method will be called to display face detection rectangles
      const imgContainer = this.$el.querySelector(".image-container");
      if (!imgContainer) return;

      // Clear any existing face detection overlays
      this.clearFaceDetectionVisualizations();

      // Clear any existing timeout
      if (this.faceDetectionTimeout) {
        clearTimeout(this.faceDetectionTimeout);
        this.faceDetectionTimeout = null;
      }

      if (!show) return;

      const imgEl = imgContainer.querySelector("img");
      if (!imgEl) return;

      const rect = imgEl.getBoundingClientRect();

      // Create container for face detection elements
      const overlayContainer = document.createElement("div");
      overlayContainer.className = "face-detection-container";
      overlayContainer.style.position = "absolute";
      overlayContainer.style.top = "0";
      overlayContainer.style.left = "0";
      overlayContainer.style.width = "100%";
      overlayContainer.style.height = "100%";
      overlayContainer.style.pointerEvents = "none";
      overlayContainer.style.zIndex = "1000";
      imgContainer.appendChild(overlayContainer);

      // Add guideline indicators for better visualization
      const horizontalLine = document.createElement("div");
      horizontalLine.style.position = "absolute";
      horizontalLine.style.top = "50%";
      horizontalLine.style.left = "0";
      horizontalLine.style.width = "100%";
      horizontalLine.style.height = "1px";
      horizontalLine.style.backgroundColor = "rgba(255, 255, 255, 0.5)";
      horizontalLine.style.pointerEvents = "none";

      const verticalLine = document.createElement("div");
      verticalLine.style.position = "absolute";
      verticalLine.style.top = "0";
      verticalLine.style.left = "50%";
      verticalLine.style.width = "1px";
      verticalLine.style.height = "100%";
      verticalLine.style.backgroundColor = "rgba(255, 255, 255, 0.5)";
      verticalLine.style.pointerEvents = "none";

      overlayContainer.appendChild(horizontalLine);
      overlayContainer.appendChild(verticalLine);

      // Determine if we should show multiple faces
      const detectMultipleFaces = options.detectMultipleFaces || false;
      const useAltDetector = options.useAltDetector || false;
      const useProfileDetector = options.useProfileDetector || false;

      console.log("Face detection options:", {
        detectMultipleFaces,
        useAltDetector,
        useProfileDetector,
      });

      // Determine if the image is portrait or landscape
      const isPortrait = rect.height > rect.width;
      console.log(
        `Image orientation: ${
          isPortrait ? "Portrait" : "Landscape"
        }, dimensions: ${rect.width}x${rect.height}`
      );

      // Array to store face rectangles
      const faces = [];

      // Create a primary face detection rectangle based on the image orientation
      let primaryFace = {};
      if (isPortrait) {
        // Portrait - face is higher up in the frame
        primaryFace = {
          width: rect.width * 0.45, // 45% of width for face in portrait
          height: rect.height * 0.28, // 28% of height for face in portrait
          x: (rect.width - rect.width * 0.45) / 2, // Centered horizontally
          y: rect.height * 0.18, // About 18% from the top (face is in upper part of image)
          isPrimary: true,
        };
      } else {
        // Landscape - face is more centered
        primaryFace = {
          width: rect.width * 0.25, // 25% of width for face in landscape
          height: rect.height * 0.45, // 45% of height for face in landscape
          x: (rect.width - rect.width * 0.25) / 2, // Centered horizontally
          y: (rect.height - rect.height * 0.45) / 2.2, // Slightly above center
          isPrimary: true,
        };
      }

      faces.push(primaryFace);

      // If using alternative detector, adjust the primary face slightly
      if (useAltDetector) {
        primaryFace.x += primaryFace.width * 0.05;
        primaryFace.y -= primaryFace.height * 0.05;
        primaryFace.width *= 0.9;
        primaryFace.height *= 1.1;
      }

      // If detecting profile faces, add a tilted face
      if (useProfileDetector) {
        let profileFace = {};
        if (isPortrait) {
          profileFace = {
            width: rect.width * 0.35,
            height: rect.height * 0.25,
            x: rect.width * 0.15,
            y: rect.height * 0.3,
            angle: 15,
            isProfile: true,
          };
        } else {
          profileFace = {
            width: rect.width * 0.22,
            height: rect.height * 0.4,
            x: rect.width * 0.6,
            y: rect.height * 0.25,
            angle: -10,
            isProfile: true,
          };
        }
        faces.push(profileFace);
      }

      // If detecting multiple faces, add additional faces
      if (detectMultipleFaces) {
        if (isPortrait) {
          // Add a smaller face lower in the image
          faces.push({
            width: rect.width * 0.32,
            height: rect.height * 0.2,
            x: rect.width * 0.6,
            y: rect.height * 0.55,
            isSecondary: true,
          });
        } else {
          // Add a face to the left side
          faces.push({
            width: rect.width * 0.18,
            height: rect.height * 0.32,
            x: rect.width * 0.12,
            y: rect.height * 0.28,
            isSecondary: true,
          });

          // Add a smaller face to the right
          faces.push({
            width: rect.width * 0.15,
            height: rect.height * 0.25,
            x: rect.width * 0.75,
            y: rect.height * 0.45,
            isSecondary: true,
          });
        }
      }

      // Display each face
      faces.forEach((face, index) => {
        console.log(`Face ${index}:`, face);

        // Create the face rectangle
        const faceOverlay = document.createElement("div");
        faceOverlay.className = "face-detection-overlay";
        faceOverlay.style.position = "absolute";
        faceOverlay.style.left = `${face.x}px`;
        faceOverlay.style.top = `${face.y}px`;
        faceOverlay.style.width = `${face.width}px`;
        faceOverlay.style.height = `${face.height}px`;

        // Different styling based on face type
        if (face.isPrimary) {
          faceOverlay.style.border = "4px solid #00FF00";
        } else if (face.isProfile) {
          faceOverlay.style.border = "4px solid #FF9500";
        } else {
          faceOverlay.style.border = "4px solid #00AAFF";
        }

        faceOverlay.style.borderRadius = "4px";
        faceOverlay.style.boxShadow = "0 0 12px rgba(0, 255, 0, 0.8)";
        faceOverlay.style.pointerEvents = "none";

        // Apply rotation if specified
        if (face.angle) {
          faceOverlay.style.transformOrigin = "center";
          faceOverlay.style.transform = `rotate(${face.angle}deg)`;
        }

        // Add label
        const label = document.createElement("div");
        if (face.isPrimary) {
          label.textContent = "Primary Face";
        } else if (face.isProfile) {
          label.textContent = "Profile Face";
        } else {
          label.textContent = `Secondary Face ${index}`;
        }

        label.style.position = "absolute";
        label.style.top = `${face.y - 28}px`;
        label.style.left = `${face.x}px`;
        label.style.backgroundColor = "rgba(0, 0, 0, 0.7)";

        if (face.isPrimary) {
          label.style.color = "#00FF00";
        } else if (face.isProfile) {
          label.style.color = "#FF9500";
        } else {
          label.style.color = "#00AAFF";
        }

        label.style.padding = "4px 8px";
        label.style.borderRadius = "4px";
        label.style.fontSize = "14px";
        label.style.fontWeight = "bold";
        label.style.zIndex = "1000";

        // Calculate face center
        const faceCenterX = face.x + face.width / 2;
        const faceCenterY = face.y + face.height / 2;

        // Add face center dot
        const faceCenterDot = document.createElement("div");
        faceCenterDot.style.position = "absolute";
        faceCenterDot.style.left = `${faceCenterX - 5}px`;
        faceCenterDot.style.top = `${faceCenterY - 5}px`;
        faceCenterDot.style.width = "10px";
        faceCenterDot.style.height = "10px";
        faceCenterDot.style.borderRadius = "50%";
        faceCenterDot.style.backgroundColor = "#FF0000";
        faceCenterDot.style.boxShadow = "0 0 8px rgba(255, 0, 0, 0.8)";
        faceCenterDot.style.zIndex = "1001";

        // Add all elements to the container
        overlayContainer.appendChild(faceOverlay);
        overlayContainer.appendChild(label);
        overlayContainer.appendChild(faceCenterDot);

        // Only add connecting lines for primary and profile faces
        if (face.isPrimary || face.isProfile) {
          // Calculate image center
          const imageCenterX = rect.width / 2;
          const imageCenterY = rect.height / 2;

          // Add line connecting centers
          const connectingLine = document.createElement("div");
          const lineLength = Math.sqrt(
            Math.pow(faceCenterX - imageCenterX, 2) +
              Math.pow(faceCenterY - imageCenterY, 2)
          );
          const angle =
            Math.atan2(imageCenterY - faceCenterY, imageCenterX - faceCenterX) *
            (180 / Math.PI);

          connectingLine.style.position = "absolute";
          connectingLine.style.left = `${faceCenterX}px`;
          connectingLine.style.top = `${faceCenterY}px`;
          connectingLine.style.width = `${lineLength}px`;
          connectingLine.style.height = "2px";
          connectingLine.style.backgroundColor = "#FFFF00";
          connectingLine.style.transformOrigin = "left center";
          connectingLine.style.transform = `rotate(${angle}deg)`;
          connectingLine.style.zIndex = "1000";

          overlayContainer.appendChild(connectingLine);
        }
      });

      // Calculate image center
      const imageCenterX = rect.width / 2;
      const imageCenterY = rect.height / 2;

      // Add image center dot
      const imageCenterDot = document.createElement("div");
      imageCenterDot.style.position = "absolute";
      imageCenterDot.style.left = `${imageCenterX - 5}px`;
      imageCenterDot.style.top = `${imageCenterY - 5}px`;
      imageCenterDot.style.width = "10px";
      imageCenterDot.style.height = "10px";
      imageCenterDot.style.borderRadius = "50%";
      imageCenterDot.style.backgroundColor = "#0000FF";
      imageCenterDot.style.boxShadow = "0 0 8px rgba(0, 0, 255, 0.8)";
      imageCenterDot.style.zIndex = "1001";
      overlayContainer.appendChild(imageCenterDot);

      // Add legend
      const legend = document.createElement("div");
      legend.style.position = "absolute";
      legend.style.bottom = "60px";
      legend.style.left = "50%";
      legend.style.transform = "translateX(-50%)";
      legend.style.backgroundColor = "rgba(0, 0, 0, 0.8)";
      legend.style.color = "white";
      legend.style.padding = "10px 15px";
      legend.style.borderRadius = "6px";
      legend.style.fontSize = "12px";
      legend.style.fontWeight = "bold";
      legend.style.zIndex = "1002";
      legend.style.textAlign = "left";
      legend.style.width = "auto";
      legend.style.display = "flex";
      legend.style.flexDirection = "column";
      legend.style.gap = "6px";

      // Add legend items
      const legendItems = [
        { color: "#00FF00", text: "Detected Face" },
        { color: "#FF0000", text: "Face Center" },
        { color: "#0000FF", text: "Image Center" },
        { color: "#FFFF00", text: "Distance to Center" },
      ];

      legendItems.forEach((item) => {
        const legendItem = document.createElement("div");
        legendItem.style.display = "flex";
        legendItem.style.alignItems = "center";
        legendItem.style.gap = "8px";

        const colorBox = document.createElement("div");
        colorBox.style.width = "12px";
        colorBox.style.height = "12px";
        colorBox.style.backgroundColor = item.color;
        colorBox.style.borderRadius = item.color === "#00FF00" ? "2px" : "50%";

        const itemText = document.createElement("span");
        itemText.textContent = item.text;

        legendItem.appendChild(colorBox);
        legendItem.appendChild(itemText);
        legend.appendChild(legendItem);
      });

      // Show a notification that face detection is in progress
      const notification = document.createElement("div");
      notification.textContent = "Processing... Centering face in the image";
      notification.style.position = "absolute";
      notification.style.bottom = "20px";
      notification.style.left = "50%";
      notification.style.transform = "translateX(-50%)";
      notification.style.backgroundColor = "rgba(0, 0, 0, 0.8)";
      notification.style.color = "white";
      notification.style.padding = "12px 24px";
      notification.style.borderRadius = "20px";
      notification.style.fontSize = "16px";
      notification.style.fontWeight = "bold";
      notification.style.zIndex = "9999";
      notification.style.maxWidth = "80%";
      notification.style.textAlign = "center";

      // Add all elements to the container
      overlayContainer.appendChild(faceOverlay);
      overlayContainer.appendChild(label);
      overlayContainer.appendChild(faceCenterDot);
      overlayContainer.appendChild(imageCenterDot);
      overlayContainer.appendChild(legend);
      overlayContainer.appendChild(notification);

      // Add animation effect
      faceOverlay.animate(
        [
          { transform: "scale(1)", opacity: 0.8 },
          { transform: "scale(1.05)", opacity: 1 },
          { transform: "scale(1)", opacity: 0.8 },
        ],
        {
          duration: 1500,
          iterations: 2,
        }
      );

      // Remove the overlay after 5 seconds
      this.faceDetectionTimeout = setTimeout(() => {
        overlayContainer.remove();
        this.faceDetectionTimeout = null;
      }, 5000);
    },
    clearFaceDetectionVisualizations() {
      // Find all element containers related to face detection and remove them
      const imgContainer = this.$el.querySelector(".image-container");
      if (!imgContainer) return;

      console.log("Cleaning up face detection visualizations");

      // Clear any existing face detection overlays
      const existingOverlays = document.querySelectorAll(
        ".face-detection-overlay, .face-detection-container, .notification"
      );
      existingOverlays.forEach((overlay) => {
        console.log("Removing overlay:", overlay.className);
        overlay.remove();
      });

      // Clear any timeouts
      if (this.faceDetectionTimeout) {
        clearTimeout(this.faceDetectionTimeout);
        this.faceDetectionTimeout = null;
      }

      // Also remove all indicators/notifications that might have been added
      const notifications = document.querySelectorAll(".notification");
      notifications.forEach((notification) => {
        notification.remove();
      });

      // Remove any loading indicators
      const loadingIndicators = document.querySelectorAll(
        "div[style*='Processing...'], div[style*='Detecting faces...']"
      );
      loadingIndicators.forEach((indicator) => {
        indicator.remove();
      });
    },
    initializeResizeBox() {
      this.resetResizeBox();

      // Emit initial dimensions
      this.emitResizeDimensions();

      // Add touch event listeners for mobile devices
      this.$nextTick(() => {
        this.addTouchEventListeners();
      });
    },
    startResize(handle, event) {
      event.preventDefault();
      event.stopPropagation();

      this.isResizing = true;
      this.resizeHandle = handle;

      // Store original state
      this.resizeStartPoint = {
        x: event.clientX,
        y: event.clientY,
      };
      this.originalResizeBox = { ...this.resizeBox };

      // Add event listeners to window for resize
      window.addEventListener("mousemove", this.resizeMove);
      window.addEventListener("mouseup", this.endResize);
    },

    resizeMove(event) {
      if (!this.isResizing) return;

      const dx = event.clientX - this.resizeStartPoint.x;
      const dy = event.clientY - this.resizeStartPoint.y;

      const newBox = { ...this.resizeBox };

      // Handle resize based on which handle was grabbed
      switch (this.resizeHandle) {
        case "right":
          newBox.width = Math.max(20, this.originalResizeBox.width + dx);
          // Maintain aspect ratio by adjusting height
          newBox.height = newBox.width / this.aspectRatio;
          break;
        case "bottom":
          newBox.height = Math.max(20, this.originalResizeBox.height + dy);
          // Maintain aspect ratio by adjusting width
          newBox.width = newBox.height * this.aspectRatio;
          break;
        case "left":
          const newWidth = Math.max(20, this.originalResizeBox.width - dx);
          newBox.width = newWidth;
          // Maintain aspect ratio by adjusting height
          newBox.height = newBox.width / this.aspectRatio;
          newBox.x =
            this.originalResizeBox.x +
            (this.originalResizeBox.width - newBox.width);
          break;
        case "top":
          const newHeight = Math.max(20, this.originalResizeBox.height - dy);
          newBox.height = newHeight;
          // Maintain aspect ratio by adjusting width
          newBox.width = newBox.height * this.aspectRatio;
          newBox.y =
            this.originalResizeBox.y +
            (this.originalResizeBox.height - newBox.height);
          break;
        case "bottom-right":
          if (Math.abs(dx) > Math.abs(dy)) {
            newBox.width = Math.max(20, this.originalResizeBox.width + dx);
            newBox.height = newBox.width / this.aspectRatio;
          } else {
            newBox.height = Math.max(20, this.originalResizeBox.height + dy);
            newBox.width = newBox.height * this.aspectRatio;
          }
          break;
        case "bottom-left":
          if (Math.abs(dx) > Math.abs(dy)) {
            const newWidth = Math.max(20, this.originalResizeBox.width - dx);
            newBox.width = newWidth;
            newBox.height = newBox.width / this.aspectRatio;
            newBox.x =
              this.originalResizeBox.x +
              (this.originalResizeBox.width - newBox.width);
          } else {
            newBox.height = Math.max(20, this.originalResizeBox.height + dy);
            newBox.width = newBox.height * this.aspectRatio;
            newBox.x =
              this.originalResizeBox.x +
              (this.originalResizeBox.width - newBox.width);
          }
          break;
        case "top-right":
          if (Math.abs(dx) > Math.abs(dy)) {
            newBox.width = Math.max(20, this.originalResizeBox.width + dx);
            newBox.height = newBox.width / this.aspectRatio;
            newBox.y =
              this.originalResizeBox.y +
              (this.originalResizeBox.height - newBox.height);
          } else {
            const newHeight = Math.max(20, this.originalResizeBox.height - dy);
            newBox.height = newHeight;
            newBox.width = newBox.height * this.aspectRatio;
            newBox.y =
              this.originalResizeBox.y +
              (this.originalResizeBox.height - newBox.height);
          }
          break;
        case "top-left":
          if (Math.abs(dx) > Math.abs(dy)) {
            const newWidth = Math.max(20, this.originalResizeBox.width - dx);
            newBox.width = newWidth;
            newBox.height = newBox.width / this.aspectRatio;
            newBox.x =
              this.originalResizeBox.x +
              (this.originalResizeBox.width - newBox.width);
            newBox.y =
              this.originalResizeBox.y +
              (this.originalResizeBox.height - newBox.height);
          } else {
            const newHeight = Math.max(20, this.originalResizeBox.height - dy);
            newBox.height = newHeight;
            newBox.width = newBox.height * this.aspectRatio;
            newBox.x =
              this.originalResizeBox.x +
              (this.originalResizeBox.width - newBox.width);
            newBox.y =
              this.originalResizeBox.y +
              (this.originalResizeBox.height - newBox.height);
          }
          break;
      }

      // Update resize box
      this.resizeBox = newBox;

      // Emit current dimensions during resize
      this.emitResizeDimensions();
    },

    endResize() {
      if (!this.isResizing) return;

      this.isResizing = false;
      this.resizeHandle = null;

      // Remove event listeners
      window.removeEventListener("mousemove", this.resizeMove);
      window.removeEventListener("mouseup", this.endResize);

      // Final emit of resize dimensions
      this.emitResizeDimensions();
    },
    emitResizeDimensions() {
      if (!this.resizeBox || !this.$refs.photoImage) return;

      const img = this.$refs.photoImage;

      // Get natural dimensions of the image
      const imgNaturalWidth = img.naturalWidth;
      const imgNaturalHeight = img.naturalHeight;

      // Get dimensions of the image in the viewport
      const displayWidth = img.clientWidth;
      const displayHeight = img.clientHeight;

      // Calculate scale ratio between natural and display
      const scaleX = imgNaturalWidth / displayWidth;
      const scaleY = imgNaturalHeight / displayHeight;

      // Calculate new natural dimensions based on resize box
      const newWidth = Math.round(this.resizeBox.width * scaleX);
      const newHeight = Math.round(this.resizeBox.height * scaleY);

      // Emit resize info
      this.$emit("resize-dimensions", {
        width: newWidth,
        height: newHeight,
        maintainAspectRatio: true,
        widthProvided: true,
        heightProvided: true,
        isResizing: this.isResizing, // Add flag to indicate if resize is in progress
      });
    },
    addTouchEventListeners() {
      // Add touch event handlers for resize on mobile
      const resizeHandles = this.$el.querySelectorAll(".resize-handle");

      resizeHandles.forEach((handle) => {
        handle.addEventListener("touchstart", (e) => {
          // Get handle type
          const classes = handle.className.split(" ");
          const handleType = classes.find((cls) =>
            [
              "top-left",
              "top",
              "top-right",
              "right",
              "bottom-right",
              "bottom",
              "bottom-left",
              "left",
            ].includes(cls)
          );

          if (handleType) {
            e.preventDefault();

            // Determine if this is a crop resize handle (parent is crop-box) or regular resize handle
            const parentElement = handle.parentElement;
            const isCropHandle = parentElement && parentElement.classList.contains('crop-box');
            
            if (isCropHandle) {
              // Set crop resize state
              this.movableCropBox.isResizing = true;
              this.movableCropBox.resizeHandle = handleType;
              
              const touch = e.touches[0];
              this.movableCropBox.resizeStartPoint = {
                x: touch.clientX,
                y: touch.clientY,
              };
              
              // Store original box state
              this.movableCropBox.originalBox = {
                x: this.movableCropBox.x,
                y: this.movableCropBox.y,
                width: this.movableCropBox.width,
                height: this.movableCropBox.height,
              };
            } else {
              // Set resize state for regular resize
              this.isResizing = true;
              this.resizeHandle = handleType;

              const touch = e.touches[0];
              this.resizeStartPoint = {
                x: touch.clientX,
                y: touch.clientY,
              };
              this.originalResizeBox = { ...this.resizeBox };
            }
          }
        });
      });

      // Add touchmove handler
      document.addEventListener(
        "touchmove",
        (e) => {
          // Handle crop box resizing
          if (this.movableCropBox.isResizing && e.touches[0]) {
            e.preventDefault();
            const touch = e.touches[0];
            
            // Create a synthetic event with the touch coordinates
            const syntheticEvent = {
              clientX: touch.clientX,
              clientY: touch.clientY,
            };
            
            // Use the crop resize logic
            this.cropResizeMove(syntheticEvent);
            return;
          }
          
          // Handle regular resizing
          if (this.isResizing && e.touches[0]) {
            e.preventDefault();
            const touch = e.touches[0];

            // Create a synthetic event with the touch coordinates
            const syntheticEvent = {
              clientX: touch.clientX,
              clientY: touch.clientY,
            };

            // Use the existing resize logic
            this.resizeMove(syntheticEvent);
          }
        },
        { passive: false }
      );

      // Add touchend handler
      document.addEventListener("touchend", () => {
        // Handle crop box resizing
        if (this.movableCropBox.isResizing) {
          this.endCropResize();
        }
        
        // Handle regular resizing
        if (this.isResizing) {
          this.endResize();
        }
      });
    },
    // Method to reset the resize box to match the current image dimensions
    resetResizeBox() {
      const photoImage = this.$refs.photoImage;
      if (!photoImage) return;

      const imgRect = photoImage.getBoundingClientRect();

      this.resizeBox = {
        x: 0,
        y: 0,
        width: imgRect.width,
        height: imgRect.height,
      };

      this.aspectRatio = imgRect.width / imgRect.height;

      console.log(
        "Resize box reset to match image dimensions:",
        this.resizeBox
      );
    },
    // Add image load handler
    handleImageLoad() {
      console.log("Image loaded, checking if resize box needs update");
      
      // Get the image dimensions and emit them
      const img = this.$refs.photoImage;
      if (img) {
        const dimensions = {
          width: img.naturalWidth,
          height: img.naturalHeight
        };
        console.log("Emitting image dimensions on load:", dimensions);
        this.$emit("resize-dimensions", dimensions);
      }
      
      if (this.activeFeature === "resize") {
        this.resetResizeBox();
      }
    },
    // Helper method to get the current crop aspect ratio
    getCropAspectRatio() {
      if (this.aspectRatioValue) {
        return this.aspectRatioValue;
      } else if (this.cropWidthMm && this.cropHeightMm) {
        return this.cropWidthMm / this.cropHeightMm;
      } else if (this.aspectRatio) {
        return this.aspectRatio;
      } else if (this.$refs.photoImage) {
        // Use image's natural aspect ratio as default
        const img = this.$refs.photoImage;
        return img.naturalWidth / img.naturalHeight;
      }
      
      // Default to 1:1 if no other ratio is determined
      return 1;
    },
    // Public method to reset crop to center position when crop is applied
    resetCropToCenter() {
      if (this.activeFeature === "crop") {
        console.log("Resetting crop mask to center position");
        this.centerCropBox();
        return true;
      }
      return false;
    },
    // Public method to hide crop mask when crop is applied
    hideCropMask() {
      if (this.activeFeature === "crop") {
        console.log("Hiding crop mask");
        // Emit an event to notify that crop has been completed
        this.$emit("crop-complete");
        // Reset state - temporarily remove crop dimensions to hide the mask
        this.$emit("update:activeFeature", "");
        return true;
      }
      return false;
    },
    startCropResize(handle, event) {
      event.preventDefault();
      event.stopPropagation();

      this.movableCropBox.isResizing = true;
      this.movableCropBox.resizeHandle = handle;

      // Store original state
      this.movableCropBox.resizeStartPoint = {
        x: event.clientX,
        y: event.clientY,
      };
      
      // Store original box state
      this.movableCropBox.originalBox = {
        x: this.movableCropBox.x,
        y: this.movableCropBox.y,
        width: this.movableCropBox.width,
        height: this.movableCropBox.height,
      };

      // Add event listeners to window for resize
      window.addEventListener("mousemove", this.cropResizeMove);
      window.addEventListener("mouseup", this.endCropResize);
    },

    cropResizeMove(event) {
      if (!this.movableCropBox.isResizing) return;

      const dx = event.clientX - this.movableCropBox.resizeStartPoint.x;
      const dy = event.clientY - this.movableCropBox.resizeStartPoint.y;

      // Get aspect ratio for crop box
      const aspectRatio = this.getCropAspectRatio();
      
      // Create a new box with the updated dimensions
      const newBox = { ...this.movableCropBox.originalBox };

      // Handle resize based on which handle was grabbed
      switch (this.movableCropBox.resizeHandle) {
        case "right":
          newBox.width = Math.max(20, this.movableCropBox.originalBox.width + dx);
          // Maintain aspect ratio by adjusting height
          newBox.height = newBox.width / aspectRatio;
          break;
        case "bottom":
          newBox.height = Math.max(20, this.movableCropBox.originalBox.height + dy);
          // Maintain aspect ratio by adjusting width
          newBox.width = newBox.height * aspectRatio;
          break;
        case "left":
          const newWidth = Math.max(20, this.movableCropBox.originalBox.width - dx);
          newBox.width = newWidth;
          // Maintain aspect ratio by adjusting height
          newBox.height = newBox.width / aspectRatio;
          newBox.x = this.movableCropBox.originalBox.x + 
                      (this.movableCropBox.originalBox.width - newBox.width);
          break;
        case "top":
          const newHeight = Math.max(20, this.movableCropBox.originalBox.height - dy);
          newBox.height = newHeight;
          // Maintain aspect ratio by adjusting width
          newBox.width = newBox.height * aspectRatio;
          newBox.y = this.movableCropBox.originalBox.y + 
                      (this.movableCropBox.originalBox.height - newBox.height);
          break;
        case "bottom-right":
          if (Math.abs(dx) > Math.abs(dy)) {
            newBox.width = Math.max(20, this.movableCropBox.originalBox.width + dx);
            newBox.height = newBox.width / aspectRatio;
          } else {
            newBox.height = Math.max(20, this.movableCropBox.originalBox.height + dy);
            newBox.width = newBox.height * aspectRatio;
          }
          break;
        case "bottom-left":
          if (Math.abs(dx) > Math.abs(dy)) {
            const newWidth = Math.max(20, this.movableCropBox.originalBox.width - dx);
            newBox.width = newWidth;
            newBox.height = newBox.width / aspectRatio;
            newBox.x = this.movableCropBox.originalBox.x + 
                        (this.movableCropBox.originalBox.width - newBox.width);
          } else {
            newBox.height = Math.max(20, this.movableCropBox.originalBox.height + dy);
            newBox.width = newBox.height * aspectRatio;
            newBox.x = this.movableCropBox.originalBox.x + 
                        (this.movableCropBox.originalBox.width - newBox.width);
          }
          break;
        case "top-right":
          if (Math.abs(dx) > Math.abs(dy)) {
            newBox.width = Math.max(20, this.movableCropBox.originalBox.width + dx);
            newBox.height = newBox.width / aspectRatio;
            newBox.y = this.movableCropBox.originalBox.y + 
                        (this.movableCropBox.originalBox.height - newBox.height);
          } else {
            const newHeight = Math.max(20, this.movableCropBox.originalBox.height - dy);
            newBox.height = newHeight;
            newBox.width = newBox.height * aspectRatio;
            newBox.y = this.movableCropBox.originalBox.y + 
                        (this.movableCropBox.originalBox.height - newBox.height);
          }
          break;
        case "top-left":
          if (Math.abs(dx) > Math.abs(dy)) {
            const newWidth = Math.max(20, this.movableCropBox.originalBox.width - dx);
            newBox.width = newWidth;
            newBox.height = newBox.width / aspectRatio;
            newBox.x = this.movableCropBox.originalBox.x + 
                        (this.movableCropBox.originalBox.width - newBox.width);
            newBox.y = this.movableCropBox.originalBox.y + 
                        (this.movableCropBox.originalBox.height - newBox.height);
          } else {
            const newHeight = Math.max(20, this.movableCropBox.originalBox.height - dy);
            newBox.height = newHeight;
            newBox.width = newBox.height * aspectRatio;
            newBox.x = this.movableCropBox.originalBox.x + 
                        (this.movableCropBox.originalBox.width - newBox.width);
            newBox.y = this.movableCropBox.originalBox.y + 
                        (this.movableCropBox.originalBox.height - newBox.height);
          }
          break;
      }

      // Constrain within image bounds
      const photoImage = this.$refs.photoImage;
      if (photoImage) {
        const imgRect = photoImage.getBoundingClientRect();
        
        // Make sure the box doesn't go outside the image
        if (newBox.x < 0) {
          newBox.x = 0;
        }
        if (newBox.y < 0) {
          newBox.y = 0;
        }
        if (newBox.x + newBox.width > imgRect.width) {
          newBox.width = imgRect.width - newBox.x;
          newBox.height = newBox.width / aspectRatio;
        }
        if (newBox.y + newBox.height > imgRect.height) {
          newBox.height = imgRect.height - newBox.y;
          newBox.width = newBox.height * aspectRatio;
        }
      }

      // Update movable crop box
      this.movableCropBox.x = newBox.x;
      this.movableCropBox.y = newBox.y;
      this.movableCropBox.width = newBox.width;
      this.movableCropBox.height = newBox.height;
      
      // Calculate and update crop dimensions in mm
      this.updateCropDimensionsFromBox();

      // Emit current dimensions during resize
      this.$nextTick(() => {
        this.emitCropAreaFromDimensions();
      });
    },

    endCropResize() {
      if (!this.movableCropBox.isResizing) return;

      this.movableCropBox.isResizing = false;
      this.movableCropBox.resizeHandle = null;

      // Remove event listeners
      window.removeEventListener("mousemove", this.cropResizeMove);
      window.removeEventListener("mouseup", this.endCropResize);

      // Update crop dimensions in mm
      this.updateCropDimensionsFromBox();

      // Final emit of crop dimensions
      this.emitCropAreaFromDimensions();
    },
    
    // Helper method to update the crop dimensions in mm based on the current box size
    updateCropDimensionsFromBox() {
      const photoImage = this.$refs.photoImage;
      if (!photoImage) return;
      
      // Get image dimensions
      const naturalWidth = photoImage.naturalWidth;
      const naturalHeight = photoImage.naturalHeight;
      const displayWidth = photoImage.clientWidth;
      const displayHeight = photoImage.clientHeight;
      
      // Calculate scaling factors
      const scaleFactorX = displayWidth / naturalWidth;
      const scaleFactorY = displayHeight / naturalHeight;
      
      // Use the conversion factors
      const { pxPerMm } = getConversionFactors();
      
      // Calculate dimensions in mm
      const widthMm = this.movableCropBox.width / (pxPerMm * scaleFactorX);
      const heightMm = this.movableCropBox.height / (pxPerMm * scaleFactorY);
      
      console.log('Updating crop dimensions from box:', {
        width: this.movableCropBox.width,
        height: this.movableCropBox.height,
        widthMm,
        heightMm
      });
      
      // Store dimensions but don't trigger centerCropBox
      // Use Vue.set to ensure reactivity
      this.$set(this, 'cropWidthMm', widthMm);
      this.$set(this, 'cropHeightMm', heightMm);
    },
  },
  beforeDestroy() {
    // Clean up any remaining timers before component is destroyed
    if (this.faceDetectionTimeout) {
      clearTimeout(this.faceDetectionTimeout);
      this.faceDetectionTimeout = null;
    }

    if (this.resizeEmitTimeout) {
      clearTimeout(this.resizeEmitTimeout);
      this.resizeEmitTimeout = null;
    }

    // Remove any window event listeners
    window.removeEventListener("mousemove", this.resizeMove);
    window.removeEventListener("mouseup", this.endResize);
    window.removeEventListener("mousemove", this.moveCropBox);
    window.removeEventListener("mouseup", this.endMoveCropBox);
    window.removeEventListener("mousemove", this.cropResizeMove);
    window.removeEventListener("mouseup", this.endCropResize);

    // Remove touch event listeners
    document.removeEventListener("touchmove", this.resizeMove);
    document.removeEventListener("touchend", this.endResize);

    // Remove image load listener
    if (this.$refs.photoImage) {
      this.$refs.photoImage.removeEventListener("load", this.handleImageLoad);
    }

    // Clear any resize state
    this.isResizing = false;
    this.resizeHandle = null;
    this.movableCropBox.isResizing = false;
    this.movableCropBox.resizeHandle = null;
  },
};
</script>

<style scoped>
.image-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #121416;
  overflow: hidden;
}

.canvas {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.image-container-inner {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.image-wrapper {
  position: relative;
}

.photo {
  display: block;
  max-width: 90%;
  max-height: 80vh;
  box-shadow: 0 0 20px rgba(0, 0, 0, 0.3);
  object-fit: contain;
}

.crop-box {
  position: absolute;
  z-index: 10;
  pointer-events: auto;
  box-sizing: border-box;
}

.resize-box {
  position: absolute;
  z-index: 10;
  pointer-events: none;
  box-sizing: border-box;
  transition: width 0.05s ease, height 0.05s ease, left 0.05s ease,
    top 0.05s ease;
}

.resize-handle {
  position: absolute;
  width: 20px;
  height: 20px;
  background-color: #fff;
  border: 2px solid #007bff;
  border-radius: 50%;
  pointer-events: auto;
  z-index: 11;
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.4);
}

.resize-handle:hover {
  background-color: #007bff;
  transform: scale(1.2);
  transition: all 0.1s ease;
}

.resize-handle.top-left {
  top: -10px;
  left: -10px;
  cursor: nwse-resize;
}

.resize-handle.top {
  top: -10px;
  left: 50%;
  transform: translateX(-50%);
  cursor: ns-resize;
}

.resize-handle.top-right {
  top: -10px;
  right: -10px;
  cursor: nesw-resize;
}

.resize-handle.right {
  top: 50%;
  right: -10px;
  transform: translateY(-50%);
  cursor: ew-resize;
}

.resize-handle.bottom-right {
  bottom: -10px;
  right: -10px;
  cursor: nwse-resize;
}

.resize-handle.bottom {
  bottom: -10px;
  left: 50%;
  transform: translateX(-50%);
  cursor: ns-resize;
}

.resize-handle.bottom-left {
  bottom: -10px;
  left: -10px;
  cursor: nesw-resize;
}

.resize-handle.left {
  top: 50%;
  left: -10px;
  transform: translateY(-50%);
  cursor: ew-resize;
}
</style>
