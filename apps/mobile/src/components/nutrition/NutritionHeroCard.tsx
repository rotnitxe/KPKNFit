import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { Button } from '../../components/ui';
import { useColors } from '../../theme';
import { NutritionRingsContainer } from './NutritionSkiaRings';
import { ArrowDownIcon, ArrowUpIcon, MinusIcon } from '../icons';

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
  onPressSettings: () => void;
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
  onPressSettings,
  primaryLabel,
}) => {
  const colors = useColors();
  
  const getPct = (val: number, goal: number) => Math.min(100, Math.round((val / (goal || 1)) * 100));
  const proteinPct = getPct(protein, proteinGoal);
  const carbsPct = getPct(carbs, carbGoal);
  const fatsPct = getPct(fats, fatGoal);
  
  // Calculate deficit/surplus indicator
  const diff = caloriesToday - calorieGoal;
  const diffPercent = Math.abs(diff) / calorieGoal;
  let indicator = 'equals';
  let indicatorColor = colors.onSurfaceVariant;
  let IndicatorIcon = MinusIcon;
  
  if (diffPercent > 0.05) { // ±5% tolerance
    if (diff > 0) {
      indicator = 'surplus';
      indicatorColor = '#EF4444'; // Red
      IndicatorIcon = ArrowUpIcon;
    } else {
      indicator = 'deficit';
      indicatorColor = '#10B981'; // Green
      IndicatorIcon = ArrowDownIcon;
    }
  }

  return (
    <TouchableOpacity 
      style={[styles.card, { backgroundColor: `${colors.surface}CC`, borderColor: `${colors.onSurface}1A` }]}
      onPress={onPressSettings}
      activeOpacity={0.8}
    >
      <View style={styles.header}>
        <Text style={[styles.dateLabel, { color: colors.onSurfaceVariant }]}>
          HOY · {dateLabel}
        </Text>
        <View style={[styles.indicator, { backgroundColor: `${indicatorColor}20` }]}>
          <IndicatorIcon size={12} color={indicatorColor} />
          <Text style={[styles.indicatorText, { color: indicatorColor }]}>
            {indicator === 'equals' ? 'Objetivo' : indicator === 'surplus' ? 'Superávit' : 'Déficit'}
          </Text>
        </View>
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
      
      {/* Macro Bar */}
      <View style={styles.macroBarContainer}>
        <View style={[styles.macroBarSegment, { backgroundColor: '#10b981', width: `${proteinPct}%` }]} />
        <View style={[styles.macroBarSegment, { backgroundColor: '#f59e0b', width: `${carbsPct}%` }]} />
        <View style={[styles.macroBarSegment, { backgroundColor: '#f43f5e', width: `${fatsPct}%` }]} />
      </View>
      <View style={styles.macroLegend}>
        <View style={styles.macroLegendItem}>
          <View style={[styles.legendDot, { backgroundColor: '#10b981' }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>P {Math.round(protein)}g</Text>
        </View>
        <View style={styles.macroLegendItem}>
          <View style={[styles.legendDot, { backgroundColor: '#f59e0b' }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>C {Math.round(carbs)}g</Text>
        </View>
        <View style={styles.macroLegendItem}>
          <View style={[styles.legendDot, { backgroundColor: '#f43f5e' }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>G {Math.round(fats)}g</Text>
        </View>
      </View>

      <View style={styles.footer}>
        <Button onPress={onPressPrimary}>
          {primaryLabel ?? 'Registrar comida'}
        </Button>
      </View>
    </TouchableOpacity>
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
  indicator: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    gap: 4,
  },
  indicatorText: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
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
  macroBarContainer: {
    flexDirection: 'row',
    height: 4,
    borderRadius: 2,
    overflow: 'hidden',
    marginBottom: 8,
  },
  macroBarSegment: {
    height: '100%',
  },
  macroLegend: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 16,
  },
  macroLegendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  legendDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  legendText: {
    fontSize: 11,
    fontWeight: '600',
  },
  footer: {
    marginTop: 'auto',
  },
});