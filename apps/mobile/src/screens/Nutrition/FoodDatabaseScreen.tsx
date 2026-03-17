import React, { useState, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '../../components/ScreenShell';
import { FoodSearchBar, FoodCard, FoodFilterChips } from '../../components/nutrition';
import { FOOD_DATABASE, searchFoods, filterFoodsByCategory } from '../../data/foodDatabase';
import { FoodItem, FoodCategory } from '../../types/food';
import { NutritionStackParamList } from '../../navigation/AppNavigator';
import { useColors } from '../../theme';

type NavigationProp = NativeStackNavigationProp<NutritionStackParamList, 'FoodDatabase'>;

export const FoodDatabaseScreen: React.FC = () => {
  const colors = useColors();
  const navigation = useNavigation<NavigationProp>();
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<FoodCategory | 'Todos'>('Todos');

  const filteredFoods = useMemo(() => {
    let results = filterFoodsByCategory(FOOD_DATABASE, category);
    results = searchFoods(results, query);
    return results;
  }, [query, category]);

  const handleFoodPress = (food: FoodItem) => {
    navigation.navigate('FoodDetail', { foodId: food.id });
  };

  return (
    <ScreenShell title="Base de Alimentos">
      <View style={styles.container}>
        <FoodSearchBar query={query} onChangeQuery={setQuery} onClear={() => setQuery('')} />

        <FoodFilterChips selectedCategory={category} onSelectCategory={setCategory} />

        {filteredFoods.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
              No se encontraron alimentos para "{query}" en esta categoría.
            </Text>
          </View>
        ) : (
          <ScrollView style={styles.list} showsVerticalScrollIndicator={false}>
            {filteredFoods.map((item) => (
              <FoodCard key={item.id} food={item} onPress={handleFoodPress} />
            ))}
          </ScrollView>
        )}
      </View>
    </ScreenShell>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingTop: 8,
    flex: 1,
  },
  emptyState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 80,
  },
  emptyText: {
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
  list: {
    paddingBottom: 24,
  },
});
