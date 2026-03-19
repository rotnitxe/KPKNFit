import React, { useCallback } from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassModal } from '../ui/LiquidGlassModal';
import { Button } from '../ui/Button';
import { SkiaProgressCircle } from '../workout/SkiaProgressCircle';
import { getReadiness } from '../../services/readinessSummary';

export const ReadinessBottomSheet = React.memo(
  ({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) => {
    const colors = useColors();
    const readiness = getReadiness();
    
    const readinessScore = readiness.score;
    const showLowAlert = readinessScore < 40;
    
    const handleClose = useCallback(() => {
      onClose();
    }, [onClose]);
    
    if (!isOpen) return null;
    
    const scoreRatio = readinessScore / 100;
    const recommendation =
      readinessScore >= 80
        ? '¡Estás listo para entrenar a tope!'
        : readinessScore >= 60
        ? 'Puedes entrenar, pero cuida la forma.'
        : readinessScore >= 40
        ? 'Reduce la intensidad hoy.'
        : 'Descansa y recupérate antes de la próxima sesión.';

    return (
      <LiquidGlassModal
        visible={isOpen}
        onClose={handleClose}
        title="Readiness"
        height={560}
      >
        <View style={styles.content}>
          {showLowAlert && (
            <View style={[styles.alertBanner, { backgroundColor: `${colors.error}15` }]}>
              <Text style={[styles.alertText, { color: colors.error }]}>
                ¡Readiness baja! Considera descansar hoy.
              </Text>
            </View>
          )}
          
          {/* Ring */}
          <View style={styles.ringWrapper}>
            <SkiaProgressCircle
              progress={scoreRatio}
              size={140}
              strokeWidth={12}
              color={readinessScore >= 80 ? colors.primary : readinessScore >= 60 ? colors.tertiary : colors.error}
            />
            <View style={styles.ringLabel}>
              <Text style={[styles.scoreText, { color: colors.onSurface }]}>{readinessScore.toFixed(0)}</Text>
              <Text style={[styles.outOf, { color: colors.onSurfaceVariant }]}>/100</Text>
            </View>
          </View>

          {/* Factor cards */}
          <View style={styles.factorsRow}>
            {Object.entries(readiness.factors).map(([key, value]: [string, number]) => (
              <View key={key} style={styles.factorCard}>
                <Text style={[styles.factorLabel, { color: colors.onSurfaceVariant }]}>{key}</Text>
                <Text style={[styles.factorValue, { color: colors.onSurface }]}>{value}%</Text>
              </View>
            ))}
          </View>

          {/* Recommendation */}
          <Text style={[styles.recommendation, { color: colors.primary }]}>{recommendation}</Text>

          {/* 7‑day mini bars */}
          <View style={styles.miniBars}>
            {readiness.history.map((day: any, idx: number) => (
              <View key={idx} style={[styles.miniBarContainer]}>
                <Text style={[styles.miniBarDate, { color: colors.onSurfaceVariant }]}>
                  {new Date(day.date).toLocaleDateString('es-ES', { weekday: 'short', day: '2-digit' })}
                </Text>
                <View 
                  style={[
                    styles.miniBar, 
                    { 
                      height: `${Math.max(day.score / 100, 10)}%`,
                      backgroundColor: day.score >= 80 ? colors.primary : day.score >= 60 ? colors.tertiary : colors.error 
                    }
                  ]} 
                />
              </View>
            ))}
          </View>

          {/* Action button */}
          <Pressable onPress={handleClose} style={styles.closeButton}>
            <Button variant="secondary">Cerrar</Button>
          </Pressable>
        </View>
      </LiquidGlassModal>
    );
  }
);

const styles = StyleSheet.create({
  content: { alignItems: 'center', gap: 20, paddingHorizontal: 16 },
  alertBanner: {
    width: '100%',
    padding: 12,
    borderRadius: 12,
    marginBottom: 16,
    alignItems: 'center',
  },
  alertText: {
    fontSize: 14,
    fontWeight: '600',
  },
  ringWrapper: { alignItems: 'center', justifyContent: 'center' },
  ringLabel: { position: 'absolute', alignItems: 'center' },
  scoreText: { fontSize: 32, fontWeight: '900' },
  outOf: { fontSize: 14, marginTop: 4 },
  factorsRow: { flexDirection: 'row', gap: 12, justifyContent: 'center' },
  factorCard: { alignItems: 'center', paddingVertical: 8, paddingHorizontal: 12, borderRadius: 12, backgroundColor: 'rgba(255,255,255,0.04)' },
  factorLabel: { fontSize: 12, textTransform: 'uppercase' },
  factorValue: { fontSize: 16, fontWeight: '600' },
  recommendation: { fontSize: 14, fontWeight: '500', textAlign: 'center' },
  miniBars: { flexDirection: 'row', gap: 4, marginTop: 12 },
  miniBarContainer: { alignItems: 'center', gap: 8 },
  miniBarDate: { fontSize: 10, fontWeight: '600', textAlign: 'center', minWidth: 60 },
  miniBar: { flex: 1, height: 6, borderRadius: 3 },
  closeButton: { marginTop: 12, width: '100%' },
});
