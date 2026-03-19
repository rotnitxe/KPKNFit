import React, { useState, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TextInput, TouchableOpacity } from 'react-native';
import { useColors } from '@/theme';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { BaseChartWrapper } from '@/components/charts/BaseChartWrapper';
import { useBodyStore } from '@/stores/bodyStore';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';

interface BodyLabViewProps {
    // Optional: pass latest entry data directly if needed
    latestWeight?: number;
    latestBodyFat?: number;
    latestMuscleMass?: number;
}

export const BodyLabView: React.FC<BodyLabViewProps> = ({
    latestWeight,
    latestBodyFat,
    latestMuscleMass,
}) => {
    const colors = useColors();
    const rawSettings = readStoredSettingsRaw() as any;
    const height = rawSettings.userVitals?.height as number | undefined;
    const bodyProgress = useBodyStore(state => state.bodyProgress);

    // FFMI Calculator state
    const [weightInput, setWeightInput] = useState(latestWeight?.toString() ?? '');
    const [bodyFatInput, setBodyFatInput] = useState(latestBodyFat?.toString() ?? '');
    const [heightInput, setHeightInput] = useState(height?.toString() ?? '');

    // TDEE Estimator state
    const [activityLevel, setActivityLevel] = useState(1.2);
    const [age, setAge] = useState(rawSettings.userVitals?.age?.toString() ?? '30');
    const [gender, setGender] = useState<'male' | 'female'>('male');

    // Computed values
    const ffmi = useMemo(() => {
        const w = parseFloat(weightInput);
        const bf = parseFloat(bodyFatInput);
        const h = parseFloat(heightInput);
        if (!w || !bf || !h) return null;
        const leanMass = w * (1 - bf / 100);
        const hm = h / 100;
        return leanMass / (hm * hm);
    }, [weightInput, bodyFatInput, heightInput]);

    const ffmiAdjusted = useMemo(() => {
        if (!ffmi) return null;
        // Adjusted FFMI: add 0.5 for every 5 cm over 180 cm (approx)
        const h = parseFloat(heightInput) || 0;
        const extra = Math.max(0, (h - 180) / 5) * 0.5;
        return ffmi + extra;
    }, [ffmi, heightInput]);

    const bmrMifflin = useMemo(() => {
        const w = parseFloat(weightInput) || 0;
        const a = parseFloat(age) || 0;
        const h = parseFloat(heightInput) || 0;
        if (!w || !a || !h) return null;
        return gender === 'male'
            ? 10 * w + 6.25 * h - 5 * a + 5
            : 10 * w + 6.25 * h - 5 * a - 161;
    }, [weightInput, heightInput, age, gender]);

    const bmrHarris = useMemo(() => {
        const w = parseFloat(weightInput) || 0;
        const a = parseFloat(age) || 0;
        const h = parseFloat(heightInput) || 0;
        if (!w || !a || !h) return null;
        return gender === 'male'
            ? 88.362 + 13.397 * w + 4.799 * h - 5.677 * a
            : 447.593 + 9.247 * w + 3.098 * h - 4.330 * a;
    }, [weightInput, heightInput, age, gender]);

    const tdee = useMemo(() => {
        if (!bmrMifflin) return null;
        return bmrMifflin * activityLevel;
    }, [bmrMifflin, activityLevel]);

    // Composition donut data (simple estimation)
    const compositionData = useMemo(() => {
        const w = parseFloat(weightInput) || 0;
        const bf = parseFloat(bodyFatInput) || 0;
        if (!w) return null;
        const fatMass = w * (bf / 100);
        const leanMass = w - fatMass;
        // Approx water ~60% of lean mass, other ~10% of lean mass
        const waterMass = leanMass * 0.6;
        const otherMass = leanMass * 0.1;
        const muscleMass = leanMass - waterMass - otherMass;
        return [
            { label: 'Grasa', value: fatMass, color: colors.error },
            { label: 'Músculo', value: muscleMass, color: colors.primary },
            { label: 'Agua', value: waterMass, color: colors.tertiary },
            { label: 'Otros', value: otherMass, color: colors.secondary },
        ];
    }, [weightInput, bodyFatInput, colors]);

    // Projection
    const [weeklyChange, setWeeklyChange] = useState('-0.5');
    const [goalWeight, setGoalWeight] = useState('');

    const weeksToGoal = useMemo(() => {
        const w = parseFloat(weightInput);
        const g = parseFloat(goalWeight);
        const change = parseFloat(weeklyChange);
        if (!w || !g || !change) return null;
        const diff = g - w;
        if (diff * change <= 0) return null; // no progress in desired direction
        return Math.abs(diff / change);
    }, [weightInput, goalWeight, weeklyChange]);

    // Comparison with past
    const comparisonData = useMemo(() => {
        const now = new Date();
        const days = [30, 60, 90];
        const results: { days: number; weight?: number; bodyFat?: number }[] = [];
        for (const d of days) {
            const targetDate = new Date(now);
            targetDate.setDate(targetDate.getDate() - d);
            const targetStr = targetDate.toISOString().slice(0, 10);
            const entry = bodyProgress.find(e => e.date.slice(0, 10) === targetStr);
            results.push({ days: d, weight: entry?.weight, bodyFat: entry?.bodyFatPercentage });
        }
        return results;
    }, [bodyProgress]);

    const activityLevels = [
        { label: 'Sedentario', factor: 1.2 },
        { label: 'Ligero', factor: 1.375 },
        { label: 'Moderado', factor: 1.55 },
        { label: 'Activo', factor: 1.725 },
        { label: 'Muy activo', factor: 1.9 },
    ];

    return (
        <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
            {/* FFMI Calculator */}
            <BaseChartWrapper title="Calculadora FFMI" subtitle="Índice de masa libre de grasa">
                <View style={styles.inputRow}>
                    <View style={styles.inputGroup}>
                        <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>Peso (kg)</Text>
                        <TextInput
                            style={[styles.input, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outlineVariant }]}
                            value={weightInput}
                            onChangeText={setWeightInput}
                            keyboardType="decimal-pad"
                            placeholder="70"
                            placeholderTextColor={colors.onSurfaceVariant}
                        />
                    </View>
                    <View style={styles.inputGroup}>
                        <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>% Grasa</Text>
                        <TextInput
                            style={[styles.input, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outlineVariant }]}
                            value={bodyFatInput}
                            onChangeText={setBodyFatInput}
                            keyboardType="decimal-pad"
                            placeholder="15"
                            placeholderTextColor={colors.onSurfaceVariant}
                        />
                    </View>
                    <View style={styles.inputGroup}>
                        <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>Altura (cm)</Text>
                        <TextInput
                            style={[styles.input, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outlineVariant }]}
                            value={heightInput}
                            onChangeText={setHeightInput}
                            keyboardType="decimal-pad"
                            placeholder="175"
                            placeholderTextColor={colors.onSurfaceVariant}
                        />
                    </View>
                </View>

                {ffmi && (
                    <View style={styles.resultRow}>
                        <View style={[styles.resultBox, { backgroundColor: colors.primaryContainer }]}>
                            <Text style={[styles.resultLabel, { color: colors.onPrimaryContainer }]}>FFMI</Text>
                            <Text style={[styles.resultValue, { color: colors.onPrimaryContainer }]}>{ffmi.toFixed(1)}</Text>
                        </View>
                        <View style={[styles.resultBox, { backgroundColor: colors.secondaryContainer }]}>
                            <Text style={[styles.resultLabel, { color: colors.onSecondaryContainer }]}>FFMI Ajustado</Text>
                            <Text style={[styles.resultValue, { color: colors.onSecondaryContainer }]}>{ffmiAdjusted?.toFixed(1) ?? '--'}</Text>
                        </View>
                    </View>
                )}

                {/* Visual scale */}
                <View style={styles.scaleContainer}>
                    <Text style={[styles.scaleTitle, { color: colors.onSurfaceVariant }]}>Escala FFMI</Text>
                    <View style={styles.scaleBar}>
                        <View style={[styles.scaleSegment, { backgroundColor: colors.tertiary, flex: 1 }]} />
                        <View style={[styles.scaleSegment, { backgroundColor: colors.primary, flex: 1 }]} />
                        <View style={[styles.scaleSegment, { backgroundColor: colors.error, flex: 1 }]} />
                    </View>
                    <View style={styles.scaleLabels}>
                        <Text style={[styles.scaleLabel, { color: colors.onSurfaceVariant }]}>18</Text>
                        <Text style={[styles.scaleLabel, { color: colors.onSurfaceVariant }]}>20</Text>
                        <Text style={[styles.scaleLabel, { color: colors.onSurfaceVariant }]}>25 (natural)</Text>
                    </View>
                </View>
            </BaseChartWrapper>

            {/* TDEE Estimator */}
            <BaseChartWrapper title="Estimador TDEE" subtitle="Gasto energético total diario">
                <View style={styles.inputRow}>
                    <View style={styles.inputGroup}>
                        <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>Edad</Text>
                        <TextInput
                            style={[styles.input, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outlineVariant }]}
                            value={age}
                            onChangeText={setAge}
                            keyboardType="number-pad"
                            placeholder="30"
                            placeholderTextColor={colors.onSurfaceVariant}
                        />
                    </View>
                    <View style={styles.inputGroup}>
                        <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>Género</Text>
                        <View style={styles.genderSelector}>
                            <TouchableOpacity
                                style={[styles.genderBtn, gender === 'male' && { backgroundColor: colors.primaryContainer }]}
                                onPress={() => setGender('male')}
                            >
                                <Text style={{ color: colors.onSurface }}>Hombre</Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                                style={[styles.genderBtn, gender === 'female' && { backgroundColor: colors.primaryContainer }]}
                                onPress={() => setGender('female')}
                            >
                                <Text style={{ color: colors.onSurface }}>Mujer</Text>
                            </TouchableOpacity>
                        </View>
                    </View>
                </View>

                <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>Nivel de actividad</Text>
                <View style={styles.activitySelector}>
                    {activityLevels.map(level => (
                        <TouchableOpacity
                            key={level.label}
                            style={[styles.activityBtn, activityLevel === level.factor && { backgroundColor: colors.primaryContainer }]}
                            onPress={() => setActivityLevel(level.factor)}
                        >
                            <Text style={[styles.activityLabel, { color: colors.onSurface }]}>{level.label}</Text>
                            <Text style={[styles.activityFactor, { color: colors.onSurfaceVariant }]}>{level.factor}</Text>
                        </TouchableOpacity>
                    ))}
                </View>

                {tdee && (
                    <View style={styles.tdeeResult}>
                        <Text style={[styles.tdeeLabel, { color: colors.onSurface }]}>TDEE Estimado</Text>
                        <Text style={[styles.tdeeValue, { color: colors.primary }]}>{Math.round(tdee)} kcal/día</Text>
                        <View style={styles.tdeeBreakdown}>
                            <View style={styles.tdeeItem}>
                                <Text style={{ color: colors.onSurfaceVariant }}>BMR (Mifflin)</Text>
                                <Text style={{ color: colors.onSurface }}>{Math.round(bmrMifflin ?? 0)}</Text>
                            </View>
                            <View style={styles.tdeeItem}>
                                <Text style={{ color: colors.onSurfaceVariant }}>BMR (Harris)</Text>
                                <Text style={{ color: colors.onSurface }}>{Math.round(bmrHarris ?? 0)}</Text>
                            </View>
                        </View>
                    </View>
                )}
            </BaseChartWrapper>

            {/* Proyección */}
            <BaseChartWrapper title="Proyección" subtitle="Tiempo estimado para objetivo">
                <View style={styles.inputRow}>
                    <View style={styles.inputGroup}>
                        <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>Cambio semanal (kg)</Text>
                        <TextInput
                            style={[styles.input, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outlineVariant }]}
                            value={weeklyChange}
                            onChangeText={setWeeklyChange}
                            keyboardType="decimal-pad"
                            placeholder="-0.5"
                            placeholderTextColor={colors.onSurfaceVariant}
                        />
                    </View>
                    <View style={styles.inputGroup}>
                        <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>Peso objetivo (kg)</Text>
                        <TextInput
                            style={[styles.input, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outlineVariant }]}
                            value={goalWeight}
                            onChangeText={setGoalWeight}
                            keyboardType="decimal-pad"
                            placeholder="70"
                            placeholderTextColor={colors.onSurfaceVariant}
                        />
                    </View>
                </View>

                {weeksToGoal != null && (
                    <View style={[styles.projectionResult, { backgroundColor: colors.primaryContainer }]}>
                        <Text style={{ color: colors.onPrimaryContainer }}>
                            Alcanzarás tu objetivo en aproximadamente <Text style={{ fontWeight: '700' }}>{weeksToGoal.toFixed(1)} semanas</Text>
                        </Text>
                    </View>
                )}
            </BaseChartWrapper>

            {/* Comparación temporal */}
            <BaseChartWrapper title="Comparación temporal" subtitle="Métricas hace 30/60/90 días">
                {comparisonData.map(item => (
                    <View key={item.days} style={styles.comparisonRow}>
                        <Text style={[styles.comparisonLabel, { color: colors.onSurface }]}>{item.days} días</Text>
                        <Text style={{ color: colors.onSurfaceVariant }}>
                            {item.weight ? `${item.weight} kg` : '--'} · {item.bodyFat ? `${item.bodyFat}%` : '--'}
                        </Text>
                    </View>
                ))}
            </BaseChartWrapper>
        </ScrollView>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    inputRow: {
        flexDirection: 'row',
        gap: 12,
        marginBottom: 16,
    },
    inputGroup: {
        flex: 1,
    },
    inputLabel: {
        fontSize: 10,
        textTransform: 'uppercase',
        letterSpacing: 1,
        marginBottom: 4,
    },
    input: {
        borderWidth: 1,
        borderRadius: 12,
        paddingHorizontal: 12,
        paddingVertical: 8,
        fontSize: 14,
    },
    resultRow: {
        flexDirection: 'row',
        gap: 12,
        marginTop: 12,
    },
    resultBox: {
        flex: 1,
        padding: 12,
        borderRadius: 12,
        alignItems: 'center',
    },
    resultLabel: {
        fontSize: 10,
        textTransform: 'uppercase',
        letterSpacing: 1,
    },
    resultValue: {
        fontSize: 24,
        fontWeight: '700',
        marginTop: 4,
    },
    scaleContainer: {
        marginTop: 16,
    },
    scaleTitle: {
        fontSize: 10,
        textTransform: 'uppercase',
        letterSpacing: 1,
        marginBottom: 8,
    },
    scaleBar: {
        flexDirection: 'row',
        height: 8,
        borderRadius: 4,
        overflow: 'hidden',
    },
    scaleSegment: {
        height: '100%',
    },
    scaleLabels: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginTop: 4,
    },
    scaleLabel: {
        fontSize: 10,
    },
    genderSelector: {
        flexDirection: 'row',
        gap: 8,
    },
    genderBtn: {
        flex: 1,
        paddingVertical: 8,
        borderRadius: 8,
        alignItems: 'center',
        borderWidth: 1,
        borderColor: '#ccc',
    },
    activitySelector: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 8,
        marginBottom: 16,
    },
    activityBtn: {
        paddingHorizontal: 12,
        paddingVertical: 6,
        borderRadius: 8,
        borderWidth: 1,
        borderColor: '#ccc',
    },
    activityLabel: {
        fontSize: 12,
        fontWeight: '600',
    },
    activityFactor: {
        fontSize: 10,
    },
    tdeeResult: {
        marginTop: 12,
        alignItems: 'center',
    },
    tdeeLabel: {
        fontSize: 12,
    },
    tdeeValue: {
        fontSize: 32,
        fontWeight: '700',
    },
    tdeeBreakdown: {
        flexDirection: 'row',
        gap: 24,
        marginTop: 8,
    },
    tdeeItem: {
        alignItems: 'center',
    },
    projectionResult: {
        padding: 16,
        borderRadius: 12,
        alignItems: 'center',
        marginTop: 12,
    },
    comparisonRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        paddingVertical: 8,
        borderBottomWidth: 1,
        borderBottomColor: '#eee',
    },
    comparisonLabel: {
        fontWeight: '600',
    },
});
