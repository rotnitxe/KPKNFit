import React, { useMemo, useState } from 'react';
import { StyleSheet, Text, View, Pressable } from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { useBodyStore } from '@/stores/bodyStore';
import { useColors } from '@/theme';

type ScenarioId = 'squat' | 'hinge';

function formatNumber(value: number | null, decimals = 1) {
  if (value === null) return '—';
  return value.toFixed(decimals);
}

function safeRatio(numerator: number | null, denominator: number | null) {
  if (numerator === null || denominator === null || denominator === 0) return null;
  return numerator / denominator;
}

export function WikiBiomechanicsScreen() {
  const colors = useColors();
  const biomechanicalData = useBodyStore(state => state.biomechanicalData);
  const biomechanicalAnalysis = useBodyStore(state => state.biomechanicalAnalysis);
  const [scenario, setScenario] = useState<ScenarioId>('squat');

  const metrics = useMemo(() => {
    const height = biomechanicalData?.height ?? null;
    const wingspan = biomechanicalData?.wingspan ?? null;
    const torso = biomechanicalData?.torsoLength ?? null;
    const femur = biomechanicalData?.femurLength ?? null;
    const tibia = biomechanicalData?.tibiaLength ?? null;
    const humerus = biomechanicalData?.humerusLength ?? null;
    const forearm = biomechanicalData?.forearmLength ?? null;
    const apeIndex = height !== null && wingspan !== null ? wingspan - height : null;

    return {
      height,
      wingspan,
      torso,
      femur,
      tibia,
      humerus,
      forearm,
      apeIndex,
      torsoFemurRatio: safeRatio(torso, femur),
      humerusForearmRatio: safeRatio(humerus, forearm),
    };
  }, [biomechanicalData]);

  const scenarioInsight = useMemo(() => {
    if (scenario === 'squat') {
      if (metrics.torsoFemurRatio === null) {
        return 'Sin datos suficientes para leer la sentadilla con precisión.';
      }

      if (metrics.torsoFemurRatio < 0.95) {
        return 'Torso más corto frente al fémur: la sentadilla pedirá más inclinación y control de cadera.';
      }

      return 'Leverage más equilibrado para sentadilla: el torso puede mantenerse relativamente más vertical.';
    }

    if (metrics.apeIndex === null) {
      return 'Sin envergadura suficiente no podemos leer la bisagra con precisión.';
    }

    if (metrics.apeIndex > 0) {
      return 'Envergadura favorable para bisagra y tracción: la barra puede mantenerse más cerca del cuerpo.';
    }

    return 'Brazos más cortos para bisagra: la carga puede exigir mayor rango de cadera y espalda alta.';
  }, [metrics.apeIndex, metrics.torsoFemurRatio, scenario]);

  const hasData = Boolean(biomechanicalData);
  const analysisAdvantages = biomechanicalAnalysis?.advantages ?? [];
  const analysisChallenges = biomechanicalAnalysis?.challenges ?? [];
  const analysisRecommendations = biomechanicalAnalysis?.exerciseSpecificRecommendations ?? [];

  return (
    <ScreenShell title="Biomecánica" subtitle="Palancas, rangos y lectura corporal del atleta">
      <View style={styles.container}>
        <View style={[styles.heroCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>WikiLab Engineering</Text>
          <Text style={[styles.heroTitle, { color: colors.onSurface }]}>Palitos biomecánicos</Text>
          <Text style={[styles.bodyText, { color: colors.onSurfaceVariant }]}>
            La lectura útil de tu cuerpo no es un adorno: ayuda a entender por qué ciertas palancas
            se sienten más favorables en sentadilla, peso muerto o press.
          </Text>
        </View>

        <View style={styles.scenarioRow}>
          <Pressable
            onPress={() => setScenario('squat')}
            style={[
              styles.scenarioCard,
              {
                backgroundColor: scenario === 'squat' ? `${colors.primary}1A` : colors.surface,
                borderColor: scenario === 'squat' ? colors.primary : colors.outlineVariant,
              },
            ]}
            accessibilityRole="button"
            accessibilityLabel="Escenario sentadilla"
          >
            <Text style={[styles.scenarioTitle, { color: colors.onSurface }]}>Sentadilla</Text>
            <Text style={[styles.scenarioSubtitle, { color: colors.onSurfaceVariant }]}>
              Lectura de torso y fémur
            </Text>
          </Pressable>

          <Pressable
            onPress={() => setScenario('hinge')}
            style={[
              styles.scenarioCard,
              {
                backgroundColor: scenario === 'hinge' ? `${colors.primary}1A` : colors.surface,
                borderColor: scenario === 'hinge' ? colors.primary : colors.outlineVariant,
              },
            ]}
            accessibilityRole="button"
            accessibilityLabel="Escenario bisagra"
          >
            <Text style={[styles.scenarioTitle, { color: colors.onSurface }]}>Bisagra</Text>
            <Text style={[styles.scenarioSubtitle, { color: colors.onSurfaceVariant }]}>
              Lectura de envergadura y cadera
            </Text>
          </Pressable>
        </View>

        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Diagnóstico instantáneo</Text>
          <Text style={[styles.bodyText, { color: colors.onSurface }]}>{scenarioInsight}</Text>
          <Text style={[styles.metaText, { color: colors.onSurfaceVariant }]}>
            Escenario actual: {scenario === 'squat' ? 'Sentadilla' : 'Bisagra'}.
          </Text>
        </View>

        <View style={styles.metricGrid}>
          {[
            { label: 'Altura', value: `${formatNumber(metrics.height, 0)} cm` },
            { label: 'Envergadura', value: `${formatNumber(metrics.wingspan, 0)} cm` },
            { label: 'Ape index', value: `${formatNumber(metrics.apeIndex)} cm` },
            { label: 'Torso/Fémur', value: formatNumber(metrics.torsoFemurRatio) },
            { label: 'Húmero/Antebrazo', value: formatNumber(metrics.humerusForearmRatio) },
            { label: 'Tibia', value: `${formatNumber(metrics.tibia, 0)} cm` },
          ].map(metric => (
            <View
              key={metric.label}
              style={[styles.metricCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
            >
              <Text style={[styles.metricLabel, { color: colors.onSurfaceVariant }]}>{metric.label}</Text>
              <Text style={[styles.metricValue, { color: colors.onSurface }]}>{metric.value}</Text>
            </View>
          ))}
        </View>

        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Antropometría activa</Text>
          <Text style={[styles.bodyText, { color: colors.onSurfaceVariant }]}>
            Fémur {formatNumber(metrics.femur, 1)} cm · Tibia {formatNumber(metrics.tibia, 1)} cm ·
            Torso {formatNumber(metrics.torso, 1)} cm · Húmero {formatNumber(metrics.humerus, 1)} cm ·
            Antebrazo {formatNumber(metrics.forearm, 1)} cm
          </Text>
        </View>

        {hasData ? (
          <>
            {analysisAdvantages.length > 0 && (
              <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
                <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Ventajas</Text>
                {analysisAdvantages.map(item => (
                  <View key={item.title} style={styles.listItem}>
                    <Text style={[styles.listItemTitle, { color: colors.onSurface }]}>{item.title}</Text>
                    <Text style={[styles.listItemBody, { color: colors.onSurfaceVariant }]}>
                      {item.explanation}
                    </Text>
                  </View>
                ))}
              </View>
            )}

            {analysisChallenges.length > 0 && (
              <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
                <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Retos</Text>
                {analysisChallenges.map(item => (
                  <View key={item.title} style={styles.listItem}>
                    <Text style={[styles.listItemTitle, { color: colors.onSurface }]}>{item.title}</Text>
                    <Text style={[styles.listItemBody, { color: colors.onSurfaceVariant }]}>
                      {item.explanation}
                    </Text>
                  </View>
                ))}
              </View>
            )}

            {analysisRecommendations.length > 0 && (
              <View
                style={[
                  styles.card,
                  { backgroundColor: `${colors.primary}0D`, borderColor: `${colors.primary}33` },
                ]}
              >
                <Text style={[styles.sectionTitle, { color: colors.primary }]}>Recomendaciones</Text>
                {analysisRecommendations.map(item => (
                  <View key={item.exerciseName} style={styles.listItem}>
                    <Text style={[styles.listItemTitle, { color: colors.onSurface }]}>{item.exerciseName}</Text>
                    <Text style={[styles.listItemBody, { color: colors.onSurfaceVariant }]}>
                      {item.recommendation}
                    </Text>
                  </View>
                ))}
              </View>
            )}
          </>
        ) : (
          <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Sin datos aún</Text>
            <Text style={[styles.bodyText, { color: colors.onSurfaceVariant }]}>
              Cuando se hidraten tus medidas corporales, esta vista mostrará ventajas, retos y lectura de
              palancas sin inventar números.
            </Text>
          </View>
        )}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  heroCard: {
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 18,
    gap: 10,
  },
  heroTitle: {
    fontSize: 28,
    fontWeight: '800',
    lineHeight: 32,
  },
  sectionTitle: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  bodyText: {
    fontSize: 15,
    lineHeight: 22,
  },
  scenarioRow: {
    flexDirection: 'row',
    gap: 12,
  },
  scenarioCard: {
    flex: 1,
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 14,
    gap: 6,
  },
  scenarioTitle: {
    fontSize: 15,
    fontWeight: '800',
  },
  scenarioSubtitle: {
    fontSize: 12,
    lineHeight: 18,
  },
  card: {
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 16,
    gap: 10,
  },
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  metricCard: {
    width: '48%',
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 14,
    gap: 4,
  },
  metricLabel: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
  },
  metricValue: {
    fontSize: 20,
    fontWeight: '800',
  },
  metaText: {
    fontSize: 12,
    fontWeight: '600',
  },
  listItem: {
    gap: 4,
    marginTop: 6,
  },
  listItemTitle: {
    fontSize: 14,
    fontWeight: '800',
  },
  listItemBody: {
    fontSize: 13,
    lineHeight: 20,
  },
});

