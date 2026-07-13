import React, { useEffect, useRef } from 'react';
import { View, Animated, StyleSheet, ViewStyle } from 'react-native';

interface SkeletonLoaderProps {
  type?: 'line' | 'card' | 'circle';
  lines?: number;
  style?: ViewStyle;
  testID?: string;
}

export function SkeletonLoader({
  type = 'line',
  lines = 3,
  style,
  testID,
}: SkeletonLoaderProps) {
  const pulseAnim = useRef(new Animated.Value(0.3)).current;

  useEffect(() => {
    const pulse = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, {
          toValue: 0.7,
          duration: 750,
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnim, {
          toValue: 0.3,
          duration: 750,
          useNativeDriver: true,
        }),
      ])
    );
    pulse.start();
    return () => pulse.stop();
  }, [pulseAnim]);

  const baseStyle = {
    backgroundColor: 'rgba(51,65,85,0.5)',
    opacity: pulseAnim,
  } as const;

  if (type === 'card') {
    return (
      <Animated.View
        style={[styles.card, baseStyle, style]}
        testID={testID}
      />
    );
  }

  if (type === 'circle') {
    return (
      <Animated.View
        style={[styles.circle, baseStyle, style]}
        testID={testID}
      />
    );
  }

  // Default 'line' type
  const lineElements = [];
  const widths: any[] = ['100%', '85%', '70%'];

  for (let i = 0; i < lines; i++) {
    const width = widths[i % widths.length];
    lineElements.push(
      <Animated.View
        key={i}
        style={[styles.line, { width }, baseStyle]}
      />
    );
  }

  return (
    <View style={styles.linesContainer} testID={testID}>
      {lineElements}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 8,
  },
  circle: {
    borderRadius: 9999,
  },
  linesContainer: {
    gap: 10,
  },
  line: {
    height: 16,
    borderRadius: 6,
  },
});

export default SkeletonLoader;