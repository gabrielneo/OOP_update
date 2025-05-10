<template>
  <div class="dimensions-row" v-if="imageDimensions">
    <div class="image-dimensions">
      <span class="dimensions-label">Image Size:</span>
      <span class="dimensions-value">{{ imageDimensions.width }} × {{ imageDimensions.height }}px</span>
      <span class="dimensions-separator">|</span>
      <span class="dimensions-value">{{ pixelsToMm(imageDimensions.width).toFixed(1) }} × {{ pixelsToMm(imageDimensions.height).toFixed(1) }}mm</span>
    </div>
  </div>
</template>

<script>
export default {
  name: "DimensionsBar",
  props: {
    imageDimensions: {
      type: Object,
      default: null
    }
  },
  methods: {
    pixelsToMm(pixels) {
      // Approximate conversion from pixels to millimeters
      // Using a standard factor of 3.78 pixels per mm (same as in the app)
      const pxPerMm = 3.78;
      return pixels / pxPerMm;
    }
  }
}
</script>

<style scoped>
.dimensions-row {
  display: flex;
  justify-content: center;
  padding: 8px 16px;
  background-color: #1e2124;
  border-top: 1px solid #3a3f45;
}

.image-dimensions {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  background-color: #3a3f45;
  border-radius: 4px;
  font-size: 14px;
  white-space: nowrap;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
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

@media (max-width: 1200px) {
  .dimensions-value:last-child {
    display: none; /* Hide mm dimensions on medium screens */
  }
  
  .dimensions-separator {
    display: none;
  }
}

@media (max-width: 650px) {
  .image-dimensions {
    max-width: none;
    font-size: 12px;
  }
}
</style> 