import { registerPlugin } from '@capacitor/core';
const NativeAudio = registerPlugin('NativeAudio', {
    web: () => import('./web').then((m) => new m.NativeAudioWeb()),
});
export * from './definitions';
export { NativeAudio };
//# sourceMappingURL=index.js.map