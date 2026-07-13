import React, { useMemo } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '../../components/ScreenShell';
import { Button } from '../../components/ui';
import { FoodItem } from '../../types/food';
import { NutritionStackParamList } from '../../navigation/types';
import { useColors } from '../../theme';
import { getFoodById } from '../../services/foodIndexService';
import { usePantryStore } from '../../stores/pantryStore';

type RoutePropType = RouteProp<NutritionStackParamList, 'FoodDetail'>;
type NavigationProp = NativeStackNavigationProp<NutritionStackParamList, 'FoodDetail'>;

function formatMacro(value: number) {
  return `${Math.round(value * 10) / 10}`;
}

function buildAliasList(food: FoodItem) {
  return [...(food.aliases ?? []), ...(food.searchAliases ?? [])]
    .map(alias => alias.trim())
    .filter((alias, index, list) => alias.length > 0 && list.indexOf(alias) === index);
}

export const FoodDetailScreen: React.FC = () => {
  const colors = useColors();
  const navigation = useNavigation<NavigationProp>();
  const route = useRoute<RoutePropType>();
  const addFoodToPantry = usePantryStore(state => state.addFoodToPantry);
  const { foodId } = route.params;

  const food = useMemo(() => getFoodById(foodId), [foodId]);

  if (!food) {
    return (
      <ScreenShell title="No encontrado" subtitle="Detalle de alimento">
        <View style={styles.emptyState}>
          <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>El alimento solicitado no existe.</Text>
          <Text style={[styles.emptyBody, { color: colors.onSurfaceVariant }]}>
            Puede que se haya retirado del catálogo o que el enlace esté desactualizado.
          </Text>
          <Button onPress={() => navigation.goBack()}>Volver</Button>
        </View>
      </ScreenShell>
    );
  }

  const aliases = buildAliasList(food);
  const micronutrients = food.micronutrients ?? [];

  return (
    <ScreenShell title={food.name} subtitle={food.brand || food.category || 'Base nutricional'}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.content}>
        <View style={[styles.heroCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <View style={styles.heroHeader}>
            <View style={[styles.categoryBadge, { backgroundColor: colors.primaryContainer }]}>
              <Text style={[styles.categoryText, { color: colors.onPrimaryContainer }]}>
                {food.category || 'Otros'}
              </Text>
            </View>
            <Text style={[styles.portionText, { color: colors.onSurfaceVariant }]}>
              Porción: {food.portionSize || `${food.servingSize ?? 100} ${food.servingUnit ?? food.unit ?? 'g'}`}
            </Text>
          </View>

          <View style={styles.caloriesBlock}>
            <Text style={[styles.caloriesValue, { color: colors.primary }]}>
              {Math.round(food.calories)}
            </Text>
            <Text style={[styles.caloriesLabel, { color: colors.onSurfaceVariant }]}>Calorías</Text>
          </View>

          <View style={styles.macroRow}>
            <View style={[styles.macroCard, { backgroundColor: colors.surfaceContainerHigh }]}>
              <Text style={[styles.macroValue, { color: colors.tertiary }]}>{formatMacro(food.protein)}g</Text>
              <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Proteína</Text>
            </View>
            <View style={[styles.macroCard, { backgroundColor: colors.surfaceContainerHigh }]}>
              <Text style={[styles.macroValue, { color: colors.secondary }]}>{formatMacro(food.carbs)}g</Text>
              <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Carbohidratos</Text>
            </View>
            <View style={[styles.macroCard, { backgroundColor: colors.surfaceContainerHigh }]}>
              <Text style={[styles.macroValue, { color: colors.error }]}>{formatMacro(food.fats)}g</Text>
              <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Grasas</Text>
            </View>
          </View>

          {food.cookingBehavior || food.cookingWeightFactor ? (
            <View style={[styles.noteCard, { backgroundColor: colors.surfaceContainer }]}>
              <Text style={[styles.sectionEyebrow, { color: colors.onSurfaceVariant }]}>COMPORTAMIENTO DE COCINA</Text>
              <Text style={[styles.noteText, { color: colors.onSurface }]}>
                {food.cookingBehavior === 'shrinks'
                  ? 'Tiende a contraerse al cocinarse.'
                  : food.cookingBehavior === 'expands'
                    ? 'Tiende a expandirse al cocinarse.'
                    : 'No hay ajuste de cocción explícito para este alimento.'}
                {food.cookingWeightFactor ? ` Factor estimado: x${food.cookingWeightFactor}.` : ''}
              </Text>
            </View>
          ) : null}
        </View>

        <View style={[styles.sectionCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionEyebrow, { color: colors.onSurfaceVariant }]}>ACCIONES</Text>
          <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Usar este alimento en tu flujo</Text>
          <Text style={[styles.sectionBody, { color: colors.onSurfaceVariant }]}>
            Puedes llevarlo directo a la despensa para reutilizarlo en el planner o guardarlo como referencia rápida.
          </Text>
          <Button onPress={() => addFoodToPantry(food)}>Añadir a despensa</Button>
        </View>

        {aliases.length > 0 ? (
          <View style={[styles.sectionCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.sectionEyebrow, { color: colors.onSurfaceVariant }]}>ALIAS Y BÚSQUEDA</Text>
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Nombres que resuelve el índice</Text>
            <View style={styles.chipRow}>
              {aliases.map(alias => (
                <View key={alias} style={[styles.chip, { backgroundColor: colors.primaryContainer }]}>
                  <Text style={[styles.chipText, { color: colors.onPrimaryContainer }]}>{alias}</Text>
                </View>
              ))}
            </View>
          </View>
        ) : null}

        {food.tags && food.tags.length > 0 ? (
          <View style={[styles.sectionCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.sectionEyebrow, { color: colors.onSurfaceVariant }]}>ETIQUETAS</Text>
            <View style={styles.chipRow}>
              {food.tags.map(tag => (
                <View key={tag} style={[styles.chip, { backgroundColor: colors.surfaceContainerHigh }]}>
                  <Text style={[styles.chipText, { color: colors.onSurface }]}>{tag}</Text>
                </View>
              ))}
            </View>
          </View>
        ) : null}

        {micronutrients.length > 0 ? (
          <View style={[styles.sectionCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.sectionEyebrow, { color: colors.onSurfaceVariant }]}>MICRONUTRIENTES</Text>
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Aportes destacados</Text>
            <View style={styles.microList}>
              {micronutrients.map(micro => (
                <View key={`${micro.name}-${micro.unit}`} style={[styles.microRow, { borderColor: colors.outlineVariant }]}>
                  <Text style={[styles.microName, { color: colors.onSurface }]}>{micro.name}</Text>
                  <Text style={[styles.microValue, { color: colors.onSurfaceVariant }]}>
                    {formatMacro(micro.amount)} {micro.unit}
                  </Text>
                </View>
              ))}
            </View>
          </View>
        ) : (
          <View style={[styles.sectionCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.sectionEyebrow, { color: colors.onSurfaceVariant }]}>MICRONUTRIENTES</Text>
            <Text style={[styles.sectionBody, { color: colors.onSurfaceVariant }]}>
              No hay micronutrientes destacados en esta ficha.
            </Text>
          </View>
        )}
      </ScrollView>
    </ScreenShell>
  );
};

const styles = StyleSheet.create({
  content: {
    gap: 14,
    paddingBottom: 32,
  },
  emptyState: {
    gap: 12,
    paddingHorizontal: 8,
    paddingTop: 12,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '800',
  },
  emptyBody: {
    fontSize: 13,
    lineHeight: 18,
  },
  heroCard: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 18,
    gap: 14,
  },
  heroHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  categoryBadge: {
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  categoryText: {
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  portionText: {
    fontSize: 12,
    fontWeight: '600',
  },
  caloriesBlock: {
    alignItems: 'center',
    paddingVertical: 10,
  },
  caloriesValue: {
    fontSize: 48,
    lineHeight: 52,
    fontWeight: '900',
    letterSpacing: -1.5,
  },
  caloriesLabel: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 2,
    textTransform: 'uppercase',
    marginTop: 2,
  },
  macroRow: {
    flexDirection: 'row',
    gap: 8,
  },
  macroCard: {
    flex: 1,
    borderRadius: 18,
    paddingVertical: 14,
    paddingHorizontal: 12,
    alignItems: 'center',
    gap: 2,
  },
  macroValue: {
    fontSize: 18,
    fontWeight: '900',
  },
  macroLabel: {
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  noteCard: {
    borderRadius: 18,
    padding: 14,
    gap: 4,
  },
  noteText: {
    fontSize: 13,
    lineHeight: 18,
  },
  sectionCard: {
    borderRadius: 22,
    borderWidth: 1,
    padding: 16,
    gap: 10,
  },
  sectionEyebrow: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.4,
    textTransform: 'uppercase',
  },
  sectionTitle: {
    fontSize: 18,
    lineHeight: 22,
    fontWeight: '900',
  },
  sectionBody: {
    fontSize: 13,
    lineHeight: 18,
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 7,
  },
  chipText: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.4,
  },
  microList: {
    gap: 8,
  },
  microRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  microName: {
    fontSize: 13,
    fontWeight: '800',
  },
  microValue: {
    fontSize: 12,
    fontWeight: '700',
  },
});

