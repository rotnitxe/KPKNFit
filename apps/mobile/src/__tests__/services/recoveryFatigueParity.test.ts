import { calculateHistoricalFatigueData } from '../../services/analysisService';
import { calculateDailyReadiness } from '../../services/recoveryService';
import { getLocalDateString } from '../../utils/dateUtils';

describe('recovery and fatigue parity helpers', () => {
  it('aggregates weekly fatigue with persisted sleep and wellbeing logs', () => {
    const today = getLocalDateString();
    const history = [
      {
        id: 'log-1',
        date: `${today}T12:00:00.000Z`,
        sessionName: 'Sesión de prueba',
        completedExercises: [],
      },
    ] as any;

    const result = calculateHistoricalFatigueData(history, {} as any, [], {
      sleepLogs: [
        {
          startTime: `${today}T23:00:00.000Z`,
          endTime: `${today}T07:00:00.000Z`,
          duration: 8,
          quality: 4.5,
        },
      ] as any,
      dailyWellbeingLogs: [
        {
          date: today,
          stressLevel: 3,
        },
      ],
    });

    expect(result).toHaveLength(1);
    expect(result[0].avgSleepHours).toBe(8);
    expect(result[0].avgSleepQuality).toBe(4.5);
    expect(result[0].avgStressLevel).toBe(3);
  });

  it('returns a healthier readiness verdict when sleep and stress are better', () => {
    const healthy = calculateDailyReadiness(
      [
        {
          id: 'sleep-healthy',
          startTime: '2025-03-18T22:00:00.000Z',
          endTime: '2025-03-19T06:00:00.000Z',
          duration: 8,
          quality: 5,
        },
      ] as any,
      [
        {
          date: '2025-03-19',
          stressLevel: 1,
          doms: 1,
        },
      ],
      { calorieGoalObjective: 'maintenance' } as any,
      90,
    );

    const fatigued = calculateDailyReadiness(
      [
        {
          id: 'sleep-fatigued',
          startTime: '2025-03-18T23:00:00.000Z',
          endTime: '2025-03-19T04:00:00.000Z',
          duration: 5,
          quality: 2,
        },
      ] as any,
      [
        {
          date: '2025-03-19',
          stressLevel: 5,
          doms: 5,
        },
      ],
      { calorieGoalObjective: 'maintenance' } as any,
      25,
    );

    expect(healthy.status).toBe('green');
    expect(fatigued.status).toBe('red');
    expect(healthy.stressMultiplier).toBeLessThan(fatigued.stressMultiplier);
  });
});
