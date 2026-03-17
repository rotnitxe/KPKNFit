import { create } from 'zustand';
import {
  loadPersistedDomainPayload,
  persistDomainPayload,
} from '../services/mobilePersistenceService';
import { setJsonValue } from '../storage/mmkv';
import type { Program, ActiveProgramState, SplitTemplate } from '../types/workout';
import { generateId } from '../utils/generateId';

interface ProgramMigrationPayload {
  programs?: Program[];
  activeProgramState?: ActiveProgramState | null;
}

interface ProgramStoreState {
  status: 'idle' | 'ready' | 'failed';
  programs: Program[];
  activeProgramState: ActiveProgramState | null;
  hasHydrated: boolean;
  errorMessage: string | null;
  hydrateFromMigration: () => Promise<void>;
  removeProgram: (programId: string) => Promise<void>;
  createDraftProgram: () => Promise<Program>;
  activateProgram: (programId: string) => Promise<void>;
  pauseProgram: () => Promise<void>;
  updateProgram: (program: Program) => Promise<void>;

  // Deep Edit Helpers
  updateSession: (programId: string, weekId: string, session: any) => Promise<void>;
  addExerciseToSession: (programId: string, weekId: string, sessionId: string, exercise: any) => Promise<void>;
  removeExerciseFromSession: (programId: string, weekId: string, sessionId: string, exerciseId: string) => Promise<void>;
  updateExerciseInSession: (programId: string, weekId: string, sessionId: string, exercise: any) => Promise<void>;
  addSession: (programId: string, weekId: string) => Promise<void>;
  deleteSession: (programId: string, weekId: string, sessionId: string) => Promise<void>;
  addProgram: (program: Program) => Promise<void>;
  updateProgramSplit: (programId: string, split: SplitTemplate, startDay: number, scope: 'week' | 'block' | 'program', targetBlockId?: string, targetWeekId?: string) => Promise<void>;

  // Drag-and-Drop Session Management
  moveSessionToDay: (programId: string, sessionId: string, fromDay: number, toDay: number) => Promise<void>;
  swapSessions: (programId: string, session1Id: string, session2Id: string) => Promise<void>;
  duplicateSession: (programId: string, weekId: string, sessionId: string) => Promise<void>;
}

async function persistProgramsSnapshot(programs: Program[], activeProgramState: ActiveProgramState | null) {
  await persistDomainPayload('programs', {
    programs,
    activeProgramState,
  });

  setJsonValue('rn.programs', programs);
  setJsonValue('rn.activeProgramState', activeProgramState);
}

