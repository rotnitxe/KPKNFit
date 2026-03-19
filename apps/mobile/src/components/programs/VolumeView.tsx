import React, { useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useColors } from '../../theme';
import type { DetailedMuscleVolumeAnalysis, MuscleHierarchy } from '../../types/workout';

interface VolumeViewProps {
    volumeAnalysis: DetailedMuscleVolumeAnalysis[];
    muscleHierarchy: MuscleHierarchy | null;
    onSelectWeek?: (weekId: string) => void;
    currentWeeks: any[]; // Placeholder for week selector logic
    selectedWeekId?: string | null;
}

const CATEGORY_ORDER = ['Piernas', 'Torso Superior', 'Brazos', 'Core'];

const VolumeView: React.FC<VolumeViewProps> = ({
    volumeAnalysis,
    muscleHierarchy,
    currentWeeks,
    selectedWeekId,
    onSelectWeek
}) => {
    const colors = useColors();

    // Group volume analysis by category
    const groupedData = useMemo(() => {
        const groups: Record<string, DetailedMuscleVolumeAnalysis[]> = {
            'Piernas': [],
            'Torso Superior': [],
            'Brazos': [],
            'Core': [],
            'Otros': []
        };

        volumeAnalysis.forEach(item => {
            // Try to map muscle group to category using hierarchy
            let category = 'Otros';
            if (muscleHierarchy) {
                const bodyPart = muscleHierarchy.muscleToBodyPart[item.muscleGroup];
                if (bodyPart) {
                    // Simple mapping based on common body parts
                    if (bodyPart.includes('Leg') || bodyPart.includes('Pierna') || item.muscleGroup.includes('Cuadriceps') || item.muscleGroup.includes('Gluteo')) {
                        category = 'Piernas';
                    } else if (bodyPart.includes('Arm') || bodyPart.includes('Brazo') || item.muscleGroup.includes('Biceps') || item.muscleGroup.includes('Triceps')) {
                        category = 'Brazos';
                    } else if (bodyPart.includes('Torso') || bodyPart.includes('Chest') || bodyPart.includes('Back') || bodyPart.includes('Pecho') || bodyPart.includes('Espalda')) {
                        category = 'Torso Superior';
                    } else if (bodyPart.includes('Core') || bodyPart.includes('Abdomen')) {
                        category = 'Core';
                    }
                }
            }
            
            // Fallback based on muscle group name
            if (category === 'Otros') {
                const lowerName = item.muscleGroup.toLowerCase();
                if (lowerName.includes('pierna') || lowerName.includes('cuadriceps') || lowerName.includes('gluteo') || lowerName.includes('isquio')) {
                    category = 'Piernas';
                } else if (lowerName.includes('pecho') || lowerName.includes('espalda') || lowerName.includes('hombro')) {
                    category = 'Torso Superior';
                } else if (lowerName.includes('biceps') || lowerName.includes('triceps') || lowerName.includes('antebrazo')) {
                    category = 'Brazos';
                } else if (lowerName.includes('abdomen') || lowerName.includes('core') || lowerName.includes('cuello')) {
                    category = 'Core';
                }
            }

            groups[category].push(item);
        });

        return groups;
    }, [volumeAnalysis, muscleHierarchy]);

    const getVolumeColor = (volume: number) => {
        // Green: within MV-MEV (optimal) - assuming < 15 is optimal for example
        // Yellow: above MEV (15-20)
        // Red: above MRV (> 20)
        if (volume <= 15) return colors.primary; // Green-ish (using primary as accent)
        if (volume <= 20) return '#F59E0B'; // Yellow/Orange
        return '#EF4444'; // Red
    };

    const renderMuscleRow = (item: DetailedMuscleVolumeAnalysis) => {
        const barWidth = Math.min(100, (item.displayVolume / 30) * 100); // Normalize max volume to 30
        const barColor = getVolumeColor(item.displayVolume);

        return (
            <View key={item.muscleGroup} style={styles.muscleRow}>
                <View style={styles.muscleInfo}>
                    <Text style={[styles.muscleName, { color: colors.onSurface }]}>{item.muscleGroup}</Text>
                    <Text style={[styles.muscleVolume, { color: colors.onSurfaceVariant }]}>
                        {item.displayVolume.toFixed(1)} avg
                    </Text>
                </View>
                <View style={styles.barContainer}>
                    <View style={[styles.barFill, { width: `${barWidth}%`, backgroundColor: barColor }]} />
                </View>
                <Text style={[styles.totalSets, { color: colors.onSurfaceVariant }]}>
                    {item.totalSets} sets
                </Text>
            </View>
        );
    };

    return (
        <ScrollView style={styles.container} contentContainerStyle={styles.content}>
            {/* Week Selector (Simplified) */}
            <View style={styles.weekSelector}>
                <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Comparar Semanas</Text>
                {/* Placeholder for week toggle logic */}
                <View style={{ height: 8 }} />
            </View>

            {/* Muscle Categories */}
            {CATEGORY_ORDER.map(category => (
                groupedData[category] && groupedData[category].length > 0 && (
                    <View key={category} style={styles.categorySection}>
                        <Text style={[styles.categoryTitle, { color: colors.primary }]}>{category.toUpperCase()}</Text>
                        <View style={styles.muscleList}>
                            {groupedData[category].map(renderMuscleRow)}
                        </View>
                    </View>
                )
            ))}

            {/* Others if any */}
            {groupedData['Otros'] && groupedData['Otros'].length > 0 && (
                <View style={styles.categorySection}>
                    <Text style={[styles.categoryTitle, { color: colors.onSurfaceVariant }]}>OTROS</Text>
                    <View style={styles.muscleList}>
                        {groupedData['Otros'].map(renderMuscleRow)}
                    </View>
                </View>
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
    weekSelector: {
        marginBottom: 20,
    },
    sectionTitle: {
        fontSize: 12,
        fontWeight: '800',
        textTransform: 'uppercase',
        letterSpacing: 1,
    },
    categorySection: {
        marginBottom: 24,
    },
    categoryTitle: {
        fontSize: 11,
        fontWeight: '900',
        letterSpacing: 1.5,
        marginBottom: 12,
    },
    muscleList: {
        gap: 12,
    },
    muscleRow: {
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: 'rgba(255,255,255,0.05)',
        borderRadius: 12,
        padding: 12,
    },
    muscleInfo: {
        width: 100,
    },
    muscleName: {
        fontSize: 13,
        fontWeight: '700',
    },
    muscleVolume: {
        fontSize: 10,
        fontWeight: '600',
        marginTop: 2,
    },
    barContainer: {
        flex: 1,
        height: 8,
        backgroundColor: 'rgba(128,128,128,0.3)',
        borderRadius: 4,
        marginHorizontal: 12,
        overflow: 'hidden',
    },
    barFill: {
        height: '100%',
        borderRadius: 4,
    },
    totalSets: {
        fontSize: 11,
        fontWeight: '800',
        width: 50,
        textAlign: 'right',
    },
});

export default React.memo(VolumeView);
