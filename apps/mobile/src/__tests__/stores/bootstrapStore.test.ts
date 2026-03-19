import { useBootstrapStore } from '../../stores/bootstrapStore';
import { importBridgeSnapshotIfNeeded, type MigrationImportSummary } from '../../services/migrationImportService';
import { hydrateFromMigrationSnapshot, type HydrationResult } from '../../services/migrationHydrationService';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { useMealTemplateStore } from '../../stores/mealTemplateStore';
import { useProgramStore } from '../../stores/programStore';
import { useBodyStore } from '../../stores/bodyStore';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useLocalAiDiagnosticsStore } from '../../stores/localAiDiagnosticsStore';
import { useMealPlannerStore } from '../../stores/mealPlannerStore';
import { useCoachStore } from '../../stores/coachStore';
import { useAugeRuntimeStore } from '../../stores/augeRuntimeStore';

jest.mock('../../services/migrationImportService', () => ({
  importBridgeSnapshotIfNeeded: jest.fn(),
}));

jest.mock('../../services/migrationHydrationService', () => ({
  hydrateFromMigrationSnapshot: jest.fn(),
}));

jest.mock('../../storage/mmkv', () => ({
  appStorage: { getString: jest.fn(), set: jest.fn(), delete: jest.fn() },
  setJsonValue: jest.fn(),
  getJsonValue: jest.fn(() => null),
}));

// Mock all store hydration methods
const mockNutritionHydrate = jest.fn().mockResolvedValue(undefined);
const mockWorkoutHydrate = jest.fn().mockResolvedValue(undefined);
const mockSettingsHydrate = jest.fn().mockResolvedValue(undefined);
const mockWellbeingHydrate = jest.fn().mockResolvedValue(undefined);
const mockMealTemplateHydrate = jest.fn().mockResolvedValue(undefined);
const mockProgramHydrate = jest.fn().mockResolvedValue(undefined);
const mockBodyHydrate = jest.fn().mockResolvedValue(undefined);
const mockExerciseHydrate = jest.fn().mockResolvedValue(undefined);
const mockMealPlannerHydrate = jest.fn().mockResolvedValue(undefined);
const mockCoachHydrate = jest.fn().mockResolvedValue(undefined);
const mockAugeRuntimeHydrate = jest.fn().mockResolvedValue(undefined);
const mockDiagnosticsRefresh = jest.fn().mockResolvedValue(undefined);

function resetAllStoreHydrationMocks() {
  mockNutritionHydrate.mockResolvedValue(undefined);
  mockWorkoutHydrate.mockResolvedValue(undefined);
  mockSettingsHydrate.mockResolvedValue(undefined);
  mockWellbeingHydrate.mockResolvedValue(undefined);
  mockMealTemplateHydrate.mockResolvedValue(undefined);
  mockProgramHydrate.mockResolvedValue(undefined);
  mockBodyHydrate.mockResolvedValue(undefined);
  mockExerciseHydrate.mockResolvedValue(undefined);
  mockMealPlannerHydrate.mockResolvedValue(undefined);
  mockCoachHydrate.mockResolvedValue(undefined);
  mockAugeRuntimeHydrate.mockResolvedValue(undefined);
  mockDiagnosticsRefresh.mockResolvedValue(undefined);

  useMobileNutritionStore.setState({ hydrateFromStorage: mockNutritionHydrate } as any);
  useWorkoutStore.setState({ hydrateFromMigration: mockWorkoutHydrate } as any);
  useSettingsStore.setState({ hydrateFromMigration: mockSettingsHydrate } as any);
  useWellbeingStore.setState({ hydrateFromMigration: mockWellbeingHydrate } as any);
  useMealTemplateStore.setState({ hydrateFromMigration: mockMealTemplateHydrate } as any);
  useProgramStore.setState({ hydrateFromMigration: mockProgramHydrate } as any);
  useBodyStore.setState({ hydrateFromMigration: mockBodyHydrate } as any);
  useExerciseStore.setState({ hydrateFromMigration: mockExerciseHydrate } as any);
  useMealPlannerStore.setState({ hydrateFromStorage: mockMealPlannerHydrate } as any);
  useCoachStore.setState({ hydrateFromStorage: mockCoachHydrate } as any);
  useAugeRuntimeStore.setState({ hydrateFromStorage: mockAugeRuntimeHydrate } as any);
  useLocalAiDiagnosticsStore.setState({ refreshStatus: mockDiagnosticsRefresh } as any);
}

