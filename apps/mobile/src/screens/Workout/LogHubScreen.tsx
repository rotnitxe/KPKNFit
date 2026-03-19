import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '../../components/ScreenShell';
import { useColors } from '../../theme';
import { loadLocalWorkoutLogs } from '../../services/mobilePersistenceService';
import { useWorkoutStore } from '../../stores/workoutStore';
import type { WorkoutStackParamList } from '../../navigation/types';
import type { WorkoutLogSummary } from '@kpkn/shared-types';
import { formatRelativeDate } from '../../utils/dateUtils';

type WorkoutNav = NativeStackNavigationProp<WorkoutStackParamList>;

function StatCard({ label, value }: { label: string; value: string }) {
  const colors = useColors();
  return (
    <View style={[styles.statCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
      <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <Text style={[styles.statValue, { color: colors.onSurface }]}>{value}</Text>
    </View>
  );
}

export function LogHubScreen() {
  const colors = useColors();
  const navigation = useNavigation<WorkoutNav>();
  const history = useWorkoutStore(state => state.history);
  const skippedLogs = useWorkoutStore(state => state.skippedLogs);
  const syncQueue = useWorkoutStore(state => state.syncQueue);
  const refreshInfrastructure = useWorkoutStore(state => state.refreshInfrastructure);
  const [localLogs, setLocalLogs] = useState<WorkoutLogSummary[]>([]);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const logs = await loadLocalWorkoutLogs(80);
      setLocalLogs(logs);
      await refreshInfrastructure();
    } finally {
      setLoading(false);
    }
  }, [refreshInfrastructure]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const grouped = useMemo(() => {
    const map = new Map<string, WorkoutLogSummary[]>();
    for (const log of localLogs) {
      const group = map.get(log.date) ?? [];
      group.push(log);
      map.set(log.date, group);
    }
    return Array.from(map.entries()).sort((a, b) => b[0].localeCompare(a[0]));
  }, [localLogs]);

  return (
    <ScreenShell
      title="Log Hub"
      subtitle="Historial y control de registros de entrenamiento"
    >
      <View style={styles.container}>
        <View style={styles.actionsRow}>
          <Pressable
            onPress={() => navigation.navigate('LogWorkout')}
            style={[styles.primaryAction, { backgroundColor: colors.primary }]}
          >
            <Text style={[styles.primaryActionText, { color: colors.onPrimary }]}>Registrar entreno manual</Text>
          </Pressable>
          <Pressable
            onPress={() => void reload()}
            style={[styles.secondaryAction, { borderColor: `${colors.onSurface}33` }]}
          >
            <Text style={[styles.secondaryActionText, { color: colors.onSurface }]}>
              {loading ? 'Actualizando...' : 'Actualizar'}
            </Text>
          </Pressable>
        </View>

        <View style={styles.statsGrid}>
          <StatCard label="Logs locales" value={String(localLogs.length)} />
          <StatCard label="History store" value={String(history.length)} />
          <StatCard label="Skip logs" value={String(skippedLogs.length)} />
          <StatCard label="Sync queue" value={String(syncQueue.length)} />
        </View>

        <View style={[styles.block, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.blockTitle, { color: colors.onSurface }]}>Entrenos registrados</Text>
          <Text style={[styles.blockSubtitle, { color: colors.onSurfaceVariant }]}>
            Vista consolidada de los logs guardados localmente en RN.
          </Text>
          {grouped.length === 0 ? (
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
              Aún no hay entrenos registrados en esta app RN.
            </Text>
          ) : (
            grouped.slice(0, 20).map(([date, logs]) => (
              <View key={date} style={styles.dayGroup}>
                <Text style={[styles.dayTitle, { color: colors.onSurface }]}>
                  {date} · {formatRelativeDate(date)}
                </Text>
                {logs.slice(0, 6).map(log => (
                  <View key={log.id} style={[styles.logRow, { borderBottomColor: `${colors.onSurface}14` }]}>
                    <Text style={[styles.logSession, { color: colors.onSurface }]} numberOfLines={1}>
                      {log.sessionName}
                    </Text>
                    <Text style={[styles.logMeta, { color: colors.onSurfaceVariant }]}>
                      {log.exerciseCount} ej · {log.completedSetCount} sets
                    </Text>
                  </View>
                ))}
              </View>
            ))
          )}
        </View>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 14,
  },
  actionsRow: {
    flexDirection: 'row',
    gap: 10,
  },
  primaryAction: {
    flex: 1,
    minHeight: 48,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 14,
  },
  primaryActionText: {
    fontSize: 13,
    fontWeight: '800',
  },
  secondaryAction: {
    minHeight: 48,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 14,
    borderWidth: 1,
  },
  secondaryActionText: {
    fontSize: 13,
    fontWeight: '700',
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  statCard: {
    width: '48%',
    borderRadius: 16,
    borderWidth: 1,
    padding: 12,
    gap: 4,
  },
  statLabel: {
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1.2,
  },
  statValue: {
    fontSize: 22,
    fontWeight: '900',
  },
  block: {
    borderRadius: 18,
    borderWidth: 1,
    padding: 14,
    gap: 6,
  },
  blockTitle: {
    fontSize: 18,
    fontWeight: '800',
  },
  blockSubtitle: {
    fontSize: 12,
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 13,
    marginTop: 10,
  },
  dayGroup: {
    marginTop: 12,
    gap: 2,
  },
  dayTitle: {
    fontSize: 13,
    fontWeight: '800',
    marginBottom: 2,
  },
  logRow: {
    paddingVertical: 8,
    borderBottomWidth: 1,
    gap: 2,
  },
  logSession: {
    fontSize: 14,
    fontWeight: '700',
  },
  logMeta: {
    fontSize: 12,
  },
});

