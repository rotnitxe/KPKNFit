import React, { useMemo } from 'react';
import { View, ScrollView, Text, Pressable, StyleSheet } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { WIKI_MUSCLES, WIKI_JOINTS, WIKI_TENDONS } from '../../data/wikiData';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { WorkoutStackParamList } from '../../navigation/types';
import { useColors } from '@/theme';

export function WikiJointDetailScreen() {
  const route = useRoute<RouteProp<WorkoutStackParamList, 'WikiJointDetail'>>();
  const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
  const colors = useColors();
  const { jointId } = route.params;

  const joint = useMemo(() => WIKI_JOINTS.find(j => j.id === jointId), [jointId]);

  if (!joint) {
    return (
      <ScreenShell title="Error" subtitle="Articulación no encontrada.">
        <Text style={[styles.errorText, { color: colors.onSurface }]}>
          La articulación solicitada no existe.
        </Text>
      </ScreenShell>
    );
  }

  const muscles = useMemo(() => WIKI_MUSCLES.filter(m => joint.musclesCrossing?.includes(m.id)), [joint]);
  const tendons = useMemo(() => WIKI_TENDONS.filter(t => joint.tendonsRelated?.includes(t.id)), [joint]);

  const RelatedItem = ({ item, onPress }: { item: { id: string; name: string }; onPress: () => void }) => (
    <Pressable
      onPress={onPress}
      style={[styles.relatedItem, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
    >
      <Text style={[styles.relatedItemText, { color: colors.onSurface }]}>{item.name}</Text>
    </Pressable>
  );

  return (
    <ScreenShell title={joint.name} subtitle="Detalle articular">
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={[styles.section, styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Descripción</Text>
          <Text style={[styles.bodyText, { color: colors.onSurface }]}>{joint.description}</Text>
          <View style={styles.metaRow}>
            <Text style={[styles.metaLabel, { color: colors.onSurfaceVariant }]}>Tipo:</Text>
            <Text style={[styles.metaValue, { color: colors.primary }]}>{joint.type}</Text>
          </View>
        </View>

        {muscles.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Músculos que la cruzan
            </Text>
            {muscles.map(m => (
              <RelatedItem
                key={m.id}
                item={m}
                onPress={() => navigation.navigate('WikiMuscleDetail', { muscleId: m.id })}
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

        {joint.protectiveExercises && joint.protectiveExercises.length > 0 && (
          <View
            style={[
              styles.section,
              styles.card,
              { backgroundColor: `${colors.primary}0D`, borderColor: `${colors.primary}33` },
            ]}
          >
            <Text style={[styles.sectionTitle, { color: colors.primary }]}>Ejercicios protectores</Text>
            {joint.protectiveExercises.map((ex, idx) => (
              <Text key={idx} style={[styles.listItem, { color: colors.onSurface }]}>
                • {ex}
              </Text>
            ))}
          </View>
        )}

        {joint.commonInjuries && joint.commonInjuries.length > 0 && (
          <View
            style={[
              styles.section,
              styles.card,
              { backgroundColor: `${colors.error}14`, borderColor: `${colors.error}33` },
            ]}
          >
            <Text style={[styles.sectionTitle, { color: colors.error }]}>Lesiones comunes</Text>
            {joint.commonInjuries.map((injury, idx) => (
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
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 16,
  },
  metaLabel: {
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  metaValue: {
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
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
  listItem: {
    fontSize: 15,
    marginBottom: 8,
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
