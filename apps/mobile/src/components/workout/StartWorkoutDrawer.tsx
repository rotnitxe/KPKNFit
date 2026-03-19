import React, { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Program, Session } from '../../types/workout';
import { PlayIcon } from '../icons';
import { useColors } from '../../theme';
import { WorkoutDrawer } from './WorkoutDrawer';
import { getSessionExercises, getSessionSetCount } from '../../utils/workoutSession';

type VariantKey = 'A' | 'B' | 'C' | 'D';

interface StartWorkoutPayload {
  key: VariantKey;
  session: Session;
  programId: string;
  programName: string;
}

interface StartWorkoutDrawerProps {
  visible: boolean;
  programs: Program[];
  onClose: () => void;
  onStart: (payload: StartWorkoutPayload) => void;
}

interface SessionPick {
  id: string;
  programId: string;
  programName: string;
  breadcrumb: string;
  session: Session;
}

function getSessionVariants(session: Session): Array<{ key: VariantKey; session: Session }> {
  const variants: Array<{ key: VariantKey; session: Session }> = [{ key: 'A', session }];
  if (session.sessionB) variants.push({ key: 'B', session: session.sessionB });
  if (session.sessionC) variants.push({ key: 'C', session: session.sessionC });
  if (session.sessionD) variants.push({ key: 'D', session: session.sessionD });
  return variants;
}

function flattenProgramSessions(programs: Program[]): SessionPick[] {
  return programs.flatMap(program =>
    (program.macrocycles ?? []).flatMap((macro, macroIndex) =>
      (macro.blocks ?? []).flatMap((block, blockIndex) =>
        (block.mesocycles ?? []).flatMap((meso, mesoIndex) =>
          (meso.weeks ?? []).flatMap(week =>
            (week.sessions ?? []).map(session => ({
              id: `${program.id}:${session.id}`,
              programId: program.id,
              programName: program.name,
              breadcrumb: `Macro ${macroIndex + 1} · Bloque ${blockIndex + 1} · Meso ${mesoIndex + 1} · ${week.name}`,
              session,
            })),
          ),
        ),
      ),
    ),
  );
}

