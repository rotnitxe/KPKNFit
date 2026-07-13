import React, { useState, useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { SparklesIcon } from '../icons';
import { generateWeightProjection } from '../../services/aiService';

type BodyProgressLog = {
  date: string;
  weight?: number;
};

type NutritionLog = {
  date: string;
  foods?: { calories: number; protein?: number; carbs?: number; fats?: number }[];
  totals?: { calories?: number; protein?: number; carbs?: number; fats?: number };
};

interface GoalProjectionProps {
  bodyProgress?: BodyProgressLog[];
  settings?: {
    userVitals?: {
      age?: number;
      weight?: number;
      height?: number;
      targetWeight?: number;
      gender?: 'male' | 'female';
      activityLevel?: 'sedentary' | 'light' | 'moderate' | 'active' | 'very_active';
    };
    targetWeight?: number;
  };
  isOnline?: boolean;
  nutritionLogs?: NutritionLog[];
  onNavigate?: (screen: string) => void;
}

const GoalProjection: React.FC<GoalProjectionProps> = ({
  bodyProgress = [],
  settings = {},
  isOnline = true,
  nutritionLogs = [],
  onNavigate,
}) => {
  const colors = useColors();
  
  const styles = useMemo(() => StyleSheet.create({
    card: {
      padding: 20,
      marginBottom: 16,
    },
    header: {
      marginBottom: 24,
    },
    title: {
      fontSize: 18,
      fontWeight: '800',
      textTransform: 'uppercase',
      letterSpacing: 1,
      color: colors.onSurface,
    },
    noTargetContainer: {
      alignItems: 'center',
      padding: 24,
    },
    noTargetText: {
      fontSize: 16,
      fontWeight: '600',
      color: colors.onSurface,
      marginBottom: 16,
      textAlign: 'center',
    },
    button: {
      paddingVertical: 12,
      paddingHorizontal: 16,
      borderRadius: 12,
      alignItems: 'center',
    },
    buttonText: {
      fontSize: 14,
      fontWeight: '600',
      color: colors.onPrimary,
    },
    progressContainer: {
      gap: 20,
    },
    progressTextContainer: {
      alignItems: 'center',
    },
    progressLabel: {
      fontSize: 14,
      color: colors.onSurfaceVariant,
      marginBottom: 4,
    },
    progressPercentage: {
      fontSize: 36,
      fontWeight: '900',
      color: colors.primary,
    },
    progressBarBackground: {
      height: 8,
      backgroundColor: 'rgba(255,255,255,0.05)',
      borderRadius: 4,
      overflow: 'hidden',
    },
    progressBarFill: {
      height: '100%',
      backgroundColor: colors.primary,
    },
    progressLabels: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      marginTop: 8,
    },
    progressLabelText: {
      fontSize: 12,
      color: colors.onSurfaceVariant,
      fontWeight: '600',
    },
    projectionResult: {
      paddingVertical: 16,
      borderTopWidth: 1,
      borderColor: 'rgba(255,255,255,0.1)',
    },
    projectionSummary: {
      fontSize: 14,
      color: colors.onSurfaceVariant,
      fontStyle: 'italic',
      textAlign: 'center',
      marginBottom: 8,
    },
    projectionTime: {
      fontSize: 16,
      fontWeight: '600',
      color: colors.onSurface,
      textAlign: 'center',
    },
    sparklesIcon: {
      marginLeft: 4,
    },
    loading: {
      marginVertical: 8,
    },
  }), [colors]);
  
  const [projection, setProjection] = useState<{ projection: string; summary: string } | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  
  const { targetWeight, startWeight, currentWeight, progressPercentage } = useMemo(() => {
    const target = settings?.userVitals?.targetWeight ?? settings?.targetWeight ?? null;
    if (!target) return { targetWeight: null, startWeight: null, currentWeight: null, progressPercentage: 0 };
    
    const logsWithWeight = bodyProgress.filter((log: BodyProgressLog) => log.weight).sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
    if (logsWithWeight.length < 1) return { targetWeight: target, startWeight: null, currentWeight: null, progressPercentage: 0 };
    
    const start = logsWithWeight[0].weight!;
    const current = logsWithWeight[logsWithWeight.length - 1].weight!;
    
    const totalToChange = start - target;
    const changedSoFar = start - current;
    
    const percentage = totalToChange !== 0 ? (changedSoFar / totalToChange) * 100 : (current === target ? 100 : 0);
    
    return { targetWeight: target, startWeight: start, currentWeight: current, progressPercentage: Math.min(100, Math.max(0, percentage)) };
  }, [bodyProgress, settings]);

  const handleGenerateProjection = async () => {
    if (!isOnline || bodyProgress.length < 2 || !targetWeight) return;
    setIsLoading(true);
    setProjection(null);

    const twoWeeksAgo = new Date();
    twoWeeksAgo.setDate(twoWeeksAgo.getDate() - 14);
    const recentNutritionLogs = nutritionLogs.filter((log: NutritionLog) => new Date(log.date) >= twoWeeksAgo);
    const daysWithLogs = new Set(recentNutritionLogs.map((l: NutritionLog) => l.date.split('T')[0])).size;
    const totalCals = recentNutritionLogs.reduce((sum: number, log: NutritionLog) => {
      const fromTotals = Number(log.totals?.calories ?? 0);
      const fromFoods = (log.foods || []).reduce((s: number, f: any) => s + Number(f.calories || 0), 0);
      return sum + (fromTotals > 0 ? fromTotals : fromFoods);
    }, 0);
    const avgIntake = daysWithLogs > 0 ? totalCals / daysWithLogs : 0;
    
    const { age, weight, height, gender, activityLevel } = settings?.userVitals || {};
    let tdee = 0;
    if (age && weight && height && gender && activityLevel) {
      const bmr = (10 * weight) + (6.25 * height) - (5 * age) + (gender === 'male' ? 5 : -161);
      const activityMultipliers: Record<string, number> = { 
        sedentary: 1.2, 
        light: 1.375, 
        moderate: 1.55, 
        active: 1.725, 
        very_active: 1.9 
      };
      tdee = bmr * (activityMultipliers[activityLevel] || 1.2);
    }

    const logsWithWeight = bodyProgress.filter((log: BodyProgressLog) => log.weight).slice(-14);

    try {
      const result = await generateWeightProjection(avgIntake, tdee, logsWithWeight, targetWeight, settings);
      setProjection(result);
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  if (!targetWeight) {
    return (
      <LiquidGlassCard style={styles.card}>
        <View style={styles.noTargetContainer}>
          <Text style={styles.noTargetText}>No has definido un peso objetivo.</Text>
          <TouchableOpacity 
            activeOpacity={0.7}
            onPress={() => onNavigate?.('athlete-profile')}
            style={styles.button}
          >
            <Text style={styles.buttonText}>Definir Objetivo</Text>
          </TouchableOpacity>
        </View>
      </LiquidGlassCard>
    );
  }

  return (
    <LiquidGlassCard style={styles.card}>
      <View style={styles.header}>
        <Text style={styles.title}>Proyección de Metas</Text>
      </View>
      
      <View style={styles.progressContainer}>
        <View style={styles.progressTextContainer}>
          <Text style={styles.progressLabel}>Progreso hacia tu meta de {targetWeight}kg</Text>
          <Text style={styles.progressPercentage}>{progressPercentage.toFixed(1)}%</Text>
        </View>
        
        <View style={styles.progressBarBackground}>
          <View style={[
            styles.progressBarFill,
            { width: `${progressPercentage}%` }
          ]} />
        </View>
        
        <View style={styles.progressLabels}>
          <Text style={styles.progressLabelText}>{startWeight}kg</Text>
          <Text style={styles.progressLabelText}>{currentWeight}kg</Text>
          <Text style={styles.progressLabelText}>{targetWeight}kg</Text>
        </View>
      </View>
      
      {projection && !isLoading && (
        <View style={styles.projectionResult}>
          <Text style={styles.projectionSummary}>"{projection.summary}"</Text>
          <Text style={styles.projectionTime}>Tiempo estimado: {projection.projection}</Text>
        </View>
      )}
      
      <TouchableOpacity 
        activeOpacity={0.7}
        onPress={handleGenerateProjection}
        disabled={!isOnline || bodyProgress.length < 2 || !targetWeight || isLoading}
        style={[
          styles.button,
          { 
            backgroundColor: isLoading || !isOnline ? colors.onSurfaceVariant : colors.primary,
            opacity: (isLoading || !isOnline || bodyProgress.length < 2 || !targetWeight) ? 0.5 : 1
          }
        ]}
      >
        <Text style={[
          styles.buttonText,
          { 
            color: isLoading || !isOnline ? colors.onSurface : colors.onPrimary
          }
        ]}>
          {projection ? 'Recalcular Proyección' : 'Calcular Proyección IA'}
        </Text>
        {!isLoading && <SparklesIcon size={16} color={colors.onPrimary} style={styles.sparklesIcon} />}
      </TouchableOpacity>
    </LiquidGlassCard>
  );
};

export default GoalProjection;
