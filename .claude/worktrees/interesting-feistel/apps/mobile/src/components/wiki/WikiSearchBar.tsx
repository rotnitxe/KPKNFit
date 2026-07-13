import React from 'react';
import { View, TextInput, TouchableOpacity, Text, StyleSheet } from 'react-native';
import { useColors } from '@/theme';

interface WikiSearchBarProps {
  query: string;
  onChangeQuery: (value: string) => void;
  onClear?: () => void;
  placeholder?: string;
}

export const WikiSearchBar: React.FC<WikiSearchBarProps> = ({
  query,
  onChangeQuery,
  onClear,
  placeholder = 'Buscar en la Wiki...',
}) => {
  const colors = useColors();

  return (
    <View style={[styles.container, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <Text style={styles.searchIcon}>🔍</Text>
      <TextInput
        style={[styles.input, { color: colors.onSurface }]}
        placeholder={placeholder}
        placeholderTextColor={`${colors.onSurface}4D`}
        value={query}
        onChangeText={onChangeQuery}
        autoCorrect={false}
      />
      {query.length > 0 && (
        <TouchableOpacity onPress={onClear || (() => onChangeQuery(''))}>
          <Text style={[styles.clearIcon, { color: colors.onSurfaceVariant }]}>✕</Text>
        </TouchableOpacity>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginBottom: 16,
  },
  searchIcon: {
    fontSize: 20,
    marginRight: 12,
  },
  input: {
    flex: 1,
    fontSize: 16,
  },
  clearIcon: {
    fontSize: 20,
    marginLeft: 12,
  },
});
