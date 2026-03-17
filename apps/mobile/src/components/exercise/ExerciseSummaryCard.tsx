import React, { memo } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import type { ExerciseMuscleInfo } from '@/types/workout';
import { useColors } from '@/theme';

interface ExerciseSummaryCardProps {
  item: ExerciseMuscleInfo;
  onPress?: (id: string) => void;
}

export const ExerciseSummaryCard = memo(({ item, onPress }: ExerciseSummaryCardProps) => {
  const colors = useColors();

  const primaryMuscles = item.involvedMuscles
    ?.filter(m => m.role === 'primary')
    .map(m => m.muscle)
    .join(', ');

  const getTypeStyle = (type: string) => {
    switch (type) {
      case 'Básico':
        return { bg: `${colors.cyberCopper}1A`, border: `${colors.cyberCopper}4D`, text: colors.cyberCopper };
      case 'Accesorio':
        return { bg: `${colors.cyberCyan}1A`, border: `${colors.cyberCyan}4D`, text: colors.cyberCyan };
      default:
        return { bg: `${colors.primary}1A`, border: `${colors.primary}4D`, text: colors.primary };
    }
  };

  const badgeStyle = getTypeStyle(item.type);

  return (
    <TouchableOpacity
      onPress={() => onPress?.(item.id)}
      style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
      activeOpacity={0.7}
    >
      <View style={[styles.imagePlaceholder, { backgroundColor: colors.surfaceContainerHigh }]}>
        <Text style={styles.imageIcon}>🏋️</Text>
      </View>

      <View style={styles.content}>
        <View style={styles.headerRow}>
          <View style={[styles.badge, { backgroundColor: badgeStyle.bg, borderColor: badgeStyle.border }]}>
            <Text style={[styles.badgeText, { color: badgeStyle.text }]}>
              {item.type}
            </Text>
          </View>
          <Text style={[styles.equipmentText, { color: colors.onSurfaceVariant }]}>
            · {item.equipment}
          </Text>
        </View>

        <Text style={[styles.name, { color: colors.onSurface }]} numberOfLines={1}>
          {item.name}
        </Text>

        {primaryMuscles ? (
          <Text style={[styles.muscles, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
            {primaryMuscles}
          </Text>
        ) : null}
      </View>

      <Text style={[styles.chevron, { color: colors.onSurfaceVariant }]}>›</Text>
    </TouchableOpacity>
  );
});

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderRadius: 16,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  imagePlaceholder: {
    height: 64,
    width: 64,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 16,
  },
  imageIcon: {
    fontSize: 24,
    opacity: 0.3,
  },
  content: {
    flex: 1,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 4,
  },
  badge: {
    borderWidth: 1,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 999,
  },
  badgeText: {
    fontSize: 10,
    textTransform: 'uppercase',
    fontWeight: 'bold',
    letterSpacing: 1,
  },
  equipmentText: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 1,
    fontWeight: '500',
  },
  name: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  muscles: {
    fontSize: 12,
    fontStyle: 'italic',
  },
  chevron: {
    fontSize: 24,
    opacity: 0.4,
  },
});
