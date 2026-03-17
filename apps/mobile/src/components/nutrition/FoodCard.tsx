import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { FoodItem } from '../../types/food';
import { useColors } from '../../theme';

interface FoodCardProps {
  food: FoodItem;
  onPress: (food: FoodItem) => void;
}

export const FoodCard: React.FC<FoodCardProps> = ({ food, onPress }) => {
  const colors = useColors();

  return (
    <Pressable
      onPress={() => onPress(food)}
      style={[styles.container, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
    >
      <View style={styles.content}>
        <View style={styles.titleRow}>
          <Text style={[styles.name, { color: colors.onSurface }]} numberOfLines={1}>
            {food.name}
          </Text>
          {food.brand && (
            <Text style={[styles.brand, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
              {food.brand}
            </Text>
          )}
        </View>

        <View style={styles.metaRow}>
          <Text style={[styles.portion, { color: colors.onSurfaceVariant }]}>
            {food.portionSize}
          </Text>
          <View style={[styles.categoryBadge, { backgroundColor: colors.primaryContainer }]}>
            <Text style={[styles.categoryText, { color: colors.onPrimaryContainer }]}>
              {food.category}
            </Text>
          </View>
        </View>
      </View>

      <View style={styles.caloriesSection}>
        <Text style={[styles.caloriesValue, { color: colors.primary }]}>{food.calories}</Text>
        <Text style={[styles.caloriesUnit, { color: colors.onSurfaceVariant }]}>kcal</Text>
      </View>
    </Pressable>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    marginBottom: 12,
  },
  content: {
    flex: 1,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginBottom: 8,
  },
  name: {
    fontSize: 16,
    fontWeight: '600',
    marginRight: 8,
  },
  brand: {
    fontSize: 13,
    fontStyle: 'italic',
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  portion: {
    fontSize: 12,
    marginRight: 12,
  },
  categoryBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  categoryText: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  caloriesSection: {
    alignItems: 'flex-end',
  },
  caloriesValue: {
    fontSize: 20,
    fontWeight: '700',
  },
  caloriesUnit: {
    fontSize: 10,
    textTransform: 'uppercase',
  },
});
