import { getMockDatabase, resetMockDatabase } from '../mocks/mockDatabase';
import { deleteNutritionLog, updateNutritionLogDescription, persistNutritionLog, loadSavedNutritionLogs } from '../../services/mobilePersistenceService';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { SavedNutritionEntry } from '../../types/nutrition';

// Mock the database provider to use our in-memory mock
jest.mock('../../storage/mobileDatabase', () => ({
  getMobileDatabase: () => require('../mocks/mockDatabase').getMockDatabase(),
}));

describe('Nutrition CRUD Persistence', () => {
  beforeEach(() => {
    resetMockDatabase();
  });

  const sampleEntry: SavedNutritionEntry = {
    id: 'log-123',
    description: 'Avena con plátano',
    createdAt: new Date().toISOString(),
    totals: { calories: 350, protein: 12, carbs: 60, fats: 8 },
    analysis: {
      items: [],
      overallConfidence: 1,
      containsEstimatedItems: false,
      requiresReview: false,
      elapsedMs: 10,
      modelVersion: 'test',
      engine: 'unavailable',
      runtimeError: null,
    },
  };

  it('should delete a nutrition log', async () => {
    await persistNutritionLog(sampleEntry);
    let logs = await loadSavedNutritionLogs();
    expect(logs).toHaveLength(1);

    await deleteNutritionLog('log-123');
    logs = await loadSavedNutritionLogs();
    expect(logs).toHaveLength(0);
  });

  it('should update a nutrition log description', async () => {
    await persistNutritionLog(sampleEntry);
    
    await updateNutritionLogDescription('log-123', 'Avena con muchos arándanos');
    
    const logs = await loadSavedNutritionLogs();
    expect(logs[0].description).toBe('Avena con muchos arándanos');
    expect(logs[0].id).toBe('log-123'); // Should keep the same ID
  });

  it('should not delete other logs when deleting one', async () => {
    const entry2: SavedNutritionEntry = { ...sampleEntry, id: 'log-456', description: 'Omitir' };
    await persistNutritionLog(sampleEntry);
    await persistNutritionLog(entry2);
    
    await deleteNutritionLog('log-456');
    
    const logs = await loadSavedNutritionLogs();
    expect(logs).toHaveLength(1);
    expect(logs[0].id).toBe('log-123');
  });

  it('delete de un ID inexistente no lanza error', async () => {
    await expect(deleteNutritionLog('non-existent-uuid')).resolves.not.toThrow();
  });

  it('update descripción de un ID inexistente no lanza error', async () => {
    await expect(updateNutritionLogDescription('non-existent-uuid', 'nueva desc')).resolves.not.toThrow();
  });

  it('duplicateLog genera un ID diferente al original', async () => {
    const store = useMobileNutritionStore.getState();
    
    // Preparar el store con un log (mockeando la persistencia que usa el store internamente)
    // El store usa generateId() y persistNutritionLog()
    store.setDescription('Test');
    // Para que analyze funcione necesitamos mockear analyzeNutritionDraft o simplemente setear el estado
    useMobileNutritionStore.setState({
      lastAnalysis: sampleEntry.analysis,
      description: 'Test'
    });
    
    await useMobileNutritionStore.getState().saveCurrent();
    
    const logs = useMobileNutritionStore.getState().savedLogs;
    expect(logs.length).toBe(1);
    const originalId = logs[0].id;
    
    await useMobileNutritionStore.getState().duplicateLog(originalId);
    
    const updatedLogs = useMobileNutritionStore.getState().savedLogs;
    expect(updatedLogs.length).toBe(2);
    
    const duplicate = updatedLogs.find(l => l.id !== originalId);
    expect(duplicate).toBeDefined();
    expect(duplicate?.description).toBe('Test (copia)');
    expect(duplicate?.id).not.toBe(originalId);
  });
});
