import React, { useMemo, useState, useCallback } from 'react';
import { View, Text, StyleSheet, LayoutChangeEvent } from 'react-native';
import { Canvas, Path, Circle, Line, Skia } from '@shopify/react-native-skia';
import { useColors } from '@/theme';
import { computeLinearRegression } from '@/utils/calculations';

export interface BodyWeightPoint {
    dateMs: number;
    weight: number;
    label?: string;
}

interface BodyWeightChartProps {
    data: BodyWeightPoint[];
    height?: number;
    targetWeight?: number;
    strokeWidth?: number;
    testID?: string;
}

const PADDING = { left: 40, right: 16, top: 16, bottom: 24 };

export const BodyWeightChart: React.FC<BodyWeightChartProps> = ({
    data,
    height = 200,
    targetWeight,
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
        const values = sorted.map(d => d.weight);
        const min = Math.min(...values);
        const max = Math.max(...values);
        const range = max - min || 1;

        const chartWidth = width - PADDING.left - PADDING.right;
        const chartHeight = height - PADDING.top - PADDING.bottom;

        // Map points
        const points = sorted.map((d, i) => {
            const x = PADDING.left + (i / (sorted.length - 1)) * chartWidth;
            const y = height - PADDING.bottom - ((d.weight - min) / range) * chartHeight;
            return { x, y, ...d };
        });

        // Build path for actual weight line
        const path = Skia.Path.Make();
        if (points.length > 0) {
            path.moveTo(points[0].x, points[0].y);
            for (let i = 1; i < points.length; i++) {
                path.lineTo(points[i].x, points[i].y);
            }
        }

        // Linear regression trend line
        const regressionPoints = sorted.map((d, i) => ({ x: i, y: d.weight }));
        const { slope, intercept } = computeLinearRegression(regressionPoints);
        const trendPath = Skia.Path.Make();
        if (points.length > 0) {
            const firstX = points[0].x;
            const lastX = points[points.length - 1].x;
            const firstY = height - PADDING.bottom - ((slope * 0 + intercept - min) / range) * chartHeight;
            const lastY = height - PADDING.bottom - ((slope * (points.length - 1) + intercept - min) / range) * chartHeight;
            trendPath.moveTo(firstX, firstY);
            trendPath.lineTo(lastX, lastY);
        }

        // Target line (horizontal)
        let targetY: number | null = null;
        if (targetWeight != null) {
            targetY = height - PADDING.bottom - ((targetWeight - min) / range) * chartHeight;
        }

        const firstLabel = sorted[0].label ?? new Date(sorted[0].dateMs).toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });
        const lastLabel = sorted[sorted.length - 1].label ?? new Date(sorted[sorted.length - 1].dateMs).toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });

        return { path, points, trendPath, targetY, firstLabel, lastLabel, min, max };
    }, [data, width, height, targetWeight]);

    if (!data || data.length < 2) {
        return (
            <View testID={testID} style={[styles.emptyContainer, { height }]}>
                <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
                    Datos insuficientes para gráfica
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

                    {/* Target line */}
                    {chartData.targetY != null && (
                        <>
                            <Path
                                path={Skia.Path.Make()
                                    .moveTo(PADDING.left, chartData.targetY)
                                    .lineTo(width - PADDING.right, chartData.targetY)}
                                style="stroke"
                                color={colors.tertiary}
                                strokeWidth={1.5}
                                strokeCap="round"
                            />
                            <Circle cx={width - PADDING.right - 4} cy={chartData.targetY} r={3} color={colors.tertiary} />
                        </>
                    )}

                    {/* Trend line */}
                    <Path
                        path={chartData.trendPath}
                        style="stroke"
                        color={colors.secondary}
                        strokeWidth={strokeWidth * 0.7}
                        strokeCap="round"
                        strokeJoin="round"
                        opacity={0.7}
                    />

                    {/* Actual weight line */}
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
        borderRadius: 12,
    },
    emptyText: {
        fontSize: 14,
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
        fontSize: 10,
    },
});
