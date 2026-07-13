import React from 'react';
import { View, Text, StyleSheet, Modal, TouchableOpacity, Pressable } from 'react-native';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { useColors } from '../../theme';
import ReactNativeHapticFeedback from '@/services/hapticsService';

interface SessionDayPickerModalProps {
  visible: boolean;
  onClose: () => void;
  onSelect: (day: number) => void;
  currentDay?: number;
}

const DAYS = [
  { id: 1, label: 'Lunes' },
  { id: 2, label: 'Martes' },
  { id: 3, label: 'Miércoles' },
  { id: 4, label: 'Jueves' },
  { id: 5, label: 'Viernes' },
  { id: 6, label: 'Sábado' },
  { id: 7, label: 'Domingo' },
  { id: 0, label: 'Flexible / Off' },
];

export const SessionDayPickerModal: React.FC<SessionDayPickerModalProps> = ({
  visible,
  onClose,
  onSelect,
  currentDay,
}) => {
  const colors = useColors();

  const handleSelect = (day: number) => {
    ReactNativeHapticFeedback.trigger('selection');
    onSelect(day);
    onClose();
  };

  return (
    <Modal transparent visible={visible} animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
        <LiquidGlassCard style={styles.card}>
          <Text style={[styles.title, { color: colors.onSurface }]}>Asignar Día</Text>
          <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>Elige el día de la semana para esta sesión.</Text>
          
          <View style={styles.grid}>
            {DAYS.map((day) => {
              const isActive = day.id === currentDay;
              return (
                <TouchableOpacity
                  key={day.id}
                  onPress={() => handleSelect(day.id)}
                  style={[
                    styles.dayBtn,
                    { backgroundColor: colors.surfaceContainer },
                    isActive && { backgroundColor: `${colors.primary}20`, borderColor: colors.primary }
                  ]}
                >
                  <Text style={[styles.dayLabel, { color: colors.onSurface }, isActive && { color: colors.primary }]}>
                    {day.label}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>

          <TouchableOpacity style={[styles.closeBtn, { backgroundColor: colors.surfaceVariant }]} onPress={onClose}>
            <Text style={[styles.closeBtnTxt, { color: colors.onSurface }]}>Cerrar</Text>
          </TouchableOpacity>
        </LiquidGlassCard>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.3)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    padding: 24,
    gap: 16,
  },
  title: {
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 13,
    fontWeight: '500',
    opacity: 0.7,
    marginTop: -8,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginTop: 8,
  },
  dayBtn: {
    width: '48%',
    paddingVertical: 14,
    borderRadius: 14,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  dayLabel: {
    fontSize: 14,
    fontWeight: '800',
  },
  closeBtn: {
    marginTop: 12,
    height: 52,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
  },
  closeBtnTxt: {
    fontSize: 14,
    fontWeight: '900',
  },
});
