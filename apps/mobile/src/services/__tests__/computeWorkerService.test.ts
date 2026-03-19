jest.mock('../auge', () => ({
  calculateMuscleBattery: jest.fn(() => 11),
  calculateGlobalBatteries: jest.fn(() => ({ overall: 77 })),
  calculateSystemicFatigue: jest.fn(() => ({ stress: 3 })),
  calculateDailyReadiness: jest.fn(() => ({ score: 8 })),
  calculatePredictedSessionDrain: jest.fn(() => ({ cns: 1 })),
  calculateCompletedSessionStress: jest.fn(() => 5),
}));

jest.mock('../analysisService', () => ({
  calculateACWR: jest.fn(() => ({ acwr: 1.2 })),
  calculateAverageVolumeForWeeks: jest.fn(() => [{ muscleGroup: 'Pecho' }]),
  calculateWeeklyTonnageComparison: jest.fn(() => ({ delta: 12 })),
}));

import {
  calculateMuscleBatteryAsync,
  calculateGlobalBatteriesAsync,
  calculateACWRAsync,
  terminateWorker,
} from '../computeWorkerService';

describe('computeWorkerService', () => {
  it('exposes async wrappers compatible with PWA API', async () => {
    await expect((calculateMuscleBatteryAsync as any)({})).resolves.toBe(11);
    await expect((calculateGlobalBatteriesAsync as any)({})).resolves.toEqual({ overall: 77 });
    await expect((calculateACWRAsync as any)([], {}, [])).resolves.toEqual({ acwr: 1.2 });
  });

  it('keeps terminateWorker as safe no-op on RN', () => {
    expect(() => terminateWorker()).not.toThrow();
  });
});
