import { updateSessionExercise } from '../../screens/Workout/sessionEditorMutations';

describe('sessionEditorMutations', () => {
  it('updates exercise inside parts when session is partitioned', () => {
    const session = {
      id: 's1',
      name: 'Sesion A',
      focus: '',
      exercises: [],
      parts: [
        {
          id: 'p1',
          name: 'A',
          exercises: [
            { id: 'e1', name: 'Sentadilla', sets: [{ id: 'set1', targetReps: 5, weight: 100 }] },
          ],
        },
      ],
    } as any;

    const updated = updateSessionExercise(session, 'e1', exercise => ({
      ...exercise,
      sets: [...exercise.sets, { id: 'set2', targetReps: 5, weight: 105 }],
    }));

    expect(updated.parts[0].exercises[0].sets).toHaveLength(2);
    expect(updated.exercises).toHaveLength(0);
  });

  it('updates exercise in flat session when there are no parts', () => {
    const session = {
      id: 's2',
      name: 'Sesion B',
      focus: '',
      exercises: [
        { id: 'e2', name: 'Press banca', sets: [{ id: 'set1', targetReps: 8, weight: 80 }] },
      ],
      parts: [],
    } as any;

    const updated = updateSessionExercise(session, 'e2', exercise => ({
      ...exercise,
      sets: [],
    }));

    expect(updated.exercises[0].sets).toHaveLength(0);
  });
});
