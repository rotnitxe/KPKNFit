import React, { memo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Button } from '../ui/Button';
import { useColors } from '../../theme';

interface EmptyProgramsStateProps {
  onCreatePress?: () => void;
}

export const EmptyProgramsState = memo(({ onCreatePress }: EmptyProgramsStateProps) => {
  const colors = useColors();

  return (
    <View style={styles.container}>
      <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}>
        <Text style={styles.icon}>{'\u{1F4AA}'}</Text>
      </View>
      <Text style={[styles.title, { color: colors.onSurface }]}>Comienza Hoy</Text>
      <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
        Aun no tienes programas configurados
      </Text>
      <Button 
        onPress={onCreatePress || (() => console.log('Create first program'))}
        style={styles.button}
      >
        Crear primer programa
      </Button>
    </View>
  );
});

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  iconBox: {
    height: 80,
    width: 80,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 32,
  },
  icon: {
    fontSize: 40,
  },
  title: {
    marginTop: 32,
    fontSize: 24,
    fontWeight: 'bold',
    textTransform: 'uppercase',
  },
  subtitle: {
    marginTop: 8,
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    textAlign: 'center',
  },
  button: {
    marginTop: 40,
  },
});

export default EmptyProgramsState;