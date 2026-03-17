import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import type { Program } from '../../types/workout';
import { AugeIntelCard } from './AugeIntelCard';
import { MetricsWidgetGrid } from './MetricsWidgetGrid';

interface AnalyticsTabViewProps {
  program: Program;
}

/**
 * AnalyticsTabView
 * Orquestador principal de la pestaña de analíticas en el detalle del programa.
 */
export const AnalyticsTabView: React.FC<AnalyticsTabViewProps> = ({ program }) => {
  const colors = useColors();
  return (
    <View style={styles.container}>
      <AugeIntelCard program={program} />
      
      <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>
        Métricas Clave
      </Text>
      
      <MetricsWidgetGrid program={program} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingVertical: 24,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 16,
    marginTop: 8,
  },
});
