import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useColors } from '@/theme';
import type { ExerciseMuscleInfo } from '../../types/workout';

interface ExerciseListItemProps {
  item: ExerciseMuscleInfo;
  onPress?: (id: string) => void;
}

export const ExerciseListItem: React.FC<ExerciseListItemProps> = ({
  item,
  onPress,
}) => {
  const colors = useColors();

  const primaryMuscles = item.involvedMuscles
    ?.filter(m => m.role === 'primary')
    .map(m => m.muscle)
    .join(', ');

  const getTypeBadgeStyles = (type: string) => {
    switch (type) {
      case 'Básico':
        return {
          backgroundColor: 'rgba(251, 191, 36, 0.1)',
          borderColor: 'rgba(251, 191, 36, 0.3)',
          textColor: '#fcd34d',
        };
      case 'Accesorio':
        return {
          backgroundColor: 'rgba(14, 165, 233, 0.1)',
          borderColor: 'rgba(14, 165, 233, 0.3)',
          textColor: '#7dd3fc',
        };
      default:
        return {
          backgroundColor: `${colors.primary}20`,
          borderColor: `${colors.primary}40`,
          textColor: colors.primary,
        };
    }
  };

  const badgeStyles = getTypeBadgeStyles(item.type);

  return (
    <TouchableOpacity
      onPress={() => onPress?.(item.id)}
      style={[styles.container, { borderColor: colors.outlineVariant }]}
      activeOpacity={0.7}
    >
      <View style={styles.content}>
        <View style={styles.badgeRow}>
          <View
            style={[
              styles.badge,
              {
                backgroundColor: badgeStyles.backgroundColor,
                borderColor: badgeStyles.borderColor,
              },
            ]}
          >
            <Text style={[styles.badgeText, { color: badgeStyles.textColor }]}>
              {item.type}
            </Text>
          </View>
          <Text style={[styles.equipmentText, { color: colors.onSurfaceVariant }]}>
            · {item.equipment}
          </Text>
        </View>

        <Text style={[styles.nameText, { color: colors.onSurface }]} numberOfLines={1}>
          {item.name}
        </Text>

        {primaryMuscles ? (
          <Text style={[styles.muscleText, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
            {primaryMuscles}
          </Text>
        ) : null}
      </View>

      <Text style={[styles.chevron, { color: colors.onSurfaceVariant, opacity: 0.4 }]}>〉</Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#1A1C24',
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 16,
    marginBottom: 12,
  },
  content: {
    flex: 1,
    paddingRight: 16,
  },
  badgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 6,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 9999,
    borderWidth: 1,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  equipmentText: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    fontWeight: '500',
  },
  nameText: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 4,
  },
  muscleText: {
    fontSize: 12,
    fontStyle: 'italic',
  },
  chevron: {
    fontSize: 20,
    paddingLeft: 4,
  },
});
