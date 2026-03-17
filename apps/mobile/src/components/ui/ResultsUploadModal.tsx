import React from 'react';
import { View, Text, Modal, StyleSheet, ActivityIndicator } from 'react-native';
import Animated, { useSharedValue, useAnimatedStyle, withRepeat, withTiming, Easing } from 'react-native-reanimated';
import { LiquidGlassCard } from './LiquidGlassCard';
import { useColors } from '../../theme';

interface ResultsUploadModalProps {
  visible: boolean;
  onClose: () => void;
}

export const ResultsUploadModal: React.FC<ResultsUploadModalProps> = ({ visible, onClose }) => {
  const colors = useColors();
  
  // Animation for shimmer effect
  const shimmerOpacity = useSharedValue(0.3);
  
  React.useEffect(() => {
    if (visible) {
      shimmerOpacity.value = withRepeat(
        withTiming(0.8, { duration: 1000, easing: Easing.inOut(Easing.linear) }),
        -1, // Repeat indefinitely
        true // Reverse on repeat
      );
    } else {
      shimmerOpacity.value = withTiming(0.3, { duration: 500 });
    }
  }, [visible]);

  const shimmerStyle = useAnimatedStyle(() => {
    return {
      opacity: shimmerOpacity.value,
    };
  });

  return (
    <Modal transparent visible={visible} animationType="fade">
      <View style={styles.overlay}>
        <LiquidGlassCard style={styles.modalCard}>
          <View style={styles.content}>
            <Animated.View style={[styles.shimmerContainer, shimmerStyle]}>
              <View style={styles.skeletonCircle} />
            </Animated.View>
            
            <Text style={[styles.title, { color: colors.primary }]}>Procesando resultados</Text>
            <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>Tu IA entrenadora está analizando tu rendimiento</Text>
            
            <ActivityIndicator 
              size="large" 
              color={colors.primary} 
              style={styles.indicator} 
            />
            
            <Text style={[styles.description, { color: colors.onSurfaceVariant }]}>
              Resumen de entrenamiento, análisis de fatiga y recomendaciones inteligentes
            </Text>
          </View>
        </LiquidGlassCard>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.4)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
  },
  modalCard: {
    width: '100%',
    maxWidth: 480,
    padding: 32,
    alignItems: 'center',
  },
  content: {
    alignItems: 'center',
  },
  shimmerContainer: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: 'rgba(100, 100, 100, 0.2)',
    marginBottom: 24,
    justifyContent: 'center',
    alignItems: 'center',
  },
  skeletonCircle: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: 'rgba(100, 100, 100, 0.3)',
  },
  title: {
    fontSize: 20,
    fontWeight: '800',
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 24,
    textAlign: 'center',
  },
  indicator: {
    marginVertical: 16,
  },
  description: {
    fontSize: 12,
    fontWeight: '400',
    textAlign: 'center',
    lineHeight: 18,
    opacity: 0.8,
  },
});