import React, { useMemo, useState, useCallback } from 'react';
import { View, Text, StyleSheet, LayoutChangeEvent } from 'react-native';
import { Canvas, RoundedRect, Text as SkiaText } from '@shopify/react-native-skia';
import { useColors, useFonts } from '@/theme';
import { useFont } from '@/lib/skiaFont';

export interface BarTrendPoint {
  key: string;
  label: string;
  value: number;
  highlight?: boolean;
}

interface BarTrendChartProps {
  data: BarTrendPoint[];
  height?: number;
  testID?: string;
}

const PADDING = { left: 10, right: 10, top: 20, bottom: 20 };
const BAR_GAP = 8;

export const BarTrendChart: React.FC<BarTrendChartProps> = ({
  data,
  height = 160,
  testID,
}) => {
  const colors = useColors();
  const { sans } = useFonts();
  const font = useFont(sans, 9);
  const valueFont = useFont(sans, 10);

  const [width, setWidth] = useState(0);

  const onLayout = useCallback((event: LayoutChangeEvent) => {
    setWidth(event.nativeEvent.layout.width);
  }, []);

  /**
   * Memoized bar calculations to prevent recomputation on every render.
   */
  const barsData = useMemo(() => {
    if (!data || data.length === 0 || width === 0) {
      return null;
    }

    const max = Math.max(1, ...data.map((p) => p.value));
    const chartWidth = width - PADDING.left - PADDING.right;
    const chartHeight = height - PADDING.top - PADDING.bottom;

    const barWidth = (chartWidth / data.length) - BAR_GAP;

    return data.map((p, i) => {
      const barHeight = (p.value / max) * chartHeight;
      const x = PADDING.left + (i / data.length) * chartWidth + BAR_GAP / 2;
      const y = height - PADDING.bottom - barHeight;

      // Show label for sparse datasets or first/middle/last bars
      const showLabel =
        data.length < 8 ||
        i === 0 ||
        i === data.length - 1 ||
        i === Math.floor(data.length / 2);

      return {
        x,
        y,
        width: barWidth,
        height: barHeight,
        label: p.label,
        value: p.value,
        highlight: p.highlight ?? false,
        showLabel,
      };
    });
  }, [data, width, height]);

  if (!data || data.length === 0) {
    return (
      <View
        testID={testID}
        style={[styles.emptyContainer, { height }]}
      >
        <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
          Sin datos para mostrar
        </Text>
      </View>
    );
  }

  return (
    <View onLayout={onLayout} style={styles.chartContainer} testID={testID}>
      {width > 0 && barsData && (
        <Canvas style={{ width, height }}>
          {barsData.map((bar, index) => (
            <React.Fragment key={index}>
              <RoundedRect
                x={bar.x}
                y={bar.y}
                width={bar.width}
                height={bar.height}
                r={4}
                color={
                  bar.highlight ? colors.primary : (colors.surfaceContainerHigh ?? 'rgba(255,255,255,0.1)')
                }
              />

              {/* Numeric value above highlighted bar */}
              {bar.highlight && (
                <SkiaText
                  x={bar.x + bar.width / 2}
                  y={bar.y - 6}
                  text={Math.round(bar.value).toString()}
                  font={valueFont as any}
                  color={colors.primary}
                />
              )}

              {/* Label below bar */}
              {bar.showLabel && (
                <SkiaText
                  x={bar.x + bar.width / 2}
                  y={height - 4}
                  text={bar.label}
                  font={font as any}
                  color={colors.onSurfaceVariant}
                />
              )}
            </React.Fragment>
          ))}
        </Canvas>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  chartContainer: {
    width: '100%',
  },
  emptyContainer: {
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
  },
  emptyText: {
    fontSize: 14,
  },
});
