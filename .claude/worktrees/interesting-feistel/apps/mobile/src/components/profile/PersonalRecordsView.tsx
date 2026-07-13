import React, { useMemo, useState } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { LiquidGlassCard } from '../../components/ui/LiquidGlassCard';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { useColors } from '../../theme';
import { calculateBrzycki1RM } from '../../utils/calculations';
import { readStoredSettingsRaw } from '../../services/mobileDomainStateService';

type FilterType = 'all' | 'basics' | 'program';
type SortType = 'weight' | 'relative' | 'date';

interface PREntry {
  exerciseId: string;
  exerciseName: string;
  bestWeight: number;
  bestReps: number;
  bestE1RM: number;
  date: string;
  isNew: boolean;
}

function PRCard({ pr, onPress }: { pr: PREntry; onPress: () => void }) {
  const colors = useColors();

  return (
    <TouchableOpacity onPress={onPress}>
      <LiquidGlassCard
        style={prCardStyles.card}
        padding={16}
      >
        <View style={prCardStyles.header}>
          <Text style={[prCardStyles.name, { color: colors.onSurface }]} numberOfLines={1}>
            {pr.exerciseName}
          </Text>
          {pr.isNew && (
            <View style={[prCardStyles.badge, { backgroundColor: colors.primary }]}>
              <Text style={prCardStyles.badgeText}>NUEVO</Text>
            </View>
          )}
        </View>
        <View style={prCardStyles.values}>
          <View style={prCardStyles.valueItem}>
            <Text style={[prCardStyles.value, { color: colors.primary }]}>{pr.bestWeight}</Text>
            <Text style={[prCardStyles.unit, { color: colors.onSurfaceVariant }]}>kg</Text>
          </View>
          <Text style={[prCardStyles.reps, { color: colors.onSurfaceVariant }]}>× {pr.bestReps} reps</Text>
          <View style={prCardStyles.e1rm}>
            <Text style={[prCardStyles.e1rmLabel, { color: colors.onSurfaceVariant }]}>e1RM</Text>
            <Text style={[prCardStyles.e1rmValue, { color: colors.secondary }]}>{pr.bestE1RM.toFixed(1)}</Text>
          </View>
        </View>
        <Text style={[prCardStyles.date, { color: colors.onSurfaceVariant }]}>
          {new Date(pr.date).toLocaleDateString('es-CL')}
        </Text>
      </LiquidGlassCard>
    </TouchableOpacity>
  );
}

const prCardStyles = StyleSheet.create({
  card: {
    borderRadius: 16,
    marginBottom: 12,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  name: {
    fontSize: 14,
    fontWeight: '800',
    flex: 1,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
  },
  badgeText: {
    fontSize: 8,
    fontWeight: '900',
    color: '#fff',
  },
  values: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  valueItem: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  value: {
    fontSize: 24,
    fontWeight: '900',
  },
  unit: {
    fontSize: 12,
    fontWeight: '700',
    marginLeft: 2,
  },
  reps: {
    fontSize: 14,
    fontWeight: '600',
  },
  e1rm: {
    marginLeft: 'auto',
    alignItems: 'flex-end',
  },
  e1rmLabel: {
    fontSize: 8,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  e1rmValue: {
    fontSize: 16,
    fontWeight: '900',
  },
  date: {
    fontSize: 10,
    fontWeight: '600',
    marginTop: 8,
  },
});

function Big3Card({ title, weight }: { title: string; weight: number }) {
  const colors = useColors();
  return (
    <LiquidGlassCard style={big3Styles.card} padding={12}>
      <Text style={[big3Styles.title, { color: colors.onSurfaceVariant }]}>{title}</Text>
      <Text style={[big3Styles.weight, { color: colors.primary }]}>{weight}</Text>
      <Text style={[big3Styles.unit, { color: colors.onSurfaceVariant }]}>kg</Text>
    </LiquidGlassCard>
  );
}

const big3Styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    flex: 1,
    alignItems: 'center',
  },
  title: {
    fontSize: 9,
    fontWeight: '800',
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  weight: {
    fontSize: 22,
    fontWeight: '900',
  },
  unit: {
    fontSize: 10,
    fontWeight: '700',
  },
});

function FilterChip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  const colors = useColors();
  return (
    <TouchableOpacity
      onPress={onPress}
      style={[
        chipStyles.chip,
        { backgroundColor: active ? colors.primary : 'transparent', borderColor: colors.primary },
      ]}
    >
      <Text
        style={[
          chipStyles.label,
          { color: active ? '#fff' : colors.onSurface },
        ]}
      >
        {label}
      </Text>
    </TouchableOpacity>
  );
}

const chipStyles = StyleSheet.create({
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
  },
  label: {
    fontSize: 11,
    fontWeight: '700',
  },
});

