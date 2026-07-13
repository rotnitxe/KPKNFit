import React, { useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import type { DetailedMuscleVolumeAnalysis } from '../../types/workout';

interface VolumeBarProps {
  muscle: string;
  current: number;
  mev: number;
  mav: number;
  mrv: number;
}

function VolumeBar({ muscle, current, mev, mav, mrv }: VolumeBarProps) {
  const colors = useColors();
  const maxBar = Math.max(current, mrv * 1.2);
  const currentPct = (current / maxBar) * 100;
  const mevPct = (mev / maxBar) * 100;
  const mavPct = (mav / maxBar) * 100;
  const mrvPct = (mrv / maxBar) * 100;

  const getZoneColor = () => {
    if (current < mev) return colors.primary;
    if (current <= mav) return '#4CAF50';
    if (current <= mrv) return '#FFB300';
    return colors.error;
  };

  return (
    <View style={barStyles.container}>
      <View style={barStyles.labelRow}>
        <Text style={[barStyles.muscleName, { color: colors.onSurface }]}>{muscle}</Text>
        <Text style={[barStyles.volumeValue, { color: getZoneColor() }]}>{current.toFixed(1)}</Text>
      </View>
      <View style={[barStyles.barBg, { backgroundColor: `${colors.onSurface}15` }]}>
        <View style={[barStyles.zoneMev, { left: `${mevPct}%`, backgroundColor: `${colors.primary}40` }]} />
        <View style={[barStyles.zoneMav, { left: `${mavPct}%`, backgroundColor: '#4CAF5040' }]} />
        <View style={[barStyles.zoneMrv, { left: `${mrvPct}%`, backgroundColor: `${colors.error}40` }]} />
        <View
          style={[
            barStyles.currentMarker,
            { left: `${currentPct}%`, backgroundColor: getZoneColor() },
          ]}
        />
      </View>
      <View style={barStyles.zones}>
        <Text style={[barStyles.zoneLabel, { color: colors.onSurfaceVariant }]}>0</Text>
        <Text style={[barStyles.zoneLabel, { color: colors.primary }]}>MEV</Text>
        <Text style={[barStyles.zoneLabel, { color: '#4CAF50' }]}>MAV</Text>
        <Text style={[barStyles.zoneLabel, { color: colors.error }]}>MRV</Text>
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
  muscleName: {
    fontSize: 12,
    fontWeight: '700',
  },
  volumeValue: {
    fontSize: 14,
    fontWeight: '900',
  },
  barBg: {
    height: 12,
    borderRadius: 6,
    position: 'relative',
    overflow: 'hidden',
  },
  zoneMev: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 2,
  },
  zoneMav: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 2,
  },
  zoneMrv: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 2,
  },
  currentMarker: {
    position: 'absolute',
    top: -2,
    bottom: -2,
    width: 4,
    borderRadius: 2,
  },
  zones: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 4,
  },
  zoneLabel: {
    fontSize: 8,
    fontWeight: '700',
  },
});

interface WorkoutVolumeAnalysisWidgetProps {
  volumeData: DetailedMuscleVolumeAnalysis[];
  totalWeeklyVolume: number;
  previousWeekVolume?: number;
  showAverage?: boolean;
}

