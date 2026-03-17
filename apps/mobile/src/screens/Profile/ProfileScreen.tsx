import React, { useEffect, useMemo } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '@/components/ScreenShell';
import { CaupolicanIcon } from '@/components/CaupolicanIcon';
import { BodyMetricsCarousel } from '@/components/body/BodyMetricsCarousel';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { Button } from '@/components/ui/Button';
import {
  ActivityIcon,
  CameraIcon,
  EditIcon,
  InfoIcon,
  MoonIcon,
  UserBadgeIcon,
} from '@/components/icons';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import { useBodyStore } from '@/stores/bodyStore';
import { useSettingsStore } from '@/stores/settingsStore';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useColors } from '@/theme';
import type { ProfileStackParamList } from '@/navigation/types';

function computeFfmi(weightKg?: number, bodyFatPct?: number, heightCm?: number) {
  if (!weightKg || !bodyFatPct || !heightCm) return null;
  const leanMassKg = weightKg * (1 - bodyFatPct / 100);
  const heightMeters = heightCm / 100;
  if (!heightMeters) return null;
  return leanMassKg / (heightMeters * heightMeters);
}

function athleteTier(ffmi: number | null) {
  if (!ffmi) return 'Perfil general';
  if (ffmi >= 24) return 'Élite';
  if (ffmi >= 22) return 'Avanzado';
  if (ffmi >= 20) return 'Intermedio';
  return 'Base';
}

function StatBox({
  label,
  value,
  unit,
}: {
  label: string;
  value: string;
  unit?: string;
}) {
  const colors = useColors();
  return (
    <LiquidGlassCard style={styles.statBox} padding={16}>
      <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <View style={styles.statValueRow}>
        <Text style={[styles.statValue, { color: colors.onSurface }]}>{value}</Text>
        {unit ? <Text style={[styles.statUnit, { color: colors.onSurfaceVariant }]}>{unit}</Text> : null}
      </View>
    </LiquidGlassCard>
  );
}

