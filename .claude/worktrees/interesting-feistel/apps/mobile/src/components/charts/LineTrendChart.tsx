import React, { useMemo, useState, useCallback } from 'react';
import { View, Text, StyleSheet, LayoutChangeEvent } from 'react-native';
import {
  Canvas,
  Path,
  Circle,
  Line,
  Text as SkiaText,
  Skia,
} from '@shopify/react-native-skia';
import { useColors, useFonts } from '@/theme';
import { useFont } from '@/lib/skiaFont';

export interface LineTrendPoint {
  key: string;
  label: string;
  value: number;
}

interface LineTrendChartProps {
  data: LineTrendPoint[];
  height?: number;
  strokeWidth?: number;
  testID?: string;
}

const PADDING = { left: 20, right: 12, top: 12, bottom: 22 };

export const LineTrendChart: React.FC<LineTrendChartProps> = ({
  data,
  height = 160,
  strokeWidth = 2.5,
  testID,
}) => {
  const colors = useColors();
  const { sans } = useFonts();
  const font = useFont(sans, 10);

  const [width, setWidth] = useState(0);

  const onLayout = useCallback((event: LayoutChangeEvent) => {
    setWidth(event.nativeEvent.layout.width);
  }, []);

  /**
   * Memoized chart calculations to prevent recomputation on every render.
   */
  const chartData = useMemo(() => {
    if (!data || data.length < 2 || width === 0) {
      return null;
    }

    const values = data.map((p) => p.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;

    const chartWidth = width - PADDING.left - PADDING.right;
    const chartHeight = height - PADDING.top - PADDING.bottom;

    // Calculate points
    const points = data.map((p, i) => {
      const x = PADDING.left + (i / (data.length - 1)) * chartWidth;
      const y = height - PADDING.bottom - ((p.value - min) / range) * chartHeight;
      return { x, y, label: p.label, value: p.value };
    });

    // Build Skia path for the trend line
    const path = Skia.Path.Make();
    if (points.length > 0) {
      path.moveTo(points[0].x, points[0].y);
      for (let i = 1; i < points.length; i++) {
        path.lineTo(points[i].x, points[i].y);
      }
    }

    const lastPoint = points[points.length - 1];
    const firstPoint = points[0];

    return { path, points, firstPoint, lastPoint, min, max };
  }, [data, width, height]);

  if (!data || data.length < 2) {
    return (
      <View
        testID={testID}
        style={[styles.emptyContainer, { height }]}
      >
        <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
          Datos insuficientes para tendencia
        </Text>
      </View>
    );
  }

  return (
    <View onLayout={onLayout} style={styles.chartContainer} testID={testID}>
      {width > 0 && chartData && (
        <Canvas style={{ width, height }}>
          {/* Base horizontal axis line */}
          <Line
            p1={{ x: PADDING.left, y: height - PADDING.bottom }}
            p2={{ x: width - PADDING.right, y: height - PADDING.bottom }}
            color={colors.outlineVariant}
            strokeWidth={1}
            opacity={0.3}
          />

          {/* Trend line */}
          <Path
            path={chartData.path}
            style="stroke"
            color={colors.primary}
            strokeWidth={strokeWidth}
            strokeCap="round"
            strokeJoin="round"
          />

          {/* Highlighted end point */}
          <Circle
            cx={chartData.lastPoint.x}
            cy={chartData.lastPoint.y}
            r={strokeWidth * 1.5}
            color={colors.primary}
          />

          {/* First label */}
          {chartData.firstPoint && (
            <SkiaText
              x={chartData.firstPoint.x}
              y={height - 4}
              text={chartData.firstPoint.label}
              font={font as any}
              color={colors.onSurfaceVariant}
            />
          )}

          {/* Last label */}
          {chartData.lastPoint && (
            <SkiaText
              x={chartData.lastPoint.x}
              y={height - 4}
              text={chartData.lastPoint.label}
              font={font as any}
              color={colors.onSurfaceVariant}
            />
          )}
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
