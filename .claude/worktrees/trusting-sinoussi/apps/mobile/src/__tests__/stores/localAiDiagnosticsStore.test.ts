import { useLocalAiDiagnosticsStore } from '../../stores/localAiDiagnosticsStore';
import { localAiModule } from '../../modules/localAi';
import { runLocalAiSmokeTest } from '../../services/localAiSmokeTestService';

jest.mock('../../modules/localAi', () => ({
  localAiModule: {
    getStatus: jest.fn(),
    getDiagnostics: jest.fn(),
    warmup: jest.fn(),
    unload: jest.fn(),
  },
}));

jest.mock('../../services/localAiSmokeTestService', () => ({
  runLocalAiSmokeTest: jest.fn(),
}));

describe('localAiDiagnosticsStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // Reset store state
    useLocalAiDiagnosticsStore.setState({
      status: null,
      nativeDiagnostics: null,
      refreshState: 'idle',
      smokeTestState: 'idle',
      smokeTestError: null,
      lastCheckedAt: null,
      recentRuns: [],
    });
  });

  it('refreshStatus("status") should call getStatus and update state', async () => {
    const mockStatus = { available: true, engine: 'runtime' };
    const mockDiagnostics = { runtimeLoaded: true };
    (localAiModule.getStatus as jest.Mock).mockResolvedValue(mockStatus);
    (localAiModule.getDiagnostics as jest.Mock).mockResolvedValue(mockDiagnostics);

    await useLocalAiDiagnosticsStore.getState().refreshStatus('status');

    expect(localAiModule.getStatus).toHaveBeenCalled();
    expect(localAiModule.getDiagnostics).toHaveBeenCalled();
    expect(useLocalAiDiagnosticsStore.getState().status).toEqual(mockStatus);
    expect(useLocalAiDiagnosticsStore.getState().nativeDiagnostics).toEqual(mockDiagnostics);
    expect(useLocalAiDiagnosticsStore.getState().lastCheckedAt).not.toBeNull();
  });

  it('refreshStatus("warmup") should call warmup and update state', async () => {
    const mockStatus = { modelReady: true };
    (localAiModule.warmup as jest.Mock).mockResolvedValue(mockStatus);
    (localAiModule.getDiagnostics as jest.Mock).mockResolvedValue({});

    await useLocalAiDiagnosticsStore.getState().refreshStatus('warmup');

    expect(localAiModule.warmup).toHaveBeenCalled();
    expect(useLocalAiDiagnosticsStore.getState().status).toEqual(mockStatus);
  });

  it('runSmokeTest should update state and record result', async () => {
    const mockResult = { 
      engine: 'runtime', 
      items: [], 
      elapsedMs: 100, 
      overallConfidence: 1, 
      modelVersion: 'v1',
      runtimeError: null 
    };
    (runLocalAiSmokeTest as jest.Mock).mockResolvedValue(mockResult);
    (localAiModule.getStatus as jest.Mock).mockResolvedValue({});
    (localAiModule.getDiagnostics as jest.Mock).mockResolvedValue({});

    await useLocalAiDiagnosticsStore.getState().runSmokeTest('test-smoke');

    expect(runLocalAiSmokeTest).toHaveBeenCalledWith('test-smoke');
    expect(useLocalAiDiagnosticsStore.getState().recentRuns.length).toBe(1);
    expect(useLocalAiDiagnosticsStore.getState().smokeTestState).toBe('idle');
  });

  it('recordRun should keep only the last 5 runs', () => {
    const store = useLocalAiDiagnosticsStore.getState();
    const mockResult: any = { items: [], engine: 'runtime', elapsedMs: 10, overallConfidence: 1 };

    for (let i = 0; i < 10; i++) {
      store.recordRun(mockResult, `run-${i}`);
    }

    const runs = useLocalAiDiagnosticsStore.getState().recentRuns;
    expect(runs.length).toBe(5);
    expect(runs[0].descriptionPreview).toBe('run-9');
  });

  it('runSmokeTest should report error when it fails', async () => {
    (runLocalAiSmokeTest as jest.Mock).mockRejectedValue(new Error('Smoke Crash'));

    await useLocalAiDiagnosticsStore.getState().runSmokeTest();

    expect(useLocalAiDiagnosticsStore.getState().smokeTestError).toBe('Smoke Crash');
    expect(useLocalAiDiagnosticsStore.getState().smokeTestState).toBe('idle');
  });
});
