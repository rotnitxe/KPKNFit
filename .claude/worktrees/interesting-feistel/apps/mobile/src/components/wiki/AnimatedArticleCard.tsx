import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { useColors } from '@/theme';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';

interface AnimatedArticleCardProps {
  title: string;
  category: string;
  description: string;
  readTime: string;
  onPress?: () => void;
  index?: number;
}

export const AnimatedArticleCard: React.FC<AnimatedArticleCardProps> = ({
  title,
  category,
  description,
  readTime,
  onPress,
}) => {
  const colors = useColors();

  const handlePress = () => {
    ReactNativeHapticFeedback.trigger('impactLight', {
      enableVibrateFallback: true,
      ignoreAndroidSystemSettings: false,
    });
    if (onPress) onPress();
  };

  return (
    <Pressable
      onPress={handlePress}
      accessibilityRole="button"
      accessibilityLabel={`${category}: ${title}`}
      style={[
        styles.card,
        {
          backgroundColor: colors.surfaceContainer,
          borderColor: colors.outlineVariant,
        },
      ]}
    >
      <View style={styles.header}>
        <View style={[styles.categoryBadge, { backgroundColor: colors.primaryContainer }]}>
          <Text style={[styles.categoryText, { color: colors.onPrimaryContainer }]}>
            {category}
          </Text>
        </View>
        <View style={[styles.readTimeBadge, { backgroundColor: colors.surfaceContainerHigh }]}>
          <Text style={[styles.readTimeText, { color: colors.onSurfaceVariant }]}>
            {readTime}
          </Text>
        </View>
      </View>

      <Text style={[styles.title, { color: colors.onSurface }]} numberOfLines={2}>
        {title}
      </Text>

      <Text
        style={[styles.description, { color: colors.onSurfaceVariant }]}
        numberOfLines={2}
      >
        {description}
      </Text>

      <View style={[styles.footer, { borderColor: colors.outlineVariant }]}>
        <Text style={[styles.footerText, { color: colors.onSurfaceVariant }]}>
          Leer artículo
        </Text>
        <Text style={[styles.chevron, { color: colors.primary }]}>›</Text>
      </View>
    </Pressable>
  );
};

const styles = StyleSheet.create({
  card: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
    marginBottom: 12,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  categoryBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
  },
  categoryText: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  readTimeBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  readTimeText: {
    fontSize: 11,
    fontWeight: '700',
  },
  title: {
    fontSize: 16,
    fontWeight: '800',
    lineHeight: 22,
    letterSpacing: -0.3,
    marginBottom: 6,
  },
  description: {
    fontSize: 13,
    lineHeight: 18,
    marginBottom: 12,
    opacity: 0.8,
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
    letterSpacing: 0.5,
    opacity: 0.6,
  },
  chevron: {
    fontSize: 20,
    fontWeight: '700',
  },
});
