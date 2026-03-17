import { createNavigationContainerRef } from '@react-navigation/native';
import type { RootTabParamList } from './AppNavigator';

export const navigationRef = createNavigationContainerRef<RootTabParamList>();

const SCREEN_MAP: Partial<Record<string, keyof RootTabParamList>> = {
  home: 'Home',
  rings: 'Rings',
  workout: 'Workout',
  nutrition: 'Nutrition',
  profile: 'Profile',
  wiki: 'Wiki',
  settings: 'Settings',
  coach: 'Coach',
};

export function navigateFromExternalTarget(target: string | null | undefined) {
  if (!target || !navigationRef.isReady()) return;
  const normalized = target.toLowerCase();

  if (normalized === 'progress') {
    navigationRef.navigate('Profile', { screen: 'ProgressOverview' });
    return;
  }

  const screen = SCREEN_MAP[normalized];
  if (screen) {
    navigationRef.navigate(screen);
  }
}
