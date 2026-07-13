/**
 * Material You Shape Scale for React Native
 * Border radius values adapted for mobile
 */

export const M3_SHAPES = {
  none: 0,
  extraSmall: 4,
  small: 8,
  medium: 12,
  large: 16,
  extraLarge: 24,
  full: 9999, // Large enough to create fully rounded corners
} as const;

export type ShapeScale = keyof typeof M3_SHAPES;

/**
 * Get border radius value by shape name
 */
export function getShapeRadius(shape: ShapeScale): number {
  return M3_SHAPES[shape];
}

/**
 * Helper function to create RN View style with shape
 */
export function createShapeStyle(shape: ShapeScale) {
  return {
    borderRadius: getShapeRadius(shape),
  };
}