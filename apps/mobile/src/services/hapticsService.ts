import ReactNativeHapticFeedback, { HapticFeedbackTypes, type HapticOptions } from 'react-native-haptic-feedback';

type HapticType = keyof typeof HapticFeedbackTypes | HapticFeedbackTypes;
type HapticTriggerOptions = HapticOptions;

const DEFAULT_OPTIONS: HapticTriggerOptions = {
  enableVibrateFallback: true,
  ignoreAndroidSystemSettings: false,
};

export function triggerHaptic(type: HapticType = 'impactLight', options?: HapticTriggerOptions) {
  const mergedOptions: HapticTriggerOptions = {
    ...DEFAULT_OPTIONS,
    ...options,
  };

  try {
    ReactNativeHapticFeedback.trigger(type, mergedOptions);
  } catch (error) {
    console.warn('[haptics] No se pudo disparar feedback haptico.', error);
  }
}

const hapticsService = {
  trigger: triggerHaptic,
};

export default hapticsService;
