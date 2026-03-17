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

export const AddBodyLogModal: React.FC<AddBodyLogModalProps> = ({
  visible,
  onClose,
  initialLogId,
}) => {
  const colors = useColors();
  const [weight, setWeight] = useState('');
  const [fat, setFat] = useState('');
  const [muscle, setMuscle] = useState('');
  const [waist, setWaist] = useState('');
  const [hip, setHip] = useState('');
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
        setWaist(initialLog.measurements?.waist?.toString() || '');
        setHip(initialLog.measurements?.hip?.toString() || '');
      } else {
        setWeight('');
        setFat('');
        setMuscle('');
        setWaist('');
        setHip('');
      }
      setError(null);
    }
  }, [visible, initialLog]);

  const handleSave = async () => {
    const w = parseFloat(weight);
    if (isNaN(w) || w <= 0) {
      setError('El peso es obligatorio y debe ser mayor a 0');
      return;
    }

    const measurements: Record<string, number> = {};
    const waistVal = parseFloat(waist);
    const hipVal = parseFloat(hip);
    if (!isNaN(waistVal) && waistVal > 0) measurements.waist = waistVal;
    if (!isNaN(hipVal) && hipVal > 0) measurements.hip = hipVal;

    try {
      if (initialLog) {
        await updateBodyLog(initialLog.id, {
          weight: w,
          bodyFatPercentage: parseFloat(fat) || undefined,
          muscleMassPercentage: parseFloat(muscle) || undefined,
          measurements: Object.keys(measurements).length > 0 ? measurements : undefined,
        });
      } else {
        await addBodyLog({
          weight: w,
          bodyFatPercentage: parseFloat(fat) || undefined,
          muscleMassPercentage: parseFloat(muscle) || undefined,
          measurements: Object.keys(measurements).length > 0 ? measurements : undefined,
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
              <View style={styles.row}>
                <View style={styles.flex1}>
                  <InputField
                    label="Cintura (cm)"
                    value={waist}
                    onChange={setWaist}
                    placeholder="Ej: 80"
                    keyboardType="decimal-pad"
                  />
                </View>
                <View style={styles.flex1}>
                  <InputField
                    label="Cadera (cm)"
                    value={hip}
                    onChange={setHip}
                    placeholder="Ej: 95"
                    keyboardType="decimal-pad"
                  />
                </View>
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
