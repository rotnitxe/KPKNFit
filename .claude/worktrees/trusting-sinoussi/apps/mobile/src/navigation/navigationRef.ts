import { createNavigationContainerRef } from '@react-navigation/native';
import type { RootTabParamList } from './types';
import type { WikiChainId } from '@/data/wikiExploreData';
import type { ProgramMetricRoute } from './types';
import { useProgramStore } from '@/stores/programStore';

export const navigationRef = createNavigationContainerRef<RootTabParamList>();

type RootTabKey = keyof RootTabParamList;
type RootTabParams = RootTabParamList[RootTabKey];

type LegacyView =
  | 'auth'
  | 'home'
  | 'athlete-id'
  | 'my-rings'
  | 'programs'
  | 'program-detail'
  | 'program-editor'
  | 'session-editor'
  | 'workout'
  | 'progress'
  | 'settings'
  | 'coach'
  | 'log-hub'
  | 'achievements'
  | 'log-workout'
  | 'kpkn'
  | 'ai-art-studio'
  | 'body-lab'
  | 'mobility-lab'
  | 'training-purpose'
  | 'exercise-database'
  | 'food-database'
  | 'smart-meal-planner'
  | 'exercise-detail'
  | 'muscle-group-detail'
  | 'body-part-detail'
  | 'muscle-category'
  | 'chain-detail'
  | 'joint-detail'
  | 'tendon-detail'
  | 'movement-pattern-detail'
  | 'wiki-home'
  | 'wikilab-biomechanics'
  | 'nutrition'
  | 'food-detail'
  | 'session-detail'
  | 'tasks'
  | 'social-feed'
  | 'athlete-profile'
  | 'recovery'
  | 'sleep'
  | 'program-metric-volume'
  | 'program-metric-strength'
  | 'program-metric-density'
  | 'program-metric-frequency'
  | 'program-metric-banister'
  | 'program-metric-recovery'
  | 'program-metric-adherence'
  | 'program-metric-rpe'
  | 'body-progress'
  | 'home-card-page';

type NavigationData = Record<string, unknown> | undefined;

interface NavigationTarget {
  screen: RootTabKey;
  params?: RootTabParams;
}

const PROGRAM_METRIC_VIEW_MAP: Record<
  | 'program-metric-volume'
  | 'program-metric-strength'
  | 'program-metric-density'
  | 'program-metric-frequency'
  | 'program-metric-banister'
  | 'program-metric-recovery'
  | 'program-metric-adherence'
  | 'program-metric-rpe',
  ProgramMetricRoute
> = {
  'program-metric-volume': 'volume',
  'program-metric-strength': 'strength',
  'program-metric-density': 'density',
  'program-metric-frequency': 'frequency',
  'program-metric-banister': 'banister',
  'program-metric-recovery': 'recovery',
  'program-metric-adherence': 'adherence',
  'program-metric-rpe': 'rpe',
};

const SIMPLE_TAB_TARGETS: Partial<Record<LegacyView, NavigationTarget>> = {
  home: { screen: 'Home', params: { screen: 'HomeMain' } },
  auth: { screen: 'Home', params: { screen: 'Auth' } },
  achievements: { screen: 'Home', params: { screen: 'Achievements' } },
  'ai-art-studio': { screen: 'Home', params: { screen: 'AIArtStudio' } },
  tasks: { screen: 'Home', params: { screen: 'Tasks' } },
  'social-feed': { screen: 'Home', params: { screen: 'SocialFeed' } },
  recovery: { screen: 'Home', params: { screen: 'Recovery' } },
  sleep: { screen: 'Home', params: { screen: 'Sleep' } },
  settings: { screen: 'Settings' },
  coach: { screen: 'Coach', params: { screen: 'CoachChat' } },
  'my-rings': { screen: 'Rings' },
  nutrition: { screen: 'Nutrition', params: { screen: 'NutritionDashboard' } },
  programs: { screen: 'Workout', params: { screen: 'ProgramsList' } },
  workout: { screen: 'Workout', params: { screen: 'WorkoutMain' } },
  'log-hub': { screen: 'Workout', params: { screen: 'LogHub' } },
  'log-workout': { screen: 'Workout', params: { screen: 'LogWorkout' } },
  'body-progress': { screen: 'Profile', params: { screen: 'BodyProgress' } },
  progress: { screen: 'Profile', params: { screen: 'ProgressOverview' } },
  'athlete-profile': { screen: 'Profile', params: { screen: 'ProfileMain' } },
  'athlete-id': { screen: 'Profile', params: { screen: 'AthleteID' } },
  'body-lab': { screen: 'Profile', params: { screen: 'BodyLab' } },
  'training-purpose': { screen: 'Coach', params: { screen: 'TrainingPurpose' } },
  'food-database': { screen: 'Nutrition', params: { screen: 'FoodDatabase' } },
  'smart-meal-planner': { screen: 'Nutrition', params: { screen: 'MealPlanner' } },
  'food-detail': { screen: 'Nutrition', params: { screen: 'FoodDatabase' } },
  'exercise-database': { screen: 'Workout', params: { screen: 'ExerciseDatabase' } },
  'wiki-home': { screen: 'Wiki', params: { screen: 'WikiHome' } },
  kpkn: { screen: 'Wiki', params: { screen: 'WikiHome' } },
  'wikilab-biomechanics': { screen: 'Wiki', params: { screen: 'WikiBiomechanics' } },
  'mobility-lab': { screen: 'Wiki', params: { screen: 'WikiMobility' } },
};

