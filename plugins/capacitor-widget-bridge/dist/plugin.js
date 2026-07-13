import { registerPlugin, WebPlugin } from '@capacitor/core';

class WidgetBridgeWeb extends WebPlugin {
    constructor() {
        super(...arguments);
        this.store = {};
    }
    async setItem(options) { this.store[options.key] = options.value; }
    async getItem(options) { return { value: this.store[options.key] ?? null }; }
    async reloadWidget() { }
}
const WidgetBridge = registerPlugin('WidgetBridge', {
    web: () => Promise.resolve(new WidgetBridgeWeb()),
});

export { WidgetBridge };
//# sourceMappingURL=plugin.js.map
