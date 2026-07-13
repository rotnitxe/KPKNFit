import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '@/theme';
import type { InvolvedMuscle } from '../../types/workout';

interface MuscleBadgeListProps {
  muscles: InvolvedMuscle[];
  maxItems?: number;
}

export const MuscleBadgeList: React.FC<MuscleBadgeListProps> = ({
  muscles,
  maxItems,
}) => {
  const colors = useColors();

  if (!muscles || muscles.length === 0) {
    return (
      <View style={styles.emptyContainer}>
        <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
          Sin musculatura definida
        </Text>
      </View>
    );
  }

  const displayedMuscles = maxItems ? muscles.slice(0, maxItems) : muscles;

  return (
    <View style={styles.container}>
      {displayedMuscles.map((item, idx) => (
        <View
          key={`${item.muscle}-${idx}`}
          style={[
            styles.badge,
            {
              backgroundColor:
                item.role === 'primary'
                  ? `${colors.primary}20`
                  : `${colors.outlineVariant}10`,
              borderColor:
                item.role === 'primary'
                  ? `${colors.primary}40`
                  : `${colors.outlineVariant}20`,
            },
          ]}
        >
          <Text
            style={[
              styles.badgeText,
              {
                color: item.role === 'primary' ? colors.primary : colors.onSurfaceVariant,
              },
            ]}
          >
            {item.muscle}
          </Text>
        </View>
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  emptyContainer: {
    paddingVertical: 8,
  },
  emptyText: {
    fontSize: 12,
    fontStyle: 'italic',
  },
  container: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginTop: 8,
  },
  badge: {
    paddingHorizontal: 12,
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
});
