'use strict';

var core = require('@capacitor/core');

class WidgetBridgeWeb extends core.WebPlugin {
    constructor() {
        super(...arguments);
        this.store = {};
    }
    async setItem(options) { this.store[options.key] = options.value; }
    async getItem(options) { return { value: this.store[options.key] ?? null }; }
    async reloadWidget() { }
}
const WidgetBridge = core.registerPlugin('WidgetBridge', {
    web: () => Promise.resolve(new WidgetBridgeWeb()),
});

exports.WidgetBridge = WidgetBridge;
//# sourceMappingURL=plugin.cjs.js.map