export function ProfileScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<ProfileStackParamList>>();
  const colors = useColors();
  const rawSettings = readStoredSettingsRaw();

  const settingsStatus = useSettingsStore(state => state.status);
  const hydrateSettings = useSettingsStore(state => state.hydrateFromMigration);

  const bodyStatus = useBodyStore(state => state.status);
  const bodyProgress = useBodyStore(state => state.bodyProgress);
  const bodyLabAnalysis = useBodyStore(state => state.bodyLabAnalysis);
  const hydrateBody = useBodyStore(state => state.hydrateFromMigration);

  const wellbeingStatus = useWellbeingStore(state => state.status);
  const wellbeingOverview = useWellbeingStore(state => state.overview);
  const hydrateWellbeing = useWellbeingStore(state => state.hydrateFromMigration);

  useEffect(() => {
    if (settingsStatus === 'idle') {
      void hydrateSettings();
    }
  }, [hydrateSettings, settingsStatus]);

  useEffect(() => {
    if (bodyStatus === 'idle') {
      void hydrateBody();
    }
  }, [bodyStatus, hydrateBody]);

  useEffect(() => {
    if (wellbeingStatus === 'idle') {
      void hydrateWellbeing();
    }
  }, [hydrateWellbeing, wellbeingStatus]);

  const latestBody = bodyProgress[0];
  const ffmi = useMemo(
    () =>
      computeFfmi(
        latestBody?.weight,
        latestBody?.bodyFatPercentage,
        typeof rawSettings.height === 'number' ? rawSettings.height : undefined,
      ),
    [latestBody?.bodyFatPercentage, latestBody?.weight, rawSettings.height],
  );

  const athleteName =
    typeof rawSettings.userName === 'string' && rawSettings.userName.trim() !== ''
      ? rawSettings.userName.trim()
      : 'Atleta KPKN';
  const subtitle = athleteTier(ffmi);

  const headerContent = (
    <View style={styles.header}>
      <View style={styles.badgeRow}>
        <UserBadgeIcon size={18} color={colors.primary} />
        <Text style={[styles.headerEyebrow, { color: colors.onSurfaceVariant }]}>Athlete ID</Text>
      </View>
      <Text style={[styles.headerTitle, { color: colors.onSurface }]}>{athleteName}</Text>
      <Text style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>
        {subtitle}
      </Text>
    </View>
  );

  return (
    <ScreenShell
      title={athleteName}
      showBack={false}
      headerContent={headerContent}
      contentContainerStyle={styles.shellContent}
    >
      <View style={styles.container}>
        <LiquidGlassCard style={styles.heroCard} padding={22}>
          <View style={styles.heroTop}>
            <View style={[styles.avatarCircle, { backgroundColor: `${colors.onSurface}10` }]}>
              <CaupolicanIcon size={44} color={colors.primary} />
            </View>

            <View style={styles.heroActions}>
              <Pressable style={[styles.heroAction, { backgroundColor: `${colors.onSurface}10` }]}>
                <CameraIcon size={18} color={colors.onSurfaceVariant} />
              </Pressable>
              <Pressable style={[styles.heroAction, { backgroundColor: `${colors.onSurface}10` }]}>
                <EditIcon size={18} color={colors.onSurfaceVariant} />
              </Pressable>
            </View>
          </View>

          <Text style={[styles.heroTier, { color: colors.primary }]}>{subtitle}</Text>
          <Text style={[styles.heroSummary, { color: colors.onSurfaceVariant }]}>
            {bodyLabAnalysis?.profileSummary ||
              'Este panel va a concentrar la misma identidad atlética de la PWA: composición corporal, recuperación, progreso y contexto del atleta.'}
          </Text>
        </LiquidGlassCard>

        <BodyMetricsCarousel
          weight={latestBody?.weight}
          bodyFat={latestBody?.bodyFatPercentage}
          ffmi={ffmi || undefined}
        />

        <View style={styles.statsGrid}>
          <StatBox label="Peso" value={latestBody?.weight ? `${latestBody.weight}` : '--'} unit="kg" />
          <StatBox label="% Grasa" value={latestBody?.bodyFatPercentage ? `${latestBody.bodyFatPercentage}` : '--'} unit="%" />
          <StatBox label="FFMI" value={ffmi ? ffmi.toFixed(1) : '--'} />
          <StatBox
            label="Sueño"
            value={
              wellbeingOverview?.averageSleepHoursLast7Days
                ? `${wellbeingOverview.averageSleepHoursLast7Days}`
                : '--'
            }
            unit="h"
          />
        </View>

        <LiquidGlassCard style={styles.detailCard} padding={20}>
          <View style={styles.detailHeader}>
            <InfoIcon size={16} color={colors.onSurfaceVariant} />
            <Text style={[styles.detailEyebrow, { color: colors.onSurfaceVariant }]}>Resumen rápido</Text>
          </View>
          <View style={styles.detailGrid}>
            <View style={styles.detailItem}>
              <Text style={[styles.detailValue, { color: colors.primary }]}>
                {typeof rawSettings.height === 'number' ? `${rawSettings.height} cm` : '--'}
              </Text>
              <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Altura</Text>
            </View>
            <View style={styles.detailItem}>
              <Text style={[styles.detailValue, { color: colors.secondary }]}>
                {wellbeingOverview?.waterTodayMl ? `${wellbeingOverview.waterTodayMl} ml` : '--'}
              </Text>
              <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Agua hoy</Text>
            </View>
            <View style={styles.detailItem}>
              <Text style={[styles.detailValue, { color: colors.tertiary }]}>
                {wellbeingOverview?.pendingTaskCount ?? 0}
              </Text>
              <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Tareas</Text>
            </View>
          </View>
        </LiquidGlassCard>

        <LiquidGlassCard style={styles.detailCard} padding={20}>
          <View style={styles.detailHeader}>
            <MoonIcon size={16} color={colors.onSurfaceVariant} />
            <Text style={[styles.detailEyebrow, { color: colors.onSurfaceVariant }]}>Acciones</Text>
          </View>

          <View style={styles.buttonColumn}>
            <Button onPress={() => navigation.navigate('BodyProgress')}>Abrir progreso corporal</Button>
            <Button onPress={() => navigation.navigate('ProgressOverview')} variant="secondary">
              Ver progreso integral
            </Button>
          </View>
        </LiquidGlassCard>

        <LiquidGlassCard style={styles.detailCard} padding={20}>
          <View style={styles.detailHeader}>
            <ActivityIcon size={16} color={colors.onSurfaceVariant} />
            <Text style={[styles.detailEyebrow, { color: colors.onSurfaceVariant }]}>Estado actual</Text>
          </View>
          <Text style={[styles.statusCopy, { color: colors.onSurfaceVariant }]}>
            Seguimos cerrando la paridad 1:1 del Athlete ID. Lo importante acá es que ya no es una maqueta: este panel consume stores reales de cuerpo y wellbeing.
          </Text>
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
  badgeRow: {
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
  heroCard: {
    borderRadius: 32,
  },
  heroTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  avatarCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroActions: {
    flexDirection: 'row',
    gap: 10,
  },
  heroAction: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroTier: {
    marginTop: 18,
    fontSize: 20,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  heroSummary: {
    marginTop: 10,
    fontSize: 14,
    lineHeight: 22,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  statBox: {
    width: '47%',
    borderRadius: 24,
  },
  statLabel: {
    fontSize: 9,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  statValueRow: {
    marginTop: 10,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 4,
  },
  statValue: {
    fontSize: 26,
    fontWeight: '900',
    letterSpacing: -0.8,
  },
  statUnit: {
    fontSize: 11,
    fontWeight: '700',
    marginBottom: 4,
  },
  detailCard: {
    borderRadius: 28,
  },
  detailHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  detailEyebrow: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.4,
  },
  detailGrid: {
    marginTop: 14,
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  detailItem: {
    flex: 1,
  },
  detailValue: {
    fontSize: 21,
    fontWeight: '900',
  },
  detailLabel: {
    marginTop: 4,
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  buttonColumn: {
    marginTop: 14,
    gap: 12,
  },
  statusCopy: {
    marginTop: 12,
    fontSize: 13,
    lineHeight: 20,
  },
});
