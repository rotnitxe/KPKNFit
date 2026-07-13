import React from 'react';
import { TouchableOpacity, Text, View, StyleSheet } from 'react-native';
import { useColors } from '@/theme';

interface WikiListItemProps {
  title: string;
  subtitle?: string;
  onPress?: () => void;
  icon?: React.ReactNode;
  accent?: 'sky' | 'purple' | 'emerald' | 'amber';
}

export const WikiListItem: React.FC<WikiListItemProps> = ({
  title,
  subtitle,
  onPress,
  icon,
  accent = 'sky',
}) => {
  const colors = useColors();

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

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.7}
      accessibilityRole="button"
      accessibilityLabel={subtitle ? `${title}. ${subtitle}` : title}
      style={[styles.container, { borderColor: colors.outlineVariant, backgroundColor: colors.surfaceContainer }]}
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
      <Text style={[styles.chevron, { color: colors.onSurfaceVariant, opacity: 0.5 }]}>›</Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 18,
    paddingVertical: 16,
    marginBottom: 12,
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
    marginRight: 14,
  },
  textContainer: {
    flex: 1,
  },
  textContainerWithIcon: {
    flex: 1,
  },
  title: {
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 22,
    letterSpacing: -0.2,
    marginBottom: 3,
  },
  subtitle: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
    opacity: 0.7,
  },
  chevron: {
    fontSize: 22,
    fontWeight: '700',
  },
});
