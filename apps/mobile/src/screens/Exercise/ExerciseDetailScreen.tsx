import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { useRoute, useNavigation } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { WorkoutStackParamList } from '../../navigation/AppNavigator';
import { ScreenShell } from '../../components/ScreenShell';
import { useExerciseStore } from '../../stores/exerciseStore';
import { MuscleBadgeList } from '../../components/exercise';
import { useColors } from '@/theme';

export function ExerciseDetailScreen() {
  const route = useRoute<RouteProp<WorkoutStackParamList, 'ExerciseDetail'>>();
  const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
  const colors = useColors();
  const getExerciseById = useExerciseStore(state => state.getExerciseById);

  const exerciseId = route.params.exerciseId;
  const exercise = getExerciseById(exerciseId);

  if (!exercise) {
    return (
      <ScreenShell title="Error" subtitle="Ejercicio no encontrado">
        <View style={[styles.errorContainer, { backgroundColor: colors.background }]}>
          <Text style={[styles.errorText, { color: colors.onSurfaceVariant }]}>
            El ejercicio seleccionado no existe en la base de datos o aún no ha sido migrado.
          </Text>
          <TouchableOpacity
            style={[styles.backButton, { backgroundColor: colors.primary }]}
            onPress={() => navigation.goBack()}
          >
            <Text style={styles.backButtonText}>Volver</Text>
          </TouchableOpacity>
        </View>
      </ScreenShell>
    );
  }

  const TechIndicator = ({ label, value }: { label: string; value?: number }) => (
    <View style={styles.techIndicator}>
      <View style={[styles.techCircle, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}>
        <Text style={[styles.techValue, { color: colors.primary }]}>{value ?? '--'}</Text>
      </View>
      <Text style={[styles.techLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
    </View>
  );

  const getTypeBadgeStyles = (type: string) => {
    switch (type) {
      case 'Básico':
        return {
          backgroundColor: `${colors.tertiary}1A`, // 0.1 opacity
          borderColor: `${colors.tertiary}4D`,     // 0.3 opacity
          textColor: colors.tertiary,
        };
      default:
        return {
          backgroundColor: `${colors.primary}1A`,
          borderColor: `${colors.primary}4D`,
          textColor: colors.primary,
        };
    }
  };

  const badgeStyles = getTypeBadgeStyles(exercise.type);

  return (
    <ScreenShell title={exercise.name} subtitle={`${exercise.category} · ${exercise.equipment}`}>
      <ScrollView showsVerticalScrollIndicator={false} style={styles.scrollView}>
        <View style={styles.header}>
          <View style={styles.headerLeft}>
            <View
              style={[
                styles.typeBadge,
                {
                  backgroundColor: badgeStyles.backgroundColor,
                  borderColor: badgeStyles.borderColor,
                },
              ]}
            >
              <Text style={[styles.typeBadgeText, { color: badgeStyles.textColor }]}>
                {exercise.type}
              </Text>
            </View>
            {exercise.alias && (
              <Text style={[styles.aliasText, { color: colors.onSurfaceVariant }]}>
                ({exercise.alias})
              </Text>
            )}
          </View>

          {exercise.isFavorite && (
            <View style={[styles.favoriteBadge, { backgroundColor: `${colors.primary}20` }]}>
              <Text style={[styles.favoriteText, { color: colors.primary }]}>★</Text>
            </View>
          )}
        </View>

        <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Músculos Involucrados</Text>
          <MuscleBadgeList muscles={exercise.involvedMuscles} />
        </View>

        {exercise.description ? (
          <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Descripción / Técnica</Text>
            <Text style={[styles.descriptionText, { color: colors.onSurface }]}>
              {exercise.description}
            </Text>
          </View>
        ) : null}

        <View style={[styles.section, styles.techniqueSection, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
            Perfil de Tensión / Fatiga
          </Text>
          <View style={styles.techRow}>
            <TechIndicator label="SNC" value={exercise.cnc} />
            <TechIndicator label="EFC" value={exercise.efc} />
            <TechIndicator label="SSC" value={exercise.ssc} />
            <TechIndicator label="TTC" value={exercise.ttc} />
          </View>
        </View>

        <TouchableOpacity
          style={[styles.backButtonFull, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}
          onPress={() => navigation.goBack()}
        >
          <Text style={[styles.backButtonTextFull, { color: colors.onSurfaceVariant }]}>
            Volver al catálogo
          </Text>
        </TouchableOpacity>
      </ScrollView>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  errorContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 80,
  },
  errorText: {
    fontSize: 16,
    textAlign: 'center',
    paddingHorizontal: 40,
    marginBottom: 32,
  },
  backButton: {
    paddingHorizontal: 32,
    paddingVertical: 12,
    borderRadius: 9999,
  },
  backButtonText: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 4,
    marginBottom: 24,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  typeBadge: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 9999,
    borderWidth: 1,
  },
  typeBadgeText: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  aliasText: {
    fontSize: 12,
    fontStyle: 'italic',
  },
  favoriteBadge: {
    padding: 8,
    borderRadius: 9999,
  },
  favoriteText: {
    fontSize: 12,
  },
  section: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 20,
    marginBottom: 24,
  },
  techniqueSection: {
    paddingBottom: 24,
    marginBottom: 32,
  },
  sectionTitle: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 2,
    marginBottom: 12,
    textAlign: 'center',
  },
  descriptionText: {
    fontSize: 16,
    lineHeight: 24,
    opacity: 0.8,
  },
  techRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  techIndicator: {
    alignItems: 'center',
    flex: 1,
  },
  techCircle: {
    width: 48,
    height: 48,
    borderRadius: 24,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  techValue: {
    fontSize: 16,
    fontWeight: '700',
  },
  techLabel: {
    fontSize: 9,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
    textAlign: 'center',
  },
  backButtonFull: {
    borderRadius: 16,
    borderWidth: 1,
    paddingVertical: 16,
    alignItems: 'center',
    marginBottom: 40,
  },
  backButtonTextFull: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },
});
