import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useNavigation } from '@react-navigation/native';
import { ScreenShell } from '../../components/ScreenShell';
import { useProgramStore } from '../../stores/programStore';
import type { Program } from '../../types/workout';
import type { WorkoutStackParamList } from '../../navigation/types';
import { useColors } from '../../theme';

type WorkoutNavProp = NativeStackNavigationProp<WorkoutStackParamList>;

interface ProgramCardProps {
  program: Program;
  stats: { weeks: number; sessions: number };
  isActive: boolean;
  onPress: () => void;
  onDelete: () => void;
}

function ProgramListCard({ program, stats, isActive, onPress, onDelete }: ProgramCardProps) {
  const colors = useColors();

  return (
    <View
      style={[
        styles.programRowWrap,
        {
          shadowColor: isActive ? '#2563EB' : '#000000',
        },
      ]}
    >
      <Pressable
        onPress={onPress}
        style={({ pressed }) => [
          styles.programCard,
          {
            backgroundColor: '#FFFFFF',
            borderColor: isActive ? '#2563EB' : '#E7E0EC',
            borderWidth: isActive ? 2 : 1,
            opacity: pressed ? 0.96 : 1,
          },
        ]}
      >
        <View style={styles.programMainRow}>
          <View
            style={[
              styles.programIcon,
              {
                backgroundColor: isActive ? '#2563EB' : '#ECE6F0',
              },
            ]}
          >
            <Text
              style={[
                styles.programIconGlyph,
                { color: isActive ? '#FFFFFF' : colors.onSurfaceVariant },
              ]}
            >
              {isActive ? '▶' : '≡'}
            </Text>
          </View>

          <View style={styles.programTextBlock}>
            {isActive ? (
              <View style={styles.activeBadgeRow}>
                <View style={styles.activeDot} />
                <Text style={styles.activeBadgeLabel}>Ejecutando ahora</Text>
              </View>
            ) : null}

            <Text numberOfLines={1} style={styles.programName}>
              {program.name}
            </Text>
            <Text numberOfLines={1} style={styles.programMeta}>
              {stats.weeks} semanas · {stats.sessions} sesiones
            </Text>
          </View>

          <View
            style={[
              styles.programActionBubble,
              { backgroundColor: isActive ? '#EEF2FF' : '#F5F1FA' },
            ]}
          >
            <Text style={[styles.programActionGlyph, { color: '#2563EB' }]}>
              {isActive ? '▶' : '›'}
            </Text>
          </View>
        </View>
      </Pressable>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Eliminar ${program.name}`}
        onPress={onDelete}
        style={({ pressed }) => [
          styles.deletePill,
          { opacity: pressed ? 0.85 : 1 },
        ]}
      >
        <Text style={styles.deletePillText}>Eliminar</Text>
      </Pressable>
    </View>
  );
}

function EmptyProgramsState({ onCreate }: { onCreate: () => void }) {
  return (
    <View style={styles.emptyState}>
      <View style={styles.emptyIconWrap}>
        <Text style={styles.emptyIconGlyph}>🏋</Text>
      </View>
      <Text style={styles.emptyTitle}>Comienza hoy</Text>
      <Text style={styles.emptySubtitle}>Aún no tienes programas configurados</Text>
      <Pressable onPress={onCreate} style={styles.emptyPrimaryButton}>
        <Text style={styles.emptyPrimaryButtonText}>Crear primer programa</Text>
      </Pressable>
    </View>
  );
}

export function ProgramsScreen() {
  const navigation = useNavigation<WorkoutNavProp>();
  const colors = useColors();
  const [isCreating, setIsCreating] = useState(false);

  const status = useProgramStore(state => state.status);
  const errorMessage = useProgramStore(state => state.errorMessage);
  const programs = useProgramStore(state => state.programs);
  const activeProgramState = useProgramStore(state => state.activeProgramState);
  const hydrateFromMigration = useProgramStore(state => state.hydrateFromMigration);
  const removeProgram = useProgramStore(state => state.removeProgram);
  const createDraftProgram = useProgramStore(state => state.createDraftProgram);

  useEffect(() => {
    if (status === 'idle') {
      void hydrateFromMigration();
    }
  }, [status, hydrateFromMigration]);

  const getProgramStats = useCallback((program: Program) => {
    const allWeeks = program.macrocycles.flatMap(macro =>
      (macro.blocks || []).flatMap(block => block.mesocycles.flatMap(meso => meso.weeks)),
    );
    const totalSessions = allWeeks.reduce((acc, week) => acc + week.sessions.length, 0);
    return { weeks: allWeeks.length, sessions: totalSessions };
  }, []);

  const activeProgramId = activeProgramState?.status === 'active' ? activeProgramState.programId : null;
  const orderedPrograms = useMemo(() => {
    const active = programs.find(program => program.id === activeProgramId) ?? null;
    const rest = programs.filter(program => program.id !== activeProgramId);
    return {
      active,
      inactive: rest,
    };
  }, [activeProgramId, programs]);

  const handleCreate = useCallback(async () => {
    try {
      setIsCreating(true);
      const next = await createDraftProgram();
      navigation.navigate('ProgramWizard', { mode: 'create', programId: next.id });
    } catch (error) {
      Alert.alert(
        'No pudimos crear el programa',
        error instanceof Error ? error.message : 'Intenta de nuevo.',
      );
    } finally {
      setIsCreating(false);
    }
  }, [createDraftProgram, navigation]);

  const handleDelete = useCallback((program: Program) => {
    Alert.alert(
      'Eliminar programa',
      `¿Quieres eliminar "${program.name}"?`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Eliminar',
          style: 'destructive',
          onPress: () => {
            void removeProgram(program.id);
          },
        },
      ],
    );
  }, [removeProgram]);

  const openProgram = useCallback((programId: string) => {
    navigation.navigate('ProgramDetail', { programId });
  }, [navigation]);

  const headerContent = (
    <View style={styles.headerShell}>
      <View style={styles.headerTextBlock}>
        <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Programas</Text>
        <Text style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>
          Gestiona tus planes de entrenamiento
        </Text>
      </View>
      <Pressable
        onPress={handleCreate}
        disabled={isCreating}
        style={({ pressed }) => [
          styles.headerAction,
          { opacity: pressed || isCreating ? 0.9 : 1 },
        ]}
      >
        <Text style={styles.headerActionText}>{isCreating ? 'Creando…' : '＋ Nuevo'}</Text>
      </Pressable>
    </View>
  );

  return (
    <ScreenShell
      title="Programas"
      subtitle="Gestiona tus planes de entrenamiento"
      showBack={false}
      headerContent={headerContent}
      contentContainerStyle={styles.shellContent}
    >
      {status !== 'ready' ? (
        <View style={[styles.infoCard, { borderColor: '#E7E0EC' }]}>
          <Text style={styles.infoEyebrow}>Estado</Text>
          <Text style={[styles.infoTitle, { color: colors.onSurface }]}>
            {status === 'failed' ? 'No pudimos cargar tus programas' : 'Preparando tus programas'}
          </Text>
          <Text style={[styles.infoDescription, { color: colors.onSurfaceVariant }]}>
            {status === 'failed'
              ? errorMessage ?? 'Revisa la migración o vuelve a abrir la app.'
              : 'Estamos trayendo tu biblioteca de entrenamiento a la vista nativa.'}
          </Text>
        </View>
      ) : programs.length === 0 ? (
        <EmptyProgramsState onCreate={handleCreate} />
      ) : (
        <View style={styles.listSection}>
          {orderedPrograms.active ? (
            <ProgramListCard
              program={orderedPrograms.active}
              stats={getProgramStats(orderedPrograms.active)}
              isActive
              onPress={() => openProgram(orderedPrograms.active!.id)}
              onDelete={() => handleDelete(orderedPrograms.active!)}
            />
          ) : null}

          {orderedPrograms.inactive.map(program => (
            <ProgramListCard
              key={program.id}
              program={program}
              stats={getProgramStats(program)}
              isActive={false}
              onPress={() => openProgram(program.id)}
              onDelete={() => handleDelete(program)}
            />
          ))}
        </View>
      )}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  shellContent: {
    paddingHorizontal: 16,
    paddingBottom: 120,
  },
  headerShell: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 16,
  },
  headerTextBlock: {
    flex: 1,
    gap: 4,
  },
  headerTitle: {
    fontSize: 30,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: -0.5,
  },
  headerSubtitle: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  headerAction: {
    minHeight: 48,
    paddingHorizontal: 18,
    borderRadius: 20,
    backgroundColor: '#2563EB',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#2563EB',
    shadowOpacity: 0.24,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 10 },
    elevation: 6,
  },
  headerActionText: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
    color: '#FFFFFF',
  },
  infoCard: {
    borderRadius: 34,
    borderWidth: 1,
    backgroundColor: 'rgba(255,255,255,0.92)',
    paddingHorizontal: 20,
    paddingVertical: 22,
  },
  infoEyebrow: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
    color: '#2563EB',
  },
  infoTitle: {
    marginTop: 10,
    fontSize: 24,
    fontWeight: '900',
  },
  infoDescription: {
    marginTop: 8,
    fontSize: 14,
    lineHeight: 22,
  },
  listSection: {
    gap: 12,
  },
  programRowWrap: {
    marginBottom: 8,
    shadowOpacity: 0.08,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 10 },
    elevation: 4,
  },
  programCard: {
    borderRadius: 36,
    paddingHorizontal: 20,
    paddingVertical: 20,
  },
  programMainRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  programIcon: {
    width: 58,
    height: 58,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  programIconGlyph: {
    fontSize: 24,
    fontWeight: '900',
  },
  programTextBlock: {
    flex: 1,
  },
  activeBadgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 6,
  },
  activeDot: {
    width: 7,
    height: 7,
    borderRadius: 999,
    backgroundColor: '#10B981',
  },
  activeBadgeLabel: {
    fontSize: 9,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
    color: '#2563EB',
  },
  programName: {
    fontSize: 20,
    fontWeight: '900',
    color: '#18181B',
    textTransform: 'uppercase',
    letterSpacing: -0.3,
  },
  programMeta: {
    marginTop: 4,
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.6,
    color: '#5F5B66',
  },
  programActionBubble: {
    width: 46,
    height: 46,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  programActionGlyph: {
    fontSize: 22,
    fontWeight: '900',
  },
  deletePill: {
    alignSelf: 'flex-end',
    marginTop: 8,
    marginRight: 12,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999,
    backgroundColor: 'rgba(179,38,30,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(179,38,30,0.25)',
  },
  deletePillText: {
    fontSize: 11,
    fontWeight: '800',
    color: '#8C1D18',
  },
  emptyState: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 56,
    paddingBottom: 40,
    paddingHorizontal: 24,
  },
  emptyIconWrap: {
    width: 88,
    height: 88,
    borderRadius: 32,
    backgroundColor: '#E8F0FE',
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyIconGlyph: {
    fontSize: 42,
  },
  emptyTitle: {
    marginTop: 28,
    fontSize: 24,
    fontWeight: '900',
    textTransform: 'uppercase',
    color: '#18181B',
  },
  emptySubtitle: {
    marginTop: 8,
    fontSize: 11,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
    color: '#5F5B66',
    textAlign: 'center',
  },
  emptyPrimaryButton: {
    marginTop: 28,
    width: '100%',
    maxWidth: 280,
    minHeight: 58,
    borderRadius: 26,
    backgroundColor: '#18181B',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 18,
  },
  emptyPrimaryButtonText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2.4,
  },
});

export default ProgramsScreen;
