import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';

interface NutritionMacroProgressCardProps {
  protein: number;
  carbs: number;
  fats: number;
  proteinGoal: number;
  carbGoal: number;
  fatGoal: number;
}

interface MacroRowProps {
  label: string;
  value: number;
  goal: number;
  fillColor: string;
  isLast?: boolean;
}

const MacroRow = ({ label, value, goal, fillColor, isLast }: MacroRowProps) => {
  const colors = useColors();
  const safeGoal = goal > 0 ? goal : 1;
  const pct = Math.min(100, Math.round((value / safeGoal) * 100));
  
  return (
    <View style={[styles.row, !isLast && { borderBottomWidth: 1, borderBottomColor: colors.outlineVariant }]}>
      <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <View style={styles.valueContainer}>
        <Text style={[styles.valueText, { color: colors.onSurface }]}>
          {value} / {goal} g
        </Text>
      </View>
      <View style={[styles.progressBackground, { backgroundColor: `${colors.onSurface}1A` }]}>
        <View
          style={[styles.progressFill, { backgroundColor: fillColor, width: `${pct}%` }]}
        />
      </View>
    </View>
  );
};

export const NutritionMacroProgressCard: React.FC<NutritionMacroProgressCardProps> = ({
  protein,
  carbs,
  fats,
  proteinGoal,
  carbGoal,
  fatGoal,
}) => {
  const colors = useColors();

  return (
    <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <Text style={[styles.title, { color: colors.onSurface }]}>
        Distribución de Macros
      </Text>
      <MacroRow label="Proteína" value={protein} goal={proteinGoal} fillColor={colors.batteryHigh} />
      <MacroRow label="Carbos" value={carbs} goal={carbGoal} fillColor={colors.ringMuscular} />
      <MacroRow label="Grasas" value={fats} goal={fatGoal} fillColor={colors.error} isLast />
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
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
  },
  label: {
    fontSize: 14,
    fontWeight: '500',
  },
  valueContainer: {
    alignItems: 'flex-end',
  },
  valueText: {
    fontSize: 14,
    fontWeight: '600',
  },
  progressBackground: {
    position: 'relative',
    width: '66.666%',
    height: 6,
    borderRadius: 9999,
    overflow: 'hidden',
  },
  progressFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: '100%',
  },
});
