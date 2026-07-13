import React from 'react';
import { ScrollView, TouchableOpacity, Text, View, StyleSheet } from 'react-native';
import { useColors } from '@/theme';

export type ExerciseFilterType = 'todos' | 'Básico' | 'Accesorio' | 'Aislamiento';

interface ExerciseFilterChipsProps {
  selectedType: ExerciseFilterType;
  onSelectType: (value: ExerciseFilterType) => void;
}

const FILTER_OPTIONS: ExerciseFilterType[] = ['todos', 'Básico', 'Accesorio', 'Aislamiento'];

export const ExerciseFilterChips: React.FC<ExerciseFilterChipsProps> = ({
  selectedType,
  onSelectType,
}) => {
  const colors = useColors();

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.container}
      contentContainerStyle={styles.contentContainer}
    >
      <View style={styles.row}>
        {FILTER_OPTIONS.map((type) => {
          const isActive = selectedType === type;
          return (
            <TouchableOpacity
              key={type}
              onPress={() => onSelectType(type)}
              style={[
                styles.chip,
                {
                  backgroundColor: isActive ? colors.primary : colors.surface,
                  borderColor: isActive ? colors.primary : colors.outlineVariant,
                },
              ]}
            >
              <Text
                style={[
                  styles.chipText,
                  {
                    color: isActive ? colors.onPrimary : colors.onSurfaceVariant,
                  },
                ]}
              >
                {type === 'todos' ? 'TODOS' : type.toUpperCase()}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    marginBottom: 24,
  },
  contentContainer: {
    paddingRight: 16,
  },
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  chip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 9999,
    borderWidth: 1,
  },
  chipText: {
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
});
