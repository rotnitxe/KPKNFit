import { NativeModules } from 'react-native';
import type { WidgetDashboardSnapshot, WidgetModuleContract } from '@kpkn/shared-types';

const nativeModule = NativeModules.KPKNWidgets as WidgetModuleContract | undefined;

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
};
