import {
  hasExpectedCountData,
  hasExpectedNutritionData,
  hasExpectedWellbeingData,
  isHydratedModuleReady,
} from '../../stores/cutoverReadiness';

describe('cutoverReadiness helpers', () => {
  it('hasExpectedCountData requires data only when snapshot expects it', () => {
    expect(hasExpectedCountData(undefined, 0)).toBe(true);
    expect(hasExpectedCountData(0, 0)).toBe(true);
    expect(hasExpectedCountData(3, 0)).toBe(false);
    expect(hasExpectedCountData(3, 1)).toBe(true);
  });

  it('hasExpectedNutritionData mirrors count readiness behavior', () => {
    expect(hasExpectedNutritionData(2, 0)).toBe(false);
    expect(hasExpectedNutritionData(2, 3)).toBe(true);
  });

  it('hasExpectedWellbeingData only enforces data when snapshot has wellbeing records', () => {
    expect(hasExpectedWellbeingData(undefined, false)).toBe(true);
    expect(hasExpectedWellbeingData({ sleepLogs: 0, waterLogs: 0, tasks: 0 }, false)).toBe(true);
    expect(hasExpectedWellbeingData({ sleepLogs: 1, waterLogs: 0, tasks: 0 }, false)).toBe(false);
    expect(hasExpectedWellbeingData({ sleepLogs: 1, waterLogs: 1, tasks: 1 }, true)).toBe(true);
  });

  it('isHydratedModuleReady enforces non-empty ready state when data is expected', () => {
    expect(isHydratedModuleReady('empty', 0, 0)).toBe(true);
    expect(isHydratedModuleReady('ready', 0, 0)).toBe(true);
    expect(isHydratedModuleReady('ready', 2, 0)).toBe(false);
    expect(isHydratedModuleReady('ready', 2, 2)).toBe(true);
  });
});
