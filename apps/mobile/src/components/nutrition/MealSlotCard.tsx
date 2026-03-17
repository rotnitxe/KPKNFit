import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import type { MealSlot } from '../../types/mealPlanner';
import type { MealTemplateSummary } from '../../stores/mealTemplateStore';
import { Button } from '../ui';
import { useColors } from '../../theme';

interface MealSlotCardProps {
  slot: MealSlot;
  selectedTemplate: MealTemplateSummary | null;
  onPressSelect: () => void;
  onPressClear?: () => void;
}

const slotLabels: Record<MealSlot, string> = {
  breakfast: 'Desayuno',
  lunch: 'Almuerzo',
  dinner: 'Cena',
  snack: 'Snack',
};

export const MealSlotCard: React.FC<MealSlotCardProps> = ({
  slot,
  selectedTemplate,
  onPressSelect,
  onPressClear,
}) => {
  const colors = useColors();

  return (
    <View style={[styles.container, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <View style={styles.header}>
        <Text style={[styles.slotLabel, { color: colors.primary }]}>
          {slotLabels[slot]}
        </Text>
        {selectedTemplate && onPressClear && (
          <Pressable onPress={onPressClear} style={styles.clearButton}>
            <Text style={[styles.clearText, { color: colors.error }]}>Quitar</Text>
          </Pressable>
        )}
      </View>

      {selectedTemplate ? (
        <View style={styles.templateSection}>
          <Text style={[styles.templateName, { color: colors.onSurface }]} numberOfLines={1}>
            {selectedTemplate.name}
          </Text>
          <Text style={[styles.templateDescription, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
            {selectedTemplate.description}
          </Text>
          <View style={styles.macroRow}>
            <Text style={[styles.macroCalories, { color: colors.onSurface }]}>
              {Math.round(selectedTemplate.calories)} kcal
            </Text>
            <Text style={[styles.macroDetails, { color: colors.onSurfaceVariant }]}>
              P: {Math.round(selectedTemplate.protein)}g · C: {Math.round(selectedTemplate.carbs)}g · G:{' '}
              {Math.round(selectedTemplate.fats)}g
            </Text>
          </View>
        </View>
      ) : (
        <View style={styles.emptySection}>
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
            Sin plantilla asignada
          </Text>
        </View>
      )}

      <Button
        variant={selectedTemplate ? 'secondary' : 'primary'}
        onPress={onPressSelect}
      >
        {selectedTemplate ? 'Cambiar plantilla' : 'Seleccionar plantilla'}
      </Button>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  slotLabel: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
  },
  clearButton: {
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  clearText: {
    fontSize: 12,
    fontWeight: '500',
  },
  templateSection: {
    marginBottom: 12,
  },
  templateName: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 4,
  },
  templateDescription: {
    fontSize: 13,
    lineHeight: 18,
    marginBottom: 8,
  },
  macroRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  macroCalories: {
    fontSize: 13,
    fontWeight: '600',
  },
  macroDetails: {
    fontSize: 12,
  },
  emptySection: {
    paddingVertical: 8,
    marginBottom: 12,
  },
  emptyText: {
    fontSize: 13,
    fontStyle: 'italic',
  },
});
