import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, TextInput, ScrollView } from 'react-native';
import { useColors } from '../../../theme';

interface CalendarStepProps {
  programName: string;
  onProgramNameChange: (name: string) => void;
  programDescription: string;
  onProgramDescriptionChange: (description: string) => void;
  trainingDays: number[];
  onTrainingDaysChange: (days: number[]) => void;
}

const DAYS_OF_WEEK = [
  { label: 'LUN', value: 1 },
  { label: 'MAR', value: 2 },
  { label: 'MIÉ', value: 3 },
  { label: 'JUE', value: 4 },
  { label: 'VIE', value: 5 },
  { label: 'SÁB', value: 6 },
  { label: 'DOM', value: 0 },
];

const CalendarStep: React.FC<CalendarStepProps> = ({
  programName,
  onProgramNameChange,
  programDescription,
  onProgramDescriptionChange,
  trainingDays,
  onTrainingDaysChange,
}) => {
  const colors = useColors();

  const toggleDay = (dayValue: number) => {
    const newDays = trainingDays.includes(dayValue)
      ? trainingDays.filter(d => d !== dayValue)
      : [...trainingDays, dayValue];
    onTrainingDaysChange(newDays.sort((a, b) => a - b));
  };

  const trainingDaysCount = trainingDays.length;

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      <View style={styles.section}>
        <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>NOMBRE DEL PROGRAMA</Text>
        <TextInput
          value={programName}
          onChangeText={onProgramNameChange}
          placeholder="Ej: Programa de Verano"
          placeholderTextColor={colors.onSurfaceVariant + '60'}
          style={[
            styles.input,
            {
              color: colors.onSurface,
              backgroundColor: colors.surfaceContainerHigh,
              borderColor: colors.outlineVariant,
            },
          ]}
        />
      </View>

      <View style={styles.section}>
        <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>DESCRIPCIÓN</Text>
        <TextInput
          value={programDescription}
          onChangeText={onProgramDescriptionChange}
          placeholder="Describe los objetivos del programa..."
          placeholderTextColor={colors.onSurfaceVariant + '60'}
          multiline
          numberOfLines={4}
          style={[
            styles.input,
            styles.textarea,
            {
              color: colors.onSurface,
              backgroundColor: colors.surfaceContainerHigh,
              borderColor: colors.outlineVariant,
            },
          ]}
        />
      </View>

      <View style={styles.section}>
        <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>DÍAS DE ENTRENAMIENTO</Text>
        <View style={styles.grid}>
          {DAYS_OF_WEEK.map(day => {
            const isSelected = trainingDays.includes(day.value);
            return (
              <TouchableOpacity
                key={day.value}
                onPress={() => toggleDay(day.value)}
                style={[
                  styles.dayButton,
                  {
                    backgroundColor: isSelected ? colors.primary : colors.surfaceVariant,
                  },
                ]}
              >
                <Text
                  style={[
                    styles.dayButtonText,
                    { color: isSelected ? colors.onPrimary : colors.onSurfaceVariant },
                  ]}
                >
                  {day.label}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>
        <Text style={[styles.hint, { color: colors.onSurfaceVariant }]}>
          {trainingDaysCount > 0
            ? `${trainingDaysCount} días/semana`
            : 'Selecciona al menos un día'}
        </Text>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  section: {
    marginBottom: 24,
  },
  label: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1,
    marginBottom: 8,
  },
  input: {
    height: 56,
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 20,
    fontSize: 16,
    fontWeight: '700',
  },
  textarea: {
    height: 100,
    paddingTop: 16,
    paddingBottom: 16,
    textAlignVertical: 'top',
  },
  grid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
  },
  dayButton: {
    flex: 1,
    height: 48,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dayButtonText: {
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  hint: {
    fontSize: 12,
    marginTop: 8,
    textAlign: 'center',
    fontWeight: '700',
  },
});

export default CalendarStep;