function buildDraftProgram(existingCount: number): Program {
  const createSet = (targetReps: number, weight: number, rir = 2) => ({
    id: generateId(),
    targetReps,
    weight,
    targetRIR: rir,
  });
  const createExercise = (
    name: string,
    setCount: number,
    targetReps: number,
    weight: number,
    restTime = 120,
  ) => ({
    id: generateId(),
    name,
    sets: Array.from({ length: setCount }, () => createSet(targetReps, weight)),
    restTime,
    trainingMode: 'reps' as const,
  });
  const macrocycleId = generateId();
  const blockId = generateId();
  const mesocycleId = generateId();
  const weekId = generateId();

  return {
    id: generateId(),
    name: `Programa ${existingCount + 1}`,
    description: 'Nuevo programa creado desde la app móvil.',
    mode: 'hypertrophy',
    structure: 'complex',
    author: 'KPKN Mobile',
    isDraft: true,
    macrocycles: [
      {
        id: macrocycleId,
        name: 'Macrociclo 1',
        blocks: [
          {
            id: blockId,
            name: 'Bloque 1',
            mesocycles: [
              {
                id: mesocycleId,
                name: 'Mesociclo 1',
                goal: 'Acumulación',
                weeks: [
                  {
                    id: weekId,
                    name: 'Semana 1',
                    sessions: [
                      {
                        id: generateId(),
                        name: 'Upper 1',
                        focus: 'Pecho · Espalda · Hombros',
                        dayOfWeek: 1,
                        exercises: [
                          createExercise('Press banca', 4, 6, 60),
                          createExercise('Remo con barra', 4, 8, 50),
                          createExercise('Press militar', 3, 10, 30, 90),
                        ],
                      },
                      {
                        id: generateId(),
                        name: 'Lower 1',
                        focus: 'Pierna · Glúteo · Core',
                        dayOfWeek: 3,
                        exercises: [
                          createExercise('Sentadilla trasera', 4, 5, 80),
                          createExercise('Peso muerto rumano', 3, 8, 70),
                          createExercise('Prensa', 3, 12, 140, 90),
                        ],
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  };
}

export const useProgramStore = create<ProgramStoreState>((set) => ({
  status: 'idle',
  programs: [],
  activeProgramState: null,
  hasHydrated: false,
  errorMessage: null,
  hydrateFromMigration: async () => {
    try {
      const payload = await loadPersistedDomainPayload<ProgramMigrationPayload>('programs');
      if (!payload) {
        set({ status: 'ready', hasHydrated: true, programs: [], activeProgramState: null });
        return;
      }

      const programs: Program[] = Array.isArray(payload.programs) ? payload.programs : [];
      const activeProgramState: ActiveProgramState | null = payload.activeProgramState ?? null;

      // Persist to MMKV for quick access and compatibility
      setJsonValue('rn.programs', programs);
      setJsonValue('rn.activeProgramState', activeProgramState);

      set({
        status: 'ready',
        hasHydrated: true,
        programs,
        activeProgramState,
        errorMessage: null,
      });
    } catch (error) {
      set({
        status: 'failed',
        hasHydrated: true,
        errorMessage: error instanceof Error ? error.message : 'No pudimos cargar los programas.',
      });
    }
  },
  removeProgram: async (programId: string) => {
    let nextPrograms: Program[] = [];
    let nextActiveState: ActiveProgramState | null = null;

    set(state => {
      nextPrograms = state.programs.filter(program => program.id !== programId);
      nextActiveState =
        state.activeProgramState?.programId === programId ? null : state.activeProgramState;

      return {
        programs: nextPrograms,
        activeProgramState: nextActiveState,
        errorMessage: null,
      };
    });

    try {
      await persistProgramsSnapshot(nextPrograms, nextActiveState);
      set({ status: 'ready' });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'No pudimos eliminar el programa.',
      });
    }
  },
  createDraftProgram: async () => {
    let createdProgram: Program | null = null;
    let nextPrograms: Program[] = [];
    let nextActiveState: ActiveProgramState | null = null;

    set(state => {
      createdProgram = buildDraftProgram(state.programs.length);
      nextPrograms = [createdProgram, ...state.programs];
      nextActiveState = state.activeProgramState;

      return {
        programs: nextPrograms,
        errorMessage: null,
      };
    });

    try {
      await persistProgramsSnapshot(nextPrograms, nextActiveState);
      set({ status: 'ready' });
      if (!createdProgram) {
        throw new Error('No se pudo construir el programa borrador.');
      }
      return createdProgram;
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'No pudimos crear el programa.',
      });
      throw error;
    }
  },
  activateProgram: async (programId: string) => {
    let nextPrograms: Program[] = [];
    let nextActiveState: ActiveProgramState | null = null;

    set(state => {
      nextPrograms = state.programs;
      const program = state.programs.find(item => item.id === programId) ?? null;
      const firstBlock = program?.macrocycles?.[0]?.blocks?.[0] ?? null;
      const firstMesocycle = firstBlock?.mesocycles?.[0] ?? null;
      const firstWeek = firstMesocycle?.weeks?.[0] ?? null;

      if (!program || !firstWeek) {
        return {
          errorMessage: 'El programa no tiene semanas configuradas para activarlo.',
        };
      }

      nextActiveState = {
        programId,
        status: 'active',
        startDate: new Date().toISOString().slice(0, 10),
        firstSessionDate: new Date().toISOString().slice(0, 10),
        currentMacrocycleIndex: 0,
        currentBlockIndex: 0,
        currentMesocycleIndex: 0,
        currentWeekId: firstWeek.id,
      };

      return {
        activeProgramState: nextActiveState,
        errorMessage: null,
      };
    });

    if (!nextActiveState) {
      return;
    }

    try {
      await persistProgramsSnapshot(nextPrograms, nextActiveState);
      set({ status: 'ready' });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'No pudimos activar el programa.',
      });
    }
  },
  pauseProgram: async () => {
    let nextPrograms: Program[] = [];
    let nextActiveState: ActiveProgramState | null = null;

    set(state => {
      nextPrograms = state.programs;
      nextActiveState = state.activeProgramState
        ? {
            ...state.activeProgramState,
            status: state.activeProgramState.status === 'paused' ? 'active' : 'paused',
          }
        : null;

      return {
        activeProgramState: nextActiveState,
        errorMessage: null,
      };
    });

    try {
      await persistProgramsSnapshot(nextPrograms, nextActiveState);
      set({ status: 'ready' });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'No pudimos actualizar el estado del programa.',
      });
    }
  },

  updateProgram: async (program: Program) => {
    let nextPrograms: Program[] = [];
    set(state => {
      nextPrograms = state.programs.map(p => (p.id === program.id ? program : p));
      return { programs: nextPrograms, errorMessage: null };
    });
    try {
      const activeState = useProgramStore.getState().activeProgramState;
      await persistProgramsSnapshot(nextPrograms, activeState);
      set({ status: 'ready' });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'No pudimos guardar los cambios del programa.',
      });
    }
  },

  updateSession: async (programId: string, weekId: string, session: any) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = { ...program };
    // Búsqueda profunda para actualizar la sesión exacta
    nextProgram.macrocycles = nextProgram.macrocycles.map(m => ({
      ...m,
      blocks: m.blocks?.map(b => ({
        ...b,
        mesocycles: b.mesocycles.map(meso => ({
          ...meso,
          weeks: meso.weeks.map(w => {
            if (w.id !== weekId) return w;
            return {
              ...w,
              sessions: w.sessions.map(s => (s.id === session.id ? session : s)),
            };
          }),
        })),
      })),
    }));

    await useProgramStore.getState().updateProgram(nextProgram);
  },

  addExerciseToSession: async (programId: string, weekId: string, sessionId: string, exercise: any) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = { ...program };
    nextProgram.macrocycles = nextProgram.macrocycles.map(m => ({
      ...m,
      blocks: m.blocks?.map(b => ({
        ...b,
        mesocycles: b.mesocycles.map(meso => ({
          ...meso,
          weeks: meso.weeks.map(w => {
            if (w.id !== weekId) return w;
            return {
              ...w,
              sessions: w.sessions.map(s => {
                if (s.id !== sessionId) return s;
                return { ...s, exercises: [...s.exercises, { ...exercise, id: generateId() }] };
              }),
            };
          }),
        })),
      })),
    }));

    await useProgramStore.getState().updateProgram(nextProgram);
  },

  removeExerciseFromSession: async (programId: string, weekId: string, sessionId: string, exerciseId: string) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = { ...program };
    nextProgram.macrocycles = nextProgram.macrocycles.map(m => ({
      ...m,
      blocks: m.blocks?.map(b => ({
        ...b,
        mesocycles: b.mesocycles.map(meso => ({
          ...meso,
          weeks: meso.weeks.map(w => {
            if (w.id !== weekId) return w;
            return {
              ...w,
              sessions: w.sessions.map(s => {
                if (s.id !== sessionId) return s;
                return { ...s, exercises: s.exercises.filter(ex => ex.id !== exerciseId) };
              }),
            };
          }),
        })),
      })),
    }));

    await useProgramStore.getState().updateProgram(nextProgram);
  },

  updateExerciseInSession: async (programId: string, weekId: string, sessionId: string, exercise: any) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = { ...program };
    nextProgram.macrocycles = nextProgram.macrocycles.map(m => ({
      ...m,
      blocks: m.blocks?.map(b => ({
        ...b,
        mesocycles: b.mesocycles.map(meso => ({
          ...meso,
          weeks: meso.weeks.map(w => {
            if (w.id !== weekId) return w;
            return {
              ...w,
              sessions: w.sessions.map(s => {
                if (s.id !== sessionId) return s;
                return { ...s, exercises: s.exercises.map(ex => (ex.id === exercise.id ? exercise : ex)) };
              }),
            };
          }),
        })),
      })),
    }));

    await useProgramStore.getState().updateProgram(nextProgram);
  },

  moveSessionToDay: async (programId: string, sessionId: string, fromDay: number, toDay: number) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = { ...program };
    let sessionMoved = false;

    // Recorrer estructura profunda para encontrar y mover la sesión
    nextProgram.macrocycles = nextProgram.macrocycles.map(m => ({
      ...m,
      blocks: m.blocks?.map(b => ({
        ...b,
        mesocycles: b.mesocycles.map(meso => ({
          ...meso,
          weeks: meso.weeks.map(w => {
            const matchingSession = w.sessions.find(s => s.id === sessionId);
            if (matchingSession) {
              sessionMoved = true;
              // Actualizar día de la semana
              return {
                ...w,
                sessions: w.sessions.map(s => 
                  s.id === sessionId ? { ...s, dayOfWeek: toDay } : s
                ),
              };
            }
            return w;
          }),
        })),
      })),
    }));

    if (sessionMoved) {
      await useProgramStore.getState().updateProgram(nextProgram);
    }
  },

  swapSessions: async (programId: string, session1Id: string, session2Id: string) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = { ...program };
    let session1Day: number | undefined;
    let session2Day: number | undefined;

    // Primero, encontrar los días de ambas sesiones
    nextProgram.macrocycles.forEach(m => {
      m.blocks?.forEach(b => {
        b.mesocycles.forEach(meso => {
          meso.weeks.forEach(w => {
            const s1 = w.sessions.find(s => s.id === session1Id);
            const s2 = w.sessions.find(s => s.id === session2Id);
            if (s1) session1Day = s1.dayOfWeek;
            if (s2) session2Day = s2.dayOfWeek;
          });
        });
      });
    });

    // Si ambas sesiones están en la misma semana, intercambiar días
    if (session1Day !== undefined && session2Day !== undefined) {
      nextProgram.macrocycles = nextProgram.macrocycles.map(m => ({
        ...m,
        blocks: m.blocks?.map(b => ({
          ...b,
          mesocycles: b.mesocycles.map(meso => ({
            ...meso,
            weeks: meso.weeks.map(w => {
              return {
                ...w,
                sessions: w.sessions.map(s => {
                  if (s.id === session1Id) return { ...s, dayOfWeek: session2Day };
                  if (s.id === session2Id) return { ...s, dayOfWeek: session1Day };
                  return s;
                }),
              };
            }),
          })),
        })),
      }));

      await useProgramStore.getState().updateProgram(nextProgram);
    }
  },

  duplicateSession: async (programId: string, weekId: string, sessionId: string) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = { ...program };
    
    nextProgram.macrocycles = nextProgram.macrocycles.map(m => ({
      ...m,
      blocks: m.blocks?.map(b => ({
        ...b,
        mesocycles: b.mesocycles.map(meso => ({
          ...meso,
          weeks: meso.weeks.map(w => {
            if (w.id !== weekId) return w;
            
            const originalSession = w.sessions.find(s => s.id === sessionId);
            if (!originalSession) return w;

            // Crear copia con nuevo ID pero misma estructura
            const duplicatedSession = {
              ...originalSession,
              id: generateId(),
              name: `${originalSession.name} (Copia)`,
              // Clonar ejercicios con nuevos IDs para evitar conflictos
              exercises: originalSession.exercises.map(ex => ({
                ...ex,
                id: generateId(),
                sets: ex.sets.map(s => ({ ...s, id: generateId() })),
              })),
            };

            return {
              ...w,
              sessions: [...w.sessions, duplicatedSession],
            };
          }),
        })),
      })),
    }));

    await useProgramStore.getState().updateProgram(nextProgram);
  },

  addSession: async (programId: string, weekId: string) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = { ...program };
    const newSession = {
      id: generateId(),
      name: 'Nueva Sesión',
      focus: 'Foco de la sesión',
      exercises: [],
      dayOfWeek: 0,
    };

    nextProgram.macrocycles = nextProgram.macrocycles.map(m => ({
      ...m,
      blocks: m.blocks?.map(b => ({
        ...b,
        mesocycles: b.mesocycles.map(meso => ({
          ...meso,
          weeks: meso.weeks.map(w => {
            if (w.id !== weekId) return w;
            return {
              ...w,
              sessions: [...w.sessions, newSession],
            };
          }),
        })),
      })),
    }));

    await useProgramStore.getState().updateProgram(nextProgram);
  },

  deleteSession: async (programId: string, weekId: string, sessionId: string) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = JSON.parse(JSON.stringify(program));
    nextProgram.macrocycles.forEach((m: any) => {
      m.blocks?.forEach((b: any) => {
        b.mesocycles.forEach((meso: any) => {
          const week = meso.weeks.find((w: any) => w.id === weekId);
          if (week) {
            week.sessions = week.sessions.filter((s: any) => s.id !== sessionId);
          }
        });
      });
    });

    await useProgramStore.getState().updateProgram(nextProgram);
  },

  addProgram: async (program: Program) => {
    let nextPrograms: Program[] = [];
    set(state => {
      nextPrograms = [program, ...state.programs];
      return { programs: nextPrograms, errorMessage: null };
    });
    try {
      const activeState = useProgramStore.getState().activeProgramState;
      await persistProgramsSnapshot(nextPrograms, activeState);
      set({ status: 'ready' });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'No pudimos guardar el nuevo programa.',
      });
    }
  },

  updateProgramSplit: async (programId: string, split: SplitTemplate, startDay: number, scope: 'week' | 'block' | 'program', targetBlockId?: string, targetWeekId?: string) => {
    const state = useProgramStore.getState();
    const program = state.programs.find(p => p.id === programId);
    if (!program) return;

    const nextProgram = JSON.parse(JSON.stringify(program));
    nextProgram.startDay = startDay;
    nextProgram.selectedSplitId = split.id;

    const regenerateWeekSessions = (week: any) => {
      const newSessions: any[] = [];
      split.pattern.forEach((label: string, dayIndex: number) => {
        if (label && label.toLowerCase() !== 'descanso' && label.trim() !== '') {
          const assignedDay = (startDay + dayIndex) % 7;
          // Intentar preservar ejercicios si la sesión tiene el mismo nombre
          const existingSession = week.sessions.find((s: any) => s.name.toLowerCase() === label.toLowerCase());
          
          if (existingSession) {
            newSessions.push({ ...existingSession, dayOfWeek: assignedDay });
          } else {
            newSessions.push({
              id: generateId(),
              name: label,
              description: '',
              exercises: [],
              warmup: [],
              dayOfWeek: assignedDay
            });
          }
        }
      });
      week.sessions = newSessions;
    };

    nextProgram.macrocycles.forEach((macro: any) => {
      macro.blocks?.forEach((block: any) => {
        const isTargetBlock = !targetBlockId || block.id === targetBlockId;
        if (!isTargetBlock && scope === 'block') return;

        block.mesocycles.forEach((meso: any) => {
          meso.weeks.forEach((week: any) => {
            if (scope === 'week' && week.id !== targetWeekId) return;
            regenerateWeekSessions(week);
          });
        });
      });
    });

    await useProgramStore.getState().updateProgram(nextProgram);
  },
}));