export function WorkoutVolumeAnalysisWidget({
  volumeData,
  totalWeeklyVolume,
  previousWeekVolume,
  showAverage = false,
}: WorkoutVolumeAnalysisWidgetProps) {
  const colors = useColors();

  const trend = useMemo(() => {
    if (!previousWeekVolume) return null;
    const diff = ((totalWeeklyVolume - previousWeekVolume) / previousWeekVolume) * 100;
    return {
      value: Math.abs(diff).toFixed(0),
      direction: diff > 0 ? 'up' : diff < 0 ? 'down' : 'equal',
    };
  }, [totalWeeklyVolume, previousWeekVolume]);

  const getZoneStatus = (current: number, mev: number, mav: number, mrv: number) => {
    if (current < mev) return 'Subvolumen';
    if (current <= mav) return 'Óptimo';
    if (current <= mrv) return 'Alto';
    return 'Exceso';
  };

  const defaultRecommendations: Record<string, { mev: number; mav: number; mrv: number }> = {
    Pectorales: { mev: 10, mav: 20, mrv: 25 },
    Dorsales: { mev: 10, mav: 20, mrv: 25 },
    Cuádriceps: { mev: 12, mav: 25, mrv: 30 },
    Isquiosurales: { mev: 10, mav: 20, mrv: 25 },
    Glúteos: { mev: 10, mav: 20, mrv: 25 },
    Hombros: { mev: 8, mav: 16, mrv: 20 },
    Bíceps: { mev: 6, mav: 12, mrv: 15 },
    Tríceps: { mev: 6, mav: 12, mrv: 15 },
    Trapecio: { mev: 6, mav: 12, mrv: 15 },
    Abdomen: { mev: 6, mav: 12, mrv: 15 },
    Gemelos: { mev: 6, mav: 12, mrv: 15 },
    Antebrazos: { mev: 4, mav: 8, mrv: 10 },
  };

  return (
    <LiquidGlassCard style={styles.container} padding={20}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurface }]}>Volumen Semanal</Text>
        {showAverage && (
          <TouchableOpacity style={[styles.toggle, { backgroundColor: colors.primaryContainer }]}>
            <Text style={[styles.toggleText, { color: colors.primary }]}>Promedio 4 sem</Text>
          </TouchableOpacity>
        )}
      </View>

      <View style={styles.totalRow}>
        <Text style={[styles.totalValue, { color: colors.primary }]}>{totalWeeklyVolume.toFixed(0)}</Text>
        <Text style={[styles.totalLabel, { color: colors.onSurfaceVariant }]}>series</Text>
        {trend && (
          <View style={[styles.trendBadge, { 
            backgroundColor: trend.direction === 'up' ? '#4CAF5030' : trend.direction === 'down' ? '#FF572230' : `${colors.onSurface}15` 
          }]}>
            <Text style={[styles.trendText, { 
              color: trend.direction === 'up' ? '#4CAF50' : trend.direction === 'down' ? '#FF5722' : colors.onSurfaceVariant 
            }]}>
              {trend.direction === 'up' ? '↑' : trend.direction === 'down' ? '↓' : '='} {trend.value}%
            </Text>
          </View>
        )}
      </View>

      <ScrollView style={styles.barsContainer} showsVerticalScrollIndicator={false}>
        {volumeData.map((muscle) => {
          const rec = defaultRecommendations[muscle.muscleGroup] || { mev: 10, mav: 20, mrv: 25 };
          return (
            <VolumeBar
              key={muscle.muscleGroup}
              muscle={muscle.muscleGroup}
              current={muscle.displayVolume}
              mev={rec.mev}
              mav={rec.mav}
              mrv={rec.mrv}
            />
          );
        })}
      </ScrollView>

      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendColor, { backgroundColor: colors.primary }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>Subvolumen</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendColor, { backgroundColor: '#4CAF50' }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>Óptimo</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendColor, { backgroundColor: '#FFB300' }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>Alto</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendColor, { backgroundColor: colors.error }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>Exceso</Text>
        </View>
      </View>
    </LiquidGlassCard>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: 24,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  title: {
    fontSize: 16,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  toggle: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
  },
  toggleText: {
    fontSize: 10,
    fontWeight: '800',
  },
  totalRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 8,
    marginBottom: 20,
  },
  totalValue: {
    fontSize: 48,
    fontWeight: '900',
  },
  totalLabel: {
    fontSize: 16,
    fontWeight: '700',
  },
  trendBadge: {
    marginLeft: 'auto',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  trendText: {
    fontSize: 12,
    fontWeight: '800',
  },
  barsContainer: {
    maxHeight: 300,
  },
  legend: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 16,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.1)',
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  legendColor: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  legendText: {
    fontSize: 9,
    fontWeight: '700',
  },
});
