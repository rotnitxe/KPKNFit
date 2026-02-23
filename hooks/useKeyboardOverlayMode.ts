import { useEffect } from 'react';

/**
 * Habilita el modo overlay del teclado virtual para que NO redimensione la vista
 * cuando el usuario escribe en inputs. El teclado superpone el contenido en lugar
 * de reducir el viewport. Aplica a Session Editor y Workout Session.
 */
export function useKeyboardOverlayMode(enabled = true) {
    useEffect(() => {
        if (!enabled) return;
        const vk = (navigator as Navigator & { virtualKeyboard?: { overlaysContent: boolean } }).virtualKeyboard;
        if (!vk) return;
        const prev = vk.overlaysContent;
        vk.overlaysContent = true;
        return () => {
            vk.overlaysContent = prev;
        };
    }, [enabled]);
}
