type OptionalCount = number | undefined;

export interface WellbeingExpectedCounts {
  sleepLogs?: number;
  waterLogs?: number;
  tasks?: number;
}

export function hasExpectedCountData(expectedCount: OptionalCount, currentCount: number): boolean {
  return (expectedCount ?? 0) === 0 || currentCount > 0;
}

export function hasExpectedNutritionData(expectedNutritionLogs: OptionalCount, currentCount: number): boolean {
  return hasExpectedCountData(expectedNutritionLogs, currentCount);
}

export function hasExpectedWellbeingData(expected: WellbeingExpectedCounts | null | undefined, hasAnyData: boolean): boolean {
  const expectedSleep = expected?.sleepLogs ?? 0;
  const expectedWater = expected?.waterLogs ?? 0;
  const expectedTasks = expected?.tasks ?? 0;
  const expectsAnyData = expectedSleep > 0 || expectedWater > 0 || expectedTasks > 0;
  return !expectsAnyData || hasAnyData;
}

export function isHydratedModuleReady(
  status: 'ready' | 'empty' | string,
  expectedCount: OptionalCount,
  currentCount: number,
): boolean {
  if (expectedCount && expectedCount > 0) {
    return status === 'ready' && currentCount > 0;
  }
  return status === 'ready' || status === 'empty';
}
