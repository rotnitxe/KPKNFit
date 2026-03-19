import React, { useState, useCallback } from 'react';
import { View, Text, TextInput, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import { useSettingsStore } from '../../stores/settingsStore';
import WizardLayout from '../programs/WizardLayout';

// Tipos para el wizard
interface NutritionProfile {
  goal: 'lose_fat' | 'maintain' | 'gain_muscle' | 'recomposition';
  weight: number; // kg
  height: number; // cm
  age: number;
  sex: 'male' | 'female';
  activityLevel: 'sedentary' | 'light' | 'moderate' | 'active' | 'very_active';
  proteinPerKg: number;
  fatPercentage: number;
}

const STEPS = ['Objetivo', 'Datos', 'Calorías', 'Macros', 'Confirmar'];

const NutritionWizard: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const colors = useColors();
  const { updateSettings } = useSettingsStore();
  const currentSettings = useSettingsStore.getState().getSettings();
  const [currentStep, setCurrentStep] = useState(0);
  const [profile, setProfile] = useState<NutritionProfile>(() => {
    const weight = currentSettings?.userVitals?.weight ?? 70;
    const height = currentSettings?.userVitals?.height ?? 170;
    const age = currentSettings?.userVitals?.age ?? 30;
    const sex = currentSettings?.userVitals?.gender === 'female' || currentSettings?.userVitals?.gender === 'transfemale'
      ? 'female'
      : 'male';
    const activityLevel = currentSettings?.userVitals?.activityLevel ?? 'moderate';
    const goal = currentSettings?.calorieGoalObjective === 'deficit'
      ? 'lose_fat'
      : currentSettings?.calorieGoalObjective === 'surplus'
        ? 'gain_muscle'
        : 'maintain';
    const proteinPerKg = currentSettings?.dailyProteinGoal && weight > 0
      ? Math.max(0.8, Math.round((currentSettings.dailyProteinGoal / weight) * 10) / 10)
      : 1.6;
    const fatPercentage = currentSettings?.dailyCalorieGoal && currentSettings?.dailyFatGoal
      ? Math.max(0.15, Math.min(0.4, (currentSettings.dailyFatGoal * 9) / currentSettings.dailyCalorieGoal))
      : 0.25;

    return {
      goal,
      weight,
      height,
      age,
      sex,
      activityLevel,
      proteinPerKg,
      fatPercentage,
    };
  });

  // Cálculos
  const calculateBMR = useCallback(() => {
    if (profile.sex === 'male') {
      return 10 * profile.weight + 6.25 * profile.height - 5 * profile.age + 5;
    }
    return 10 * profile.weight + 6.25 * profile.height - 5 * profile.age - 161;
  }, [profile]);

  const calculateTDEE = useCallback(() => {
    const bmr = calculateBMR();
    const activityMultipliers: Record<string, number> = {
      sedentary: 1.2,
      light: 1.375,
      moderate: 1.55,
      active: 1.725,
      very_active: 1.9,
    };
    return bmr * (activityMultipliers[profile.activityLevel] || 1.55);
  }, [calculateBMR, profile.activityLevel]);

  const calculateTargetCalories = useCallback(() => {
    const tdee = calculateTDEE();
    switch (profile.goal) {
      case 'lose_fat':
        return tdee - 500;
      case 'gain_muscle':
        return tdee + 300;
      case 'recomposition':
        return tdee - 200;
      default:
        return tdee;
    }
  }, [calculateTDEE, profile.goal]);

  const calculateMacros = useCallback(() => {
    const calories = calculateTargetCalories();
    const protein = profile.weight * profile.proteinPerKg; // en gramos
    const fats = (calories * profile.fatPercentage) / 9; // en gramos (1g grasa = 9 kcal)
    const carbsCalories = calories - (protein * 4) - (fats * 9);
    const carbs = carbsCalories / 4; // en gramos (1g carb = 4 kcal)
    return { protein, carbs, fats, calories };
  }, [calculateTargetCalories, profile.weight, profile.proteinPerKg, profile.fatPercentage]);

  const handleNext = () => {
    if (currentStep < STEPS.length - 1) {
      setCurrentStep(currentStep + 1);
    }
  };

  const handleBack = () => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1);
    }
  };

  const handleSave = useCallback(async () => {
    const macros = calculateMacros();
    const calorieGoalConfig = {
      formula: 'mifflin' as const,
      activityLevel: {
        sedentary: 1,
        light: 2,
        moderate: 3,
        active: 4,
        very_active: 5,
      }[profile.activityLevel] ?? 3,
      goal: profile.goal === 'lose_fat' ? 'lose' as const : profile.goal === 'gain_muscle' ? 'gain' as const : 'maintain' as const,
      weeklyChangeKg: profile.goal === 'maintain' ? 0 : 0.45,
      healthMultiplier: 1,
    };

    await updateSettings({
      hasSeenNutritionWizard: true,
      nutritionWizardVersion: 2,
      hasDismissedNutritionSetup: true,
      calorieGoalObjective: profile.goal === 'lose_fat'
        ? 'deficit'
        : profile.goal === 'gain_muscle'
          ? 'surplus'
          : 'maintenance',
      calorieGoalConfig,
      dailyCalorieGoal: Math.round(macros.calories),
      dailyProteinGoal: Math.round(macros.protein),
      dailyCarbGoal: Math.round(macros.carbs),
      dailyFatGoal: Math.round(macros.fats),
      dietaryPreference: currentSettings?.dietaryPreference ?? 'omnivore',
      metabolicConditions: currentSettings?.metabolicConditions ?? [],
      userVitals: {
        ...currentSettings?.userVitals,
        age: profile.age,
        weight: profile.weight,
        height: profile.height,
        gender: profile.sex,
        activityLevel: profile.activityLevel,
      },
    });
    onClose();
  }, [calculateMacros, currentSettings?.dietaryPreference, currentSettings?.metabolicConditions, currentSettings?.userVitals, onClose, profile.age, profile.goal, profile.height, profile.sex, profile.weight, profile.activityLevel, updateSettings]);

  const renderStep = () => {
    switch (currentStep) {
      case 0: // Objetivo
        return (
          <ScrollView style={styles.stepContainer}>
            <Text style={[styles.title, { color: colors.onSurface }]}>¿Cuál es tu objetivo?</Text>
            {[
              { id: 'lose_fat', label: 'Perder grasa', desc: 'Déficit calórico controlado' },
              { id: 'maintain', label: 'Mantener', desc: 'Mantenimiento de peso' },
              { id: 'gain_muscle', label: 'Ganar músculo', desc: 'Superávit calórico moderado' },
              { id: 'recomposition', label: 'Recomposición', desc: 'Perder grasa y ganar músculo' },
            ].map((opt) => (
              <TouchableOpacity
                key={opt.id}
                style={[
                  styles.optionButton,
                  {
                    backgroundColor: profile.goal === opt.id ? colors.primaryContainer : colors.surfaceContainer,
                    borderColor: colors.outlineVariant,
                  },
                ]}
                onPress={() => setProfile({ ...profile, goal: opt.id as any })}
              >
                <Text style={{ color: colors.onSurface, fontWeight: 'bold' }}>{opt.label}</Text>
                <Text style={{ color: colors.onSurfaceVariant, fontSize: 12 }}>{opt.desc}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        );
      case 1: // Datos corporales
        return (
          <ScrollView style={styles.stepContainer}>
            <Text style={[styles.title, { color: colors.onSurface }]}>Datos corporales</Text>
            <View style={styles.inputRow}>
              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Peso (kg)</Text>
                <TextInput
                  style={[styles.input, { color: colors.onSurface }]}
                  value={String(profile.weight)}
                  onChangeText={(v) => setProfile({ ...profile, weight: parseFloat(v) || 0 })}
                  keyboardType="numeric"
                />
              </View>
              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Altura (cm)</Text>
                <TextInput
                  style={[styles.input, { color: colors.onSurface }]}
                  value={String(profile.height)}
                  onChangeText={(v) => setProfile({ ...profile, height: parseFloat(v) || 0 })}
                  keyboardType="numeric"
                />
              </View>
            </View>
            <View style={styles.inputRow}>
              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Edad</Text>
                <TextInput
                  style={[styles.input, { color: colors.onSurface }]}
                  value={String(profile.age)}
                  onChangeText={(v) => setProfile({ ...profile, age: parseInt(v) || 0 })}
                  keyboardType="numeric"
                />
              </View>
              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Sexo</Text>
                <View style={styles.toggleRow}>
                  <TouchableOpacity
                    style={[styles.toggleButton, profile.sex === 'male' && { backgroundColor: colors.primary }]}
                    onPress={() => setProfile({ ...profile, sex: 'male' })}
                  >
                    <Text style={{ color: profile.sex === 'male' ? colors.onPrimary : colors.onSurface }}>Hombre</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.toggleButton, profile.sex === 'female' && { backgroundColor: colors.primary }]}
                    onPress={() => setProfile({ ...profile, sex: 'female' })}
                  >
                    <Text style={{ color: profile.sex === 'female' ? colors.onPrimary : colors.onSurface }}>Mujer</Text>
                  </TouchableOpacity>
                </View>
              </View>
            </View>
            <View style={styles.inputGroup}>
              <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Nivel de actividad</Text>
              <View style={styles.toggleRow}>
                {['sedentary', 'light', 'moderate', 'active', 'very_active'].map((level) => (
                  <TouchableOpacity
                    key={level}
                    style={[styles.toggleButton, profile.activityLevel === level && { backgroundColor: colors.primary }]}
                    onPress={() => setProfile({ ...profile, activityLevel: level as any })}
                  >
                    <Text style={{ color: profile.activityLevel === level ? colors.onPrimary : colors.onSurface }}>
                      {level.charAt(0).toUpperCase()}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          </ScrollView>
        );
      case 2: // Cálculo de calorías
        const bmr = calculateBMR();
        const tdee = calculateTDEE();
        const target = calculateTargetCalories();
        return (
          <ScrollView style={styles.stepContainer}>
            <Text style={[styles.title, { color: colors.onSurface }]}>Cálculo de calorías</Text>
            <View style={styles.resultCard}>
              <Text style={[styles.resultLabel, { color: colors.onSurfaceVariant }]}>Tasa Metabólica Basal (BMR)</Text>
              <Text style={[styles.resultValue, { color: colors.onSurface }]}>{Math.round(bmr)} kcal/día</Text>
            </View>
            <View style={styles.resultCard}>
              <Text style={[styles.resultLabel, { color: colors.onSurfaceVariant }]}>Gasto Calórico Total (TDEE)</Text>
              <Text style={[styles.resultValue, { color: colors.onSurface }]}>{Math.round(tdee)} kcal/día</Text>
            </View>
            <View style={[styles.resultCard, { backgroundColor: colors.primaryContainer }]}>
              <Text style={[styles.resultLabel, { color: colors.onPrimaryContainer }]}>Objetivo Diario</Text>
              <Text style={[styles.resultValue, { color: colors.onPrimaryContainer, fontSize: 28 }]}>
                {Math.round(target)} kcal
              </Text>
              <Text style={{ color: colors.onPrimaryContainer, opacity: 0.7 }}>
                {profile.goal === 'lose_fat' ? 'Déficit de 500 kcal' : 
                 profile.goal === 'gain_muscle' ? 'Superávit de 300 kcal' : 'Mantenimiento'}
              </Text>
            </View>
          </ScrollView>
        );
      case 3: // Distribución de macros
        const macros = calculateMacros();
        return (
          <ScrollView style={styles.stepContainer}>
            <Text style={[styles.title, { color: colors.onSurface }]}>Distribución de macros</Text>
            <View style={styles.macroInputs}>
              <View style={styles.macroInput}>
                <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Proteína (g/kg)</Text>
                <TextInput
                  style={[styles.input, { color: colors.onSurface }]}
                  value={String(profile.proteinPerKg)}
                  onChangeText={(v) => setProfile({ ...profile, proteinPerKg: parseFloat(v) || 0 })}
                  keyboardType="numeric"
                />
              </View>
              <View style={styles.macroInput}>
                <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Grasas (%)</Text>
                <TextInput
                  style={[styles.input, { color: colors.onSurface }]}
                  value={String(profile.fatPercentage * 100)}
                  onChangeText={(v) => setProfile({ ...profile, fatPercentage: (parseFloat(v) || 0) / 100 })}
                  keyboardType="numeric"
                />
              </View>
            </View>
            <View style={styles.macroPreview}>
              <View style={styles.macroItem}>
                <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Proteína</Text>
                <Text style={[styles.macroValue, { color: colors.primary }]}>
                  {Math.round(macros.protein)} g
                </Text>
              </View>
              <View style={styles.macroItem}>
                <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Carbohidratos</Text>
                <Text style={[styles.macroValue, { color: colors.primary }]}>
                  {Math.round(macros.carbs)} g
                </Text>
              </View>
              <View style={styles.macroItem}>
                <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Grasas</Text>
                <Text style={[styles.macroValue, { color: colors.primary }]}>
                  {Math.round(macros.fats)} g
                </Text>
              </View>
            </View>
          </ScrollView>
        );
      case 4: // Confirmación
        const finalMacros = calculateMacros();
        return (
          <ScrollView style={styles.stepContainer}>
            <Text style={[styles.title, { color: colors.onSurface }]}>Resumen</Text>
            <View style={styles.summaryCard}>
              <Text style={[styles.summaryTitle, { color: colors.onSurface }]}>Plan de Nutrición</Text>
              <View style={styles.summaryRow}>
                <Text style={{ color: colors.onSurfaceVariant }}>Calorías diarias:</Text>
                <Text style={{ color: colors.onSurface, fontWeight: 'bold' }}>
                  {Math.round(finalMacros.calories)} kcal
                </Text>
              </View>
              <View style={styles.summaryRow}>
                <Text style={{ color: colors.onSurfaceVariant }}>Proteína:</Text>
                <Text style={{ color: colors.onSurface }}>{Math.round(finalMacros.protein)} g</Text>
              </View>
              <View style={styles.summaryRow}>
                <Text style={{ color: colors.onSurfaceVariant }}>Carbohidratos:</Text>
                <Text style={{ color: colors.onSurface }}>{Math.round(finalMacros.carbs)} g</Text>
              </View>
              <View style={styles.summaryRow}>
                <Text style={{ color: colors.onSurfaceVariant }}>Grasas:</Text>
                <Text style={{ color: colors.onSurface }}>{Math.round(finalMacros.fats)} g</Text>
              </View>
            </View>
          </ScrollView>
        );
      default:
        return null;
    }
  };

  return (
    <WizardLayout
      title="Configuración de Nutrición"
      steps={STEPS}
      currentStep={currentStep}
      onBack={handleBack}
      onNext={currentStep === STEPS.length - 1 ? handleSave : handleNext}
      canNext={
        currentStep === 0 ? true :
        currentStep === 1 ? profile.weight > 0 && profile.height > 0 && profile.age > 0 :
        true
      }
      isLastStep={currentStep === STEPS.length - 1}
    >
      {renderStep()}
    </WizardLayout>
  );
};

const styles = StyleSheet.create({
  stepContainer: {
    flex: 1,
  },
  title: {
    fontSize: 22,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
  },
  optionButton: {
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 12,
  },
  inputRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  inputGroup: {
    flex: 1,
    marginRight: 8,
  },
  label: {
    fontSize: 12,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  input: {
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
  },
  toggleRow: {
    flexDirection: 'row',
    gap: 8,
  },
  toggleButton: {
    flex: 1,
    padding: 10,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: '#e0e0e0',
  },
  resultCard: {
    padding: 16,
    borderRadius: 12,
    backgroundColor: '#f5f5f5',
    marginBottom: 12,
  },
  resultLabel: {
    fontSize: 12,
    marginBottom: 4,
  },
  resultValue: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  macroInputs: {
    flexDirection: 'row',
    marginBottom: 16,
  },
  macroInput: {
    flex: 1,
    marginRight: 8,
  },
  macroPreview: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: 16,
    backgroundColor: '#f5f5f5',
    borderRadius: 12,
  },
  macroItem: {
    alignItems: 'center',
  },
  macroLabel: {
    fontSize: 12,
  },
  macroValue: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  summaryCard: {
    padding: 16,
    borderRadius: 12,
    backgroundColor: '#f5f5f5',
  },
  summaryTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
});

export default NutritionWizard;
