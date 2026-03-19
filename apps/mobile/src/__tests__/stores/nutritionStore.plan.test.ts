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

jest.mock('../../stores/settingsStore', () => ({
  useSettingsStore: {
    getState: jest.fn(),
  },
}));

import { useSettingsStore } from '../../stores/settingsStore';
import { loadSavedNutritionLogs } from '../../services/mobilePersistenceService';
import { useMobileNutritionStore } from '../../stores/nutritionStore';

describe('nutritionStore plan persistence', () => {
  const settingsFixture = {
    dailyCalorieGoal: 2400,
    dailyProteinGoal: 180,
    dailyCarbGoal: 240,
    dailyFatGoal: 70,
    calorieGoalConfig: {
      formula: 'mifflin',
      activityLevel: 3,
      goal: 'maintain',
      weeklyChangeKg: 0.45,
      healthMultiplier: 1,
    },
    calorieGoalObjective: 'maintenance',
  };

  const mockGetSettings = jest.fn(() => settingsFixture);
  const mockUpdateSettings = jest.fn().mockResolvedValue(undefined);

  beforeEach(() => {
    jest.clearAllMocks();
    (loadSavedNutritionLogs as jest.Mock).mockResolvedValue([]);
    (useSettingsStore.getState as jest.Mock).mockReturnValue({
      getSettings: mockGetSettings,
      updateSettings: mockUpdateSettings,
    });
    useMobileNutritionStore.setState({
      description: '',
      status: 'idle',
      lastAnalysis: null,
      savedLogs: [],
      nutritionPlan: {
        calories: 2000,
        protein: 150,
        carbs: 200,
        fats: 60,
      },
      favorites: [],
      hasHydrated: false,
      isDetailVisible: false,
      saveNotice: null,
      errorMessage: null,
    });
  });

  it('hydrates the nutrition plan from settings', async () => {
    await useMobileNutritionStore.getState().hydrateFromStorage();

    expect(useMobileNutritionStore.getState().nutritionPlan).toEqual({
      calories: 2400,
      protein: 180,
      carbs: 240,
      fats: 70,
      periodization: undefined,
    });
  });

  it('writes the normalized nutrition plan back to settings', async () => {
    await useMobileNutritionStore.getState().updateNutritionPlan({
      calories: 2250,
      protein: 165,
      carbs: 230,
      fats: 68,
    });

    expect(mockUpdateSettings).toHaveBeenCalledWith({
      dailyCalorieGoal: 2250,
      dailyProteinGoal: 165,
      dailyCarbGoal: 230,
      dailyFatGoal: 68,
      calorieGoalConfig: settingsFixture.calorieGoalConfig,
    });
    expect(useMobileNutritionStore.getState().nutritionPlan).toEqual({
      calories: 2250,
      protein: 165,
      carbs: 230,
      fats: 68,
    });
  });
});
