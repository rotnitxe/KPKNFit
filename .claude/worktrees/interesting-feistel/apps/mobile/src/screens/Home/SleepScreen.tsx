import React, { useEffect, useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { SleepTrackerWidget } from '@/components/analytics/SleepTrackerWidget';
import { readStoredWellbeingPayload } from '@/services/mobileDomainStateService';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useColors } from '@/theme';

type SleepLogEntry = { id?: string; date: string; duration?: number; quality?: number };

function isSleepLogEntry(value: unknown): value is SleepLogEntry {
  return typeof value === 'object' && value !== null && typeof (value as SleepLogEntry).date === 'string';
}

function formatDate(date: string) {
  const value = new Date(date);
  if (Number.isNaN(value.getTime())) return date;
  return value.toLocaleDateString('es-CL', {
    day: '2-digit',
    month: 'short',
  });
}

export function SleepScreen() {
  const colors = useColors();
  const status = useWellbeingStore(state => state.status);
  const hydrateWellbeing = useWellbeingStore(state => state.hydrateFromMigration);
  const overview = useWellbeingStore(state => state.overview);

  useEffect(() => {
    if (status === 'idle') void hydrateWellbeing();
  }, [hydrateWellbeing, status]);

  const sleepLogs = useMemo(() => {
    const payload = readStoredWellbeingPayload();
    return [...(payload.sleepLogs ?? [])]
      .filter(isSleepLogEntry)
      .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
      .slice(0, 5);
  }, [overview]);

  const qualityAverage = useMemo(() => {
    const withQuality = sleepLogs.filter(log => typeof log.quality === 'number');
    if (withQuality.length === 0) return null;
    const total = withQuality.reduce((sum, log) => sum + Number(log.quality ?? 0), 0);
    return total / withQuality.length;
  }, [sleepLogs]);

  return (
    <ScreenShell
      title="Sueno"
      subtitle="Consistencia, duracion y ultimos registros del modulo wellbeing."
    >
      <View style={styles.container}>
        <LiquidGlassCard style={styles.summaryCard} padding={20}>
          <Text style={[styles.eyebrow, { color: colors.onSurfaceVariant }]}>Resumen</Text>
          <View style={styles.summaryGrid}>
            <View style={styles.summaryMetric}>
              <Text style={[styles.summaryValue, { color: colors.onSurface }]}>
                {overview?.averageSleepHoursLast7Days
                  ? `${overview.averageSleepHoursLast7Days}h`
                  : '--'}
              </Text>
              <Text style={[styles.summaryLabel, { color: colors.onSurfaceVariant }]}>Promedio 7d</Text>
            </View>
            <View style={styles.summaryMetric}>
              <Text style={[styles.summaryValue, { color: colors.onSurface }]}>
                {qualityAverage ? `${qualityAverage.toFixed(1)}/10` : '--'}
              </Text>
              <Text style={[styles.summaryLabel, { color: colors.onSurfaceVariant }]}>Calidad reciente</Text>
            </View>
            <View style={styles.summaryMetric}>
              <Text style={[styles.summaryValue, { color: colors.onSurface }]}>
                {overview?.sleepEntriesLast7Days ?? 0}
              </Text>
              <Text style={[styles.summaryLabel, { color: colors.onSurfaceVariant }]}>Registros 7d</Text>
            </View>
          </View>
        </LiquidGlassCard>

        <SleepTrackerWidget />

        <LiquidGlassCard style={styles.logCard} padding={20}>
          <Text style={[styles.eyebrow, { color: colors.onSurfaceVariant }]}>Ultimos registros</Text>
          {sleepLogs.length === 0 ? (
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
              Aun no hay noches registradas. Usa el widget de arriba para empezar a construir la tendencia.
            </Text>
          ) : (
            <View style={styles.logList}>
              {sleepLogs.map(log => (
                <View
                  key={String(log.id ?? log.date)}
                  style={[styles.logRow, { borderBottomColor: `${colors.outlineVariant}55` }]}
                >
                  <View>
                    <Text style={[styles.logDate, { color: colors.onSurface }]}>{formatDate(log.date)}</Text>
                    <Text style={[styles.logMeta, { color: colors.onSurfaceVariant }]}>
                      Calidad {typeof log.quality === 'number' ? `${log.quality}/10` : 'sin nota'}
                    </Text>
                  </View>
                  <Text style={[styles.logHours, { color: colors.primary }]}>
                    {typeof log.duration === 'number' ? `${log.duration}h` : '--'}
                  </Text>
                </View>
              ))}
            </View>
          )}
        </LiquidGlassCard>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  summaryCard: {
    borderRadius: 28,
  },
  eyebrow: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.6,
    marginBottom: 14,
  },
  summaryGrid: {
    flexDirection: 'row',
    gap: 10,
  },
  summaryMetric: {
    flex: 1,
  },
  summaryValue: {
    fontSize: 26,
    fontWeight: '900',
  },
  summaryLabel: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  logCard: {
    borderRadius: 28,
  },
  logList: {
    gap: 12,
  },
  logRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingBottom: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  logDate: {
    fontSize: 15,
    fontWeight: '700',
  },
  logMeta: {
    fontSize: 12,
    marginTop: 2,
  },
  logHours: {
    fontSize: 18,
    fontWeight: '900',
  },
  emptyText: {
    fontSize: 14,
    lineHeight: 20,
  },
});
