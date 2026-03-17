import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Canvas, Path, Skia, LinearGradient, vec } from '@shopify/react-native-skia';
import { useColors } from '../../theme';

interface NutritionSkiaRingProps {
  size: number;
  strokeWidth: number;
  percentage: number;
  color: string;
  backgroundColor: string;
}

export const NutritionSkiaRing: React.FC<NutritionSkiaRingProps> = ({
  size,
  strokeWidth,
  percentage,
  color,
  backgroundColor,
}) => {
  const radius = (size - strokeWidth) / 2;
  const center = size / 2;
  
  const path = Skia.Path.Make();
  path.addArc(
    Skia.XYWHRect(center - radius, center - radius, radius * 2, radius * 2),
    -90,
    360 * (percentage / 100)
  );

  const bgPath = Skia.Path.Make();
  bgPath.addCircle(center, center, radius);

  return (
    <View style={{ width: size, height: size }}>
      <Canvas style={{ flex: 1 }}>
        <Path
          path={bgPath}
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
        />
      </Canvas>
    </View>
  );
};

export const NutritionRingsContainer: React.FC<{ 
  calories: number, 
  goal: number,
  proteinPct: number,
  carbsPct: number,
  fatsPct: number
}> = ({ calories, goal, proteinPct, carbsPct, fatsPct }) => {
  const colors = useColors();
  const size = 180;
  const stroke = 12;
  const spacing = 4;

  const calPct = Math.min(100, (calories / goal) * 100);

  return (
    <View style={styles.container}>
      <View style={styles.ringsStack}>
        {/* Main Calorie Ring */}
        <NutritionSkiaRing 
          size={size} 
          strokeWidth={stroke} 
          percentage={calPct} 
          color={colors.primary} 
          backgroundColor={`${colors.primary}20`} 
        />
        
        {/* Nested Macro Rings (Concentric style) */}
        <View style={[styles.nested, { top: stroke + spacing, left: stroke + spacing }]}>
          <NutritionSkiaRing 
            size={size - (stroke + spacing) * 2} 
            strokeWidth={stroke} 
            percentage={proteinPct} 
            color={colors.batteryHigh} 
            backgroundColor={`${colors.batteryHigh}20`} 
          />
        </View>

        <View style={[styles.nested, { top: (stroke + spacing) * 2, left: (stroke + spacing) * 2 }]}>
          <NutritionSkiaRing 
            size={size - (stroke + spacing) * 4} 
            strokeWidth={stroke} 
            percentage={carbsPct} 
            color={colors.ringMuscular} 
            backgroundColor={`${colors.ringMuscular}20`} 
          />
        </View>

        <View style={[styles.nested, { top: (stroke + spacing) * 3, left: (stroke + spacing) * 3 }]}>
          <NutritionSkiaRing 
            size={size - (stroke + spacing) * 6} 
            strokeWidth={stroke} 
            percentage={fatsPct} 
            color={colors.error} 
            backgroundColor={`${colors.error}20`} 
          />
        </View>

        <View style={styles.centerText}>
          <Text style={[styles.val, { color: colors.onSurface }]}>{Math.round(calories)}</Text>
          <Text style={[styles.lbl, { color: colors.onSurfaceVariant }]}>kcal</Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 20,
  },
  ringsStack: {
    width: 180,
    height: 180,
    position: 'relative',
  },
  nested: {
    position: 'absolute',
  },
  centerText: {
    position: 'absolute',
    top: 0, left: 0, right: 0, bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  val: {
    fontSize: 28,
    fontWeight: '900',
  },
  lbl: {
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
  }
});
