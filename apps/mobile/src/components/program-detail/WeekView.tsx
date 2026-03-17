import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import type { ProgramWeek, Session } from '../../types/workout';
import { SessionCard } from './SessionCard';

interface WeekViewProps {
  week: ProgramWeek;
  weekNumber: number;
  onSessionPress?: (session: Session) => void;
}

export const WeekView: React.FC<WeekViewProps> = ({ week, weekNumber, onSessionPress }) => {
  const colors = useColors();
  return (
    <View style={styles.container}>
      <Text style={[styles.weekHeader, { color: colors.onSurfaceVariant }]}>
        Semana {weekNumber} · {week.name}
      </Text>
      
      {!week.sessions || week.sessions.length === 0 ? (
        <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
          Sin sesiones configuradas
        </Text>
      ) : (
        week.sessions.map((session, index) => (
          <SessionCard 
            key={session.id || index.toString()} 
            session={session} 
            dayIndex={index} 
            onPress={onSessionPress ? () => onSessionPress(session) : undefined}
          />
        ))
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginBottom: 24,
  },
  weekHeader: {
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 2,
    marginBottom: 12,
  },
  emptyText: {
    fontSize: 14,
    fontStyle: 'italic',
  },
});
