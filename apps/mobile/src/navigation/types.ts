import { NavigatorScreenParams } from '@react-navigation/native';
import type { WikiChainId } from '@/data/wikiExploreData';

export type NutritionStackParamList = {
  NutritionDashboard: undefined;
  MealPlanner: undefined;
  NutritionLog: undefined;
  FoodDatabase: undefined;
  FoodDetail: { foodId: string };
};

export type WorkoutStackParamList = {
  ProgramsList: undefined;
  WorkoutMain: undefined;
  LogHub: undefined;
  LogWorkout: undefined;
  SessionDetail: { sessionId: string };
  ProgramDetail: { programId: string };
  ActiveSession: { programId: string; sessionId: string; sessionName: string };
  SessionEditor: { programId: string; blockIndex?: number; mesoIndex?: number; weekIndex?: number; sessionIndex?: number; weekId?: string; sessionId?: string };
  ProgramWizard: { mode: 'create' | 'edit'; programId?: string };
  SplitEditor: { programId: string };
  MacrocycleEditor: { programId: string };
  ExerciseDatabase: undefined;
  ExerciseDetail: { exerciseId: string };
  WikiHome: undefined;
  WikiArticle: { articleType: 'muscle' | 'joint' | 'tendon' | 'pattern'; articleId: string };
  WikiMuscleDetail: { muscleId: string };
  WikiJointDetail: { jointId: string };
  WikiTendonDetail: { tendonId: string };
  WikiPatternDetail: { patternId: string };
  WikiBiomechanics: undefined;
  WikiMobility: undefined;
  WikiChainDetail: { chainId?: WikiChainId } | undefined;
};

export type ProfileStackParamList = {
  ProfileMain: undefined;
  BodyProgress: undefined;
  ProgressOverview: undefined;
};

export type WikiStackParamList = {
  WikiBiomechanics: undefined;
  WikiMobility: undefined;
  WikiChainDetail: { chainId?: WikiChainId } | undefined;
  WikiHome: undefined;
  WikiArticle: { articleType: 'muscle' | 'joint' | 'tendon' | 'pattern'; articleId: string };
  WikiMuscleDetail: { muscleId: string };
  WikiJointDetail: { jointId: string };
  WikiTendonDetail: { tendonId: string };
  WikiPatternDetail: { patternId: string };
  ExerciseDatabase: undefined;
  ExerciseDetail: { exerciseId: string };
};

export type RootTabParamList = {
  Rings: undefined;
  Workout: NavigatorScreenParams<WorkoutStackParamList> | undefined;
  Home: undefined;
  Nutrition: NavigatorScreenParams<NutritionStackParamList> | undefined;
  Profile: NavigatorScreenParams<ProfileStackParamList> | undefined;
  Wiki: NavigatorScreenParams<WikiStackParamList> | undefined;
  Settings: undefined;
  Coach: undefined;
};
