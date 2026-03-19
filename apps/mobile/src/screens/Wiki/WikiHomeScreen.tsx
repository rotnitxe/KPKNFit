import React, { useMemo } from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '@/components/ScreenShell';
import { WikiSearchBar } from '@/components/wiki/WikiSearchBar';
import { AnimatedCategoryCard } from '@/components/wiki/AnimatedCategoryCard';
import { AnimatedListItem } from '@/components/wiki/AnimatedListItem';
import { AnimatedArticleCard } from '@/components/wiki/AnimatedArticleCard';
import {
  WikiCategoryCardSkeleton,
  WikiArticleCardSkeleton,
  WikiListItemSkeleton,
} from '@/components/wiki/WikiSkeleton';
import {
  WIKI_JOINTS,
  WIKI_MOVEMENT_PATTERNS,
  WIKI_MUSCLES,
  WIKI_TENDONS,
  searchWiki,
} from '@/data/wikiData';
import { WIKI_CHAIN_DEFINITIONS } from '@/data/wikiExploreData';
import type { WikiStackParamList } from '@/navigation/types';
import { useColors } from '@/theme';

type WikiHomeNavProp = NativeStackNavigationProp<WikiStackParamList, 'WikiHome'>;

export function WikiHomeScreen() {
  const colors = useColors();
  const navigation = useNavigation<WikiHomeNavProp>();
  const [query, setQuery] = React.useState('');
  const [isLoading, setIsLoading] = React.useState(true);

  React.useEffect(() => {
    const timer = setTimeout(() => setIsLoading(false), 600);
    return () => clearTimeout(timer);
  }, []);

  const filteredMuscles = useMemo(() => searchWiki(WIKI_MUSCLES, query), [query]);
  const filteredJoints = useMemo(() => searchWiki(WIKI_JOINTS, query), [query]);
  const filteredTendons = useMemo(() => searchWiki(WIKI_TENDONS, query), [query]);
  const filteredPatterns = useMemo(() => searchWiki(WIKI_MOVEMENT_PATTERNS, query), [query]);

  const featuredMuscle = filteredMuscles[0] ?? WIKI_MUSCLES[0];
  const featuredJoint = filteredJoints[0] ?? WIKI_JOINTS[0];
  const featuredTendon = filteredTendons[0] ?? WIKI_TENDONS[0];
  const featuredPattern = filteredPatterns[0] ?? WIKI_MOVEMENT_PATTERNS[0];

  const hasAnyContent =
    filteredMuscles.length > 0 ||
    filteredJoints.length > 0 ||
    filteredTendons.length > 0 ||
    filteredPatterns.length > 0;

  const labCards = useMemo(
    () => [
      {
        key: 'lab-biomechanics',
        title: 'Biomecánica aplicada',
        category: 'Laboratorio',
        description:
          'Palancas, vectores y lectura corporal para entender sentadillas, bisagras y empujes.',
        readTime: '4 min',
        onPress: () => navigation.navigate('WikiBiomechanics'),
      },
      {
        key: 'lab-mobility',
        title: 'Movilidad útil',
        category: 'Laboratorio',
        description:
          'Rutinas de cadera, hombro y tobillo para entrar mejor a la sesión sin forzar rangos.',
        readTime: '5 min',
        onPress: () => navigation.navigate('WikiMobility'),
      },
      {
        key: `article-muscle-${featuredMuscle.id}`,
        title: featuredMuscle.name,
        category: 'Músculo',
        description: featuredMuscle.description,
        readTime: '3 min',
        onPress: () =>
          navigation.navigate('WikiArticle', {
            articleType: 'muscle',
            articleId: featuredMuscle.id,
          }),
      },
      {
        key: `article-pattern-${featuredPattern.id}`,
        title: featuredPattern.name,
        category: 'Patrón',
        description: featuredPattern.description,
        readTime: '3 min',
        onPress: () =>
          navigation.navigate('WikiArticle', {
            articleType: 'pattern',
            articleId: featuredPattern.id,
          }),
      },
    ],
    [featuredMuscle, featuredPattern, navigation],
  );

  const spotlightItems = useMemo(
    () => [
      {
        key: `joint-${featuredJoint.id}`,
        title: featuredJoint.name,
        subtitle: featuredJoint.description,
        onPress: () => navigation.navigate('WikiJointDetail', { jointId: featuredJoint.id }),
      },
      {
        key: `tendon-${featuredTendon.id}`,
        title: featuredTendon.name,
        subtitle: featuredTendon.description,
        onPress: () => navigation.navigate('WikiTendonDetail', { tendonId: featuredTendon.id }),
      },
      {
        key: `pattern-${featuredPattern.id}`,
        title: featuredPattern.name,
        subtitle: featuredPattern.description,
        onPress: () =>
          navigation.navigate('WikiPatternDetail', { patternId: featuredPattern.id }),
      },
      {
        key: `muscle-${featuredMuscle.id}`,
        title: featuredMuscle.name,
        subtitle: featuredMuscle.description,
        onPress: () => navigation.navigate('WikiMuscleDetail', { muscleId: featuredMuscle.id }),
      },
    ],
    [featuredJoint, featuredMuscle, featuredPattern, featuredTendon, navigation],
  );

  return (
    <ScreenShell
      title="WikiLab"
      subtitle="La enciclopedia definitiva del entrenamiento, biomecánica y anatomía aplicada."
      showBack={false}
    >
      <View style={styles.container}>
        <WikiSearchBar query={query} onChangeQuery={setQuery} onClear={() => setQuery('')} />

        {!hasAnyContent && query.length > 0 ? (
          <View style={[styles.emptyState, { backgroundColor: colors.surfaceContainer }]}>
            <Text style={[styles.emptyStateTitle, { color: colors.onSurface }]}>
              Sin resultados
            </Text>
            <Text style={[styles.emptyStateText, { color: colors.onSurfaceVariant }]}>
              No encontramos coincidencias para "{query}". Intenta con otro término.
            </Text>
          </View>
        ) : isLoading ? (
          <>
            <WikiCategoryCardSkeleton count={4} />
            <WikiArticleCardSkeleton count={2} />
            <WikiListItemSkeleton count={4} />
          </>
        ) : (
          <>
            <View style={styles.categoryGrid}>
              <AnimatedCategoryCard
                title="Músculos"
                subtitle="Anatomía"
                count={filteredMuscles.length}
                accent="purple"
                index={0}
                onPress={() => {
                  if (filteredMuscles[0]) {
                    navigation.navigate('WikiMuscleDetail', { muscleId: filteredMuscles[0].id });
                  }
                }}
              />
              <AnimatedCategoryCard
                title="Articulaciones"
                subtitle="Biomecánica"
                count={filteredJoints.length}
                accent="sky"
                index={1}
                onPress={() => {
                  if (filteredJoints[0]) {
                    navigation.navigate('WikiJointDetail', { jointId: filteredJoints[0].id });
                  }
                }}
              />
              <AnimatedCategoryCard
                title="Tendones"
                subtitle="Recuperación"
                count={filteredTendons.length}
                accent="amber"
                index={2}
                onPress={() => {
                  if (filteredTendons[0]) {
                    navigation.navigate('WikiTendonDetail', { tendonId: filteredTendons[0].id });
                  }
                }}
              />
              <AnimatedCategoryCard
                title="Patrones"
                subtitle="Movimiento"
                count={filteredPatterns.length}
                accent="emerald"
                index={3}
                onPress={() => {
                  if (filteredPatterns[0]) {
                    navigation.navigate('WikiPatternDetail', { patternId: filteredPatterns[0].id });
                  }
                }}
              />
            </View>

            <View style={styles.section}>
              <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
                Laboratorios
              </Text>
              <View style={styles.cardsGrid}>
                {labCards.map((card, index) => (
                  <AnimatedArticleCard
                    key={card.key}
                    title={card.title}
                    category={card.category}
                    description={card.description}
                    readTime={card.readTime}
                    onPress={card.onPress}
                    index={index}
                  />
                ))}
              </View>
            </View>

            {WIKI_CHAIN_DEFINITIONS.length > 0 && (
              <View style={styles.section}>
                <View style={styles.sectionHeader}>
                  <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
                    Cadenas Cinéticas
                  </Text>
                  <Text style={[styles.sectionCount, { color: colors.onSurfaceVariant }]}>
                    {WIKI_CHAIN_DEFINITIONS.length} Grupos
                  </Text>
                </View>
                <View style={styles.chainList}>
                  {WIKI_CHAIN_DEFINITIONS.map((chain, index) => (
                    <AnimatedListItem
                      key={chain.id}
                      title={chain.title}
                      subtitle={chain.subtitle}
                      index={index}
                      onPress={() => navigation.navigate('WikiChainDetail', { chainId: chain.id })}
                    />
                  ))}
                </View>
              </View>
            )}

            <View style={styles.section}>
              <View style={styles.sectionHeader}>
                <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
                  Resultados destacados
                </Text>
                <Text style={[styles.sectionCount, { color: colors.onSurfaceVariant }]}>
                  {spotlightItems.length} elementos
                </Text>
              </View>

              <FlatList
                data={spotlightItems}
                keyExtractor={item => item.key}
                renderItem={({ item, index }) => (
                  <AnimatedListItem
                    title={item.title}
                    subtitle={item.subtitle}
                    onPress={item.onPress}
                    index={index}
                  />
                )}
                scrollEnabled={false}
                ItemSeparatorComponent={() => <View style={{ height: 0 }} />}
              />
            </View>
          </>
        )}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  categoryGrid: {
    gap: 12,
  },
  cardsGrid: {
    gap: 12,
  },
  chainList: {
    gap: 8,
  },
  section: {
    gap: 12,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  sectionTitle: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  sectionCount: {
    fontSize: 10,
    fontWeight: '700',
    opacity: 0.5,
  },
  emptyState: {
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
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
