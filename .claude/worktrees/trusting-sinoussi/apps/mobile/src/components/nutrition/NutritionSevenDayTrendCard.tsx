import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';

export interface TrendPoint {
  key: string;
  label: string;
  calories: number;
  isToday: boolean;
}

interface NutritionSevenDayTrendCardProps {
  points: TrendPoint[];
  calorieGoal: number;
}

export const NutritionSevenDayTrendCard: React.FC<NutritionSevenDayTrendCardProps> = ({
  points,
  calorieGoal,
}) => {
  const colors = useColors();
  const maxCalories = Math.max(1, ...points.map(p => p.calories), calorieGoal || 0);

  return (
    <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurface }]}>
          Últimos 7 días
        </Text>
      </View>
      <View style={styles.chartContainer}>
        {points.map((p) => {
          const heightPx = Math.max(6, Math.round((p.calories / maxCalories) * 80));
          return (
            <View style={styles.columnContainer} key={p.key}>
              <View style={styles.barWrapper}>
                <View 
                  style={[
                    styles.barFill,
                    {
                      backgroundColor: p.isToday ? colors.primary : `${colors.onSurface}33`,
                      height: heightPx,
                    }
                  ]}
                />
                {p.isToday && (
                  <View style={[styles.tooltipContainer, { backgroundColor: colors.onSurface }]}>
                    <Text style={[styles.tooltipText, { color: colors.surface }]}>{p.calories} kcal</Text>
                  </View>
                )}
              </View>
              <Text style={[styles.label, { color: colors.onSurface }]}>
                {p.label}
              </Text>
            </View>
          );
        })}
      </View>
      <View style={styles.footer}>
        <Text style={[styles.footerText, { color: colors.onSurfaceVariant }]}>
          Meta diaria: {calorieGoal} kcal
        </Text>
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
    marginBottom: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  chartContainer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    columnGap: 8,
    height: 112,
  },
  columnContainer: {
    alignItems: 'center',
  },
  barWrapper: {
    position: 'relative',
    width: 24,
  },
  barFill: {
    position: 'absolute',
    left: 0,
    bottom: 0,
    width: '100%',
    borderTopLeftRadius: 6,
    borderTopRightRadius: 6,
  },
  tooltipContainer: {
    position: 'absolute',
    left: '50%',
    top: -8,
    transform: [{ translateX: -18 }],
    alignItems: 'center',
    borderRadius: 9999,
    paddingHorizontal: 4,
    paddingVertical: 2,
    minWidth: 36,
  },
  tooltipText: {
    fontSize: 10,
    fontWeight: 'bold',
    textAlign: 'center',
  },
  label: {
    fontSize: 10,
    marginTop: 4,
  },
  footer: {
    marginTop: 12,
  },
  footerText: {
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
});