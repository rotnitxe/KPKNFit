import React from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import { FoodItem } from '../../types/food';

interface PortionSelectorProps {
  food: FoodItem;
  quantity: number;
  unit: string;
  onQuantityChange: (quantity: number) => void;
  onUnitChange: (unit: string) => void;
  macroPreview?: { calories: number; protein: number; carbs: number; fats: number };
}

export const PortionSelector: React.FC<PortionSelectorProps> = ({
  food,
  quantity,
  unit,
  onQuantityChange,
  onUnitChange,
  macroPreview,
}) => {
  const colors = useColors();

  // Calcular macros basados en la cantidad
  const calculateMacros = () => {
    if (macroPreview) {
      return macroPreview;
    }

    const factor = quantity; // Simplificación: asumimos unitario para gramos
    return {
      calories: food.calories * factor,
      protein: food.protein * factor,
      carbs: food.carbs * factor,
      fats: (food.fats ?? food.fat ?? 0) * factor,
    };
  };

  const macros = calculateMacros();

  // Unidades disponibles (basadas en el alimento)
  const units = ['unit', 'g', 'ml', 'portion', 'cup', 'tbsp', 'tsp'];

  return (
    <View style={styles.container}>
      <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Porción</Text>
      
      <View style={styles.selectorRow}>
        {/* Input de cantidad */}
        <View style={styles.quantityContainer}>
          <TouchableOpacity
            style={[styles.stepperButton, { backgroundColor: colors.surfaceVariant }]}
            onPress={() => onQuantityChange(Math.max(0.5, quantity - 0.5))}
          >
            <Text style={{ color: colors.onSurface }}>-</Text>
          </TouchableOpacity>
          
          <TextInput
            style={[styles.quantityInput, { color: colors.onSurface }]}
            value={String(quantity)}
            onChangeText={(text) => onQuantityChange(parseFloat(text) || 0)}
            keyboardType="numeric"
          />
          
          <TouchableOpacity
            style={[styles.stepperButton, { backgroundColor: colors.surfaceVariant }]}
            onPress={() => onQuantityChange(quantity + 0.5)}
          >
            <Text style={{ color: colors.onSurface }}>+</Text>
          </TouchableOpacity>
        </View>

        {/* Selector de unidad */}
        <View style={styles.unitPicker}>
          {units.map((u) => (
            <TouchableOpacity
              key={u}
              style={[
                styles.unitButton,
                unit === u && { backgroundColor: colors.primary }
              ]}
              onPress={() => onUnitChange(u)}
            >
              <Text style={{ color: unit === u ? colors.onPrimary : colors.onSurface }}>
                {u}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {/* Vista previa de macros */}
      <View style={styles.macroPreview}>
        <View style={styles.macroItem}>
          <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Cal</Text>
          <Text style={[styles.macroValue, { color: colors.primary }]}>
            {Math.round(macros.calories)}
          </Text>
        </View>
        <View style={styles.macroItem}>
          <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Prot</Text>
          <Text style={[styles.macroValue, { color: colors.primary }]}>
            {Math.round(macros.protein)}g
          </Text>
        </View>
        <View style={styles.macroItem}>
          <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Carb</Text>
          <Text style={[styles.macroValue, { color: colors.primary }]}>
            {Math.round(macros.carbs)}g
          </Text>
        </View>
        <View style={styles.macroItem}>
          <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Grasa</Text>
          <Text style={[styles.macroValue, { color: colors.primary }]}>
            {Math.round(macros.fats)}g
          </Text>
        </View>
      </View>

      {/* Barras visuales de macros */}
      <View style={styles.macroBars}>
        <View style={[styles.macroBar, { backgroundColor: 'rgba(255,99,71,0.3)' }]}>
          <View
            style={[
              styles.macroBarFill,
              {
                width: `${Math.min((macros.protein / 50) * 100, 100)}%`,
                backgroundColor: 'rgb(255,99,71)',
              },
            ]}
          />
        </View>
        <View style={[styles.macroBar, { backgroundColor: 'rgba(54,162,235,0.3)' }]}>
          <View
            style={[
              styles.macroBarFill,
              {
                width: `${Math.min((macros.carbs / 100) * 100, 100)}%`,
                backgroundColor: 'rgb(54,162,235)',
              },
            ]}
          />
        </View>
        <View style={[styles.macroBar, { backgroundColor: 'rgba(255,206,86,0.3)' }]}>
          <View
            style={[
              styles.macroBarFill,
              {
                width: `${Math.min((macros.fats / 30) * 100, 100)}%`,
                backgroundColor: 'rgb(255,206,86)',
              },
            ]}
          />
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginTop: 16,
    padding: 12,
    borderRadius: 12,
    backgroundColor: '#f5f5f5',
  },
  label: {
    fontSize: 12,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  selectorRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  quantityContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  stepperButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  quantityInput: {
    width: 50,
    textAlign: 'center',
    fontSize: 16,
    fontWeight: 'bold',
    marginHorizontal: 8,
  },
  unitPicker: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 4,
    maxWidth: '60%',
  },
  unitButton: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    backgroundColor: '#e0e0e0',
  },
  macroPreview: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 8,
  },
  macroItem: {
    alignItems: 'center',
  },
  macroLabel: {
    fontSize: 10,
  },
  macroValue: {
    fontSize: 14,
    fontWeight: 'bold',
  },
  macroBars: {
    gap: 4,
  },
  macroBar: {
    height: 6,
    borderRadius: 3,
    overflow: 'hidden',
  },
  macroBarFill: {
    height: '100%',
  },
});

export default PortionSelector;
