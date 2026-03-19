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

  if (diffPercent > 0.05) {
    if (diff > 0) {
      indicator = 'surplus';
      indicatorColor = '#EF4444';
      IndicatorIcon = ArrowUpIcon;
    } else {
      indicator = 'deficit';
      indicatorColor = '#10B981';
      IndicatorIcon = ArrowDownIcon;
    }
  }

  const formattedDate = new Date(`${dateLabel}T00:00:00`).toLocaleDateString('es-ES', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });

  return (
    <TouchableOpacity
      style={[styles.card, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
      onPress={onPressSettings}
      activeOpacity={0.7}
    >
      {/* Header: Date and status */}
      <View style={styles.header}>
        <View style={styles.dateSection}>
          <Text style={[styles.dateLabel, { color: colors.onSurfaceVariant }]}>
            {formattedDate}
          </Text>
          <Text style={[styles.dateSublabel, { color: colors.onSurfaceVariant }]}>
            Nutrición y macros
          </Text>
        </View>
        <View style={[styles.indicator, { backgroundColor: `${indicatorColor}15` }]}>
          <IndicatorIcon size={14} color={indicatorColor} />
          <Text style={[styles.indicatorText, { color: indicatorColor }]}>
            {indicator === 'equals' ? 'En objetivo' : indicator === 'surplus' ? 'Superávit' : 'Déficit'}
          </Text>
        </View>
      </View>

      {/* Main content: Calories + Rings */}
      <View style={styles.mainContent}>
        {/* Left: Calorie summary */}
        <View style={styles.calorieSection}>
          <View style={styles.calorieLabel}>
            <View style={[styles.calorieDot, { backgroundColor: colors.primary }]} />
            <Text style={[styles.calorieLabelText, { color: colors.onSurfaceVariant }]}>
              Calorías
            </Text>
          </View>
          <View style={styles.calorieValueRow}>
            <Text style={[styles.calorieValue, { color: colors.onSurface }]}>
              {caloriesToday}
            </Text>
            <Text style={[styles.calorieUnit, { color: colors.onSurfaceVariant }]}>
              / {calorieGoal} kcal
            </Text>
          </View>
          <View style={styles.mealCountRow}>
            <Text style={[styles.mealCountText, { color: colors.onSurfaceVariant }]}>
              {mealCount} comidas registradas
            </Text>
          </View>
        </View>

        {/* Right: Macro rings */}
        <View style={styles.ringsSection}>
          <NutritionRingsContainer
            calories={caloriesToday}
            goal={calorieGoal}
            proteinPct={proteinPct}
            carbsPct={carbsPct}
            fatsPct={fatsPct}
          />
        </View>
      </View>

      {/* Macro progress bar */}
      <View style={styles.macroBarContainer}>
        <View style={[styles.macroBarSegment, { backgroundColor: '#10b981', width: `${proteinPct}%` }]} />
        <View style={[styles.macroBarSegment, { backgroundColor: '#f59e0b', width: `${carbsPct}%` }]} />
        <View style={[styles.macroBarSegment, { backgroundColor: '#f43f5e', width: `${fatsPct}%` }]} />
      </View>

      {/* Macro legend */}
      <View style={styles.macroLegend}>
        <View style={styles.macroLegendItem}>
          <View style={[styles.legendDot, { backgroundColor: '#10b981' }]} />
          <Text style={[styles.legendLabel, { color: colors.onSurfaceVariant }]}>P</Text>
          <Text style={[styles.legendValue, { color: colors.onSurface }]}>
            {Math.round(protein)}g
          </Text>
        </View>
        <View style={styles.macroLegendItem}>
          <View style={[styles.legendDot, { backgroundColor: '#f59e0b' }]} />
          <Text style={[styles.legendLabel, { color: colors.onSurfaceVariant }]}>C</Text>
          <Text style={[styles.legendValue, { color: colors.onSurface }]}>
            {Math.round(carbs)}g
          </Text>
        </View>
        <View style={styles.macroLegendItem}>
          <View style={[styles.legendDot, { backgroundColor: '#f43f5e' }]} />
          <Text style={[styles.legendLabel, { color: colors.onSurfaceVariant }]}>G</Text>
          <Text style={[styles.legendValue, { color: colors.onSurface }]}>
            {Math.round(fats)}g
          </Text>
        </View>
      </View>

      {/* Footer: Primary CTA */}
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
    borderRadius: 28,
    borderWidth: 1,
    paddingHorizontal: 18,
    paddingVertical: 18,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  dateSection: {
    flex: 1,
    paddingRight: 12,
  },
  dateLabel: {
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 0.5,
    marginBottom: 2,
  },
  dateSublabel: {
    fontSize: 10,
    fontWeight: '600',
    opacity: 0.7,
  },
  indicator: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 14,
    gap: 6,
  },
  indicatorText: {
    fontSize: 9,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  mainContent: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  calorieSection: {
    flex: 1,
    paddingRight: 12,
  },
  calorieLabel: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
    gap: 6,
  },
  calorieDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  calorieLabelText: {
    fontSize: 9,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  calorieValueRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginBottom: 4,
  },
  calorieValue: {
    fontSize: 40,
    fontWeight: '900',
    lineHeight: 44,
    letterSpacing: -1,
  },
  calorieUnit: {
    fontSize: 13,
    fontWeight: '600',
    marginLeft: 4,
  },
  mealCountRow: {
    marginTop: 4,
  },
  mealCountText: {
    fontSize: 11,
    fontWeight: '600',
  },
  ringsSection: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  macroBarContainer: {
    flexDirection: 'row',
    height: 5,
    borderRadius: 3,
    overflow: 'hidden',
    marginBottom: 10,
  },
  macroBarSegment: {
    height: '100%',
  },
  macroLegend: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 16,
    paddingHorizontal: 8,
  },
  macroLegendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  legendDot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
  },
  legendLabel: {
    fontSize: 10,
    fontWeight: '700',
  },
  legendValue: {
    fontSize: 12,
    fontWeight: '700',
  },
  footer: {
    marginTop: 4,
  },
});