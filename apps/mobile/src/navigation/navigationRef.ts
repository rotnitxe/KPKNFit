import { createNavigationContainerRef } from '@react-navigation/native';
import type { RootTabParamList } from './types';

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

const NESTED_TARGET_MAP: Record<string, { screen: keyof RootTabParamList; params: NonNullable<RootTabParamList[keyof RootTabParamList]> }> = {
  'log-hub': { screen: 'Workout', params: { screen: 'LogHub' } },
  'log-workout': { screen: 'Workout', params: { screen: 'LogWorkout' } },
  progress: { screen: 'Profile', params: { screen: 'ProgressOverview' } },
  'body-progress': { screen: 'Profile', params: { screen: 'BodyProgress' } },
  programs: { screen: 'Workout', params: { screen: 'ProgramsList' } },
  'workout-main': { screen: 'Workout', params: { screen: 'WorkoutMain' } },
  'meal-planner': { screen: 'Nutrition', params: { screen: 'MealPlanner' } },
  'nutrition-log': { screen: 'Nutrition', params: { screen: 'NutritionLog' } },
  'food-database': { screen: 'Nutrition', params: { screen: 'FoodDatabase' } },
  'wiki-home': { screen: 'Wiki', params: { screen: 'WikiHome' } },
  'wiki-mobility': { screen: 'Wiki', params: { screen: 'WikiMobility' } },
  'wiki-biomechanics': { screen: 'Wiki', params: { screen: 'WikiBiomechanics' } },
  'exercise-database': { screen: 'Wiki', params: { screen: 'ExerciseDatabase' } },
};

export function navigateFromExternalTarget(target: string | null | undefined) {
  if (!target || !navigationRef.isReady()) return;
  const normalized = target.toLowerCase();
  const navigate = navigationRef.navigate as (
    screen: keyof RootTabParamList,
    params?: RootTabParamList[keyof RootTabParamList],
  ) => void;

  const nestedTarget = NESTED_TARGET_MAP[normalized];
  if (nestedTarget) {
    navigate(nestedTarget.screen, nestedTarget.params);
    return;
  }

  const screen = SCREEN_MAP[normalized];
  if (screen) {
    navigate(screen);
  }
}
