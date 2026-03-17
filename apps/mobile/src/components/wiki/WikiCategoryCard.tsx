import React from 'react';
import { TouchableOpacity, Text, View, StyleSheet } from 'react-native';
import { useColors } from '@/theme';

interface WikiCategoryCardProps {
  title: string;
  subtitle: string;
  count: number;
  accent?: 'sky' | 'purple' | 'emerald' | 'amber';
  onPress: () => void;
}

interface AccentColors {
  border: string;
  text: string;
  bg: string;
}

export const WikiCategoryCard: React.FC<WikiCategoryCardProps> = ({
  title,
  subtitle,
  count,
  accent = 'sky',
  onPress,
}) => {
  const colors = useColors();

  // Mapeo de colores semánticos usando tokens del tema
  const getAccentColors = (accent: string): AccentColors => {
    switch (accent) {
      case 'sky':
        return {
          border: 'rgba(14, 165, 233, 0.2)',
          text: '#38bdf8',
          bg: 'rgba(14, 165, 233, 0.1)',
        };
      case 'purple':
        return {
          border: 'rgba(168, 85, 247, 0.2)',
          text: '#c084fc',
          bg: 'rgba(168, 85, 247, 0.1)',
        };
      case 'emerald':
        return {
          border: 'rgba(16, 185, 129, 0.2)',
          text: '#34d399',
          bg: 'rgba(16, 185, 129, 0.1)',
        };
      case 'amber':
        return {
          border: 'rgba(251, 191, 36, 0.2)',
          text: '#fcd34d',
          bg: 'rgba(251, 191, 36, 0.1)',
        };
      default:
        return {
          border: colors.outlineVariant,
          text: colors.primary,
          bg: colors.surfaceContainer,
        };
    }
  };

  const accentColors = getAccentColors(accent);

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.7}
      style={[
        styles.card,
        {
          backgroundColor: colors.surface,
          borderColor: accentColors.border,
        },
      ]}
    >
      <View style={styles.header}>
        <View style={styles.titleContainer}>
          <Text style={[styles.subtitle, { color: accentColors.text }]}>
            {subtitle}
          </Text>
          <Text style={[styles.title, { color: colors.onSurface }]}>{title}</Text>
        </View>
        <View
          style={[
            styles.countBadge,
            { backgroundColor: accentColors.bg, borderColor: accentColors.border },
          ]}
        >
          <Text style={[styles.countText, { color: accentColors.text }]}>{count}</Text>
        </View>
      </View>
      <View style={[styles.footer, { borderColor: colors.outlineVariant }]}>
        <Text style={[styles.footerText, { color: colors.onSurfaceVariant }]}>
          Ver biblioteca completa
        </Text>
        <Text style={[styles.chevron, { color: accentColors.text }]}>〉</Text>
      </View>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 20,
    marginBottom: 16,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  titleContainer: {
    flex: 1,
    paddingRight: 16,
  },
  subtitle: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 2,
    marginBottom: 4,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    lineHeight: 26,
  },
  countBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
  },
  countText: {
    fontSize: 12,
    fontWeight: '700',
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 16,
    borderTopWidth: 1,
  },
  footerText: {
    fontSize: 12,
    fontWeight: '500',
  },
  chevron: {
    fontSize: 18,
    fontWeight: '700',
  },
});
