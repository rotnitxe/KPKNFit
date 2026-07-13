// services/cameraService.ts
import { Capacitor } from '@capacitor/core';
import { Filesystem, Directory } from '@capacitor/filesystem';

/**
 * Takes a picture using the device's camera, saves it to the app's data directory,
 * and returns the permanent file URI. This avoids loading large base64 strings into memory.
 * @returns A promise that resolves with the file URI (e.g., 'file://...') of the saved image, or null if cancelled.
 */
export const takePicture = async (): Promise<string | null> => {
    if (!Capacitor.isNativePlatform()) {
        alert("La cámara solo está disponible en la app nativa.");
        return null;
    }
    try {
        const { Camera, CameraResultType, CameraSource } = await import('@capacitor/camera');
        const image = await Camera.getPhoto({
            quality: 90,
            allowEditing: false,
            resultType: CameraResultType.Uri,
            source: CameraSource.Prompt,
        });

        if (!image.webPath) {
            return null;
        }

        // Convert the temporary webPath to a permanent file in the app's data directory
        const response = await fetch(image.webPath);
        const blob = await response.blob();
        
        const fileName = `photo_${new Date().getTime()}.jpeg`;

        // Read the blob as a base64 string to write it to the filesystem
        const base64Data = await new Promise<string>((resolve, reject) => {
            const reader = new FileReader();
            reader.onerror = reject;
            reader.onload = () => {
                resolve((reader.result as string).split(',')[1]);
            };
            reader.readAsDataURL(blob);
        });

        const savedFile = await Filesystem.writeFile({
            path: fileName,
            data: base64Data,
            directory: Directory.Data,
        });

        // On some platforms (like iOS), we need to clean up the temporary file
        if (image.path) {
            try {
                await Filesystem.deleteFile({ path: image.path });
            } catch (cleanupError) {
                console.warn("Could not delete temporary camera file:", cleanupError);
            }
        }
        
        return savedFile.uri;

    } catch (error) {
        // User cancellation is not an error we need to log
        if (error instanceof Error && (error.message.includes("cancelled") || error.message.includes("canceled"))) {
            return null;
        }
        console.error('Camera or Filesystem error:', error);
        alert('No se pudo tomar o guardar la foto.');
        return null;
    }
};
