import React, { useMemo } from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable } from 'react-native';
import { useRoute, RouteProp, useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '../../components/ScreenShell';
import { useColors } from '../../theme';
import { WIKI_MUSCLES, WIKI_JOINTS, WIKI_TENDONS, WIKI_MOVEMENT_PATTERNS } from '../../data/wikiData';
import type { WikiStackParamList } from '../../navigation/types';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';

type WikiArticleRouteProp = RouteProp<WikiStackParamList, 'WikiArticle'>;
type WikiArticleNavigationProp = NativeStackNavigationProp<WikiStackParamList>;

type ArticleType = 'muscle' | 'joint' | 'tendon' | 'pattern';

interface ArticleData {
  type: ArticleType;
  id: string;
  name: string;
  subtitle: string;
  description: string;
  relatedItems?: Array<{ id: string; name: string; type: 'muscle' | 'joint' | 'tendon' | 'pattern' }>;
  commonInjuries?: Array<{ name: string; description: string }>;
  movementPatterns?: string[];
  exampleExercises?: string[];
  detailBadge: string;
}

interface AnimatedRelatedItemProps {
  item: { id: string; name: string; type: 'muscle' | 'joint' | 'tendon' | 'pattern' };
  index: number;
  onPress: () => void;
  colors: any;
}

const AnimatedRelatedItem: React.FC<AnimatedRelatedItemProps> = ({ item, index, onPress, colors }) => {
  const handlePress = () => {
    ReactNativeHapticFeedback.trigger('impactLight', {
      enableVibrateFallback: true,
      ignoreAndroidSystemSettings: false,
    });
    onPress();
  };

  return (
    <Pressable onPress={handlePress} style={styles.relatedItemWrapper}>
      <View
        style={[
          styles.relatedItem,
          { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant },
        ]}
      >
        <View>
          <Text style={[styles.relatedItemText, { color: colors.onSurface }]}>
            {item.name}
          </Text>
          <Text style={[styles.relatedItemType, { color: colors.onSurfaceVariant }]}>
            {item.type === 'muscle'
              ? 'Músculo'
              : item.type === 'joint'
                ? 'Articulación'
                : item.type === 'tendon'
                  ? 'Tendón'
                  : 'Patrón'}
          </Text>
        </View>
      </View>
    </Pressable>
  );
};

export function WikiArticleScreen() {
  const route = useRoute<WikiArticleRouteProp>();
  const navigation = useNavigation<WikiArticleNavigationProp>();
  const colors = useColors();

  const article = useMemo<ArticleData | null>(() => {
    const { articleType, articleId } = route.params;

    if (articleType === 'muscle') {
      const muscle = WIKI_MUSCLES.find(item => item.id === articleId);
      if (!muscle) return null;

      return {
        type: 'muscle',
        id: muscle.id,
        name: muscle.name,
        subtitle: 'Anatomía muscular',
        description: muscle.description,
        relatedItems: [
          ...(muscle.relatedJoints ?? []).map(jointId => {
            const joint = WIKI_JOINTS.find(item => item.id === jointId);
            return joint ? { id: joint.id, name: joint.name, type: 'joint' as const } : null;
          }),
          ...(muscle.relatedTendons ?? []).map(tendonId => {
            const tendon = WIKI_TENDONS.find(item => item.id === tendonId);
            return tendon ? { id: tendon.id, name: tendon.name, type: 'tendon' as const } : null;
          }),
        ].filter((value): value is NonNullable<typeof value> => value !== null),
        commonInjuries: muscle.commonInjuries?.map(item => ({
          name: item.name,
          description: item.description,
        })),
        movementPatterns: muscle.movementPatterns,
        detailBadge: 'Músculo',
      };
    }

    if (articleType === 'joint') {
      const joint = WIKI_JOINTS.find(item => item.id === articleId);
      if (!joint) return null;

      return {
        type: 'joint',
        id: joint.id,
        name: joint.name,
        subtitle: 'Biomecánica articular',
        description: joint.description,
        relatedItems: [
          ...joint.musclesCrossing.map(muscleId => {
            const muscle = WIKI_MUSCLES.find(item => item.id === muscleId);
            return muscle ? { id: muscle.id, name: muscle.name, type: 'muscle' as const } : null;
          }),
          ...joint.tendonsRelated.map(tendonId => {
            const tendon = WIKI_TENDONS.find(item => item.id === tendonId);
            return tendon ? { id: tendon.id, name: tendon.name, type: 'tendon' as const } : null;
          }),
        ].filter((value): value is NonNullable<typeof value> => value !== null),
        commonInjuries: joint.commonInjuries?.map(item => ({
          name: item.name,
          description: item.description,
        })),
        movementPatterns: joint.movementPatterns,
        detailBadge: `Tipo ${joint.type}`,
      };
    }

    if (articleType === 'tendon') {
      const tendon = WIKI_TENDONS.find(item => item.id === articleId);
      if (!tendon) return null;

      const muscle = WIKI_MUSCLES.find(item => item.id === tendon.muscleId);
      const joint = tendon.jointId ? WIKI_JOINTS.find(item => item.id === tendon.jointId) : null;

      return {
        type: 'tendon',
        id: tendon.id,
        name: tendon.name,
        subtitle: 'Tejido conectivo',
        description: tendon.description,
        relatedItems: [
          muscle ? { id: muscle.id, name: muscle.name, type: 'muscle' as const } : null,
          joint ? { id: joint.id, name: joint.name, type: 'joint' as const } : null,
        ].filter((value): value is NonNullable<typeof value> => value !== null),
        commonInjuries: tendon.commonInjuries?.map(item => ({
          name: item.name,
          description: item.description,
        })),
        detailBadge: 'Tendón',
      };
    }

    const pattern = WIKI_MOVEMENT_PATTERNS.find(item => item.id === articleId);
    if (!pattern) return null;

    return {
      type: 'pattern',
      id: pattern.id,
      name: pattern.name,
      subtitle: 'Patrón de movimiento',
      description: pattern.description,
      relatedItems: [
        ...pattern.primaryMuscles.map(muscleId => {
          const muscle = WIKI_MUSCLES.find(item => item.id === muscleId);
          return muscle ? { id: muscle.id, name: muscle.name, type: 'muscle' as const } : null;
        }),
        ...pattern.primaryJoints.map(jointId => {
          const joint = WIKI_JOINTS.find(item => item.id === jointId);
          return joint ? { id: joint.id, name: joint.name, type: 'joint' as const } : null;
        }),
      ].filter((value): value is NonNullable<typeof value> => value !== null),
      exampleExercises: pattern.exampleExercises,
      detailBadge: 'Patrón',
    };
  }, [route.params]);

  if (!article) {
    return (
      <ScreenShell title="Error" subtitle="Artículo no encontrado">
        <View style={styles.container}>
          <View style={[styles.emptyState, { backgroundColor: colors.surfaceContainer }]}>
            <Text style={[styles.emptyStateTitle, { color: colors.onSurface }]}>
              Artículo no disponible
            </Text>
            <Text style={[styles.emptyStateText, { color: colors.onSurfaceVariant }]}>
              El artículo solicitado no existe o fue removido de la base de datos.
            </Text>
          </View>
        </View>
      </ScreenShell>
    );
  }

  return (
    <ScreenShell title={article.name} subtitle={article.subtitle}>
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.section}>
          <View style={[styles.card, { backgroundColor: colors.surfaceContainerHigh }]}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              {article.detailBadge}
            </Text>
            <Text style={[styles.bodyText, { color: colors.onSurface }]}>{article.description}</Text>
          </View>
        </View>

        {article.relatedItems && article.relatedItems.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Estructuras relacionadas
            </Text>
            {article.relatedItems.map((item, index) => (
              <AnimatedRelatedItem
                key={item.id}
                item={item}
                index={index}
                colors={colors}
                onPress={() => {
                  if (item.type === 'muscle') {
                    navigation.navigate('WikiMuscleDetail', { muscleId: item.id });
                  }
                  if (item.type === 'joint') {
                    navigation.navigate('WikiJointDetail', { jointId: item.id });
                  }
                  if (item.type === 'tendon') {
                    navigation.navigate('WikiTendonDetail', { tendonId: item.id });
                  }
                  if (item.type === 'pattern') {
                    navigation.navigate('WikiPatternDetail', { patternId: item.id });
                  }
                }}
              />
            ))}
          </View>
        )}

        {article.movementPatterns && article.movementPatterns.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Patrones de movimiento
            </Text>
            {article.movementPatterns.map((patternId, index) => {
              const pattern = WIKI_MOVEMENT_PATTERNS.find(item => item.id === patternId);
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

        {article.exampleExercises && article.exampleExercises.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Ejercicios de ejemplo
            </Text>
            {article.exampleExercises.map((exercise, index) => (
              <View
                key={`${exercise}-${index}`}
                style={[
                  styles.exampleItem,
                  { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant },
                ]}
              >
                <Text style={[styles.exampleItemText, { color: colors.onSurface }]}>{exercise}</Text>
              </View>
            ))}
          </View>
        )}

        {article.commonInjuries && article.commonInjuries.length > 0 && (
          <View style={styles.section}>
            <View
              style={[
                styles.card,
                { backgroundColor: `${colors.error}14`, borderColor: `${colors.error}33` },
              ]}
            >
              <Text style={[styles.sectionTitle, { color: colors.error }]}>Lesiones comunes</Text>
              {article.commonInjuries.map((injury, index) => (
                <View key={`${injury.name}-${index}`} style={styles.injuryItem}>
                  <Text style={[styles.injuryName, { color: colors.onSurface }]}>{injury.name}</Text>
                  <Text style={[styles.injuryDescription, { color: colors.onSurfaceVariant }]}>
                    {injury.description}
                  </Text>
                </View>
              ))}
            </View>
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
    marginBottom: 8,
    paddingHorizontal: 16,
  },
  card: {
    borderRadius: 24,
    paddingVertical: 16,
    paddingHorizontal: 16,
  },
  sectionTitle: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 2,
    textTransform: 'uppercase',
    marginBottom: 12,
  },
  bodyText: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '400',
  },
  relatedItemWrapper: {
    marginBottom: 8,
  },
  relatedItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  relatedItemText: {
    fontSize: 15,
    fontWeight: '700',
    marginBottom: 2,
  },
  relatedItemType: {
    fontSize: 11,
    fontWeight: '500',
    opacity: 0.6,
  },
  exampleItem: {
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 8,
  },
  exampleItemText: {
    fontSize: 15,
    fontWeight: '600',
  },
  injuryItem: {
    marginBottom: 16,
  },
  injuryName: {
    fontSize: 15,
    fontWeight: '700',
    marginBottom: 6,
  },
  injuryDescription: {
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '400',
  },
  emptyState: {
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
    marginTop: 16,
  },
  emptyStateTitle: {
    fontSize: 18,
    fontWeight: '800',
    marginBottom: 8,
  },
  emptyStateText: {
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
    opacity: 0.7,
  },
});
