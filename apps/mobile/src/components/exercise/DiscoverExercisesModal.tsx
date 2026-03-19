import React, { useMemo } from 'react';
import { View, Text, Modal, TouchableOpacity, StyleSheet, FlatList, ScrollView } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { WorkoutStackParamList } from '../../navigation/types';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useColors } from '../../theme';

type NavigationProp = NativeStackNavigationProp<WorkoutStackParamList>;

interface DiscoverExercisesModalProps {
  visible: boolean;
  onClose: () => void;
}

const DISCOVER_CATEGORIES = [
  { id: 't1', title: 'Top Tier', icon: '⭐', filter: (e: { tier?: string }) => e.tier === 'T1' },
  { id: 'favorites', title: 'Favoritos', icon: '❤️', filter: (e: { isFavorite?: boolean }) => e.isFavorite },
  { id: 'chest', title: 'Pecho', icon: '💪', filter: (e: { category?: string }) => e.category === 'Pecho' },
  { id: 'back', title: 'Espalda', icon: '🔙', filter: (e: { category?: string }) => e.category === 'Espalda' },
  { id: 'legs', title: 'Piernas', icon: '🦵', filter: (e: { category?: string }) => e.category === 'Piernas' },
  { id: 'shoulders', title: 'Hombros', icon: '🎯', filter: (e: { category?: string }) => e.category === 'Hombros' },
  { id: 'arms', title: 'Brazos', icon: '💪', filter: (e: { category?: string }) => e.category === 'Bíceps' || e.category === 'Tríceps' },
  { id: 'lowfatigue', title: 'Baja Fatiga', icon: '⚡', filter: (e: { efc?: number }) => (e.efc ?? 5) <= 3 },
];

export function DiscoverExercisesModal({ visible, onClose }: DiscoverExercisesModalProps) {
  const navigation = useNavigation<NavigationProp>();
  const colors = useColors();
  const exerciseList = useExerciseStore(state => state.exerciseList);
  const [selectedCategory, setSelectedCategory] = React.useState(DISCOVER_CATEGORIES[0].id);

  const filteredExercises = useMemo(() => {
    const category = DISCOVER_CATEGORIES.find(c => c.id === selectedCategory);
    if (!category) return exerciseList.slice(0, 10);
    return exerciseList.filter(e => category.filter(e)).slice(0, 10);
  }, [selectedCategory, exerciseList]);

  const handleExercisePress = (exerciseId: string) => {
    onClose();
    navigation.navigate('ExerciseDetail', { exerciseId });
  };

  return (
    <Modal visible={visible} animationType="slide" transparent>
      <View style={styles.overlay}>
        <View style={[styles.container, { backgroundColor: colors.background }]}>
          <View style={styles.header}>
            <Text style={[styles.title, { color: colors.onSurface }]}>Descubrir Ejercicios</Text>
            <TouchableOpacity onPress={onClose} style={styles.closeButton}>
              <Text style={[styles.closeText, { color: colors.onSurfaceVariant }]}>✕</Text>
            </TouchableOpacity>
          </View>

          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.categoryScroll} contentContainerStyle={styles.categoryContainer}>
            {DISCOVER_CATEGORIES.map(cat => (
              <TouchableOpacity
                key={cat.id}
                style={[styles.categoryChip, selectedCategory === cat.id && { backgroundColor: colors.primaryContainer }]}
                onPress={() => setSelectedCategory(cat.id)}
              >
                <Text style={styles.categoryIcon}>{cat.icon}</Text>
                <Text style={[styles.categoryText, selectedCategory === cat.id && { color: colors.onPrimaryContainer }]}>{cat.title}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>

          <FlatList
            data={filteredExercises}
            keyExtractor={(item) => item.id}
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.listContent}
            renderItem={({ item }) => (
              <TouchableOpacity
                style={[styles.exerciseCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
                onPress={() => handleExercisePress(item.id)}
              >
                <View style={[styles.cardHeader, { backgroundColor: colors.surfaceContainer }]}>
                  <Text style={styles.cardIcon}>🏋️</Text>
                </View>
                <Text style={[styles.cardName, { color: colors.onSurface }]} numberOfLines={1}>{item.name}</Text>
                <Text style={[styles.cardCategory, { color: colors.onSurfaceVariant }]}>{item.category}</Text>
                {item.tier && (
                  <View style={[styles.tierBadge, { backgroundColor: `${colors.primary}20` }]}>
                    <Text style={[styles.tierText, { color: colors.primary }]}>{item.tier}</Text>
                  </View>
                )}
              </TouchableOpacity>
            )}
            ListEmptyComponent={
              <View style={styles.emptyState}>
                <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>Sin ejercicios en esta categoría</Text>
              </View>
            }
          />
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  container: { borderTopLeftRadius: 24, borderTopRightRadius: 24, paddingBottom: 40, maxHeight: '70%' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 20, borderBottomWidth: 1, borderBottomColor: '#eee' },
  title: { fontSize: 18, fontWeight: '700' },
  closeButton: { padding: 8 },
  closeText: { fontSize: 18 },
  categoryScroll: { maxHeight: 60 },
  categoryContainer: { paddingHorizontal: 16, paddingVertical: 12, gap: 8 },
  categoryChip: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 8, borderRadius: 9999, backgroundColor: '#f0f0f0', marginRight: 8 },
  categoryIcon: { fontSize: 14, marginRight: 6 },
  categoryText: { fontSize: 12, fontWeight: '600' },
  listContent: { paddingHorizontal: 16, paddingVertical: 16 },
  exerciseCard: { width: 140, borderRadius: 16, borderWidth: 1, padding: 12, marginRight: 12 },
  cardHeader: { height: 60, borderRadius: 12, alignItems: 'center', justifyContent: 'center', marginBottom: 12 },
  cardIcon: { fontSize: 24 },
  cardName: { fontSize: 13, fontWeight: '600', marginBottom: 4 },
  cardCategory: { fontSize: 11, marginBottom: 8 },
  tierBadge: { alignSelf: 'flex-start', paddingHorizontal: 8, paddingVertical: 2, borderRadius: 9999 },
  tierText: { fontSize: 9, fontWeight: '700', textTransform: 'uppercase' },
  emptyState: { padding: 40, alignItems: 'center' },
  emptyText: { fontSize: 14 },
});
