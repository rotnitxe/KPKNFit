import React from 'react';
import { NavigationContainer, NavigatorScreenParams } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { HomeScreen } from '../screens/HomeScreen';
import { RecoveryScreen } from '../screens/Home/RecoveryScreen';
import { SleepScreen } from '../screens/Home/SleepScreen';
import { TasksScreen } from '../screens/Home/TasksScreen';
import { AchievementsScreen } from '../screens/Home/AchievementsScreen';
import { AIArtStudioScreen } from '../screens/Home/AIArtStudioScreen';
import { SocialFeedScreen } from '../screens/Home/SocialFeedScreen';
import { AuthScreen } from '../screens/Home/AuthScreen';
import { RingsScreen } from '../screens/Rings/RingsScreen';
import { ProfileScreen } from '../screens/Profile/ProfileScreen';
import { NutritionLogScreen } from '../screens/Nutrition/NutritionLogScreen';
import { NutritionDashboardScreen } from '../screens/Nutrition/NutritionDashboardScreen';
import { MealPlannerScreen } from '../screens/Nutrition/MealPlannerScreen';
import { FoodDatabaseScreen } from '../screens/Nutrition/FoodDatabaseScreen';
import { FoodDetailScreen } from '../screens/Nutrition/FoodDetailScreen';
import { WorkoutScreen } from '../screens/Workout/WorkoutScreen';
import { SessionDetailScreen } from '../screens/Workout/SessionDetailScreen';
import { LogHubScreen } from '../screens/Workout/LogHubScreen';
import { LogWorkoutScreen } from '../screens/Workout/LogWorkoutScreen';
import { ProgressScreen } from '../screens/Progress/ProgressScreen';
import { BodyProgressScreen } from '../screens/Progress/BodyProgressScreen';
import { SettingsScreen } from '../screens/Settings/SettingsScreen';
import { ProgramsScreen } from '../screens/Programs/ProgramsScreen';
import { ProgramDetailScreen } from '../screens/Workout/ProgramDetailScreen';
import { ActiveSessionScreen } from '../screens/Workout/ActiveSessionScreen';
import { SessionEditorScreen } from '../screens/Workout/SessionEditorScreen';
import { ExerciseDatabaseScreen } from '../screens/Exercise/ExerciseDatabaseScreen';
import { ExerciseDetailScreen } from '../screens/Exercise/ExerciseDetailScreen';
import { WikiHomeScreen } from '../screens/Wiki/WikiHomeScreen';
import { BodyPartDetailScreen } from '../screens/Wiki/BodyPartDetailScreen';
import { MuscleCategoryScreen } from '../screens/Wiki/MuscleCategoryScreen';
import { WikiMuscleDetailScreen } from '../screens/Wiki/WikiMuscleDetailScreen';
import { WikiJointDetailScreen } from '../screens/Wiki/WikiJointDetailScreen';
import { WikiTendonDetailScreen } from '../screens/Wiki/WikiTendonDetailScreen';
import { WikiPatternDetailScreen } from '../screens/Wiki/WikiPatternDetailScreen';
import { WikiBiomechanicsScreen } from '../screens/Wiki/WikiBiomechanicsScreen';
import { WikiMobilityScreen } from '../screens/Wiki/WikiMobilityScreen';
import { WikiChainDetailScreen } from '../screens/Wiki/WikiChainDetailScreen';
import { WikiArticleScreen } from '../screens/Wiki/WikiArticleScreen';
import { CoachChatScreen } from '../screens/Coach/CoachChatScreen';
import { TrainingPurposeScreen } from '../screens/Coach/TrainingPurposeScreen';
import { AthleteIDScreen } from '../screens/Profile/AthleteIDScreen';
import { PersonalRecordsScreen } from '../screens/Profile/PersonalRecordsScreen';
import { BodyLabScreen } from '../screens/Profile/BodyLabScreen';
import { navigationRef } from './navigationRef';
import { KpknBottomBar } from '../components/navigation/KpknBottomBar';

