import React from 'react';
import { View, Text, TextInput, Pressable, StyleSheet } from 'react-native';
import { useColors } from '../../theme';

interface FoodSearchBarProps {
  query: string;
  onChangeQuery: (v: string) => void;
  onClear?: () => void;
}

export const FoodSearchBar: React.FC<FoodSearchBarProps> = ({
  query,
  onChangeQuery,
  onClear,
}) => {
  const colors = useColors();

  return (
    <View style={[styles.container, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <Text style={styles.icon}>🔍</Text>
      <TextInput
        value={query}
        onChangeText={onChangeQuery}
        placeholder="Buscar alimento, marca o tag"
        placeholderTextColor={colors.onSurfaceVariant}
        style={[styles.input, { color: colors.onSurface }]}
        autoCapitalize="none"
        autoCorrect={false}
      />
      {query.length > 0 && onClear && (
        <Pressable onPress={onClear} style={styles.clearButton}>
          <Text style={[styles.clearIcon, { color: colors.onSurfaceVariant }]}>✕</Text>
        </Pressable>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginBottom: 16,
  },
  icon: {
    fontSize: 18,
    marginRight: 12,
    opacity: 0.6,
  },
  input: {
    flex: 1,
    fontSize: 16,
  },
  clearButton: {
    marginLeft: 8,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  clearIcon: {
    fontSize: 18,
  },
});
