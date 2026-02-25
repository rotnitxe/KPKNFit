export interface WidgetBridgeSetItemOptions {
  key: string;
  value: string;
}

export interface WidgetBridgeGetItemOptions {
  key: string;
}

export interface WidgetBridgeGetItemResult {
  value: string | null;
}

export interface WidgetBridgePlugin {
  setItem(options: WidgetBridgeSetItemOptions): Promise<void>;
  getItem(options: WidgetBridgeGetItemOptions): Promise<WidgetBridgeGetItemResult>;
  reloadWidget(): Promise<void>;
}
