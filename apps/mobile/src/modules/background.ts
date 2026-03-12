import { NativeModules } from 'react-native';
import type { BackgroundModuleContract } from '@kpkn/shared-types';

const nativeModule = NativeModules.KPKNBackground as BackgroundModuleContract | undefined;

export const backgroundModule: BackgroundModuleContract = nativeModule ?? {
  async schedulePeriodicSync() {
    return { scheduled: false };
  },
  async cancelPeriodicSync() {
    return { cancelled: false };
  },
  async runImmediateSync() {
    return { started: false };
  },
};