export function PersonalRecordsView() {
  const colors = useColors();
  const history = useWorkoutStore(state => state.history);
  const settingsSummary = useSettingsStore(state => state.summary);
  const rawSettings = settingsSummary ?? (readStoredSettingsRaw() as any);
  const weight = rawSettings.userVitals?.weight;

  const [filter, setFilter] = useState<FilterType>('all');
  const [sort, setSort] = useState<SortType>('weight');

  const prs = useMemo(() => {
    const exercisePRs: Record<string, PREntry> = {};
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    history.forEach(log => {
      const logDate = new Date(log.date);
      log.completedExercises.forEach(ex => {
        const maxSet = ex.sets.reduce(
          (best, set) => {
            const e1rm = calculateBrzycki1RM(set.weight || 0, set.completedReps || 0);
            const reps = set.completedReps || 0;
            return e1rm > best.e1rm ? { e1rm, weight: set.weight || 0, reps } : best;
          },
          { e1rm: 0, weight: 0, reps: 0 }
        );

        if (maxSet.e1rm === 0) return;

        const existing = exercisePRs[ex.exerciseName];
        const isNew = logDate >= thirtyDaysAgo;

        if (!existing || maxSet.e1rm > existing.bestE1RM) {
          exercisePRs[ex.exerciseName] = {
            exerciseId: ex.exerciseDbId || ex.exerciseId || '',
            exerciseName: ex.exerciseName,
            bestWeight: maxSet.weight,
            bestReps: maxSet.reps,
            bestE1RM: maxSet.e1rm,
            date: log.date,
            isNew,
          };
        }
      });
    });

    let prList = Object.values(exercisePRs);

    if (filter === 'basics') {
      const basics = ['sentadilla', 'press de banca', 'banca', 'peso muerto', 'deadlift', 'pesas'];
      prList = prList.filter(pr =>
        basics.some(b => pr.exerciseName.toLowerCase().includes(b))
      );
    }

    switch (sort) {
      case 'weight':
        prList.sort((a, b) => b.bestWeight - a.bestWeight);
        break;
      case 'relative':
        if (weight) {
          prList.sort((a, b) => b.bestWeight / weight - a.bestWeight / weight);
        }
        break;
      case 'date':
        prList.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
        break;
    }

    return prList;
  }, [history, filter, sort, weight]);

  const big3 = useMemo(() => {
    const getLift = (keywords: string[]) =>
      prs.find(pr => keywords.some(k => pr.exerciseName.toLowerCase().includes(k)));

    return {
      squat: getLift(['sentadilla', 'squat'])?.bestWeight || 0,
      bench: getLift(['press de banca', 'banca', 'bench'])?.bestWeight || 0,
      deadlift: getLift(['peso muerto', 'deadlift'])?.bestWeight || 0,
    };
  }, [prs]);

  const total = big3.squat + big3.bench + big3.deadlift;

  return (
    <ScreenShell title="Records Personales" subtitle="Tu mejor historial de lifts">
      <View style={styles.container}>
        <View style={styles.big3Section}>
          <Big3Card title="Sentadilla" weight={big3.squat} />
          <Big3Card title="Banca" weight={big3.bench} />
          <Big3Card title="P. Muerto" weight={big3.deadlift} />
        </View>

        {total > 0 && (
          <LiquidGlassCard style={styles.totalCard} padding={12}>
            <Text style={[styles.totalLabel, { color: colors.onSurfaceVariant }]}>Total Combinado</Text>
            <Text style={[styles.totalValue, { color: colors.primary }]}>{total} kg</Text>
          </LiquidGlassCard>
        )}

        <View style={styles.filters}>
          <View style={styles.filterRow}>
            <FilterChip label="Todos" active={filter === 'all'} onPress={() => setFilter('all')} />
            <FilterChip label="Básicos" active={filter === 'basics'} onPress={() => setFilter('basics')} />
          </View>
          <View style={styles.filterRow}>
            <FilterChip
              label="Peso"
              active={sort === 'weight'}
              onPress={() => setSort('weight')}
            />
            <FilterChip
              label="Relativo"
              active={sort === 'relative'}
              onPress={() => setSort('relative')}
            />
            <FilterChip label="Fecha" active={sort === 'date'} onPress={() => setSort('date')} />
          </View>
        </View>

        <FlatList
          data={prs}
          keyExtractor={item => item.exerciseId || item.exerciseName}
          renderItem={({ item }) => <PRCard pr={item} onPress={() => {}} />}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <View style={styles.empty}>
              <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
                Aún no hay récords para mostrar. Marca tus ejercicios principales como favoritos (⭐) para que aparezcan aquí.
              </Text>
            </View>
          }
        />
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  big3Section: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 16,
    paddingHorizontal: 16,
  },
  totalCard: {
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 16,
    alignItems: 'center',
  },
  totalLabel: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  totalValue: {
    fontSize: 32,
    fontWeight: '900',
  },
  filters: {
    paddingHorizontal: 16,
    marginBottom: 16,
    gap: 8,
  },
  filterRow: {
    flexDirection: 'row',
    gap: 8,
  },
  list: {
    paddingHorizontal: 16,
    paddingBottom: 40,
  },
  empty: {
    padding: 40,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 14,
    textAlign: 'center',
  },
});
