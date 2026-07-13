/**
 * KPKN Fit — Material You Theme Engine
 * Genera tokens de color dinámicos a partir de un color semilla,
 * usando @material/material-color-utilities (la misma base que Android 13+).
 *
 * Uso:
 *   applyTheme('#00F0FF')              → aplica el tema con el cian de KPKN
 *   applyTheme(await extractSeedFromImage(img)) → extrae color de una imagen
 */

import {
    argbFromHex,
    hexFromArgb,
    themeFromSourceColor,
    TonalPalette,
    Scheme,
} from '@material/material-color-utilities';

// ─── Tipos ───────────────────────────────────────────────────────────────────

export interface KPKNThemeTokens {
    // Core Material You
    primary: string;
    onPrimary: string;
    primaryContainer: string;
    onPrimaryContainer: string;
    secondary: string;
    onSecondary: string;
    secondaryContainer: string;
    onSecondaryContainer: string;
    tertiary: string;
    onTertiary: string;
    tertiaryContainer: string;
    onTertiaryContainer: string;
    error: string;
    errorContainer: string;
    // Surfaces
    background: string;
    onBackground: string;
    surface: string;
    onSurface: string;
    surfaceVariant: string;
    onSurfaceVariant: string;
    surfaceContainer: string;
    surfaceContainerHigh: string;
    outline: string;
    outlineVariant: string;
    // KPKN Semánticos (derivados de la paleta)
    batteryHigh: string;
    batteryMid: string;
    batteryLow: string;
    ringCns: string;
    ringMuscular: string;
    ringSpinal: string;
}

// ─── Colores semilla por defecto de KPKN ────────────────────────────────────

/** Color semilla principal: el cian KPKN */
export const KPKN_SEED_COLOR = '#00C8E0';

// ─── Generador de tema ────────────────────────────────────────────────────────

/**
 * Dado un color hexadecimal semilla, genera todas las variables CSS de
 * Material You y las inyecta en :root. Llámalo una vez al iniciar la app.
 */
export function applyTheme(seedHex: string, dark: boolean = false): KPKNThemeTokens {
    const sourceColor = argbFromHex(seedHex);
    const theme = themeFromSourceColor(sourceColor);

    const scheme: Scheme = dark ? theme.schemes.dark : theme.schemes.light;

    // Extraer paleta personalizada para colores KPKN específicos
    // Usamos la paleta tonal para generar los colores de rings y batería
    const primaryPalette = theme.palettes.primary;
    const secondaryPalette = theme.palettes.secondary;
    const tertiaryPalette = theme.palettes.tertiary;
    const errorPalette = theme.palettes.error;
    const neutralPalette = theme.palettes.neutral;

    // Tonos para modo oscuro (tonos más altos = más claro)
    const tokens: KPKNThemeTokens = {
        primary: hexFromArgb(scheme.primary),
        onPrimary: hexFromArgb(scheme.onPrimary),
        primaryContainer: hexFromArgb(scheme.primaryContainer),
        onPrimaryContainer: hexFromArgb(scheme.onPrimaryContainer),
        secondary: hexFromArgb(scheme.secondary),
        onSecondary: hexFromArgb(scheme.onSecondary),
        secondaryContainer: hexFromArgb(scheme.secondaryContainer),
        onSecondaryContainer: hexFromArgb(scheme.onSecondaryContainer),
        tertiary: hexFromArgb(scheme.tertiary),
        onTertiary: hexFromArgb(scheme.onTertiary),
        tertiaryContainer: hexFromArgb(scheme.tertiaryContainer),
        onTertiaryContainer: hexFromArgb(scheme.onTertiaryContainer),
        error: hexFromArgb(scheme.error),
        errorContainer: hexFromArgb(scheme.errorContainer),
        background: hexFromArgb(scheme.background),
        onBackground: hexFromArgb(scheme.onBackground),
        surface: hexFromArgb(scheme.surface),
        onSurface: hexFromArgb(scheme.onSurface),
        surfaceVariant: hexFromArgb(scheme.surfaceVariant),
        onSurfaceVariant: hexFromArgb(scheme.onSurfaceVariant),
        surfaceContainer: hexFromArgb(dark
            ? neutralPalette.tone(12)
            : neutralPalette.tone(94)),
        surfaceContainerHigh: hexFromArgb(dark
            ? neutralPalette.tone(17)
            : neutralPalette.tone(92)),
        outline: hexFromArgb(scheme.outline),
        outlineVariant: hexFromArgb(scheme.outlineVariant),

        // ── KPKN Semánticos ──────────────────────────────────────────────────
        // Batería: verde → ámbar → rojo (usando error-palette y success custom)
        batteryHigh: dark ? '#10C984' : '#0A8C5C',   // verde esmeralda fijo (no varía con semilla)
        batteryMid: hexFromArgb(dark ? tertiaryPalette.tone(70) : tertiaryPalette.tone(45)),
        batteryLow: hexFromArgb(dark ? errorPalette.tone(70) : errorPalette.tone(45)),

        // Rings: derivados de primary y secondary  
        ringCns: hexFromArgb(dark ? primaryPalette.tone(72) : primaryPalette.tone(40)),
        ringMuscular: hexFromArgb(dark ? secondaryPalette.tone(72) : secondaryPalette.tone(40)),
        ringSpinal: hexFromArgb(dark ? tertiaryPalette.tone(72) : tertiaryPalette.tone(40)),
    };

    // ── Inyectar en CSS ──────────────────────────────────────────────────────
    injectCSSVariables(tokens, dark);

    return tokens;
}

