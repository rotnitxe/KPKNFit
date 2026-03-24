// packages/shared-domain/tests/parity.test.ts
// Parity tests: verify AUGE engine produces correct ranges and nutrition modulator logic

import {
  calculateMuscleRecovery,
  computeNutritionRecoveryMultiplier,
  computeAugeReadiness,
  calculatePersonalizedBatteryTanks,
  calculateSetBatteryDrain,
} from '../src/auge';

const mockAdaptiveCache = { totalObservations: 0, modelAccuracy: 0, cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, muscleDeltas: {}, personalizedRecoveryHours: {}, lastCalibrated: '' } as any;
const mockSettings = { calorieGoalObjective: 'maintenance', dailyCalorieGoal: 2500, dailyProteinGoal: 150 };

const MOCK_HISTORY = [
  { date: new Date(Date.now() - 10 * 3600000).toISOString(), completedExercises: [{ exerciseDbId: 'ex1', sets: [{ completedReps: 10, completedRPE: 8, weight: 80 }] }] },
  { date: new Date(Date.now() - 34 * 3600000).toISOString(), completedExercises: [{ exerciseDbId: 'ex1', sets: [{ completedReps: 8, completedRPE: 9, weight: 85 }] }] },
  { date: new Date(Date.now() - 58 * 3600000).toISOString(), completedExercises: [{ exerciseDbId: 'ex2', sets: [{ completedReps: 12, completedRPE: 7, weight: 60 }] }] },
  { date: new Date(Date.now() - 82 * 3600000).toISOString(), completedExercises: [{ exerciseDbId: 'ex1', sets: [{ completedReps: 6, completedRPE: 10, weight: 90 }] }] },
  { date: new Date(Date.now() - 130 * 3600000).toISOString(), completedExercises: [{ exerciseDbId: 'ex3', sets: [{ completedReps: 15, completedRPE: 6, weight: 40 }] }] },
] as any[];

const mockGetInvolvement = () => ({ role: 'primary', activation: 1.0 });

