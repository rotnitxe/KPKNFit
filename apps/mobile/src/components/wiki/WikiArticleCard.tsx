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
      style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.cyberBorder }]}
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
    fontWeight: 'bold',
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
    fontWeight: '600',
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 8,
    lineHeight: 24,
  },
  description: {
    fontSize: 13,
    lineHeight: 18,
    marginBottom: 16,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 12,
    borderTopWidth: 1,
  },
  footerText: {
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  chevron: {
    fontSize: 20,
    fontWeight: 'bold',
  },
});
