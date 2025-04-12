// Utility functions for DPI and unit conversions

// Get the device's DPI
export function getDPI() {
  // Create a temporary div to measure DPI
  const div = document.createElement('div');
  div.style.width = '1in';
  div.style.height = '1in';
  div.style.position = 'absolute';
  div.style.left = '-100%';
  document.body.appendChild(div);
  
  // Get the actual width in pixels
  const dpi = div.offsetWidth;
  document.body.removeChild(div);
  
  return dpi;
}

// Convert millimeters to pixels based on actual DPI
export function mmToPx(mm) {
  const dpi = getDPI();
  const inches = mm / 25.4; // Convert mm to inches
  return Math.round(inches * dpi);
}

// Convert pixels to millimeters based on actual DPI
export function pxToMm(px) {
  const dpi = getDPI();
  const inches = px / dpi;
  return Math.round(inches * 25.4 * 100) / 100; // Round to 2 decimal places
}

// Get conversion factors
export function getConversionFactors() {
  const dpi = getDPI();
  const pxPerMm = dpi / 25.4;
  const mmPerPx = 25.4 / dpi;
  
  return {
    pxPerMm,
    mmPerPx
  };
} 