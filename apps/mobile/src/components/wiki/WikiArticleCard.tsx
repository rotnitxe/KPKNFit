import React from 'react';
import { TouchableOpacity, Text, View, StyleSheet } from 'react-native';
import { useColors } from '@/theme';

interface WikiArticleCardProps {
  title: string;
  category: string;
  description: string;
  readTime: string;
  onPress?: () => void;
}

export function WikiArticleCard({
  title,
  category,
  description,
  readTime,
  onPress,
}: WikiArticleCardProps) {
  const colors = useColors();

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.7}
      accessibilityRole="button"
      accessibilityLabel={`${category}: ${title}`}
      style={[styles.card, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
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
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 20,
    marginBottom: 12,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  categoryBadge: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 999,
  },
  categoryText: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  readTimeBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 14,
  },
  readTimeText: {
    fontSize: 11,
    fontWeight: '700',
  },
  title: {
    fontSize: 17,
    fontWeight: '800',
    lineHeight: 22,
    letterSpacing: -0.3,
    marginBottom: 8,
  },
  description: {
    fontSize: 13,
    lineHeight: 19,
    marginBottom: 16,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 14,
    borderTopWidth: 1,
  },
  footerText: {
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
    opacity: 0.7,
  },
  chevron: {
    fontSize: 20,
    fontWeight: '700',
  },
});
