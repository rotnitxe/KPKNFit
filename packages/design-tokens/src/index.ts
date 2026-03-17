// ─── M3 Color Token Interface ────────────────────────────────────────────────
export interface KPKNColorTokens {
  primary: string; onPrimary: string;
  primaryContainer: string; onPrimaryContainer: string;
  secondary: string; onSecondary: string;
  secondaryContainer: string; onSecondaryContainer: string;
  tertiary: string; onTertiary: string;
  tertiaryContainer: string; onTertiaryContainer: string;
  error: string; errorContainer: string;
  background: string; onBackground: string;
  surface: string; onSurface: string;
  surfaceVariant: string; onSurfaceVariant: string;
  surfaceContainer: string; surfaceContainerHigh: string;
  outline: string; outlineVariant: string;
  batteryHigh: string; batteryMid: string; batteryLow: string;
  ringCns: string; ringMuscular: string; ringSpinal: string;
  cyberCanvas: string; cyberCard: string; cyberBorder: string;
  cyberCyan: string; cyberCopper: string; cyberSuccess: string;
  cyberWarning: string; cyberDanger: string;
}
// ─── M3 Typography ───────────────────────────────────────────────────────────
export interface TypeStyle {
  fontSize: number;
  lineHeight: number;
  fontWeight: '400' | '500' | '600' | '700';
  letterSpacing: number;
}
export const typography: Record<string, TypeStyle> = {
  'display-large':  { fontSize: 57, lineHeight: 64, fontWeight: '400', letterSpacing: -0.25 },
  'display-medium': { fontSize: 45, lineHeight: 52, fontWeight: '400', letterSpacing: 0 },
  'display-small':  { fontSize: 36, lineHeight: 44, fontWeight: '400', letterSpacing: 0 },
  'headline-large': { fontSize: 32, lineHeight: 40, fontWeight: '400', letterSpacing: 0 },
  'headline-medium':{ fontSize: 28, lineHeight: 36, fontWeight: '400', letterSpacing: 0 },
  'headline-small': { fontSize: 24, lineHeight: 32, fontWeight: '400', letterSpacing: 0 },
  'title-large':    { fontSize: 22, lineHeight: 28, fontWeight: '500', letterSpacing: 0 },
  'title-medium':   { fontSize: 16, lineHeight: 24, fontWeight: '500', letterSpacing: 0.15 },
  'title-small':    { fontSize: 14, lineHeight: 20, fontWeight: '500', letterSpacing: 0.1 },
  'body-large':     { fontSize: 16, lineHeight: 24, fontWeight: '400', letterSpacing: 0.5 },
  'body-medium':    { fontSize: 14, lineHeight: 20, fontWeight: '400', letterSpacing: 0.25 },
  'body-small':     { fontSize: 12, lineHeight: 16, fontWeight: '400', letterSpacing: 0.4 },
  'label-large':    { fontSize: 14, lineHeight: 20, fontWeight: '500', letterSpacing: 0.1 },
  'label-medium':   { fontSize: 12, lineHeight: 16, fontWeight: '500', letterSpacing: 0.5 },
  'label-small':    { fontSize: 12, lineHeight: 16, fontWeight: '500', letterSpacing: 0.5 },
};
// ─── M3 Shapes ───────────────────────────────────────────────────────────────
export const shapes = {
  none: 0,
  extraSmall: 4,
  small: 8,
  medium: 12,
  large: 16,
  extraLarge: 24,
  full: 9999,
} as const;
// ─── Spacing ─────────────────────────────────────────────────────────────────
export const spacing = {
  xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32,
} as const;
// ─── Shadows ─────────────────────────────────────────────────────────────────
export const shadows = {
  soft: {
    shadowColor: '#000000',
    shadowOpacity: 0.16,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 10 },
    elevation: 8,
  },
} as const;
// ─── Legacy exports (backward compat) ────────────────────────────────────────
// Old code imports `colors` from design-tokens — keep it working
export const colors = {
  background: '#090B12',
  surface: '#121622',
  surfaceElevated: '#1A2030',
  border: 'rgba(255,255,255,0.08)',
  textPrimary: '#F5F7FF',
  textSecondary: '#A7B0C3',
  textPlaceholder: '#6E7891',
  brand: '#00F0FF',
  brandMuted: 'rgba(0,240,255,0.12)',
  success: '#32D583',
  warning: '#F5B942',
  danger: '#F97066',
} as const;
// Keep old `radii` export
export const radii = {
  sm: 10, md: 16, lg: 24, pill: 999,
} as const;
