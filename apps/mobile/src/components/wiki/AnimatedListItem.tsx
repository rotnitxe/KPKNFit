import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
  FadeInRight,
} from 'react-native-reanimated';
import { useColors } from '@/theme';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';

interface AnimatedListItemProps {
  title: string;
  subtitle?: string;
  onPress?: () => void;
  icon?: React.ReactNode;
  accent?: 'sky' | 'purple' | 'emerald' | 'amber';
  index?: number;
}

export const AnimatedListItem: React.FC<AnimatedListItemProps> = ({
  title,
  subtitle,
  onPress,
  icon,
  accent = 'sky',
  index = 0,
}) => {
  const colors = useColors();
  const scale = useSharedValue(1);
  const translateX = useSharedValue(0);

  const getAccentColors = (accent: string): string => {
    switch (accent) {
      case 'sky':
        return 'rgba(14, 165, 233, 0.15)';
      case 'purple':
        return 'rgba(168, 85, 247, 0.15)';
      case 'emerald':
        return 'rgba(16, 185, 129, 0.15)';
      case 'amber':
        return 'rgba(251, 191, 36, 0.15)';
      default:
        return colors.surfaceContainerHigh;
    }
  };

  const iconBgColor = getAccentColors(accent);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const chevronAnimatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: translateX.value }],
  }));

  const handlePressIn = () => {
    scale.value = withSpring(0.98, { damping: 15, stiffness: 400 });
    translateX.value = withTiming(4, { duration: 100 });
    ReactNativeHapticFeedback.trigger('selection', {
      enableVibrateFallback: true,
      ignoreAndroidSystemSettings: false,
    });
  };

  const handlePressOut = () => {
    scale.value = withSpring(1, { damping: 15, stiffness: 400 });
    translateX.value = withTiming(0, { duration: 100 });
  };

  return (
    <Animated.View entering={FadeInRight}>
      <Pressable
        onPress={onPress}
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
        accessibilityRole="button"
        accessibilityLabel={subtitle ? `${title}. ${subtitle}` : title}
      >
        <Animated.View
          style={[
            styles.container,
            { borderColor: colors.outlineVariant, backgroundColor: colors.surfaceContainer },
            animatedStyle,
          ]}
        >
          <View style={styles.content}>
            {icon && (
              <View style={[styles.iconContainer, { backgroundColor: iconBgColor }]}>
                {icon}
              </View>
            )}
            <View style={[styles.textContainer, icon ? styles.textContainerWithIcon : null]}>
              <Text style={[styles.title, { color: colors.onSurface }]} numberOfLines={1}>
                {title}
              </Text>
              {subtitle && (
                <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
                  {subtitle}
                </Text>
              )}
            </View>
          </View>
          <Animated.Text
            style={[
              styles.chevron,
              { color: colors.onSurfaceVariant, opacity: 0.3 },
              chevronAnimatedStyle,
            ]}
          >
            ›
          </Animated.Text>
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 10,
  },
  content: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingRight: 12,
  },
  iconContainer: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  textContainer: {
    flex: 1,
  },
  textContainerWithIcon: {
    flex: 1,
  },
  title: {
    fontSize: 15,
    fontWeight: '700',
    lineHeight: 20,
    letterSpacing: -0.2,
    marginBottom: 2,
  },
  subtitle: {
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '500',
    opacity: 0.6,
  },
  chevron: {
    fontSize: 22,
    fontWeight: '700',
  },
});
