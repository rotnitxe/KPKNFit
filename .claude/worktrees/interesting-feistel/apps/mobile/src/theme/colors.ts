/**
 * Pre-computed Material You color tokens for KPKN seed #00C8E0.
 * Generated from @material/material-color-utilities — hardcoded to avoid DOM dependency.
 */

export interface KPKNColorTokens {
  // 22 Core M3 tokens
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
  // Standard M3 error state token
  onError: string;
  // 6 KPKN semantic
  batteryHigh: string;
  batteryMid: string;
  batteryLow: string;
  ringCns: string;
  ringMuscular: string;
  ringSpinal: string;
  // Static cyber palette (brand-specific, doesn't change with theme)
  cyberCanvas: string;
  cyberCard: string;
  cyberBorder: string;
  cyberCyan: string;
  cyberCopper: string;
  cyberSuccess: string;
  cyberWarning: string;
  cyberDanger: string;
}

/** Color semilla principal: el cian KPKN */
export const KPKN_SEED_COLOR = '#00C8E0';

/**
 * DARK MODE: Cyberpunk-inspired Material You colors
 * Based on the PWA fallback values with M3-compliant structure
 */
export const DARK_COLORS: KPKNColorTokens = {
  // Core Material You (dark scheme)
  primary: '#00F0FF',
  onPrimary: '#000000',
  primaryContainer: 'rgba(0, 224, 238, 0.2)',
  onPrimaryContainer: '#00F0FF',
  secondary: '#FF7B00',
  onSecondary: '#000000',
  secondaryContainer: 'rgba(255, 123, 0, 0.2)',
  onSecondaryContainer: '#FF7B00',
  tertiary: '#A0A7B8',
  onTertiary: '#000000',
  tertiaryContainer: 'rgba(160, 167, 184, 0.2)',
  onTertiaryContainer: '#A0A7B8',
  error: '#FF2E43',
  errorContainer: 'rgba(255, 46, 67, 0.2)',
  background: '#0A0B0E',
  onBackground: '#FFFFFF',
  surface: '#15171E',
  onSurface: '#FFFFFF',
  surfaceVariant: '#1A1C24',
  onSurfaceVariant: '#A0A7B8',
  surfaceContainer: '#1A1C24',
  surfaceContainerHigh: '#1F212A',
  outline: '#4A4D58',
  outlineVariant: 'rgba(74, 77, 88, 0.5)',
  onError: '#000000',

  // KPKN semantic (dark mode)
  batteryHigh: '#10C984',   // fixed emerald green
  batteryMid: '#8B5CF6',    // violet-500 equivalent
  batteryLow: '#EF4444',    // red-500 equivalent
  ringCns: '#3B82F6',       // blue-500 equivalent
  ringMuscular: '#F59E0B',  // amber-500 equivalent
  ringSpinal: '#06B6D4',    // cyan-500 equivalent

  // Static cyber palette
  cyberCanvas: '#0A0B0E',
  cyberCard: '#15171E',
  cyberBorder: '#2A2D38',
  cyberCyan: '#00F0FF',
  cyberCopper: '#FF7B00',
  cyberSuccess: '#00FF9D',
  cyberWarning: '#FFD600',
  cyberDanger: '#FF2E43',
};

/**
 * WORKOUT THEME: The specific "Liquid Glass" Lavender palette from PWA.
 * Strictly 1:1 with Material 3 Dark tokens but using Lavender seed #D0BCFF.
 */
export const WORKOUT_DARK_COLORS: KPKNColorTokens = {
  ...DARK_COLORS, // Base defaults
  primary: '#D0BCFF',
  onPrimary: '#381E72',
  primaryContainer: '#4F378B',
  onPrimaryContainer: '#EADDFF',
  secondary: '#CCC2DC',
  onSecondary: '#332D41',
  secondaryContainer: '#4A4458',
  onSecondaryContainer: '#E8DEF8',
  tertiary: '#EFB8C8',
  onTertiary: '#492532',
  tertiaryContainer: '#633B48',
  onTertiaryContainer: '#FFD8E4',
  surface: '#1C1B1F',
  onSurface: '#E6E1E5',
  surfaceVariant: '#49454F',
  onSurfaceVariant: '#CAC4D0',
  outline: '#938F99',
  outlineVariant: '#44474E',
  surfaceContainer: '#211F26',
  surfaceContainerHigh: '#2B2930',
  // Glass Specials
  background: '#1C1B1F',
  onBackground: '#E6E1E5',
};

/**
 * LIGHT MODE: Light Material You colors derived from cyan seed
 */
export const LIGHT_COLORS: KPKNColorTokens = {
  // Core Material You (light scheme)
  primary: '#006B73',
  onPrimary: '#FFFFFF',
  primaryContainer: '#9DF1F7',
  onPrimaryContainer: '#001F22',
  secondary: '#4D5F15',
  onSecondary: '#FFFFFF',
  secondaryContainer: '#D2E9AA',
  onSecondaryContainer: '#0F2000',
  tertiary: '#4A607A',
  onTertiary: '#FFFFFF',
  tertiaryContainer: '#D1E4FF',
  onTertiaryContainer: '#001D35',
  error: '#BA1A1A',
  errorContainer: '#FFDAD6',
  background: '#FAFDFD',
  onBackground: '#191C1D',
  surface: '#FAFDFD',
  onSurface: '#191C1D',
  surfaceVariant: '#DBE4E8',
  onSurfaceVariant: '#3F484B',
  surfaceContainer: '#EEF2F4',
  surfaceContainerHigh: '#F4F8FA',
  outline: '#6F797C',
  outlineVariant: '#BFC8CC',
  onError: '#FFFFFF',

  // KPKN semantic (light mode)
  batteryHigh: '#0A8C5C',   // fixed emerald green
  batteryMid: '#A855F7',    // violet-500 equivalent
  batteryLow: '#DC2626',    // red-600 equivalent
  ringCns: '#2563EB',       // blue-600 equivalent
  ringMuscular: '#D97706',  // amber-600 equivalent
  ringSpinal: '#0891B2',    // cyan-600 equivalent

  // Static cyber palette (same in both modes)
  cyberCanvas: '#0A0B0E',
  cyberCard: '#15171E',
  cyberBorder: '#2A2D38',
  cyberCyan: '#00F0FF',
  cyberCopper: '#FF7B00',
  cyberSuccess: '#00FF9D',
  cyberWarning: '#FFD600',
  cyberDanger: '#FF2E43',
};

/**
 * Get color tokens for the specified theme mode
 */
export function getColors(dark: boolean, isWorkout?: boolean): KPKNColorTokens {
  if (isWorkout) return WORKOUT_DARK_COLORS;
  return dark ? DARK_COLORS : LIGHT_COLORS;
}