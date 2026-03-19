import React, { useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { Canvas, Line, vec, Circle, Path, Skia, Group } from '@shopify/react-native-skia';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { calculateBrzycki1RM } from '../../utils/calculations';

interface LiftData {
  name: string;
  e1rm: number;
}

interface RelativeStrengthWidgetProps {
  lifts: LiftData[];
  bodyweight: number;
}

const STRENGTH_STANDARDS = {
  bench: { novice: 0.5, intermediate: 1.0, advanced: 1.5, elite: 2.0 },
  squat: { novice: 0.75, intermediate: 1.5, advanced: 2.0, elite: 2.5 },
  deadlift: { novice: 1.0, intermediate: 1.75, advanced: 2.5, elite: 3.0 },
  overhead: { novice: 0.35, intermediate: 0.7, advanced: 1.0, elite: 1.25 },
};

function getStrengthLevel(ratio: number, liftType: string): string {
  const standards = STRENGTH_STANDARDS[liftType as keyof typeof STRENGTH_STANDARDS] || STRENGTH_STANDARDS.bench;
  if (ratio >= standards.elite) return 'Élite';
  if (ratio >= standards.advanced) return 'Avanzado';
  if (ratio >= standards.intermediate) return 'Intermedio';
  if (ratio >= standards.novice) return 'Novato';
  return 'Principiante';
}

function getStrengthColor(level: string): string {
  switch (level) {
    case 'Élite': return '#FF5722';
    case 'Avanzado': return '#FFB300';
    case 'Intermedio': return '#4CAF50';
    case 'Novato': return '#2196F3';
    default: return '#9E9E9E';
  }
}

function StrengthBar({ label, ratio, benchmarks }: { label: string; ratio: number; benchmarks: typeof STRENGTH_STANDARDS.bench }) {
  const colors = useColors();
  const maxValue = Math.max(ratio, benchmarks.elite * 1.1);
  const fillPct = (ratio / maxValue) * 100;
  
  const level = getStrengthLevel(ratio, label.toLowerCase().includes('banca') ? 'bench' : label.toLowerCase().includes('sentadilla') ? 'squat' : label.toLowerCase().includes('muerto') ? 'deadlift' : 'overhead');
  const barColor = getStrengthColor(level);

  return (
    <View style={barStyles.container}>
      <View style={barStyles.labelRow}>
        <Text style={[barStyles.label, { color: colors.onSurface }]}>{label}</Text>
        <Text style={[barStyles.ratio, { color: barColor }]}>{ratio.toFixed(2)} × BW</Text>
      </View>
      <View style={[barStyles.barBg, { backgroundColor: `${colors.onSurface}15` }]}>
        <View style={[barStyles.benchmarks, { borderLeftColor: colors.outlineVariant }]} />
        <View style={[barStyles.fill, { width: `${fillPct}%`, backgroundColor: barColor }]} />
      </View>
      <View style={barStyles.levels}>
        <Text style={[barStyles.levelText, { color: colors.onSurfaceVariant }]}>N</Text>
        <Text style={[barStyles.levelText, { color: colors.onSurfaceVariant }]}>I</Text>
        <Text style={[barStyles.levelText, { color: colors.onSurfaceVariant }]}>A</Text>
        <Text style={[barStyles.levelText, { color: colors.onSurfaceVariant }]}>E</Text>
      </View>
    </View>
  );
}

const barStyles = StyleSheet.create({
  container: {
    marginBottom: 20,
  },
  labelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  label: {
    fontSize: 14,
    fontWeight: '700',
  },
  ratio: {
    fontSize: 16,
    fontWeight: '900',
  },
  barBg: {
    height: 16,
    borderRadius: 8,
    overflow: 'hidden',
    position: 'relative',
  },
  benchmarks: {
    position: 'absolute',
    left: '20%',
    top: 0,
    bottom: 0,
    width: 1,
    borderLeftWidth: 1,
    borderStyle: 'dashed',
  },
  fill: {
    height: '100%',
    borderRadius: 8,
  },
  levels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 4,
    paddingHorizontal: '2%',
  },
  levelText: {
    fontSize: 8,
    fontWeight: '800',
  },
});

