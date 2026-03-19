import React, { useMemo } from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '@/components/ScreenShell';
import { WikiSearchBar } from '@/components/wiki/WikiSearchBar';
import { WikiCategoryCard } from '@/components/wiki/WikiCategoryCard';
import { WikiListItem } from '@/components/wiki/WikiListItem';
import { WikiArticleCard } from '@/components/wiki/WikiArticleCard';
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

  const filteredMuscles = useMemo(() => searchWiki(WIKI_MUSCLES, query), [query]);
  const filteredJoints = useMemo(() => searchWiki(WIKI_JOINTS, query), [query]);
  const filteredTendons = useMemo(() => searchWiki(WIKI_TENDONS, query), [query]);
  const filteredPatterns = useMemo(() => searchWiki(WIKI_MOVEMENT_PATTERNS, query), [query]);

  const featuredMuscle = filteredMuscles[0] ?? WIKI_MUSCLES[0];
  const featuredJoint = filteredJoints[0] ?? WIKI_JOINTS[0];
  const featuredTendon = filteredTendons[0] ?? WIKI_TENDONS[0];
  const featuredPattern = filteredPatterns[0] ?? WIKI_MOVEMENT_PATTERNS[0];

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
      subtitle="La biblioteca real de la PWA: músculos, articulaciones, tendones y patrones."
      showBack={false}
    >
      <View style={styles.container}>
        <WikiSearchBar query={query} onChangeQuery={setQuery} onClear={() => setQuery('')} />

        <View style={styles.categoryGrid}>
          <WikiCategoryCard
            title="Músculos"
            subtitle="Biblioteca"
            count={filteredMuscles.length}
            accent="sky"
            onPress={() => {
              if (filteredMuscles[0]) {
                navigation.navigate('WikiMuscleDetail', { muscleId: filteredMuscles[0].id });
              }
            }}
          />
          <WikiCategoryCard
            title="Articulaciones"
            subtitle="Biomecánica"
            count={filteredJoints.length}
            accent="purple"
            onPress={() => {
              if (filteredJoints[0]) {
                navigation.navigate('WikiJointDetail', { jointId: filteredJoints[0].id });
              }
            }}
          />
          <WikiCategoryCard
            title="Tendones"
            subtitle="Recuperación"
            count={filteredTendons.length}
            accent="amber"
            onPress={() => {
              if (filteredTendons[0]) {
                navigation.navigate('WikiTendonDetail', { tendonId: filteredTendons[0].id });
              }
            }}
          />
          <WikiCategoryCard
            title="Patrones"
            subtitle="Movimiento"
            count={filteredPatterns.length}
            accent="emerald"
            onPress={() => {
              if (filteredPatterns[0]) {
                navigation.navigate('WikiPatternDetail', { patternId: filteredPatterns[0].id });
              }
            }}
          />
        </View>

        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Laboratorios</Text>
        <View style={styles.cardsGrid}>
          {labCards.map(card => (
            <WikiArticleCard
              key={card.key}
              title={card.title}
              category={card.category}
              description={card.description}
              readTime={card.readTime}
              onPress={card.onPress}
            />
          ))}
        </View>

        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Cadenas</Text>
        <View style={styles.chainList}>
          {WIKI_CHAIN_DEFINITIONS.map(chain => (
            <WikiListItem
              key={chain.id}
              title={chain.title}
              subtitle={chain.subtitle}
              onPress={() => navigation.navigate('WikiChainDetail', { chainId: chain.id })}
            />
          ))}
        </View>

        <View style={styles.spotlightHeader}>
          <Text style={[styles.spotlightTitle, { color: colors.onSurfaceVariant }]}>
            Resultados destacados
          </Text>
        </View>

        <FlatList
          data={spotlightItems}
          keyExtractor={item => item.key}
          renderItem={({ item }) => (
            <WikiListItem title={item.title} subtitle={item.subtitle} onPress={item.onPress} />
          )}
          scrollEnabled={false}
          ItemSeparatorComponent={() => <View style={{ height: 0 }} />}
        />
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
    gap: 12,
  },
  spotlightHeader: {
    marginTop: 8,
  },
  sectionTitle: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 2,
    textTransform: 'uppercase',
    marginTop: 8,
  },
  spotlightTitle: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
});

