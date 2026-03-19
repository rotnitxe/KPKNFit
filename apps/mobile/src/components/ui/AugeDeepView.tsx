import React, { useState } from 'react';
import { View, Text, Pressable, StyleSheet, ScrollView } from 'react-native';
import Svg, { Path, Line, Rect } from 'react-native-svg';
import Animated, { FadeIn } from 'react-native-reanimated';
import { useColors } from '../../theme';
import { BrainIcon, ChevronDownIcon, ActivityIcon, TargetIcon, TrendingUpIcon, ZapIcon } from '../icons';
import {
  AugeAdaptiveCache,
  GPFatiguePrediction,
  BanisterSystemResult,
  getConfidenceLabel,
  getConfidenceColor,
} from '../../services/augeRuntimeService';

// --- SVG Chart Helpers ---

function polylinePath(xs: number[], ys: number[], width: number, height: number, padding = 8): string {
  if (xs.length === 0) return '';
  const xMin = Math.min(...xs), xMax = Math.max(...xs);
  const yMin = Math.min(...ys), yMax = Math.max(...ys);
  const xRange = xMax - xMin || 1;
  const yRange = yMax - yMin || 1;
  return xs.map((x, i) => {
    const px = padding + ((x - xMin) / xRange) * (width - 2 * padding);
    const py = padding + (1 - (ys[i] - yMin) / yRange) * (height - 2 * padding);
    return `${i === 0 ? 'M' : 'L'}${px.toFixed(1)},${py.toFixed(1)}`;
  }).join(' ');
}

function areaPath(xs: number[], upper: number[], lower: number[], width: number, height: number, padding = 8): string {
  if (xs.length === 0) return '';
  const xMin = Math.min(...xs), xMax = Math.max(...xs);
  const allY = [...upper, ...lower];
  const yMin = Math.min(...allY), yMax = Math.max(...allY);
  const xRange = xMax - xMin || 1;
  const yRange = yMax - yMin || 1;
  const scale = (x: number, y: number) => ({
    px: padding + ((x - xMin) / xRange) * (width - 2 * padding),
    py: padding + (1 - (y - yMin) / yRange) * (height - 2 * padding),
  });
  const fwd = xs.map((x, i) => { const { px, py } = scale(x, upper[i]); return `${i === 0 ? 'M' : 'L'}${px.toFixed(1)},${py.toFixed(1)}`; }).join(' ');
  const rev = [...xs].reverse().map((x, i) => { const { px, py } = scale(x, lower[xs.length - 1 - i]); return `L${px.toFixed(1)},${py.toFixed(1)}`; }).join(' ');
  return `${fwd} ${rev} Z`;
}

function sparklinePath(ys: number[], width: number, height: number, padding = 4): string {
  if (ys.length < 2) return '';
  const yMin = Math.min(...ys), yMax = Math.max(...ys);
  const yRange = yMax - yMin || 1;
  const step = (width - 2 * padding) / (ys.length - 1);
  return ys.map((y, i) => {
    const px = padding + i * step;
    const py = padding + (1 - (y - yMin) / yRange) * (height - 2 * padding);
    return `${i === 0 ? 'M' : 'L'}${px.toFixed(1)},${py.toFixed(1)}`;
  }).join(' ');
}

// --- Sub-components ---

