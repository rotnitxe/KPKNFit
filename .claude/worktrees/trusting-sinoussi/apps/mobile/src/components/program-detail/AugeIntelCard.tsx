import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import type { Program } from '../../types/workout';

interface AugeIntelCardProps {
  program: Program;
}

/**
 * AugeIntelCard
 * Presenta un análisis de IA sobre la estructura del programa.
 * Utiliza un diseño de tarjeta "cyber" con bordes acentuados.
 */
export const AugeIntelCard: React.FC<AugeIntelCardProps> = ({ program }) => {
  const colors = useColors();
  const volumeType = program.volumeSystem === 'israetel' ? 'optimizada' : 'estándar';
  const structureType = program.structure === 'complex' ? 'periodizada y adaptativa' : 'lineal de progresión';

  return (
    <View style={[styles.container, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.primary}4D` }]}>
      <View style={styles.header}>
        <View style={[styles.dot, { backgroundColor: colors.primary }]} />
        <Text style={[styles.badge, { color: colors.primary }]}>
          AUGE Engine
        </Text>
      </View>
      
      <Text style={[styles.title, { color: colors.onSurface }]}>
        Análisis de Estructura
      </Text>
      
      <Text style={[styles.description, { color: colors.onSurfaceVariant }]}>
        Este programa utiliza un sistema de gestión de volumen {volumeType} con una arquitectura de entrenamiento {structureType}. 
        {program.mode === 'powerlifting' && ' Se prioriza la especificidad en los levantamientos SBD.'}
        {program.mode === 'hypertrophy' && ' Se enfoca en la acumulación de volumen y estrés metabólico.'}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    borderWidth: 1,
    borderRadius: 20,
    padding: 20,
    marginBottom: 16,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 8,
  },
  badge: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    fontWeight: 'bold',
  },
  title: {
    fontSize: 16,
    fontWeight: '600',
    marginTop: 8,
    marginBottom: 4,
  },
  description: {
    fontSize: 14,
    lineHeight: 20,
  },
});
