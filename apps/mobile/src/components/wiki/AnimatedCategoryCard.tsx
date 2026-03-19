import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { useColors } from '@/theme';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';

interface AnimatedCategoryCardProps {
  title: string;
  subtitle: string;
  count: number;
  accent?: 'sky' | 'purple' | 'emerald' | 'amber';
  onPress: () => void;
  index?: number;
}

interface AccentColors {
  iconBg: string;
  iconText: string;
}

export const AnimatedCategoryCard: React.FC<AnimatedCategoryCardProps> = ({
  title,
  subtitle,
  count,
  accent = 'sky',
  onPress,
}) => {
  const colors = useColors();

  const getAccentColors = (accent: string): AccentColors => {
    switch (accent) {
      case 'sky':
        return {
          iconBg: 'rgba(14, 165, 233, 0.15)',
          iconText: '#0ea5e9',
        };
      case 'purple':
        return {
          iconBg: 'rgba(168, 85, 247, 0.15)',
          iconText: '#a855f7',
        };
      case 'emerald':
        return {
          iconBg: 'rgba(16, 185, 129, 0.15)',
          iconText: '#10b981',
        };
      case 'amber':
        return {
          iconBg: 'rgba(251, 191, 36, 0.15)',
          iconText: '#f59e0b',
        };
      default:
        return {
          iconBg: colors.surfaceContainerHigh,
          iconText: colors.primary,
        };
    }
  };

  const accentColors = getAccentColors(accent);

  const handlePress = () => {
    ReactNativeHapticFeedback.trigger('impactLight', {
      enableVibrateFallback: true,
      ignoreAndroidSystemSettings: false,
    });
    onPress();
  };

  return (
    <Pressable
      onPress={handlePress}
      accessibilityRole="button"
      accessibilityLabel={`${title}. ${subtitle}. ${count} elementos.`}
      style={({ pressed }) => [
        styles.card,
        {
          backgroundColor: colors.surfaceContainer,
          borderColor: colors.outlineVariant,
          opacity: pressed ? 0.7 : 1,
          transform: [{ scale: pressed ? 0.96 : 1 }],
        },
      ]}
    >
      <View style={styles.iconContainer}>
        <View style={[styles.iconCircle, { backgroundColor: accentColors.iconBg }]}>
          <Text style={[styles.iconChar, { color: accentColors.iconText }]}>
            {title.charAt(0).toUpperCase()}
          </Text>
        </View>
      </View>

      <View style={styles.content}>
        <Text style={[styles.title, { color: colors.onSurface }]} numberOfLines={2}>
          {title}
        </Text>
        <Text style={[styles.subtitle, { color: accentColors.iconText }]}>
          {subtitle}
        </Text>
        {count !== undefined && (
          <Text style={[styles.countText, { color: colors.onSurfaceVariant }]}>
            {count} elementos
          </Text>
        )}
      </View>

      <View style={[styles.footer, { borderColor: colors.outlineVariant }]}>
        <Text style={[styles.footerText, { color: colors.onSurfaceVariant }]}>
          Explorar
        </Text>
        <Text style={[styles.chevron, { color: accentColors.iconText }]}>›</Text>
      </View>
    </Pressable>
  );
};

const styles = StyleSheet.create({
  card: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 20,
    marginBottom: 12,
  },
  iconContainer: {
    marginBottom: 16,
  },
  iconCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconChar: {
    fontSize: 24,
    fontWeight: '800',
  },
  content: {
    flex: 1,
    marginBottom: 16,
  },
  title: {
    fontSize: 17,
    fontWeight: '800',
    lineHeight: 22,
    letterSpacing: -0.5,
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 9,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 2,
    opacity: 0.6,
    marginBottom: 4,
  },
  countText: {
    fontSize: 11,
    fontWeight: '600',
    opacity: 0.5,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 12,
    borderTopWidth: 1,
  },
  footerText: {
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
    opacity: 0.5,
  },
  chevron: {
    fontSize: 20,
    fontWeight: '700',
  },
});
