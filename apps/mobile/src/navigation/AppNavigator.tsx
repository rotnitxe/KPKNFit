import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Text } from 'react-native';
import { HomeScreen } from '../screens/HomeScreen';
import { NutritionLogScreen } from '../screens/Nutrition/NutritionLogScreen';
import { WorkoutScreen } from '../screens/Workout/WorkoutScreen';
import { ProgressScreen } from '../screens/Progress/ProgressScreen';
import { SettingsScreen } from '../screens/Settings/SettingsScreen';
import { navigationRef } from './navigationRef';

export type RootTabParamList = {
  Home: undefined;
  Workout: undefined;
  Nutrition: undefined;
  Progress: undefined;
  Settings: undefined;
};

const Tab = createBottomTabNavigator<RootTabParamList>();

const linking = {
  prefixes: ['kpkn://'] as string[],
  config: {
    screens: {
      Home: 'home',
      Workout: 'workout',
      Nutrition: 'nutrition',
      Progress: 'progress',
      Settings: 'settings',
    },
  },
};

export function AppNavigator() {
  return (
    <NavigationContainer ref={navigationRef} linking={linking}>
      <Tab.Navigator
        screenOptions={{
          headerShown: false,
          tabBarStyle: {
            backgroundColor: '#121622',
            borderTopColor: 'rgba(255,255,255,0.08)',
            height: 72,
            paddingBottom: 10,
            paddingTop: 8,
          },
          tabBarActiveTintColor: '#00F0FF',
          tabBarInactiveTintColor: '#A7B0C3',
        }}
      >
        <Tab.Screen
          name="Home"
          component={HomeScreen}
          options={{
            tabBarLabel: ({ color }) => <Text style={{ color, fontSize: 12 }}>Inicio</Text>,
          }}
        />
        <Tab.Screen
          name="Workout"
          component={WorkoutScreen}
          options={{
            tabBarLabel: ({ color }) => <Text style={{ color, fontSize: 12 }}>Entreno</Text>,
          }}
        />
        <Tab.Screen
          name="Nutrition"
          component={NutritionLogScreen}
          options={{
            tabBarLabel: ({ color }) => <Text style={{ color, fontSize: 12 }}>Nutrición</Text>,
          }}
        />
        <Tab.Screen
          name="Progress"
          component={ProgressScreen}
          options={{
            tabBarLabel: ({ color }) => <Text style={{ color, fontSize: 12 }}>Progreso</Text>,
          }}
        />
        <Tab.Screen
          name="Settings"
          component={SettingsScreen}
          options={{
            tabBarLabel: ({ color }) => <Text style={{ color, fontSize: 12 }}>Ajustes</Text>,
          }}
        />
      </Tab.Navigator>
    </NavigationContainer>
  );
}
