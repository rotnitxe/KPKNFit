// services/hapticsService.ts
import { Capacitor } from '@capacitor/core';
import { useSettingsStore } from '../stores/settingsStore';
import { ImpactStyle, NotificationType } from '@capacitor/haptics';

// Re-export enums for components (enums are zero runtime cost)
export { ImpactStyle, NotificationType };

const COOLDOWN_MS = 200;
let lastHapticTime = 0;

const canUseHaptics = (): boolean => {
    if (!Capacitor.isNativePlatform()) return false;
    try {
        const settings = useSettingsStore.getState().settings;
        return settings?.hapticFeedbackEnabled ?? true;
    } catch {
        return true; // Failsafe
    }
};

const checkCooldown = (): boolean => {
    const now = Date.now();
    if (now - lastHapticTime < COOLDOWN_MS) return false;
    lastHapticTime = now;
    return true;
}

// Function to trigger a haptic impact feedback
export const hapticImpact = async (style: ImpactStyle = ImpactStyle.Light) => {
  if (!canUseHaptics() || !checkCooldown()) return;
  try {
    const { Haptics } = await import('@capacitor/haptics');
    await Haptics.impact({ style });
  } catch (error) {
    console.warn("Haptics not available on this device.", error);
  }
};

// Function to trigger a haptic notification feedback
export const hapticNotification = async (type: NotificationType) => {
  if (!canUseHaptics() || !checkCooldown()) return;
  try {
    const { Haptics } = await import('@capacitor/haptics');
    await Haptics.notification({ type });
  } catch (error) {
    console.warn("Haptics notification not available on this device.", error);
  }
};

// Function to trigger a haptic feedback for selection changes
export const hapticSelection = async () => {
  if (!canUseHaptics() || !checkCooldown()) return;
  try {
    const { Haptics } = await import('@capacitor/haptics');
    await Haptics.selectionStart();
  } catch (error) {
    console.warn("Haptics selection not available on this device.", error);
  }
};