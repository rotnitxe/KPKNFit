// services/shareService.ts
import { Share } from '@capacitor/share';
import { Filesystem, Directory } from '@capacitor/filesystem';
import html2canvas from 'html2canvas';

/**
 * Captures a DOM element by ID, converts it to an image, and shares it natively.
 * Prioritizes Web Share API with Files for PWAs (Instagram Stories compatibility),
 * falls back to Capacitor native share or direct download.
 */
export const shareElementAsImage = async (elementId: string, title: string, text: string = "") => {
    try {
        const element = document.getElementById(elementId);
        if (!element) {
            console.error(`Element with id '${elementId}' not found.`);
            return;
        }

        // Generate canvas from DOM element (540x960 → 1080x1920 para Instagram Stories)
        const canvas = await html2canvas(element, {
            backgroundColor: '#000000',
            useCORS: true,
            scale: 2,
            logging: false,
            scrollX: 0,
            scrollY: 0,
        });

        // 1. INTENTO PWA (Web Share API) - Solución para Instagram
        const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png', 1.0));
        
        if (blob && navigator.canShare) {
            const file = new File([blob], 'kpkn_workout.png', { type: 'image/png' });
            // Verificamos si el navegador/celular permite compartir archivos (fotos) directamente
            if (navigator.canShare({ files: [file] })) {
                await navigator.share({
                    files: [file],
                    title: title,
                    text: text
                });
                return; // Éxito, terminamos aquí.
            }
        }

        // 2. FALLBACK CAPACITOR NATIVO (Si es una app compilada y no PWA)
        const dataUrl = canvas.toDataURL('image/jpeg', 0.9);
        const base64Data = dataUrl.split(',')[1];
        const fileName = `share_${Date.now()}.jpg`;

        const savedFile = await Filesystem.writeFile({
            path: fileName,
            data: base64Data,
            directory: Directory.Cache,
        });

        await Share.share({
            title: title,
            text: text,
            url: savedFile.uri,
            dialogTitle: 'Compartir tu logro'
        });

    } catch (error) {
        console.error("Error sharing content:", error);
        
        // 3. FALLBACK DE EMERGENCIA: Forzar descarga directa si ambas opciones fallan
        try {
            const fallbackElement = document.getElementById(elementId);
            if (fallbackElement) {
                const fallbackCanvas = await html2canvas(fallbackElement);
                const link = document.createElement('a');
                link.download = `kpkn_workout_${Date.now()}.png`;
                link.href = fallbackCanvas.toDataURL('image/png');
                link.click();
                alert("No se pudo abrir el menú nativo. La imagen se ha descargado en tu dispositivo.");
            }
        } catch (e) {
            alert("La función de compartir nativa no está disponible en este navegador/dispositivo.");
        }
    }
};