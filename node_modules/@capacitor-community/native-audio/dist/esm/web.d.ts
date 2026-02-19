import { WebPlugin } from '@capacitor/core';
import type { ConfigureOptions, PreloadOptions, NativeAudio } from './definitions';
export declare class NativeAudioWeb extends WebPlugin implements NativeAudio {
    private static readonly FILE_LOCATION;
    private static readonly AUDIO_ASSET_BY_ASSET_ID;
    resume(options: {
        assetId: string;
    }): Promise<void>;
    pause(options: {
        assetId: string;
    }): Promise<void>;
    getCurrentTime(options: {
        assetId: string;
    }): Promise<{
        currentTime: number;
    }>;
    getDuration(options: {
        assetId: string;
    }): Promise<{
        duration: number;
    }>;
    configure(options: ConfigureOptions): Promise<void>;
    preload(options: PreloadOptions): Promise<void>;
    play(options: {
        assetId: string;
        time?: number;
    }): Promise<void>;
    loop(options: {
        assetId: string;
    }): Promise<void>;
    stop(options: {
        assetId: string;
    }): Promise<void>;
    unload(options: {
        assetId: string;
    }): Promise<void>;
    setVolume(options: {
        assetId: string;
        volume: number;
    }): Promise<void>;
    isPlaying(options: {
        assetId: string;
    }): Promise<{
        isPlaying: boolean;
    }>;
    private getAudioAsset;
    private checkAssetId;
}
