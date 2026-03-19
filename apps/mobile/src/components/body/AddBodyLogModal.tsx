import React, { useEffect, useState, useCallback } from 'react';
import {
  Modal,
  View,
  Text,
  TextInput,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Pressable,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { useBodyStore } from '../../stores/bodyStore';
import { Button } from '../ui';
import { useColors } from '../../theme';

interface AddBodyLogModalProps {
  visible: boolean;
  onClose: () => void;
  initialLogId?: string | null;
}

const MEASUREMENT_FIELDS = [
  { key: 'Pecho', label: 'Pecho (cm)' },
  { key: 'Cintura', label: 'Cintura (cm)' },
  { key: 'Cadera', label: 'Cadera (cm)' },
  { key: 'Cuello', label: 'Cuello (cm)' },
  { key: 'Bíceps (Izq)', label: 'Bíceps Izq (cm)' },
  { key: 'Bíceps (Der)', label: 'Bíceps Der (cm)' },
  { key: 'Antebrazo (Izq)', label: 'Antebrazo Izq (cm)' },
  { key: 'Antebrazo (Der)', label: 'Antebrazo Der (cm)' },
  { key: 'Muslo (Izq)', label: 'Muslo Izq (cm)' },
  { key: 'Muslo (Der)', label: 'Muslo Der (cm)' },
  { key: 'Pantorrilla (Izq)', label: 'Pantorrilla Izq (cm)' },
  { key: 'Pantorrilla (Der)', label: 'Pantorrilla Der (cm)' },
];

export const AddBodyLogModal: React.FC<AddBodyLogModalProps> = ({
  visible,
  onClose,
  initialLogId,
}) => {
  const colors = useColors();
  const [weight, setWeight] = useState('');
  const [fat, setFat] = useState('');
  const [muscle, setMuscle] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [notes, setNotes] = useState('');
  const [measurements, setMeasurements] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  const initialLog = useBodyStore(
    useCallback(
      (state) => state.bodyProgress.find((e) => e.id === initialLogId) ?? null,
      [initialLogId]
    )
  );
  const addBodyLog = useBodyStore((state) => state.addBodyLog);
  const updateBodyLog = useBodyStore((state) => state.updateBodyLog);
  const isSaving = useBodyStore((state) => state.status === 'ready' && state.notice !== null);

  useEffect(() => {
    if (visible) {
      if (initialLog) {
        setWeight(initialLog.weight?.toString() || '');
        setFat(initialLog.bodyFatPercentage?.toString() || '');
        setMuscle(initialLog.muscleMassPercentage?.toString() || '');
        setDate(initialLog.date?.slice(0, 10) || new Date().toISOString().slice(0, 10));
        setNotes(initialLog.aiInsight || '');
        const meas: Record<string, string> = {};
        if (initialLog.measurements) {
          for (const [key, val] of Object.entries(initialLog.measurements)) {
            meas[key] = val.toString();
          }
        }
        setMeasurements(meas);
      } else {
        setWeight('');
        setFat('');
        setMuscle('');
        setDate(new Date().toISOString().slice(0, 10));
        setNotes('');
        setMeasurements({});
      }
      setError(null);
    }
  }, [visible, initialLog]);

  const handleMeasurementChange = (key: string, value: string) => {
    setMeasurements(prev => ({ ...prev, [key]: value }));
  };

  const handleSave = async () => {
    const w = parseFloat(weight);
    if (isNaN(w) || w <= 0) {
      setError('El peso es obligatorio y debe ser mayor a 0');
      return;
    }

    const parsedMeasurements: Record<string, number> = {};
    for (const [key, val] of Object.entries(measurements)) {
      const num = parseFloat(val);
      if (!isNaN(num) && num > 0) {
        parsedMeasurements[key] = num;
      }
    }

    try {
      if (initialLog) {
        await updateBodyLog(initialLog.id, {
          weight: w,
          bodyFatPercentage: parseFloat(fat) || undefined,
          muscleMassPercentage: parseFloat(muscle) || undefined,
          date: new Date(date).toISOString(),
          aiInsight: notes || undefined,
          measurements: Object.keys(parsedMeasurements).length > 0 ? parsedMeasurements : undefined,
        });
      } else {
        await addBodyLog({
          weight: w,
          bodyFatPercentage: parseFloat(fat) || undefined,
          muscleMassPercentage: parseFloat(muscle) || undefined,
          date: new Date(date).toISOString(),
          aiInsight: notes || undefined,
          measurements: Object.keys(parsedMeasurements).length > 0 ? parsedMeasurements : undefined,
        });
      }
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al guardar el registro.');
    }
  };

  const InputField = ({
    label,
    value,
    onChange,
    placeholder,
    keyboardType = 'decimal-pad',
  }: {
    label: string;
    value: string;
    onChange: (text: string) => void;
    placeholder: string;
    keyboardType?: 'numeric' | 'decimal-pad';
  }) => (
    <View style={styles.inputContainer}>
      <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <TextInput
        value={value}
        onChangeText={onChange}
        placeholder={placeholder}
        placeholderTextColor={`${colors.onSurface}33`}
        keyboardType={keyboardType}
        style={[
          styles.input,
          {
            backgroundColor: `${colors.onSurface}0D`,
            borderColor: colors.outlineVariant,
            color: colors.onSurface,
          },
        ]}
      />
    </View>
  );

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable onPress={onClose} style={[styles.overlay, { backgroundColor: `${colors.background}CC` }]}>
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          style={styles.keyboardView}
        >
          <Pressable
            style={[
              styles.modalContent,
              {
                backgroundColor: colors.surface,
                borderTopColor: colors.outlineVariant,
              },
            ]}
          >
            <View style={styles.handleContainer}>
              <View style={[styles.handle, { backgroundColor: colors.outlineVariant }]} />
              <Text style={[styles.modalTitle, { color: colors.onSurface }]}>
                {initialLog ? 'Editar registro corporal' : 'Nuevo registro corporal'}
              </Text>
            </View>

            <ScrollView showsVerticalScrollIndicator={false} style={styles.scrollArea}>
              <InputField
                label="Peso (kg) *"
                value={weight}
                onChange={(t) => {
                  setWeight(t);
                  setError(null);
                }}
                placeholder="Ej: 75.5"
                keyboardType="decimal-pad"
              />
              <View style={styles.row}>
                <View style={styles.flex1}>
                  <InputField
                    label="% Grasa"
                    value={fat}
                    onChange={setFat}
                    placeholder="Ej: 15"
                    keyboardType="decimal-pad"
                  />
                </View>
                <View style={styles.flex1}>
                  <InputField
                    label="% Músculo"
                    value={muscle}
                    onChange={setMuscle}
                    placeholder="Ej: 40"
                    keyboardType="decimal-pad"
                  />
                </View>
              </View>
              <InputField
                label="Fecha"
                value={date}
                onChange={setDate}
                placeholder="YYYY-MM-DD"
              />

              <View style={styles.row}>
                {MEASUREMENT_FIELDS.slice(0, 4).map(field => (
                  <View key={field.key} style={styles.flex1}>
                    <InputField
                      label={field.label}
                      value={measurements[field.key] || ''}
                      onChange={(v) => handleMeasurementChange(field.key, v)}
                      placeholder="0"
                      keyboardType="decimal-pad"
                    />
                  </View>
                ))}
              </View>
              <View style={styles.row}>
                {MEASUREMENT_FIELDS.slice(4, 8).map(field => (
                  <View key={field.key} style={styles.flex1}>
                    <InputField
                      label={field.label}
                      value={measurements[field.key] || ''}
                      onChange={(v) => handleMeasurementChange(field.key, v)}
                      placeholder="0"
                      keyboardType="decimal-pad"
                    />
                  </View>
                ))}
              </View>
              <View style={styles.row}>
                {MEASUREMENT_FIELDS.slice(8, 12).map(field => (
                  <View key={field.key} style={styles.flex1}>
                    <InputField
                      label={field.label}
                      value={measurements[field.key] || ''}
                      onChange={(v) => handleMeasurementChange(field.key, v)}
                      placeholder="0"
                      keyboardType="decimal-pad"
                    />
                  </View>
                ))}
              </View>

              <View style={styles.inputContainer}>
                <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>Notas</Text>
                <TextInput
                  value={notes}
                  onChangeText={setNotes}
                  placeholder="Notas adicionales..."
                  placeholderTextColor={`${colors.onSurface}33`}
                  multiline
                  numberOfLines={3}
                  style={[
                    styles.input,
                    styles.textArea,
                    {
                      backgroundColor: `${colors.onSurface}0D`,
                      borderColor: colors.outlineVariant,
                      color: colors.onSurface,
                    },
                  ]}
                />
              </View>

              {error && <Text style={[styles.errorText, { color: colors.error }]}>{error}</Text>}
            </ScrollView>

            <View style={styles.footer}>
              <View style={styles.flex1}>
                <Button variant="secondary" onPress={onClose}>
                  Cancelar
                </Button>
              </View>
              <View style={styles.flex1}>
                <Button onPress={handleSave} disabled={!weight}>
                  {isSaving ? (
                    <ActivityIndicator size="small" color={colors.onPrimary} />
                  ) : (
                    'Guardar'
                  )}
                </Button>
              </View>
            </View>
          </Pressable>
        </KeyboardAvoidingView>
      </Pressable>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  keyboardView: {
    width: '100%',
  },
  modalContent: {
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
    padding: 24,
    paddingBottom: 40,
    borderTopWidth: 1,
  },
  handleContainer: {
    alignItems: 'center',
    marginBottom: 24,
  },
  handle: {
    width: 48,
    height: 6,
    borderRadius: 3,
    marginBottom: 24,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: '700',
  },
  scrollArea: {
    maxHeight: 400,
  },
  inputContainer: {
    marginBottom: 16,
  },
  inputLabel: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    marginBottom: 8,
    marginLeft: 4,
  },
  input: {
    borderWidth: 1,
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
  },
  textArea: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  row: {
    flexDirection: 'row',
    gap: 16,
  },
  flex1: {
    flex: 1,
  },
  errorText: {
    fontSize: 13,
    marginTop: 8,
    textAlign: 'center',
  },
  footer: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 32,
  },
});
