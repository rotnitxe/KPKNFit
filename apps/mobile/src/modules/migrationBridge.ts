import { NativeModules } from 'react-native';
import type { MigrationBridgeContract } from '@kpkn/shared-types';

const nativeBridge = NativeModules.KPKNMigrationBridge as MigrationBridgeContract | undefined;
export const isMigrationBridgeAvailable = Boolean(nativeBridge);

export const migrationBridge: MigrationBridgeContract = nativeBridge ?? {
  async readMigrationSnapshot() {
    return null;
  },
  async markMigrationComplete() {
    return;
  },
};