// ─── Inyector de Variables CSS ───────────────────────────────────────────────

function injectCSSVariables(tokens: KPKNThemeTokens, dark: boolean): void {
    const root = document.documentElement;

    // Material You tokens
    root.style.setProperty('--md-sys-color-primary', tokens.primary);
    root.style.setProperty('--md-sys-color-on-primary', tokens.onPrimary);
    root.style.setProperty('--md-sys-color-primary-container', tokens.primaryContainer);
    root.style.setProperty('--md-sys-color-on-primary-container', tokens.onPrimaryContainer);
    root.style.setProperty('--md-sys-color-secondary', tokens.secondary);
    root.style.setProperty('--md-sys-color-on-secondary', tokens.onSecondary);
    root.style.setProperty('--md-sys-color-secondary-container', tokens.secondaryContainer);
    root.style.setProperty('--md-sys-color-on-secondary-container', tokens.onSecondaryContainer);
    root.style.setProperty('--md-sys-color-tertiary', tokens.tertiary);
    root.style.setProperty('--md-sys-color-on-tertiary', tokens.onTertiary);
    root.style.setProperty('--md-sys-color-tertiary-container', tokens.tertiaryContainer);
    root.style.setProperty('--md-sys-color-on-tertiary-container', tokens.onTertiaryContainer);
    root.style.setProperty('--md-sys-color-error', tokens.error);
    root.style.setProperty('--md-sys-color-error-container', tokens.errorContainer);
    root.style.setProperty('--md-sys-color-background', tokens.background);
    root.style.setProperty('--md-sys-color-on-background', tokens.onBackground);
    root.style.setProperty('--md-sys-color-surface', tokens.surface);
    root.style.setProperty('--md-sys-color-on-surface', tokens.onSurface);
    root.style.setProperty('--md-sys-color-surface-variant', tokens.surfaceVariant);
    root.style.setProperty('--md-sys-color-on-surface-variant', tokens.onSurfaceVariant);
    root.style.setProperty('--md-sys-color-surface-container', tokens.surfaceContainer);
    root.style.setProperty('--md-sys-color-surface-container-high', tokens.surfaceContainerHigh);
    root.style.setProperty('--md-sys-color-outline', tokens.outline);
    root.style.setProperty('--md-sys-color-outline-variant', tokens.outlineVariant);

    // KPKN semánticos
    root.style.setProperty('--kpkn-battery-high', tokens.batteryHigh);
    root.style.setProperty('--kpkn-battery-mid', tokens.batteryMid);
    root.style.setProperty('--kpkn-battery-low', tokens.batteryLow);
    root.style.setProperty('--kpkn-ring-cns', tokens.ringCns);
    root.style.setProperty('--kpkn-ring-muscular', tokens.ringMuscular);
    root.style.setProperty('--kpkn-ring-spinal', tokens.ringSpinal);

    // Modo light/dark en el atributo del documento
    root.setAttribute('data-theme', dark ? 'dark' : 'light');
}

