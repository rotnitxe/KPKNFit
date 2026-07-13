jest.mock('../../services/mobileDomainStateService', () => ({
  readStoredSettingsRaw: jest.fn(),
}));

import React from 'react';
import renderer from 'react-test-renderer';
import { ThemeProvider, useIsDark } from '../../theme';
import { readStoredSettingsRaw } from '../../services/mobileDomainStateService';
import { useSettingsStore } from '../../stores/settingsStore';

function Probe({ onValue }: { onValue: (value: boolean) => void }) {
  const isDark = useIsDark();
  onValue(isDark);
  return null;
}

describe('ThemeProvider', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useSettingsStore.setState({
      status: 'ready',
      summary: {
        appTheme: 'light',
      } as any,
    } as any);
    (readStoredSettingsRaw as jest.Mock).mockReturnValue({
      appTheme: 'light',
    });
  });

  it('respects the stored appTheme preference', () => {
    let captured = true;
    renderer.act(() => {
      renderer.create(
        <ThemeProvider initialDark={true}>
          <Probe onValue={value => {
            captured = value;
          }} />
        </ThemeProvider>,
      );
    });

    expect(captured).toBe(false);
  });
});
