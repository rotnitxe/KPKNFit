import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Text } from 'react-native';
import { HomeScreen } from '../screens/HomeScreen';
import { NutritionLogScreen } from '../screens/Nutrition/NutritionLogScreen';

export type RootTabParamList = {
  Home: undefined;
  Nutrition: undefined;
};

const Tab = createBottomTabNavigator<RootTabParamList>();

export function AppNavigator() {
  return (
    <NavigationContainer>
      <Tab.Navigator
        screenOptions={{
          headerShown: false,
          tabBarStyle: {
            backgroundColor: '#121622',
            borderTopColor: 'rgba(255,255,255,0.08)',
            height: 68,
            paddingBottom: 10,
            paddingTop: 10,
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
          name="Nutrition"
          component={NutritionLogScreen}
          options={{
            tabBarLabel: ({ color }) => <Text style={{ color, fontSize: 12 }}>Nutrición</Text>,
          }}
        />
      </Tab.Navigator>
    </NavigationContainer>
  );
}
