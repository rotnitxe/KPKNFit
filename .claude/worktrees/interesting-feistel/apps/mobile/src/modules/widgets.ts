import { NativeModules } from 'react-native';
import type { WidgetDashboardSnapshot, WidgetModuleContract } from '@kpkn/shared-types';

const nativeModule = NativeModules.KPKNWidgets as WidgetModuleContract | undefined;
export const isWidgetModuleAvailable = Boolean(nativeModule);

export const widgetModule: WidgetModuleContract = nativeModule ?? {
  async setItem() {
    return;
  },
  async reloadWidget() {
    return;
  },
  async syncDashboardState(_snapshot: WidgetDashboardSnapshot) {
    return;
  },
  async getStatus() {
    return {
      lastSyncAtMs: null,
      lastReloadAtMs: null,
      lastError: null,
      stale: true,
      staleReason: 'widget-module-unavailable',
      source: 'unknown',
    };
  },
  async markStale() {
    return;
  },
};
