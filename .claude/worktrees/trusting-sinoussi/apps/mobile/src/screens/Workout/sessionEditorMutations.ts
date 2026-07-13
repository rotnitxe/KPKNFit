import type { Exercise, Session } from '../../types/workout';

type ExerciseMapper = (exercise: Exercise) => Exercise;

export function updateSessionExercise(
  session: Session,
  exerciseId: string,
  mapper: ExerciseMapper,
): Session {
  if (session.parts && session.parts.length > 0) {
    return {
      ...session,
      parts: session.parts.map(part => ({
        ...part,
        exercises: part.exercises.map(exercise => (
          exercise.id === exerciseId ? mapper(exercise) : exercise
        )),
      })),
    };
  }

  return {
    ...session,
    exercises: session.exercises.map(exercise => (
      exercise.id === exerciseId ? mapper(exercise) : exercise
    )),
  };
}
