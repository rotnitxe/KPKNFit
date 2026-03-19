import React, { useMemo, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useColors } from '../../theme';
import type { WorkoutLog } from '../../types/workout';

interface HistoryViewProps {
    program: any; // Program type
    history: WorkoutLog[];
}

const HistoryView: React.FC<HistoryViewProps> = ({ program, history }) => {
    const colors = useColors();
    const [expandedLogId, setExpandedLogId] = useState<string | null>(null);

    // Filter and sort logs
    const programLogs = useMemo(() => {
        return history
            .filter(log => log.programId === program.id)
            .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
    }, [history, program.id]);

    // Calculate streak
    const streak = useMemo(() => {
        if (programLogs.length === 0) return 0;
        let currentStreak = 1;
        for (let i = 0; i < programLogs.length - 1; i++) {
            const current = new Date(programLogs[i].date);
            const next = new Date(programLogs[i + 1].date);
            const diffTime = Math.abs(next.getTime() - current.getTime());
            const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
            if (diffDays <= 3) {
                currentStreak++;
            } else {
                break;
            }
        }
        return currentStreak;
    }, [programLogs]);

    const getFatigueColor = (level: number) => {
        if (level <= 3) return '#10B981'; // Green
        if (level <= 6) return '#F59E0B'; // Yellow
        return '#EF4444'; // Red
    };

    const formatDate = (dateString: string) => {
        const date = new Date(dateString);
        return date.toLocaleDateString('es-CL', { day: 'numeric', month: 'short', year: 'numeric' });
    };

    const toggleExpand = (id: string) => {
        setExpandedLogId(prev => prev === id ? null : id);
    };

    return (
        <ScrollView style={styles.container} contentContainerStyle={styles.content}>
            <View style={styles.header}>
                <Text style={[styles.title, { color: colors.onSurface }]}>Historial</Text>
                <View style={styles.stats}>
                    <Text style={[styles.statText, { color: colors.onSurfaceVariant }]}>
                        {programLogs.length} registros
                    </Text>
                    <Text style={[styles.statText, { color: colors.primary }]}>
                        {streak} streak
                    </Text>
                </View>
            </View>

            {programLogs.length === 0 ? (
                <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
                    No hay registros de sesiones.
                </Text>
            ) : (
                programLogs.map(log => {
                    const isExpanded = expandedLogId === log.id;
                    const totalSets = log.completedExercises.reduce(
                        (acc, ex) => acc + (ex.sets?.length || 0), 0
                    );

                    return (
                        <TouchableOpacity
                            key={log.id}
                            onPress={() => toggleExpand(log.id)}
                            style={[
                                styles.logItem,
                                { backgroundColor: isExpanded ? 'rgba(255,255,255,0.1)' : 'rgba(255,255,255,0.05)' }
                            ]}
                        >
                            <View style={styles.logHeader}>
                                <View style={styles.logInfo}>
                                    <Text style={[styles.sessionName, { color: colors.onSurface }]}>
                                        {log.sessionName}
                                    </Text>
                                    <Text style={[styles.logDate, { color: colors.onSurfaceVariant }]}>
                                        {formatDate(log.date)}
                                    </Text>
                                </View>
                                <View style={styles.logMetrics}>
                                    <View style={[styles.fatigueBadge, { backgroundColor: getFatigueColor(log.fatigueLevel) }]}>
                                        <Text style={styles.fatigueText}>{log.fatigueLevel}</Text>
                                    </View>
                                    <Text style={[styles.setsText, { color: colors.onSurfaceVariant }]}>
                                        {totalSets} sets
                                    </Text>
                                </View>
                            </View>

                            {isExpanded && (
                                <View style={styles.logDetails}>
                                    <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>
                                        DURACIÓN: {log.duration ? `${log.duration} min` : 'N/A'}
                                    </Text>
                                    <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>
                                        CLARIDAD MENTAL: {log.mentalClarity}/5
                                    </Text>
                                    <View style={styles.exercisesList}>
                                        {log.completedExercises.map((ex, idx) => (
                                            <View key={idx} style={styles.exerciseItem}>
                                                <Text style={[styles.exerciseNameDetail, { color: colors.onSurface }]}>
                                                    {ex.exerciseName}
                                                </Text>
                                                <Text style={[styles.exerciseSets, { color: colors.onSurfaceVariant }]}>
                                                    {ex.sets.length} series
                                                </Text>
                                            </View>
                                        ))}
                                    </View>
                                </View>
                            )}
                        </TouchableOpacity>
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
    header: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 20,
    },
    title: {
        fontSize: 18,
        fontWeight: '900',
    },
    stats: {
        flexDirection: 'row',
        gap: 12,
    },
    statText: {
        fontSize: 12,
        fontWeight: '700',
    },
    emptyText: {
        fontSize: 14,
        textAlign: 'center',
        marginTop: 20,
    },
    logItem: {
        borderRadius: 12,
        padding: 12,
        marginBottom: 8,
    },
    logHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    logInfo: {
        flex: 1,
    },
    sessionName: {
        fontSize: 15,
        fontWeight: '700',
        marginBottom: 2,
    },
    logDate: {
        fontSize: 11,
        fontWeight: '600',
    },
    logMetrics: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
    },
    fatigueBadge: {
        width: 24,
        height: 24,
        borderRadius: 12,
        justifyContent: 'center',
        alignItems: 'center',
    },
    fatigueText: {
        color: '#FFF',
        fontSize: 10,
        fontWeight: '900',
    },
    setsText: {
        fontSize: 11,
        fontWeight: '700',
    },
    logDetails: {
        marginTop: 12,
        paddingTop: 12,
        borderTopWidth: 1,
        borderTopColor: 'rgba(255,255,255,0.1)',
    },
    detailLabel: {
        fontSize: 11,
        fontWeight: '600',
        marginBottom: 4,
    },
    exercisesList: {
        marginTop: 8,
        gap: 4,
    },
    exerciseItem: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        paddingVertical: 2,
    },
    exerciseNameDetail: {
        fontSize: 13,
        fontWeight: '500',
    },
    exerciseSets: {
        fontSize: 12,
        fontWeight: '600',
    },
});

export default React.memo(HistoryView);
