import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { analyzeNutritionDraft } from '../../services/nutritionAnalyzer';
import {
  loadSavedNutritionLogs,
  persistNutritionLog,
  deleteNutritionLog,
  updateNutritionLogDescription,
} from '../../services/mobilePersistenceService';
import { syncNutritionWidgetState } from '../../services/widgetSyncService';
import { rescheduleCoreNotificationsFromStorage } from '../../services/mobileNotificationService';
import type { LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';
import type { SavedNutritionEntry } from '../../types/nutrition';

jest.mock('../../services/nutritionAnalyzer', () => ({
  analyzeNutritionDraft: jest.fn(),
}));

jest.mock('../../services/mobilePersistenceService', () => ({
  loadSavedNutritionLogs: jest.fn(),
  persistNutritionLog: jest.fn(),
  deleteNutritionLog: jest.fn(),
  updateNutritionLogDescription: jest.fn(),
}));

jest.mock('../../services/widgetSyncService', () => ({
  syncNutritionWidgetState: jest.fn(),
}));

jest.mock('../../services/mobileNotificationService', () => ({
  rescheduleCoreNotificationsFromStorage: jest.fn(),
}));

jest.mock('../../storage/mmkv', () => ({
  appStorage: { getString: jest.fn(), set: jest.fn(), delete: jest.fn() },
  setJsonValue: jest.fn(),
  getJsonValue: jest.fn(() => null),
}));

const mockAnalysisResult: LocalAiNutritionAnalysisResult = {
  items: [
    {
      rawText: 'pollo a la plancha 200g',
      canonicalName: 'Pollo a la plancha',
      calories: 330,
      protein: 62,
      carbs: 0,
      fats: 7,
      grams: 200,
      source: 'local-ai-estimate',
      confidence: 0.9,
      reviewRequired: false,
    },
    {
      rawText: 'arroz blanco 150g',
      canonicalName: 'Arroz blanco',
      calories: 195,
      protein: 4,
      carbs: 43,
      fats: 0.4,
      grams: 150,
      source: 'local-ai-estimate',
      confidence: 0.85,
      reviewRequired: false,
    },
  ],
  overallConfidence: 0.87,
  containsEstimatedItems: true,
  requiresReview: false,
  elapsedMs: 450,
  modelVersion: 'kpkn-food-fg270m-v1',
  engine: 'runtime',
};

const mockEmptyAnalysisResult: LocalAiNutritionAnalysisResult = {
  items: [],
  overallConfidence: 0,
  containsEstimatedItems: false,
  requiresReview: false,
  elapsedMs: 120,
  modelVersion: 'kpkn-food-fg270m-v1',
  engine: 'heuristics',
};

const mockSavedLog: SavedNutritionEntry = {
  id: 'log-1',
  description: 'Pollo con arroz',
  createdAt: '2025-01-01T12:00:00.000Z',
  totals: { calories: 525, protein: 66, carbs: 43, fats: 7.4 },
  analysis: mockAnalysisResult,
};

function resetStore() {
  useMobileNutritionStore.setState({
    description: '',
    status: 'idle',
    lastAnalysis: null,
    savedLogs: [],
    hasHydrated: false,
    isDetailVisible: false,
    saveNotice: null,
    errorMessage: null,
  });
}

describe('nutritionStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    resetStore();
    (syncNutritionWidgetState as jest.Mock).mockResolvedValue(undefined);
    (rescheduleCoreNotificationsFromStorage as jest.Mock).mockResolvedValue(undefined);
    (persistNutritionLog as jest.Mock).mockResolvedValue(undefined);
    (deleteNutritionLog as jest.Mock).mockResolvedValue(undefined);
    (updateNutritionLogDescription as jest.Mock).mockResolvedValue(undefined);
  });

  describe('hydrateFromStorage', () => {
    it('should load saved logs and sync widgets/notifications', async () => {
      (loadSavedNutritionLogs as jest.Mock).mockResolvedValue([mockSavedLog]);

      await useMobileNutritionStore.getState().hydrateFromStorage();

      const state = useMobileNutritionStore.getState();
      expect(state.savedLogs).toEqual([mockSavedLog]);
      expect(state.hasHydrated).toBe(true);
      expect(syncNutritionWidgetState).toHaveBeenCalledWith([mockSavedLog]);
      expect(rescheduleCoreNotificationsFromStorage).toHaveBeenCalledTimes(1);
    });

    it('should handle empty storage', async () => {
      (loadSavedNutritionLogs as jest.Mock).mockResolvedValue([]);

      await useMobileNutritionStore.getState().hydrateFromStorage();

      expect(useMobileNutritionStore.getState().savedLogs).toEqual([]);
      expect(useMobileNutritionStore.getState().hasHydrated).toBe(true);
    });
  });

  describe('setDescription', () => {
    it('should reset analysis state when description changes', () => {
      useMobileNutritionStore.setState({
        status: 'ready',
        lastAnalysis: mockAnalysisResult,
        errorMessage: 'previous error',
        saveNotice: 'saved!',
      });

      useMobileNutritionStore.getState().setDescription('nuevo texto');

      const state = useMobileNutritionStore.getState();
      expect(state.description).toBe('nuevo texto');
      expect(state.status).toBe('idle');
      expect(state.lastAnalysis).toBeNull();
      expect(state.errorMessage).toBeNull();
      expect(state.saveNotice).toBeNull();
    });
  });

  describe('analyze', () => {
    it('should fail if description is empty', async () => {
      useMobileNutritionStore.setState({ description: '   ' });

      await useMobileNutritionStore.getState().analyze();

      expect(useMobileNutritionStore.getState().status).toBe('failed');
      expect(useMobileNutritionStore.getState().errorMessage).toContain('Escribe algo');
      expect(analyzeNutritionDraft).not.toHaveBeenCalled();
    });

    it('should analyze a description successfully', async () => {
      useMobileNutritionStore.setState({ description: 'pollo con arroz' });
      (analyzeNutritionDraft as jest.Mock).mockResolvedValue(mockAnalysisResult);

      await useMobileNutritionStore.getState().analyze();

      const state = useMobileNutritionStore.getState();
      expect(state.status).toBe('ready');
      expect(state.lastAnalysis).toEqual(mockAnalysisResult);
      expect(state.isDetailVisible).toBe(false);
      expect(state.errorMessage).toBeNull();
    });

    it('should pass locale, schema, knownFoods, and userMemory to analyzer', async () => {
      useMobileNutritionStore.setState({
        description: 'pollo con arroz',
        savedLogs: [mockSavedLog],
      });
      (analyzeNutritionDraft as jest.Mock).mockResolvedValue(mockAnalysisResult);

      await useMobileNutritionStore.getState().analyze();

      expect(analyzeNutritionDraft).toHaveBeenCalledWith(
        expect.objectContaining({
          description: 'pollo con arroz',
          locale: 'es-CL',
          schemaVersion: 'rn-v1',
          knownFoods: expect.any(Array),
          userMemory: ['Pollo con arroz'],
        }),
      );
    });

    it('should show error when analysis returns empty items', async () => {
      useMobileNutritionStore.setState({ description: 'xyz random' });
      (analyzeNutritionDraft as jest.Mock).mockResolvedValue(mockEmptyAnalysisResult);

      await useMobileNutritionStore.getState().analyze();

      const state = useMobileNutritionStore.getState();
      expect(state.status).toBe('failed');
      expect(state.errorMessage).toContain('No pudimos estimar');
    });

    it('should handle analyzer errors', async () => {
      useMobileNutritionStore.setState({ description: 'something' });
      (analyzeNutritionDraft as jest.Mock).mockRejectedValue(new Error('AI timeout'));

      await useMobileNutritionStore.getState().analyze();

      expect(useMobileNutritionStore.getState().status).toBe('failed');
      expect(useMobileNutritionStore.getState().errorMessage).toBe('AI timeout');
    });
  });

  describe('saveCurrent', () => {
    it('should not save if no analysis', async () => {
      useMobileNutritionStore.setState({ description: 'pollo', lastAnalysis: null });

      await useMobileNutritionStore.getState().saveCurrent();

      expect(persistNutritionLog).not.toHaveBeenCalled();
    });

    it('should save current analysis as a new log', async () => {
      useMobileNutritionStore.setState({
        description: 'pollo con arroz',
        lastAnalysis: mockAnalysisResult,
        savedLogs: [],
      });

      await useMobileNutritionStore.getState().saveCurrent();

      expect(persistNutritionLog).toHaveBeenCalledTimes(1);
      const savedEntry = (persistNutritionLog as jest.Mock).mock.calls[0][0];
      expect(savedEntry.description).toBe('pollo con arroz');
      expect(savedEntry.totals.calories).toBe(525);
      expect(savedEntry.totals.protein).toBe(66);
      expect(savedEntry.id).toBeDefined();

      const state = useMobileNutritionStore.getState();
      expect(state.savedLogs).toHaveLength(1);
      expect(state.saveNotice).toContain('guardado');
      expect(syncNutritionWidgetState).toHaveBeenCalled();
    });

    it('should limit saved logs to 100', async () => {
      const manyLogs = Array.from({ length: 100 }, (_, i) => ({
        ...mockSavedLog,
        id: `log-${i}`,
      }));
      useMobileNutritionStore.setState({
        description: 'new meal',
        lastAnalysis: mockAnalysisResult,
        savedLogs: manyLogs,
      });

      await useMobileNutritionStore.getState().saveCurrent();

      expect(useMobileNutritionStore.getState().savedLogs).toHaveLength(100);
    });
  });

  describe('deleteLog', () => {
    it('should remove a log by id', async () => {
      useMobileNutritionStore.setState({
        savedLogs: [mockSavedLog, { ...mockSavedLog, id: 'log-2' }],
      });

      await useMobileNutritionStore.getState().deleteLog('log-1');

      expect(deleteNutritionLog).toHaveBeenCalledWith('log-1');
      expect(useMobileNutritionStore.getState().savedLogs).toHaveLength(1);
      expect(useMobileNutritionStore.getState().savedLogs[0].id).toBe('log-2');
      expect(syncNutritionWidgetState).toHaveBeenCalled();
    });
  });

  describe('editDescription', () => {
    it('should update a log description', async () => {
      useMobileNutritionStore.setState({ savedLogs: [mockSavedLog] });

      await useMobileNutritionStore.getState().editDescription('log-1', 'Pollo con ensalada');

      expect(updateNutritionLogDescription).toHaveBeenCalledWith('log-1', 'Pollo con ensalada');
      const updated = useMobileNutritionStore.getState().savedLogs[0];
      expect(updated.description).toBe('Pollo con ensalada');
    });
  });

  describe('duplicateLog', () => {
    it('should duplicate a log with (copia) suffix', async () => {
      useMobileNutritionStore.setState({ savedLogs: [mockSavedLog] });

      await useMobileNutritionStore.getState().duplicateLog('log-1');

      const state = useMobileNutritionStore.getState();
      expect(state.savedLogs).toHaveLength(2);
      expect(state.savedLogs[0].description).toBe('Pollo con arroz (copia)');
      expect(state.savedLogs[0].id).not.toBe('log-1');
      expect(state.saveNotice).toContain('duplicada');
      expect(persistNutritionLog).toHaveBeenCalledTimes(1);
    });

    it('should not duplicate a non-existent log', async () => {
      useMobileNutritionStore.setState({ savedLogs: [mockSavedLog] });

      await useMobileNutritionStore.getState().duplicateLog('nonexistent');

      expect(persistNutritionLog).not.toHaveBeenCalled();
      expect(useMobileNutritionStore.getState().savedLogs).toHaveLength(1);
    });
  });

  describe('toggleDetail / clearNotice', () => {
    it('should toggle detail visibility', () => {
      expect(useMobileNutritionStore.getState().isDetailVisible).toBe(false);
      useMobileNutritionStore.getState().toggleDetail();
      expect(useMobileNutritionStore.getState().isDetailVisible).toBe(true);
      useMobileNutritionStore.getState().toggleDetail();
      expect(useMobileNutritionStore.getState().isDetailVisible).toBe(false);
    });

    it('should clear save notice', () => {
      useMobileNutritionStore.setState({ saveNotice: 'Saved!' });
      useMobileNutritionStore.getState().clearNotice();
      expect(useMobileNutritionStore.getState().saveNotice).toBeNull();
    });
  });
});
