import { registerPlugin, WebPlugin } from '@capacitor/core';
import type { WidgetBridgePlugin } from './definitions';

class WidgetBridgeWeb extends WebPlugin implements WidgetBridgePlugin {
  private store: Record<string, string> = {};
  async setItem(options: { key: string; value: string }) { this.store[options.key] = options.value; }
  async getItem(options: { key: string }) { return { value: this.store[options.key] ?? null }; }
  async reloadWidget() {}
}

const WidgetBridge = registerPlugin<WidgetBridgePlugin>('WidgetBridge', {
  web: () => Promise.resolve(new WidgetBridgeWeb()),
});

export * from './definitions';
export { WidgetBridge };
