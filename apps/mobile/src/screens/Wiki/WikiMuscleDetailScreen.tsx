import React, { useMemo } from 'react';
import { View, ScrollView, Text, Pressable, StyleSheet } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import {
  WIKI_MUSCLES,
  WIKI_JOINTS,
  WIKI_TENDONS,
  WIKI_MOVEMENT_PATTERNS
} from '../../data/wikiData';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { WorkoutStackParamList } from '../../navigation/AppNavigator';
import { useColors } from '@/theme';

export function WikiMuscleDetailScreen() {
  const route = useRoute<RouteProp<WorkoutStackParamList, 'WikiMuscleDetail'>>();
  const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
  const colors = useColors();
  const { muscleId } = route.params;

  const muscle = useMemo(() => WIKI_MUSCLES.find(m => m.id === muscleId), [muscleId]);

  if (!muscle) {
    return (
      <ScreenShell title="Error" subtitle="Músculo no encontrado.">
        <Text style={[styles.errorText, { color: colors.onSurface }]}>
          El músculo solicitado no existe.
        </Text>
      </ScreenShell>
    );
  }

  const joints = useMemo(() => WIKI_JOINTS.filter(j => muscle.relatedJoints?.includes(j.id)), [muscle]);
  const tendons = useMemo(() => WIKI_TENDONS.filter(t => muscle.relatedTendons?.includes(t.id)), [muscle]);
  const patterns = useMemo(() => WIKI_MOVEMENT_PATTERNS.filter(p => muscle.movementPatterns?.includes(p.id)), [muscle]);

  const RelatedItem = ({ item, onPress }: { item: { id: string; name: string }; onPress: () => void }) => (
    <Pressable
      onPress={onPress}
      style={[styles.relatedItem, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
    >
      <Text style={[styles.relatedItemText, { color: colors.onSurface }]}>{item.name}</Text>
    </Pressable>
  );

  return (
    <ScreenShell title={muscle.name} subtitle="Detalle muscular">
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={[styles.section, styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Descripción</Text>
          <Text style={[styles.bodyText, { color: colors.onSurface }]}>{muscle.description}</Text>
        </View>

        {joints.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Articulaciones relacionadas
            </Text>
            {joints.map(j => (
              <RelatedItem
                key={j.id}
                item={j}
                onPress={() => navigation.navigate('WikiJointDetail', { jointId: j.id })}
              />
            ))}
          </View>
        )}

        {tendons.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Tendones relacionados
            </Text>
            {tendons.map(t => (
              <RelatedItem
                key={t.id}
                item={t}
                onPress={() => navigation.navigate('WikiTendonDetail', { tendonId: t.id })}
              />
            ))}
          </View>
        )}

        {patterns.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Patrones de movimiento
            </Text>
            {patterns.map(p => (
              <RelatedItem
                key={p.id}
                item={p}
                onPress={() => navigation.navigate('WikiPatternDetail', { patternId: p.id })}
              />
            ))}
          </View>
        )}

        {muscle.commonInjuries && muscle.commonInjuries.length > 0 && (
          <View
            style={[
              styles.section,
              styles.card,
              { backgroundColor: `${colors.error}14`, borderColor: `${colors.error}33` },
            ]}
          >
            <Text style={[styles.sectionTitle, { color: colors.error }]}>Lesiones comunes</Text>
            {muscle.commonInjuries.map((injury, idx) => (
              <View key={idx} style={styles.injuryItem}>
                <Text style={[styles.injuryName, { color: colors.onSurface }]}>{injury.name}</Text>
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
  scrollView: {
    flex: 1,
  },
  section: {
    marginBottom: 24,
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
  },
  relatedItem: {
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginBottom: 8,
  },
  relatedItemText: {
    fontSize: 16,
    fontWeight: '600',
  },
  injuryItem: {
    marginBottom: 12,
  },
  injuryName: {
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 4,
  },
  injuryDescription: {
    fontSize: 14,
    lineHeight: 20,
  },
  errorText: {
    fontSize: 16,
    textAlign: 'center',
    padding: 40,
  },
});
