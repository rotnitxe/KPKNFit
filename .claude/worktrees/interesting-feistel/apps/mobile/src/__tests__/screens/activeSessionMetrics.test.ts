import { calculateSessionTonnage } from '../../screens/Workout/activeSessionMetrics';
import { OngoingWorkoutState } from '../../types/workout';

describe('calculateSessionTonnage', () => {
  it('returns 0 when there are no completed sets', () => {
    expect(calculateSessionTonnage({})).toBe(0);
  });

  it('sums tonnage for effective single-side sets', () => {
    const completedSets: OngoingWorkoutState['completedSets'] = {
      s1: { weight: 100, reps: 5, performanceMode: 'target' },
      s2: { weight: 80, reps: 8, performanceMode: 'failure' },
    };

    expect(calculateSessionTonnage(completedSets)).toBe(1140);
  });

  it('excludes failed, ineffective and bilateral placeholder entries', () => {
    const completedSets: OngoingWorkoutState['completedSets'] = {
      s1: { weight: 120, reps: 3, performanceMode: 'target' },
      s2: { weight: 120, reps: 3, performanceMode: 'failed' },
      s3: { weight: 60, reps: 10, isIneffective: true },
      s4: { left: { weight: 24, reps: 12 }, right: { weight: 24, reps: 12 } },
    };

    expect(calculateSessionTonnage(completedSets)).toBe(360);
  });
});