function RadarChart({ data, size = 200 }: { data: number[]; size?: number }) {
  const colors = useColors();
  const center = size / 2;
  const maxRadius = size / 2 - 30;
  const axes = 5;
  const angleStep = (2 * Math.PI) / axes;

  const points = data.map((value, i) => {
    const angle = i * angleStep - Math.PI / 2;
    const radius = (value / 10) * maxRadius;
    return vec(center + radius * Math.cos(angle), center + radius * Math.sin(angle));
  });

  const path = Skia.Path.Make();
  points.forEach((point, i) => {
    if (i === 0) path.moveTo(point.x, point.y);
    else path.lineTo(point.x, point.y);
  });
  path.close();

  return (
    <Canvas style={{ width: size, height: size }}>
      {[0.2, 0.4, 0.6, 0.8, 1].map((level, i) => (
        <Circle
          key={i}
          cx={center}
          cy={center}
          r={maxRadius * level}
          color={`${colors.primary}20`}
        />
      ))}
      {Array.from({ length: axes }).map((_, i) => {
        const angle = i * angleStep - Math.PI / 2;
        return (
          <Line
            key={i}
            p1={vec(center, center)}
            p2={vec(center + maxRadius * Math.cos(angle), center + maxRadius * Math.sin(angle))}
            color={`${colors.onSurfaceVariant}30`}
            strokeWidth={1}
          />
        );
      })}
      <Group color={colors.primary}>
        <Path
          path={path}
          style="fill"
          color={colors.primary}
          opacity={0.5}
        />
      </Group>
    </Canvas>
  );
}



export function RelativeStrengthWidget({ lifts, bodyweight }: RelativeStrengthWidgetProps) {
  const colors = useColors();

  const ratios = useMemo(() => {
    if (!bodyweight) return { bench: 0, squat: 0, deadlift: 0, overhead: 0 };
    
    const getRatio = (name: string) => {
      const lift = lifts.find(l => 
        l.name.toLowerCase().includes(name.toLowerCase())
      );
      return lift ? lift.e1rm / bodyweight : 0;
    };

    return {
      bench: getRatio('banca') || getRatio('bench'),
      squat: getRatio('sentadilla') || getRatio('squat'),
      deadlift: getRatio('muerto') || getRatio('deadlift'),
      overhead: getRatio('militar') || getRatio('press'),
    };
  }, [lifts, bodyweight]);

  const totalSBD = ratios.bench + ratios.squat + ratios.deadlift;
  const sbdRatio = bodyweight ? totalSBD : 0;

  const radarData = useMemo(() => {
    return [
      Math.min(ratios.bench * 5, 10),
      Math.min(ratios.deadlift * 4, 10),
      Math.min(ratios.squat * 4, 10),
      Math.min(ratios.overhead * 8, 10),
      Math.min(ratios.bench * 4, 10), // arms approximated with bench ratio
    ];
  }, [ratios]);

  return (
    <LiquidGlassCard style={styles.container} padding={20}>
      <Text style={[styles.title, { color: colors.onSurface }]}>FUERZA RELATIVA</Text>

      <View style={styles.bars}>
        <StrengthBar label="Press Banca" ratio={ratios.bench} benchmarks={STRENGTH_STANDARDS.bench} />
        <StrengthBar label="Sentadilla" ratio={ratios.squat} benchmarks={STRENGTH_STANDARDS.squat} />
        <StrengthBar label="Peso Muerto" ratio={ratios.deadlift} benchmarks={STRENGTH_STANDARDS.deadlift} />
        <StrengthBar label="Press Militar" ratio={ratios.overhead} benchmarks={STRENGTH_STANDARDS.overhead} />
      </View>

      <View style={styles.totals}>
        <View style={styles.totalItem}>
          <Text style={[styles.totalLabel, { color: colors.onSurfaceVariant }]}>SBD Total</Text>
          <Text style={[styles.totalValue, { color: colors.primary }]}>{totalSBD.toFixed(2)} × BW</Text>
        </View>
        <View style={styles.totalItem}>
          <Text style={[styles.totalLabel, { color: colors.onSurfaceVariant }]}>Ratio</Text>
          <Text style={[styles.totalValue, { color: colors.secondary }]}>{sbdRatio.toFixed(2)}</Text>
        </View>
      </View>

      <View style={styles.radarContainer}>
        <Text style={[styles.radarTitle, { color: colors.onSurfaceVariant }]}>Perfil de Fuerza</Text>
        <RadarChart data={radarData} size={180} />
      </View>
    </LiquidGlassCard>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: 24,
  },
  title: {
    fontSize: 14,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
    marginBottom: 20,
  },
  bars: {
    marginBottom: 24,
  },
  totals: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 16,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
    marginBottom: 20,
  },
  totalItem: {
    alignItems: 'center',
  },
  totalLabel: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  totalValue: {
    fontSize: 24,
    fontWeight: '900',
  },
  radarContainer: {
    alignItems: 'center',
  },
  radarTitle: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    marginBottom: 12,
  },
});
