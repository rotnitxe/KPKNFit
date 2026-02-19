// services/hapticsService.ts
import { Capacitor } from '@capacitor/core';
import { storageService } from './storageService';
import { Settings } from '../types';
import type { ImpactStyle as CapImpactStyle, NotificationType as CapNotificationType } from '@capacitor/haptics';

// Re-export the types for use in other parts of the app
export type ImpactStyle = CapImpactStyle;
export type NotificationType = CapNotificationType;

// Manually define and export the enums since we can't export from a dynamic import.
// These are just string constants.
export const ImpactStyle = {
    Heavy: 'HEAVY',
    Light: 'LIGHT',
    Medium: 'MEDIUM'
} as const;

export const NotificationType = {
    SUCCESS: 'SUCCESS',
    WARNING: 'WARNING',
    ERROR: 'ERROR'
} as const;


const canUseHaptics = async (): Promise<boolean> => {
    if (!Capacitor.isNativePlatform()) return false;
    try {
        const settings = await storageService.get<Settings>('yourprime-settings');
        // Default to true if settings are not found or property is missing
        return settings?.hapticFeedbackEnabled ?? true;
    } catch {
        return true; // Failsafe
    }
}

// Function to trigger a haptic impact feedback
export const hapticImpact = async (style: CapImpactStyle = ImpactStyle.Light) => {
  if (!await canUseHaptics()) return;
  try {
    const { Haptics } = await import('@capacitor/haptics');
    await Haptics.impact({ style });
  } catch (error) {
    console.warn("Haptics not available on this device.", error);
  }
};

// Function to trigger a haptic notification feedback
export const hapticNotification = async (type: CapNotificationType) => {
  if (!await canUseHaptics()) return;
  try {
    const { Haptics } = await import('@capacitor/haptics');
    await Haptics.notification({ type });
  } catch (error) {
    console.warn("Haptics notification not available on this device.", error);
  }
};

// Function to trigger a haptic feedback for selection changes
export const hapticSelection = async () => {
  if (!await canUseHaptics()) return;
  try {
    const { Haptics } = await import('@capacitor/haptics');
    await Haptics.selectionStart();
  } catch (error) {
    console.warn("Haptics selection not available on this device.", error);
  }
};