import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Canvas, Path, Group } from '@shopify/react-native-skia';
import { useColors } from '@/theme';

/**
 * Activity Rings Card – shows three concentric rings:
 *  - Training (red) – completed sets vs planned sets this week
 *  - Nutrition (green) – calories consumed today vs daily goal
 *  - Recovery (blue) – readiness score (0‑100)
 */
interface ActivityRingsCardProps {
  trainingPct: number; // 0‑100
  nutritionPct: number; // 0‑100
  recoveryPct: number; // 0‑100
  sourceLabel?: string;
}

const clamp = (val: number) => Math.min(Math.max(val ?? 0, 0), 100);

/** Helper to create circular arc path for Skia */
const createCircularPath = (cx: number, cy: number, radius: number, progress: number) => {
  const startAngle = -Math.PI / 2;
  const endAngle = startAngle + Math.PI * 2 * progress;
  const x = cx + radius * Math.cos(endAngle);
  const y = cy + radius * Math.sin(endAngle);
  const largeArcFlag = progress > 0.5 ? 1 : 0;
  if (progress >= 1) {
    // full circle
    return `M ${cx} ${cy - radius} A ${radius} ${radius} 0 1 1 ${cx} ${cy + radius} A ${radius} ${radius} 0 1 1 ${cx} ${cy - radius} Z`;
  }
  if (progress <= 0) return '';
  const startX = cx + radius * Math.cos(startAngle);
  const startY = cy + radius * Math.sin(startAngle);
  return `M ${startX} ${startY} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x} ${y}`;
};

export const ActivityRingsCard = React.memo(({ trainingPct, nutritionPct, recoveryPct, sourceLabel }: ActivityRingsCardProps) => {
  const colors = useColors();
  const size = 160;
  const center = size / 2;
  const strokeWidth = 12;
  const gap = 4;

  // Radii for outer / middle / inner rings
  const outerRadius = center - strokeWidth / 2 - 8; // outer
  const middleRadius = outerRadius - strokeWidth - gap;
  const innerRadius = middleRadius - strokeWidth - gap;

  const clampedTraining = clamp(trainingPct);
  const clampedNutrition = clamp(nutritionPct);
  const clampedRecovery = clamp(recoveryPct);

  // Simple status based on recovery (blue) ring
  const status = React.useMemo(() => {
    if (clampedRecovery >= 80) return { label: 'Óptimo', color: colors.batteryHigh, backgroundColor: `${colors.batteryHigh}1A` };
    if (clampedRecovery >= 50) return { label: 'Moderado', color: colors.batteryMid, backgroundColor: `${colors.batteryMid}1A` };
    return { label: 'Bajo', color: colors.batteryLow, backgroundColor: `${colors.batteryLow}1A` };
  }, [clampedRecovery, colors]);

  return (
    <View style={[styles.container, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <View style={styles.header}>
        <View style={styles.titleSection}>
          <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>Mis Rings</Text>
          {sourceLabel && (
            <Text style={[styles.sourceLabel, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
              {sourceLabel}
            </Text>
          )}
        </View>
        <View style={[styles.statusBadge, { backgroundColor: status.backgroundColor }]}>
          <Text style={[styles.statusText, { color: status.color }]} numberOfLines={1}>
            {status.label}
          </Text>
        </View>
      </View>

      <View style={styles.ringsContainer}>
        <Canvas style={{ width: size, height: size }}>
          {/* Outer – Training (red) */}
          <Group>
            <Path
              path={createCircularPath(center, center, outerRadius, 1)}
              color={`${colors.onSurface}1A`}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
            <Path
              path={createCircularPath(center, center, outerRadius, clampedTraining / 100)}
              color={colors.error}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
          </Group>

          {/* Middle – Nutrition (green) */}
          <Group>
            <Path
              path={createCircularPath(center, center, middleRadius, 1)}
              color={`${colors.onSurface}1A`}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
            <Path
              path={createCircularPath(center, center, middleRadius, clampedNutrition / 100)}
              color={colors.batteryHigh}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
          </Group>

          {/* Inner – Recovery (blue) */}
          <Group>
            <Path
              path={createCircularPath(center, center, innerRadius, 1)}
              color={`${colors.onSurface}1A`}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
            <Path
              path={createCircularPath(center, center, innerRadius, clampedRecovery / 100)}
              color={colors.ringCns}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
          </Group>
        </Canvas>
        <View style={styles.centerLabel}>
          <Text style={[styles.centerPercentage, { color: colors.onSurface }]}>{Math.round(clampedRecovery)}%</Text>
          <Text style={[styles.centerLabelText, { color: colors.onSurfaceVariant }]}>Readiness</Text>
        </View>
      </View>

      <View style={[styles.statsRow, { borderTopColor: colors.outlineVariant }]}>
        <View style={styles.statItem}>
          <Text style={[styles.statValue, { color: colors.error }]}>{Math.round(clampedTraining)}%</Text>
          <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Entren.</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={[styles.statValue, { color: colors.batteryHigh }]}>{Math.round(clampedNutrition)}%</Text>
          <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Nutri.</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={[styles.statValue, { color: colors.ringCns }]}>{Math.round(clampedRecovery)}%</Text>
          <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Recup.</Text>
        </View>
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  container: { borderRadius: 24, borderWidth: 1, padding: 16 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 },
  titleSection: { flex: 1 },
  title: { fontSize: 11, fontWeight: '700', letterSpacing: 2, textTransform: 'uppercase', marginBottom: 2 },
  sourceLabel: { fontSize: 10, fontStyle: 'italic' },
  statusBadge: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16 },
  statusText: { fontSize: 10, fontWeight: '700', letterSpacing: 0.5, textTransform: 'uppercase' },
  ringsContainer: { alignItems: 'center', justifyContent: 'center', marginBottom: 16 },
  centerLabel: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  centerPercentage: { fontSize: 24, fontWeight: '700' },
  centerLabelText: { fontSize: 10, fontWeight: '700', letterSpacing: 1, textTransform: 'uppercase', marginTop: 2 },
  statsRow: { flexDirection: 'row', justifyContent: 'space-around', borderTopWidth: 1, paddingTop: 16 },
  statItem: { alignItems: 'center' },
  statValue: { fontSize: 16, fontWeight: '700' },
  statLabel: { fontSize: 10, fontWeight: '700', letterSpacing: 1, textTransform: 'uppercase', marginTop: 4 },
});
