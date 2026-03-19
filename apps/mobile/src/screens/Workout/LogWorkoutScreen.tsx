import React, { useCallback, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { useColors } from '../../theme';
import { getLocalDateString } from '../../utils/dateUtils';
import { persistLocalWorkoutLog } from '../../services/mobilePersistenceService';
import { generateId } from '../../utils/generateId';
import { useWorkoutStore } from '../../stores/workoutStore';

export function LogWorkoutScreen() {
  const colors = useColors();
  const refreshInfrastructure = useWorkoutStore(state => state.refreshInfrastructure);
  const [date, setDate] = useState(getLocalDateString());
  const [programName, setProgramName] = useState('Programa libre');
  const [sessionName, setSessionName] = useState('');
  const [exerciseCount, setExerciseCount] = useState('6');
  const [setCount, setSetCount] = useState('18');
  const [duration, setDuration] = useState('60');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const submit = useCallback(async () => {
    const normalizedSession = sessionName.trim();
    if (!normalizedSession) {
      setNotice('Debes indicar el nombre de la sesión.');
      return;
    }

    const parsedExercises = Number(exerciseCount);
    const parsedSets = Number(setCount);
    const parsedDuration = Number(duration);
    if (!Number.isFinite(parsedExercises) || parsedExercises <= 0) {
      setNotice('El número de ejercicios debe ser mayor a 0.');
      return;
    }
    if (!Number.isFinite(parsedSets) || parsedSets <= 0) {
      setNotice('El número de sets debe ser mayor a 0.');
      return;
    }

    setSaving(true);
    setNotice(null);
    try {
      await persistLocalWorkoutLog({
        id: generateId(),
        date,
        programName: programName.trim() || 'Programa libre',
        sessionName: normalizedSession,
        exerciseCount: parsedExercises,
        completedSetCount: parsedSets,
        durationMinutes: Number.isFinite(parsedDuration) && parsedDuration > 0 ? parsedDuration : null,
      });
      await refreshInfrastructure();
      setNotice('Entreno guardado correctamente.');
      setSessionName('');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'No pudimos guardar el entreno manual.');
    } finally {
      setSaving(false);
    }
  }, [date, duration, exerciseCount, programName, refreshInfrastructure, sessionName, setCount]);

  return (
    <ScreenShell
      title="Registrar entreno"
      subtitle="Carga manual rápida para cerrar huecos del historial"
    >
      <View style={styles.container}>
        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.cardTitle, { color: colors.onSurface }]}>Datos del registro</Text>
          <Text style={[styles.cardSub, { color: colors.onSurfaceVariant }]}>
            Este flujo alimenta `workout_logs_local` y refresca widgets/recordatorios.
          </Text>

          <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Fecha (YYYY-MM-DD)</Text>
          <TextInput
            value={date}
            onChangeText={setDate}
            placeholder="2026-03-18"
            placeholderTextColor={`${colors.onSurfaceVariant}99`}
            style={[styles.input, { color: colors.onSurface, borderColor: `${colors.onSurface}33` }]}
          />

          <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Programa</Text>
          <TextInput
            value={programName}
            onChangeText={setProgramName}
            placeholder="Programa libre"
            placeholderTextColor={`${colors.onSurfaceVariant}99`}
            style={[styles.input, { color: colors.onSurface, borderColor: `${colors.onSurface}33` }]}
          />

          <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Sesión</Text>
          <TextInput
            value={sessionName}
            onChangeText={setSessionName}
            placeholder="Upper A"
            placeholderTextColor={`${colors.onSurfaceVariant}99`}
            style={[styles.input, { color: colors.onSurface, borderColor: `${colors.onSurface}33` }]}
          />

          <View style={styles.row}>
            <View style={styles.half}>
              <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Ejercicios</Text>
              <TextInput
                value={exerciseCount}
                onChangeText={setExerciseCount}
                keyboardType="numeric"
                style={[styles.input, { color: colors.onSurface, borderColor: `${colors.onSurface}33` }]}
              />
            </View>
            <View style={styles.half}>
              <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Sets</Text>
              <TextInput
                value={setCount}
                onChangeText={setSetCount}
                keyboardType="numeric"
                style={[styles.input, { color: colors.onSurface, borderColor: `${colors.onSurface}33` }]}
              />
            </View>
          </View>

          <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Duración (min)</Text>
          <TextInput
            value={duration}
            onChangeText={setDuration}
            keyboardType="numeric"
            style={[styles.input, { color: colors.onSurface, borderColor: `${colors.onSurface}33` }]}
          />

          {notice ? (
            <Text style={[styles.notice, { color: notice.includes('correctamente') ? colors.cyberSuccess : colors.error }]}>
              {notice}
            </Text>
          ) : null}

          <Pressable
            onPress={() => void submit()}
            disabled={saving}
            style={[styles.saveButton, { backgroundColor: colors.primary, opacity: saving ? 0.65 : 1 }]}
          >
            <Text style={[styles.saveButtonText, { color: colors.onPrimary }]}>
              {saving ? 'Guardando...' : 'Guardar entreno'}
            </Text>
          </Pressable>
        </View>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  card: {
    borderRadius: 18,
    borderWidth: 1,
    padding: 14,
    gap: 8,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '800',
  },
  cardSub: {
    fontSize: 12,
    marginBottom: 8,
  },
  label: {
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  input: {
    minHeight: 44,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 12,
    fontSize: 14,
  },
  row: {
    flexDirection: 'row',
    gap: 10,
  },
  half: {
    flex: 1,
    gap: 8,
  },
  notice: {
    fontSize: 13,
    fontWeight: '700',
    marginTop: 4,
  },
  saveButton: {
    minHeight: 48,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
  },
  saveButtonText: {
    fontSize: 14,
    fontWeight: '800',
  },
});

