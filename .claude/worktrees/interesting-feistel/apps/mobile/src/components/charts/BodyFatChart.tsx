import React, { useMemo, useState, useCallback } from 'react';
import { View, Text, StyleSheet, LayoutChangeEvent } from 'react-native';
import { Canvas, Path, Circle, Line, Skia, Rect } from '@shopify/react-native-skia';
import { useColors } from '@/theme';

export interface BodyFatPoint {
    dateMs: number;
    bodyFat: number;
    label?: string;
}

interface BodyFatChartProps {
    data: BodyFatPoint[];
    height?: number;
    strokeWidth?: number;
    testID?: string;
}

const PADDING = { left: 40, right: 16, top: 16, bottom: 24 };

export const BodyFatChart: React.FC<BodyFatChartProps> = ({
    data,
    height = 200,
    strokeWidth = 2.5,
    testID,
}) => {
    const colors = useColors();

    const [width, setWidth] = useState(0);

    const onLayout = useCallback((event: LayoutChangeEvent) => {
        setWidth(event.nativeEvent.layout.width);
    }, []);

    const chartData = useMemo(() => {
        if (!data || data.length < 2 || width === 0) {
            return null;
        }

        const sorted = [...data].sort((a, b) => a.dateMs - b.dateMs);
        const values = sorted.map(d => d.bodyFat);
        const min = Math.min(...values);
        const max = Math.max(...values);
        // Define zones: green < 15%, yellow 15-25%, red > 25%
        const zoneMin = 0;
        const zoneMax = 35; // arbitrary upper bound for scaling
        const range = zoneMax - zoneMin || 1;

        const chartWidth = width - PADDING.left - PADDING.right;
        const chartHeight = height - PADDING.top - PADDING.bottom;

        // Map points
        const points = sorted.map((d, i) => {
            const x = PADDING.left + (i / (sorted.length - 1)) * chartWidth;
            const y = height - PADDING.bottom - ((d.bodyFat - zoneMin) / range) * chartHeight;
            return { x, y, ...d };
        });

        // Build path for actual line
        const path = Skia.Path.Make();
        if (points.length > 0) {
            path.moveTo(points[0].x, points[0].y);
            for (let i = 1; i < points.length; i++) {
                path.lineTo(points[i].x, points[i].y);
            }
        }

        // Zone backgrounds (green, yellow, red)
        const zones = [
            { top: 0, bottom: 15, color: `${colors.tertiary}40` }, // green zone
            { top: 15, bottom: 25, color: `${colors.tertiary}20` }, // yellow zone
            { top: 25, bottom: 35, color: `${colors.error}20` }, // red zone
        ];

        const firstLabel = sorted[0].label ?? new Date(sorted[0].dateMs).toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });
        const lastLabel = sorted[sorted.length - 1].label ?? new Date(sorted[sorted.length - 1].dateMs).toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });

        return { path, points, zones, firstLabel, lastLabel };
    }, [data, width, height, colors]);

    if (!data || data.length < 2) {
        return (
            <View testID={testID} style={[styles.emptyContainer, { height, borderColor: colors.outlineVariant }]}>
                <Text style={[styles.emptyIcon, { color: colors.onSurfaceVariant }]}>
                    📊
                </Text>
                <Text style={[styles.emptyText, { color: colors.onSurface }]}>
                    Sin datos suficientes
                </Text>
                <Text style={[styles.emptyHint, { color: colors.onSurfaceVariant }]}>
                    Se necesitan al menos 2 registros
                </Text>
            </View>
        );
    }

    return (
        <View onLayout={onLayout} style={styles.chartContainer} testID={testID}>
            {width > 0 && chartData && (
                <Canvas style={{ width, height }}>
                    {/* Axis line */}
                    <Line
                        p1={{ x: PADDING.left, y: height - PADDING.bottom }}
                        p2={{ x: width - PADDING.right, y: height - PADDING.bottom }}
                        color={colors.outlineVariant}
                        strokeWidth={1}
                        opacity={0.3}
                    />

                    {/* Zone backgrounds */}
                    {chartData.zones.map((zone, i) => {
                        const yTop = height - PADDING.bottom - ((zone.bottom - 0) / 35) * (height - PADDING.top - PADDING.bottom);
                        const yBottom = height - PADDING.bottom - ((zone.top - 0) / 35) * (height - PADDING.top - PADDING.bottom);
                        return (
                            <Rect
                                key={i}
                                x={PADDING.left}
                                y={yTop}
                                width={width - PADDING.left - PADDING.right}
                                height={yBottom - yTop}
                                color={zone.color}
                            />
                        );
                    })}

                    {/* Actual line */}
                    <Path
                        path={chartData.path}
                        style="stroke"
                        color={colors.primary}
                        strokeWidth={strokeWidth}
                        strokeCap="round"
                        strokeJoin="round"
                    />

                    {/* Points */}
                    {chartData.points.map((p, i) => (
                        <Circle
                            key={i}
                            cx={p.x}
                            cy={p.y}
                            r={strokeWidth * 1.2}
                            color={colors.primary}
                        />
                    ))}
                </Canvas>
            )}
            {width > 0 && chartData && (
                <View style={[styles.labelsContainer, { width, height }]}>
                    <Text style={[styles.label, { left: chartData.points[0]?.x ?? 0, color: colors.onSurfaceVariant }]}>
                        {chartData.firstLabel}
                    </Text>
                    <Text style={[styles.label, { right: 0, color: colors.onSurfaceVariant }]}>
                        {chartData.lastLabel}
                    </Text>
                </View>
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
        borderRadius: 16,
        borderWidth: 1,
        borderStyle: 'dashed',
        gap: 6,
        paddingVertical: 20,
    },
    emptyIcon: {
        fontSize: 28,
        marginBottom: 4,
    },
    emptyText: {
        fontSize: 15,
        fontWeight: '700',
    },
    emptyHint: {
        fontSize: 12,
        fontWeight: '500',
        opacity: 0.6,
    },
    labelsContainer: {
        position: 'absolute',
        bottom: 0,
        left: 0,
        flexDirection: 'row',
        justifyContent: 'space-between',
        paddingHorizontal: 40,
    },
    label: {
        position: 'absolute',
        fontSize: 11,
        fontWeight: '600',
        letterSpacing: 0.3,
    },
});
