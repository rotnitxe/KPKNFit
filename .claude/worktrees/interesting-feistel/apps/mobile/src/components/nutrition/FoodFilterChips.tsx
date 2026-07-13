import React from 'react';
import { Text, ScrollView, Pressable, StyleSheet } from 'react-native';
import { FoodCategory } from '../../types/food';
import { useColors } from '../../theme';

interface FoodFilterChipsProps {
  selectedCategory: FoodCategory | 'Todos';
  onSelectCategory: (category: FoodCategory | 'Todos') => void;
}

const CATEGORIES: (FoodCategory | 'Todos')[] = [
  'Todos',
  'Proteínas',
  'Carbohidratos',
  'Grasas',
  'Vegetales',
  'Frutas',
  'Lácteos',
  'Snacks',
  'Bebidas',
  'Otros'
];

export const FoodFilterChips: React.FC<FoodFilterChipsProps> = ({ 
  selectedCategory, 
  onSelectCategory 
}) => {
  const colors = useColors();

  return (
    <ScrollView 
      horizontal 
      showsHorizontalScrollIndicator={false}
      style={styles.scrollView}
      contentContainerStyle={styles.scrollContent}
    >
      {CATEGORIES.map((category) => {
        const isActive = selectedCategory === category;
        return (
          <Pressable
            key={category}
            onPress={() => onSelectCategory(category)}
            style={[
              styles.chip,
              {
                backgroundColor: isActive ? colors.primary : colors.surface,
                borderColor: isActive ? colors.primary : colors.outlineVariant,
              }
            ]}
          >
            <Text style={[
              styles.chipText,
              {
                color: isActive ? colors.onPrimary : colors.onSurfaceVariant,
              }
            ]}>
              {category}
            </Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  scrollView: {
    flexDirection: 'row',
    marginBottom: 16,
  },
  scrollContent: {
    paddingRight: 16,
  },
  chip: {
    marginRight: 8,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 9999,
    borderWidth: 1,
  },
  chipText: {
    fontSize: 14,
    fontWeight: '500',
  },
});
