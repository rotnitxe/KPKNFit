import React, { createContext, useContext, useMemo, useState, useCallback } from 'react';
import { useColorScheme as useRNColorScheme } from 'react-native';
import { getColors, KPKNColorTokens } from './colors';
import { M3_TYPE_SCALE, TypeStyle, FONT_SANS, FONT_MONO, FONT_SYSTEM } from './typography';
import { M3_SHAPES } from './shapes';

export interface KPKNTheme {
  dark: boolean;
  colors: KPKNColorTokens;
  typography: Record<string, TypeStyle>;
  shapes: typeof M3_SHAPES;
  fonts: { sans: string; mono: string; system: string };
}

interface ThemeContextValue {
  theme: KPKNTheme;
  colors: KPKNColorTokens;
  isDark: boolean;
  toggleDark: () => void;
  setDark: (dark: boolean) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

interface ThemeProviderProps {
  children: React.ReactNode;
  /** If true, follow system dark/light preference. Default: false (dark only for now) */
  followSystem?: boolean;
  /** Force initial dark mode. Default: true */
  initialDark?: boolean;
}

export function ThemeProvider({
  children,
  followSystem = false,
  initialDark = true
}: ThemeProviderProps) {
  const systemScheme = useRNColorScheme(); // 'dark' | 'light' | null
  const [darkOverride, setDarkOverride] = useState<boolean>(initialDark);

  const isDark = followSystem ? (systemScheme !== 'light') : darkOverride;

  const theme = useMemo<KPKNTheme>(() => ({
    dark: isDark,
    colors: getColors(isDark),
    typography: M3_TYPE_SCALE,
    shapes: M3_SHAPES,
    fonts: { sans: FONT_SANS, mono: FONT_MONO, system: FONT_SYSTEM },
  }), [isDark]);

  const toggleDark = useCallback(() => setDarkOverride(prev => !prev), []);
  const setDark = useCallback((dark: boolean) => setDarkOverride(dark), []);

  const value = useMemo(() => ({
    theme,
    colors: theme.colors,
    isDark,
    toggleDark,
    setDark
  }), [theme, isDark, toggleDark, setDark]);

  return (
    <ThemeContext.Provider value={value}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return ctx;
}

/** Shortcut: just get colors */
export function useColors(isWorkout?: boolean): KPKNColorTokens {
  const { isDark } = useTheme();
  return useMemo(() => getColors(isDark, isWorkout), [isDark, isWorkout]);
}

/** Shortcut: just get isDark */
export function useIsDark(): boolean {
  return useTheme().isDark;
}

/** Shortcut: just get typography */
export function useTypography(): Record<string, TypeStyle> {
  return useTheme().theme.typography;
}

/** Shortcut: just get shapes */
export function useShapes() {
  return useTheme().theme.shapes;
}

/** Shortcut: just get fonts */
export function useFonts() {
  return useTheme().theme.fonts;
}