import {
  HomeStackParamList,
  NutritionStackParamList,
  WorkoutStackParamList,
  ProfileStackParamList,
  WikiStackParamList,
  CoachStackParamList,
  RootTabParamList,
} from './types';
import { ProgramWizardScreen } from '../screens/Workout/ProgramWizardScreen';
import { SplitEditorScreen } from '../screens/Workout/SplitEditorScreen';
import { MacrocycleEditorScreen } from '../screens/Workout/MacrocycleEditorScreen';
import { ProgramMetricDetailScreen } from '../screens/Workout/ProgramMetricDetailScreen';

const HomeStack = createNativeStackNavigator<HomeStackParamList>();
const NutritionStack = createNativeStackNavigator<NutritionStackParamList>();

const WorkoutStack = createNativeStackNavigator<WorkoutStackParamList>();
const ProfileStack = createNativeStackNavigator<ProfileStackParamList>();
const WikiStack = createNativeStackNavigator<WikiStackParamList>();
const CoachStack = createNativeStackNavigator<CoachStackParamList>();
const Tab = createBottomTabNavigator<RootTabParamList>();

function HomeStackScreen() {
  return (
    <HomeStack.Navigator
      id="HomeStack"
      initialRouteName="HomeMain"
      screenOptions={{ headerShown: false }}
    >
      <HomeStack.Screen name="HomeMain" component={HomeScreen} />
      <HomeStack.Screen name="Recovery" component={RecoveryScreen} />
      <HomeStack.Screen name="Sleep" component={SleepScreen} />
      <HomeStack.Screen name="Tasks" component={TasksScreen} />
      <HomeStack.Screen name="Achievements" component={AchievementsScreen} />
      <HomeStack.Screen name="AIArtStudio" component={AIArtStudioScreen} />
      <HomeStack.Screen name="SocialFeed" component={SocialFeedScreen} />
      <HomeStack.Screen name="Auth" component={AuthScreen} />
    </HomeStack.Navigator>
  );
}

function NutritionStackScreen() {
  return (
    <NutritionStack.Navigator id="NutritionStack" screenOptions={{ headerShown: false }}>
      <NutritionStack.Screen name="NutritionDashboard" component={NutritionDashboardScreen} />
      <NutritionStack.Screen name="MealPlanner" component={MealPlannerScreen} />
      <NutritionStack.Screen name="NutritionLog" component={NutritionLogScreen} />
      <NutritionStack.Screen name="FoodDatabase" component={FoodDatabaseScreen} />
      <NutritionStack.Screen name="FoodDetail" component={FoodDetailScreen} />
    </NutritionStack.Navigator>
  );
}

function WorkoutStackScreen() {
  return (
    <WorkoutStack.Navigator
      id="WorkoutStack"
      initialRouteName="ProgramsList"
      screenOptions={{ headerShown: false }}
    >
      <WorkoutStack.Screen name="ProgramsList" component={ProgramsScreen} />
      <WorkoutStack.Screen name="WorkoutMain" component={WorkoutScreen} />
      <WorkoutStack.Screen name="LogHub" component={LogHubScreen} />
      <WorkoutStack.Screen name="LogWorkout" component={LogWorkoutScreen} />
      <WorkoutStack.Screen name="SessionDetail" component={SessionDetailScreen} />
      <WorkoutStack.Screen name="ProgramDetail" component={ProgramDetailScreen} />
      <WorkoutStack.Screen name="ActiveSession" component={ActiveSessionScreen} />
      <WorkoutStack.Screen name="SessionEditor" component={SessionEditorScreen} />
      <WorkoutStack.Screen name="ProgramWizard" component={ProgramWizardScreen} />
      <WorkoutStack.Screen name="ProgramMetricDetail" component={ProgramMetricDetailScreen} />
      <WorkoutStack.Screen name="SplitEditor" component={SplitEditorScreen} />
      <WorkoutStack.Screen name="MacrocycleEditor" component={MacrocycleEditorScreen} />
      <WorkoutStack.Screen name="ExerciseDatabase" component={ExerciseDatabaseScreen} />
      <WorkoutStack.Screen name="ExerciseDetail" component={ExerciseDetailScreen} />
      <WorkoutStack.Screen name="WikiHome" component={WikiHomeScreen} />
      <WorkoutStack.Screen name="WikiArticle" component={WikiArticleScreen} />
      <WorkoutStack.Screen name="WikiMuscleDetail" component={WikiMuscleDetailScreen} />
      <WorkoutStack.Screen name="WikiJointDetail" component={WikiJointDetailScreen} />
      <WorkoutStack.Screen name="WikiTendonDetail" component={WikiTendonDetailScreen} />
      <WorkoutStack.Screen name="WikiPatternDetail" component={WikiPatternDetailScreen} />
      <WorkoutStack.Screen name="WikiBiomechanics" component={WikiBiomechanicsScreen} />
      <WorkoutStack.Screen name="WikiMobility" component={WikiMobilityScreen} />
      <WorkoutStack.Screen name="WikiChainDetail" component={WikiChainDetailScreen} />
    </WorkoutStack.Navigator>
  );
}