const SectionToggle: React.FC<{
  title: string;
  icon: React.ReactNode;
  color: string;
  isOpen: boolean;
  onToggle: () => void;
  children: React.ReactNode;
}> = ({ title, icon, color, isOpen, onToggle, children }) => {
  const colors = useColors();
  return (
    <View style={[styles.sectionContainer, { borderColor: colors.outlineVariant, backgroundColor: colors.surfaceContainer }]}>
      <Pressable style={styles.sectionHeader} onPress={onToggle}>
        <View style={styles.titleRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            {icon}
            <Text style={[styles.sectionTitle, { color }]}>{title}</Text>
          </View>
          <ChevronDownIcon size={14} color={colors.onSurfaceVariant} style={{ transform: [{ rotate: isOpen ? '180deg' : '0deg' }] }} />
        </View>
      </Pressable>
      {isOpen && (
        <Animated.View entering={FadeIn} style={styles.sectionContent}>
          {children}
        </Animated.View>
      )}
    </View>
  );
};

// --- GP Fatigue Curve ---

export const GPFatigueCurve: React.FC<{
  data: GPFatiguePrediction | null;
  currentHour?: number;
  predictedLine?: { hours: number[]; values: number[] } | null;
  actualLine?: { hours: number[]; values: number[] } | null;
  compact?: boolean;
}> = ({ data, currentHour, predictedLine, actualLine, compact }) => {
  const colors = useColors();
  const w = compact ? 200 : 320;
  const h = compact ? 80 : 120;

  if (!data) return (
    <View style={styles.placeholder}>
      <Text style={[styles.placeholderText, { color: colors.onSurfaceVariant }]}>Sin datos GP — Completa más sesiones</Text>
    </View>
  );

  const { hours, mean_fatigue, upper_bound, lower_bound, peak_fatigue_hour, supercompensation_hour, full_recovery_hour } = data;

  return (
    <View>
      <Svg width="100%" height={h} viewBox={`0 0 ${w} ${h}`}>
        <Path d={areaPath(hours, upper_bound, lower_bound, w, h)} fill={`${colors.secondary}1A`} />
        <Path d={polylinePath(hours, mean_fatigue, w, h)} fill="none" stroke={colors.secondary} strokeWidth="2" strokeLinecap="round" />
        {predictedLine && (
          <Path d={polylinePath(predictedLine.hours, predictedLine.values, w, h)} fill="none" stroke={colors.onSurfaceVariant} strokeWidth="1" strokeDasharray="3,3" />
        )}
        {actualLine && (
          <Path d={polylinePath(actualLine.hours, actualLine.values, w, h)} fill="none" stroke={colors.error} strokeWidth="2" />
        )}
        {currentHour !== undefined && (() => {
          const xMin = Math.min(...hours), xMax = Math.max(...hours);
          const xRange = xMax - xMin || 1;
          const px = 8 + ((currentHour - xMin) / xRange) * (w - 16);
          return <Line x1={px} y1="4" x2={px} y2={h - 4} stroke={colors.cyberWarning} strokeWidth="1" strokeDasharray="2,2" />;
        })()}
      </Svg>
      {!compact && (
        <View style={styles.footer}>
          <Text style={[styles.footerText, { color: colors.onSurfaceVariant }]}>Pico: {peak_fatigue_hour}h</Text>
          {supercompensation_hour && <Text style={[styles.footerText, { color: colors.primary }]}>Super: {supercompensation_hour}h</Text>}
          <Text style={[styles.footerText, { color: colors.secondary }]}>Recup: {full_recovery_hour}h</Text>
        </View>
      )}
    </View>
  );
};

// --- Bayesian Confidence Indicator ---

export const BayesianConfidence: React.FC<{
  totalObservations: number;
  personalizedRecoveryHours?: Record<string, number>;
  compact?: boolean;
}> = ({ totalObservations, personalizedRecoveryHours, compact }) => {
  const colors = useColors();
  const label = getConfidenceLabel(totalObservations);
  const color = getConfidenceColor(totalObservations);
  const pct = Math.min(100, (totalObservations / 25) * 100);
  const labelMap: Record<string, string> = { poblacional: 'Poblacional', baja: 'Aprendiendo', media: 'Personalizado', alta: 'Alta Precisión' };

  let barColor = colors.outline;
  if (label === 'alta') barColor = colors.cyberWarning;
  else if (label === 'media') barColor = colors.primary;
  else if (label === 'baja') barColor = colors.secondary;

  return (
    <View>
      <View style={styles.confidenceHeader}>
        <Text style={[styles.confidenceLabel, { color }]}>{labelMap[label]}</Text>
        <Text style={[styles.observationCount, { color: colors.onSurfaceVariant }]}>{totalObservations} obs</Text>
      </View>
      <View style={[styles.progressBar, { backgroundColor: colors.surfaceContainerHigh }]}>
        <View style={[styles.progressFill, { width: `${pct}%`, backgroundColor: barColor }]} />
      </View>
      {!compact && personalizedRecoveryHours && Object.keys(personalizedRecoveryHours).length > 0 && (
        <View style={styles.personalizedList}>
          {Object.entries(personalizedRecoveryHours).slice(0, 5).map(([muscle, hrs]) => (
            <View key={muscle} style={styles.personalizedItem}>
              <Text style={[styles.muscleName, { color: colors.onSurfaceVariant }]}>{muscle}</Text>
              <Text style={[styles.hoursText, { color }]}>{hrs.toFixed(0)}h</Text>
            </View>
          ))}
        </View>
      )}
    </View>
  );
};

// --- Banister Trend Chart ---

export const BanisterTrend: React.FC<{
  systemData: BanisterSystemResult | null;
  compact?: boolean;
}> = ({ systemData, compact }) => {
  const colors = useColors();
  const w = compact ? 200 : 320;
  const h = compact ? 80 : 120;

  if (!systemData) return (
    <View style={styles.placeholder}>
      <Text style={[styles.placeholderText, { color: colors.onSurfaceVariant }]}>Sin datos Banister — Entrena más</Text>
    </View>
  );

  const { timeline_hours, fitness, fatigue, performance, next_optimal_session_hour } = systemData;

  return (
    <View>
      <Svg width="100%" height={h} viewBox={`0 0 ${w} ${h}`}>
        <Path d={polylinePath(timeline_hours, fitness, w, h)} fill="none" stroke={colors.primary} strokeWidth="2" opacity="0.6" />
        <Path d={polylinePath(timeline_hours, fatigue, w, h)} fill="none" stroke={colors.error} strokeWidth="2" opacity="0.6" />
        <Path d={polylinePath(timeline_hours, performance, w, h)} fill="none" stroke={colors.cyberWarning} strokeWidth="2.5" />
      </Svg>
      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.primary }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>Fitness</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.error }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>Fatiga</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.cyberWarning }]} />
          <Text style={[styles.legendText, { color: colors.onSurfaceVariant }]}>Performance</Text>
        </View>
      </View>
      {!compact && next_optimal_session_hour !== null && (
        <Text style={[styles.nextSession, { color: colors.primary }]}>Próxima sesión: {next_optimal_session_hour}h</Text>
      )}
    </View>
  );
};

