import React, { useEffect, useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { BodyMetricsCarousel } from '@/components/body/BodyMetricsCarousel';
import { BaseChartWrapper } from '@/components/charts/BaseChartWrapper';
import { LineChartBase } from '@/components/charts/LineChartBase';
import { BarChartBase } from '@/components/charts/BarChartBase';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { useBodyProgressData } from '@/data/useBodyProgressData';
import { useBodyStore } from '@/stores/bodyStore';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import { useColors } from '@/theme';

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
  const rawSettings = readStoredSettingsRaw();

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
    () => computeFfmi(latestEntry?.weight, latestEntry?.bodyFatPercentage, rawSettings.height as number | undefined),
    [latestEntry?.bodyFatPercentage, latestEntry?.weight, rawSettings.height],
  );

  const hasChartData = weightTrendData.length > 0 || bodyFatTrendData.length > 0;

  return (
    <ScreenShell title="Progreso corporal" subtitle="Métricas reales importadas y editables desde RN.">
      <View style={styles.content}>
        <BodyMetricsCarousel
          weight={latestEntry?.weight}
          bodyFat={latestEntry?.bodyFatPercentage}
          ffmi={ffmi}
        />

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
            <LineChartBase data={weightTrendData} xKey="dateMs" yKeys={['weight']} />
          </BaseChartWrapper>
        ) : null}

        {bodyFatTrendData.length > 0 ? (
          <BaseChartWrapper title="Grasa corporal" subtitle="Evolución (%)">
            <BarChartBase data={bodyFatTrendData} xKey="dateMs" yKeys={['bodyFat']} />
          </BaseChartWrapper>
        ) : null}
      </View>
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
});
