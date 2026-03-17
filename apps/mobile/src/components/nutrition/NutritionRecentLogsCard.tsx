import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import type { SavedNutritionEntry } from '../../types/nutrition';
import { useColors } from '../../theme';

interface NutritionRecentLogsCardProps {
  logs: SavedNutritionEntry[];
  onPressLog?: (logId: string) => void;
  maxItems?: number;
}

export const NutritionRecentLogsCard: React.FC<NutritionRecentLogsCardProps> = ({
  logs,
  onPressLog,
  maxItems,
}) => {
  const colors = useColors();
  const items = logs.slice(0, maxItems ?? 5);

  return (
    <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <Text style={[styles.title, { color: colors.onSurface }]}>
        Registros recientes
      </Text>

      {items.length === 0 ? (
        <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
          Aún no hay registros guardados.
        </Text>
      ) : (
        <View>
          {items.map((log, index) => {
            const isLast = index === items.length - 1;
            return (
              <View
                key={log.id}
                style={[
                  styles.logItemContainer,
                  !isLast && { 
                    borderBottomWidth: 1, 
                    borderBottomColor: colors.outlineVariant,
                    marginBottom: 12,
                    paddingBottom: 12,
                  }
                ]}
              >
                {onPressLog ? (
                  <Pressable onPress={() => onPressLog(log.id)} style={styles.pressable}>
                    <View style={styles.logContent}>
                      <Text style={[styles.logTitle, { color: colors.onSurface }]}>
                        {log.description}
                      </Text>
                      <View style={styles.rowBetween}>
                        <Text style={[styles.logDate, { color: colors.onSurfaceVariant }]}>
                          {new Date(log.createdAt).toLocaleDateString('es-ES', {
                            month: 'short',
                            day: 'numeric',
                          })}
                        </Text>
                        <Text style={[styles.logCalories, { color: colors.onSurface }]}>
                          {Math.round(log.totals.calories)} kcal
                        </Text>
                      </View>
                      <View style={styles.macrosRow}>
                        <Text style={[styles.macroText, { color: colors.onSurfaceVariant }]}>
                          P: {Math.round(log.totals.protein)}g
                        </Text>
                        <Text style={[styles.macroText, { color: colors.onSurfaceVariant }]}>
                          C: {Math.round(log.totals.carbs)}g
                        </Text>
                        <Text style={[styles.macroText, { color: colors.onSurfaceVariant }]}>
                          G: {Math.round(log.totals.fats)}g
                        </Text>
                      </View>
                    </View>
                  </Pressable>
                ) : (
                  <View style={styles.logContent}>
                    <Text style={[styles.logTitle, { color: colors.onSurface }]}>
                      {log.description}
                    </Text>
                    <View style={styles.rowBetween}>
                      <Text style={[styles.logDate, { color: colors.onSurfaceVariant }]}>
                        {new Date(log.createdAt).toLocaleDateString('es-ES', {
                          month: 'short',
                          day: 'numeric',
                        })}
                      </Text>
                      <Text style={[styles.logCalories, { color: colors.onSurface }]}>
                        {Math.round(log.totals.calories)} kcal
                      </Text>
                    </View>
                    <View style={styles.macrosRow}>
                      <Text style={[styles.macroText, { color: colors.onSurfaceVariant }]}>
                        P: {Math.round(log.totals.protein)}g
                      </Text>
                      <Text style={[styles.macroText, { color: colors.onSurfaceVariant }]}>
                        C: {Math.round(log.totals.carbs)}g
                      </Text>
                      <Text style={[styles.macroText, { color: colors.onSurfaceVariant }]}>
                        G: {Math.round(log.totals.fats)}g
                      </Text>
                    </View>
                  </View>
                )}
              </View>
            );
          })}
        </View>
      )}
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
  emptyText: {
    fontSize: 14,
    textAlign: 'center',
    fontStyle: 'italic',
    paddingVertical: 24,
  },
  logItemContainer: {
    flexDirection: 'column',
  },
  pressable: {
    width: '100%',
  },
  logContent: {
    padding: 12,
  },
  logTitle: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 4,
  },
  rowBetween: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  logDate: {
    fontSize: 12,
  },
  logCalories: {
    fontSize: 12,
    fontWeight: '600',
  },
  macrosRow: {
    flexDirection: 'row',
    columnGap: 16,
    marginTop: 4,
  },
  macroText: {
    fontSize: 12,
  },
});