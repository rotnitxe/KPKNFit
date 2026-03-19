import React, { useEffect, useMemo, useState } from 'react';
import { Modal, ScrollView, StyleSheet, Switch, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { useColors } from '../../theme';
import { useSettingsStore } from '../../stores/settingsStore';
import type { Settings } from '../../types/settings';
import type { CalorieGoalConfig } from '../../types/workout';
import { calculateDailyCalorieGoal } from '../../utils/calorieFormulas';

interface NutritionPlan {
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
    periodization?: {
        trainingDayCalories: number;
        restDayCalories: number;
    };
}

interface NutritionPlanEditorModalProps {
    visible: boolean;
    onClose: () => void;
    onSave: (plan: NutritionPlan) => void;
    initialPlan: NutritionPlan;
}

function buildConfigFromSettings(settings: Settings): CalorieGoalConfig {
    return settings.calorieGoalConfig ?? {
        formula: 'mifflin',
        activityLevel: settings.userVitals?.activityLevel
            ? {
                sedentary: 1,
                light: 2,
                moderate: 3,
                active: 4,
                very_active: 5,
            }[settings.userVitals.activityLevel] ?? 3
            : 3,
        goal: settings.calorieGoalObjective === 'deficit'
            ? 'lose'
            : settings.calorieGoalObjective === 'surplus'
              ? 'gain'
              : 'maintain',
        weeklyChangeKg: 0.45,
        healthMultiplier: 1,
    };
}

const NutritionPlanEditorModal: React.FC<NutritionPlanEditorModalProps> = ({
    visible,
    onClose,
    onSave,
    initialPlan,
}) => {
    const colors = useColors();
    const { updateSettings } = useSettingsStore();
    const currentSettings = useSettingsStore(state => state.summary ?? state.getSettings());
    const [plan, setPlan] = useState<NutritionPlan>(initialPlan);
    const [usePeriodization, setUsePeriodization] = useState(false);

    useEffect(() => {
        if (!visible) return;
        setPlan(initialPlan);
        setUsePeriodization(!!initialPlan.periodization);
    }, [visible, initialPlan]);

    const activeConfig = useMemo(() => buildConfigFromSettings(currentSettings), [currentSettings]);

    const updateMacro = (key: 'protein' | 'carbs' | 'fats', value: string) => {
        const numValue = parseFloat(value) || 0;
        const nextPlan = { ...plan, [key]: numValue };
        const calculatedCalories = nextPlan.protein * 4 + nextPlan.carbs * 4 + nextPlan.fats * 9;
        setPlan({ ...nextPlan, calories: Math.round(calculatedCalories) });
    };

    const handleReset = () => {
        const p = Math.round((plan.calories * 0.3) / 4);
        const c = Math.round((plan.calories * 0.4) / 4);
        const g = Math.round((plan.calories * 0.3) / 9);
        setPlan({ ...plan, protein: p, carbs: c, fats: g });
    };

    const handleSave = async () => {
        const nextCalories = Math.max(0, Math.round(plan.calories));
        const nextSettingsPatch: Partial<Settings> = {
            dailyCalorieGoal: nextCalories,
            dailyProteinGoal: Math.max(0, Math.round(plan.protein)),
            dailyCarbGoal: Math.max(0, Math.round(plan.carbs)),
            dailyFatGoal: Math.max(0, Math.round(plan.fats)),
            calorieGoalConfig: {
                ...activeConfig,
                goal: activeConfig.goal,
            },
            calorieGoalObjective: currentSettings?.calorieGoalObjective ?? 'maintenance',
        };

        if (usePeriodization && plan.periodization) {
            nextSettingsPatch.calorieGoalConfig = {
                ...activeConfig,
                customActivityFactor: activeConfig.customActivityFactor,
            };
        }

        await updateSettings(nextSettingsPatch);
        onSave(plan);
        onClose();
    };

    const caloriesFromSettings = calculateDailyCalorieGoal(currentSettings, activeConfig);
    const computedCalories = plan.calories > 0 ? plan.calories : caloriesFromSettings;

    return (
        <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
            <View style={styles.overlay}>
                <View style={[styles.container, { backgroundColor: colors.surface }]}>
                    <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
                        <Text style={[styles.title, { color: colors.onSurface }]}>Editar plan nutricional</Text>
                        <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
                            Los cambios actualizan `dailyCalorieGoal` y macros en settings.
                        </Text>

                        <View style={styles.section}>
                            <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>CALORÍAS OBJETIVO</Text>
                            <TextInput
                                style={[styles.input, { color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                keyboardType="numeric"
                                value={String(computedCalories)}
                                onChangeText={(v) => setPlan({ ...plan, calories: parseInt(v) || 0 })}
                            />
                        </View>

                        <View style={styles.section}>
                            <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>DISTRIBUCIÓN DE MACROS</Text>
                            <View style={styles.macroRow}>
                                <Text style={[styles.macroLabel, { color: colors.onSurface }]}>Proteína</Text>
                                <TextInput
                                    style={[styles.macroInput, { color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                    keyboardType="numeric"
                                    value={String(plan.protein)}
                                    onChangeText={(v) => updateMacro('protein', v)}
                                />
                                <Text style={[styles.macroUnit, { color: colors.onSurfaceVariant }]}>g</Text>
                            </View>

                            <View style={styles.macroRow}>
                                <Text style={[styles.macroLabel, { color: colors.onSurface }]}>Carbohidratos</Text>
                                <TextInput
                                    style={[styles.macroInput, { color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                    keyboardType="numeric"
                                    value={String(plan.carbs)}
                                    onChangeText={(v) => updateMacro('carbs', v)}
                                />
                                <Text style={[styles.macroUnit, { color: colors.onSurfaceVariant }]}>g</Text>
                            </View>

                            <View style={styles.macroRow}>
                                <Text style={[styles.macroLabel, { color: colors.onSurface }]}>Grasas</Text>
                                <TextInput
                                    style={[styles.macroInput, { color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                    keyboardType="numeric"
                                    value={String(plan.fats)}
                                    onChangeText={(v) => updateMacro('fats', v)}
                                />
                                <Text style={[styles.macroUnit, { color: colors.onSurfaceVariant }]}>g</Text>
                            </View>
                        </View>

                        <View style={styles.section}>
                            <View style={styles.switchRow}>
                                <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>PERIODIZACIÓN</Text>
                                <Switch
                                    value={usePeriodization}
                                    onValueChange={setUsePeriodization}
                                    trackColor={{ false: colors.surfaceContainer, true: colors.primary }}
                                    thumbColor={colors.onPrimary}
                                />
                            </View>

                            {usePeriodization && (
                                <View style={styles.periodizationInputs}>
                                    <View style={styles.periodRow}>
                                        <Text style={[styles.periodLabel, { color: colors.onSurface }]}>Entreno</Text>
                                        <TextInput
                                            style={[styles.periodInput, { color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                            keyboardType="numeric"
                                            placeholder="kcal"
                                            value={plan.periodization ? String(plan.periodization.trainingDayCalories) : ''}
                                            onChangeText={(v) => setPlan({
                                                ...plan,
                                                periodization: {
                                                    ...(plan.periodization || { trainingDayCalories: 0, restDayCalories: 0 }),
                                                    trainingDayCalories: parseInt(v) || 0,
                                                },
                                            })}
                                        />
                                    </View>
                                    <View style={styles.periodRow}>
                                        <Text style={[styles.periodLabel, { color: colors.onSurface }]}>Descanso</Text>
                                        <TextInput
                                            style={[styles.periodInput, { color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                            keyboardType="numeric"
                                            placeholder="kcal"
                                            value={plan.periodization ? String(plan.periodization.restDayCalories) : ''}
                                            onChangeText={(v) => setPlan({
                                                ...plan,
                                                periodization: {
                                                    ...(plan.periodization || { trainingDayCalories: 0, restDayCalories: 0 }),
                                                    restDayCalories: parseInt(v) || 0,
                                                },
                                            })}
                                        />
                                    </View>
                                </View>
                            )}
                        </View>

                        <View style={styles.buttonRow}>
                            <TouchableOpacity onPress={handleReset} style={[styles.button, styles.secondaryButton]}>
                                <Text style={[styles.buttonText, { color: colors.primary }]}>Reset a calculado</Text>
                            </TouchableOpacity>
                            <TouchableOpacity onPress={handleSave} style={[styles.button, styles.primaryButton, { backgroundColor: colors.primary }]}>
                                <Text style={[styles.buttonText, { color: colors.onPrimary }]}>Guardar</Text>
                            </TouchableOpacity>
                        </View>
                    </ScrollView>
                </View>
            </View>
        </Modal>
    );
};

const styles = StyleSheet.create({
    overlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.5)',
        justifyContent: 'flex-end',
    },
    container: {
        borderTopLeftRadius: 24,
        borderTopRightRadius: 24,
        maxHeight: '80%',
    },
    content: {
        padding: 20,
        paddingBottom: 32,
    },
    title: {
        fontSize: 20,
        fontWeight: '900',
        marginBottom: 6,
    },
    subtitle: {
        fontSize: 12,
        lineHeight: 18,
        marginBottom: 18,
    },
    section: {
        marginBottom: 20,
    },
    label: {
        fontSize: 12,
        fontWeight: '700',
        textTransform: 'uppercase',
        letterSpacing: 1,
        marginBottom: 8,
    },
    input: {
        fontSize: 18,
        fontWeight: '700',
        padding: 12,
        borderRadius: 12,
        borderWidth: 1,
        borderColor: 'transparent',
    },
    macroRow: {
        flexDirection: 'row',
        alignItems: 'center',
        marginBottom: 8,
    },
    macroLabel: {
        flex: 1,
        fontSize: 14,
        fontWeight: '600',
    },
    macroInput: {
        width: 76,
        textAlign: 'center',
        padding: 8,
        borderRadius: 8,
        fontSize: 14,
        fontWeight: '700',
    },
    macroUnit: {
        width: 30,
        textAlign: 'center',
        fontSize: 14,
        fontWeight: '600',
        marginLeft: 4,
    },
    switchRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 12,
    },
    periodizationInputs: {
        backgroundColor: 'rgba(0,0,0,0.05)',
        borderRadius: 12,
        padding: 12,
    },
    periodRow: {
        flexDirection: 'row',
        alignItems: 'center',
        marginBottom: 8,
    },
    periodLabel: {
        flex: 1,
        fontSize: 14,
        fontWeight: '600',
    },
    periodInput: {
        width: 100,
        textAlign: 'center',
        padding: 8,
        borderRadius: 8,
        fontSize: 14,
        fontWeight: '700',
    },
    buttonRow: {
        flexDirection: 'row',
        gap: 12,
        marginTop: 20,
    },
    button: {
        flex: 1,
        padding: 14,
        borderRadius: 12,
        alignItems: 'center',
    },
    primaryButton: {
        backgroundColor: '#007AFF',
    },
    secondaryButton: {
        backgroundColor: 'transparent',
        borderWidth: 1,
        borderColor: 'rgba(128,128,128,0.3)',
    },
    buttonText: {
        fontSize: 14,
        fontWeight: '900',
    },
});

export default React.memo(NutritionPlanEditorModal);
