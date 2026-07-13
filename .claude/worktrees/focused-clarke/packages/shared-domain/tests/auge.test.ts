import { 
    calculateMuscleRecovery, 
    computeAugeReadiness,
    calculatePersonalizedBatteryTanks,
    calculateSetBatteryDrain
} from '../src/auge';

describe('AUGE Engine Logic', () => {
    const mockSettings = {
        athleteType: 'bodybuilder',
        athleteScore: { profileLevel: 'Advanced', trainingStyle: 'Bodybuilder' },
        calorieGoalObjective: 'maintenance',
        algorithmSettings: { augeEnableNutritionTracking: false }
    };

    const mockAdaptiveCache = {
        cnsDelta: 0,
        muscularDelta: 0,
        spinalDelta: 0,
        lastCalibrated: '2024-01-01',
        personalizedRecoveryHours: {}
    };

    describe('calculateMuscleRecovery', () => {
        it('should return 100% recovery for no history', () => {
            const result = calculateMuscleRecovery(
                'Bíceps',
                [],
                mockAdaptiveCache,
                undefined,
                mockSettings,
                () => null,
                () => 1.0
            );
            expect(result.recoveryScore).toBe(100);
            expect(result.status).toBe('fresh');
        });

        it('should decrease recovery score after a recent workout', () => {
            const lastSessionDate = new Date(Date.now() - 12 * 3600 * 1000).toISOString(); // 12h ago
            const history = [{
                date: lastSessionDate,
                completedExercises: [{
                    exerciseName: 'Curls',
                    sets: [{ completedReps: 10, completedRPE: 9 }]
                }]
            }];

            const result = calculateMuscleRecovery(
                'Bíceps',
                history as any,
                mockAdaptiveCache,
                undefined,
                mockSettings,
                () => ({ role: 'primary', activation: 1.0 }),
                () => 1.0
            );

            expect(result.recoveryScore).toBeLessThan(100);
            expect(result.status).toBe('recovering');
        });
    });

    describe('computeAugeReadiness', () => {
        it('should return green status for good wellbeing and high battery', () => {
            const config = {
                settings: mockSettings,
                adaptiveCache: mockAdaptiveCache,
                wellbeing: {
                    date: '2024-01-01',
                    sleepHours: 8,
                    sleepQuality: 4,
                    stressLevel: 2,
                    nutritionStatus: 'maintenance' as any,
                    hydration: 'good' as any
                },
                history: [],
                cnsBattery: 90
            };

            const result = computeAugeReadiness(config);
            expect(result.status).toBe('green');
            expect(result.stressMultiplier).toBe(1.0);
        });

        it('should return red status for low battery and poor sleep', () => {
            const config = {
                settings: mockSettings,
                adaptiveCache: mockAdaptiveCache,
                wellbeing: {
                    date: '2024-01-01',
                    sleepHours: 5,
                    sleepQuality: 2,
                    stressLevel: 5,
                    nutritionStatus: 'maintenance' as any,
                    hydration: 'poor' as any
                },
                history: [],
                cnsBattery: 35
            };

            const result = computeAugeReadiness(config);
            expect(result.status).toBe('red');
            expect(result.stressMultiplier).toBeGreaterThan(1.5);
        });
    });

    describe('Fatigue Calculations', () => {
        it('should calculate correct battery tanks based on style', () => {
            const tanks = calculatePersonalizedBatteryTanks(mockSettings);
            expect(tanks.muscularTank).toBeGreaterThan(0);
            expect(tanks.cnsTank).toBeGreaterThan(0);
        });

        it('should calculate non-zero drain for effective sets', () => {
            const tanks = calculatePersonalizedBatteryTanks(mockSettings);
            const set = { completedReps: 10, completedRPE: 8 };
            const drain = calculateSetBatteryDrain(set, { type: 'Básico' }, tanks);
            
            expect(drain.muscularDrainPct).toBeGreaterThan(0);
            expect(drain.cnsDrainPct).toBeGreaterThan(0);
        });
    });
});
