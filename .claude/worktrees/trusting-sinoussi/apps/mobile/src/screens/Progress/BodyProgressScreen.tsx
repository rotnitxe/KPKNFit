import React, { useEffect, useMemo, useState, useCallback } from 'react';
import { StyleSheet, Text, View, FlatList, TouchableOpacity, ActivityIndicator } from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { BodyMetricsCarousel } from '@/components/body/BodyMetricsCarousel';
import { BaseChartWrapper } from '@/components/charts/BaseChartWrapper';
import { BodyWeightChart } from '@/components/charts/BodyWeightChart';
import { BodyFatChart } from '@/components/charts/BodyFatChart';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { AddBodyLogModal } from '@/components/body/AddBodyLogModal';
import { Button } from '@/components/ui';
import GoalProjection from '@/components/analytics/GoalProjection';
import { useBodyProgressData } from '@/data/useBodyProgressData';
import { useBodyStore } from '@/stores/bodyStore';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import { useColors } from '@/theme';
import { computeTrendDirection } from '@/utils/calculations';
import type { BodyProgressEntry } from '@/types/workout';

function computeFfmi(weightKg?: number, bodyFatPct?: number, heightCm?: number) {
  if (!weightKg || !bodyFatPct || !heightCm) return undefined;
  const leanMassKg = weightKg * (1 - bodyFatPct / 100);
  const heightMeters = heightCm / 100;
  if (!heightMeters) return undefined;
  return leanMassKg / (heightMeters * heightMeters);
}

export function BodyProgressScreen() {
  const colors = useColors();
  const status = useBodyStore(state => state.status);
  const hydrateBody = useBodyStore(state => state.hydrateFromMigration);
  const { logs, latestEntry } = useBodyProgressData();
  const rawSettings = readStoredSettingsRaw() as any;
  const savedLogs = useMobileNutritionStore(state => state.savedLogs);

  const [isModalVisible, setModalVisible] = useState(false);

  useEffect(() => {
    if (status === 'idle') {
      void hydrateBody();
    }
  }, [hydrateBody, status]);

  const weightTrendData = useMemo(
    () =>
      logs
        .filter(entry => typeof entry.weight === 'number')
        .map(entry => ({
          dateMs: Date.parse(entry.date),
          weight: entry.weight as number,
        }))
        .reverse(),
    [logs],
  );

  const bodyFatTrendData = useMemo(
    () =>
      logs
        .filter(entry => typeof entry.bodyFatPercentage === 'number')
        .map(entry => ({
          dateMs: Date.parse(entry.date),
          bodyFat: entry.bodyFatPercentage as number,
        }))
        .reverse(),
    [logs],
  );

  const ffmi = useMemo(
    () =>
      computeFfmi(
        latestEntry?.weight,
        latestEntry?.bodyFatPercentage,
        rawSettings.userVitals?.height as number | undefined,
      ),
    [latestEntry?.bodyFatPercentage, latestEntry?.weight, rawSettings.userVitals?.height],
  );

  const weightTrendDirection = useMemo(() => {
    const weights = logs.map(l => l.weight).filter((w): w is number => typeof w === 'number');
    return computeTrendDirection(weights);
  }, [logs]);

  const hasChartData = weightTrendData.length > 0 || bodyFatTrendData.length > 0;

  const targetWeight = rawSettings.userVitals?.targetWeight as number | undefined;

  const renderHistoryItem = useCallback(({ item }: { item: BodyProgressEntry }) => {
    const dateStr = new Date(item.date).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
    return (
      <View style={[styles.historyItem, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
        <View style={styles.historyItemLeft}>
          <Text style={[styles.historyDate, { color: colors.onSurface }]}>{dateStr}</Text>
          <Text style={[styles.historyDetails, { color: colors.onSurfaceVariant }]}>
            {item.weight ? `${item.weight} kg` : '--'}
            {item.bodyFatPercentage ? ` · ${item.bodyFatPercentage}% grasa` : ''}
            {item.muscleMassPercentage ? ` · ${item.muscleMassPercentage}% músculo` : ''}
          </Text>
        </View>
      </View>
    );
  }, [colors]);

  const keyExtractor = useCallback((item: BodyProgressEntry) => item.id, []);

  return (
    <ScreenShell title="Progreso corporal" subtitle="Métricas reales importadas y editables desde RN.">
      <View style={styles.content}>
        <BodyMetricsCarousel
          weight={latestEntry?.weight}
          bodyFat={latestEntry?.bodyFatPercentage}
          ffmi={ffmi}
        />

        {/* Trend indicator */}
        {weightTrendDirection !== 'stable' && (
          <View style={[styles.trendBanner, { backgroundColor: colors.primaryContainer }]}>
            <Text style={[styles.trendText, { color: colors.onPrimaryContainer }]}>
              {weightTrendDirection === 'up' ? '↑ Subiendo' : '↓ Bajando'} en peso (últimos 7 registros)
            </Text>
          </View>
        )}

        {/* Add button */}
        <Button onPress={() => setModalVisible(true)} style={styles.addButton}>
          Registrar Medida
        </Button>

        {!hasChartData ? (
          <LiquidGlassCard style={styles.emptyCard} padding={20}>
            <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>
              Todavía no hay suficientes registros corporales
            </Text>
            <Text style={[styles.emptyBody, { color: colors.onSurfaceVariant }]}>
              En cuanto importemos o registremos más mediciones, esta vista va a verse como la PWA: peso, grasa corporal y tendencias en la misma pantalla.
            </Text>
          </LiquidGlassCard>
        ) : null}

        {weightTrendData.length > 0 ? (
          <BaseChartWrapper title="Tendencia de peso" subtitle="Historial importado">
            <BodyWeightChart data={weightTrendData} targetWeight={targetWeight} />
          </BaseChartWrapper>
        ) : null}

        {bodyFatTrendData.length > 0 ? (
          <BaseChartWrapper title="Grasa corporal" subtitle="Evolución (%)">
            <BodyFatChart data={bodyFatTrendData} />
          </BaseChartWrapper>
        ) : null}

        <GoalProjection
          bodyProgress={logs}
          settings={rawSettings as any}
          nutritionLogs={savedLogs as any}
        />

        {/* History list */}
        {logs.length > 0 && (
          <BaseChartWrapper title="Historial" subtitle="Registros anteriores">
            <FlatList
              data={logs.slice(0, 10)}
              renderItem={renderHistoryItem}
              keyExtractor={keyExtractor}
              scrollEnabled={false}
              showsVerticalScrollIndicator={false}
            />
          </BaseChartWrapper>
        )}
      </View>

      <AddBodyLogModal
        visible={isModalVisible}
        onClose={() => setModalVisible(false)}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: 16,
  },
  emptyCard: {
    borderRadius: 28,
  },
  emptyTitle: {
    fontSize: 22,
    fontWeight: '800',
  },
  emptyBody: {
    marginTop: 10,
    fontSize: 14,
    lineHeight: 22,
  },
  trendBanner: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  trendText: {
    fontSize: 12,
    fontWeight: '600',
  },
  addButton: {
    marginTop: 8,
  },
  historyItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 8,
  },
  historyItemLeft: {
    flex: 1,
  },
  historyDate: {
    fontSize: 14,
    fontWeight: '600',
  },
  historyDetails: {
    fontSize: 12,
    marginTop: 2,
  },
});
