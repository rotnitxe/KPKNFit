import type { Session } from '../../types/workout';

jest.mock('../../storage/mmkv', () => ({
  appStorage: { delete: jest.fn() },
  getJsonValue: jest.fn(),
  setJsonValue: jest.fn(),
}));

import { getJsonValue } from '../../storage/mmkv';
import {
  getActiveSessionSummary,
  recoverActiveSession,
} from '../../services/activeSessionPersistenceService';

describe('activeSessionPersistenceService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (getJsonValue as jest.Mock).mockReturnValue(null);
  });

  it('restores a parts-only checkpoint into a flat execution session', async () => {
    const checkpoint = {
      programId: 'prog-1',
      session: {
        id: 'session-parts',
        name: 'Parts Session',
        exercises: [],
        parts: [
          {
            id: 'part-a',
            name: 'Parte A',
            exercises: [
              {
                id: 'ex-a',
                name: 'Squat',
                sets: [{ id: 'set-a1', targetReps: 5 }],
              },
            ],
          },
          {
            id: 'part-b',
            name: 'Parte B',
            exercises: [
              {
                id: 'ex-b',
                name: 'Row',
                sets: [{ id: 'set-b1', targetReps: 8 }],
              },
            ],
          },
        ],
      } as unknown as Session,
      startTime: Date.now() - 5 * 60 * 1000,
      activeExerciseId: null,
      activeSetId: null,
      completedSets: {},
      dynamicWeights: {},
      sessionAdjusted1RMs: {},
      selectedBrands: {},
      setTypeOverrides: {},
      checkpointTime: Date.now(),
      isPaused: false,
    };

    (getJsonValue as jest.Mock).mockReturnValue(checkpoint);

    const recovered = await recoverActiveSession();

    expect(recovered?.session.exercises.map(ex => ex.id)).toEqual(['ex-a', 'ex-b']);
    expect(recovered?.activeExerciseId).toBe('ex-a');
    expect(recovered?.activeSetId).toBe('set-a1');
  });

  it('counts flattened sets when summarizing a checkpoint', async () => {
    const checkpoint = {
      programId: 'prog-1',
      session: {
        id: 'session-parts',
        name: 'Parts Session',
        exercises: [],
        parts: [
          {
            id: 'part-a',
            name: 'Parte A',
            exercises: [
              {
                id: 'ex-a',
                name: 'Squat',
                sets: [{ id: 'set-a1' }, { id: 'set-a2' }],
              },
            ],
          },
          {
            id: 'part-b',
            name: 'Parte B',
            exercises: [
              {
                id: 'ex-b',
                name: 'Row',
                sets: [{ id: 'set-b1' }],
              },
            ],
          },
        ],
      } as unknown as Session,
      startTime: Date.now() - 12 * 60 * 1000,
      activeExerciseId: null,
      activeSetId: null,
      completedSets: {
        'set-a1': { weight: 100 },
        'set-b1': { weight: 80 },
      },
      dynamicWeights: {},
      sessionAdjusted1RMs: {},
      selectedBrands: {},
      setTypeOverrides: {},
      checkpointTime: Date.now(),
      isPaused: false,
    };

    (getJsonValue as jest.Mock).mockReturnValue(checkpoint);

    const summary = await getActiveSessionSummary();

    expect(summary?.sessionName).toBe('Parts Session');
    expect(summary?.completedSets).toBe(2);
    expect(summary?.totalSets).toBe(3);
    expect(summary?.elapsedMinutes).toBeGreaterThanOrEqual(0);
  });
});
