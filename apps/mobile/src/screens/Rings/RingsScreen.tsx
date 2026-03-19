import React, { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { BatteryRingCard } from '@/components/activity/BatteryRingCard';
import { AugeStatusCard } from '@/components/auge/AugeStatusCard';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import {
  ActivityIcon,
  BrainIcon,
  InfoIcon,
  IntertwinedRingsIcon,
  MoonIcon,
  SingleRingIcon,
  TrendingUpIcon,
} from '@/components/icons';
import { readStoredWellbeingPayload } from '@/services/mobileDomainStateService';
import { calculateRingsMetrics } from '@/services/ringsMetrics';
import { useAugeRuntimeStore } from '@/stores/augeRuntimeStore';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import { useWorkoutStore } from '@/stores/workoutStore';
import { useColors } from '@/theme';

type RingsMode = 'rings' | 'individual';

function StatCard({
  icon,
  value,
  label,
  accent,
}: {
  icon: React.ReactNode;
  value: string;
  label: string;
  accent: string;
}) {
  const colors = useColors();
  return (
    <LiquidGlassCard style={styles.statCard} padding={16}>
      <View style={styles.statIconWrap}>{icon}</View>
      <Text style={[styles.statValue, { color: accent }]}>{value}</Text>
      <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
    </LiquidGlassCard>
  );
}

function InfoCard({
  title,
  body,
  accent,
  footer,
}: {
  title: string;
  body: string;
  accent: string;
  footer: string;
}) {
  const colors = useColors();
  return (
    <LiquidGlassCard style={styles.infoCard} padding={20}>
      <View style={styles.infoHeader}>
        <View style={[styles.infoDot, { backgroundColor: accent }]} />
        <Text style={[styles.infoTitle, { color: colors.onSurface }]}>{title}</Text>
      </View>
      <Text style={[styles.infoBody, { color: colors.onSurfaceVariant }]}>{body}</Text>
      <View style={styles.infoFooter}>
        <TrendingUpIcon size={14} color={accent} />
        <Text style={[styles.infoFooterText, { color: accent }]}>{footer}</Text>
      </View>
    </LiquidGlassCard>
  );
}

export function RingsScreen() {
  const colors = useColors();
  const [viewMode, setViewMode] = useState<RingsMode>('individual');

  const augeStatus = useAugeRuntimeStore(state => state.status);
  const augeSnapshot = useAugeRuntimeStore(state => state.snapshot);
  const isRefreshingAuge = useAugeRuntimeStore(state => state.isRefreshing);
  const hydrateAuge = useAugeRuntimeStore(state => state.hydrateFromStorage);
  const recomputeAuge = useAugeRuntimeStore(state => state.recompute);

  const workoutStatus = useWorkoutStore(state => state.status);
  const workoutOverview = useWorkoutStore(state => state.overview);
  const hydrateWorkout = useWorkoutStore(state => state.hydrateFromMigration);

  const wellbeingStatus = useWellbeingStore(state => state.status);
  const wellbeingOverview = useWellbeingStore(state => state.overview);
  const hydrateWellbeing = useWellbeingStore(state => state.hydrateFromMigration);

  useEffect(() => {
    if (augeStatus === 'idle') {
      void hydrateAuge();
    }
  }, [augeStatus, hydrateAuge]);

  useEffect(() => {
    if (workoutStatus === 'idle') {
      void hydrateWorkout();
    }
  }, [hydrateWorkout, workoutStatus]);

  useEffect(() => {
    if (wellbeingStatus === 'idle') {
      void hydrateWellbeing();
    }
  }, [hydrateWellbeing, wellbeingStatus]);

  const battery = workoutOverview?.battery;

  const headerContent = (
    <View style={styles.header}>
      <View style={styles.headerBadgeRow}>
        <ActivityIcon size={18} color={colors.primary} />
        <Text style={[styles.headerEyebrow, { color: colors.onSurfaceVariant }]}>Biometría en tiempo real</Text>
      </View>
      <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Mis Rings</Text>
      <Text style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>
        Monitoreo de recuperación y estrés
      </Text>
    </View>
  );

  const sourceLabel = useMemo(() => {
    if (!battery) return 'sin datos todavía';
    return battery.source === 'wellbeing-derived' ? 'derivado desde wellbeing' : 'estimación RN';
  }, [battery]);

  const wellbeingMetrics = useMemo(() => calculateRingsMetrics(readStoredWellbeingPayload()), [
    wellbeingOverview,
    wellbeingStatus,
  ]);

  return (
    <ScreenShell
      title="Mis Rings"
      showBack={false}
      headerContent={headerContent}
      contentContainerStyle={styles.shellContent}
    >
      <View style={styles.container}>
        <View style={[styles.segmentedControl, { backgroundColor: `${colors.onSurface}10` }]}>
          <Pressable
            onPress={() => setViewMode('rings')}
            style={[styles.segmentedButton, viewMode === 'rings' && { backgroundColor: `${colors.onSurface}14` }]}
          >
            <IntertwinedRingsIcon size={18} color={viewMode === 'rings' ? colors.primary : colors.onSurfaceVariant} />
            <Text style={[styles.segmentedText, { color: viewMode === 'rings' ? colors.onSurface : colors.onSurfaceVariant }]}>Combinado</Text>
          </Pressable>
          <Pressable
            onPress={() => setViewMode('individual')}
            style={[styles.segmentedButton, viewMode === 'individual' && { backgroundColor: `${colors.onSurface}14` }]}
          >
            <SingleRingIcon size={18} color={viewMode === 'individual' ? colors.primary : colors.onSurfaceVariant} />
            <Text style={[styles.segmentedText, { color: viewMode === 'individual' ? colors.onSurface : colors.onSurfaceVariant }]}>Individual</Text>
          </Pressable>
        </View>

        {viewMode === 'rings' ? (
          <BatteryRingCard
            overallPct={battery?.overall ?? 0}
            cnsPct={battery?.cns ?? 0}
            muscularPct={battery?.muscular ?? 0}
            sourceLabel={sourceLabel}
          />
        ) : (
          <View style={styles.individualGrid}>
            <StatCard
              icon={<MoonIcon size={18} color={colors.primary} />}
              value={wellbeingMetrics.avgSleepHours !== null ? `${wellbeingMetrics.avgSleepHours}h` : '--'}
              label="Sueño prom."
              accent={colors.primary}
            />
            <StatCard
              icon={<BrainIcon size={18} color={colors.error} />}
              value={wellbeingMetrics.avgStressLevel !== null ? `${wellbeingMetrics.avgStressLevel}%` : '--'}
              label="Estrés prom."
              accent={colors.error}
            />
            <StatCard
              icon={<ActivityIcon size={18} color={colors.secondary} />}
              value={wellbeingMetrics.avgEnergyLevel !== null ? `${wellbeingMetrics.avgEnergyLevel}%` : '--'}
              label="Energía prom."
              accent={colors.secondary}
            />
          </View>
        )}

        <AugeStatusCard
          snapshot={augeSnapshot}
          isRefreshing={isRefreshingAuge}
          onRefresh={() => void recomputeAuge()}
        />

        <View style={styles.infoBlock}>
          <View style={styles.infoRow}>
            <InfoIcon size={16} color={colors.onSurfaceVariant} />
            <Text style={[styles.infoEyebrow, { color: colors.onSurfaceVariant }]}>Información de cada sistema</Text>
          </View>

          <InfoCard
            title="Sistema Muscular"
            body="Indica qué tan recuperados están tus músculos después del entrenamiento. Un nivel bajo significa que tus fibras necesitan descansar para evitar sobrecargas y estar listas para tu próximo reto."
            accent={colors.ringMuscular}
            footer="Se recupera en 24-72 horas pos-esfuerzo"
          />
          <InfoCard
            title="Sistema Nervioso Central"
            body="Es tu batería de coordinación y energía mental. Si está baja, puedes sentirte más lento de reflejos o con la mente cansada después de un día intenso o poco sueño."
            accent={colors.ringCns}
            footer="Depende directamente de la calidad del sueño"
          />
          <InfoCard
            title="Columna y Articulaciones"
            body="Mide el impacto acumulado en tu espalda y articulaciones. Te ayuda a saber cuándo es mejor bajar la carga para cuidar tu estructura y evitar molestias articulares."
            accent={colors.ringSpinal}
            footer="Impacto acumulativo de cargas pesadas"
          />
        </View>

        <LiquidGlassCard style={styles.placeholderCard} padding={20}>
          <View style={styles.placeholderIcon}>
            <ActivityIcon size={24} color={colors.secondary} />
          </View>
          <Text style={[styles.placeholderTitle, { color: colors.onSurface }]}>Google Health Connect</Text>
          <Text style={[styles.placeholderBody, { color: colors.onSurfaceVariant }]}>
            Sincroniza tus datos de sueño, actividad y frecuencia cardíaca para una medición más precisa.
          </Text>
          <View style={[styles.placeholderButton, { backgroundColor: `${colors.secondary}20` }]}>
            <Text style={[styles.placeholderButtonText, { color: colors.secondary }]}>Próximamente</Text>
          </View>
        </LiquidGlassCard>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  shellContent: {
    paddingTop: 8,
  },
  header: {
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 8,
  },
  headerBadgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 10,
  },
  headerEyebrow: {
    fontSize: 9,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: '900',
    letterSpacing: -0.9,
  },
  headerSubtitle: {
    marginTop: 6,
    fontSize: 12,
    lineHeight: 18,
  },
  container: {
    gap: 18,
  },
  segmentedControl: {
    flexDirection: 'row',
    alignSelf: 'center',
    padding: 4,
    borderRadius: 999,
    gap: 4,
  },
  segmentedButton: {
    minWidth: 132,
    minHeight: 42,
    borderRadius: 999,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  segmentedText: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.2,
  },
  individualGrid: {
    flexDirection: 'row',
    gap: 12,
  },
  statCard: {
    flex: 1,
    borderRadius: 26,
    alignItems: 'center',
    gap: 8,
  },
  statIconWrap: {
    marginTop: 2,
  },
  statValue: {
    fontSize: 24,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  statLabel: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1.1,
    textTransform: 'uppercase',
  },
  infoBlock: {
    gap: 12,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 2,
  },
  infoEyebrow: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },
  infoCard: {
    borderRadius: 28,
  },
  infoHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  infoDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  infoTitle: {
    fontSize: 13,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 0.7,
  },
  infoBody: {
    marginTop: 12,
    fontSize: 13,
    lineHeight: 20,
  },
  infoFooter: {
    marginTop: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  infoFooterText: {
    flex: 1,
    fontSize: 11,
    fontWeight: '700',
  },
  placeholderCard: {
    borderRadius: 28,
    alignItems: 'center',
  },
  placeholderIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  placeholderTitle: {
    marginTop: 12,
    fontSize: 14,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  placeholderBody: {
    marginTop: 8,
    fontSize: 12,
    lineHeight: 18,
    textAlign: 'center',
  },
  placeholderButton: {
    marginTop: 14,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 999,
  },
  placeholderButtonText: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },
});
