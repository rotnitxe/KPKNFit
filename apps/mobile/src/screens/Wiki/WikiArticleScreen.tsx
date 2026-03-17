import React, { useMemo } from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { useRoute, RouteProp, useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '../../components/ScreenShell';
import { useColors } from '../../theme';
import { WIKI_MUSCLES, WIKI_JOINTS, WIKI_TENDONS, WIKI_MOVEMENT_PATTERNS } from '../../data/wikiData';
import type { WorkoutStackParamList } from '../../navigation/types';
import type { WikiMuscle, WikiJoint, WikiTendon, WikiMovementPattern } from '../../types/wiki';

type WikiArticleRouteProp = RouteProp<WorkoutStackParamList, 'WikiMuscleDetail' | 'WikiJointDetail' | 'WikiTendonDetail' | 'WikiPatternDetail'>;
type WikiArticleNavigationProp = NativeStackNavigationProp<WorkoutStackParamList>;

type ArticleType = 'muscle' | 'joint' | 'tendon' | 'pattern';

interface ArticleData {
  type: ArticleType;
  id: string;
  name: string;
  subtitle: string;
  description: string;
  relatedItems?: Array<{ id: string; name: string; type: ArticleType }>;
  commonInjuries?: Array<{ name: string; description: string }>;
  movementPatterns?: string[];
  exampleExercises?: string[];
}

export function WikiArticleScreen() {
  const route = useRoute<WikiArticleRouteProp>();
  const navigation = useNavigation<WikiArticleNavigationProp>();
  const colors = useColors();

  const { muscleId, jointId, tendonId, patternId } = route.params as {
    muscleId?: string;
    jointId?: string;
    tendonId?: string;
    patternId?: string;
  };

  const article: ArticleData | null = useMemo(() => {
    if (muscleId) {
      const muscle = WIKI_MUSCLES.find((m) => m.id === muscleId);
      if (!muscle) return null;

      const relatedJoints = muscle.relatedJoints?.map((jid) => {
        const joint = WIKI_JOINTS.find((j) => j.id === jid);
        return joint ? { id: joint.id, name: joint.name, type: 'joint' as ArticleType } : null;
      }).filter(Boolean) as Array<{ id: string; name: string; type: ArticleType }> | undefined;

      const relatedTendons = muscle.relatedTendons?.map((tid) => {
        const tendon = WIKI_TENDONS.find((t) => t.id === tid);
        return tendon ? { id: tendon.id, name: tendon.name, type: 'tendon' as ArticleType } : null;
      }).filter(Boolean) as Array<{ id: string; name: string; type: ArticleType }> | undefined;

      return {
        type: 'muscle',
        id: muscle.id,
        name: muscle.name,
        subtitle: 'Anatomía Muscular',
        description: muscle.description,
        relatedItems: [...(relatedJoints || []), ...(relatedTendons || [])],
        commonInjuries: muscle.commonInjuries?.map((i) => ({ name: i.name, description: i.description })),
        movementPatterns: muscle.movementPatterns,
      };
    }

    if (jointId) {
      const joint = WIKI_JOINTS.find((j) => j.id === jointId);
      if (!joint) return null;

      return {
        type: 'joint',
        id: joint.id,
        name: joint.name,
        subtitle: 'Biomecánica Articular',
        description: joint.description,
        commonInjuries: joint.commonInjuries?.map((i) => ({ name: i.name, description: i.description })),
        movementPatterns: joint.movementPatterns,
      };
    }

    if (tendonId) {
      const tendon = WIKI_TENDONS.find((t) => t.id === tendonId);
      if (!tendon) return null;

      return {
        type: 'tendon',
        id: tendon.id,
        name: tendon.name,
        subtitle: 'Tejido Conectivo',
        description: tendon.description,
        commonInjuries: tendon.commonInjuries?.map((i) => ({ name: i.name, description: i.description })),
      };
    }

    if (patternId) {
      const pattern = WIKI_MOVEMENT_PATTERNS.find((p) => p.id === patternId);
      if (!pattern) return null;

      return {
        type: 'pattern',
        id: pattern.id,
        name: pattern.name,
        subtitle: 'Patrón de Movimiento',
        description: pattern.description,
        exampleExercises: pattern.exampleExercises,
      };
    }

    return null;
  }, [muscleId, jointId, tendonId, patternId]);

  if (!article) {
    return (
      <ScreenShell title="Error" subtitle="Artículo no encontrado">
        <View style={styles.container}>
          <Text style={[styles.bodyText, { color: colors.onSurfaceVariant }]}>
            El artículo solicitado no existe o fue removido.
          </Text>
        </View>
      </ScreenShell>
    );
  }

  return (
    <ScreenShell title={article.name} subtitle={article.subtitle}>
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {/* Description Section */}
        <View style={[styles.section, styles.card, { backgroundColor: colors.surfaceContainerHigh }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Descripción</Text>
          <Text style={[styles.bodyText, { color: colors.onSurface }]}>{article.description}</Text>
        </View>

        {/* Related Items Section */}
        {article.relatedItems && article.relatedItems.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Estructuras relacionadas
            </Text>
            {article.relatedItems.map((item) => (
              <View
                key={item.id}
                style={[
                  styles.relatedItem,
                  { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant },
                ]}
              >
                <Text style={[styles.relatedItemText, { color: colors.onSurface }]}>
                  {item.name}
                </Text>
                <Text style={[styles.relatedItemType, { color: colors.onSurfaceVariant }]}>
                  {item.type === 'joint' ? 'Articulación' : item.type === 'tendon' ? 'Tendón' : ''}
                </Text>
              </View>
            ))}
          </View>
        )}

        {/* Movement Patterns Section */}
        {article.movementPatterns && article.movementPatterns.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Patrones de movimiento
            </Text>
            {article.movementPatterns.map((patternId) => {
              const pattern = WIKI_MOVEMENT_PATTERNS.find((p) => p.id === patternId);
              return pattern ? (
                <View
                  key={pattern.id}
                  style={[
                    styles.relatedItem,
                    { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant },
                  ]}
                >
                  <Text style={[styles.relatedItemText, { color: colors.onSurface }]}>
                    {pattern.name}
                  </Text>
                </View>
              ) : null;
            })}
          </View>
        )}

        {/* Example Exercises Section */}
        {article.exampleExercises && article.exampleExercises.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Ejercicios de ejemplo
            </Text>
            {article.exampleExercises.map((exercise, index) => (
              <View
                key={index}
                style={[
                  styles.exampleItem,
                  { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant },
                ]}
              >
                <Text style={[styles.exampleItemText, { color: colors.onSurface }]}>
                  {exercise}
                </Text>
              </View>
            ))}
          </View>
        )}

        {/* Common Injuries Section */}
        {article.commonInjuries && article.commonInjuries.length > 0 && (
          <View
            style={[
              styles.section,
              styles.card,
              { backgroundColor: 'rgba(255, 46, 67, 0.08)', borderColor: 'rgba(255, 46, 67, 0.2)' },
            ]}
          >
            <Text style={[styles.sectionTitle, { color: colors.error }]}>Lesiones comunes</Text>
            {article.commonInjuries.map((injury, index) => (
              <View key={index} style={styles.injuryItem}>
                <Text style={[styles.injuryName, { color: colors.onSurface }]}>
                  {injury.name}
                </Text>
                <Text style={[styles.injuryDescription, { color: colors.onSurfaceVariant }]}>
                  {injury.description}
                </Text>
              </View>
            ))}
          </View>
        )}
      </ScrollView>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  scrollView: {
    flex: 1,
  },
  section: {
    marginBottom: 16,
    paddingHorizontal: 16,
  },
  card: {
    borderRadius: 16,
    paddingVertical: 16,
    paddingHorizontal: 16,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 2,
    textTransform: 'uppercase',
    marginBottom: 12,
  },
  bodyText: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '400',
  },
  relatedItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginBottom: 8,
  },
  relatedItemText: {
    fontSize: 15,
    fontWeight: '600',
  },
  relatedItemType: {
    fontSize: 12,
    fontWeight: '400',
  },
  exampleItem: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginBottom: 8,
  },
  exampleItemText: {
    fontSize: 15,
    fontWeight: '500',
  },
  injuryItem: {
    marginBottom: 16,
  },
  injuryName: {
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 4,
  },
  injuryDescription: {
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '400',
  },
});
