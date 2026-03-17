import React, { useMemo } from 'react';
import { View, StyleSheet } from 'react-native';
import { CartesianChart, Bar } from 'victory-native';
import { useColors, useFonts } from '@/theme';
import { METRIC_COLORS } from '@/data/useChartDataFormatter';
import { useFont } from '@/lib/skiaFont';

interface BarChartBaseProps {
  data: any[];
  xKey: string;
  yKeys: string[];
  colors?: string[];
}

/**
 * Memoized domain configuration to prevent object allocation on every render.
 * Skia/Victory will not recompute scales unnecessarily.
 */
const DEFAULT_DOMAIN = { y: [0, 100] as [number, number] };

export function BarChartBase({ data, xKey, yKeys, colors: colorOverrides }: BarChartBaseProps) {
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
      font: font as any,
      line: { color: themeColors.outlineVariant },
      label: { color: themeColors.onSurfaceVariant },
    }),
    [font, themeColors.outlineVariant, themeColors.onSurfaceVariant]
  );

  /**
   * Default bar color from theme or override.
   */
  const defaultColor = colorOverrides?.[0] ?? themeColors.cyberCyan;

  /**
   * Render a single bar series with rounded corners.
   */
  const renderBar = (yKey: string, index: number, points: any, chartBounds: any) => {
    const metricColorKey = METRIC_COLORS[yKey];
    const color = colorOverrides?.[index] ?? (metricColorKey ? themeColors[metricColorKey] : defaultColor);

    return (
      <Bar
        key={yKey}
        points={points}
        chartBounds={chartBounds}
        color={color}
        roundedCorners={{ topLeft: 4, topRight: 4 }}
        animate={{ type: 'spring', damping: 15 }}
      />
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
        {({ points, chartBounds }) => yKeys.map((yKey, index) => renderBar(yKey, index, points[yKey], chartBounds))}
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
