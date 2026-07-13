import { useBodyStore } from '../../stores/bodyStore';
import { loadPersistedDomainPayload, persistDomainPayload } from '../../services/mobilePersistenceService';
import { setJsonValue } from '../../storage/mmkv';

jest.mock('../../services/mobilePersistenceService', () => ({
  loadPersistedDomainPayload: jest.fn(),
  persistDomainPayload: jest.fn(),
}));

jest.mock('../../storage/mmkv', () => ({
  setJsonValue: jest.fn(),
}));

describe('bodyStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useBodyStore.setState({
      status: 'idle',
      bodyProgress: [],
      bodyLabAnalysis: null,
      biomechanicalData: null,
      biomechanicalAnalysis: null,
      hasHydrated: false,
      errorMessage: null,
      notice: null,
    });
  });

  describe('hydration', () => {
    it('should hydrate successfully with valid payload', async () => {
      const mockPayload = {
        bodyProgress: [{ id: '1', date: '2024-01-01', weight: 80 }],
        bodyLabAnalysis: { cholesterol: 180 },
      };
      (loadPersistedDomainPayload as jest.Mock).mockResolvedValue(mockPayload);

      await useBodyStore.getState().hydrateFromMigration();

      const state = useBodyStore.getState();
      expect(state.status).toBe('ready');
      expect(state.bodyProgress).toEqual(mockPayload.bodyProgress);
      expect(state.bodyLabAnalysis).toEqual(mockPayload.bodyLabAnalysis);
      expect(state.hasHydrated).toBe(true);
      expect(setJsonValue).toHaveBeenCalledWith('rn.body', expect.any(Object));
    });

    it('should handle errors during hydration', async () => {
      (loadPersistedDomainPayload as jest.Mock).mockRejectedValue(new Error('Body error'));

      await useBodyStore.getState().hydrateFromMigration();

      const state = useBodyStore.getState();
      expect(state.status).toBe('failed');
      expect(state.errorMessage).toBe('Body error');
    });
  });

  describe('CRUD operations', () => {
    it('should add a new body log', async () => {
      const newLog = { weight: 75, muscleMass: 35 };
      (persistDomainPayload as jest.Mock).mockResolvedValue(undefined);

      await useBodyStore.getState().addBodyLog(newLog as any);

      const state = useBodyStore.getState();
      expect(state.bodyProgress).toHaveLength(1);
      expect(state.bodyProgress[0]).toMatchObject(newLog);
      expect(state.bodyProgress[0].id).toBeDefined();
      expect(state.bodyProgress[0].date).toBeDefined();
      expect(state.notice).toBe('Registro corporal guardado.');
      expect(persistDomainPayload).toHaveBeenCalledWith('body', expect.any(Object));
    });

    it('should update an existing body log', async () => {
      const initialLog = { id: '1', date: '2024-01-01', weight: 80 };
      useBodyStore.setState({ bodyProgress: [initialLog as any] });
      (persistDomainPayload as jest.Mock).mockResolvedValue(undefined);

      await useBodyStore.getState().updateBodyLog('1', { weight: 78 });

      const state = useBodyStore.getState();
      expect(state.bodyProgress[0].weight).toBe(78);
      expect(state.notice).toBe('Registro corporal actualizado.');
    });

    it('should handle updating a non-existent log', async () => {
      await useBodyStore.getState().updateBodyLog('non-existent', { weight: 70 });
      expect(useBodyStore.getState().notice).toBe('No encontramos el registro para editar.');
      expect(persistDomainPayload).not.toHaveBeenCalled();
    });

    it('should delete an existing body log', async () => {
      const logToDelete = { id: '1', date: '2024-01-01', weight: 80 };
      useBodyStore.setState({ bodyProgress: [logToDelete as any] });
      (persistDomainPayload as jest.Mock).mockResolvedValue(undefined);

      await useBodyStore.getState().deleteBodyLog('1');

      const state = useBodyStore.getState();
      expect(state.bodyProgress).toHaveLength(0);
      expect(state.notice).toBe('Registro corporal eliminado.');
    });

    it('should handle deleting a non-existent log', async () => {
      await useBodyStore.getState().deleteBodyLog('non-existent');
      expect(useBodyStore.getState().notice).toBe('No encontramos el registro para eliminar.');
      expect(persistDomainPayload).not.toHaveBeenCalled();
    });

    it('should clear the notice', () => {
      useBodyStore.setState({ notice: 'Some notice' });
      useBodyStore.getState().clearNotice();
      expect(useBodyStore.getState().notice).toBeNull();
    });

    it('should handle errors during persistence', async () => {
      (persistDomainPayload as jest.Mock).mockRejectedValue(new Error('Persistence failed'));
      
      await useBodyStore.getState().addBodyLog({ weight: 70 } as any);

      const state = useBodyStore.getState();
      expect(state.status).toBe('failed');
      expect(state.errorMessage).toBe('Persistence failed');
    });
  });
});
