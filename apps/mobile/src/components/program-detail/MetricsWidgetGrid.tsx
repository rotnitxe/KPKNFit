import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import type { Program } from '../../types/workout';

interface MetricsWidgetGridProps {
  program: Program;
}

/**
 * MetricsWidgetGrid
 * Una grilla de 2 columnas para mostrar métricas clave del programa de forma segura.
 */
export const MetricsWidgetGrid: React.FC<MetricsWidgetGridProps> = ({ program }) => {
  const colors = useColors();
  
  // Lógica defensiva para cálculo de sesiones por semana
  const sessionsPerWeek = program.macrocycles?.[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.[0]?.sessions?.length || 0;
  
  // Cantidad de bloques en la estructura
  const blockCount = program.macrocycles?.[0]?.blocks?.length || 1;

  // Widget Interno
  const Widget = ({ label, value }: { label: string; value: string | number }) => (
    <View style={[styles.widget, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}0D` }]}>
      <Text style={[styles.widgetLabel, { color: colors.onSurfaceVariant }]}>
        {label}
      </Text>
      <Text style={[styles.widgetValue, { color: colors.onSurface }]}>
        {value}
      </Text>
    </View>
  );

  return (
    <View style={styles.grid}>
      <Widget 
        label="Frecuencia" 
        value={`${sessionsPerWeek} d/sem`} 
      />
      
      <Widget 
        label="Fase" 
        value={program.mode === 'powerlifting' ? 'Fuerza' : (program.mode === 'hypertrophy' ? 'Hipertrof.' : 'Híbrido')} 
      />
      
      <Widget 
        label="Intensidad" 
        value="RPE 8-9" 
      />
      
      <Widget 
        label="Bloques" 
        value={`${blockCount} Bloque${blockCount !== 1 ? 's' : ''}`} 
      />
    </View>
  );
};

const styles = StyleSheet.create({
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  widget: {
    width: '48%',
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  widgetLabel: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 1,
    fontWeight: '500',
  },
  widgetValue: {
    fontSize: 14,
    fontWeight: '600',
    marginTop: 4,
  },
});
