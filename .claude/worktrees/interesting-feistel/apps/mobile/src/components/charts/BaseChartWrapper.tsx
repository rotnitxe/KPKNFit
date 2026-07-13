import React, { memo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '@/theme';

interface BaseChartWrapperProps {
  title?: string;
  subtitle?: string;
  children: React.ReactNode;
}

export const BaseChartWrapper = memo(({ title, subtitle, children }: BaseChartWrapperProps) => {
  const colors = useColors();

  return (
    <View style={[styles.container, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      {(title || subtitle) && (
        <View style={styles.header}>
          {title && <Text style={[styles.title, { color: colors.onSurface }]}>{title}</Text>}
          {subtitle && <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>{subtitle}</Text>}
        </View>
      )}
      {children}
    </View>
  );
});

const styles = StyleSheet.create({
  container: {
    borderWidth: 1,
    borderRadius: 24,
    padding: 16,
  },
  header: {
    marginBottom: 12,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
  },
  subtitle: {
    fontSize: 14,
    marginTop: 4,
  },
});
