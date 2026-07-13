import React, { useMemo } from 'react';
import { View, ScrollView, Text, Pressable, StyleSheet } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { WIKI_MUSCLES, WIKI_JOINTS, WIKI_TENDONS } from '../../data/wikiData';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { WorkoutStackParamList } from '../../navigation/types';
import { useColors } from '@/theme';

export function WikiTendonDetailScreen() {
  const route = useRoute<RouteProp<WorkoutStackParamList, 'WikiTendonDetail'>>();
  const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
  const colors = useColors();
  const { tendonId } = route.params;

  const tendon = useMemo(() => WIKI_TENDONS.find(t => t.id === tendonId), [tendonId]);

  if (!tendon) {
    return (
      <ScreenShell title="Error" subtitle="Tendón no encontrado.">
        <Text style={[styles.errorText, { color: colors.onSurface }]}>
          El tendón solicitado no existe.
        </Text>
      </ScreenShell>
    );
  }

  const muscle = useMemo(() => WIKI_MUSCLES.find(m => m.id === tendon.muscleId), [tendon]);
  const joint = useMemo(() => WIKI_JOINTS.find(j => j.id === tendon.jointId), [tendon]);

  return (
    <ScreenShell title={tendon.name} subtitle="Detalle de tendón">
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={[styles.section, styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Descripción</Text>
          <Text style={[styles.bodyText, { color: colors.onSurface }]}>{tendon.description}</Text>
        </View>

        {muscle && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Músculo asociado
            </Text>
            <Pressable
              onPress={() => navigation.navigate('WikiMuscleDetail', { muscleId: muscle.id })}
              style={[styles.relatedItem, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
            >
              <Text style={[styles.relatedItemText, { color: colors.onSurface }]}>{muscle.name}</Text>
            </Pressable>
          </View>
        )}

        {joint && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Articulación asociada
            </Text>
            <Pressable
              onPress={() => navigation.navigate('WikiJointDetail', { jointId: joint.id })}
              style={[styles.relatedItem, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
            >
              <Text style={[styles.relatedItemText, { color: colors.onSurface }]}>{joint.name}</Text>
            </Pressable>
          </View>
        )}

        {tendon.protectiveExercises && tendon.protectiveExercises.length > 0 && (
          <View
            style={[
              styles.section,
              styles.card,
              { backgroundColor: `${colors.primary}0D`, borderColor: `${colors.primary}33` },
            ]}
          >
            <Text style={[styles.sectionTitle, { color: colors.primary }]}>Ejercicios protectores</Text>
            {tendon.protectiveExercises.map((ex, idx) => (
              <Text key={idx} style={[styles.listItem, { color: colors.onSurface }]}>
                • {ex}
              </Text>
            ))}
          </View>
        )}

        {tendon.commonInjuries && tendon.commonInjuries.length > 0 && (
          <View
            style={[
              styles.section,
              styles.card,
              { backgroundColor: `${colors.error}14`, borderColor: `${colors.error}33` },
            ]}
          >
            <Text style={[styles.sectionTitle, { color: colors.error }]}>Lesiones comunes</Text>
            {tendon.commonInjuries.map((injury, idx) => (
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
