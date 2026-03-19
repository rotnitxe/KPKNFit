import React, { useState, useMemo } from 'react';
import { View, FlatList, Text, TouchableOpacity, TextInput, StyleSheet, ScrollView } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { WorkoutStackParamList } from '../../navigation/types';
import { ScreenShell } from '../../components/ScreenShell';
import { ExerciseSummaryCard } from '../../components/exercise/ExerciseSummaryCard';
import { Button } from '../../components/ui';
import { useColors } from '@/theme';
import { useExerciseStore } from '../../stores/exerciseStore';

type NavigationProp = NativeStackNavigationProp<WorkoutStackParamList>;
type ViewMode = 'list' | 'grid';

const CATEGORIES = ['Todos', 'Pecho', 'Espalda', 'Hombros', 'Bíceps', 'Tríceps', 'Piernas', 'Core', 'Glúteos'];
const EQUIPMENT = ['Todos', 'Maquinas', 'Peso Libre', 'Poleas', 'Cable', 'Bodyweight', 'Banda'];
const TYPES = ['Todos', 'Básico', 'Accesorio', 'Aislamiento'];
const FATIGUE_LEVELS = ['Todos', 'Baja', 'Media', 'Alta'];

export function ExerciseDatabaseScreen() {
  const navigation = useNavigation<NavigationProp>();
  const colors = useColors();
  const [query, setQuery] = useState('');
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [showFilters, setShowFilters] = useState(false);
  const [category, setCategory] = useState('Todos');
  const [equipment, setEquipment] = useState('Todos');
  const [type, setType] = useState('Todos');
  const [fatigue, setFatigue] = useState('Todos');

  const exerciseList = useExerciseStore(state => state.exerciseList);
  const status = useExerciseStore(state => state.status);

  const filteredExercises = useMemo(() => {
    let result = exerciseList;
    
    if (query.trim()) {
      const q = query.toLowerCase().trim();
      result = result.filter(e =>
        e.name.toLowerCase().includes(q) ||
        (e.description && e.description.toLowerCase().includes(q)) ||
        e.involvedMuscles.some(m => m.muscle.toLowerCase().includes(q))
      );
    }

    if (category !== 'Todos') {
      result = result.filter(e => e.category === category);
    }

    if (equipment !== 'Todos') {
      result = result.filter(e => e.equipment.toLowerCase().includes(equipment.toLowerCase()));
    }

    if (type !== 'Todos') {
      result = result.filter(e => e.type === type);
    }

    if (fatigue !== 'Todos') {
      const fatigueMap: Record<string, { min: number; max: number }> = {
        'Baja': { min: 0, max: 3 },
        'Media': { min: 4, max: 6 },
        'Alta': { min: 7, max: 10 },
      };
      const range = fatigueMap[fatigue];
      if (range) {
        result = result.filter(e => {
          const efc = e.efc ?? 5;
          return efc >= range.min && efc <= range.max;
        });
      }
    }

    return result;
  }, [query, exerciseList, category, equipment, type, fatigue]);

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
          <Text style={[styles.clearButtonText, { color: colors.primary }]}>Limpiar búsqueda</Text>
        </TouchableOpacity>
      )}
    </View>
  );

  const FilterSection = () => (
    <View style={[styles.filterSection, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
      <Text style={[styles.filterTitle, { color: colors.onSurfaceVariant }]}>Filtros</Text>
      
      <Text style={[styles.filterLabel, { color: colors.onSurface }]}>Categoría</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterChips}>
        {CATEGORIES.map(cat => (
          <TouchableOpacity
            key={cat}
            style={[styles.filterChip, category === cat && { backgroundColor: colors.primaryContainer }]}
            onPress={() => setCategory(cat)}
          >
            <Text style={[styles.filterChipText, category === cat && { color: colors.onPrimaryContainer }]}>{cat}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <Text style={[styles.filterLabel, { color: colors.onSurface }]}>Equipo</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterChips}>
        {EQUIPMENT.map(eq => (
          <TouchableOpacity
            key={eq}
            style={[styles.filterChip, equipment === eq && { backgroundColor: colors.primaryContainer }]}
            onPress={() => setEquipment(eq)}
          >
            <Text style={[styles.filterChipText, equipment === eq && { color: colors.onPrimaryContainer }]}>{eq}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <Text style={[styles.filterLabel, { color: colors.onSurface }]}>Tipo</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterChips}>
        {TYPES.map(t => (
          <TouchableOpacity
            key={t}
            style={[styles.filterChip, type === t && { backgroundColor: colors.primaryContainer }]}
            onPress={() => setType(t)}
          >
            <Text style={[styles.filterChipText, type === t && { color: colors.onPrimaryContainer }]}>{t}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <Text style={[styles.filterLabel, { color: colors.onSurface }]}>Fatiga</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterChips}>
        {FATIGUE_LEVELS.map(f => (
          <TouchableOpacity
            key={f}
            style={[styles.filterChip, fatigue === f && { backgroundColor: colors.primaryContainer }]}
            onPress={() => setFatigue(f)}
          >
            <Text style={[styles.filterChipText, fatigue === f && { color: colors.onPrimaryContainer }]}>{f}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </View>
  );

  return (
    <ScreenShell
      title="Base de ejercicios"
      subtitle="Catálogo rápido para explorar técnica, músculos y selección."
    >
      <View style={styles.container}>
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
          <View style={styles.searchActions}>
            <TouchableOpacity
              style={[styles.filterButton, showFilters && { backgroundColor: colors.primaryContainer }]}
              onPress={() => setShowFilters(!showFilters)}
            >
              <Text style={[styles.filterButtonText, { color: colors.onSurface }]}>☰</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.viewToggle, { backgroundColor: viewMode === 'grid' ? colors.primaryContainer : colors.surface }]}
              onPress={() => setViewMode(viewMode === 'list' ? 'grid' : 'list')}
            >
              <Text style={{ color: colors.onSurface }}>{viewMode === 'list' ? '▦' : '☰'}</Text>
            </TouchableOpacity>
          </View>
        </View>

        {showFilters && <FilterSection />}

        <View style={styles.actionSection}>
          <Button
            onPress={() => navigation.navigate('WikiHome' as never)}
            variant="secondary"
          >
            Ir a WikiLab
          </Button>
        </View>

        <View style={styles.countSection}>
          <Text style={[styles.countText, { color: colors.onSurfaceVariant }]}>
            {filteredExercises.length} Resultados
          </Text>
        </View>

        <FlatList
          data={filteredExercises}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => (
            <ExerciseSummaryCard
              item={item}
              onPress={(id) => navigation.navigate('ExerciseDetail', { exerciseId: id })}
              showFatigue
              showTier
            />
          )}
          ListEmptyComponent={renderEmpty}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.listContent}
          numColumns={viewMode === 'grid' ? 2 : 1}
          key={viewMode}
        />
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  searchSection: { marginBottom: 16, flexDirection: 'row', gap: 8 },
  searchInput: {
    flex: 1,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
  },
  searchActions: { flexDirection: 'row', gap: 8 },
  filterButton: { paddingHorizontal: 12, paddingVertical: 12, borderRadius: 12, borderWidth: 1, borderColor: '#ccc' },
  filterButtonText: { fontSize: 16 },
  viewToggle: { paddingHorizontal: 12, paddingVertical: 12, borderRadius: 12, borderWidth: 1, borderColor: '#ccc' },
  filterSection: { borderRadius: 16, borderWidth: 1, padding: 16, marginBottom: 16 },
  filterTitle: { fontSize: 10, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 2, marginBottom: 16 },
  filterLabel: { fontSize: 12, fontWeight: '600', marginBottom: 8, marginTop: 8 },
  filterChips: { flexDirection: 'row', marginBottom: 8 },
  filterChip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 9999, marginRight: 8, backgroundColor: '#f0f0f0' },
  filterChipText: { fontSize: 12, fontWeight: '500' },
  actionSection: { marginBottom: 24, paddingHorizontal: 4 },
  countSection: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, paddingHorizontal: 4 },
  countText: { fontSize: 10, fontWeight: 'bold', textTransform: 'uppercase', letterSpacing: 2 },
  listContent: { paddingBottom: 40 },
  emptyContainer: { paddingVertical: 80, alignItems: 'center', justifyContent: 'center' },
  emptyText: { fontSize: 16, textAlign: 'center', paddingHorizontal: 40 },
  clearButton: { marginTop: 24, borderWidth: 1, paddingHorizontal: 24, paddingVertical: 8, borderRadius: 999 },
  clearButtonText: { fontWeight: 'bold', textTransform: 'uppercase', letterSpacing: 1.5, fontSize: 12 },
});
