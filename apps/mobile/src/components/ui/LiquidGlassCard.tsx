import React from 'react';
import { View, StyleSheet, ViewStyle, StyleProp } from 'react-native';
import { Canvas, LinearGradient, Rect, vec } from '@shopify/react-native-skia';
import { useColors, useTheme } from '../../theme';

interface LiquidGlassCardProps {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  gradientColors?: string[];
  padding?: number;
}

export const LiquidGlassCard: React.FC<LiquidGlassCardProps> = ({ 
  children, 
  style,
  gradientColors,
  padding = 24
}) => {
  const colors = useColors();
  const { isDark } = useTheme();
  
  const lightGradient = ['rgba(255, 255, 255, 0.95)', 'rgba(255, 255, 255, 0.85)'];
  const darkGradient = ['rgba(255, 255, 255, 0.12)', 'rgba(255, 255, 255, 0.03)'];
  const finalGradient = gradientColors || (isDark ? darkGradient : lightGradient);

  return (
    <View style={[
      styles.wrapper,
      { backgroundColor: isDark ? 'rgba(255,255,255,0.02)' : '#FFFFFF' },
      style,
    ]}>
      <View style={StyleSheet.absoluteFill}>
        <Canvas style={{ flex: 1 }}>
          <Rect x={0} y={0} width={1000} height={1000}>
            <LinearGradient start={vec(0, 0)} end={vec(300, 300)} colors={finalGradient} />
          </Rect>
        </Canvas>
      </View>
      
      {isDark ? (
        <View style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(28, 27, 31, 0.5)' }]} />
      ) : null}

      <View style={[
        styles.borderOverlay, 
        { borderColor: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.04)' },
      ]} />

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
  },
  borderOverlay: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 32,
    borderWidth: 1,
  },
  content: {},
});