const EXTERNAL_TARGET_ALIAS_MAP: Record<string, LegacyView> = {
  rings: 'my-rings',
  workout: 'workout',
  'workout-main': 'workout',
  'meal-planner': 'smart-meal-planner',
  'nutrition-log': 'nutrition',
  'wiki-home': 'wiki-home',
  'wiki-biomechanics': 'wikilab-biomechanics',
  'wiki-mobility': 'mobility-lab',
};

function asString(value: unknown) {
  return typeof value === 'string' && value.trim().length > 0 ? value : undefined;
}

function asWikiChainId(value: unknown): WikiChainId | undefined {
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim().toLowerCase();
  if (
    normalized === 'anterior' ||
    normalized === 'posterior' ||
    normalized === 'full' ||
    normalized === 'upper' ||
    normalized === 'lower' ||
    normalized === 'core' ||
    normalized === 'other'
  ) {
    return normalized;
  }
  return undefined;
}

function asProgramMetricRoute(value: unknown): ProgramMetricRoute | undefined {
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim().toLowerCase();
  if (
    normalized === 'volume' ||
    normalized === 'strength' ||
    normalized === 'density' ||
    normalized === 'frequency' ||
    normalized === 'banister' ||
    normalized === 'recovery' ||
    normalized === 'adherence' ||
    normalized === 'rpe'
  ) {
    return normalized;
  }
  return undefined;
}

