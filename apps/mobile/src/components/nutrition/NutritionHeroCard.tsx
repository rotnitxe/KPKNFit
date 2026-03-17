import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Button } from '../../components/ui';
import { useColors } from '../../theme';
import { NutritionRingsContainer } from './NutritionSkiaRings';

interface NutritionHeroCardProps {
  dateLabel: string;
  caloriesToday: number;
  calorieGoal: number;
  mealCount: number;
  protein: number;
  proteinGoal: number;
  carbs: number;
  carbGoal: number;
  fats: number;
  fatGoal: number;
  onPressPrimary: () => void;
  primaryLabel?: string;
}

export const NutritionHeroCard: React.FC<NutritionHeroCardProps> = ({
  dateLabel,
  caloriesToday,
  calorieGoal,
  mealCount,
  protein,
  proteinGoal,
  carbs,
  carbGoal,
  fats,
  fatGoal,
  onPressPrimary,
  primaryLabel,
}) => {
  const colors = useColors();
  
  const getPct = (val: number, goal: number) => Math.min(100, Math.round((val / (goal || 1)) * 100));
  const proteinPct = getPct(protein, proteinGoal);
  const carbsPct = getPct(carbs, carbGoal);
  const fatsPct = getPct(fats, fatGoal);

  return (
    <View style={[styles.card, { backgroundColor: `${colors.surface}CC`, borderColor: `${colors.onSurface}1A` }]}>
      <View style={styles.header}>
        <Text style={[styles.dateLabel, { color: colors.onSurfaceVariant }]}>
          Hoy · {dateLabel}
        </Text>
      </View>

      <NutritionRingsContainer
        calories={caloriesToday}
        goal={calorieGoal}
        proteinPct={proteinPct}
        carbsPct={carbsPct}
        fatsPct={fatsPct}
      />

      <View style={styles.subtitleContainer}>
        <Text style={[styles.subtitleText, { color: colors.onSurface }]}>
          {caloriesToday} / {calorieGoal} kcal · {mealCount} comidas
        </Text>
      </View>
      <View style={styles.footer}>
        <Button onPress={onPressPrimary}>
          {primaryLabel ?? 'Registrar comida'}
        </Button>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  dateLabel: {
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  caloriesValue: {
    fontSize: 36,
    lineHeight: 40,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  subtitleContainer: {
    marginBottom: 16,
  },
  subtitleText: {
    fontSize: 14,
  },
  progressBarBackground: {
    position: 'relative',
    height: 8,
    width: '100%',
    borderRadius: 9999,
    marginBottom: 16,
  },
  progressBarFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: '100%',
    borderRadius: 9999,
  },
  footer: {
    marginTop: 'auto',
  },
});