// ─── Extractor de color desde imagen ─────────────────────────────────────────

/**
 * Extrae un color dominante de una imagen (URL o elemento <img>).
 * Útil para hacer el tema dinámico según el avatar/foto del usuario.
 * Devuelve el hex del color más representativo.
 */
export async function extractSeedFromImage(imageSource: string | HTMLImageElement): Promise<string> {
    return new Promise((resolve) => {
        const img = typeof imageSource === 'string' ? new Image() : imageSource;
        img.crossOrigin = 'anonymous';

        const process = () => {
            try {
                const canvas = document.createElement('canvas');
                const size = 64; // resolución reducida para eficiencia
                canvas.width = size;
                canvas.height = size;
                const ctx = canvas.getContext('2d')!;
                ctx.drawImage(img, 0, 0, size, size);

                const data = ctx.getImageData(0, 0, size, size).data;

                // Calcular color promedio ponderado (excluye blancos y negros puros)
                let r = 0, g = 0, b = 0, count = 0;
                for (let i = 0; i < data.length; i += 4) {
                    const pr = data[i], pg = data[i + 1], pb = data[i + 2];
                    const brightness = (pr + pg + pb) / 3;
                    // Ignorar píxeles muy oscuros o muy claros (fondo)
                    if (brightness > 20 && brightness < 235) {
                        r += pr; g += pg; b += pb; count++;
                    }
                }

                if (count === 0) { resolve(KPKN_SEED_COLOR); return; }

                const hex = `#${Math.round(r / count).toString(16).padStart(2, '0')}${Math.round(g / count).toString(16).padStart(2, '0')}${Math.round(b / count).toString(16).padStart(2, '0')}`;
                resolve(hex);
            } catch {
                resolve(KPKN_SEED_COLOR);
            }
        };

        if (typeof imageSource === 'string') {
            img.onload = process;
            img.onerror = () => resolve(KPKN_SEED_COLOR);
            img.src = imageSource;
        } else {
            process();
        }
    });
}

import { useState, useEffect, useRef } from 'react';
import { applyTypography } from './typography';
import { applyShapes } from './shapes';

export interface UseThemeOptions {
    /** Color semilla inicial. Default: KPKN cian */
    seedColor?: string;
    /** Seguir automáticamente el dark/light mode del sistema */
    followSystemDark?: boolean;
}

/**
 * Hook React que aplica el tema Material You completo:
 * Color (dinámico) + Tipografía M3 + Shapes M3.
 * Sincroniza automáticamente con prefers-color-scheme del sistema.
 */
export function useKPKNTheme(options: UseThemeOptions = {}) {
    const { seedColor = KPKN_SEED_COLOR, followSystemDark = false } = options;

    const [isDark, setIsDark] = useState(false);
    const [seed, setSeedState] = useState(seedColor);
    const [tokens, setTokens] = useState<KPKNThemeTokens | null>(null);
    const initialized = useRef(false);

    // Aplicar tipografía y shapes una sola vez al montar
    useEffect(() => {
        if (initialized.current) return;
        initialized.current = true;
        applyTypography();
        applyShapes();
    }, []);

    // Sincronizar con prefers-color-scheme del sistema
    useEffect(() => {
        if (!followSystemDark) return;
        const mq = window.matchMedia('(prefers-color-scheme: dark)');
        const handler = (e: MediaQueryListEvent) => setIsDark(e.matches);
        setIsDark(mq.matches);
        mq.addEventListener('change', handler);
        return () => mq.removeEventListener('change', handler);
    }, [followSystemDark]);

    // Aplicar tema cada vez que cambian el seed o el modo
    useEffect(() => {
        const t = applyTheme(seed, isDark);
        setTokens(t);
    }, [seed, isDark]);

    const setSeed = (newSeed: string) => setSeedState(newSeed);
    const toggleDark = () => setIsDark(prev => !prev);

    return { tokens, isDark, setSeed, toggleDark };
}

