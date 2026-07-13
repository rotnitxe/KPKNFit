import NativeHaptics from 'react-native-haptic-feedback';
import hapticsService, { triggerHaptic } from '../hapticsService';

describe('hapticsService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('uses safe defaults when trigger is called without options', () => {
    triggerHaptic('impactMedium');

    expect(NativeHaptics.trigger).toHaveBeenCalledWith('impactMedium', {
      enableVibrateFallback: true,
      ignoreAndroidSystemSettings: false,
    });
  });

  it('supports service object usage for compatibility', () => {
    hapticsService.trigger('notificationSuccess', { ignoreAndroidSystemSettings: true });

    expect(NativeHaptics.trigger).toHaveBeenCalledWith('notificationSuccess', {
      enableVibrateFallback: true,
      ignoreAndroidSystemSettings: true,
    });
  });
});
