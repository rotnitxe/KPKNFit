import React, { useMemo, useState, useCallback } from 'react';
import { View, Text, StyleSheet, LayoutChangeEvent } from 'react-native';
import { Canvas, Path, Circle, Line, Skia } from '@shopify/react-native-skia';
import { useColors } from '@/theme';
import { BaseChartWrapper } from '@/components/charts/BaseChartWrapper';
import { useBodyStore } from '@/stores/bodyStore';
// import { computeTrendDirection } from '@/utils/calculations'; // not needed

interface BodyPartDetailViewProps {
    bodyPart: string; // e.g., "pecho", "cintura", "cadera", "brazo", "muslo", "pantorrilla"
    title?: string;
}

// Map common part names to measurement keys (including left/right)
const PART_KEYS: Record<string, string[]> = {
    pecho: ['Pecho'],
    cintura: ['Cintura'],
    cadera: ['Cadera'],
    cuello: ['Cuello'],
    biceps: ['Bíceps (Izq)', 'Bíceps (Der)'],
    antebrazo: ['Antebrazo (Izq)', 'Antebrazo (Der)'],
    muslo: ['Muslo (Izq)', 'Muslo (Der)'],
    pantorrilla: ['Pantorrilla (Izq)', 'Pantorrilla (Der)'],
};

export const BodyPartDetailView: React.FC<BodyPartDetailViewProps> = ({ bodyPart, title }) => {
    const colors = useColors();
    const bodyProgress = useBodyStore(state => state.bodyProgress);

    const partKeys = PART_KEYS[bodyPart] ?? [bodyPart];

    // Extract measurements for this part across logs
    const partData = useMemo(() => {
        const data: { dateMs: number; value: number; label: string }[] = [];
        for (const entry of bodyProgress) {
            if (!entry.measurements) continue;
            for (const key of partKeys) {
                const val = entry.measurements[key];
                if (typeof val === 'number') {
                    data.push({
                        dateMs: Date.parse(entry.date),
                        value: val,
                        label: entry.date.slice(0, 10),
                    });
                    break; // take first matching key
                }
            }
        }
        return data.sort((a, b) => a.dateMs - b.dateMs);
    }, [bodyProgress, partKeys]);

    // Compute stats
    const stats = useMemo(() => {
        if (partData.length === 0) return null;
        const values = partData.map(d => d.value);
        const latest = values[values.length - 1];
        const first = values[0];
        const best = Math.min(...values);
        const worst = Math.max(...values);
        const delta = latest - first;
        // Rate: cm per week (assuming roughly weekly data, but we can compute based on dates)
        const dateRangeDays = (partData[partData.length - 1].dateMs - partData[0].dateMs) / (1000 * 60 * 60 * 24);
        const weeks = dateRangeDays / 7 || 1;
        const rate = delta / weeks;
        // const trend = computeTrendDirection(values); // trend not used currently
        return { latest, first, best, worst, delta, rate };
    }, [partData]);

    // Chart drawing
    const [width, setWidth] = useState(0);
    const onLayout = useCallback((event: LayoutChangeEvent) => {
        setWidth(event.nativeEvent.layout.width);
    }, []);

    const chartHeight = 160;
    const PADDING = { left: 40, right: 16, top: 16, bottom: 24 };

    const chartData = useMemo(() => {
        if (partData.length < 2 || width === 0) return null;
        const values = partData.map(d => d.value);
        const min = Math.min(...values);
        const max = Math.max(...values);
        const range = max - min || 1;
        const chartWidth = width - PADDING.left - PADDING.right;
        const chartH = chartHeight - PADDING.top - PADDING.bottom;

        const points = partData.map((d, i) => ({
            x: PADDING.left + (i / (partData.length - 1)) * chartWidth,
            y: chartHeight - PADDING.bottom - ((d.value - min) / range) * chartH,
            ...d,
        }));

        const path = Skia.Path.Make();
        if (points.length > 0) {
            path.moveTo(points[0].x, points[0].y);
            for (let i = 1; i < points.length; i++) {
                path.lineTo(points[i].x, points[i].y);
            }
        }

        const firstLabel = partData[0].label;
        const lastLabel = partData[partData.length - 1].label;

        return { path, points, firstLabel, lastLabel };
    }, [partData, width]);

    if (partData.length === 0) {
        return (
            <BaseChartWrapper title={title ?? bodyPart} subtitle="Sin datos">
                <View style={[styles.emptyContainer, { height: 100 }]}>
                    <Text style={{ color: colors.onSurfaceVariant }}>No hay mediciones registradas para esta parte del cuerpo.</Text>
                </View>
            </BaseChartWrapper>
        );
    }

    return (
        <BaseChartWrapper title={title ?? bodyPart} subtitle="Evolución de circumference">
            {/* Stats row */}
            <View style={styles.statsRow}>
                <View style={styles.statItem}>
                    <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Actual</Text>
                    <Text style={[styles.statValue, { color: colors.onSurface }]}>{stats?.latest.toFixed(1)} cm</Text>
                </View>
                <View style={styles.statItem}>
                    <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Mejor</Text>
                    <Text style={[styles.statValue, { color: colors.tertiary }]}>{stats?.best.toFixed(1)} cm</Text>
                </View>
                <View style={styles.statItem}>
                    <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Peor</Text>
                    <Text style={[styles.statValue, { color: colors.error }]}>{stats?.worst.toFixed(1)} cm</Text>
                </View>
                <View style={styles.statItem}>
                    <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Delta</Text>
                    <Text style={[styles.statValue, { color: stats?.delta && stats.delta >= 0 ? colors.primary : colors.error }]}>
                        {stats?.delta && stats.delta >= 0 ? '+' : ''}{stats?.delta.toFixed(1)} cm
                    </Text>
                </View>
                <View style={styles.statItem}>
                    <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Tasa</Text>
                    <Text style={[styles.statValue, { color: colors.onSurface }]}>{stats?.rate.toFixed(2)} cm/sem</Text>
                </View>
            </View>

            {/* Chart */}
            <View onLayout={onLayout} style={styles.chartContainer}>
                {width > 0 && chartData && (
                    <Canvas style={{ width, height: chartHeight }}>
                        {/* Axis line */}
                        <Line
                            p1={{ x: PADDING.left, y: chartHeight - PADDING.bottom }}
                            p2={{ x: width - PADDING.right, y: chartHeight - PADDING.bottom }}
                            color={colors.outlineVariant}
                            strokeWidth={1}
                            opacity={0.3}
                        />
                        {/* Line */}
                        <Path
                            path={chartData.path}
                            style="stroke"
                            color={colors.primary}
                            strokeWidth={2.5}
                            strokeCap="round"
                            strokeJoin="round"
                        />
                        {/* Points */}
                        {chartData.points.map((p, i) => (
                            <Circle
                                key={i}
                                cx={p.x}
                                cy={p.y}
                                r={3}
                                color={colors.primary}
                            />
                        ))}
                    </Canvas>
                )}
            </View>
        </BaseChartWrapper>
    );
};

const styles = StyleSheet.create({
    statsRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginBottom: 16,
    },
    statItem: {
        alignItems: 'center',
    },
    statLabel: {
        fontSize: 9,
        textTransform: 'uppercase',
        letterSpacing: 0.5,
    },
    statValue: {
        fontSize: 14,
        fontWeight: '700',
        marginTop: 2,
    },
    chartContainer: {
        width: '100%',
    },
    emptyContainer: {
        alignItems: 'center',
        justifyContent: 'center',
    },
});
