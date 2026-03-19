import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import { TrendingUpIcon, AlertTriangleIcon, CheckCircleIcon } from '../icons';

type VolumeRecommendation = {
  minSets: number;
  maxSets: number;
  optimalSets: number;
  type: 'sets' | 'lifts';
  reasoning?: string;
};

interface VolumeBudgetBarProps {
  currentVolume: Record<string, number>;
  recommendation: VolumeRecommendation;
}

const VolumeBudgetBar: React.FC<VolumeBudgetBarProps> = ({ 
  currentVolume, 
  recommendation 
}) => {
  const colors = useColors();
  const { minSets, maxSets, optimalSets, type } = recommendation;
  
  const styles = useMemo(() => StyleSheet.create({
    container: {
      backgroundColor: 'rgba(255,255,255,0.02)',
      borderRadius: 24,
      overflow: 'hidden',
      borderWidth: 1,
      borderColor: 'rgba(255,255,255,0.1)',
    },
    header: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      paddingHorizontal: 20,
      paddingVertical: 16,
      borderBottomWidth: 1,
      borderColor: 'rgba(255,255,255,0.1)',
    },
    headerLeft: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
    },
    headerTitle: {
      fontSize: 16,
      fontWeight: '700',
      textTransform: 'uppercase',
      letterSpacing: 1,
      color: colors.onSurface,
    },
    headerSubtext: {
      fontSize: 12,
      color: colors.onSurfaceVariant,
    },
    musclesContainer: {
      padding: 16,
    },
    muscleRow: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      paddingVertical: 12,
      borderBottomWidth: 1,
      borderColor: 'rgba(255,255,255,0.05)',
    },
    muscleInfo: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      flex: 1,
    },
    muscleName: {
      fontSize: 14,
      fontWeight: '600',
      color: colors.onSurfaceVariant,
      maxWidth: 100,
    },
    muscleStats: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 4,
    },
    volumeText: {
      fontSize: 16,
      fontWeight: '700',
      color: colors.onSurface,
    },
    volumeUnit: {
      fontSize: 12,
      color: colors.onSurfaceVariant,
    },
    progressBarContainer: {
      position: 'relative',
      height: 4,
      flex: 1,
      marginHorizontal: 12,
    },
    optimalZoneMarker: {
      position: 'absolute',
      top: 0,
      bottom: 0,
      width: 1,
      backgroundColor: 'rgba(255,255,255,0.2)',
    },
    progressFill: {
      position: 'absolute',
      top: 0,
      bottom: 0,
      left: 0,
      borderRadius: 2,
    },
    overloadText: {
      fontSize: 10,
      color: colors.error,
      marginTop: 4,
      alignSelf: 'flex-end',
    },
    emptyContainer: {
      padding: 24,
      alignItems: 'center',
    },
    emptyIconContainer: {
      marginBottom: 16,
    },
    emptyText: {
      fontSize: 16,
      fontWeight: '600',
      color: colors.onSurface,
      marginBottom: 4,
    },
    emptySubtext: {
      fontSize: 12,
      color: colors.onSurfaceVariant,
      textAlign: 'center',
      maxWidth: 200,
    },
    legendContainer: {
      flexDirection: 'row',
      justifyContent: 'space-around',
      paddingVertical: 16,
      borderTopWidth: 1,
      borderColor: 'rgba(255,255,255,0.1)',
    },
    legendItem: {
      alignItems: 'center',
      gap: 6,
    },
    legendColor: {
      width: 10,
      height: 10,
      borderRadius: 4,
    },
    legendText: {
      fontSize: 10,
      fontWeight: '600',
      textTransform: 'uppercase',
      color: colors.onSurfaceVariant,
    },
  }), [colors]);
  
  const activeMuscles = (Object.entries(currentVolume) as [string, number][])
    .filter(([_, vol]) => vol > 0)
    .sort((a, b) => b[1] - a[1]);

  if (activeMuscles.length === 0) {
    return (
      <View style={styles.emptyContainer}>
        <View style={styles.emptyIconContainer}>
          <TrendingUpIcon size={24} color={colors.onSurfaceVariant} />
        </View>
        <Text style={styles.emptyText}>Añade ejercicios para ver el análisis de volumen</Text>
        <Text style={styles.emptySubtext}>El algoritmo calculará el impacto fraccional en tiempo real.</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <TrendingUpIcon size={18} color={colors.primary} />
          <Text style={styles.headerTitle}>Presupuesto de Volumen Semanal</Text>
        </View>
        <View style={{}}>
          <Text style={styles.headerSubtext}>Objetivo: {minSets}-{maxSets} {type === 'sets' ? 'Series' : 'Levantamientos'}</Text>
        </View>
      </View>

      <View style={styles.musclesContainer}>
        {activeMuscles.map(([muscle, volume]) => {
          const percentage = Math.min((volume / maxSets) * 100, 100);
          const statusColor = 
            volume < minSets ? colors.batteryMid :
            volume > maxSets ? colors.error :
            colors.batteryHigh;
          
          const statusText = 
            volume < minSets ? 'Bajo' :
            volume > maxSets ? 'Alto' :
            'Óptimo';

          return (
            <View key={muscle} style={styles.muscleRow}>
              <View style={styles.muscleInfo}>
                <Text style={styles.muscleName}>{muscle}</Text>
                <View style={styles.muscleStats}>
                  <Text style={styles.volumeText}>{volume.toFixed(1)}</Text>
                  <Text style={styles.volumeUnit}>/{maxSets}</Text>
                  {volume > maxSets && <AlertTriangleIcon size={12} color={colors.error} />}
                  {volume >= minSets && volume <= maxSets && <CheckCircleIcon size={12} color={colors.batteryHigh} />}
                </View>
              </View>
              
                <View style={styles.progressBarContainer}>
                <View style={[
                  styles.optimalZoneMarker,
                  { 
                    left: `${(minSets / maxSets) * 100}%` 
                  }
                ]} />
                <View style={[
                  styles.progressFill,
                  { 
                    width: `${percentage}%`,
                    backgroundColor: statusColor
                  }
                ]} />
              </View>
              
              {volume > maxSets && (
                <Text style={styles.overloadText}>
                  Excede capacidad de recuperación (+{(volume - maxSets).toFixed(1)})
                </Text>
              )}
            </View>
          );
        })}
      </View>
      
      <View style={styles.legendContainer}>
        <View style={styles.legendItem}>
          <View style={[
            styles.legendColor,
            { backgroundColor: colors.batteryMid }
          ]} />
          <Text style={styles.legendText}>Acumulación</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[
            styles.legendColor,
            { backgroundColor: colors.batteryHigh }
          ]} />
          <Text style={styles.legendText}>Óptimo</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[
            styles.legendColor,
            { backgroundColor: colors.error }
          ]} />
          <Text style={styles.legendText}>Sobrecarga</Text>
        </View>
      </View>
    </View>
  );
};

export default VolumeBudgetBar;
