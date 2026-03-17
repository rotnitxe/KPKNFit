import React from 'react';
import { View, StyleSheet, ViewStyle } from 'react-native';
import { Canvas, LinearGradient, Rect, vec, Mask, Group, BlurMask } from '@shopify/react-native-skia';
import { useColors } from '../../theme';

interface LiquidGlassCardProps {
  children: React.ReactNode;
  style?: ViewStyle;
  gradientColors?: string[];
  padding?: number;
}

/**
 * A premium card component that implements the "Liquid Glass" aesthetic
 * using Skia for gradients and native layout for content.
 */
export const LiquidGlassCard: React.FC<LiquidGlassCardProps> = ({ 
  children, 
  style,
  gradientColors,
  padding = 24
}) => {
  const colors = useColors();
  
  // Default gradient mirroring the PWA's liquid glass look in dark mode
  const defaultGradient = [
    'rgba(255, 255, 255, 0.12)',
    'rgba(255, 255, 255, 0.03)',
  ];

  const finalGradient = gradientColors || defaultGradient;

  return (
    <View style={[styles.wrapper, style]}>
      {/* Background Decor Layer */}
      <View style={StyleSheet.absoluteFill}>
        <Canvas style={{ flex: 1 }}>
          <Rect x={0} y={0} width={1000} height={1000}>
            <LinearGradient
              start={vec(0, 0)}
              end={vec(300, 300)}
              colors={finalGradient}
            />
          </Rect>
          
          {/* Subtle Inner Glow / Reflection */}
          <Rect x={1} y={1} width={998} height={200} opacity={0.1}>
             <LinearGradient
                start={vec(0, 0)}
                end={vec(0, 200)}
                colors={['#FFFFFF', 'transparent']}
             />
          </Rect>
        </Canvas>
      </View>
      
      {/* Semi-transparent Dark Tint Overlay (to simulate glass depth) */}
      <View style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(28, 27, 31, 0.5)' }]} />

      {/* Glossy Border Overlay */}
      <View style={[
        styles.borderOverlay, 
        { borderColor: 'rgba(255, 255, 255, 0.08)' }
      ]} />

      {/* Content Layer */}
      <View style={[styles.content, { padding }]}>
        {children}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  wrapper: {
    borderRadius: 32,
    overflow: 'hidden',
    position: 'relative',
    backgroundColor: 'rgba(255, 255, 255, 0.02)', // Baseline for when Canvas is loading
  },
  borderOverlay: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 32,
    borderWidth: 1,
  },
  content: {
    // padding applied dynamically
  },
});
