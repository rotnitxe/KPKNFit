
// services/soundService.ts
import { Capacitor } from '@capacitor/core';
import { TextToSpeech } from '@capacitor-community/text-to-speech';
import { NativeAudio } from '@capacitor-community/native-audio';
import { Settings } from '../types';
import { storageService } from './storageService';

// Audio element cache for Web Fallback
const audioElements: { [key: string]: HTMLAudioElement } = {};

const SOUND_FILES: { [key: string]: string } = {
  'rest-timer-sound': 'https://actions.google.com/sounds/v1/emergency/beeper_confirm.ogg',
  'set-logged-sound': 'https://actions.google.com/sounds/v1/switches/switch_toggle_on.ogg',
  'ui-click-sound': 'https://actions.google.com/sounds/v1/ui/ui_tap_forward.ogg',
  'tab-switch-sound': 'https://actions.google.com/sounds/v1/ui/ui_tap_reverse.ogg',
  'new-pr-sound': 'https://actions.google.com/sounds/v1/alarms/bugle_tune.ogg',
  'rep-surplus-sound': 'https://actions.google.com/sounds/v1/emergency/beeper_confirm.ogg',
  'session-complete-sound': 'https://actions.google.com/sounds/v1/alarms/alarm_clock.ogg',
};

// Map remote URLs to local asset paths for NativeAudio if available
// Assuming these files are placed in android/app/src/main/assets/public/assets/sounds/
const NATIVE_ASSET_PATHS: { [key: string]: string } = {
    'rest-timer-sound': 'beeper_confirm.mp3',
    'set-logged-sound': 'switch_toggle_on.mp3',
    'ui-click-sound': 'ui_tap_forward.mp3',
    'tab-switch-sound': 'ui_tap_reverse.mp3',
    'new-pr-sound': 'bugle_tune.mp3',
    'rep-surplus-sound': 'beeper_confirm.mp3',
    'session-complete-sound': 'alarm_clock.mp3',
};

let areSoundsPreloaded = false;

/**
 * Initializes and preloads sounds for low-latency playback.
 */
export const preloadSounds = async () => {
    if (areSoundsPreloaded) return;

    if (Capacitor.isNativePlatform()) {
        try {
            const promises = Object.entries(NATIVE_ASSET_PATHS).map(async ([id, path]) => {
                try {
                    // Unload first just in case
                    await NativeAudio.unload({ assetId: id }).catch(() => {});
                    await NativeAudio.preload({
                        assetId: id,
                        assetPath: path, // Path relative to assets folder
                        audioChannelNum: 1,
                        isUrl: false
                    });
                } catch (e) {
                    console.warn(`Failed to preload native sound ${id}:`, e);
                }
            });
            await Promise.all(promises);
            areSoundsPreloaded = true;
        } catch (e) {
            console.error("NativeAudio preload error:", e);
        }
    } else {
        // Web Preload
        Object.entries(SOUND_FILES).forEach(([id, url]) => {
            const audio = new Audio(url);
            audio.preload = 'auto';
            audioElements[id] = audio;
        });
        areSoundsPreloaded = true;
    }
};

/**
 * Plays a sound effect using NativeAudio (native) or HTML5 Audio (web).
 */
export const playSound = async (soundId: string) => {
  try {
    const settings = await storageService.get<Settings>('yourprime-settings');
    if (!settings?.soundsEnabled) return;

    if (Capacitor.isNativePlatform()) {
        try {
            await NativeAudio.play({ assetId: soundId });
        } catch (e) {
            // Fallback if native play fails (e.g. file not found)
             console.warn(`Native play failed for ${soundId}, trying web fallback.`);
             playWebSound(soundId);
        }
    } else {
        playWebSound(soundId);
    }
  } catch (e) {
    console.error(`Error playing sound "${soundId}":`, e);
  }
};

const playWebSound = (soundId: string) => {
    const soundUrl = SOUND_FILES[soundId];
    if (!soundUrl) return;

    let audio = audioElements[soundId];
    if (!audio) {
        audio = new Audio(soundUrl);
        audioElements[soundId] = audio;
    }

    if (!audio.paused) {
        audio.pause();
        audio.currentTime = 0;
    }

    const playPromise = audio.play();
    if (playPromise !== undefined) {
        playPromise.catch(error => {
            if (error.name !== 'NotSupportedError' && error.name !== 'NotAllowedError') {
                 console.error(`Error playing web sound "${soundId}":`, error);
            }
        });
    }
};

/**
 * Text-to-Speech that respects background music (Audio Ducking).
 */
export const speak = async (text: string, lang = 'es-ES') => {
    try {
        const settings = await storageService.get<Settings>('yourprime-settings');
        // Check setting if sounds explicitly disabled? Usually voice is separate, 
        // but let's assume it shares 'soundsEnabled' or a future 'voiceEnabled'.
        // For now, always speak if called explicitly.

        // Request Audio Focus / Ducking logic implies we are about to make noise.
        // The TextToSpeech plugin on Android usually handles ducking if category is correct.
        
        await TextToSpeech.stop(); // Stop previous speech

        await TextToSpeech.speak({
            text,
            lang,
            rate: 1.0,
            pitch: 1.0,
            volume: 1.0,
            category: 'ambient', // Critical for mixing/ducking on supported platforms
            queueStrategy: 1, // 0 = ADD, 1 = FLUSH (Stop current and speak new)
        });

    } catch (e) {
        console.error("TTS Error:", e);
        // Fallback to Web Speech API if plugin fails
        if ('speechSynthesis' in window) {
            window.speechSynthesis.cancel();
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = lang;
            window.speechSynthesis.speak(utterance);
        }
    }
};

/**
 * Configures the audio session for optimal mixing.
 * Useful to call when mounting voice components.
 */
export const configureAudioSession = async () => {
    if (Capacitor.isNativePlatform()) {
        // Prepare Native Audio engine
        preloadSounds();
        // Additional session config could go here if using a specific plugin for iOS AVAudioSession
    }
};

// Initialize on load
preloadSounds();
