import React, { useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { TrophyIcon } from '../icons';
import { InfoTooltip } from '../ui/InfoTooltip';
import { calculateBrzycki1RM, calculateIPFGLPoints } from '../../utils/calculations';
import type { WorkoutLog } from '../../types/workout';
import type { Settings } from '../../types/settings';

interface PowerliftingDashboardProps {
  history: WorkoutLog[];
  settings: Settings;
  exerciseList: any[];
}

const PowerliftingDashboard: React.FC<PowerliftingDashboardProps> = ({ 
  history, 
  settings, 
  exerciseList 
}) => {
  const colors = useColors();
  
  const styles = useMemo(() => StyleSheet.create({
    card: {
      padding: 20,
      marginBottom: 16,
    },
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      marginBottom: 16,
    },
    headerTitle: {
      fontSize: 18,
      fontWeight: '800',
      textTransform: 'uppercase',
      letterSpacing: 1,
      color: colors.onSurface,
    },
    description: {
      textAlign: 'center',
      color: colors.onSurfaceVariant,
      fontSize: 14,
      marginBottom: 20,
    },
    button: {
      paddingVertical: 12,
      paddingHorizontal: 16,
      borderRadius: 12,
      backgroundColor: colors.primary,
      alignItems: 'center',
    },
    buttonText: {
      color: colors.onPrimary,
      fontWeight: '600',
    },
    content: {
      gap: 16,
    },
    totalRow: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      paddingVertical: 12,
      borderBottomWidth: 1,
      borderColor: 'rgba(255,255,255,0.1)',
    },
    label: {
      fontSize: 14,
      color: colors.onSurfaceVariant,
      fontWeight: '600',
    },
    value: {
      fontSize: 20,
      fontWeight: '800',
      color: colors.primary,
    },
    liftsGrid: {
      flexDirection: 'row',
      justifyContent: 'space-between',
    },
    liftCard: {
      padding: 16,
      backgroundColor: 'rgba(0,0,0,0.05)',
      borderRadius: 12,
      alignItems: 'center',
    },
    liftLabel: {
      fontSize: 12,
      color: colors.onSurfaceVariant,
      textTransform: 'uppercase',
      marginBottom: 4,
    },
    liftValue: {
      fontSize: 24,
      fontWeight: '900',
      marginBottom: 2,
      color: colors.primary,
    },
    liftWeight: {
      fontSize: 12,
      color: colors.onSurfaceVariant,
    },
  }), [colors]);
  
  const ipfData = useMemo(() => {
    const { weight, gender } = settings.userVitals;
    if (!weight || weight <= 0 || !gender || gender === 'other') return null;

    const lifts = { squat: 0, bench: 0, deadlift: 0 };
    const keywords = { squat: 'sentadilla', bench: 'press de banca', deadlift: 'peso muerto' };

    history.forEach(log => (log.completedExercises || []).forEach(ex => {
      const name = ex.exerciseName.toLowerCase();
      if (name.includes(keywords.squat)) lifts.squat = Math.max(lifts.squat, ...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
      if (name.includes(keywords.bench)) lifts.bench = Math.max(lifts.bench, ...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
      if (name.includes(keywords.deadlift)) lifts.deadlift = Math.max(lifts.deadlift, ...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
    }));

    const total = lifts.squat + lifts.bench + lifts.deadlift;

    const options = {
      gender,
      equipment: 'classic' as const,
      weightUnit: settings.weightUnit,
    };

    const points = {
      squat: calculateIPFGLPoints(lifts.squat, weight, { ...options, lift: 'squat' }),
      bench: calculateIPFGLPoints(lifts.bench, weight, { ...options, lift: 'bench' }),
      deadlift: calculateIPFGLPoints(lifts.deadlift, weight, { ...options, lift: 'deadlift' }),
      total: calculateIPFGLPoints(total, weight, { ...options, lift: 'total' }),
    };

    return { lifts, points, total };
  }, [history, settings.userVitals, settings.weightUnit]);

  if (!ipfData) {
    return (
      <LiquidGlassCard style={styles.card}>
        <View style={styles.header}>
          <TrophyIcon size={20} color={colors.primary} />
          <Text style={styles.headerTitle}>Puntos IPF GL</Text>
        </View>
        <Text style={styles.description}>Registra tu peso corporal y género en "Progreso" {'>'} "Mis Objetivos" para ver tus puntos IPF GL.</Text>
        <TouchableOpacity 
          activeOpacity={0.7}
          style={styles.button}
        >
          <Text style={styles.buttonText}>Ir a Mis Objetivos</Text>
        </TouchableOpacity>
      </LiquidGlassCard>
    );
  }

  if (ipfData.total === 0) return null;

  return (
    <LiquidGlassCard style={styles.card}>
      <View style={styles.header}>
        <TrophyIcon size={20} color={colors.primary} />
        <Text style={styles.headerTitle}>Puntos IPF GL (Estimados)</Text>
      </View>
      <View style={styles.content}>
        <View style={styles.totalRow}>
          <Text style={styles.label}>Total</Text>
          <Text style={styles.value}>{ipfData.points.total}</Text>
        </View>
        
        <View style={styles.liftsGrid}>
          <View style={styles.liftCard}>
            <Text style={styles.liftLabel}>Sentadilla</Text>
            <Text style={styles.liftValue}>{ipfData.points.squat}</Text>
            <Text style={styles.liftWeight}>{ipfData.lifts.squat.toFixed(1)} {settings.weightUnit}</Text>
          </View>
          
          <View style={styles.liftCard}>
            <Text style={styles.liftLabel}>Banca</Text>
            <Text style={styles.liftValue}>{ipfData.points.bench}</Text>
            <Text style={styles.liftWeight}>{ipfData.lifts.bench.toFixed(1)} {settings.weightUnit}</Text>
          </View>
          
          <View style={styles.liftCard}>
            <Text style={styles.liftLabel}>Peso Muerto</Text>
            <Text style={styles.liftValue}>{ipfData.points.deadlift}</Text>
            <Text style={styles.liftWeight}>{ipfData.lifts.deadlift.toFixed(1)} {settings.weightUnit}</Text>
          </View>
        </View>
      </View>
    </LiquidGlassCard>
  );
};

export default PowerliftingDashboard;
