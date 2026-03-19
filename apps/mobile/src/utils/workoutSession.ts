import type { Exercise, Session } from '../types/workout';

export function getSessionExercises(session: Session | null | undefined): Exercise[] {
  if (!session) return [];
  const direct = session.exercises ?? [];
  if (direct.length > 0) return direct;
  const parts = session.parts ?? [];
  if (parts.length === 0) return direct;
  return parts.flatMap(part => part.exercises ?? []);
}

export function getSessionSetCount(session: Session | null | undefined): number {
  return getSessionExercises(session).reduce((sum, exercise) => sum + (exercise.sets?.length ?? 0), 0);
}

export function normalizeSessionForExecution(session: Session): Session {
  const direct = session.exercises ?? [];
  if (direct.length > 0) return session;
  const flattened = getSessionExercises(session);
  if (flattened.length === 0) return session;
  return {
    ...session,
    exercises: flattened,
  };
}
