import type { Session } from '../../types/workout';
import { getSessionExercises, getSessionSetCount, normalizeSessionForExecution } from '../../utils/workoutSession';

describe('workoutSession utils', () => {
  it('returns direct exercises when session already has them', () => {
    const session = {
      id: 'direct',
      name: 'Direct',
      exercises: [{ id: 'ex-1', name: 'Bench', sets: [{ id: 's1' }] }],
      parts: [
        {
          id: 'part-a',
          name: 'A',
          exercises: [{ id: 'ex-part', name: 'Part Ex', sets: [{ id: 'sp' }] }],
        },
      ],
    } as unknown as Session;

    expect(getSessionExercises(session).map(ex => ex.id)).toEqual(['ex-1']);
    expect(getSessionSetCount(session)).toBe(1);
  });

  it('falls back to parts when session.exercises is empty', () => {
    const session = {
      id: 'parts',
      name: 'Parts',
      exercises: [],
      parts: [
        {
          id: 'part-a',
          name: 'A',
          exercises: [{ id: 'ex-a', name: 'Squat', sets: [{ id: 'sa1' }, { id: 'sa2' }] }],
        },
        {
          id: 'part-b',
          name: 'B',
          exercises: [{ id: 'ex-b', name: 'Row', sets: [{ id: 'sb1' }] }],
        },
      ],
    } as unknown as Session;

    expect(getSessionExercises(session).map(ex => ex.id)).toEqual(['ex-a', 'ex-b']);
    expect(getSessionSetCount(session)).toBe(3);
  });

  it('normalizes a parts-only session for execution', () => {
    const session = {
      id: 'normalize',
      name: 'Normalize',
      exercises: [],
      parts: [
        {
          id: 'part-a',
          name: 'A',
          exercises: [{ id: 'ex-a', name: 'Press', sets: [{ id: 'sa1' }] }],
        },
      ],
    } as unknown as Session;

    const normalized = normalizeSessionForExecution(session);
    expect(normalized.exercises.map(ex => ex.id)).toEqual(['ex-a']);
    expect(normalized.parts?.length).toBe(1);
  });
});
