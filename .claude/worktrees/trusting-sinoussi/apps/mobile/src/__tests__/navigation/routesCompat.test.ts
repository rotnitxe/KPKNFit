const mockNavigate = jest.fn();
const mockIsReady = jest.fn();
const mockCanGoBack = jest.fn();
const mockGoBack = jest.fn();

import { navigationRef } from '@/navigation/navigationRef';
import { getRouterRef, routerBack, routerNavigate, setRouterRef, viewToPath } from '@/routes/navigation';
import { router } from '@/routes/router';

describe('routes compatibility layer', () => {
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

  it('exposes compatibility path mapping', () => {
    expect(viewToPath('program-detail', { programId: 'p-1' })).toBe('/workout/programs/p-1');
  });

  it('navigates via legacy router API', () => {
    routerNavigate('program-detail', { programId: 'p-1' });
    expect(mockNavigate).toHaveBeenCalledWith('Workout', {
      screen: 'ProgramDetail',
      params: { programId: 'p-1' },
    });
  });

  it('supports router module navigation payloads', () => {
    router.navigate({ to: '/nutrition/planner' });
    expect(mockNavigate).toHaveBeenCalledWith('Nutrition', { screen: 'MealPlanner' });
  });

  it('keeps deep links for nested home and metric surfaces compatible', () => {
    router.navigate({ to: '/home/tasks' });
    expect(mockNavigate).toHaveBeenCalledWith('Home', { screen: 'Tasks' });

    router.navigate({ to: '/workout/programs/p-1/metric/strength' });
    expect(mockNavigate).toHaveBeenCalledWith('Workout', {
      screen: 'ProgramMetricDetail',
      params: { programId: 'p-1', metric: 'strength' },
    });
  });

  it('keeps set/get router ref helpers', () => {
    const ref = { hello: 'world' };
    setRouterRef(ref);
    expect(getRouterRef()).toBe(ref);
  });

  it('calls back using compatibility API', () => {
    routerBack();
    expect(mockGoBack).toHaveBeenCalledTimes(1);
  });

  it('navigates to ProgramWizard via deep link', () => {
    router.navigate({ to: '/workout/programs/new/wizard' });
    expect(mockNavigate).toHaveBeenCalledWith('Workout', {
      screen: 'ProgramWizard',
      params: { mode: 'create' },
    });
  });

  it('navigates to SplitEditor via deep link', () => {
    router.navigate({ to: '/workout/programs/p-1/split' });
    expect(mockNavigate).toHaveBeenCalledWith('Workout', {
      screen: 'SplitEditor',
      params: { programId: 'p-1' },
    });
  });

  it('navigates to MacrocycleEditor via deep link', () => {
    router.navigate({ to: '/workout/programs/p-1/macrocycle' });
    expect(mockNavigate).toHaveBeenCalledWith('Workout', {
      screen: 'MacrocycleEditor',
      params: { programId: 'p-1' },
    });
  });
});
