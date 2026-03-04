/**
 * KPKN Fit — Material 3 Typography Scale
 * Equivalente a Typography.kt en Android, traducido a CSS variables.
 *
 * Usa fuentes del sistema (system-ui) como base, con Inter como upgrade
 * si está disponible. Respeta las 5 escalas M3:
 *   Display → Headline → Title → Body → Label
 */

// ─── Type Scale Definitions ──────────────────────────────────────────────────

export interface TypeStyle {
    fontSize: string;
    lineHeight: string;
    fontWeight: number;
    letterSpacing: string;
}

// M3 Type Scale — Professional / Energético
// Ref: https://m3.material.io/styles/typography/type-scale-tokens
export const M3_TYPE_SCALE: Record<string, TypeStyle> = {
    // Display: Numerales grandes, datos hero (ej: porcentajes de batería)
    'display-large': { fontSize: '57px', lineHeight: '64px', fontWeight: 400, letterSpacing: '-0.25px' },
    'display-medium': { fontSize: '45px', lineHeight: '52px', fontWeight: 400, letterSpacing: '0px' },
    'display-small': { fontSize: '36px', lineHeight: '44px', fontWeight: 400, letterSpacing: '0px' },

    // Headline: Títulos de sección (ej: "Batería Muscular")
    'headline-large': { fontSize: '32px', lineHeight: '40px', fontWeight: 400, letterSpacing: '0px' },
    'headline-medium': { fontSize: '28px', lineHeight: '36px', fontWeight: 400, letterSpacing: '0px' },
    'headline-small': { fontSize: '24px', lineHeight: '32px', fontWeight: 400, letterSpacing: '0px' },

    // Title: Subtítulos y encabezados de tarjetas
    'title-large': { fontSize: '22px', lineHeight: '28px', fontWeight: 500, letterSpacing: '0px' },
    'title-medium': { fontSize: '16px', lineHeight: '24px', fontWeight: 500, letterSpacing: '0.15px' },
    'title-small': { fontSize: '14px', lineHeight: '20px', fontWeight: 500, letterSpacing: '0.1px' },

    // Body: Texto de contenido, descripciones
    'body-large': { fontSize: '16px', lineHeight: '24px', fontWeight: 400, letterSpacing: '0.5px' },
    'body-medium': { fontSize: '14px', lineHeight: '20px', fontWeight: 400, letterSpacing: '0.25px' },
    'body-small': { fontSize: '12px', lineHeight: '16px', fontWeight: 400, letterSpacing: '0.4px' },

    // Label: Etiquetas de botones, chips, badges, tabs
    'label-large': { fontSize: '14px', lineHeight: '20px', fontWeight: 500, letterSpacing: '0.1px' },
    'label-medium': { fontSize: '12px', lineHeight: '16px', fontWeight: 500, letterSpacing: '0.5px' },
    'label-small': { fontSize: '12px', lineHeight: '16px', fontWeight: 500, letterSpacing: '0.5px' },
};

// ─── Font Stacks ──────────────────────────────────────────────────────────────

/** Fuente principal: Inter si disponible, fallback sistema */
export const FONT_SANS = "'Inter', 'SF Pro Display', system-ui, -apple-system, sans-serif";

/** Fuente de datos/numerales: JetBrains Mono para tabular nums */
export const FONT_MONO = "'JetBrains Mono', 'SF Mono', ui-monospace, monospace";

// ─── Inyector de CSS Variables ─────────────────────────────────────────────────

/**
 * Inyecta todas las variables tipográficas M3 en :root.
 * Llámalo una vez al iniciar la app (antes o después del tema de color).
 */
export function applyTypography(): void {
    const root = document.documentElement;

    // Font families
    root.style.setProperty('--md-sys-typescale-font-family-plain', FONT_SANS);
    root.style.setProperty('--md-sys-typescale-font-family-brand', FONT_SANS);
    root.style.setProperty('--md-sys-typescale-font-family-data', FONT_MONO);

    // Type scale tokens
    for (const [key, style] of Object.entries(M3_TYPE_SCALE)) {
        const prefix = `--md-sys-typescale-${key}`;
        root.style.setProperty(`${prefix}-size`, style.fontSize);
        root.style.setProperty(`${prefix}-line-height`, style.lineHeight);
        root.style.setProperty(`${prefix}-weight`, String(style.fontWeight));
        root.style.setProperty(`${prefix}-tracking`, style.letterSpacing);
    }

    // Aplicar font-family global al body
    document.body.style.fontFamily = FONT_SANS;
}
