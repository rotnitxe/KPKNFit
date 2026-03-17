import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { ActivityIcon, FlameIcon, ZapIcon } from '../icons';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { useColors } from '../../theme';
import Animated, { useAnimatedStyle, withSpring } from 'react-native-reanimated';

interface AugeTelemetryPanelProps {
  cns: number;
  muscular: number;
  spinal: number;
}

function Meter({
  label,
  value,
  color,
  Icon,
}: {
  label: string;
  value: number;
  color: string;
  Icon: React.ComponentType<{ size?: number; color?: string }>;
}) {
  const safeValue = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
  
  const animatedStyle = useAnimatedStyle(() => ({
    width: `${withSpring(safeValue)}%`,
  }));

  return (
    <View style={styles.meter}>
      <View style={styles.meterHeader}>
        <View style={styles.meterLabelRow}>
          <Icon size={12} color={color} />
          <Text style={[styles.meterLabel, { color }]}>{label}</Text>
        </View>
        <Text style={styles.meterValue}>{Math.round(safeValue)}%</Text>
      </View>
      <View style={[styles.track, { backgroundColor: `${color}15` }]}>
        <Animated.View style={[styles.fill, { backgroundColor: color }, animatedStyle]} />
      </View>
    </View>
  );
}

export const AugeTelemetryPanel: React.FC<AugeTelemetryPanelProps> = ({ cns, muscular, spinal }) => {
  const colors = useColors();
  
  return (
    <LiquidGlassCard style={styles.container}>
      <View style={styles.headlineRow}>
        <View style={styles.badge}>
            <Text style={[styles.eyebrow, { color: colors.primary }]}>AUGE REALTIME</Text>
        </View>
        <Text style={[styles.caption, { color: colors.onSurfaceVariant }]}>SENSORY DRAIN</Text>
      </View>
      <View style={styles.meterRow}>
        <Meter label="CNS" value={cns} color="#6366F1" Icon={ZapIcon} />
        <Meter label="MUSC" value={muscular} color="#F43F5E" Icon={FlameIcon} />
        <Meter label="SPIN" value={spinal} color="#10B981" Icon={ActivityIcon} />
      </View>
    </LiquidGlassCard>
  );
};

const styles = StyleSheet.create({
  container: {
    marginHorizontal: 16,
    marginTop: 8,
    marginBottom: 20,
    padding: 16,
  },
  headlineRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    backgroundColor: 'rgba(0,0,0,0.05)',
  },
  eyebrow: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1.2,
  },
  caption: {
    fontSize: 9,
    fontWeight: '800',
    opacity: 0.5,
    letterSpacing: 1,
  },
  meterRow: {
    flexDirection: 'row',
    gap: 12,
  },
  meter: {
    flex: 1,
  },
  meterHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  meterLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  meterLabel: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  meterValue: {
    fontSize: 10,
    fontWeight: '900',
    opacity: 0.8,
  },
  track: {
    height: 4,
    borderRadius: 2,
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    borderRadius: 2,
  },
});
