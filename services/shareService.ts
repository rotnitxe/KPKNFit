
// services/shareService.ts
import { Share } from '@capacitor/share';
import { Filesystem, Directory } from '@capacitor/filesystem';
import html2canvas from 'html2canvas';

/**
 * Captures a DOM element by ID, converts it to an image, saves it to the
 * local filesystem, and invokes the native share dialog.
 * 
 * @param elementId The HTML ID of the element to capture.
 * @param title The title for the share dialog.
 * @param text Optional text to share alongside the image.
 */
export const shareElementAsImage = async (elementId: string, title: string, text: string = "") => {
    try {
        const element = document.getElementById(elementId);
        if (!element) {
            console.error(`Element with id '${elementId}' not found.`);
            return;
        }

        // Generate canvas from DOM element
        // Use standard options to ensure good quality and CORS handling if images are present
        const canvas = await html2canvas(element, {
            backgroundColor: '#000000', // Ensure dark background for capturing
            useCORS: true,
            scale: 2, // Higher resolution
            logging: false,
        });

        // Convert canvas to base64 data URL
        const dataUrl = canvas.toDataURL('image/jpeg', 0.9);
        const base64Data = dataUrl.split(',')[1];
        
        // Generate a unique filename
        const fileName = `share_${Date.now()}.jpg`;

        // Save to filesystem (Cache directory is appropriate for temporary share files)
        const savedFile = await Filesystem.writeFile({
            path: fileName,
            data: base64Data,
            directory: Directory.Cache,
        });

        // Share the file
        await Share.share({
            title: title,
            text: text,
            url: savedFile.uri,
            dialogTitle: 'Compartir tu logro'
        });

    } catch (error) {
        console.error("Error sharing content:", error);
        // Fallback for web or if file writing fails
        if ((error as any).message && (error as any).message.includes('Share API not available')) {
             alert("La función de compartir nativa no está disponible en este navegador/dispositivo.");
        }
    }
};
