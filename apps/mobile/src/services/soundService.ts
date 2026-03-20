import { AccessibilityInfo, Vibration } from 'react-native';

const DEFAULT_HAPTIC_MS = 25;

export async function preloadSounds() {
  // RN wrapper kept for API parity with PWA. Real assets are optional.
}

export async function playSound(_soundId: string) {
  // Minimal tactile fallback when no dedicated audio asset pipeline is configured.
  Vibration.vibrate(DEFAULT_HAPTIC_MS);
}

export async function speak(text: string, _lang = 'es-ES') {
  if (!text.trim()) return;
  try {
    await AccessibilityInfo.announceForAccessibility(text);
  } catch (error) {
    console.warn('No se pudo anunciar el texto por accesibilidad.', error);
  }
}

export async function configureAudioSession() {
  // Reserved hook for native audio session config in future iterations.
}