function ProfileStackScreen() {
  return (
    <ProfileStack.Navigator id="ProfileStack" screenOptions={{ headerShown: false }}>
      <ProfileStack.Screen name="ProfileMain" component={ProfileScreen} />
      <ProfileStack.Screen name="AthleteID" component={AthleteIDScreen} />
      <ProfileStack.Screen name="PersonalRecords" component={PersonalRecordsScreen} />
      <ProfileStack.Screen name="BodyLab" component={BodyLabScreen} />
      <ProfileStack.Screen name="BodyProgress" component={BodyProgressScreen} />
      <ProfileStack.Screen name="ProgressOverview" component={ProgressScreen} />
    </ProfileStack.Navigator>
  );
}

function WikiStackScreen() {
  return (
    <WikiStack.Navigator id="WikiStack" screenOptions={{ headerShown: false }}>
      <WikiStack.Screen name="WikiHome" component={WikiHomeScreen} />
      <WikiStack.Screen name="WikiArticle" component={WikiArticleScreen} />
      <WikiStack.Screen name="WikiBiomechanics" component={WikiBiomechanicsScreen} />
      <WikiStack.Screen name="WikiMobility" component={WikiMobilityScreen} />
      <WikiStack.Screen name="WikiChainDetail" component={WikiChainDetailScreen} />
      <WikiStack.Screen name="WikiMuscleDetail" component={WikiMuscleDetailScreen} />
      <WikiStack.Screen name="WikiJointDetail" component={WikiJointDetailScreen} />
      <WikiStack.Screen name="WikiTendonDetail" component={WikiTendonDetailScreen} />
      <WikiStack.Screen name="WikiPatternDetail" component={WikiPatternDetailScreen} />
      <WikiStack.Screen name="BodyPartDetail" component={BodyPartDetailScreen} />
      <WikiStack.Screen name="MuscleCategory" component={MuscleCategoryScreen} />
      <WikiStack.Screen name="ExerciseDatabase" component={ExerciseDatabaseScreen} />
      <WikiStack.Screen name="ExerciseDetail" component={ExerciseDetailScreen} />
    </WikiStack.Navigator>
  );
}

function CoachStackScreen() {
  return (
    <CoachStack.Navigator id="CoachStack" screenOptions={{ headerShown: false }}>
      <CoachStack.Screen name="CoachChat" component={CoachChatScreen} />
      <CoachStack.Screen name="TrainingPurpose" component={TrainingPurposeScreen} />
    </CoachStack.Navigator>
  );
}

