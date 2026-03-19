import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import Slider from '@react-native-community/slider';
import { useColors } from '../../theme';

interface MuscleLimits {
    muscle: string;
    mv: number;
    mev: number;
    mrv: number;
}

interface VolumeCalibrationStepProps {
    onComplete: (limits: MuscleLimits[]) => void;
    initialLimits?: MuscleLimits[];
}

const DEFAULT_LIMITS: MuscleLimits[] = [
    { muscle: 'Cuádriceps', mv: 6, mev: 10, mrv: 20 },
    { muscle: 'Pectorales', mv: 4, mev: 8, mrv: 16 },
    { muscle: 'Dorsales', mv: 6, mev: 10, mrv: 18 },
    { muscle: 'Hombros', mv: 3, mev: 6, mrv: 12 },
    { muscle: 'Bíceps', mv: 3, mev: 5, mrv: 10 },
    { muscle: 'Tríceps', mv: 3, mev: 5, mrv: 10 },
    { muscle: 'Cuádriceps', mv: 6, mev: 10, mrv: 20 }, // Duplicate? Assume different context or remove
    { muscle: 'Isquiosurales', mv: 4, mev: 8, mrv: 15 },
    { muscle: 'Glúteos', mv: 4, mev: 8, mrv: 16 },
    { muscle: 'Pantorrillas', mv: 2, mev: 4, mrv: 8 },
    { muscle: 'Abdomen', mv: 2, mev: 4, mrv: 8 },
];

const VolumeCalibrationStep: React.FC<VolumeCalibrationStepProps> = ({
    onComplete,
    initialLimits,
}) => {
    const colors = useColors();
    const [limits, setLimits] = useState<MuscleLimits[]>(
        initialLimits || DEFAULT_LIMITS
    );

    const handleReset = () => {
        setLimits(DEFAULT_LIMITS);
    };

    const handleSliderChange = (muscle: string, field: keyof MuscleLimits, value: number) => {
        setLimits(prev =>
            prev.map(item =>
                item.muscle === muscle ? { ...item, [field]: value } : item
            )
        );
    };

    return (
        <ScrollView style={styles.container} contentContainerStyle={styles.content}>
            <View style={styles.header}>
                <Text style={[styles.title, { color: colors.onSurface }]}>
                    Calibración de Volumen
                </Text>
                <TouchableOpacity onPress={handleReset} style={styles.resetBtn}>
                    <Text style={[styles.resetText, { color: colors.primary }]}>
                        Reset a Defaults
                    </Text>
                </TouchableOpacity>
            </View>

            <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
                Ajusta los límites de volumen recomendados por músculo.
            </Text>

            {limits.map((item, index) => (
                <View key={`${item.muscle}-${index}`} style={styles.muscleCard}>
                    <Text style={[styles.muscleName, { color: colors.onSurface }]}>
                        {item.muscle}
                    </Text>

                    <View style={styles.sliderRow}>
                        <Text style={[styles.sliderLabel, { color: colors.onSurfaceVariant }]}>
                            MV: {item.mv}
                        </Text>
                        <Slider
                            style={styles.slider}
                            minimumValue={0}
                            maximumValue={30}
                            step={1}
                            value={item.mv}
                            onValueChange={(val: number) => handleSliderChange(item.muscle, 'mv', val)}
                            minimumTrackTintColor={colors.primary}
                            maximumTrackTintColor={colors.outlineVariant}
                            thumbTintColor={colors.primary}
                        />
                    </View>

                    <View style={styles.sliderRow}>
                        <Text style={[styles.sliderLabel, { color: colors.onSurfaceVariant }]}>
                            MEV: {item.mev}
                        </Text>
                        <Slider
                            style={styles.slider}
                            minimumValue={0}
                            maximumValue={40}
                            step={1}
                            value={item.mev}
                            onValueChange={(val: number) => handleSliderChange(item.muscle, 'mev', val)}
                            minimumTrackTintColor={colors.primary}
                            maximumTrackTintColor={colors.outlineVariant}
                            thumbTintColor={colors.primary}
                        />
                    </View>

                    <View style={styles.sliderRow}>
                        <Text style={[styles.sliderLabel, { color: colors.onSurfaceVariant }]}>
                            MRV: {item.mrv}
                        </Text>
                        <Slider
                            style={styles.slider}
                            minimumValue={0}
                            maximumValue={50}
                            step={1}
                            value={item.mrv}
                            onValueChange={(val: number) => handleSliderChange(item.muscle, 'mrv', val)}
                            minimumTrackTintColor={colors.primary}
                            maximumTrackTintColor={colors.outlineVariant}
                            thumbTintColor={colors.primary}
                        />
                    </View>
                </View>
            ))}

            <TouchableOpacity
                style={[styles.completeBtn, { backgroundColor: colors.primary }]}
                onPress={() => onComplete(limits)}
            >
                <Text style={[styles.completeText, { color: colors.onPrimary }]}>
                    Guardar Límites
                </Text>
            </TouchableOpacity>
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
        marginBottom: 16,
    },
    title: {
        fontSize: 20,
        fontWeight: '900',
    },
    resetBtn: {
        padding: 8,
    },
    resetText: {
        fontSize: 12,
        fontWeight: '700',
    },
    subtitle: {
        fontSize: 14,
        marginBottom: 20,
    },
    muscleCard: {
        backgroundColor: 'rgba(255,255,255,0.05)',
        borderRadius: 12,
        padding: 16,
        marginBottom: 12,
    },
    muscleName: {
        fontSize: 16,
        fontWeight: '800',
        marginBottom: 12,
    },
    sliderRow: {
        flexDirection: 'row',
        alignItems: 'center',
        marginBottom: 8,
    },
    sliderLabel: {
        width: 60,
        fontSize: 12,
        fontWeight: '600',
    },
    slider: {
        flex: 1,
    },
    completeBtn: {
        marginTop: 24,
        padding: 16,
        borderRadius: 12,
        alignItems: 'center',
    },
    completeText: {
        fontSize: 16,
        fontWeight: '900',
    },
});

export default React.memo(VolumeCalibrationStep);
