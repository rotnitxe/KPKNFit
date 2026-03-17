/**
 * Material You Type Scale for React Native
 * Adapted from M3 typography guidelines
 */

export interface TypeStyle {
  fontSize: number;
  lineHeight: number;
  fontWeight: '400' | '500' | '600' | '700';
  letterSpacing: number;
}

/**
 * Complete Material You type scale
 * Font sizes in pixels, converted to React Native's density-independent units
 */
export const M3_TYPE_SCALE: Record<string, TypeStyle> = {
  'display-large':    { fontSize: 57, lineHeight: 64, fontWeight: '400', letterSpacing: -0.25 },
  'display-medium':   { fontSize: 45, lineHeight: 52, fontWeight: '400', letterSpacing: 0 },
  'display-small':    { fontSize: 36, lineHeight: 44, fontWeight: '400', letterSpacing: 0 },
  'headline-large':   { fontSize: 32, lineHeight: 40, fontWeight: '400', letterSpacing: 0 },
  'headline-medium':  { fontSize: 28, lineHeight: 36, fontWeight: '400', letterSpacing: 0 },
  'headline-small':   { fontSize: 24, lineHeight: 32, fontWeight: '400', letterSpacing: 0 },
  'title-large':      { fontSize: 22, lineHeight: 28, fontWeight: '500', letterSpacing: 0 },
  'title-medium':     { fontSize: 16, lineHeight: 24, fontWeight: '500', letterSpacing: 0.15 },
  'title-small':      { fontSize: 14, lineHeight: 20, fontWeight: '500', letterSpacing: 0.1 },
  'body-large':       { fontSize: 16, lineHeight: 24, fontWeight: '400', letterSpacing: 0.5 },
  'body-medium':      { fontSize: 14, lineHeight: 20, fontWeight: '400', letterSpacing: 0.25 },
  'body-small':       { fontSize: 12, lineHeight: 16, fontWeight: '400', letterSpacing: 0.4 },
  'label-large':      { fontSize: 14, lineHeight: 20, fontWeight: '500', letterSpacing: 0.1 },
  'label-medium':     { fontSize: 12, lineHeight: 16, fontWeight: '500', letterSpacing: 0.5 },
  'label-small':      { fontSize: 11, lineHeight: 16, fontWeight: '500', letterSpacing: 0.5 },
};

/** Font family constants for React Native */
export const FONT_SANS = 'Inter';
export const FONT_MONO = 'JetBrains Mono';
export const FONT_SYSTEM = 'System';

/**
 * Helper function to get a type style by name
 */
export function getTypeStyle(name: keyof typeof M3_TYPE_SCALE): TypeStyle {
  return M3_TYPE_SCALE[name];
}

/**
 * Helper function to create RN Text style from type scale
 */
export function createTextStyle(
  typeName: keyof typeof M3_TYPE_SCALE,
  overrides: Partial<TypeStyle> = {}
): TypeStyle {
  const baseStyle = getTypeStyle(typeName);
  return { ...baseStyle, ...overrides };
}