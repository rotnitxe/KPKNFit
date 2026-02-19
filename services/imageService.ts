// services/imageService.ts

const MAX_WIDTH = 1080; // Max width for optimized images
const QUALITY = 0.8; // 80% quality for WebP

/**
 * Optimizes an image from a base64 data URL.
 * It resizes the image, converts it to WebP format, and compresses it.
 * 
 * @param base64Str The base64 data URL of the image to optimize.
 * @returns A promise that resolves with the optimized base64 data URL in WebP format.
 */
export const optimizeImage = (base64Str: string): Promise<string> => {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.src = base64Str;

    img.onload = () => {
      let { width, height } = img;

      // Calculate new dimensions while maintaining aspect ratio
      if (width > MAX_WIDTH) {
        height = (height * MAX_WIDTH) / width;
        width = MAX_WIDTH;
      }

      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;

      const ctx = canvas.getContext('2d');
      if (!ctx) {
        return reject(new Error('Could not get canvas context for image optimization.'));
      }

      // Draw image to canvas
      ctx.drawImage(img, 0, 0, width, height);

      // Get the optimized image as a WebP data URL
      const optimizedBase64 = canvas.toDataURL('image/webp', QUALITY);
      
      resolve(optimizedBase64);
    };

    img.onerror = (error) => {
      console.error("Failed to load image for optimization:", error);
      // Fallback to the original image if there's an error
      resolve(base64Str);
    };
  });
};