// --- Self-Improvement Score ---

export const SelfImprovementScore: React.FC<{
  score: number;
  trend: number[];
  recommendations?: string[];
  compact?: boolean;
}> = ({ score, trend, recommendations, compact }) => {
  const colors = useColors();
  const trendColor = trend.length >= 2 && trend[trend.length - 1] >= trend[trend.length - 2] ? colors.primary : colors.error;

  return (
    <View>
      <View style={styles.scoreRow}>
        <Text style={[styles.score, { color: colors.onSurface }]}>{score}</Text>
        <Text style={[styles.scoreMax, { color: colors.onSurfaceVariant }]}>/100</Text>
        {trend.length >= 2 && (
          <Svg style={styles.sparkline} viewBox="0 0 60 24">
            <Path d={sparklinePath(trend, 60, 24)} fill="none" stroke={trendColor} strokeWidth="2" />
          </Svg>
        )}
      </View>
      {!compact && recommendations && recommendations.length > 0 && (
        <View style={styles.recommendations}>
          {recommendations.slice(0, 3).map((rec, i) => (
            <Text key={i} style={[styles.recommendation, { color: colors.onSurfaceVariant }]}>
              <Text style={{ color: colors.cyberWarning }}>→</Text> {rec}
            </Text>
          ))}
        </View>
      )}
    </View>
  );
};

// --- Banister Verdict ---

export const BanisterVerdict: React.FC<{ banisterData: AugeAdaptiveCache['banister'] }> = ({ banisterData }) => {
  const colors = useColors();
  if (!banisterData) return null;
  return (
    <View style={styles.verdict}>
      <ZapIcon size={12} color={colors.cyberWarning} />
      <Text style={[styles.verdictText, { color: colors.onSurfaceVariant }]}>{banisterData.verdict || 'Datos insuficientes para veredicto Banister.'}</Text>
    </View>
  );
};

// --- Main Composite Component ---

interface AugeDeepViewProps {
  cache: AugeAdaptiveCache;
  showSections?: ('gp' | 'bayesian' | 'banister' | 'self')[];
  compact?: boolean;
  defaultOpen?: boolean;
}

