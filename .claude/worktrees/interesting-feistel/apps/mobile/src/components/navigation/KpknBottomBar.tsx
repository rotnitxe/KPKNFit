import React from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import type { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  DumbbellIcon,
  MealIcon,
  KpknLogoIcon,
  UserBadgeIcon,
  WikiLabIcon,
} from '@/components/icons';
import { useColors, useTheme } from '@/theme';
import { useSettingsStore } from '@/stores/settingsStore';

type VisibleRouteName =
  | 'Workout'
  | 'Nutrition'
  | 'Profile'
  | 'Wiki';

type TabConfig = {
  label: string;
  icon: React.ComponentType<{ size?: number; color?: string }>;
};

const LEFT_TABS: VisibleRouteName[] = ['Workout', 'Nutrition'];
const RIGHT_TABS: VisibleRouteName[] = ['Profile', 'Wiki'];

const TAB_CONFIG: Record<VisibleRouteName, TabConfig> = {
  Workout: { label: 'Entrenar', icon: DumbbellIcon },
  Nutrition: { label: 'Nutrición', icon: MealIcon },
  Profile: { label: 'Mi Perfil', icon: UserBadgeIcon },
  Wiki: { label: 'WikiLab', icon: WikiLabIcon },
};

function TabButton({
  label,
  active,
  onPress,
  Icon,
  compact = false,
  testID,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
  Icon: TabConfig['icon'];
  compact?: boolean;
  testID?: string;
}) {
  const colors = useColors();
  const { isDark } = useTheme();

  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ selected: active }}
      onPress={onPress}
      style={styles.navButton}
      hitSlop={8}
    >
      <View style={[styles.iconWrap, compact && styles.iconWrapCompact]}>
        <Icon
          size={compact ? 18 : 20}
          color={active ? (isDark ? '#FFFFFF' : '#111111') : colors.onSurfaceVariant}
        />
      </View>
      <View
        style={[
          styles.indicator,
          { backgroundColor: active ? (isDark ? colors.primary : '#161616') : 'transparent' },
        ]}
      />
    </Pressable>
  );
}

export function KpknBottomBar({ state, navigation }: BottomTabBarProps) {
  const colors = useColors();
  const { isDark } = useTheme();
  const insets = useSafeAreaInsets();
  const currentRoute = state.routes[state.index]?.name;
  const isCompact = useSettingsStore(state => state.summary?.tabBarStyle === 'compact');

  const navigateTo = (routeName: string) => {
    const route = state.routes.find(item => item.name === routeName);
    if (!route) return;

    const event = navigation.emit({
      type: 'tabPress',
      target: route.key,
      canPreventDefault: true,
    });

    if (!event.defaultPrevented) {
      navigation.navigate(routeName as never);
    }
  };

  const isHomeActive = currentRoute === 'Home';

  return (
    <View
      pointerEvents="box-none"
      style={[styles.outer, { paddingBottom: Math.max(insets.bottom - 2, 8) }]}
    >
      <View
        accessibilityRole="tablist"
        accessibilityLabel="Navegación principal"
        style={[
          styles.shell,
          isCompact && styles.shellCompact,
          {
            backgroundColor: isDark ? colors.surfaceContainer : colors.surfaceContainer,
            borderTopColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
          },
        ]}
      >
        <View style={styles.sideGroup}>
          {LEFT_TABS.map(routeName => {
            const config = TAB_CONFIG[routeName];
            return (
              <TabButton
                key={routeName}
                testID={`nav-${routeName.toLowerCase()}`}
                label={config.label}
                Icon={config.icon}
                active={currentRoute === routeName}
                compact={isCompact}
                onPress={() => navigateTo(routeName)}
              />
            );
          })}
        </View>

        <Pressable
          testID="nav-home"
          accessibilityRole="button"
          accessibilityLabel="Inicio"
          accessibilityState={{ selected: isHomeActive }}
          onPress={() => navigateTo('Home')}
          style={styles.homeButton}
          hitSlop={8}
        >
          <View
            style={[
              styles.homePlate,
              isCompact && styles.homePlateCompact,
              {
                backgroundColor: isDark
                  ? (isHomeActive ? 'rgba(255,255,255,0.12)' : 'rgba(255,255,255,0.06)')
                  : (isHomeActive ? 'rgba(0,0,0,0.08)' : 'rgba(0,0,0,0.04)'),
              },
            ]}
          >
            <KpknLogoIcon
              size={isCompact ? 28 : 32}
              color={isHomeActive ? (isDark ? '#FFFFFF' : '#111111') : (isDark ? 'rgba(255,255,255,0.4)' : '#404040')}
            />
          </View>
          <View
            style={[
              styles.homeIndicator,
              { backgroundColor: isHomeActive ? (isDark ? colors.primary : '#161616') : 'transparent' },
            ]}
          />
        </Pressable>

        <View style={styles.sideGroup}>
          {RIGHT_TABS.map(routeName => {
            const config = TAB_CONFIG[routeName];
            return (
              <TabButton
                key={routeName}
                testID={`nav-${routeName.toLowerCase()}`}
                label={config.label}
                Icon={config.icon}
                active={currentRoute === routeName}
                compact={isCompact}
                onPress={() => navigateTo(routeName)}
              />
            );
          })}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  outer: {
    paddingHorizontal: 0,
    paddingTop: 0,
    paddingBottom: 0,
    backgroundColor: 'transparent',
  },
  shell: {
    minHeight: 64,
    borderTopWidth: 1,
    paddingHorizontal: 8,
    paddingTop: 8,
    paddingBottom: 4,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
  },
  shellCompact: {
    minHeight: 56,
    paddingTop: 6,
    paddingBottom: 2,
  },
  sideGroup: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-evenly',
  },
  navButton: {
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 44,
  },
  iconWrap: {
    width: 34,
    height: 26,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconWrapCompact: {
    width: 32,
    height: 24,
  },
  indicator: {
    width: 22,
    height: 3,
    borderRadius: 99,
    marginTop: 1,
  },
  homeButton: {
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 1,
  },
  homePlate: {
    width: 68,
    height: 56,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  homePlateCompact: {
    width: 64,
    height: 52,
    borderRadius: 20,
  },
  homeIndicator: {
    width: 34,
    height: 3,
    borderRadius: 99,
    marginTop: 1,
  },
});
