import { useProgramStore } from '../../stores/programStore';
import { loadPersistedDomainPayload } from '../../services/mobilePersistenceService';

jest.mock('../../services/mobilePersistenceService', () => ({
  loadPersistedDomainPayload: jest.fn(),
}));

jest.mock('../../storage/mmkv', () => ({
  setJsonValue: jest.fn(),
  getJsonValue: jest.fn(),
}));

describe('programStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useProgramStore.setState({
      status: 'idle',
      programs: [],
      activeProgramState: null,
      hasHydrated: false,
      errorMessage: null,
    });
  });

  it('should have correct initial state', () => {
    const state = useProgramStore.getState();
    expect(state.status).toBe('idle');
    expect(state.programs).toEqual([]);
    expect(state.hasHydrated).toBe(false);
  });

  it('should hydrate successfully with valid payload', async () => {
    const mockPayload = {
      programs: [{ id: 'p1', name: 'Test Program' }],
      activeProgramState: { programId: 'p1' },
    };
    (loadPersistedDomainPayload as jest.Mock).mockResolvedValue(mockPayload);

    await useProgramStore.getState().hydrateFromMigration();

    const state = useProgramStore.getState();
    expect(state.status).toBe('ready');
    expect(state.programs).toEqual(mockPayload.programs);
    expect(state.activeProgramState).toEqual(mockPayload.activeProgramState);
    expect(state.hasHydrated).toBe(true);
    expect(state.errorMessage).toBeNull();
  });

  it('should handle empty payload gracefully', async () => {
    (loadPersistedDomainPayload as jest.Mock).mockResolvedValue(null);

    await useProgramStore.getState().hydrateFromMigration();

    const state = useProgramStore.getState();
    expect(state.status).toBe('ready');
    expect(state.programs).toEqual([]);
    expect(state.hasHydrated).toBe(true);
  });

  it('should handle SQLite error', async () => {
    (loadPersistedDomainPayload as jest.Mock).mockRejectedValue(new Error('DB Error'));

    await useProgramStore.getState().hydrateFromMigration();

    const state = useProgramStore.getState();
    expect(state.status).toBe('failed');
    expect(state.errorMessage).toBe('DB Error');
    expect(state.hasHydrated).toBe(true);
  });
});