const linking = {
  prefixes: ['kpkn://'] as string[],
  config: {
    screens: {
      Rings: 'rings',
      Home: {
        screens: {
          HomeMain: 'home',
          Recovery: 'home/recovery',
          Sleep: 'home/sleep',
          Tasks: 'home/tasks',
          Achievements: 'home/achievements',
          AIArtStudio: 'home/ai-art-studio',
          SocialFeed: 'home/social-feed',
          Auth: 'auth',
        },
      },
      Workout: {
        screens: {
          ProgramsList: 'workout/programs',
          WorkoutMain: 'workout/main',
          LogHub: 'workout/log-hub',
          LogWorkout: 'workout/log-workout',
          SessionDetail: 'workout/sessions/:sessionId',
          ProgramDetail: 'workout/programs/:programId',
          ActiveSession: 'workout/active',
          SessionEditor: 'workout/editor',
          ProgramWizard: 'workout/programs/:programId?/wizard',
          SplitEditor: 'workout/programs/:programId/split',
          MacrocycleEditor: 'workout/programs/:programId/macrocycle',
          ProgramMetricDetail: 'workout/programs/:programId/metric/:metric',
          ExerciseDatabase: 'workout/exercises',
          ExerciseDetail: 'workout/exercises/:exerciseId',
        },
      },
      Nutrition: {
        screens: {
          NutritionDashboard: 'nutrition',
          MealPlanner: 'nutrition/planner',
          NutritionLog: 'nutrition/log',
          FoodDatabase: 'nutrition/foods',
          FoodDetail: 'nutrition/foods/:foodId',
        },
      },
      Profile: {
        screens: {
          ProfileMain: 'profile',
          AthleteID: 'profile/athlete-id',
          PersonalRecords: 'profile/personal-records',
          BodyLab: 'profile/body-lab',
          BodyProgress: 'profile/body',
          ProgressOverview: 'profile/progress',
        },
      },
        Wiki: {
          screens: {
            WikiHome: 'wiki',
            WikiArticle: 'wiki/article/:articleType/:articleId',
            WikiBiomechanics: 'wiki/biomechanics',
          WikiMobility: 'wiki/mobility',
          WikiChainDetail: 'wiki/chains/:chainId?',
          BodyPartDetail: 'wiki/body-parts/:bodyPartId',
          MuscleCategory: 'wiki/categories/:categoryName',
          WikiMuscleDetail: 'wiki/muscles/:muscleId',
          WikiJointDetail: 'wiki/joints/:jointId',
          WikiTendonDetail: 'wiki/tendons/:tendonId',
          WikiPatternDetail: 'wiki/patterns/:patternId',
          ExerciseDatabase: 'wiki/exercises',
          ExerciseDetail: 'wiki/exercises/:exerciseId',
        },
      },
      Settings: 'settings',
      Coach: {
        screens: {
          CoachChat: 'coach',
          TrainingPurpose: 'coach/training-purpose',
        },
      },
    },
  },
};

export function AppNavigator() {
  return (
    <NavigationContainer ref={navigationRef} linking={linking}>
      <Tab.Navigator
        id="RootTabs"
        initialRouteName="Home"
        screenOptions={{
          headerShown: false,
          tabBarStyle: {
            backgroundColor: 'transparent',
            borderTopWidth: 0,
            elevation: 0,
          },
          tabBarBackground: () => null,
        }}
        tabBar={props => <KpknBottomBar {...props} />}
      >
        <Tab.Screen name="Rings" component={RingsScreen} />
        <Tab.Screen name="Workout" component={WorkoutStackScreen} />
        <Tab.Screen name="Home" component={HomeStackScreen} />
        <Tab.Screen name="Nutrition" component={NutritionStackScreen} />
        <Tab.Screen name="Profile" component={ProfileStackScreen} />
        <Tab.Screen name="Wiki" component={WikiStackScreen} />
        <Tab.Screen name="Settings" component={SettingsScreen} />
        <Tab.Screen
          name="Coach"
          component={CoachStackScreen}
          options={{ tabBarButton: () => null }}
        />
      </Tab.Navigator>
    </NavigationContainer>
  );
}
