import React, { useMemo } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useColors } from '@/theme';
import type { BodyProgressEntry } from '../../types/workout';
import { LineTrendChart } from '../charts';
import { Button } from '../ui';

interface BodyProgressSectionProps {
  entries: BodyProgressEntry[];
  onPressAdd: () => void;
  onPressEdit: (entry: BodyProgressEntry) => void;
  onPressDelete: (entry: BodyProgressEntry) => void;
}

export const BodyProgressSection: React.FC<BodyProgressSectionProps> = ({
  entries,
  onPressAdd,
  onPressEdit,
  onPressDelete,
}) => {
  const colors = useColors();

  const sortedEntries = useMemo(() => {
    return [...entries].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
  }, [entries]);

  const recentEntries = useMemo(() => sortedEntries.slice(0, 5), [sortedEntries]);

  const chartData = useMemo(() => {
    return [...entries]
      .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
      .filter(e => typeof e.weight === 'number')
      .map(e => {
        const d = new Date(e.date);
        return {
          key: e.id,
          label: `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`,
          value: e.weight!,
        };
      });
  }, [entries]);

  return (
    <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>Progreso corporal</Text>
        <Button variant="secondary" onPress={onPressAdd} style={styles.addButton}>+ Agregar</Button>
      </View>

      {entries.length === 0 ? (
        <View style={[styles.emptyContainer, { paddingVertical: 32 }]}>
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
            Aún no tienes registros corporales. Comienza agregando tu peso hoy.
          </Text>
        </View>
      ) : (
        <View style={styles.entriesList}>
          {recentEntries.map((entry) => (
            <View key={entry.id} style={[styles.entryRow, { borderBottomColor: colors.outlineVariant + '0D' }]}>
              <View style={styles.entryInfo}>
                <Text style={[styles.entryDate, { color: colors.onSurface }]}>
                  {new Date(entry.date).toLocaleDateString('es-CL', { day: '2-digit', month: 'short' })}
                </Text>
                <View style={styles.entryMetrics}>
                  {entry.bodyFatPercentage && (
                    <Text style={[styles.metricText, { color: colors.onSurfaceVariant }]}>
                      Grasa: {entry.bodyFatPercentage}%
                    </Text>
                  )}
                  {entry.muscleMassPercentage && (
                    <Text style={[styles.metricText, { color: colors.onSurfaceVariant }]}>
                      Músculo: {entry.muscleMassPercentage}%
                    </Text>
                  )}
                </View>
              </View>

              <View style={styles.entryActions}>
                <Text style={[styles.weightText, { color: colors.onSurface }]}>{entry.weight} kg</Text>
                <View style={styles.actionButtons}>
                  <TouchableOpacity onPress={() => onPressEdit(entry)}>
                    <Text style={[styles.actionText, { color: colors.primary }]}>Editar</Text>
                  </TouchableOpacity>
                  <TouchableOpacity onPress={() => onPressDelete(entry)}>
                    <Text style={[styles.deleteText, { color: colors.error }]}>Borrar</Text>
                  </TouchableOpacity>
                </View>
              </View>
            </View>
          ))}
        </View>
      )}

      {chartData.length > 1 && (
        <View style={styles.chartContainer}>
          <Text style={[styles.chartTitle, { color: colors.onSurfaceVariant }]}>Tendencia de peso</Text>
          <LineTrendChart data={chartData} height={140} />
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
    marginBottom: 16,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  addButton: {
    height: 36,
    minWidth: 100,
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 32,
  },
  emptyText: {
    fontSize: 14,
    textAlign: 'center',
    paddingHorizontal: 24,
  },
  entriesList: {
    marginBottom: 24,
  },
  entryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    paddingVertical: 12,
  },
  entryInfo: {
    flex: 1,
  },
  entryDate: {
    fontSize: 16,
    fontWeight: '500',
  },
  entryMetrics: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 4,
  },
  metricText: {
    fontSize: 12,
  },
  entryActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  weightText: {
    fontSize: 18,
    fontWeight: '700',
  },
  actionButtons: {
    flexDirection: 'row',
    gap: 16,
  },
  actionText: {
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  deleteText: {
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  chartContainer: {
    marginTop: 16,
  },
  chartTitle: {
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginBottom: 16,
    marginLeft: 4,
  },
});