const AugeDeepView: React.FC<AugeDeepViewProps> = ({
  cache,
  showSections = ['gp', 'bayesian', 'banister', 'self'],
  compact = false,
  defaultOpen = false,
}) => {
  const [isOpen, setIsOpen] = useState(defaultOpen);
  const [openSections, setOpenSections] = useState<Set<string>>(
    new Set(defaultOpen ? showSections : [showSections[0]])
  );
  const colors = useColors();

  const toggleSection = (section: string) => {
    const newOpenSections = new Set(openSections);
    if (newOpenSections.has(section)) {
      newOpenSections.delete(section);
    } else {
      newOpenSections.add(section);
    }
    setOpenSections(newOpenSections);
  };

  if (!isOpen) {
    return (
      <Pressable style={[styles.collapsed, { borderColor: colors.outlineVariant, backgroundColor: colors.surfaceContainer }]} onPress={() => setIsOpen(true)}>
        <View style={styles.collapsedRow}>
          <BrainIcon size={16} color={colors.primary} />
          <Text style={[styles.collapsedText, { color: colors.onSurfaceVariant }]}>AUGE Deep View</Text>
          <ChevronDownIcon size={14} color={colors.onSurfaceVariant} />
        </View>
      </Pressable>
    );
  }

  return (
    <View style={styles.expanded}>
      <Pressable style={[styles.closeButton, { borderColor: colors.outlineVariant, backgroundColor: colors.surfaceContainer }]} onPress={() => setIsOpen(false)}>
        <Text style={[styles.closeText, { color: colors.primary }]}>Cerrar Deep View</Text>
        <ChevronDownIcon size={14} color={colors.primary} style={{ transform: [{ rotate: '180deg' }] }} />
      </Pressable>
      <ScrollView>
        {showSections.includes('gp') && (
          <SectionToggle
            title="Curva GP de Fatiga"
            icon={<ActivityIcon size={12} color={colors.secondary} />}
            color={colors.secondary}
            isOpen={openSections.has('gp')}
            onToggle={() => toggleSection('gp')}
          >
            <GPFatigueCurve data={cache.gpCurve} compact={compact} />
          </SectionToggle>
        )}
        {showSections.includes('bayesian') && (
          <SectionToggle
            title="Confianza Bayesiana"
            icon={<TargetIcon size={12} color={colors.primary} />}
            color={getConfidenceColor(cache.totalObservations)}
            isOpen={openSections.has('bayesian')}
            onToggle={() => toggleSection('bayesian')}
          >
            <BayesianConfidence
              totalObservations={cache.totalObservations}
              personalizedRecoveryHours={cache.personalizedRecoveryHours}
              compact={compact}
            />
          </SectionToggle>
        )}
        {showSections.includes('banister') && (
          <SectionToggle
            title="Sistema Banister"
            icon={<TrendingUpIcon size={12} color={colors.cyberWarning} />}
            color={colors.cyberWarning}
            isOpen={openSections.has('banister')}
            onToggle={() => toggleSection('banister')}
          >
            {cache.banister?.systems?.muscular ? (
              <BanisterTrend systemData={cache.banister.systems.muscular} compact={compact} />
            ) : (
              <BanisterTrend systemData={null} compact={compact} />
            )}
            <BanisterVerdict banisterData={cache.banister} />
          </SectionToggle>
        )}
        {showSections.includes('self') && cache.selfImprovement && (
          <SectionToggle
            title="Mejora Personal"
            icon={<ZapIcon size={12} color={colors.cyberWarning} />}
            color={colors.cyberWarning}
            isOpen={openSections.has('self')}
            onToggle={() => toggleSection('self')}
          >
            <SelfImprovementScore
              score={cache.selfImprovement.overall_prediction_score}
              trend={cache.selfImprovement.improvement_trend}
              recommendations={cache.selfImprovement.recommendations}
              compact={compact}
            />
          </SectionToggle>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  collapsed: {
    borderWidth: 1,
    borderRadius: 12,
    paddingVertical: 10,
  },
  collapsedRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  collapsedText: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
    opacity: 0.8,
  },
  expanded: {
    // Spacer for scrolling content if needed
  },
  closeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    borderWidth: 1,
    borderRadius: 12,
    marginBottom: 16,
  },
  closeText: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
    marginRight: 8,
  },
  sectionContainer: {
    borderWidth: 1,
    borderRadius: 12,
    overflow: 'hidden',
    marginBottom: 12,
  },
  sectionHeader: {
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  titleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  sectionTitle: {
    fontSize: 11,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  sectionContent: {
    paddingHorizontal: 16,
    paddingBottom: 20,
  },
  placeholder: {
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 32,
  },
  placeholderText: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
    paddingHorizontal: 4,
  },
  footerText: {
    fontSize: 10,
    fontWeight: '700',
  },
  confidenceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  confidenceLabel: {
    fontSize: 13,
    fontWeight: '800',
  },
  observationCount: {
    fontSize: 11,
    fontFamily: 'monospace',
  },
  progressBar: {
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 4,
  },
  personalizedList: {
    marginTop: 16,
  },
  personalizedItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  muscleName: {
    fontSize: 11,
  },
  hoursText: {
    fontSize: 11,
    fontFamily: 'monospace',
    fontWeight: 'bold',
  },
  legend: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 20,
    marginTop: 12,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  legendDot: {
    width: 10,
    height: 3,
    borderRadius: 1.5,
  },
  legendText: {
    fontSize: 10,
  },
  nextSession: {
    fontSize: 11,
    textAlign: 'center',
    marginTop: 12,
    fontWeight: '700',
  },
  verdict: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginTop: 12,
  },
  verdictText: {
    fontSize: 10,
    fontWeight: '500',
    fontStyle: 'italic',
    lineHeight: 16,
    flex: 1,
  },
  scoreRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'center',
  },
  score: {
    fontSize: 28,
    fontWeight: '900',
  },
  scoreMax: {
    fontSize: 11,
    fontWeight: '700',
    marginLeft: 4,
  },
  sparkline: {
    width: 60,
    height: 24,
    marginLeft: 12,
  },
  recommendations: {
    marginTop: 16,
  },
  recommendation: {
    fontSize: 11,
    lineHeight: 16,
    marginBottom: 6,
  },
});

export default AugeDeepView;
