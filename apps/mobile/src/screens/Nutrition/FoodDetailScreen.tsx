import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { useRoute, RouteProp } from '@react-navigation/native';
import { ScreenShell } from '../../components/ScreenShell';
import { FOOD_DATABASE } from '../../data/foodDatabase';
import { NutritionStackParamList } from '../../navigation/AppNavigator';
import { useColors } from '../../theme';

type RoutePropType = RouteProp<NutritionStackParamList, 'FoodDetail'>;

export const FoodDetailScreen: React.FC = () => {
  const colors = useColors();
  const route = useRoute<RoutePropType>();
  const { foodId } = route.params;

  const food = FOOD_DATABASE.find((f) => f.id === foodId);

  if (!food) {
    return (
      <ScreenShell title="No encontrado">
        <View style={styles.container}>
          <Text style={[styles.notFoundText, { color: colors.onSurface }]}>
            El alimento solicitado no existe.
          </Text>
        </View>
      </ScreenShell>
    );
  }

  interface MacroInfoProps {
    label: string;
    value: number;
    unit: string;
    color: string;
  }

  const MacroInfo: React.FC<MacroInfoProps> = ({ label, value, unit, color }) => (
    <View style={[styles.macroCard, { backgroundColor: colors.surfaceContainerHigh }]}>
      <Text style={[styles.macroValue, { color }]}>{value}</Text>
      <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>
        {label} ({unit})
      </Text>
    </View>
  );

  return (
    <ScreenShell title={food.name} subtitle={food.brand}>
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.headerRow}>
            <View style={[styles.categoryBadge, { backgroundColor: colors.primaryContainer }]}>
              <Text style={[styles.categoryText, { color: colors.onPrimaryContainer }]}>
                {food.category}
              </Text>
            </View>
            <Text style={[styles.portionText, { color: colors.onSurfaceVariant }]}>
              Porción: {food.portionSize}
            </Text>
          </View>

          <View style={[styles.caloriesCard, { backgroundColor: colors.surfaceContainerHigh }]}>
            <Text style={[styles.caloriesValue, { color: colors.primary }]}>{food.calories}</Text>
            <Text style={[styles.caloriesLabel, { color: colors.onSurfaceVariant }]}>
              Calorías
            </Text>
          </View>

          <View style={styles.macrosRow}>
            <MacroInfo label="Proteína" value={food.protein} unit="g" color={colors.tertiary} />
            <MacroInfo label="Carbohidratos" value={food.carbs} unit="g" color={colors.secondary} />
            <MacroInfo label="Grasas" value={food.fat} unit="g" color={colors.error} />
          </View>

          {food.tags.length > 0 && (
            <View style={styles.tagsSection}>
              <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Etiquetas</Text>
              <View style={styles.tagsContainer}>
                {food.tags.map((tag) => (
                  <View
                    key={tag}
                    style={[styles.tag, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
                  >
                    <Text style={[styles.tagText, { color: colors.onSurfaceVariant }]}>
                      #{tag}
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          )}
        </View>
      </ScrollView>
    </ScreenShell>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  notFoundText: {
    fontSize: 15,
    lineHeight: 22,
  },
  scrollView: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 24,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 24,
  },
  categoryBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    marginRight: 12,
  },
  categoryText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  portionText: {
    fontSize: 13,
    fontStyle: 'italic',
  },
  caloriesCard: {
    borderRadius: 24,
    paddingVertical: 24,
    paddingHorizontal: 16,
    alignItems: 'center',
    marginBottom: 24,
  },
  caloriesValue: {
    fontSize: 48,
    fontWeight: '800',
  },
  caloriesLabel: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 4,
    textTransform: 'uppercase',
    marginTop: 4,
  },
  macrosRow: {
    flexDirection: 'row',
    marginBottom: 24,
  },
  macroCard: {
    flex: 1,
    borderRadius: 16,
    padding: 16,
    alignItems: 'center',
    marginHorizontal: 4,
  },
  macroValue: {
    fontSize: 24,
    fontWeight: '700',
  },
  macroLabel: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
    marginTop: 4,
  },
  tagsSection: {
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 12,
    paddingLeft: 4,
  },
  tagsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  tag: {
    borderRadius: 8,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 6,
    marginRight: 8,
    marginBottom: 8,
  },
  tagText: {
    fontSize: 12,
  },
});