export function StartWorkoutDrawer({ visible, programs, onClose, onStart }: StartWorkoutDrawerProps) {
  const colors = useColors();
  const sessionPicks = useMemo(() => flattenProgramSessions(programs), [programs]);
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [selectedVariant, setSelectedVariant] = useState<VariantKey>('A');

  const selectedPick = useMemo(
    () => sessionPicks.find(pick => pick.id === selectedSessionId) ?? sessionPicks[0] ?? null,
    [selectedSessionId, sessionPicks],
  );

  useEffect(() => {
    if (!visible) {
      setSelectedSessionId(null);
      setSelectedVariant('A');
      return;
    }

    setSelectedSessionId(prev => {
      if (prev && sessionPicks.some(pick => pick.id === prev)) {
        return prev;
      }
      return sessionPicks[0]?.id ?? null;
    });
    setSelectedVariant('A');
  }, [visible, sessionPicks]);

  const variantOptions = useMemo(
    () => (selectedPick ? getSessionVariants(selectedPick.session) : []),
    [selectedPick],
  );

  const selectedVariantPayload = useMemo(
    () => variantOptions.find(option => option.key === selectedVariant) ?? variantOptions[0],
    [selectedVariant, variantOptions],
  );

  const handleStart = () => {
    if (!selectedPick || !selectedVariantPayload) return;
    onStart({
      key: selectedVariantPayload.key,
      session: selectedVariantPayload.session,
      programId: selectedPick.programId,
      programName: selectedPick.programName,
    });
  };

  return (
    <WorkoutDrawer visible={visible} title="Entrenar" onClose={onClose}>
      {sessionPicks.length === 0 ? (
        <View style={[styles.emptyCard, { backgroundColor: colors.surfaceContainer }]}>
          <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>No hay sesiones disponibles</Text>
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
            Crea o activa un programa para iniciar una sesión desde RN.
          </Text>
        </View>
      ) : (
        <>
          <Text style={[styles.blockLabel, { color: colors.onSurfaceVariant }]}>Sesiones</Text>
          <View style={styles.list}>
            {sessionPicks.slice(0, 50).map(pick => {
              const selected = selectedPick?.id === pick.id;
              return (
                <Pressable
                  key={pick.id}
                  onPress={() => setSelectedSessionId(pick.id)}
                  style={[
                    styles.sessionCard,
                    {
                      backgroundColor: selected ? colors.primaryContainer : colors.surfaceContainer,
                      borderColor: selected ? colors.primary : `${colors.onSurface}20`,
                    },
                  ]}
                >
                  <Text style={[styles.sessionName, { color: selected ? colors.onPrimaryContainer : colors.onSurface }]}>
                    {pick.session.name}
                  </Text>
                  <Text style={[styles.sessionMeta, { color: selected ? colors.onPrimaryContainer : colors.onSurfaceVariant }]}>
                    {pick.programName} · {pick.breadcrumb}
                  </Text>
                  <Text style={[styles.sessionMeta, { color: selected ? colors.onPrimaryContainer : colors.onSurfaceVariant }]}>
                    {getSessionExercises(pick.session).length} ejercicios · {getSessionSetCount(pick.session)} sets
                  </Text>
                </Pressable>
              );
            })}
          </View>

          {selectedPick ? (
            <View style={[styles.variantCard, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.onSurface}1A` }]}>
              <Text style={[styles.blockLabel, { color: colors.onSurfaceVariant }]}>Variante</Text>
              <View style={styles.variantRow}>
                {variantOptions.map(option => {
                  const selected = selectedVariantPayload?.key === option.key;
                  return (
                    <Pressable
                      key={option.key}
                      onPress={() => setSelectedVariant(option.key)}
                      style={[
                        styles.variantChip,
                        {
                          backgroundColor: selected ? colors.primary : `${colors.onSurface}0D`,
                        },
                      ]}
                    >
                      <Text style={[styles.variantText, { color: selected ? colors.onPrimary : colors.onSurface }]}>
                        Sesión {option.key}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
            </View>
          ) : null}

          <Pressable
            onPress={handleStart}
            disabled={!selectedPick || !selectedVariantPayload}
            style={[
              styles.startButton,
              {
                backgroundColor: colors.primary,
                opacity: selectedPick && selectedVariantPayload ? 1 : 0.5,
              },
            ]}
          >
            <PlayIcon size={16} color={colors.onPrimary} />
            <Text style={[styles.startButtonText, { color: colors.onPrimary }]}>Iniciar sesión</Text>
          </Pressable>
        </>
      )}
    </WorkoutDrawer>
  );
}

const styles = StyleSheet.create({
  blockLabel: {
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.2,
  },
  list: {
    gap: 8,
  },
  sessionCard: {
    borderWidth: 1,
    borderRadius: 14,
    padding: 12,
    gap: 4,
  },
  sessionName: {
    fontSize: 14,
    fontWeight: '800',
  },
  sessionMeta: {
    fontSize: 12,
  },
  variantCard: {
    marginTop: 6,
    borderWidth: 1,
    borderRadius: 14,
    padding: 12,
    gap: 10,
  },
  variantRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  variantChip: {
    minHeight: 36,
    borderRadius: 10,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  variantText: {
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  startButton: {
    marginTop: 4,
    minHeight: 46,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 8,
  },
  startButtonText: {
    fontSize: 13,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  emptyCard: {
    borderRadius: 14,
    padding: 14,
    gap: 6,
  },
  emptyTitle: {
    fontSize: 15,
    fontWeight: '800',
  },
  emptyText: {
    fontSize: 13,
    lineHeight: 20,
  },
});
