import React, { useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import type { ProgramWeek, Session } from '../../types/workout';
import { ChevronRightIcon, PencilIcon, TrashIcon } from '../icons';
import { useColors } from '../../theme';

export interface DayViewProps {
  week: ProgramWeek;
  onSessionEdit?: (sessionId: string) => void;
  onSessionPlay?: (sessionId: string) => void;
  onSessionDelete?: (sessionId: string) => void;
}

function estimateDuration(session: Session) {
  // Regla simple: 5' por set
  const setCount = session.exercises?.reduce((acc, ex) => acc + (ex.sets?.length || 0), 0) || 0;
  return setCount * 5;
}

const DayView: React.FC<DayViewProps> = ({ week, onSessionEdit, onSessionPlay, onSessionDelete }) => {
  const colors = useColors();
  // Agrupa sesiones por día de la semana
  const sessionsByDay = useMemo(() => {
    const dias = [1,2,3,4,5,6,7];
    const byDay: Record<number, Session[]> = {};
    dias.forEach(d => byDay[d] = []);
    (week.sessions ?? []).forEach(s => byDay[s.dayOfWeek || 1].push(s));
    return byDay;
  }, [week.sessions]);

  const dayName = (n: number) => ['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo'][n-1];

  return (
    <View style={styles.container}>
      <ScrollView>
        {Object.entries(sessionsByDay).map(([dow, sessions]) => (
          <View key={dow} style={styles.daySection}>
            <Text style={[styles.dayLabel, { color: colors.primary }]}>{dayName(Number(dow))}</Text>
            {sessions.length === 0 && (
              <Text style={{ color: colors.onSurfaceVariant, fontSize: 12 }}>Sin sesión</Text>
            )}
            {sessions.map(session => (
              <TouchableOpacity key={session.id} style={styles.sessionCard}>
                <View style={styles.sessionHeader}>
                  <Text style={[styles.sessionName, { color: colors.onSurface }]}>{session.name}</Text>
                  <View style={styles.btnRow}>
                    <TouchableOpacity onPress={() => onSessionPlay?.(session.id)} style={styles.actionBtn}><ChevronRightIcon size={15} color={colors.primary} /></TouchableOpacity>
                    <TouchableOpacity onPress={() => onSessionEdit?.(session.id)} style={styles.actionBtn}><PencilIcon size={15} color={colors.onSurfaceVariant} /></TouchableOpacity>
                    <TouchableOpacity onPress={() => onSessionDelete?.(session.id)} style={styles.actionBtn}><TrashIcon size={15} color={colors.error} /></TouchableOpacity>
                  </View>
                </View>
                <Text style={[styles.sessionMeta, { color: colors.onSurfaceVariant }]}>Ejercicios: {session.exercises.length} · Series: {session.exercises.reduce((a, e) => a + (e.sets?.length || 0), 0)}</Text>
                {/* Estimación de duración/drain */}
                <Text style={{ color: colors.secondary, fontWeight: '900', fontSize: 11 }}>Est: {estimateDuration(session)} min</Text>
                {/* Preview ejercicios nombre+sets */}
                <View style={styles.exercisePreviewRow}>
                  {session.exercises.slice(0,3).map(ej => (
                    <Text key={ej.id} style={{ color: colors.onSurfaceVariant, fontSize: 11 }}>{ej.name} ({ej.sets.length}x)</Text>
                  ))}
                </View>
              </TouchableOpacity>
            ))}
          </View>
        ))}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingBottom: 24,
  },
  daySection: {
    marginBottom: 20,
  },
  dayLabel: {
    fontWeight: '900',
    fontSize: 13,
    marginBottom: 6,
    letterSpacing: 1,
  },
  sessionCard: {
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderRadius: 12,
    padding: 12,
    marginBottom: 10,
  },
  sessionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  sessionName: {
    fontWeight: '800',
    fontSize: 14,
  },
  btnRow: {
    flexDirection: 'row',
    gap: 4,
  },
  actionBtn: {
    marginLeft: 4,
  },
  sessionMeta: {
    fontSize: 11,
    fontWeight: '500',
    marginBottom: 3,
  },
  exercisePreviewRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 2,
  },
});

export default React.memo(DayView);
