import { OngoingWorkoutState } from '../../types/workout';

type CompletedSetEntry = OngoingWorkoutState['completedSets'][string];
type BilateralSetEntry = Extract<CompletedSetEntry, { left: unknown; right: unknown }>;

function isBilateralSet(entry: CompletedSetEntry): entry is BilateralSetEntry {
  return Boolean(entry && typeof entry === 'object' && 'left' in entry && 'right' in entry);
}

export function calculateSessionTonnage(completedSets: OngoingWorkoutState['completedSets']): number {
  return Object.values(completedSets).reduce((sum, entry) => {
    if (!entry || typeof entry !== 'object' || isBilateralSet(entry)) return sum;
    if (entry.performanceMode === 'failed' || entry.isIneffective) return sum;
    const reps = entry.reps ?? 0;
    const weight = entry.weight ?? 0;
    return sum + reps * weight;
  }, 0);
}
