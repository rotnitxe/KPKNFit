import React, { useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { Canvas, Path, Skia, Circle } from '@shopify/react-native-skia';
import { useColors } from '../../theme';
import type { WorkoutLog, CompletedExercise } from '../../types/workout';
import { calculateBrzycki1RM } from '../../utils/calculations';

interface ProgressViewProps {
    program: any; // Program type
    history: WorkoutLog[];
}

const ProgressView: React.FC<ProgressViewProps> = ({ program, history }) => {
    const colors = useColors();
    
    // Filter history for this program
    const programLogs = useMemo(() => {
        return history.filter(log => log.programId === program.id)
            .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()); // Newest first
    }, [history, program.id]);

    // Calculate e1RM per exercise over time
    const exerciseProgress = useMemo(() => {
        const progressMap: Record<string, { date: string; e1RM: number }[]> = {};

        programLogs.forEach(log => {
            log.completedExercises.forEach(ex => {
                if (!progressMap[ex.exerciseName]) {
                    progressMap[ex.exerciseName] = [];
                }
                
                // Calculate best e1RM for this exercise in this session
                let bestE1RM = 0;
                ex.sets.forEach(set => {
                    if (set.weight && set.reps) {
                        const e1RM = calculateBrzycki1RM(set.weight, set.reps);
                        if (e1RM > bestE1RM) bestE1RM = e1RM;
                    }
                });

                if (bestE1RM > 0) {
                    progressMap[ex.exerciseName].push({
                        date: log.date,
                        e1RM: bestE1RM
                    });
                }
            });
        });

        // Keep only last 8 sessions per exercise
        return Object.keys(progressMap).map(name => ({
            exerciseName: name,
            data: progressMap[name].slice(0, 8).reverse() // Oldest first for chart
        }));
    }, [programLogs]);

    // Calculate trend (improvement/regression/stable)
    const getTrend = (data: { e1RM: number }[]) => {
        if (data.length < 2) return 'stable';
        const first = data[0].e1RM;
        const last = data[data.length - 1].e1RM;
        const change = last - first;
        if (change > 5) return 'improving';
        if (change < -5) return 'regressing';
        return 'stable';
    };

    const renderChart = (data: { e1RM: number }[]) => {
        if (data.length < 2) return null;

        const width = 200;
        const height = 60;
        const padding = 5;

        const minVal = Math.min(...data.map(d => d.e1RM));
        const maxVal = Math.max(...data.map(d => d.e1RM));
        const range = maxVal - minVal || 1;

        const points = data.map((d, i) => {
            const x = padding + (i / (data.length - 1)) * (width - 2 * padding);
            const y = height - padding - ((d.e1RM - minVal) / range) * (height - 2 * padding);
            return { x, y };
        });

        const path = Skia.Path.Make();
        path.moveTo(points[0].x, points[0].y);
        for (let i = 1; i < points.length; i++) {
            path.lineTo(points[i].x, points[i].y);
        }

        return (
            <Canvas style={{ width, height }}>
                <Path
                    path={path}
                    color={colors.primary}
                    style="stroke"
                    strokeWidth={2}
                />
                {/* Points */}
                {points.map((p, i) => (
                    <Circle key={i} cx={p.x} cy={p.y} r={3} color={colors.primary} />
                ))}
            </Canvas>
        );
    };

    return (
        <ScrollView style={styles.container} contentContainerStyle={styles.content}>
            <Text style={[styles.title, { color: colors.onSurface }]}>Progreso de Fuerza</Text>
            
            {exerciseProgress.length === 0 ? (
                <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
                    No hay datos de progreso disponibles.
                </Text>
            ) : (
                exerciseProgress.map(item => {
                    const trend = getTrend(item.data);
                    const trendIcon = trend === 'improving' ? '↑' : trend === 'regressing' ? '↓' : '=';
                    const trendColor = trend === 'improving' ? '#10B981' : trend === 'regressing' ? '#EF4444' : colors.onSurfaceVariant;

                    return (
                        <View key={item.exerciseName} style={styles.exerciseRow}>
                            <View style={styles.exerciseInfo}>
                                <Text style={[styles.exerciseName, { color: colors.onSurface }]}>{item.exerciseName}</Text>
                                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
                                    <Text style={{ color: trendColor, fontWeight: '900' }}>{trendIcon}</Text>
                                    <Text style={[styles.trendText, { color: colors.onSurfaceVariant }]}>
                                        {trend === 'improving' ? 'Mejorando' : trend === 'regressing' ? 'Retrocediendo' : 'Estable'}
                                    </Text>
                                </View>
                            </View>
                            <View style={styles.chartContainer}>
                                {renderChart(item.data)}
                            </View>
                            <View style={styles.valuesInfo}>
                                <Text style={[styles.valueText, { color: colors.onSurface }]}>
                                    {item.data[item.data.length - 1]?.e1RM.toFixed(1) || 0} kg
                                </Text>
                            </View>
                        </View>
                    );
                })
            )}
        </ScrollView>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    content: {
        padding: 16,
        paddingBottom: 40,
    },
    title: {
        fontSize: 18,
        fontWeight: '900',
        marginBottom: 20,
    },
    emptyText: {
        fontSize: 14,
        textAlign: 'center',
        marginTop: 20,
    },
    exerciseRow: {
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: 'rgba(255,255,255,0.05)',
        borderRadius: 12,
        padding: 12,
        marginBottom: 12,
    },
    exerciseInfo: {
        flex: 1,
        marginRight: 12,
    },
    exerciseName: {
        fontSize: 13,
        fontWeight: '700',
        marginBottom: 4,
    },
    trendText: {
        fontSize: 10,
        fontWeight: '600',
    },
    chartContainer: {
        flex: 2,
        alignItems: 'center',
    },
    valuesInfo: {
        width: 60,
        alignItems: 'flex-end',
    },
    valueText: {
        fontSize: 12,
        fontWeight: '800',
    },
});

export default React.memo(ProgressView);