const CLEAN_SUMMARY: MigrationImportSummary = {
  source: 'empty',
  recordCounts: null,
};

const SNAPSHOT_SUMMARY: MigrationImportSummary = {
  source: 'snapshot',
  recordCounts: {
    programs: 2,
    workoutHistory: 5,
    nutritionLogs: 10,
    pantryItems: 3,
    mealTemplates: 1,
    sleepLogs: 7,
    waterLogs: 14,
    tasks: 4,
  },
};

const HYDRATION_OK: HydrationResult = {
  nutritionLogsImported: 10,
  nutritionLogsDiscarded: 0,
  settingsImported: true,
  wellbeingImported: true,
  bodyImported: true,
  exerciseImported: true,
  programsImported: true,
  workoutStatus: 'available-in-overview',
  errors: [],
};

describe('bootstrapStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useBootstrapStore.setState({
      status: 'booting',
      summary: null,
      hydrationResult: null,
      error: null,
    });
    resetAllStoreHydrationMocks();
  });

  it('should have correct initial state', () => {
    const state = useBootstrapStore.getState();
    expect(state.status).toBe('booting');
    expect(state.summary).toBeNull();
    expect(state.hydrationResult).toBeNull();
    expect(state.error).toBeNull();
  });

  it('should complete bootstrap with empty source (no snapshot hydration)', async () => {
    (importBridgeSnapshotIfNeeded as jest.Mock).mockResolvedValue(CLEAN_SUMMARY);

    await useBootstrapStore.getState().bootstrap();

    const state = useBootstrapStore.getState();
    expect(state.status).toBe('ready');
    expect(state.summary).toEqual(CLEAN_SUMMARY);
    expect(state.hydrationResult).toBeNull();
    expect(state.error).toBeNull();

    // hydrateFromMigrationSnapshot should NOT be called for already-imported
    expect(hydrateFromMigrationSnapshot).not.toHaveBeenCalled();

    // All stores should still be hydrated
    expect(mockNutritionHydrate).toHaveBeenCalledTimes(1);
    expect(mockWorkoutHydrate).toHaveBeenCalledTimes(1);
    expect(mockSettingsHydrate).toHaveBeenCalledTimes(1);
    expect(mockProgramHydrate).toHaveBeenCalledTimes(1);
  });

  it('should complete bootstrap with fresh snapshot (runs hydration)', async () => {
    (importBridgeSnapshotIfNeeded as jest.Mock).mockResolvedValue(SNAPSHOT_SUMMARY);
    (hydrateFromMigrationSnapshot as jest.Mock).mockResolvedValue(HYDRATION_OK);

    await useBootstrapStore.getState().bootstrap();

    const state = useBootstrapStore.getState();
    expect(state.status).toBe('ready');
    expect(state.summary).toEqual(SNAPSHOT_SUMMARY);
    expect(state.hydrationResult).toEqual(HYDRATION_OK);

    // hydrateFromMigrationSnapshot IS called for snapshot source
    expect(hydrateFromMigrationSnapshot).toHaveBeenCalledTimes(1);
  });

  it('should fail if snapshot has validation error', async () => {
    const invalidSummary: MigrationImportSummary = {
      source: 'snapshot',
      recordCounts: null,
      validationError: 'Schema version mismatch',
    };
    (importBridgeSnapshotIfNeeded as jest.Mock).mockResolvedValue(invalidSummary);

    await useBootstrapStore.getState().bootstrap();

    const state = useBootstrapStore.getState();
    expect(state.status).toBe('failed');
    expect(state.error).toContain('Schema version mismatch');
  });

  it('should fail if importBridgeSnapshotIfNeeded throws', async () => {
    (importBridgeSnapshotIfNeeded as jest.Mock).mockRejectedValue(new Error('SQLite locked'));

    await useBootstrapStore.getState().bootstrap();

    const state = useBootstrapStore.getState();
    expect(state.status).toBe('failed');
    expect(state.error).toBe('SQLite locked');
  });

  it('should fail if a critical store (nutrition/workout/settings) rejects', async () => {
    (importBridgeSnapshotIfNeeded as jest.Mock).mockResolvedValue(CLEAN_SUMMARY);
    mockWorkoutHydrate.mockRejectedValue(new Error('Workout DB corrupted'));

    await useBootstrapStore.getState().bootstrap();

    const state = useBootstrapStore.getState();
    expect(state.status).toBe('failed');
    expect(state.error).toContain('Workout DB corrupted');
  });

  it('should still succeed if a non-critical store (wellbeing/body) rejects', async () => {
    const consoleWarnSpy = jest.spyOn(console, 'warn').mockImplementation();
    (importBridgeSnapshotIfNeeded as jest.Mock).mockResolvedValue(CLEAN_SUMMARY);
    mockWellbeingHydrate.mockRejectedValue(new Error('Wellbeing minor issue'));

    await useBootstrapStore.getState().bootstrap();

    const state = useBootstrapStore.getState();
    expect(state.status).toBe('ready');
    expect(state.error).toBeNull();
    expect(consoleWarnSpy).toHaveBeenCalledWith(
      expect.stringContaining('[Bootstrap]'),
      expect.arrayContaining([expect.stringContaining('Wellbeing minor issue')]),
    );
    consoleWarnSpy.mockRestore();
  });

  it('should call all store hydration methods', async () => {
    (importBridgeSnapshotIfNeeded as jest.Mock).mockResolvedValue(CLEAN_SUMMARY);

    await useBootstrapStore.getState().bootstrap();

    expect(mockNutritionHydrate).toHaveBeenCalledTimes(1);
    expect(mockWorkoutHydrate).toHaveBeenCalledTimes(1);
    expect(mockSettingsHydrate).toHaveBeenCalledTimes(1);
    expect(mockWellbeingHydrate).toHaveBeenCalledTimes(1);
    expect(mockMealTemplateHydrate).toHaveBeenCalledTimes(1);
    expect(mockProgramHydrate).toHaveBeenCalledTimes(1);
    expect(mockBodyHydrate).toHaveBeenCalledTimes(1);
    expect(mockExerciseHydrate).toHaveBeenCalledTimes(1);
    expect(mockMealPlannerHydrate).toHaveBeenCalledTimes(1);
    expect(mockCoachHydrate).toHaveBeenCalledTimes(1);
    expect(mockAugeRuntimeHydrate).toHaveBeenCalledTimes(1);
    expect(mockDiagnosticsRefresh).toHaveBeenCalledTimes(1);
  });

  describe('retry', () => {
    it('should re-run bootstrap when status is failed', async () => {
      (importBridgeSnapshotIfNeeded as jest.Mock)
        .mockRejectedValueOnce(new Error('First attempt failed'))
        .mockResolvedValueOnce(CLEAN_SUMMARY);

      await useBootstrapStore.getState().bootstrap();
      expect(useBootstrapStore.getState().status).toBe('failed');

      await useBootstrapStore.getState().retry();
      expect(useBootstrapStore.getState().status).toBe('ready');
    });

    it('should NOT re-run when status is ready', async () => {
      (importBridgeSnapshotIfNeeded as jest.Mock).mockResolvedValue(CLEAN_SUMMARY);

      await useBootstrapStore.getState().bootstrap();
      expect(useBootstrapStore.getState().status).toBe('ready');

      const callsBefore = (importBridgeSnapshotIfNeeded as jest.Mock).mock.calls.length;
      await useBootstrapStore.getState().retry();
      expect((importBridgeSnapshotIfNeeded as jest.Mock).mock.calls.length).toBe(callsBefore);
    });
  });
});
