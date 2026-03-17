import React, { useState, useMemo } from 'react';
import { View, StyleSheet, Text, TouchableOpacity, Dimensions, Animated as RNAnimated } from 'react-native';
import Svg, { Path, G } from 'react-native-svg';
import { Canvas, Blur, Rect, LinearGradient, vec, Fill, Circle } from '@shopify/react-native-skia';
import { useColors } from '../../theme';
import type { DetailedMuscleVolumeAnalysis } from '../../types/workout';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const BODY_ASPECT_RATIO = 1 / 2.1;
const CONTAINER_WIDTH = Math.min(SCREEN_WIDTH * 0.8, 280);
const CONTAINER_HEIGHT = CONTAINER_WIDTH / BODY_ASPECT_RATIO;

interface HeatZoneProps {
  top: number;
  left: number;
  width: number;
  height: number;
  muscle: string;
  volume: number;
  isFocused: boolean;
  isFaded: boolean;
  onPress?: (muscle: string) => void;
}

const HeatZone: React.FC<HeatZoneProps> = ({ 
  top, left, width, height, muscle, volume, isFocused, isFaded, onPress 
}) => {
  const getHeatColor = (sets: number) => {
    if (sets === 0) return null;
    if (sets >= 15) return '#ff0000';
    if (sets >= 8) return '#00ff88';
    return '#0088ff';
  };

  const color = getHeatColor(volume);
  if (!color) return null;

  return (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => onPress?.(muscle)}
      style={[
        styles.heatZoneContainer,
        {
          top: `${top}%`,
          left: `${left}%`,
          width: `${width}%`,
          height: `${height}%`,
          opacity: isFaded ? 0.1 : (isFocused ? 0.95 : 1),
          zIndex: isFocused ? 20 : 10,
        }
      ]}
    >
      <Canvas style={{ flex: 1, borderRadius: 100 }}>
        <Fill color={color}>
          <Blur blur={isFocused ? 16 : 12} />
        </Fill>
      </Canvas>
    </TouchableOpacity>
  );
};

