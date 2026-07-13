import React, { useEffect, useMemo } from 'react';
import { View } from 'react-native';
import { Canvas, Path, Skia } from '@shopify/react-native-skia';
import Animated, { useSharedValue, withTiming } from 'react-native-reanimated';

interface SkiaProgressCircleProps {
  progress: number; // 0..1
  size?: number;
  strokeWidth?: number;
  color?: string;
  backgroundColor?: string;
}

export const SkiaProgressCircle: React.FC<SkiaProgressCircleProps> = ({
  progress,
  size = 120,
  strokeWidth = 10,
  color = '#00F0FF',
  backgroundColor = 'rgba(255, 255, 255, 0.05)',
}) => {
  const center = size / 2;
  const radius = (size - strokeWidth) / 2;
  
  const animatedProgress = useSharedValue(0);

  useEffect(() => {
    animatedProgress.value = withTiming(progress, { duration: 1000 });
  }, [progress, animatedProgress]);

  const path = useMemo(() => {
    const p = Skia.Path.Make();
    p.addCircle(center, center, radius);
    return p;
  }, [center, radius]);

  return (
    <View style={{ width: size, height: size }}>
      <Canvas style={{ flex: 1 }}>
        <Path
          path={path}
          color={backgroundColor}
          style="stroke"
          strokeWidth={strokeWidth}
          strokeCap="round"
        />
        <Path
          path={path}
          color={color}
          style="stroke"
          strokeWidth={strokeWidth}
          strokeCap="round"
          start={0}
          end={animatedProgress}
        />
      </Canvas>
    </View>
  );
};
