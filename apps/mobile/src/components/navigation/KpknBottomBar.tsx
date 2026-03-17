import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  DumbbellIcon,
  KpknLogoIcon,
  PlateIcon,
  SettingsIcon,
  TripleRingsIcon,
  UserBadgeIcon,
  WikiLabIcon,
} from '@/components/icons';
import { useColors } from '@/theme';

type VisibleRouteName =
  | 'Rings'
  | 'Workout'
  | 'Nutrition'
  | 'Profile'
  | 'Wiki'
  | 'Settings';

type TabConfig = {
  label: string;
  icon: React.ComponentType<{ size?: number; color?: string }>;
};

const LEFT_TABS: VisibleRouteName[] = ['Rings', 'Workout', 'Nutrition'];
const RIGHT_TABS: VisibleRouteName[] = ['Profile', 'Wiki', 'Settings'];

const TAB_CONFIG: Record<VisibleRouteName, TabConfig> = {
  Rings: { label: 'RINGS', icon: TripleRingsIcon },
  Workout: { label: 'ENTRENAR', icon: DumbbellIcon },
  Nutrition: { label: 'NUTRICION', icon: PlateIcon },
  Profile: { label: 'PERFIL', icon: UserBadgeIcon },
  Wiki: { label: 'WIKILAB', icon: WikiLabIcon },
  Settings: { label: 'AJUSTES', icon: SettingsIcon },
};

function TabButton({
  label,
  active,
  onPress,
  Icon,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
  Icon: TabConfig['icon'];
}) {
  const colors = useColors();

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      style={styles.navButton}
    >
      <View
        style={[
          styles.iconWrap,
          active && {
            backgroundColor: colors.secondaryContainer,
            borderColor: colors.secondary,
          },
        ]}
      >
        <Icon
          size={18}
          color={active ? colors.onSecondaryContainer : colors.onSurfaceVariant}
        />
      </View>
      <Text
        style={[
          styles.label,
          { color: active ? colors.onSurface : colors.onSurfaceVariant },
        ]}
      >
        {label}
      </Text>
      <View
        style={[
          styles.indicator,
          { backgroundColor: active ? colors.primary : 'transparent' },
        ]}
      />
    </Pressable>
  );
}

export function KpknBottomBar({ state, navigation }: BottomTabBarProps) {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const currentRoute = state.routes[state.index]?.name;

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

  return (
    <View
      pointerEvents="box-none"
      style={[styles.outer, { paddingBottom: Math.max(insets.bottom, 12) }]}
    >
      <View
        style={[
          styles.shell,
          {
            backgroundColor: 'rgba(21, 23, 30, 0.92)',
            borderColor: colors.outlineVariant,
            shadowColor: '#000000',
          },
        ]}
      >
        <View style={styles.sideGroup}>
          {LEFT_TABS.map(routeName => {
            const config = TAB_CONFIG[routeName];
            return (
              <TabButton
                key={routeName}
                label={config.label}
                Icon={config.icon}
                active={currentRoute === routeName}
                onPress={() => navigateTo(routeName)}
              />
            );
          })}
        </View>

        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Inicio"
          onPress={() => navigateTo('Home')}
          style={styles.homeButton}
        >
          <View
            style={[
              styles.homeOrb,
              {
                backgroundColor:
                  currentRoute === 'Home' ? colors.primaryContainer : colors.surfaceContainer,
                borderColor: currentRoute === 'Home' ? colors.primary : colors.outlineVariant,
              },
            ]}
          >
            <KpknLogoIcon
              size={28}
              color={currentRoute === 'Home' ? colors.primary : colors.onSurface}
            />
          </View>
          <Text
            style={[
              styles.homeLabel,
              { color: currentRoute === 'Home' ? colors.onSurface : colors.onSurfaceVariant },
            ]}
          >
            INICIO
          </Text>
        </Pressable>

        <View style={styles.sideGroup}>
          {RIGHT_TABS.map(routeName => {
            const config = TAB_CONFIG[routeName];
            return (
              <TabButton
                key={routeName}
                label={config.label}
                Icon={config.icon}
                active={currentRoute === routeName}
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
    paddingHorizontal: 12,
    paddingTop: 10,
    backgroundColor: 'transparent',
  },
  shell: {
    minHeight: 92,
    borderRadius: 32,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    shadowOpacity: 0.22,
    shadowRadius: 22,
    shadowOffset: { width: 0, height: 12 },
    elevation: 14,
  },
  sideGroup: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-evenly',
  },
  navButton: {
    alignItems: 'center',
    justifyContent: 'flex-end',
    minWidth: 54,
  },
  iconWrap: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'transparent',
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    marginTop: 6,
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 1.1,
  },
  indicator: {
    width: 18,
    height: 3,
    borderRadius: 99,
    marginTop: 6,
  },
  homeButton: {
    alignItems: 'center',
    justifyContent: 'flex-end',
    marginHorizontal: 4,
  },
  homeOrb: {
    width: 58,
    height: 58,
    borderRadius: 29,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  homeLabel: {
    marginTop: 6,
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.4,
  },
});
