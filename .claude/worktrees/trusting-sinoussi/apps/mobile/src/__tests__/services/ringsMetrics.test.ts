import { calculateRingsMetrics } from '../../services/ringsMetrics';

describe('ringsMetrics', () => {
  it('averages the first seven raw logs like the PWA rings view', () => {
    const metrics = calculateRingsMetrics({
      sleepLogs: [
        { duration: 8 },
        { duration: 7 },
        { duration: 9 },
        { duration: 6 },
        { duration: 8 },
        { duration: 7 },
        { duration: 9 },
        { duration: 100 },
      ],
      dailyWellbeingLogs: [
        { stressLevel: 1, motivation: 4 },
        { stressLevel: 2, motivation: 5 },
        { stressLevel: 3, motivation: 6 },
        { stressLevel: 4, motivation: 7 },
        { stressLevel: 5, motivation: 8 },
        { stressLevel: 6, motivation: 9 },
        { stressLevel: 7, motivation: 10 },
        { stressLevel: 100, motivation: 100 },
      ],
      waterLogs: [],
      tasks: [],
    });

    expect(metrics.avgSleepHours).toBe(8);
    expect(metrics.avgStressLevel).toBe(4);
    expect(metrics.avgEnergyLevel).toBe(7);
  });

  it('returns nulls when no numeric inputs exist', () => {
    const metrics = calculateRingsMetrics({
      sleepLogs: [],
      dailyWellbeingLogs: [{ stressLevel: 'bad' }],
      waterLogs: [],
      tasks: [],
    });

    expect(metrics.avgSleepHours).toBeNull();
    expect(metrics.avgStressLevel).toBeNull();
    expect(metrics.avgEnergyLevel).toBeNull();
  });
});
