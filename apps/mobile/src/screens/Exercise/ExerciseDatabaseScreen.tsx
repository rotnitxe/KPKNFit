import React, { useState, useMemo } from 'react';
import { View, FlatList, Text, TouchableOpacity, TextInput, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { WorkoutStackParamList } from '../../navigation/types';
import { ScreenShell } from '../../components/ScreenShell';
import { ExerciseSummaryCard } from '../../components/exercise/ExerciseSummaryCard';
import { Button } from '../../components/ui';
import { useColors } from '@/theme';
import type { ExerciseMuscleInfo } from '@/types/workout';
import { useExerciseStore } from '../../stores/exerciseStore';

type NavigationProp = NativeStackNavigationProp<WorkoutStackParamList>;

export function ExerciseDatabaseScreen() {
  const navigation = useNavigation<NavigationProp>();
  const colors = useColors();
  const [query, setQuery] = useState('');
  const exerciseList = useExerciseStore(state => state.exerciseList);
  const status = useExerciseStore(state => state.status);

  const filteredExercises = useMemo(() => {
    if (!query.trim()) {
      return exerciseList;
    }
    const q = query.toLowerCase().trim();
    return exerciseList.filter(e =>
      e.name.toLowerCase().includes(q) ||
      (e.description && e.description.toLowerCase().includes(q)) ||
      e.involvedMuscles.some(m => m.muscle.toLowerCase().includes(q))
    );
  }, [query, exerciseList]);

  const renderEmpty = () => (
    <View style={styles.emptyContainer}>
      <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
        {exerciseList.length === 0
          ? status === 'failed'
            ? 'Error cargando ejercicios'
            : 'Cargando ejercicios...'
          : 'Sin resultados para tu búsqueda.'}
      </Text>
      {query !== '' && (
        <TouchableOpacity
          style={[styles.clearButton, { borderColor: colors.primary }]}
          onPress={() => setQuery('')}
        >
          <Text style={[styles.clearButtonText, { color: colors.primary }]}>
            Limpiar búsqueda
          </Text>
        </TouchableOpacity>
      )}
    </View>
  );

  return (
    <ScreenShell
      title="Base de ejercicios"
      subtitle="Catálogo rápido para explorar técnica, músculos y selección."
    >
      <View style={styles.container}>
        {/* Search Bar */}
        <View style={styles.searchSection}>
          <TextInput
            value={query}
            onChangeText={setQuery}
            placeholder="Buscar ejercicio..."
            placeholderTextColor={`${colors.onSurfaceVariant}66`}
            style={[
              styles.searchInput,
              { 
                backgroundColor: `${colors.onSurface}0D`,
                borderColor: `${colors.onSurface}1A`,
                color: colors.onSurface
              }
            ]}
          />
        </View>

        {/* WikiLab Button */}
        <View style={styles.actionSection}>
          <Button
            onPress={() => navigation.navigate('WikiHome' as never)}
            variant="secondary"
          >
            Ir a WikiLab
          </Button>
        </View>

        {/* Results Count */}
        <View style={styles.countSection}>
          <Text style={[styles.countText, { color: colors.onSurfaceVariant }]}>
            {filteredExercises.length} Resultados
          </Text>
        </View>

        {/* Exercise List */}
        <FlatList
          data={filteredExercises}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => (
            <ExerciseSummaryCard
              item={item}
              onPress={(id) => navigation.navigate('ExerciseDetail', { exerciseId: id })}
            />
          )}
          ListEmptyComponent={renderEmpty}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.listContent}
        />
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  searchSection: {
    marginBottom: 16,
  },
  searchInput: {
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
  },
  actionSection: {
    marginBottom: 24,
    paddingHorizontal: 4,
  },
  countSection: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
    paddingHorizontal: 4,
  },
  countText: {
    fontSize: 10,
    fontWeight: 'bold',
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  listContent: {
    paddingBottom: 40,
  },
  emptyContainer: {
    paddingVertical: 80,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    fontSize: 16,
    textAlign: 'center',
    paddingHorizontal: 40,
  },
  clearButton: {
    marginTop: 24,
    borderWidth: 1,
    paddingHorizontal: 24,
    paddingVertical: 8,
    borderRadius: 999,
  },
  clearButtonText: {
    fontWeight: 'bold',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    fontSize: 12,
  },
});
