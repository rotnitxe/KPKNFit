import { NativeModules } from 'react-native';

const mockNativeModule = {
  getStatus: jest.fn(),
  getDiagnostics: jest.fn(),
  warmup: jest.fn(),
  analyzeNutritionDescription: jest.fn(),
  cancelCurrentAnalysis: jest.fn(),
  unload: jest.fn(),
};

jest.mock('react-native', () => ({
  NativeModules: {
    KPKNLocalAi: mockNativeModule,
  },
  Platform: {
    OS: 'android',
  },
}));

jest.mock('@kpkn/shared-domain', () => ({
  analyzeNutritionDescriptionLocally: jest.fn(),
  LOCAL_CHILEAN_FOOD_CATALOG: [],
}));

describe('localAi module bridge', () => {
  // We don't use a top-level require here to avoid pollution
  
  beforeEach(() => {
    jest.resetModules();
    jest.clearAllMocks();
  });

  it('isLocalAiModuleAvailable should be true when NativeModules.KPKNLocalAi exists', () => {
    const localAi = require('../../modules/localAi');
    expect(localAi.isLocalAiModuleAvailable).toBe(true);
  });

  it('getStatus should delegate to the native module', async () => {
    const localAi = require('../../modules/localAi');
    const mockStatus = { available: true };
    mockNativeModule.getStatus.mockResolvedValue(mockStatus);
    
    const status = await localAi.localAiModule.getStatus();
    
    expect(mockNativeModule.getStatus).toHaveBeenCalled();
    expect(status).toEqual(mockStatus);
  });

  it('warmup should delegate to the native module', async () => {
    const localAi = require('../../modules/localAi');
    const mockStatus = { modelReady: true };
    mockNativeModule.warmup.mockResolvedValue(mockStatus);
    
    const status = await localAi.localAiModule.warmup();
    
    expect(mockNativeModule.warmup).toHaveBeenCalled();
    expect(status).toEqual(mockStatus);
  });

  it('analyzeNutritionDescription should delegate to the native module', async () => {
    const localAi = require('../../modules/localAi');
    const request: any = { description: 'test', locale: 'es-CL', schemaVersion: '1' };
    const mockResult = { items: [] };
    mockNativeModule.analyzeNutritionDescription.mockResolvedValue(mockResult);
    
    const result = await localAi.localAiModule.analyzeNutritionDescription(request);
    
    expect(mockNativeModule.analyzeNutritionDescription).toHaveBeenCalledWith(request);
    expect(result).toEqual(mockResult);
  });

  it('isLocalAiModuleAvailable should be false when NativeModules.KPKNLocalAi does not exist', async () => {
    jest.doMock('react-native', () => ({
      NativeModules: {},
      Platform: { OS: 'android' },
    }));
    
    const isolatedLocalAi = require('../../modules/localAi');
    expect(isolatedLocalAi.isLocalAiModuleAvailable).toBe(false);
  });

  it('unload should delegate to the native module', async () => {
    // Re-mock to ensure fresh state
    jest.doMock('react-native', () => ({
      NativeModules: { KPKNLocalAi: mockNativeModule },
      Platform: { OS: 'android' },
    }));
    const localAi = require('../../modules/localAi');
    const mockUnloadResult = { unloaded: true };
    mockNativeModule.unload.mockResolvedValue(mockUnloadResult);
    
    const result = await localAi.localAiModule.unload();
    
    expect(mockNativeModule.unload).toHaveBeenCalled();
    expect(result).toEqual(mockUnloadResult);
  });
});
