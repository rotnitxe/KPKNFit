import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import type { Program } from '../../types/workout';
import { useWorkoutStore } from '../../stores/workoutStore';

interface HistoryTabViewProps {
  program: Program;
}

export const HistoryTabView: React.FC<HistoryTabViewProps> = ({ program }) => {
  const colors = useColors();
  const recentLogs = useWorkoutStore((state) => state.overview?.recentLogs || []);
  
  const programLogs = useMemo(() => 
    recentLogs.filter((log) => log.programName === program.name),
    [recentLogs, program.name]
  );

  return (
    <View style={styles.container}>
      {programLogs.length === 0 ? (
        <View style={[styles.emptyCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}0D` }]}>
          <Text style={{ color: colors.onSurfaceVariant }}>No hay historial registrado aún.</Text>
        </View>
      ) : (
        programLogs.map((log) => (
          <View 
            key={log.id} 
            style={[styles.logCard, { backgroundColor: `${colors.onSurface}0D`, borderColor: `${colors.onSurface}1A` }]}
          >
            <Text style={[styles.sessionName, { color: colors.onSurface }]}>
              {log.sessionName}
            </Text>
            <Text style={[styles.logMeta, { color: colors.onSurfaceVariant }]}>
              {log.date} · {log.completedSetCount} series completadas
            </Text>
          </View>
        ))
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 16,
    paddingTop: 24,
  },
  emptyCard: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
    borderRadius: 16,
    borderWidth: 1,
  },
  logCard: {
    borderWidth: 1,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
  },
  sessionName: {
    fontSize: 14,
    fontWeight: '600',
    lineHeight: 20,
  },
  logMeta: {
    fontSize: 12,
    marginTop: 4,
  },
});
