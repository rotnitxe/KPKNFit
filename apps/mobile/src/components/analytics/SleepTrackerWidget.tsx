import React, { useState, useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, TextInput, Modal } from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { Button } from '../ui/Button';
import { getLocalDateString } from '../../utils/dateUtils';
import { patchStoredWellbeingPayload, readStoredWellbeingPayload } from '../../services/mobileDomainStateService';

const WEEK_DAYS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

export const SleepTrackerWidget = () => {
  const colors = useColors();
  const [showAddModal, setShowAddModal] = useState(false);
  const [sleepHours, setSleepHours] = useState('');

  const wellbeingPayload = readStoredWellbeingPayload();
  const sleepLogs = Array.isArray(wellbeingPayload.sleepLogs) ? wellbeingPayload.sleepLogs : [];
  
  const last7Days = useMemo(() => {
    const today = new Date();
    return WEEK_DAYS.map((day, idx) => {
      const dayDate = new Date(today);
      const dayDiff = idx - today.getDay() + 1;
      dayDate.setDate(today.getDate() + dayDiff);
      
      const dayStr = dayDate.toISOString().slice(0, 10);
      const dayLog = sleepLogs.find((log: any) => log.date === dayStr);
      
      return {
        day,
        date: dayStr,
        hours: dayLog?.duration || 0,
        quality: dayLog?.quality || 0,
      };
    });
  }, [sleepLogs]);
  
  const avgSleep = useMemo(() => {
    const validLogs = last7Days.filter(d => d.hours > 0);
    if (validLogs.length === 0) return 0;
    const total = validLogs.reduce((sum, d) => sum + d.hours, 0);
    return total / validLogs.length;
  }, [last7Days]);
  
  const getSleepColor = (hours: number) => {
    if (hours >= 8) return colors.primary;
    if (hours >= 6) return colors.tertiary;
    return colors.error;
  };
  
  const handleAddSleep = () => {
    const hours = parseFloat(sleepHours);
    if (isNaN(hours) || hours < 0 || hours > 24) return;
    
    const today = getLocalDateString();
    const newLog = {
      id: `sleep_${Date.now()}`,
      date: today,
      startTime: `${today}T00:00:00`,
      endTime: `${today}T${Math.floor(hours)}:00:00`,
      duration: hours,
      quality: Math.min(Math.max((hours / 8) * 10, 0), 10),
    };
    patchStoredWellbeingPayload({
      sleepLogs: [newLog, ...sleepLogs],
    });
    setShowAddModal(false);
    setSleepHours('');
  };
  
  return (
    <LiquidGlassCard style={styles.card}>
      <View style={styles.header}>
        <View>
          <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Seguimiento de Sueño</Text>
          <Text style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>
            Promedio 7 días: {avgSleep.toFixed(1)}h
          </Text>
        </View>
        <TouchableOpacity 
          style={[styles.addButton, { backgroundColor: colors.primary }]}
          onPress={() => setShowAddModal(true)}
        >
          <Text style={styles.addButtonText}>+</Text>
        </TouchableOpacity>
      </View>

      <Text style={[styles.sectionHeader, { color: colors.onSurfaceVariant }]}>Consistencia Semanal</Text>
      <View style={styles.weeklyBars}>
        {last7Days.map((day) => (
          <View key={day.date} style={styles.dayColumn}>
            <Text style={[styles.dayLabel, { color: colors.onSurfaceVariant }]}>{day.day}</Text>
            <View style={styles.barContainer}>
              <View 
                style={[
                  styles.barFill,
                  { height: `${Math.min((day.hours / 10) * 100, 100)}%`, backgroundColor: getSleepColor(day.hours) }
                ]} 
              />
              <View style={[styles.targetLine, { backgroundColor: `${colors.onSurfaceVariant}33` }]} />
            </View>
            <Text style={[styles.hoursLabel, { color: colors.onSurface }]}>
              {day.hours > 0 ? `${day.hours}h` : '-'}
            </Text>
          </View>
        ))}
      </View>
      
      {avgSleep < 7 && (
        <View style={[styles.alertBanner, { backgroundColor: `${colors.error}15` }]}>
          <Text style={[styles.alertText, { color: colors.error }]}>
            Tu promedio de sueño está bajo el objetivo de 7-8h
          </Text>
        </View>
      )}

      <Modal
        visible={showAddModal}
        transparent
        animationType="fade"
        onRequestClose={() => setShowAddModal(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={[styles.modalContent, { backgroundColor: colors.background }]}>
            <Text style={[styles.modalTitle, { color: colors.onSurface }]}>
              Registrar Sueño
            </Text>
            <Text style={[styles.modalSubtitle, { color: colors.onSurfaceVariant }]}>
              ¿Cuántas horas dormiste anoche?
            </Text>
            
            <TextInput
              value={sleepHours}
              onChangeText={setSleepHours}
              placeholder="8"
              keyboardType="numeric"
              style={[
                styles.input,
                { 
                  backgroundColor: colors.surfaceContainer,
                  color: colors.onSurface,
                  borderColor: colors.outlineVariant,
                }
              ]}
            />
            
            <View style={styles.modalButtons}>
              <Button 
                variant="secondary" 
                onPress={() => setShowAddModal(false)}
                style={styles.modalButton}
              >
                Cancelar
              </Button>
              <Button 
                onPress={handleAddSleep}
                style={styles.modalButton}
              >
                Guardar
              </Button>
            </View>
          </View>
        </View>
      </Modal>
    </LiquidGlassCard>
  );
};

const styles = StyleSheet.create({
  card: {
    padding: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  headerSubtitle: {
    fontSize: 12,
    marginTop: 4,
  },
  sectionHeader: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 2,
    textTransform: 'uppercase',
    marginBottom: 12,
  },
  addButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  addButtonText: {
    fontSize: 20,
    fontWeight: '700',
    color: '#fff',
  },
  weeklyBars: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  dayColumn: {
    alignItems: 'center',
    flex: 1,
  },
  dayLabel: {
    fontSize: 10,
    fontWeight: '600',
    textTransform: 'uppercase',
    marginBottom: 8,
  },
  barContainer: {
    width: 20,
    height: 80,
    backgroundColor: 'rgba(255,255,255,0.05)',
    borderRadius: 4,
    position: 'relative',
    marginBottom: 8,
  },
  barFill: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    borderRadius: 4,
  },
  targetLine: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 1,
  },
  hoursLabel: {
    fontSize: 10,
    fontWeight: '600',
  },
  alertBanner: {
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.2)',
  },
  alertText: {
    fontSize: 12,
    fontWeight: '600',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  modalContent: {
    width: '80%',
    padding: 24,
    borderRadius: 16,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 8,
  },
  modalSubtitle: {
    fontSize: 14,
    marginBottom: 16,
  },
  input: {
    borderRadius: 8,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
    marginBottom: 16,
  },
  modalButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  modalButton: {
    flex: 1,
  },
});
