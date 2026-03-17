import React, { useMemo } from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '@/components/ScreenShell';
import { WikiSearchBar } from '@/components/wiki/WikiSearchBar';
import { WikiCategoryCard } from '@/components/wiki/WikiCategoryCard';
import { WikiListItem } from '@/components/wiki/WikiListItem';
import {
  WIKI_JOINTS,
  WIKI_MOVEMENT_PATTERNS,
  WIKI_MUSCLES,
  WIKI_TENDONS,
  searchWiki,
} from '@/data/wikiData';
import type { WikiStackParamList } from '@/navigation/AppNavigator';
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

  const spotlightItems = useMemo(
    () => [
      {
        key: `muscle-${filteredMuscles[0]?.id ?? 'none'}`,
        title: filteredMuscles[0]?.name ?? 'Sin resultados',
        subtitle: filteredMuscles[0]?.description ?? 'Prueba otra búsqueda.',
        onPress: () =>
          filteredMuscles[0] && navigation.navigate('WikiMuscleDetail', { muscleId: filteredMuscles[0].id }),
      },
      {
        key: `joint-${filteredJoints[0]?.id ?? 'none'}`,
        title: filteredJoints[0]?.name ?? 'Sin articulaciones',
        subtitle: filteredJoints[0]?.description ?? 'No encontramos articulaciones para esa búsqueda.',
        onPress: () =>
          filteredJoints[0] && navigation.navigate('WikiJointDetail', { jointId: filteredJoints[0].id }),
      },
      {
        key: `tendon-${filteredTendons[0]?.id ?? 'none'}`,
        title: filteredTendons[0]?.name ?? 'Sin tendones',
        subtitle: filteredTendons[0]?.description ?? 'No encontramos tendones para esa búsqueda.',
        onPress: () =>
          filteredTendons[0] && navigation.navigate('WikiTendonDetail', { tendonId: filteredTendons[0].id }),
      },
      {
        key: `pattern-${filteredPatterns[0]?.id ?? 'none'}`,
        title: filteredPatterns[0]?.name ?? 'Sin patrones',
        subtitle: filteredPatterns[0]?.description ?? 'No encontramos patrones para esa búsqueda.',
        onPress: () =>
          filteredPatterns[0] &&
          navigation.navigate('WikiPatternDetail', { patternId: filteredPatterns[0].id }),
      },
    ],
    [filteredJoints, filteredMuscles, filteredPatterns, filteredTendons, navigation],
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
  spotlightHeader: {
    marginTop: 8,
  },
  spotlightTitle: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
});
