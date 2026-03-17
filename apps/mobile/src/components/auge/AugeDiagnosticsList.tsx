import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';

interface AugeDiagnosticsListProps {
  diagnostics: string[];
}

export function AugeDiagnosticsList({ diagnostics }: AugeDiagnosticsListProps) {
  const colors = useColors();

  if (!diagnostics || diagnostics.length === 0) {
    return (
      <View style={styles.container}>
        <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
          Sin diagnósticos adicionales.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>Diagnósticos AUGE</Text>
      {diagnostics.map((diag, index) => (
        <View
          key={index}
          style={[styles.diagnosticItem, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
        >
          <Text style={[styles.diagnosticText, { color: colors.onSurface }]} numberOfLines={2}>
            • {diag}
          </Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginTop: 16,
    gap: 8,
    paddingHorizontal: 4,
  },
  title: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
    paddingLeft: 4,
  },
  emptyText: {
    fontSize: 12,
    fontStyle: 'italic',
  },
  diagnosticItem: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 12,
  },
  diagnosticText: {
    fontSize: 12,
    lineHeight: 18,
  },
});
