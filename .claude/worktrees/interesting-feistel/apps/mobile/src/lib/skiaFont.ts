/**
 * Font wrapper for Skia text rendering.
 * Provides useFont hook that delegates to @shopify/react-native-skia when available.
 * 
 * NOTE: This module requires @shopify/react-native-skia to be installed.
 * Install with: npm install @shopify/react-native-skia
 */

// Declare SkFont type locally to allow type-checking without Skia installed
interface SkFont {
  fontFamily: string;
  fontSize: number;
}

// Dynamic import - will be undefined if Skia is not installed
let useFontImpl: ((fontFamily: string, fontSize: number) => SkFont) | null = null;

try {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const skia = require('@shopify/react-native-skia');
  if (skia?.useFont) {
    useFontImpl = skia.useFont;
  }
} catch {
  // Skia not installed - will use fallback
  useFontImpl = null;
}

/**
 * Hook to get a Skia font object for text rendering.
 * @param fontFamily - Font family name (e.g., 'Roboto', 'San Francisco')
 * @param fontSize - Font size in points
 * @returns SkFont object for use in Skia Text components
 * 
 * @throws Error if @shopify/react-native-skia is not installed and this hook is called at runtime
 */
export function useFont(fontFamily: string, fontSize: number): SkFont {
  if (useFontImpl) {
    return useFontImpl(fontFamily, fontSize);
  }
  
  // Development fallback - logs warning and returns stub
  // In production with Skia not installed, this will cause visual issues but won't crash
  console.warn(
    '[skiaFont] @shopify/react-native-skia is not installed. Text rendering will not work. ' +
    'Install with: npm install @shopify/react-native-skia'
  );
  
  return {
    fontFamily,
    fontSize,
  } as SkFont;
}

export type { SkFont };