function decodeRouteSegment(value: string | undefined) {
  if (!value) return undefined;
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function asLegacyView(value: string): LegacyView | undefined {
  if ((SIMPLE_TAB_TARGETS as Record<string, unknown>)[value]) {
    return value as LegacyView;
  }
  if ((EXTERNAL_TARGET_ALIAS_MAP as Record<string, unknown>)[value]) {
    return EXTERNAL_TARGET_ALIAS_MAP[value];
  }
  return undefined;
}

function getNavigateFn() {
  return navigationRef.navigate as unknown as (screen: RootTabKey, params?: RootTabParams) => void;
}

function dispatchTarget(target: NavigationTarget) {
  const navigate = getNavigateFn();
  navigate(target.screen, target.params);
}

function parsePathTarget(normalized: string): NavigationTarget | null {
  const route = normalized.startsWith('/') ? normalized : `/${normalized}`;
  const parts = route.split('/').filter(Boolean).map(segment => decodeRouteSegment(segment) ?? segment);

  if (route === '/auth' || route.startsWith('/auth/')) {
    return { screen: 'Home', params: { screen: 'Auth' } };
  }

  if (route.startsWith('/home')) {
    if (route.startsWith('/home/recovery')) {
      return { screen: 'Home', params: { screen: 'Recovery' } };
    }
    if (route.startsWith('/home/sleep')) {
      return { screen: 'Home', params: { screen: 'Sleep' } };
    }
    if (route.startsWith('/home/tasks')) {
      return { screen: 'Home', params: { screen: 'Tasks' } };
    }
    if (route.startsWith('/home/achievements')) {
      return { screen: 'Home', params: { screen: 'Achievements' } };
    }
    if (route.startsWith('/home/ai-art-studio')) {
      return { screen: 'Home', params: { screen: 'AIArtStudio' } };
    }
    if (route.startsWith('/home/social-feed')) {
      return { screen: 'Home', params: { screen: 'SocialFeed' } };
    }
    return { screen: 'Home', params: { screen: 'HomeMain' } };
  }

  if (route.startsWith('/nutrition')) {
    if (route.startsWith('/nutrition/foods/')) {
      const foodId = asString(parts[2]);
      if (foodId) {
        return { screen: 'Nutrition', params: { screen: 'FoodDetail', params: { foodId } } };
      }
    }
    if (route.startsWith('/nutrition/planner')) {
      return { screen: 'Nutrition', params: { screen: 'MealPlanner' } };
    }
    if (route.startsWith('/nutrition/log')) {
      return { screen: 'Nutrition', params: { screen: 'NutritionLog' } };
    }
    if (route.startsWith('/nutrition/foods')) {
      return { screen: 'Nutrition', params: { screen: 'FoodDatabase' } };
    }
    return { screen: 'Nutrition', params: { screen: 'NutritionDashboard' } };
  }

  if (route.startsWith('/workout')) {
    if (route.startsWith('/workout/log-hub')) {
      return { screen: 'Workout', params: { screen: 'LogHub' } };
    }
    if (route.startsWith('/workout/log-workout')) {
      return { screen: 'Workout', params: { screen: 'LogWorkout' } };
    }
    if (route.startsWith('/workout/exercises/')) {
      const exerciseId = asString(parts[2]);
      if (exerciseId) {
        return { screen: 'Workout', params: { screen: 'ExerciseDetail', params: { exerciseId } } };
      }
    }
    if (route.startsWith('/workout/exercises')) {
      return { screen: 'Workout', params: { screen: 'ExerciseDatabase' } };
    }
    if (route.startsWith('/workout/sessions/')) {
      const sessionId = asString(parts[2]);
      if (sessionId) {
        return { screen: 'Workout', params: { screen: 'SessionDetail', params: { sessionId } } };
      }
    }
    if (route.startsWith('/workout/programs/')) {
      const programId = asString(parts[2]);
      if (programId) {
        if (parts[3] === 'metric') {
          const metric = asProgramMetricRoute(parts[4]);
          if (metric) {
            return {
              screen: 'Workout',
              params: { screen: 'ProgramMetricDetail', params: { programId, metric } },
            };
          }
        }
        if (parts[3] === 'macrocycle') {
          return {
            screen: 'Workout',
            params: { screen: 'MacrocycleEditor', params: { programId } },
          };
        }
        if (parts[3] === 'wizard') {
          return {
            screen: 'Workout',
            params: { screen: 'ProgramWizard', params: { mode: 'create' } },
          };
        }
        if (parts[3] === 'split') {
          return {
            screen: 'Workout',
            params: { screen: 'SplitEditor', params: { programId } },
          };
        }
        return { screen: 'Workout', params: { screen: 'ProgramDetail', params: { programId } } };
      }
    }
    if (route.startsWith('/workout/main')) {
      return { screen: 'Workout', params: { screen: 'WorkoutMain' } };
    }
    if (route.startsWith('/workout/programs')) {
      return { screen: 'Workout', params: { screen: 'ProgramsList' } };
    }
  }

  if (route.startsWith('/profile')) {
    if (route.startsWith('/profile/athlete-id')) {
      return { screen: 'Profile', params: { screen: 'AthleteID' } };
    }
    if (route.startsWith('/profile/personal-records')) {
      return { screen: 'Profile', params: { screen: 'PersonalRecords' } };
    }
    if (route.startsWith('/profile/body-lab')) {
      return { screen: 'Profile', params: { screen: 'BodyLab' } };
    }
    if (route.startsWith('/profile/body')) {
      return { screen: 'Profile', params: { screen: 'BodyProgress' } };
    }
    if (route.startsWith('/profile/progress')) {
      return { screen: 'Profile', params: { screen: 'ProgressOverview' } };
    }
    return { screen: 'Profile', params: { screen: 'ProfileMain' } };
  }

  if (route.startsWith('/wiki')) {
    if (route.startsWith('/wiki/biomechanics')) {
      return { screen: 'Wiki', params: { screen: 'WikiBiomechanics' } };
    }
    if (route.startsWith('/wiki/mobility')) {
      return { screen: 'Wiki', params: { screen: 'WikiMobility' } };
    }
    if (route.startsWith('/wiki/chains/')) {
      const chainId = asWikiChainId(parts[2]);
      return {
        screen: 'Wiki',
        params: { screen: 'WikiChainDetail', params: chainId ? { chainId } : undefined },
      };
    }
    if (route.startsWith('/wiki/body-parts/')) {
      const bodyPartId = asString(parts[2]);
      if (bodyPartId) {
        return { screen: 'Wiki', params: { screen: 'BodyPartDetail', params: { bodyPartId } } };
      }
    }
    if (route.startsWith('/wiki/categories/')) {
      const categoryName = asString(parts[2]);
      if (categoryName) {
        return { screen: 'Wiki', params: { screen: 'MuscleCategory', params: { categoryName } } };
      }
    }
    if (route.startsWith('/wiki/muscles/')) {
      const muscleId = asString(parts[2]);
      if (muscleId) {
        return { screen: 'Wiki', params: { screen: 'WikiMuscleDetail', params: { muscleId } } };
      }
    }
    if (route.startsWith('/wiki/joints/')) {
      const jointId = asString(parts[2]);
      if (jointId) {
        return { screen: 'Wiki', params: { screen: 'WikiJointDetail', params: { jointId } } };
      }
    }
    if (route.startsWith('/wiki/tendons/')) {
      const tendonId = asString(parts[2]);
      if (tendonId) {
        return { screen: 'Wiki', params: { screen: 'WikiTendonDetail', params: { tendonId } } };
      }
    }
    if (route.startsWith('/wiki/patterns/')) {
      const patternId = asString(parts[2]);
      if (patternId) {
        return { screen: 'Wiki', params: { screen: 'WikiPatternDetail', params: { patternId } } };
      }
    }
    return { screen: 'Wiki', params: { screen: 'WikiHome' } };
  }

  if (route.startsWith('/coach')) {
    if (route.startsWith('/coach/training-purpose')) {
      return { screen: 'Coach', params: { screen: 'TrainingPurpose' } };
    }
    return { screen: 'Coach', params: { screen: 'CoachChat' } };
  }

  if (route.startsWith('/settings')) {
    return { screen: 'Settings' };
  }

  if (route.startsWith('/rings')) {
    return { screen: 'Rings' };
  }

  return null;
}

function resolveLegacyView(view: LegacyView, data?: NavigationData): NavigationTarget {
  const staticTarget = SIMPLE_TAB_TARGETS[view];
  if (staticTarget) {
    return staticTarget;
  }

  const activeProgramId = useProgramStore.getState().activeProgramState?.programId;
  const programId = asString(data?.programId) ?? activeProgramId;
  const sessionId = asString(data?.sessionId);
  const exerciseId = asString(data?.exerciseId);
  const chainId = asWikiChainId(data?.chainId);
  const muscleId = asString(data?.muscleGroupId);
  const jointId = asString(data?.jointId);
  const tendonId = asString(data?.tendonId);
  const patternId = asString(data?.movementPatternId);
  const bodyPartId = asString(data?.bodyPartId);
  const categoryName = asString(data?.categoryName);
  const articleType = asString(data?.articleType) as 'muscle' | 'joint' | 'tendon' | 'pattern' | undefined;
  const articleId = asString(data?.articleId);

  switch (view) {
    case 'program-detail':
      return programId
        ? { screen: 'Workout', params: { screen: 'ProgramDetail', params: { programId } } }
        : { screen: 'Workout', params: { screen: 'ProgramsList' } };
    case 'program-editor':
      return programId
        ? { screen: 'Workout', params: { screen: 'MacrocycleEditor', params: { programId } } }
        : { screen: 'Workout', params: { screen: 'ProgramWizard', params: { mode: 'create' } } };
    case 'session-editor':
      return programId
        ? {
            screen: 'Workout',
            params: {
              screen: 'SessionEditor',
              params: {
                programId,
                blockIndex: Number(data?.blockIndex ?? 0),
                mesoIndex: Number(data?.mesoIndex ?? 0),
                weekIndex: Number(data?.weekIndex ?? 0),
                sessionIndex: Number(data?.sessionIndex ?? 0),
                weekId: asString(data?.weekId),
                sessionId: asString(data?.sessionId),
              },
            },
          }
        : { screen: 'Workout', params: { screen: 'ProgramsList' } };
    case 'session-detail':
      return sessionId
        ? { screen: 'Workout', params: { screen: 'SessionDetail', params: { sessionId } } }
        : { screen: 'Workout', params: { screen: 'WorkoutMain' } };
    case 'exercise-detail':
      return exerciseId
        ? { screen: 'Workout', params: { screen: 'ExerciseDetail', params: { exerciseId } } }
        : { screen: 'Workout', params: { screen: 'ExerciseDatabase' } };
    case 'muscle-group-detail':
      return muscleId
        ? { screen: 'Wiki', params: { screen: 'WikiMuscleDetail', params: { muscleId } } }
        : { screen: 'Wiki', params: { screen: 'WikiHome' } };
    case 'joint-detail':
      return jointId
        ? { screen: 'Wiki', params: { screen: 'WikiJointDetail', params: { jointId } } }
        : { screen: 'Wiki', params: { screen: 'WikiHome' } };
    case 'tendon-detail':
      return tendonId
        ? { screen: 'Wiki', params: { screen: 'WikiTendonDetail', params: { tendonId } } }
        : { screen: 'Wiki', params: { screen: 'WikiHome' } };
    case 'movement-pattern-detail':
      return patternId
        ? { screen: 'Wiki', params: { screen: 'WikiPatternDetail', params: { patternId } } }
        : { screen: 'Wiki', params: { screen: 'WikiHome' } };
    case 'chain-detail':
      return {
        screen: 'Wiki',
        params: { screen: 'WikiChainDetail', params: chainId ? { chainId } : undefined },
      };
    case 'body-part-detail':
      return bodyPartId
        ? { screen: 'Wiki', params: { screen: 'BodyPartDetail', params: { bodyPartId } } }
        : { screen: 'Wiki', params: { screen: 'WikiHome' } };
    case 'muscle-category':
      return categoryName
        ? { screen: 'Wiki', params: { screen: 'MuscleCategory', params: { categoryName } } }
        : { screen: 'Wiki', params: { screen: 'WikiHome' } };
    case 'home-card-page':
      return { screen: 'Home', params: { screen: 'HomeMain' } };
    case 'program-metric-volume':
    case 'program-metric-strength':
    case 'program-metric-density':
    case 'program-metric-frequency':
    case 'program-metric-banister':
    case 'program-metric-recovery':
    case 'program-metric-adherence':
    case 'program-metric-rpe': {
      const metric = PROGRAM_METRIC_VIEW_MAP[view];
      return programId
        ? {
            screen: 'Workout',
            params: { screen: 'ProgramMetricDetail', params: { programId, metric } },
          }
        : { screen: 'Workout', params: { screen: 'ProgramsList' } };
    }
    default:
      if (articleType && articleId) {
        return { screen: 'Wiki', params: { screen: 'WikiArticle', params: { articleType, articleId } } };
      }
      return { screen: 'Home', params: { screen: 'HomeMain' } };
  }
}

function decodeExternalTarget(rawTarget: string) {
  const value = rawTarget.trim().toLowerCase();
  if (value.startsWith('kpkn://')) {
    return `/${value.slice('kpkn://'.length)}`;
  }
  return value;
}

export function viewToPath(view: LegacyView, data?: NavigationData): string {
  switch (view) {
    case 'home':
      return '/home';
    case 'auth':
      return '/auth';
    case 'recovery':
      return '/home/recovery';
    case 'sleep':
      return '/home/sleep';
    case 'tasks':
      return '/home/tasks';
    case 'achievements':
      return '/home/achievements';
    case 'ai-art-studio':
      return '/home/ai-art-studio';
    case 'social-feed':
      return '/home/social-feed';
    case 'nutrition':
      return '/nutrition';
    case 'programs':
      return '/workout/programs';
    case 'program-detail':
      return `/workout/programs/${asString(data?.programId) ?? '_'}`;
    case 'program-editor':
      return `/workout/programs/${asString(data?.programId) ?? '_new'}/macrocycle`;
    case 'program-metric-volume':
      return `/workout/programs/${asString(data?.programId) ?? '_'}/metric/volume`;
    case 'program-metric-strength':
      return `/workout/programs/${asString(data?.programId) ?? '_'}/metric/strength`;
    case 'program-metric-density':
      return `/workout/programs/${asString(data?.programId) ?? '_'}/metric/density`;
    case 'program-metric-frequency':
      return `/workout/programs/${asString(data?.programId) ?? '_'}/metric/frequency`;
    case 'program-metric-banister':
      return `/workout/programs/${asString(data?.programId) ?? '_'}/metric/banister`;
    case 'program-metric-recovery':
      return `/workout/programs/${asString(data?.programId) ?? '_'}/metric/recovery`;
    case 'program-metric-adherence':
      return `/workout/programs/${asString(data?.programId) ?? '_'}/metric/adherence`;
    case 'program-metric-rpe':
      return `/workout/programs/${asString(data?.programId) ?? '_'}/metric/rpe`;
    case 'session-editor':
      return '/workout/editor';
    case 'workout':
      return '/workout/main';
    case 'log-workout':
      return '/workout/log-workout';
    case 'session-detail':
      return `/workout/sessions/${asString(data?.sessionId) ?? '_'}`;
    case 'progress':
      return '/profile/progress';
    case 'settings':
      return '/settings';
    case 'coach':
      return '/coach';
    case 'log-hub':
      return '/workout/log-hub';
    case 'wikilab-biomechanics':
      return '/wiki/biomechanics';
    case 'exercise-database':
      return '/workout/exercises';
    case 'exercise-detail':
      return `/workout/exercises/${asString(data?.exerciseId) ?? '_'}`;
    case 'food-database':
      return '/nutrition/foods';
    case 'food-detail':
      return `/nutrition/foods/${asString(data?.foodId) ?? '_'}`;
    case 'smart-meal-planner':
      return '/nutrition/planner';
    case 'training-purpose':
      return '/coach/training-purpose';
    case 'body-progress':
      return '/profile/body';
    case 'body-lab':
      return '/profile/body-lab';
    case 'athlete-id':
      return '/profile/athlete-id';
    case 'athlete-profile':
      return '/profile';
    case 'my-rings':
      return '/rings';
    case 'wiki-home':
    case 'kpkn':
      return '/wiki';
    case 'mobility-lab':
      return '/wiki/mobility';
    case 'chain-detail':
      return `/wiki/chains/${asString(data?.chainId) ?? '_'}`;
    case 'muscle-group-detail':
      return `/wiki/muscles/${asString(data?.muscleGroupId) ?? '_'}`;
    case 'joint-detail':
      return `/wiki/joints/${asString(data?.jointId) ?? '_'}`;
    case 'tendon-detail':
      return `/wiki/tendons/${asString(data?.tendonId) ?? '_'}`;
    case 'movement-pattern-detail':
      return `/wiki/patterns/${asString(data?.movementPatternId) ?? '_'}`;
    case 'body-part-detail':
      return `/wiki/body-parts/${encodeURIComponent(asString(data?.bodyPartId) ?? '_')}`;
    case 'muscle-category':
      return `/wiki/categories/${encodeURIComponent(asString(data?.categoryName) ?? '_')}`;
    default:
      return '/home';
  }
}

export function routerNavigate(view: LegacyView, data?: NavigationData, _replace?: boolean) {
  if (!navigationRef.isReady()) return;
  const target = resolveLegacyView(view, data);
  dispatchTarget(target);
}

export function routerBack() {
  if (!navigationRef.isReady()) return;
  if (navigationRef.canGoBack()) {
    navigationRef.goBack();
  }
}

export function navigateFromExternalTarget(target: string | null | undefined) {
  if (!target || !navigationRef.isReady()) return;
  const decoded = decodeExternalTarget(target);
  const parsedFromPath = parsePathTarget(decoded);
  if (parsedFromPath) {
    dispatchTarget(parsedFromPath);
    return;
  }

  const normalized = decoded.startsWith('/') ? decoded.slice(1) : decoded;
  const view = asLegacyView(normalized);
  if (!view) return;
  const resolved = resolveLegacyView(view);
  dispatchTarget(resolved);
}
