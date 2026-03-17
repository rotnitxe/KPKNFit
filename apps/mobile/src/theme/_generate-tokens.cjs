const {
  argbFromHex,
  hexFromArgb,
  themeFromSourceColor,
  TonalPalette,
  Scheme,
} = require('@material/material-color-utilities');

// Color semilla de KPKN
const seedHex = '#00C8E0';
const sourceColor = argbFromHex(seedHex);
const theme = themeFromSourceColor(sourceColor);

// Función para generar tokens para un scheme
function generateTokens(scheme, dark) {
  const primaryPalette = theme.palettes.primary;
  const secondaryPalette = theme.palettes.secondary;
  const tertiaryPalette = theme.palettes.tertiary;
  const errorPalette = theme.palettes.error;
  const neutralPalette = theme.palettes.neutral;

  return {
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
    surfaceContainer: hexFromArgb(dark ? neutralPalette.tone(12) : neutralPalette.tone(94)),
    surfaceContainerHigh: hexFromArgb(dark ? neutralPalette.tone(17) : neutralPalette.tone(92)),
    outline: hexFromArgb(scheme.outline),
    outlineVariant: hexFromArgb(scheme.outlineVariant),

    // KPKN semánticos
    batteryHigh: dark ? '#10C984' : '#0A8C5C',
    batteryMid: hexFromArgb(dark ? tertiaryPalette.tone(70) : tertiaryPalette.tone(45)),
    batteryLow: hexFromArgb(dark ? errorPalette.tone(70) : errorPalette.tone(45)),
    ringCns: hexFromArgb(dark ? primaryPalette.tone(72) : primaryPalette.tone(40)),
    ringMuscular: hexFromArgb(dark ? secondaryPalette.tone(72) : secondaryPalette.tone(40)),
    ringSpinal: hexFromArgb(dark ? tertiaryPalette.tone(72) : tertiaryPalette.tone(40)),

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
}

// Generar para dark y light
const DARK_COLORS = generateTokens(theme.schemes.dark, true);
const LIGHT_COLORS = generateTokens(theme.schemes.light, false);

// Imprimir como TypeScript
console.log('export const DARK_COLORS = {');
Object.entries(DARK_COLORS).forEach(([key, value]) => {
  console.log(`  ${key}: '${value}',`);
});
console.log('};\n');

console.log('export const LIGHT_COLORS = {');
Object.entries(LIGHT_COLORS).forEach(([key, value]) => {
  console.log(`  ${key}: '${value}',`);
});
console.log('};');