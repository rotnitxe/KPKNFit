const mockNavigate = jest.fn();
const mockIsReady = jest.fn();
const mockCanGoBack = jest.fn();
const mockGoBack = jest.fn();

import {
  navigationRef,
  navigateFromExternalTarget,
  routerBack,
  routerNavigate,
  viewToPath,
} from '../../navigation/navigationRef';

describe('navigationRef compatibility helpers', () => {
  beforeEach(() => {
    Object.assign(navigationRef, {
      navigate: mockNavigate,
      isReady: mockIsReady,
      canGoBack: mockCanGoBack,
      goBack: mockGoBack,
    });
    mockNavigate.mockReset();
    mockIsReady.mockReset();
    mockCanGoBack.mockReset();
    mockGoBack.mockReset();
    mockIsReady.mockReturnValue(true);
    mockCanGoBack.mockReturnValue(true);
  });

  it('routes nested external targets to the expected stack screen', () => {
    navigateFromExternalTarget('food-database');
    expect(mockNavigate).toHaveBeenCalledWith('Nutrition', { screen: 'FoodDatabase' });
  });

  it('supports deep-link style target paths', () => {
    navigateFromExternalTarget('kpkn://wiki/chains/posterior');
    expect(mockNavigate).toHaveBeenCalledWith('Wiki', {
      screen: 'WikiChainDetail',
      params: { chainId: 'posterior' },
    });
  });

  it('keeps support for tab level targets', () => {
    navigateFromExternalTarget('settings');
    expect(mockNavigate).toHaveBeenCalledWith('Settings', undefined);
  });

  it('maps legacy view names through routerNavigate', () => {
    routerNavigate('program-detail', { programId: 'p1' });
    expect(mockNavigate).toHaveBeenCalledWith('Workout', {
      screen: 'ProgramDetail',
      params: { programId: 'p1' },
    });
  });

  it('routes formerly-falling-back home surfaces to explicit nested screens', () => {
    routerNavigate('recovery');
    expect(mockNavigate).toHaveBeenCalledWith('Home', { screen: 'Recovery' });

    routerNavigate('auth');
    expect(mockNavigate).toHaveBeenCalledWith('Home', { screen: 'Auth' });
  });

  it('routes legacy metric and wiki detail views without collapsing to home', () => {
    routerNavigate('program-metric-volume', { programId: 'p1' });
    expect(mockNavigate).toHaveBeenCalledWith('Workout', {
      screen: 'ProgramMetricDetail',
      params: { programId: 'p1', metric: 'volume' },
    });

    routerNavigate('body-part-detail', { bodyPartId: 'Tren Superior' });
    expect(mockNavigate).toHaveBeenCalledWith('Wiki', {
      screen: 'BodyPartDetail',
      params: { bodyPartId: 'Tren Superior' },
    });

    routerNavigate('muscle-category', { categoryName: 'Pecho' });
    expect(mockNavigate).toHaveBeenCalledWith('Wiki', {
      screen: 'MuscleCategory',
      params: { categoryName: 'Pecho' },
    });
  });

  it('produces stable paths for legacy view routing', () => {
    expect(viewToPath('log-workout')).toBe('/workout/log-workout');
    expect(viewToPath('chain-detail', { chainId: 'anterior' })).toBe('/wiki/chains/anterior');
    expect(viewToPath('recovery')).toBe('/home/recovery');
    expect(viewToPath('program-metric-rpe', { programId: 'p2' })).toBe('/workout/programs/p2/metric/rpe');
  });

  it('calls back only when navigation can go back', () => {
    routerBack();
    expect(mockGoBack).toHaveBeenCalledTimes(1);

    mockCanGoBack.mockReturnValue(false);
    routerBack();
    expect(mockGoBack).toHaveBeenCalledTimes(1);
  });

  it('does nothing when navigation is not ready', () => {
    mockIsReady.mockReturnValue(false);
    navigateFromExternalTarget('log-hub');
    routerNavigate('log-hub');
    routerBack();
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(mockGoBack).not.toHaveBeenCalled();
  });
});
