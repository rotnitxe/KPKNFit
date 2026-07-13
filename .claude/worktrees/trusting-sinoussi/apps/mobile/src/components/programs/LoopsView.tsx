import React, { useMemo } from 'react';
import { View, Text, StyleSheet, Switch } from 'react-native';
import { useColors } from '../../theme';
import type { Loop } from '../../types/workout';

interface LoopsViewProps {
  currentLoop: Loop | null;
  isActive: boolean;
  onToggle?: (enabled: boolean) => void;
}

const LoopsView: React.FC<LoopsViewProps> = ({ currentLoop, isActive, onToggle }) => {
  const colors = useColors();
  if (!currentLoop) return <Text style={{ color: colors.onSurfaceVariant }}>No hay protocolo activo</Text>;
  return (
    <View style={styles.container}>
      <Text style={[styles.title, { color: colors.primary }]}>{currentLoop.title} ({currentLoop.type?.toUpperCase()})</Text>
      <Text style={[styles.param, { color: colors.onSurface }]}>{currentLoop.sessions?.length ?? 0} sesiones asignadas</Text>
      {/* Parámetros de progresión (ejemplo): repetición, duración */}
      <Text style={[styles.param, { color: colors.onSurfaceVariant }]}>Repite cada {currentLoop.repeatEveryXLoops} ciclos</Text>
      <View style={styles.toggleRow}>
        <Text style={[styles.toggleLabel, { color: colors.onSurface }]}>Activo</Text>
        <Switch value={isActive} onValueChange={v=>onToggle?.(v)} />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 18,
    borderRadius: 14,
    backgroundColor: 'rgba(255,255,255,0.04)',
  },
  title: {
    fontWeight: '800',
    fontSize: 14,
    marginBottom: 7,
  },
  param: {
    fontWeight: '500',
    fontSize: 12,
    marginBottom: 5,
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    marginTop: 8,
  },
  toggleLabel: {
    fontSize: 13,
    fontWeight: '600',
  },
});

export default React.memo(LoopsView);
