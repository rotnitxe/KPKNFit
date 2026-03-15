import { createNavigationContainerRef } from '@react-navigation/native';
import type { RootTabParamList } from './AppNavigator';

export const navigationRef = createNavigationContainerRef<RootTabParamList>();

const SCREEN_MAP: Record<string, keyof RootTabParamList> = {
  home: 'Home',
  workout: 'Workout',
  nutrition: 'Nutrition',
  progress: 'Progress',
  settings: 'Settings',
};

export function navigateFromExternalTarget(target: string | null | undefined) {
  if (!target || !navigationRef.isReady()) return;
  const normalized = target.toLowerCase();
  const screen = SCREEN_MAP[normalized];
  if (screen) {
    navigationRef.navigate(screen);
  }
}
