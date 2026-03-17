import { NavigatorScreenParams } from '@react-navigation/native';

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
  SessionDetail: { sessionId: string };
  ProgramDetail: { programId: string };
  ActiveSession: { programId: string; sessionId: string; sessionName: string };
  SessionEditor: { programId: string; weekId: string; sessionId: string };
  ProgramWizard: { mode: 'create' | 'edit'; programId?: string };
  SplitEditor: { programId: string };
  MacrocycleEditor: { programId: string };
  ExerciseDatabase: undefined;
  ExerciseDetail: { exerciseId: string };
  WikiHome: undefined;
  WikiMuscleDetail: { muscleId: string };
  WikiJointDetail: { jointId: string };
  WikiTendonDetail: { tendonId: string };
  WikiPatternDetail: { patternId: string };
};

export type ProfileStackParamList = {
  ProfileMain: undefined;
  BodyProgress: undefined;
  ProgressOverview: undefined;
};

export type WikiStackParamList = {
  WikiHome: undefined;
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
