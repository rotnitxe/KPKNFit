export const colors = {
  background: '#090B12',
  surface: '#121622',
  surfaceElevated: '#1A2030',
  border: 'rgba(255,255,255,0.08)',
  textPrimary: '#F5F7FF',
  textSecondary: '#A7B0C3',
  brand: '#00F0FF',
  brandMuted: 'rgba(0,240,255,0.12)',
  success: '#32D583',
  warning: '#F5B942',
  danger: '#F97066',
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

export const radii = {
  sm: 10,
  md: 16,
  lg: 24,
  pill: 999,
} as const;

export const typography = {
  display: 32,
  title: 22,
  body: 16,
  caption: 13,
} as const;

export const shadows = {
  soft: {
    shadowColor: '#000000',
    shadowOpacity: 0.16,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 10 },
    elevation: 8,
  },
} as const;
