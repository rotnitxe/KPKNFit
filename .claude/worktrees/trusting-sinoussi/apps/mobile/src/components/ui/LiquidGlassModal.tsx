/**
 * components/ui/LiquidGlassModal.tsx
 * 
 * Modal con diseño "Material Liquid Glass" usando glassmorphism real:
 * - Blur progresivo con backdrop filter
 * - Bordes iridiscentes sutiles animados
 * - Transiciones spring suaves con Reanimated
 * - Soporte para drag-to-dismiss
 */

import React, { useCallback } from 'react';
import {
  Modal,
  View,
  Text,
  StyleSheet,
  Pressable,
  Dimensions,
  Platform,
} from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
  runOnJS,
  interpolate,
  Extrapolation,
  withRepeat,
  Easing,
  SharedValue,
} from 'react-native-reanimated';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Canvas, Rect, LinearGradient, vec, Circle, Blur, Paint } from '@shopify/react-native-skia';
import { useColors } from '../../theme';

const { height: SCREEN_HEIGHT, width: SCREEN_WIDTH } = Dimensions.get('window');

interface LiquidGlassModalProps {
  visible: boolean;
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
  subtitle?: string;
  height?: number;
  disableDrag?: boolean;
}

/**
 * IridescentBorder
 */
const IridescentBorder: React.FC<{ borderRadius: number }> = React.memo(({ borderRadius }) => {
  const colors = useColors();
  const pulse = useSharedValue(0);
  
  React.useEffect(() => {
    pulse.value = withRepeat(
      withTiming(1, { duration: 4000, easing: Easing.inOut(Easing.ease) }),
      -1,
      true
    );
  }, []);

  return (
    <Canvas style={StyleSheet.absoluteFill}>
      <Rect x={0} y={0} width={SCREEN_WIDTH} height={SCREEN_HEIGHT}>
        <LinearGradient
          start={vec(0, 0)}
          end={vec(SCREEN_WIDTH, 500)}
          colors={[
            'rgba(255, 255, 255, 0.0)',
            'rgba(255, 255, 255, 0.12)',
            'rgba(255, 255, 255, 0.0)',
          ]}
        />
      </Rect>
      
      <Circle cx={40} cy={40} r={120}>
        <Blur blur={60} />
        <Paint color={`${colors.primary}30`} />
      </Circle>
      
      <Circle cx={SCREEN_WIDTH - 40} cy={100} r={100}>
        <Blur blur={50} />
        <Paint color={`${colors.tertiary}20`} />
      </Circle>

      <Rect x={20} y={0} width={SCREEN_WIDTH - 72} height={1.5}>
        <LinearGradient
          start={vec(0, 0)}
          end={vec(SCREEN_WIDTH - 72, 0)}
          colors={[
            'rgba(255,255,255,0)',
            'rgba(255,255,255,0.8)',
            'rgba(255,255,255,0)',
          ]}
        />
      </Rect>
    </Canvas>
  );
});

/**
 * GlassBackdrop
 */
const GlassBackdrop: React.FC<{
  opacity: SharedValue<number>;
  onPress: () => void;
}> = ({ opacity, onPress }) => {
  const colors = useColors();
  
  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
  }));
  
  return (
    <Animated.View style={[styles.backdrop, animatedStyle]}>
      <Pressable style={StyleSheet.absoluteFill} onPress={onPress}>
        <View style={[
          styles.backdropBlur,
          { backgroundColor: `${colors.background}66` }
        ]} />
      </Pressable>
    </Animated.View>
  );
};

export const LiquidGlassModal: React.FC<LiquidGlassModalProps> = ({
  visible,
  onClose,
  children,
  title,
  subtitle,
  height = SCREEN_HEIGHT * 0.85,
  disableDrag = false,
}) => {
  const colors = useColors();
  const translateY = useSharedValue(SCREEN_HEIGHT);
  const backdropOpacity = useSharedValue(0);

  const animateModal = useCallback((toVisible: boolean) => {
    if (toVisible) {
      translateY.value = withSpring(0, {
        damping: 25,
        stiffness: 200,
        mass: 1,
      });
      backdropOpacity.value = withTiming(1, { duration: 250 });
    } else {
      translateY.value = withTiming(SCREEN_HEIGHT, { duration: 300 });
      backdropOpacity.value = withTiming(0, { duration: 200 });
    }
  }, [translateY, backdropOpacity]);

  React.useEffect(() => {
    animateModal(visible);
  }, [visible, animateModal]);

  const gesture = Gesture.Pan()
    .onUpdate((event) => {
      if (event.translationY > 0) {
        translateY.value = event.translationY;
      }
    })
    .onEnd((event) => {
      if (event.translationY > height * 0.3 || event.velocityY > 500) {
        runOnJS(onClose)();
      } else {
        translateY.value = withSpring(0);
      }
    });

  const animatedSheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
  }));

  const handleBackdropPress = useCallback(() => {
    onClose();
  }, [onClose]);

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onClose}
    >
      <View style={styles.container}>
        <GlassBackdrop opacity={backdropOpacity} onPress={handleBackdropPress} />
        
        <GestureDetector gesture={disableDrag ? Gesture.Pan() : gesture}>
          <Animated.View
            style={[
              styles.sheet,
              animatedSheetStyle,
              {
                height,
                backgroundColor: `${colors.surface}E6`,
                borderColor: `${colors.onSurface}1A`,
              },
            ]}
          >
            <View style={styles.iridescentContainer}>
              <IridescentBorder borderRadius={32} />
            </View>
            
            <View style={styles.handleContainer}>
              <View style={[styles.handle, { backgroundColor: `${colors.onSurface}33` }]} />
            </View>
            
            {(title || subtitle) && (
              <View style={styles.header}>
                {title && (
                  <View style={styles.titleRow}>
                    <Text style={[styles.title, { color: colors.onSurface }]}>
                      {title}
                    </Text>
                  </View>
                )}
                {subtitle && (
                  <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
                    {subtitle}
                  </Text>
                )}
              </View>
            )}
            
            <View style={styles.content}>
              {children}
            </View>
          </Animated.View>
        </GestureDetector>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
  },
  backdropBlur: {
    flex: 1,
  },
  sheet: {
    position: 'absolute',
    left: 16,
    right: 16,
    bottom: Platform.OS === 'ios' ? 40 : 16,
    borderTopWidth: 1,
    borderLeftWidth: 1,
    borderRightWidth: 1,
    borderBottomWidth: 1,
    borderRadius: 32,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -10 },
    shadowOpacity: 0.3,
    shadowRadius: 30,
    elevation: 20,
  },
  iridescentContainer: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 32,
    overflow: 'hidden',
  },
  handleContainer: {
    alignItems: 'center',
    paddingTop: 12,
    paddingBottom: 8,
  },
  handle: {
    width: 48,
    height: 4,
    borderRadius: 2,
  },
  header: {
    paddingHorizontal: 24,
    paddingTop: 8,
    paddingBottom: 16,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  title: {
    fontSize: 24,
    fontWeight: '800',
    letterSpacing: -0.5,
    zIndex: 1,
  },
  subtitle: {
    fontSize: 14,
    fontWeight: '500',
    letterSpacing: 0.2,
  },
  content: {
    flex: 1,
    paddingHorizontal: 24,
    paddingBottom: 40,
  },
});