export const CaupolicanBody: React.FC<{
  data: DetailedMuscleVolumeAnalysis[];
  focusedMuscle?: string | null;
  onMuscleClick?: (muscle: string) => void;
}> = ({ data, focusedMuscle, onMuscleClick }) => {
  const colors = useColors();
  const [view, setView] = useState<'front' | 'back'>('front');

  const getMuscleVolume = (name: string) => {
    const found = data.find(d => 
        d.muscleGroup.toLowerCase().includes(name.toLowerCase()) ||
        (name === "Abdomen" && d.muscleGroup.toLowerCase().includes("abdom")) ||
        (name === "Pectoral" && d.muscleGroup.toLowerCase().includes("pectro"))
    );
    return found ? found.displayVolume : 0;
  };

  const renderHeatZone = (muscle: string, top: number, left: number, w: number, h: number) => {
    const vol = getMuscleVolume(muscle);
    const isFocused = !!focusedMuscle && muscle.toLowerCase().includes(focusedMuscle.toLowerCase());
    const isFaded = !!focusedMuscle && !isFocused;

    return (
      <HeatZone
        key={`${view}-${muscle}-${left}-${top}`}
        muscle={muscle}
        top={top}
        left={left}
        width={w}
        height={h}
        volume={vol}
        isFocused={isFocused}
        isFaded={isFaded}
        onPress={onMuscleClick}
      />
    );
  };

  return (
    <View style={styles.container}>
      {/* View Switcher */}
      <View style={[styles.switcher, { backgroundColor: `${colors.surfaceVariant}40`, borderColor: colors.outlineVariant }]}>
        <TouchableOpacity 
          style={[styles.switchBtn, view === 'front' && { backgroundColor: colors.surface }]} 
          onPress={() => setView('front')}
        >
          <Text style={[styles.switchText, { color: view === 'front' ? colors.onSurface : colors.onSurfaceVariant }]}>FRENTE</Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={[styles.switchBtn, view === 'back' && { backgroundColor: colors.surface }]} 
          onPress={() => setView('back')}
        >
          <Text style={[styles.switchText, { color: view === 'back' ? colors.onSurface : colors.onSurfaceVariant }]}>ESPALDA</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.bodyWrapper}>
        {/* Silhouette SVG */}
        <View style={styles.svgContainer}>
           <Svg width="100%" height="100%" viewBox={view === 'front' ? "0 -40 857 2088" : "0 0 934 2048"} preserveAspectRatio="xMidYMid meet">
             <G transform={view === 'front' ? "translate(0.000000,2048.000000) scale(0.100000,-0.100000)" : "translate(0.000000,2048.000000) scale(0.100000,-0.100000)"} fill={colors.outlineVariant} opacity={0.3}>
                <Path d={view === 'front' 
                    ? "M0 10240 l0 -10240 4285 0 4285 0 0 10240 0 10240 -4285 0 -4285 0 0 -10240z m4226 9735 l65 -25 77 26 c67 23 88 26 162 21 47 -3 103 -12 125 -21 254 -105 482 -311 598 -542 34 -68 74 -213 97 -359 6 -38 13 -81 15 -95 7 -39 -42 65 -71 150 -14 41 -29 82 -34 90 -4 9 -11 30 -14 47 -15 69 -99 205 -188 308 -49 57 -164 165 -175 165 -5 0 -22 13 -40 30 -17 16 -59 44 -93 62 -34 18 -73 39 -86 46 -57 31 -134 41 -310 42 -99 1 -219 1 -269 2 -139 2 -188 -22 -458 -216 -181 -130 -243 -228 -370 -592 -46 -130 -48 -134 -57 -134 -10 0 -5 41 16 150 9 41 18 86 20 100 11 55 47 167 67 210 50 106 188 276 287 355 111 88 188 136 282 174 115 46 241 49 354 6z"
                    : "M0 10240 l0 -10240 4670 0 4670 0 0 10240 0 10240 -4670 0 -4670 0 0 -10240z m4620 9739 c52 -20 52 -20 114 0 33 11 80 23 104 27 133 21 396 -106 540 -261 181 -192 243 -325 312 -664 36 -178 74 -310 176 -615 17 -51 33 -220 26 -270 -4 -24 -8 -18 -27 41 -24 76 -57 152 -65 153 -3 0 -4 -43 -2 -95 4 -96 -14 -291 -28 -300 -5 -3 -11 43 -14 103 -8 145 -26 310 -36 347 -5 17 -21 68 -35 114 -14 46 -24 86 -21 88 8 8 76 -85 89 -121 7 -21 17 -36 22 -35 13 4 12 7 -41 169 -25 74 -49 149 -54 165 -15 49 -50 235 -50 266 0 16 -4 29 -10 29 -5 0 -10 12 -10 28 -1 41 -45 166 -85 239 -53 97 -145 213 -178 226 -9 3 -17 4 -17 2 0 -3 20 -49 44 -102 46 -104 79 -203 89 -268 6 -35 5 -37 -4 -15 -48 111 -113 223 -175 302 -41 51 -79 95 -84 96 -6 2 -27 20 -48 41 -54 54 -118 102 -150 111 l-27 8 28 1 c33 1 60 -12 172 -84 47 -31 85 -51 85 -46 0 6 -5 13 -10 16 -15 9 -50 81 -44 88 8 8 -91 57 -113 57 -10 0 -25 5 -33 11 -12 8 -10 9 8 4 37 -11 25 11 -18 30 -22 10 -37 21 -35 26 3 4 1 10 -5 14 -5 3 -10 2 -10 -3 0 -6 -20 -4 -47 4 -62 19 -168 18 -226 -2 -43 -15 -51 -15 -87 0 -48 19 -170 21 -238 4 -81 -20 -270 -137 -256 -159 3 -5 34 7 68 26 54 30 72 35 127 35 l64 -1 -38 -13 c-21 -8 -57 -27 -79 -43 -47 -31 -120 -104 -113 -111 2 -3 -10 -23 -27 -45 l-32 -40 29 7 c17 4 48 22 70 40 44 35 138 96 148 96 4 0 -24 -32 -63 -72 -39 -39 -87 -99 -107 -132 -21 -34 -60 -95 -88 -136 -54 -79 -99 -177 -136 -297 -13 -40 -24 -71 -26 -69 -7 6 26 179 43 226 8 25 35 98 60 164 25 65 43 121 40 124 -10 9 -35 -19 -29 -34 3 -8 -1 -14 -10 -14 -23 0 -65 -56 -99 -132 -29 -64 -58 -95 -42 -45 31 101 74 198 129 287 35 58 62 107 60 109 -3 3 -54 -36 -123 -94 -38 -32 -129 -167 -167 -249 -31 -67 -52 -149 -85 -331 -24 -136 -36 -180 -80 -305 -60 -168 -81 -240 -71 -240 4 0 23 30 41 67 32 67 108 173 123 173 4 0 -1 -17 -11 -37 -78 -154 -83 -178 -92 -428 -9 -233 -17 -288 -38 -263 -26 32 -46 297 -29 383 8 38 8 39 -8 20 -10 -11 -24 -36 -32 -55 -7 -19 -18 -44 -23 -55 -5 -11 -16 -42 -25 -70 l-15 -50 5 113 c8 148 30 246 95 422 44 118 47 130 106 410 40 189 47 218 71 279 76 195 148 298 304 431 84 73 130 103 213 140 155 71 255 81 366 39z"
                }/>
             </G>
           </Svg>
        </View>

        {/* Heat Zones Overlay */}
        <View style={styles.overlay}>
          {view === 'front' ? (
            <>
              {renderHeatZone("Pectoral", 24, 32, 14, 5)}
              {renderHeatZone("Pectoral", 24, 54, 14, 5)}
              {renderHeatZone("Abdomen", 36, 42, 16, 10)}
              {renderHeatZone("Cuádriceps", 52, 28, 15, 13)}
              {renderHeatZone("Cuádriceps", 52, 57, 15, 13)}
              {renderHeatZone("Deltoides", 22, 14, 9, 9)}
              {renderHeatZone("Deltoides", 22, 77, 9, 9)}
              {renderHeatZone("Bíceps", 34, 16, 9, 10)}
              {renderHeatZone("Bíceps", 34, 75, 9, 10)}
              {renderHeatZone("Antebrazo", 46, 12, 10, 12)}
              {renderHeatZone("Antebrazo", 46, 78, 10, 12)}
            </>
          ) : (
            <>
              {renderHeatZone("Dorsal", 28, 25, 50, 16)}
              {renderHeatZone("Trapecio", 18, 45, 10, 7)}
              {renderHeatZone("Espalda Baja", 44, 42, 16, 7)}
              {renderHeatZone("Glúteos", 52, 32, 14, 9)}
              {renderHeatZone("Glúteos", 52, 54, 14, 9)}
              {renderHeatZone("Isquiosurales", 70, 25, 15, 13)}
              {renderHeatZone("Isquiosurales", 70, 60, 15, 13)}
              {renderHeatZone("Gemelos", 80, 28, 12, 12)}
              {renderHeatZone("Gemelos", 80, 60, 12, 12)}
              {renderHeatZone("Tríceps", 34, 15, 10, 12)}
              {renderHeatZone("Tríceps", 34, 75, 10, 12)}
              {renderHeatZone("Deltoides", 22, 14, 9, 9)}
              {renderHeatZone("Deltoides", 22, 77, 9, 9)}
            </>
          )}
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    alignItems: 'center',
    gap: 20,
  },
  switcher: {
    flexDirection: 'row',
    padding: 2,
    borderRadius: 99,
    borderWidth: 1,
  },
  switchBtn: {
    paddingHorizontal: 16,
    paddingVertical: 6,
    borderRadius: 99,
  },
  switchText: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1,
  },
  bodyWrapper: {
    width: CONTAINER_WIDTH,
    height: CONTAINER_HEIGHT,
    position: 'relative',
  },
  svgContainer: {
    ...StyleSheet.absoluteFillObject,
    padding: 20,
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    padding: 20,
  },
  heatZoneContainer: {
    position: 'absolute',
  }
});
