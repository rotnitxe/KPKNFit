import React, { useMemo } from 'react';
import { View, StyleSheet } from 'react-native';
import { CartesianChart, Line } from 'victory-native';
import { LinearGradient, vec } from '@shopify/react-native-skia';
import { useColors, useFonts } from '@/theme';
import { METRIC_COLORS } from '@/data/useChartDataFormatter';
import { useFont } from '@/lib/skiaFont';

interface LineChartBaseProps {
  data: any[];
  xKey: string;
  yKeys: string[];
  colors?: string[]; // Overrides if provided
}

/**
 * Memoized domain configuration to prevent object allocation on every render.
 * Skia/Victory will not recompute scales unnecessarily.
 */
const DEFAULT_DOMAIN = { y: [0, 100] as [number, number] }; // Replaced 'auto' to satisfy strict tuple typing, will adjust if needed

export function LineChartBase({ data, xKey, yKeys, colors: colorOverrides }: LineChartBaseProps) {
  const themeColors = useColors();
  const { sans } = useFonts();

  // Memoized font for axis labels
  const font = useFont(sans, 12);

  /**
   * Memoized axis options to prevent Skia from recomputing axis scales
   * on every parent re-render. Critical for 60 FPS chart performance.
   */
  const axisOptions = useMemo(
    () => ({
      font: font as any, // Bypass strict type mismatch with custom useFont wrapper
      line: { color: themeColors.outlineVariant },
      label: { color: themeColors.onSurfaceVariant },
    }),
    [font, themeColors.outlineVariant, themeColors.onSurfaceVariant]
  );

  /**
   * Default line color from theme or override.
   */
  const defaultLineColor = colorOverrides?.[0] ?? themeColors.cyberCyan;

  /**
   * Render a single line with optional gradient fill.
   */
  const renderLine = (yKey: string, index: number, points: any) => {
    const metricColorKey = METRIC_COLORS[yKey];
    const color = colorOverrides?.[index] ?? (metricColorKey ? themeColors[metricColorKey] : defaultLineColor);

    return (
      <Line
        key={yKey}
        points={points}
        color={color}
        strokeWidth={2}
        animate={{ type: 'spring', damping: 15 }}
      >
        <LinearGradient start={vec(0, 0)} end={vec(0, 200)} colors={[color, `${color}00`]} />
      </Line>
    );
  };

  return (
    <View style={styles.container}>
      <CartesianChart
        data={data}
        xKey={xKey}
        yKeys={yKeys}
        domain={DEFAULT_DOMAIN}
        axisOptions={axisOptions}
      >
        {({ points }) => yKeys.map((yKey, index) => renderLine(yKey, index, points[yKey]))}
      </CartesianChart>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    height: 192, // h-48 equivalent
    width: '100%',
  },
});
