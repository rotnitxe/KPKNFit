import type { Program, ActiveProgramState } from '../types/workout';
import { getWeekId } from '../utils/calculations';
import { appStorage } from '../storage/mmkv';

const E2E_FLAG_KEY = 'kpkn-e2e-bypass';
const E2E_PROGRAM_ID = 'e2e-seed-program';
const E2E_SESSION_ID = 'e2e-seed-session';
const E2E_EXERCISE_ID = 'e2e-seed-exercise';

export function getE2ESeedProgram(): Program {
  const today = new Date();
  const startWeekOn = 1;
  const weekId = getWeekId(today, startWeekOn);
  const dayOfWeek = today.getDay();

  return {
    id: E2E_PROGRAM_ID,
    name: 'E2E Programa de Prueba',
    mode: 'powerbuilding',
    structure: 'simple',
    macrocycles: [
      {
        id: 'e2e-macro-1',
        name: 'Macro 1',
        blocks: [
          {
            id: 'e2e-block-1',
            name: 'Bloque 1',
            mesocycles: [
              {
                id: 'e2e-meso-1',
                name: 'Meso 1',
                goal: 'Acumulación',
                weeks: [
                  {
                    id: weekId,
                    name: 'Semana 1',
                    sessions: [
                      {
                        id: E2E_SESSION_ID,
                        name: 'Sesión Full Body',
                        dayOfWeek,
                        exercises: [
                          {
                            id: E2E_EXERCISE_ID,
                            name: 'Press de Banca',
                            sets: [
                              {
                                id: 'e2e-set-1',
                                targetReps: 8,
                                targetRPE: 8,
                                intensityMode: 'rpe',
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
          },
        ],
      },
    ],
  };
}

export function getE2ESeedActiveProgramState(): ActiveProgramState {
  const today = new Date();
  const startWeekOn = 1;
  const weekId = getWeekId(today, startWeekOn);
  return {
    programId: E2E_PROGRAM_ID,
    status: 'active',
    startDate: today.toISOString().slice(0, 10),
    currentMacrocycleIndex: 0,
    currentBlockIndex: 0,
    currentMesocycleIndex: 0,
    currentWeekId: weekId,
  };
}

export function setE2EBypass(enabled: boolean) {
  if (enabled) {
    appStorage.set(E2E_FLAG_KEY, '1');
  } else {
    appStorage.delete(E2E_FLAG_KEY);
  }
}

export function shouldSeedE2E(): boolean {
  return appStorage.getString(E2E_FLAG_KEY) === '1';
}
