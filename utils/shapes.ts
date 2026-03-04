/**
 * KPKN Fit — Material 3 Shape System
 * Equivalente a Shapes.kt en Android, traducido a CSS variables.
 *
 * Define los 5 niveles de esquinas redondeadas de M3:
 *   Extra Small → Small → Medium → Large → Extra Large
 *
 * Componentes grandes (modales, sheets, cards hero) usan 24dp/28dp.
 * Componentes pequeños (chips, badges) usan 8dp.
 */

// ─── Shape Definitions ───────────────────────────────────────────────────────

export interface ShapeTokens {
    none: string;
    extraSmall: string;
    small: string;
    medium: string;
    large: string;
    extraLarge: string;
    full: string;
}

// M3 Shape Scale — Custom para KPKN (estilo profesional, esquinas amplias)
export const M3_SHAPES: ShapeTokens = {
    none: '0px',
    extraSmall: '4px',   // Inputs, badges mínimos
    small: '8px',   // Chips, toggles, small buttons
    medium: '12px',  // Cards estándar, list items
    large: '16px',  // Modales de tamaño medio, FABs
    extraLarge: '24px',  // Bottom sheets, modales grandes, cards hero
    full: '9999px', // Pills, avatares circulares
};

// ─── Inyector de CSS Variables ─────────────────────────────────────────────────

/**
 * Inyecta las variables de forma M3 en :root.
 * Llámalo una vez al iniciar la app.
 */
export function applyShapes(): void {
    const root = document.documentElement;

    root.style.setProperty('--md-sys-shape-corner-none', M3_SHAPES.none);
    root.style.setProperty('--md-sys-shape-corner-extra-small', M3_SHAPES.extraSmall);
    root.style.setProperty('--md-sys-shape-corner-small', M3_SHAPES.small);
    root.style.setProperty('--md-sys-shape-corner-medium', M3_SHAPES.medium);
    root.style.setProperty('--md-sys-shape-corner-large', M3_SHAPES.large);
    root.style.setProperty('--md-sys-shape-corner-extra-large', M3_SHAPES.extraLarge);
    root.style.setProperty('--md-sys-shape-corner-full', M3_SHAPES.full);
}
