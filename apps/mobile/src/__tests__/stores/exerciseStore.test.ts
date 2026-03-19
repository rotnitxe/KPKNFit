import { useExerciseStore } from '../../stores/exerciseStore';
import { loadPersistedDomainPayload } from '../../services/mobilePersistenceService';

jest.mock('../../services/mobilePersistenceService', () => ({
  loadPersistedDomainPayload: jest.fn(),
}));

jest.mock('../../storage/mmkv', () => ({
  setJsonValue: jest.fn(),
  getJsonValue: jest.fn(() => null),
}));

describe('exerciseStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useExerciseStore.setState({
      status: 'idle',
      exerciseList: [],
      exercisePlaylists: [],
      muscleGroupData: [],
      muscleHierarchy: null,
      hasHydrated: false,
      errorMessage: null,
    });
  });

  it('should hydrate successfully with valid payload', async () => {
    const mockPayload = {
      exerciseList: [{ id: 'ex1', name: 'Squat' }],
      muscleGroupData: [{ id: 'legs', name: 'Legs' }],
    };
    (loadPersistedDomainPayload as jest.Mock).mockResolvedValue(mockPayload);

    await useExerciseStore.getState().hydrateFromMigration();

    const state = useExerciseStore.getState();
    expect(state.status).toBe('ready');
    expect(state.exerciseList).toEqual(mockPayload.exerciseList);
    expect(state.muscleGroupData).toEqual(mockPayload.muscleGroupData);
    expect(state.hasHydrated).toBe(true);
  });

  it('should handle empty payload', async () => {
    (loadPersistedDomainPayload as jest.Mock).mockResolvedValue(null);

    await useExerciseStore.getState().hydrateFromMigration();

    const state = useExerciseStore.getState();
    expect(state.status).toBe('ready');
    expect(state.hasHydrated).toBe(true);
  });

  it('should handle errors', async () => {
    (loadPersistedDomainPayload as jest.Mock).mockRejectedValue(new Error('Exercise error'));

    await useExerciseStore.getState().hydrateFromMigration();

    const state = useExerciseStore.getState();
    expect(state.status).toBe('failed');
    expect(state.errorMessage).toBe('Exercise error');
  });
});
