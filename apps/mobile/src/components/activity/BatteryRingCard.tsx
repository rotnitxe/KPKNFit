import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Canvas, Path, Group } from '@shopify/react-native-skia';
import { useColors } from '@/theme';

interface BatteryRingCardProps {
  overallPct: number;
  cnsPct: number;
  muscularPct: number;
  sourceLabel?: string;
}

const clamp = (val: number) => Math.min(Math.max(val || 0, 0), 100);

/**
 * Creates a circular path for Skia canvas.
 * @param cx Center X
 * @param cy Center Y
 * @param radius Radius of the circle
 * @param progress Progress from 0 to 1 (for partial circle)
 * @returns Path string for Skia
 */
const createCircularPath = (cx: number, cy: number, radius: number, progress: number) => {
  const startAngle = -Math.PI / 2; // Start at top (12 o'clock)
  const endAngle = startAngle + (Math.PI * 2 * progress);

  const x = cx + radius * Math.cos(endAngle);
  const y = cy + radius * Math.sin(endAngle);

  const largeArcFlag = progress > 0.5 ? 1 : 0;

  if (progress >= 1) {
    // Full circle
    return `M ${cx} ${cy - radius} A ${radius} ${radius} 0 1 1 ${cx} ${cy + radius} A ${radius} ${radius} 0 1 1 ${cx} ${cy - radius} Z`;
  }

  if (progress <= 0) {
    return '';
  }

  // Arc from top to progress point
  const startX = cx + radius * Math.cos(startAngle);
  const startY = cy + radius * Math.sin(startAngle);

  return `M ${startX} ${startY} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x} ${y}`;
};

export function BatteryRingCard({
  overallPct,
  cnsPct,
  muscularPct,
  sourceLabel,
}: BatteryRingCardProps) {
  const colors = useColors();

  const size = 160;
  const center = size / 2;
  const strokeWidth = 12;
  const gap = 4;

  // Calculate radii for each ring (outer to inner)
  const ring1Radius = center - strokeWidth / 2 - 8; // Overall (outer)
  const ring2Radius = ring1Radius - strokeWidth - gap; // CNS (middle)
  const ring3Radius = ring2Radius - strokeWidth - gap; // Muscular (inner)

  const clampedOverall = clamp(overallPct);
  const clampedCns = clamp(cnsPct);
  const clampedMuscular = clamp(muscularPct);

  const getStatus = React.useMemo(() => {
    if (clampedOverall >= 80) {
      return { label: 'Óptimo', color: colors.batteryHigh, backgroundColor: `${colors.batteryHigh}1A` };
    }
    if (clampedOverall >= 50) {
      return { label: 'Moderado', color: colors.batteryMid, backgroundColor: `${colors.batteryMid}1A` };
    }
    return { label: 'Bajo', color: colors.batteryLow, backgroundColor: `${colors.batteryLow}1A` };
  }, [clampedOverall, colors]);

  return (
    <View style={[styles.container, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <View style={styles.header}>
        <View style={styles.titleSection}>
          <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>Tus Rings</Text>
          {sourceLabel && (
            <Text style={[styles.sourceLabel, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
              {sourceLabel}
            </Text>
          )}
        </View>
        <View style={[styles.statusBadge, { backgroundColor: getStatus.backgroundColor }]}>
          <Text style={[styles.statusText, { color: getStatus.color }]} numberOfLines={1}>
            {getStatus.label}
          </Text>
        </View>
      </View>

      <View style={styles.ringsContainer}>
        <Canvas style={{ width: size, height: size }}>
          {/* Outer Ring - Overall */}
          <Group>
            {/* Track */}
            <Path
              path={createCircularPath(center, center, ring1Radius, 1)}
              color={`${colors.onSurface}1A`}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
            {/* Progress */}
            <Path
              path={createCircularPath(center, center, ring1Radius, clampedOverall / 100)}
              color={colors.primary}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
          </Group>

          {/* Middle Ring - CNS */}
          <Group>
            {/* Track */}
            <Path
              path={createCircularPath(center, center, ring2Radius, 1)}
              color={`${colors.onSurface}1A`}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
            {/* Progress */}
            <Path
              path={createCircularPath(center, center, ring2Radius, clampedCns / 100)}
              color={colors.secondary}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
          </Group>

          {/* Inner Ring - Muscular */}
          <Group>
            {/* Track */}
            <Path
              path={createCircularPath(center, center, ring3Radius, 1)}
              color={`${colors.onSurface}1A`}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
            {/* Progress */}
            <Path
              path={createCircularPath(center, center, ring3Radius, clampedMuscular / 100)}
              color={colors.tertiary}
              style="stroke"
              strokeCap="round"
              strokeJoin="round"
              strokeWidth={strokeWidth}
            />
          </Group>
        </Canvas>

        {/* Center Label */}
        <View style={styles.centerLabel}>
          <Text style={[styles.centerPercentage, { color: colors.onSurface }]}>
            {Math.round(clampedOverall)}%
          </Text>
          <Text style={[styles.centerLabelText, { color: colors.onSurfaceVariant }]}>Batería</Text>
        </View>
      </View>

      {/* Bottom Stats */}
      <View style={[styles.statsRow, { borderTopColor: colors.outlineVariant }]}>
        <View style={styles.statItem}>
          <Text style={[styles.statValue, { color: colors.primary }]}>{Math.round(clampedOverall)}%</Text>
          <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Gral</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={[styles.statValue, { color: colors.secondary }]}>{Math.round(clampedCns)}%</Text>
          <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>SNC</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={[styles.statValue, { color: colors.tertiary }]}>{Math.round(clampedMuscular)}%</Text>
          <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Muscular</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  titleSection: {
    flex: 1,
  },
  title: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2,
    textTransform: 'uppercase',
    marginBottom: 2,
  },
  sourceLabel: {
    fontSize: 10,
    fontStyle: 'italic',
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  statusText: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  ringsContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  centerLabel: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  centerPercentage: {
    fontSize: 24,
    fontWeight: '700',
  },
  centerLabelText: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginTop: 2,
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    borderTopWidth: 1,
    paddingTop: 16,
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 16,
    fontWeight: '700',
  },
  statLabel: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginTop: 4,
  },
});
