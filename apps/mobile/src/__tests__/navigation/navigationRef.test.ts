const mockNavigate = jest.fn();
const mockIsReady = jest.fn();

import { navigationRef, navigateFromExternalTarget } from '../../navigation/navigationRef';

describe('navigateFromExternalTarget', () => {
  beforeEach(() => {
    Object.assign(navigationRef, {
      navigate: mockNavigate,
      isReady: mockIsReady,
    });
    mockNavigate.mockReset();
    mockIsReady.mockReset();
    mockIsReady.mockReturnValue(true);
  });

  it('routes nested external targets to the expected stack screen', () => {
    navigateFromExternalTarget('food-database');
    expect(mockNavigate).toHaveBeenCalledWith('Nutrition', { screen: 'FoodDatabase' });
  });

  it('still supports top-level tab targets', () => {
    navigateFromExternalTarget('settings');
    expect(mockNavigate).toHaveBeenCalledWith('Settings');
  });

  it('does nothing when navigation is not ready', () => {
    mockIsReady.mockReturnValue(false);
    navigateFromExternalTarget('log-hub');
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