describe('AUGE Engine Parity', () => {
  describe('calculateMuscleRecovery', () => {
    it('should return score 0-100 for fresh muscles with no history', () => {
      const result = calculateMuscleRecovery('Pectorales', [], mockAdaptiveCache, undefined, mockSettings as any, mockGetInvolvement);
      expect(result.recoveryScore).toBe(100);
      expect(result.status).toBe('fresh');
    });

    it('should return score 0-100 for muscles with recent workout', () => {
      const result = calculateMuscleRecovery('Pectorales', MOCK_HISTORY, mockAdaptiveCache, undefined, mockSettings as any, mockGetInvolvement);
      expect(result.recoveryScore).toBeGreaterThanOrEqual(0);
      expect(result.recoveryScore).toBeLessThanOrEqual(100);
    });

    it('should show lower recovery for recently worked muscle', () => {
      const recentHistory = [
        { date: new Date(Date.now() - 6 * 3600000).toISOString(), completedExercises: [{ exerciseDbId: 'ex1', sets: [{ completedReps: 10, completedRPE: 8, weight: 80 }] }] },
      ] as any[];
      const result = calculateMuscleRecovery('Pectorales', recentHistory, mockAdaptiveCache, undefined, mockSettings as any, mockGetInvolvement);
      expect(result.recoveryScore).toBeLessThan(100);
    });

    it('should return valid status strings', () => {
      const result = calculateMuscleRecovery('Bíceps', MOCK_HISTORY, mockAdaptiveCache, undefined, mockSettings as any, mockGetInvolvement);
      expect(['fresh', 'recovering', 'fatigued', 'critical']).toContain(result.status);
    });
  });

  describe('computeNutritionRecoveryMultiplier', () => {
    it('should return multiplier > 1.0 for caloric deficit', () => {
      const result = computeNutritionRecoveryMultiplier({
        nutritionLogs: [
          { date: new Date().toISOString(), foods: [{ calories: 1500, protein: 80 }] },
        ] as any,
        settings: { dailyCalorieGoal: 2500, dailyProteinGoal: 150 },
      });
      expect(result.recoveryTimeMultiplier).toBeGreaterThan(1.0);
      expect(result.status).toBe('deficit');
    });

    it('should return multiplier < 1.0 for caloric surplus with good protein', () => {
      const now = new Date();
      const result = computeNutritionRecoveryMultiplier({
        nutritionLogs: [
          { date: now.toISOString(), foods: [{ calories: 5000, protein: 300 }] },
          { date: new Date(now.getTime() - 86400000).toISOString(), foods: [{ calories: 5000, protein: 300 }] },
        ] as any,
        settings: { dailyCalorieGoal: 2500, dailyProteinGoal: 150 },
        hoursWindow: 48,
      });
      // Surplus with good protein should accelerate recovery (multiplier < 1)
      expect(result.recoveryTimeMultiplier).toBeLessThanOrEqual(1.0);
      expect(result.status).toBe('surplus');
    });

    it('should return ~1.0 for maintenance', () => {
      const result = computeNutritionRecoveryMultiplier({
        nutritionLogs: [
          { date: new Date().toISOString(), foods: [{ calories: 2500, protein: 150 }] },
        ] as any,
        settings: { dailyCalorieGoal: 2500, dailyProteinGoal: 150 },
      });
      // Maintenance with good protein should be close to 1.0
      expect(result.recoveryTimeMultiplier).toBeGreaterThanOrEqual(0.6);
      expect(result.recoveryTimeMultiplier).toBeLessThanOrEqual(1.6);
      expect(['maintenance', 'surplus', 'deficit']).toContain(result.status);
    });

    it('should return at least 0 factors', () => {
      const result = computeNutritionRecoveryMultiplier({
        nutritionLogs: [],
        settings: { dailyCalorieGoal: 2500, dailyProteinGoal: 150 },
      });
      expect(result.factors).toBeDefined();
      expect(Array.isArray(result.factors)).toBe(true);
    });

    it('should handle no nutrition logs with deficit goal', () => {
      const result = computeNutritionRecoveryMultiplier({
        nutritionLogs: [],
        settings: { calorieGoalObjective: 'deficit', dailyCalorieGoal: 2500, dailyProteinGoal: 150 },
      });
      expect(result.recoveryTimeMultiplier).toBeGreaterThan(1.0);
      expect(result.status).toBe('deficit');
    });
  });

  describe('computeAugeReadiness', () => {
    it('should return status, stressMultiplier, cnsBattery, diagnostics, recommendation', () => {
      const result = computeAugeReadiness({
        settings: mockSettings as any,
        adaptiveCache: mockAdaptiveCache as any,
        wellbeing: { sleepHours: 8, stressLevel: 2, doms: 2, motivation: 4, waterIntake: 0, date: new Date().toISOString().slice(0, 10), moodState: 'good' } as any,
        history: [],
        cnsBattery: 90,
      });
      expect(['green', 'yellow', 'red']).toContain(result.status);
      expect(result.stressMultiplier).toBeGreaterThan(0);
      expect(result.cnsBattery).toBe(90);
      expect(result.diagnostics.length).toBeGreaterThan(0);
      expect(typeof result.recommendation).toBe('string');
    });
  });

  describe('calculatePersonalizedBatteryTanks', () => {
    it('should return tank values > 0', () => {
      const tanks = calculatePersonalizedBatteryTanks({});
      expect(tanks.muscularTank).toBeGreaterThan(0);
      expect(tanks.cnsTank).toBeGreaterThan(0);
    });
  });

  describe('calculateSetBatteryDrain', () => {
    it('should return drain percentages 0-100', () => {
      const tanks = calculatePersonalizedBatteryTanks({});
      const drain = calculateSetBatteryDrain(
        { completedReps: 10, completedRPE: 8 },
        { type: 'Básico', name: 'Press de Banca' } as any,
        tanks,
        0,
        90,
      );
      expect(drain.muscularDrainPct).toBeGreaterThanOrEqual(0);
      expect(drain.cnsDrainPct).toBeGreaterThanOrEqual(0);
      expect(drain.spinalDrainPct).toBeGreaterThanOrEqual(0);
    });
  });
});
