import React, { useState, useMemo } from 'react';
import { View, Text, StyleSheet, Modal, Pressable, TextInput, TouchableOpacity, FlatList, Dimensions } from 'react-native';
import Animated, { useSharedValue, useAnimatedStyle, withSpring, withTiming, runOnJS } from 'react-native-reanimated';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { XCircleIcon, SearchIcon, ActivityIcon } from '../icons';
import { ExerciseMuscleInfo } from '../../types/workout';
import { PWA_WORKOUT_PALETTE as PWA } from './pwaWorkoutPalette';

interface ExerciseSubstitutionSheetProps {
  visible: boolean;
  onClose: () => void;
  oldExerciseId: string;
}

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

export const ExerciseSubstitutionSheet: React.FC<ExerciseSubstitutionSheetProps> = ({
  visible,
  onClose,
  oldExerciseId,
}) => {
  const { exerciseList } = useExerciseStore();
  const substituteExercise = useWorkoutStore(s => s.substituteExercise);

  const [search, setSearch] = useState('');
  const translateY = useSharedValue(SCREEN_HEIGHT);

  React.useEffect(() => {
    if (visible) {
      translateY.value = withSpring(0, { damping: 20, stiffness: 90 });
    } else {
      translateY.value = withTiming(SCREEN_HEIGHT);
    }
  }, [visible, translateY]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));

  const closeWithAnimation = () => {
    translateY.value = withTiming(SCREEN_HEIGHT, {}, () => {
      runOnJS(onClose)();
    });
  };

  const handleSelect = (replacement: ExerciseMuscleInfo) => {
    substituteExercise(oldExerciseId, replacement);
    closeWithAnimation();
  };

  const filteredExercises = useMemo(() => {
    if (!search.trim()) return exerciseList.slice(0, 50);
    const q = search.toLowerCase();
    return exerciseList.filter(ex =>
      ex.name.toLowerCase().includes(q) ||
      ex.involvedMuscles.some((m: any) => m.muscle.toString().toLowerCase().includes(q))
    ).slice(0, 50);
  }, [search, exerciseList]);

  const gesture = Gesture.Pan()
    .onUpdate((event) => {
      if (event.translationY > 0) {
        translateY.value = event.translationY;
      }
    })
    .onEnd((event) => {
      if (event.translationY > 150 || event.velocityY > 500) {
        runOnJS(closeWithAnimation)();
      } else {
        translateY.value = withSpring(0);
      }
    });

  if (!visible) return null;

  return (
    <Modal transparent visible={visible} animationType="none">
      <View style={styles.overlay}>
        <Pressable style={StyleSheet.absoluteFill} onPress={closeWithAnimation} />

        <GestureDetector gesture={gesture}>
          <Animated.View style={[styles.sheet, animatedStyle]}>
            <View style={styles.handle} />

            <View style={styles.content}>
              <View style={styles.header}>
                <Text style={styles.title}>Sustituir ejercicio</Text>
                <TouchableOpacity onPress={closeWithAnimation}>
                  <XCircleIcon size={24} color={PWA.muted} />
                </TouchableOpacity>
              </View>

              <View style={styles.searchBar}>
                <SearchIcon size={20} color={PWA.muted} />
                <TextInput
                  style={styles.searchInput}
                  placeholder="Buscar ejercicio..."
                  placeholderTextColor={PWA.muted}
                  value={search}
                  onChangeText={setSearch}
                />
              </View>

              <FlatList
                data={filteredExercises}
                keyExtractor={(item) => item.id}
                renderItem={({ item }) => (
                  <TouchableOpacity style={styles.item} onPress={() => handleSelect(item)}>
                    <View style={styles.itemInfo}>
                      <Text style={styles.itemName}>{item.name}</Text>
                      <Text style={styles.itemDetails}>{item.type} · {item.equipment}</Text>
                    </View>
                    <ActivityIcon size={20} color={PWA.primary} />
                  </TouchableOpacity>
                )}
                contentContainerStyle={styles.listContent}
                ListEmptyComponent={() => (
                  <View style={styles.empty}>
                    <Text style={styles.emptyText}>No se encontraron ejercicios.</Text>
                  </View>
                )}
              />
            </View>
          </Animated.View>
        </GestureDetector>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(28,27,31,0.28)',
    justifyContent: 'flex-end',
  },
  sheet: {
    height: SCREEN_HEIGHT * 0.85,
    borderTopLeftRadius: 36,
    borderTopRightRadius: 36,
    elevation: 20,
    backgroundColor: PWA.page,
    borderTopWidth: 1,
    borderTopColor: PWA.border,
  },
  handle: {
    width: 40,
    height: 4,
    backgroundColor: '#D1C9DD',
    borderRadius: 2,
    alignSelf: 'center',
    marginTop: 12,
    marginBottom: 8,
  },
  content: {
    flex: 1,
    padding: 24,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    color: PWA.text,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    height: 56,
    borderRadius: 16,
    borderWidth: 1,
    marginBottom: 24,
    gap: 12,
    backgroundColor: '#FFFFFF',
    borderColor: '#E6DCF3',
  },
  searchInput: {
    flex: 1,
    fontSize: 16,
    fontWeight: '500',
    color: PWA.text,
  },
  listContent: {
    paddingBottom: 40,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 16,
    borderBottomWidth: 1,
    justifyContent: 'space-between',
    borderBottomColor: '#EFE7F5',
  },
  itemInfo: {
    flex: 1,
  },
  itemName: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 4,
    color: PWA.text,
  },
  itemDetails: {
    fontSize: 12,
    fontWeight: '500',
    color: PWA.muted,
  },
  empty: {
    padding: 40,
    alignItems: 'center',
  },
  emptyText: {
    color: PWA.muted,
  },
});
