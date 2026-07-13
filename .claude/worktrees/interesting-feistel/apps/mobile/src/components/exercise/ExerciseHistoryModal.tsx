import React, { useMemo, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Modal,
  TouchableOpacity,
  ScrollView,
  Dimensions
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useSharedValue, useAnimatedStyle, withSpring, withTiming, runOnJS } from 'react-native-reanimated';
import { WorkoutLog } from '../../types/workout';
import { useColors } from '../../theme';
import { generateId } from '../../utils/generateId';

const { height: SCREEN_HEIGHT, width: SCREEN_WIDTH } = Dimensions.get('window');

interface ExerciseHistoryModalProps {
  visible: boolean;
  onClose: () => void;
  exerciseId: string;
  exerciseName: string;
  history: WorkoutLog[];
}

const ExerciseHistoryModal: React.FC<ExerciseHistoryModalProps> = ({
  visible,
  onClose,
  exerciseId,
  exerciseName,
  history
}) => {
  const colors = useColors();
  const translateY = useSharedValue(SCREEN_HEIGHT);

  const exerciseHistory = useMemo(() => {
    const searchId = exerciseId;
    const searchName = (exerciseName || '').trim().toLowerCase();
    
    return history
      .map(log => {
        const completedEx = log.completedExercises.find(ce => {
          const ceName = (ce.exerciseName || '').trim().toLowerCase();
          // Check by ID first (if it's a custom exercise)
          if (ce.exerciseId && ce.exerciseId === searchId) return true;
          // Check by DbId (standard exercises)
          if (ce.exerciseDbId === searchId) return true;
          // Check by name if no ID match
          if (ceName && searchName && ceName === searchName) return true;
          return false;
        });
        
        if (completedEx) {
          return {
            date: log.date,
            sessionName: log.sessionName,
            programId: log.programId,
            sets: completedEx.sets,
          };
        }
        return null;
      })
      .filter((log): log is NonNullable<typeof log> => log !== null)
      .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
  }, [history, exerciseId, exerciseName]);

  const chartData = useMemo(() => {
    return [...exerciseHistory]
      .reverse()
      .map(log => {
        const maxWeight = Math.max(...(log.sets.map(s => s.weight || 0)));
        const totalVolume = log.sets.reduce((acc, s) => acc + (s.weight || 0) * (s.completedReps || s.targetReps || 0), 0);
        return {
          date: new Date(log.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
          weight: maxWeight > 0 ? maxWeight : null,
          volume: totalVolume > 0 ? Math.round(totalVolume) : null,
        };
      })
      .filter(d => d.weight !== null || d.volume !== null);
  }, [exerciseHistory]);

  const stats = useMemo(() => {
    const totalSets = exerciseHistory.reduce((acc, log) => acc + log.sets.length, 0);
    const totalVolume = exerciseHistory.reduce((acc, log) => {
      return acc + log.sets.reduce((s, set) => s + (set.weight || 0) * (set.completedReps || set.targetReps || 0), 0);
    }, 0);
    
    // Calculate PR (heaviest weight)
    let pr = 0;
    let lastDate = '';
    exerciseHistory.forEach(log => {
      log.sets.forEach(set => {
        if (set.weight && set.weight > pr) {
          pr = set.weight;
          lastDate = log.date;
        }
      });
    });

    return {
      totalSessions: exerciseHistory.length,
      totalSets,
      totalVolume,
      pr,
      lastDate
    };
  }, [exerciseHistory]);

  React.useEffect(() => {
    if (visible) {
      translateY.value = withSpring(0, { damping: 20, stiffness: 90 });
    } else {
      translateY.value = withTiming(SCREEN_HEIGHT);
    }
  }, [visible]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }]
  }));

  const closeWithAnimation = useCallback(() => {
    translateY.value = withTiming(SCREEN_HEIGHT, {}, () => {
      runOnJS(onClose)();
    });
  }, [onClose]);

  const gesture = Gesture.Pan()
    .onUpdate((event) => {
      if (event.translationY > 0) translateY.value = event.translationY;
    })
    .onEnd((event) => {
      if (event.translationY > 150 || event.velocityY > 500) {
        runOnJS(closeWithAnimation);
      } else {
        translateY.value = withSpring(0);
      }
    });

  if (!visible) return null;

  return (
    <Modal transparent visible={visible} animationType="none">
      <View style={styles.overlay}>
        <Animated.View style={[styles.sheet, animatedStyle]}>
          <View style={styles.handle} />
          
          <View style={styles.header}>
            <View style={styles.headerInfo}>
              <Text style={styles.headerTitle}>Historial</Text>
              <Text style={styles.headerSubtitle} numberOfLines={1}>{exerciseName}</Text>
            </View>
            <TouchableOpacity onPress={closeWithAnimation} style={styles.closeBtn}>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.content}>
            {/* Stats Grid */}
            <View style={styles.statsGrid}>
              <View style={styles.statCard}>
                <Text style={styles.statLabel}>Sesiones</Text>
                <Text style={styles.statValue}>{stats.totalSessions}</Text>
              </View>
              <View style={styles.statCard}>
                <Text style={styles.statLabel}>Series</Text>
                <Text style={styles.statValue}>{stats.totalSets}</Text>
              </View>
              <View style={[styles.statCard, styles.statCardHighlight]}>
                <Text style={[styles.statLabel, { color: colors.primary }]}>PR</Text>
                <Text style={[styles.statValue, { color: colors.primary }]}>
                  {stats.pr > 0 ? `${stats.pr} kg` : '-'}
                </Text>
              </View>
            </View>

            {/* History List */}
            <View style={styles.historySection}>
              <Text style={styles.sectionTitle}>Historial Reciente</Text>
              {exerciseHistory.length === 0 ? (
                <View style={styles.emptyState}>
                  <Text style={styles.emptyStateText}>No hay registros de este ejercicio</Text>
                </View>
              ) : (
                exerciseHistory.slice(0, 10).map((log, index) => (
                  <View key={index} style={styles.logCard}>
                    <View style={styles.logHeader}>
                      <Text style={styles.logDate}>
                        {new Date(log.date).toLocaleDateString('es-ES', { 
                          weekday: 'short', 
                          day: 'numeric', 
                          month: 'short' 
                        })}
                      </Text>
                      <Text style={styles.logSession}>{log.sessionName}</Text>
                    </View>
                    <View style={styles.setsList}>
                      {log.sets.map((set, setIndex) => {
                        if (set.completedReps === undefined && !set.weight) return null;
                        return (
                          <View key={setIndex} style={styles.setItem}>
                            <Text style={styles.setNumber}>{setIndex + 1}</Text>
                            <Text style={styles.setDetails}>
                              {set.weight || '—'} kg × {set.completedReps ?? set.targetReps ?? '—'}
                              {set.completedRPE ? ` @ RPE ${set.completedRPE}` : ''}
                            </Text>
                          </View>
                        );
                      })}
                    </View>
                  </View>
                ))
              )}
            </View>
          </ScrollView>
        </Animated.View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.3)',
    justifyContent: 'flex-end',
  },
  sheet: {
    height: SCREEN_HEIGHT * 0.85,
    backgroundColor: '#FEF7FF',
    borderTopLeftRadius: 36,
    borderTopRightRadius: 36,
    borderTopWidth: 4,
    borderTopColor: '#6750A4',
  },
  handle: {
    width: 44,
    height: 4,
    backgroundColor: '#E2E8F0',
    borderRadius: 2,
    alignSelf: 'center',
    marginVertical: 12,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingBottom: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#E7E0EC',
  },
  headerInfo: {
    flex: 1,
  },
  headerTitle: {
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
    color: '#6750A4',
  },
  headerSubtitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1B1F',
    marginTop: 2,
  },
  closeBtn: {
    padding: 8,
  },
  closeText: {
    fontSize: 18,
    color: '#49454F',
  },
  content: {
    flex: 1,
    padding: 20,
  },
  statsGrid: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 24,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 16,
    padding: 16,
    alignItems: 'center',
  },
  statCardHighlight: {
    backgroundColor: '#F3EDF7',
    borderColor: '#6750A4',
  },
  statLabel: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    color: '#49454F',
    marginBottom: 4,
  },
  statValue: {
    fontSize: 20,
    fontWeight: '900',
    color: '#1C1B1F',
  },
  historySection: {
    marginBottom: 40,
  },
  sectionTitle: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: '#49454F',
    marginBottom: 16,
  },
  emptyState: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 16,
    padding: 24,
    alignItems: 'center',
  },
  emptyStateText: {
    fontSize: 14,
    color: '#49454F',
    fontWeight: '500',
  },
  logCard: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
  },
  logHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F1F5F9',
  },
  logDate: {
    fontSize: 14,
    fontWeight: '700',
    color: '#1C1B1F',
    textTransform: 'capitalize',
  },
  logSession: {
    fontSize: 12,
    fontWeight: '600',
    color: '#64748B',
  },
  setsList: {
    gap: 8,
  },
  setItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  setNumber: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#F1F5F9',
    textAlign: 'center',
    lineHeight: 24,
    fontSize: 11,
    fontWeight: '700',
    color: '#64748B',
  },
  setDetails: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1C1B1F',
  },
});

export default React.memo(ExerciseHistoryModal);
