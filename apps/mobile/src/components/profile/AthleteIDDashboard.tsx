import React, { useEffect, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Svg, { Circle, G } from 'react-native-svg';
import { ScreenShell } from '../../components/ScreenShell';
import { LiquidGlassCard } from '../../components/ui/LiquidGlassCard';
import { useBodyStore } from '../../stores/bodyStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useColors } from '../../theme';
import { calculateFFMI, calculateIPFGLPoints } from '../../utils/calculations';
import { calculateBrzycki1RM } from '../../utils/calculations';
import type { ProfileStackParamList } from '../../navigation/types';
import { RelativeStrengthWidget } from '../analytics/RelativeStrengthWidget';
import { readStoredSettingsRaw } from '../../services/mobileDomainStateService';

type NavigationProp = NativeStackNavigationProp<ProfileStackParamList>;

interface DonutChartProps {
  percentage: number;
  color: string;
  size?: number;
  strokeWidth?: number;
  label: string;
  value: string;
}

function DonutChart({ percentage, color, size = 80, strokeWidth = 8, label, value }: DonutChartProps) {
  const radius = (size - strokeWidth) / 2;
  const circumference = radius * 2 * Math.PI;
  const strokeDashoffset = circumference - (percentage / 100) * circumference;

  return (
    <View style={chartStyles.container}>
      <Svg width={size} height={size}>
        <G rotation="-90" origin={`${size / 2}, ${size / 2}`}>
          <Circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke="#E5E5E5"
            strokeWidth={strokeWidth}
            fill="none"
          />
          <Circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke={color}
            strokeWidth={strokeWidth}
            fill="none"
            strokeDasharray={`${circumference} ${circumference}`}
            strokeDashoffset={strokeDashoffset}
            strokeLinecap="round"
          />
        </G>
      </Svg>
      <View style={chartStyles.labelContainer}>
        <Text style={chartStyles.valueText}>{value}</Text>
        <Text style={chartStyles.labelText}>{label}</Text>
      </View>
    </View>
  );
}

const chartStyles = StyleSheet.create({
  container: {
    alignItems: 'center',
  },
  labelContainer: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  valueText: {
    fontSize: 14,
    fontWeight: '800',
  },
  labelText: {
    fontSize: 8,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
});

interface StatCardProps {
  label: string;
  value: string | number;
  unit?: string;
  color?: string;
  subtitle?: string;
}

function StatCard({ label, value, unit, color = '#000', subtitle }: StatCardProps) {
  const colors = useColors();
  return (
    <LiquidGlassCard style={statCardStyles.card} padding={12}>
      <Text style={[statCardStyles.label, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <View style={statCardStyles.valueRow}>
        <Text style={[statCardStyles.value, { color: color || colors.onSurface }]}>{value}</Text>
        {unit && <Text style={[statCardStyles.unit, { color: colors.onSurfaceVariant }]}>{unit}</Text>}
      </View>
      {subtitle && <Text style={[statCardStyles.subtitle, { color: colors.onSurfaceVariant }]}>{subtitle}</Text>}
    </LiquidGlassCard>
  );
}

const statCardStyles = StyleSheet.create({
  card: {
    borderRadius: 16,
    minWidth: 80,
    flex: 1,
  },
  label: {
    fontSize: 8,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 4,
  },
  valueRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 2,
  },
  value: {
    fontSize: 22,
    fontWeight: '900',
  },
  unit: {
    fontSize: 10,
    fontWeight: '700',
  },
  subtitle: {
    fontSize: 9,
    marginTop: 2,
  },
});

interface StrengthRatioBarProps {
  label: string;
  ratio: number;
  benchmarks: { label: string; value: number }[];
}

function StrengthRatioBar({ label, ratio, benchmarks }: StrengthRatioBarProps) {
  const colors = useColors();
  const maxValue = Math.max(...benchmarks.map(b => b.value), ratio);
  const getColor = (value: number) => {
    if (value >= benchmarks[2]?.value) return colors.error;
    if (value >= benchmarks[1]?.value) return '#FFB300';
    return colors.primary;
  };

  return (
    <View style={barStyles.container}>
      <View style={barStyles.labelRow}>
        <Text style={[barStyles.label, { color: colors.onSurface }]}>{label}</Text>
        <Text style={[barStyles.ratio, { color: getColor(ratio) }]}>{ratio.toFixed(2)}x</Text>
      </View>
      <View style={[barStyles.barBg, { backgroundColor: `${colors.onSurface}15` }]}>
        <View
          style={[
            barStyles.barFill,
            { width: `${Math.min((ratio / maxValue) * 100, 100)}%`, backgroundColor: getColor(ratio) },
          ]}
        />
      </View>
      <View style={barStyles.benchmarks}>
        {benchmarks.map((b, i) => (
          <Text key={i} style={[barStyles.benchmarkLabel, { color: colors.onSurfaceVariant }]}>
            {b.label}: {b.value}
          </Text>
        ))}
      </View>
    </View>
  );
}

const barStyles = StyleSheet.create({
  container: {
    marginBottom: 16,
  },
  labelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  label: {
    fontSize: 12,
    fontWeight: '700',
  },
  ratio: {
    fontSize: 14,
    fontWeight: '900',
  },
  barBg: {
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  barFill: {
    height: '100%',
    borderRadius: 4,
  },
  benchmarks: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 4,
  },
  benchmarkLabel: {
    fontSize: 8,
    fontWeight: '600',
  },
});

export function AthleteIDDashboard() {
  const navigation = useNavigation<NavigationProp>();
  const colors = useColors();
  const settingsSummary = useSettingsStore(state => state.summary);
  const rawSettings = settingsSummary ?? (readStoredSettingsRaw() as any);
  const bodyProgress = useBodyStore(state => state.bodyProgress);
  const history = useWorkoutStore(state => state.history);
  const bodyStatus = useBodyStore(state => state.status);
  const workoutStatus = useWorkoutStore(state => state.status);
  const settingsStatus = useSettingsStore(state => state.status);
  const hydrateBody = useBodyStore(state => state.hydrateFromMigration);
  const hydrateWorkout = useWorkoutStore(state => state.hydrateFromMigration);
  const hydrateSettings = useSettingsStore(state => state.hydrateFromMigration);

  useEffect(() => {
    if (bodyStatus === 'idle') void hydrateBody();
    if (workoutStatus === 'idle') void hydrateWorkout();
    if (settingsStatus === 'idle') void hydrateSettings();
  }, [bodyStatus, hydrateBody, hydrateSettings, settingsStatus, hydrateWorkout, workoutStatus]);

  const latestBody = bodyProgress[0];
  const weight = latestBody?.weight || rawSettings.userVitals?.weight;
  const height = rawSettings.userVitals?.height;
  const bodyFat = latestBody?.bodyFatPercentage || rawSettings.userVitals?.bodyFatPercentage;
  const gender = rawSettings.userVitals?.gender || 'male';

  const ffmiData = useMemo(() => {
    if (!height || !weight || bodyFat === undefined) return null;
    return calculateFFMI(height, weight, bodyFat);
  }, [height, weight, bodyFat]);

  const ipfData = useMemo(() => {
    if (!weight || !history?.length) return null;
    
    const liftKeywords: Record<string, string[]> = {
      squat: ['sentadilla', 'squat'],
      bench: ['press de banca', 'bench press', 'banca'],
      deadlift: ['peso muerto', 'deadlift'],
    };
    const maxLifts: Record<string, number> = { squat: 0, bench: 0, deadlift: 0 };

    history.forEach(log => {
      log.completedExercises.forEach(ex => {
        const name = ex.exerciseName.toLowerCase();
        const max1RM = Math.max(
          ...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0))
        );
        if (liftKeywords.squat.some(k => name.includes(k))) {
          maxLifts.squat = Math.max(maxLifts.squat, max1RM);
        }
        if (liftKeywords.bench.some(k => name.includes(k))) {
          maxLifts.bench = Math.max(maxLifts.bench, max1RM);
        }
        if (liftKeywords.deadlift.some(k => name.includes(k))) {
          maxLifts.deadlift = Math.max(maxLifts.deadlift, max1RM);
        }
      });
    });

    const total = maxLifts.squat + maxLifts.bench + maxLifts.deadlift;
    if (total === 0) return null;

    const points = calculateIPFGLPoints(total, weight, {
      gender: gender as 'male' | 'female',
      equipment: 'classic',
      lift: 'total',
      weightUnit: (rawSettings.weightUnit as 'kg' | 'lbs') || 'kg',
    });

    return { total, points, lifts: maxLifts };
  }, [weight, history, gender, rawSettings.weightUnit]);

  const strengthRatios = useMemo(() => {
    if (!weight || !ipfData) return null;
    return {
      bench: ipfData.lifts.bench / weight,
      squat: ipfData.lifts.squat / weight,
      deadlift: ipfData.lifts.deadlift / weight,
    };
  }, [weight, ipfData]);

  const relativeStrengthLifts = useMemo(() => {
    const liftKeywords: Record<string, string[]> = {
      squat: ['sentadilla', 'squat'],
      bench: ['press de banca', 'bench press', 'banca'],
      deadlift: ['peso muerto', 'deadlift'],
      overhead: ['press militar', 'overhead press', 'press'],
    };

    const bestLifts = Object.entries(liftKeywords).map(([liftName, keywords]) => {
      let best = 0;

      history.forEach(log => {
        log.completedExercises.forEach(ex => {
          const name = ex.exerciseName.toLowerCase();
          if (!keywords.some(keyword => name.includes(keyword))) return;
          ex.sets.forEach(set => {
            const e1rm = calculateBrzycki1RM(set.weight || 0, set.completedReps || 0);
            if (e1rm > best) best = e1rm;
          });
        });
      });

      return {
        name: liftName === 'bench'
          ? 'Press Banca'
          : liftName === 'squat'
            ? 'Sentadilla'
            : liftName === 'deadlift'
              ? 'Peso Muerto'
              : 'Press Militar',
        e1rm: best,
      };
    });

    return bestLifts.filter(lift => lift.e1rm > 0);
  }, [history]);

  const getFFMIColor = (ffmi: number | null) => {
    if (!ffmi) return colors.onSurfaceVariant;
    if (ffmi >= 24) return colors.error;
    if (ffmi >= 22) return '#FFB300';
    return colors.primary;
  };

  return (
    <ScreenShell
      title="Athlete ID"
      subtitle="Tu perfil atlético completo"
    >
      <ScrollView showsVerticalScrollIndicator={false} style={styles.container}>
        <LiquidGlassCard style={styles.heroCard} padding={20}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Composición Corporal</Text>
          <View style={styles.compositionRow}>
            <DonutChart
              percentage={bodyFat || 0}
              color={colors.primary}
              label="Grasa"
              value={`${bodyFat || 0}%`}
            />
            <DonutChart
              percentage={ffmiData ? (parseFloat(ffmiData.leanBodyMass) / weight) * 100 : 0}
              color={colors.tertiary}
              label="Músculo"
              value={`${ffmiData?.leanBodyMass || 0}kg`}
            />
          </View>
          {ffmiData && (
            <View style={styles.ffmiRow}>
              <View style={styles.ffmiItem}>
                <Text style={[styles.ffmiValue, { color: getFFMIColor(parseFloat(ffmiData.normalizedFfmi)) }]}>
                  {ffmiData.normalizedFfmi}
                </Text>
                <Text style={[styles.ffmiLabel, { color: colors.onSurfaceVariant }]}>FFMI Normalizado</Text>
              </View>
              <View style={styles.ffmiItem}>
                <Text style={[styles.ffmiValue, { color: colors.onSurface }]}>{ffmiData.interpretation}</Text>
                <Text style={[styles.ffmiLabel, { color: colors.onSurfaceVariant }]}>Nivel</Text>
              </View>
            </View>
          )}
        </LiquidGlassCard>

        {ipfData && (
          <LiquidGlassCard style={styles.heroCard} padding={20}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Powerlifting</Text>
            <View style={styles.statsRow}>
              <StatCard
                label="IPF DOTS"
                value={ipfData.points}
                color={colors.primary}
                subtitle="Puntos"
              />
              <StatCard
                label="Total"
                value={ipfData.total}
                unit="kg"
                color={colors.secondary}
              />
            </View>
            <View style={styles.liftsRow}>
              <View style={styles.liftItem}>
                <Text style={[styles.liftLabel, { color: colors.onSurfaceVariant }]}>Sentadilla</Text>
                <Text style={[styles.liftValue, { color: colors.onSurface }]}>{ipfData.lifts.squat} kg</Text>
              </View>
              <View style={styles.liftItem}>
                <Text style={[styles.liftLabel, { color: colors.onSurfaceVariant }]}>Banca</Text>
                <Text style={[styles.liftValue, { color: colors.onSurface }]}>{ipfData.lifts.bench} kg</Text>
              </View>
              <View style={styles.liftItem}>
                <Text style={[styles.liftLabel, { color: colors.onSurfaceVariant }]}>Peso Muerto</Text>
                <Text style={[styles.liftValue, { color: colors.onSurface }]}>{ipfData.lifts.deadlift} kg</Text>
              </View>
            </View>
          </LiquidGlassCard>
        )}

        {strengthRatios && (
          <LiquidGlassCard style={styles.heroCard} padding={20}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Fuerza Relativa</Text>
            <StrengthRatioBar
              label="Press Banca"
              ratio={strengthRatios.bench}
              benchmarks={[
                { label: 'Intermedio', value: 1.0 },
                { label: 'Avanzado', value: 1.5 },
                { label: 'Elite', value: 2.0 },
              ]}
            />
            <StrengthRatioBar
              label="Sentadilla"
              ratio={strengthRatios.squat}
              benchmarks={[
                { label: 'Intermedio', value: 1.5 },
                { label: 'Avanzado', value: 2.0 },
                { label: 'Elite', value: 2.5 },
              ]}
            />
            <StrengthRatioBar
              label="Peso Muerto"
              ratio={strengthRatios.deadlift}
              benchmarks={[
                { label: 'Intermedio', value: 1.75 },
                { label: 'Avanzado', value: 2.5 },
                { label: 'Elite', value: 3.0 },
              ]}
            />
          </LiquidGlassCard>
        )}

        <RelativeStrengthWidget lifts={relativeStrengthLifts} bodyweight={weight || 0} />

        <LiquidGlassCard style={styles.heroCard} padding={20}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Datos Básicos</Text>
          <View style={styles.vitalsGrid}>
            <View style={styles.vitalItem}>
              <Text style={[styles.vitalLabel, { color: colors.onSurfaceVariant }]}>Peso</Text>
              <Text style={[styles.vitalValue, { color: colors.onSurface }]}>{weight || '--'} kg</Text>
            </View>
            <View style={styles.vitalItem}>
              <Text style={[styles.vitalLabel, { color: colors.onSurfaceVariant }]}>Altura</Text>
              <Text style={[styles.vitalValue, { color: colors.onSurface }]}>{height ? `${height} cm` : '--'}</Text>
            </View>
            <View style={styles.vitalItem}>
              <Text style={[styles.vitalLabel, { color: colors.onSurfaceVariant }]}>Sexo</Text>
              <Text style={[styles.vitalValue, { color: colors.onSurface }]}>
                {gender === 'male' ? 'Masculino' : gender === 'female' ? 'Femenino' : '--'}
              </Text>
            </View>
          </View>
        </LiquidGlassCard>

        <View style={{ height: 40 }} />
      </ScrollView>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  heroCard: {
    borderRadius: 24,
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
    marginBottom: 16,
  },
  compositionRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 16,
  },
  ffmiRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.1)',
  },
  ffmiItem: {
    alignItems: 'center',
  },
  ffmiValue: {
    fontSize: 28,
    fontWeight: '900',
  },
  ffmiLabel: {
    fontSize: 9,
    fontWeight: '700',
    textTransform: 'uppercase',
    marginTop: 4,
  },
  statsRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 16,
  },
  liftsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  liftItem: {
    alignItems: 'center',
  },
  liftLabel: {
    fontSize: 9,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  liftValue: {
    fontSize: 16,
    fontWeight: '900',
    marginTop: 4,
  },
  vitalsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 16,
  },
  vitalItem: {
    minWidth: '40%',
  },
  vitalLabel: {
    fontSize: 9,
    fontWeight: '700',
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  vitalValue: {
    fontSize: 18,
    fontWeight: '800',
  },
});
