import React, { useMemo } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, FlatList } from 'react-native';
import { useRoute, useNavigation } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { WorkoutStackParamList } from '../../navigation/types';
import { ScreenShell } from '../../components/ScreenShell';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { MuscleBadgeList } from '../../components/exercise';
import { useColors } from '@/theme';

export function ExerciseDetailScreen() {
  const [activeTab, setActiveTab] = React.useState<'info' | 'history' | 'biomechanic' | 'alternatives'>('info');
  const route = useRoute<RouteProp<WorkoutStackParamList, 'ExerciseDetail'>>();
  const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
  const colors = useColors();
  const getExerciseById = useExerciseStore(state => state.getExerciseById);
  const getExerciseHistory = useExerciseStore(state => state.getExerciseHistory);
  const getExercisePRs = useExerciseStore(state => state.getExercisePRs);
  const history = useWorkoutStore(state => state.history);
  const exerciseList = useExerciseStore(state => state.exerciseList);

  const exerciseId = route.params.exerciseId;
  const exercise = getExerciseById(exerciseId);

  const exerciseHistory = useMemo(() => {
    return getExerciseHistory(exerciseId, history);
  }, [exerciseId, history, getExerciseHistory, getExercisePRs]);

  const exercisePRs = useMemo(() => {
    return getExercisePRs(exerciseId, history);
  }, [exerciseId, history, getExercisePRs]);

  const alternatives = useMemo(() => {
    if (!exercise) return [];
    return exerciseList
      .filter(e => e.id !== exercise.id && e.category === exercise.category)
      .slice(0, 6);
  }, [exercise, exerciseList]);

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

  const FatigaBar = ({ label, value, color }: { label: string; value?: number; color: string }) => (
    <View style={styles.fatigaBarContainer}>
      <View style={styles.fatigaBarHeader}>
        <Text style={[styles.fatigaBarLabel, { color: colors.onSurface }]}>{label}</Text>
        <Text style={[styles.fatigaBarValue, { color }]}>{value ?? 0}</Text>
      </View>
      <View style={[styles.fatigaBar, { backgroundColor: colors.surfaceContainer }]}>
        <View style={[styles.fatigaBarFill, { width: `${Math.min((value ?? 0) * 10, 100)}%`, backgroundColor: color }]} />
      </View>
    </View>
  );

  const getTypeBadgeStyles = (type: string) => {
    switch (type) {
      case 'Básico':
        return {
          backgroundColor: `${colors.tertiary}1A`,
          borderColor: `${colors.tertiary}4D`,
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

  const TabBar = () => (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.tabBarScroll} contentContainerStyle={styles.tabBarContainer}>
      {(['INFO', 'HISTORIAL', 'BIOMECÁNICA', 'ALTERNATIVAS'] as const).map(tab => {
        const key = tab.toLowerCase() as typeof activeTab;
        const isActive = activeTab === key;
        return (
          <TouchableOpacity
            key={tab}
            style={[styles.tabItem, isActive && { backgroundColor: colors.primaryContainer }]}
            onPress={() => setActiveTab(key)}
          >
            <Text style={[styles.tabText, isActive && { color: colors.onPrimaryContainer }]}>{tab}</Text>
          </TouchableOpacity>
        );
      })}
    </ScrollView>
  );

  const InfoTab = () => (
    <ScrollView showsVerticalScrollIndicator={false}>
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <View style={[styles.typeBadge, { backgroundColor: badgeStyles.backgroundColor, borderColor: badgeStyles.borderColor }]}>
            <Text style={[styles.typeBadgeText, { color: badgeStyles.textColor }]}>{exercise.type}</Text>
          </View>
          {exercise.alias && <Text style={[styles.aliasText, { color: colors.onSurfaceVariant }]}>({exercise.alias})</Text>}
        </View>
        {exercise.isFavorite && <View style={[styles.favoriteBadge, { backgroundColor: `${colors.primary}20` }]}>
          <Text style={[styles.favoriteText, { color: colors.primary }]}>★</Text>
        </View>}
      </View>

      <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Músculos Involucrados</Text>
        <MuscleBadgeList muscles={exercise.involvedMuscles} />
      </View>

      {exercise.description && (
        <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Descripción / Técnica</Text>
          <Text style={[styles.descriptionText, { color: colors.onSurface }]}>{exercise.description}</Text>
        </View>
      )}

      {exercise.executionCues && exercise.executionCues.length > 0 && (
        <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Notas de Ejecución</Text>
          {exercise.executionCues.map((cue, idx) => (
            <View key={idx} style={styles.cueItem}>
              <Text style={[styles.cueBullet, { color: colors.primary }]}>•</Text>
              <Text style={[styles.cueText, { color: colors.onSurface }]}>{cue}</Text>
            </View>
          ))}
        </View>
      )}

      <View style={[styles.section, styles.techniqueSection, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Perfil de Tensión / Fatiga</Text>
        <View style={styles.techRow}>
          <TechIndicator label="SNC" value={exercise.cnc} />
          <TechIndicator label="EFC" value={exercise.efc} />
          <TechIndicator label="SSC" value={exercise.ssc} />
          <TechIndicator label="TTC" value={exercise.ttc} />
        </View>
        <View style={styles.fatigaBarsContainer}>
          <FatigaBar label="Neurológico" value={exercise.cnc} color={colors.tertiary} />
          <FatigaBar label="Muscular" value={exercise.efc} color={colors.error} />
          <FatigaBar label="Metabólico" value={exercise.ssc} color={colors.primary} />
        </View>
      </View>

      <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Detalles</Text>
        <View style={styles.detailRow}>
          <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Equipo:</Text>
          <Text style={[styles.detailValue, { color: colors.onSurface }]}>{exercise.equipment}</Text>
        </View>
        <View style={styles.detailRow}>
          <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Categoría:</Text>
          <Text style={[styles.detailValue, { color: colors.onSurface }]}>{exercise.category}</Text>
        </View>
        <View style={styles.detailRow}>
          <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Fuerza:</Text>
          <Text style={[styles.detailValue, { color: colors.onSurface }]}>{exercise.force}</Text>
        </View>
        {exercise.tier && (
          <View style={styles.detailRow}>
            <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Tier:</Text>
            <View style={[styles.tierBadge, { backgroundColor: `${colors.primary}20` }]}>
              <Text style={[styles.tierText, { color: colors.primary }]}>{exercise.tier}</Text>
            </View>
          </View>
        )}
      </View>
    </ScrollView>
  );

  const HistoryTab = () => {
    const stats = useMemo(() => {
      const totalSessions = exerciseHistory.length;
      const totalSets = exerciseHistory.reduce((acc, log) => acc + log.sets.length, 0);
      const bestPR = exercisePRs.length > 0 ? exercisePRs[0] : null;
      const lastSession = exerciseHistory[0];
      return { totalSessions, totalSets, bestPR, lastSession };
    }, [exerciseHistory, exercisePRs]);

    return (
      <ScrollView showsVerticalScrollIndicator={false}>
        <View style={[styles.statsGrid, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <View style={[styles.statCard, { backgroundColor: colors.surfaceContainer }]}>
            <Text style={[styles.statValue, { color: colors.primary }]}>{stats.totalSessions}</Text>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Sesiones</Text>
          </View>
          <View style={[styles.statCard, { backgroundColor: colors.surfaceContainer }]}>
            <Text style={[styles.statValue, { color: colors.primary }]}>{stats.totalSets}</Text>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Series</Text>
          </View>
          <View style={[styles.statCard, { backgroundColor: colors.primaryContainer }]}>
            <Text style={[styles.statValue, { color: colors.primary }]}>{stats.bestPR?.weight ?? '-'}</Text>
            <Text style={[styles.statLabel, { color: colors.onPrimaryContainer }]}>PR (kg)</Text>
          </View>
        </View>

        {exercisePRs.length > 0 && (
          <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>PRs Recientes</Text>
            {exercisePRs.slice(0, 5).map((pr, idx) => (
              <View key={idx} style={[styles.prItem, idx === 0 && { backgroundColor: `${colors.primary}10` }]}>
                <Text style={[styles.prWeight, { color: colors.primary }]}>{pr.weight} kg × {pr.reps}</Text>
                <Text style={[styles.prDate, { color: colors.onSurfaceVariant }]}>{pr.date}</Text>
              </View>
            ))}
          </View>
        )}

        <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Historial Reciente</Text>
          {exerciseHistory.length === 0 ? (
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>Sin registros aún</Text>
          ) : (
            exerciseHistory.slice(0, 10).map((log, idx) => (
              <View key={idx} style={styles.historyItem}>
                <View style={styles.historyHeader}>
                  <Text style={[styles.historyDate, { color: colors.onSurface }]}>{log.date}</Text>
                  <Text style={[styles.historySession, { color: colors.onSurfaceVariant }]}>{log.sessionName}</Text>
                </View>
                <View style={styles.historySets}>
                  {log.sets.slice(0, 4).map((set, sIdx) => (
                    <Text key={sIdx} style={[styles.historySetText, { color: colors.onSurfaceVariant }]}>
                      {set.weight ?? 0}kg × {set.completedReps ?? set.targetReps ?? 0}
                    </Text>
                  ))}
                </View>
              </View>
            ))
          )}
        </View>
      </ScrollView>
    );
  };

  const BiomechanicTab = () => (
    <ScrollView showsVerticalScrollIndicator={false}>
      <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Mapa Muscular</Text>
        <View style={styles.muscleMapContainer}>
          {exercise.involvedMuscles.slice(0, 6).map((muscle, idx) => (
            <View key={idx} style={[styles.muscleChip, { backgroundColor: muscle.role === 'primary' ? `${colors.primary}20` : `${colors.surfaceContainer}` }]}>
              <Text style={[styles.muscleChipText, { color: muscle.role === 'primary' ? colors.primary : colors.onSurfaceVariant }]}>
                {muscle.muscle}
              </Text>
            </View>
          ))}
        </View>
      </View>

      <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Cadena Cinética</Text>
        <Text style={[styles.bioText, { color: colors.onSurface }]}>{exercise.chain || 'No especificada'}</Text>
      </View>

      <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Plano de Movimiento</Text>
        <Text style={[styles.bioText, { color: colors.onSurface }]}>{exercise.bodyPart || 'No especificado'}</Text>
      </View>

      {exercise.injuryRisk && (
        <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Score de Riesgo</Text>
          <View style={styles.riskContainer}>
            <View style={[styles.riskBar, { backgroundColor: colors.surfaceContainer }]}>
              <View style={[styles.riskFill, { width: `${exercise.injuryRisk.level * 10}%`, backgroundColor: exercise.injuryRisk.level > 7 ? colors.error : colors.primary }]} />
            </View>
            <Text style={[styles.riskText, { color: colors.onSurface }]}>{exercise.injuryRisk.details}</Text>
          </View>
        </View>
      )}

      {exercise.technicalDifficulty != null && (
        <View style={[styles.section, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Dificultad Técnica</Text>
          <View style={styles.difficultyContainer}>
            {[1, 2, 3, 4, 5].map(i => (
              <View key={i} style={[styles.difficultyDot, { backgroundColor: (exercise.technicalDifficulty ?? 0) >= i ? colors.primary : colors.surfaceContainer }]} />
            ))}
          </View>
        </View>
      )}
    </ScrollView>
  );

  const AlternativesTab = () => (
    <ScrollView showsVerticalScrollIndicator={false}>
      {alternatives.length === 0 ? (
        <View style={styles.tabContentCenter}>
          <Text style={{ color: colors.onSurfaceVariant }}>No hay alternativas disponibles</Text>
        </View>
      ) : (
        <View style={styles.alternativesList}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant, textAlign: 'center' }]}>Ejercicios Similares</Text>
          {alternatives.map((alt, idx) => (
            <TouchableOpacity
              key={alt.id}
              style={[styles.altCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}
              onPress={() => navigation.push('ExerciseDetail', { exerciseId: alt.id })}
            >
              <View style={styles.altHeader}>
                <Text style={[styles.altName, { color: colors.onSurface }]}>{alt.name}</Text>
                <View style={[styles.similarityBadge, { backgroundColor: `${colors.primary}20` }]}>
                  <Text style={[styles.similarityText, { color: colors.primary }]}>~85%</Text>
                </View>
              </View>
              <View style={styles.altDetails}>
                <Text style={[styles.altCategory, { color: colors.onSurfaceVariant }]}>{alt.category} · {alt.equipment}</Text>
              </View>
              <MuscleBadgeList muscles={alt.involvedMuscles} maxItems={4} />
            </TouchableOpacity>
          ))}
        </View>
      )}
    </ScrollView>
  );

  const renderTabContent = () => {
    switch (activeTab) {
      case 'history': return <HistoryTab />;
      case 'biomechanic': return <BiomechanicTab />;
      case 'alternatives': return <AlternativesTab />;
      default: return <InfoTab />;
    }
  };

  return (
    <ScreenShell title={exercise.name} subtitle={`${exercise.category} · ${exercise.equipment}`}>
      <TabBar />
      <View style={styles.tabContent}>{renderTabContent()}</View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  scrollView: { flex: 1 },
  errorContainer: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: 80 },
  errorText: { fontSize: 16, textAlign: 'center', paddingHorizontal: 40, marginBottom: 32 },
  backButton: { paddingHorizontal: 32, paddingVertical: 12, borderRadius: 9999 },
  backButtonText: { fontSize: 10, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 1 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 4, marginBottom: 24 },
  headerLeft: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  typeBadge: { paddingHorizontal: 12, paddingVertical: 4, borderRadius: 9999, borderWidth: 1 },
  typeBadgeText: { fontSize: 10, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 1 },
  aliasText: { fontSize: 12, fontStyle: 'italic' },
  favoriteBadge: { padding: 8, borderRadius: 9999 },
  favoriteText: { fontSize: 12 },
  section: { borderRadius: 16, borderWidth: 1, paddingHorizontal: 16, paddingVertical: 20, marginBottom: 24 },
  techniqueSection: { paddingBottom: 24, marginBottom: 32 },
  sectionTitle: { fontSize: 10, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 2, marginBottom: 12, textAlign: 'center' },
  descriptionText: { fontSize: 16, lineHeight: 24, opacity: 0.8 },
  techRow: { flexDirection: 'row', justifyContent: 'space-between' },
  techIndicator: { alignItems: 'center', flex: 1 },
  techCircle: { width: 48, height: 48, borderRadius: 24, borderWidth: 1, alignItems: 'center', justifyContent: 'center', marginBottom: 4 },
  techValue: { fontSize: 16, fontWeight: '700' },
  techLabel: { fontSize: 9, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 1, textAlign: 'center' },
  fatigaBarsContainer: { marginTop: 20, gap: 12 },
  fatigaBarContainer: { marginBottom: 8 },
  fatigaBarHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  fatigaBarLabel: { fontSize: 12, fontWeight: '600' },
  fatigaBarValue: { fontSize: 12, fontWeight: '700' },
  fatigaBar: { height: 6, borderRadius: 3, overflow: 'hidden' },
  fatigaBarFill: { height: '100%', borderRadius: 3 },
  cueItem: { flexDirection: 'row', marginBottom: 8 },
  cueBullet: { fontSize: 14, marginRight: 8, fontWeight: '700' },
  cueText: { fontSize: 14, flex: 1 },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  detailLabel: { fontSize: 12, fontWeight: '600' },
  detailValue: { fontSize: 12, fontWeight: '500' },
  tierBadge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 9999 },
  tierText: { fontSize: 10, fontWeight: '700' },
  tabBarScroll: { marginVertical: 8 },
  tabBarContainer: { paddingHorizontal: 8 },
  tabItem: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 9999, marginRight: 8, borderWidth: 1, borderColor: '#ccc' },
  tabText: { fontSize: 12, fontWeight: '600', textTransform: 'uppercase' },
  tabContent: { flex: 1 },
  tabContentCenter: { alignItems: 'center', justifyContent: 'center', paddingVertical: 40 },
  statsGrid: { flexDirection: 'row', gap: 12, padding: 16, borderRadius: 16, marginBottom: 24 },
  statCard: { flex: 1, padding: 12, borderRadius: 12, alignItems: 'center' },
  statValue: { fontSize: 24, fontWeight: '700' },
  statLabel: { fontSize: 10, fontWeight: '600', textTransform: 'uppercase', marginTop: 4 },
  prItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 12, borderRadius: 8, marginBottom: 8 },
  prWeight: { fontSize: 14, fontWeight: '700' },
  prDate: { fontSize: 12 },
  historyItem: { paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#eee' },
  historyHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  historyDate: { fontSize: 14, fontWeight: '600' },
  historySession: { fontSize: 12 },
  historySets: { flexDirection: 'row', gap: 12 },
  historySetText: { fontSize: 12 },
  emptyText: { fontSize: 14, textAlign: 'center', paddingVertical: 20 },
  muscleMapContainer: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  muscleChip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 9999 },
  muscleChipText: { fontSize: 11, fontWeight: '600', textTransform: 'uppercase' },
  bioText: { fontSize: 14, textAlign: 'center' },
  riskContainer: { gap: 8 },
  riskBar: { height: 8, borderRadius: 4, overflow: 'hidden' },
  riskFill: { height: '100%', borderRadius: 4 },
  riskText: { fontSize: 12 },
  difficultyContainer: { flexDirection: 'row', gap: 8, justifyContent: 'center' },
  difficultyDot: { width: 16, height: 16, borderRadius: 8 },
  alternativesList: { gap: 16 },
  altCard: { borderRadius: 16, borderWidth: 1, padding: 16 },
  altHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  altName: { fontSize: 16, fontWeight: '600', flex: 1 },
  similarityBadge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 9999 },
  similarityText: { fontSize: 10, fontWeight: '700' },
  altDetails: { marginBottom: 12 },
  altCategory: { fontSize: 12 },
});
