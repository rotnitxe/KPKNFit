import React from 'react';
import { View, Text, ActivityIndicator, StyleSheet } from 'react-native';
import { Button } from '../ui';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import type { AugeRuntimeSnapshot } from '../../types/augeRuntime';
import { useColors } from '../../theme';

interface AugeStatusCardProps {
  snapshot: AugeRuntimeSnapshot | null;
  isRefreshing?: boolean;
  onRefresh?: () => void;
}

export function AugeStatusCard({ snapshot, isRefreshing, onRefresh }: AugeStatusCardProps) {
  const colors = useColors();

  const statusConfig = React.useMemo(() => {
    if (!snapshot) return null;

    switch (snapshot.readinessStatus) {
      case 'green':
        return {
          label: 'Óptimo',
          backgroundColor: `${colors.batteryHigh}33`,
          textColor: colors.batteryHigh,
        };
      case 'red':
        return {
          label: 'Fatiga Crítica',
          backgroundColor: `${colors.error}33`,
          textColor: colors.error,
        };
      case 'yellow':
      default:
        return {
          label: 'Precaución',
          backgroundColor: `${colors.cyberWarning}33`,
          textColor: colors.cyberWarning,
        };
    }
  }, [snapshot, colors]);

  if (!snapshot) {
    return (
      <LiquidGlassCard style={styles.glassContainer}>
        <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>Estado AUGE</Text>
        <Text style={[styles.emptyText, { color: colors.onSurface }]}>AUGE sin datos aún.</Text>
        <View style={styles.buttonSection}>
          <Button onPress={onRefresh} disabled={isRefreshing} variant="secondary">
            {isRefreshing ? 'Calculando...' : 'Analizar Readiness'}
          </Button>
        </View>
      </LiquidGlassCard>
    );
  }

  const status = statusConfig;

  return (
    <LiquidGlassCard style={styles.glassContainer}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>Readiness</Text>
        <View style={[styles.statusBadge, { backgroundColor: status?.backgroundColor }]}>
          <Text style={[styles.statusText, { color: status?.textColor }]}>{status?.label}</Text>
        </View>
      </View>

      <Text style={[styles.recommendation, { color: colors.onSurface }]} numberOfLines={3}>
        {snapshot.recommendation}
      </Text>

      <View style={styles.statsRow}>
        <View style={[styles.statCard, { backgroundColor: 'rgba(255, 255, 255, 0.05)', borderColor: 'rgba(255, 255, 255, 0.1)' }]}>
          <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Batería CNS</Text>
          <Text style={[styles.statValue, { color: colors.primary }]}>{Math.round(snapshot.cnsBattery)}%</Text>
        </View>
        <View style={[styles.statCard, { backgroundColor: 'rgba(255, 255, 255, 0.05)', borderColor: 'rgba(255, 255, 255, 0.1)' }]}>
          <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Stress Multi</Text>
          <Text style={[styles.statValue, { color: colors.onSurface }]}>{snapshot.stressMultiplier.toFixed(2)}x</Text>
        </View>
      </View>

      <View style={styles.buttonSection}>
        <Button onPress={onRefresh} disabled={isRefreshing} variant="secondary" style={styles.refreshButton}>
          {isRefreshing ? (
            <View style={styles.loadingRow}>
              <ActivityIndicator size="small" color={colors.primary} />
              <Text style={[styles.loadingText, { color: colors.onSurface }]}>Recalculando...</Text>
            </View>
          ) : (
            'Recalcular Readiness'
          )}
        </Button>
      </View>
    </LiquidGlassCard>
  );
}

const styles = StyleSheet.create({
  glassContainer: {
    padding: 24,
  },
  title: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 99,
  },
  statusText: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  recommendation: {
    fontSize: 22,
    fontWeight: '800',
    lineHeight: 30,
    marginTop: 20,
    letterSpacing: -0.5,
  },
  emptyText: {
    fontSize: 15,
    lineHeight: 22,
    marginTop: 16,
  },
  statsRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 24,
  },
  statCard: {
    flex: 1,
    borderRadius: 20,
    borderWidth: 1,
    padding: 12,
  },
  statLabel: {
    fontSize: 9,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  statValue: {
    fontSize: 20,
    fontWeight: '900',
    marginTop: 4,
  },
  buttonSection: {
    marginTop: 24,
  },
  refreshButton: {
    borderRadius: 16,
    height: 48,
  },
  loadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  loadingText: {
    fontSize: 14,
    fontWeight: '600',
  },
});
