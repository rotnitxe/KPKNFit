import React, { useMemo, useRef, useState } from 'react';
import { Animated, LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Canvas, Circle, Path, RadialGradient, vec } from '@shopify/react-native-skia';
import { useColors, useTheme } from '@/theme';
import { useSettingsStore } from '@/stores/settingsStore';
import { useWorkoutStore } from '@/stores/workoutStore';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';

type RingId = 'muscular' | 'cns' | 'spinal';
const RING_LABELS_SHORT: Record<RingId, string> = { muscular: 'Músc.', cns: 'SNC', spinal: 'Col.' };
const ringIds: RingId[] = ['muscular', 'cns', 'spinal'];

function clamp01(v: number) { return !Number.isFinite(v) ? 0 : Math.min(Math.max(v, 0), 100); }

function describeArc(cx: number, cy: number, r: number, progress: number) {
  const pct = Math.min(Math.max(progress, 0), 1);
  if (pct <= 0) return '';
  if (pct >= 1) return `M ${cx} ${cy - r} A ${r} ${r} 0 1 1 ${cx} ${cy + r} A ${r} ${r} 0 1 1 ${cx} ${cy - r} Z`;
  const sa = -Math.PI / 2;
  const ea = sa + Math.PI * 2 * pct;
  return `M ${cx + r * Math.cos(sa)} ${cy + r * Math.sin(sa)} A ${r} ${r} 0 ${pct > 0.5 ? 1 : 0} 1 ${cx + r * Math.cos(ea)} ${cy + r * Math.sin(ea)}`;
}

export function HomeAugeRings({ cns, muscular, spinal }: { cns: number | null; muscular: number | null; spinal: number | null }) {
  const colors = useColors();
  const { isDark } = useTheme();
  const [layoutWidth, setLayoutWidth] = useState(0);

  const updateSettings = useSettingsStore(s => s.updateSettings);
  const settingsSummary = useSettingsStore(s => s.summary);
  const settingsRaw = useMemo(() => readStoredSettingsRaw(), [settingsSummary]);
  const overview = useWorkoutStore(s => s.overview);

  const calibration = (settingsRaw?.batteryCalibration as Record<string, unknown>) || {};
  const cnsDelta = (calibration.cnsDelta as number) || 0;
  const muscularDelta = (calibration.muscularDelta as number) || 0;
  const spinalDelta = (calibration.spinalDelta as number) || 0;

  const orFull = (v: number | null | undefined, fb: number | null) => { const r = v ?? fb; return r != null && r > 0 ? r : 100; };
  const baseCns = orFull(overview?.battery?.cns, cns);
  const baseMuscular = orFull(overview?.battery?.muscular, muscular);
  const baseSpinal = orFull(overview?.battery?.spinal, spinal);

  const displayCns = clamp01(baseCns + cnsDelta);
  const displayMuscular = clamp01(baseMuscular + muscularDelta);
  const displaySpinal = clamp01(baseSpinal + spinalDelta);

  // ── Calibration state ──────────────────────────────────────────────
  const [calibRing, setCalibRing] = useState<RingId | null>(null);
  const calibValRef = useRef(0);
  const [calibVal, setCalibVal] = useState(0);
  const calibStartY = useRef(0);
  const calibStartVal = useRef(0);
  const calibBase = useRef(0);
  const badgeAnim = useRef(new Animated.Value(0)).current;

  // ── Layout ─────────────────────────────────────────────────────────
  const w = Math.max(layoutWidth, 320);
  const ringSize = Math.max(140, Math.min(160, Math.round(w * 0.38)));
  const sw = 8;
  const rr = ringSize / 2 - sw / 2 - 2;
  const overlap = Math.round(ringSize * 0.3675);
  const fW = ringSize * 3 - overlap * 2;
  const cW = w;
  const cH = ringSize + 30;
  const cY = ringSize / 2 + 8;
  const fSx = (cW - fW) / 2;
  const lX = fSx + ringSize / 2;
  const mX = lX + ringSize - overlap;
  const rX = mX + ringSize - overlap;

  const pos: Record<RingId, number> = { cns: lX, muscular: mX, spinal: rX };
  const rColors: Record<RingId, string> = { cns: colors.ringCns, muscular: colors.ringMuscular, spinal: colors.ringSpinal };
  const displayVals: Record<RingId, number> = { cns: displayCns, muscular: displayMuscular, spinal: displaySpinal };
  const bases: Record<RingId, number> = { cns: baseCns, muscular: baseMuscular, spinal: baseSpinal };

  const currentVal = (id: RingId) => calibRing === id ? calibVal : displayVals[id];

  const tOp = isDark ? '26' : '1E';
  const iFOp = isDark ? '1A' : '14';
  const sheen = isDark ? '#FFFFFF4D' : '#FFFFFF66';
  const shad = isDark ? '#00000040' : '#00000020';
  const lTone = isDark ? 'rgba(255,255,255,0.62)' : 'rgba(29,27,32,0.58)';

  // ── Create gestures (stable references, use refs for current values) ─
  const gestures = useMemo(() => {
    const makeGesture = (id: RingId) => {
      return Gesture.Pan()
        .activateAfterLongPress(400)
        .onStart((e) => {
          const startVal = id === 'cns' ? displayCns : id === 'muscular' ? displayMuscular : displaySpinal;
          calibStartY.current = e.absoluteY;
          calibStartVal.current = startVal;
          calibBase.current = id === 'cns' ? baseCns : id === 'muscular' ? baseMuscular : baseSpinal;
          calibValRef.current = startVal;
          setCalibRing(id);
          setCalibVal(startVal);
          Animated.timing(badgeAnim, { toValue: 1, duration: 150, useNativeDriver: true }).start();
        })
        .onUpdate((e) => {
          const delta = -(e.absoluteY - calibStartY.current) * 0.45;
          const nv = clamp01(calibStartVal.current + delta);
          calibValRef.current = nv;
          setCalibVal(nv);
        })
        .onEnd(() => {
          const fv = Math.round(calibValRef.current);
          const sv = Math.round(calibStartVal.current);
          if (fv !== sv) {
            const nd = fv - calibBase.current;
            const cur = (settingsRaw?.batteryCalibration as Record<string, unknown>) || {};
            const up: Record<string, unknown> = { ...cur, lastCalibrated: new Date().toISOString() };
            up[`${id}Delta`] = nd;
            void updateSettings({ batteryCalibration: up as any, hasPrecalibratedBattery: true });
          }
          Animated.timing(badgeAnim, { toValue: 0, duration: 200, useNativeDriver: true }).start(() => {
            setCalibRing(null);
          });
        })
        .onFinalize(() => {
          badgeAnim.setValue(0);
          setCalibRing(null);
        });
    };
    return {
      muscular: makeGesture('muscular'),
      cns: makeGesture('cns'),
      spinal: makeGesture('spinal'),
    };
  }, [displayCns, displayMuscular, displaySpinal, baseCns, baseMuscular, baseSpinal, settingsRaw]);

  // ── Render ─────────────────────────────────────────────────────────
  return (
    <View style={styles.wrap} onLayout={(e) => { const lw = Math.round(e.nativeEvent.layout.width); if (lw !== layoutWidth) setLayoutWidth(lw); }}>
      <View pointerEvents="none" style={[styles.canvas, { width: cW, height: cH }]}>
        <Canvas style={{ width: cW, height: cH }}>
          {ringIds.map(id => {
            const cx = pos[id], color = rColors[id], value = currentVal(id);
            return (
              <React.Fragment key={id}>
                <Circle cx={cx} cy={cY} r={rr - 4}>
                  <RadialGradient c={vec(cx, cY)} r={rr - 4} colors={[`${color}${iFOp}`, `${color}00`]} />
                </Circle>
                <Path path={describeArc(cx, cY, rr, 1)} color={`${color}${tOp}`} style="stroke" strokeWidth={sw} strokeCap="round" />
                <Path path={describeArc(cx, cY, rr, value / 100)} color={shad} style="stroke" strokeWidth={sw + 2} strokeCap="round" />
                <Path path={describeArc(cx, cY, rr, value / 100)} color={color} style="stroke" strokeWidth={calibRing === id ? sw + 1 : sw} strokeCap="round" />
                <Path path={describeArc(cx, cY, rr, value / 100)} color={sheen} style="stroke" strokeWidth={Math.max(1.5, sw * 0.18)} strokeCap="round" />
              </React.Fragment>
            );
          })}
        </Canvas>
      </View>

      {ringIds.map(id => (
        <GestureDetector key={id} gesture={gestures[id]}>
          <View
            style={{
              position: 'absolute',
              left: pos[id] - ringSize * 0.4,
              width: ringSize * 0.8,
              height: ringSize * 0.9,
              top: cY - ringSize * 0.45,
            }}
          />
        </GestureDetector>
      ))}

      {calibRing ? (
        <Animated.View pointerEvents="none" style={[styles.badge, { opacity: badgeAnim, backgroundColor: rColors[calibRing], left: pos[calibRing] - 36, top: cY - ringSize * 0.55 }]}>
          <Text style={styles.badgeText}>{Math.round(calibVal)}%</Text>
        </Animated.View>
      ) : null}

      <View style={[styles.labels, { width: fW }]}>
        {ringIds.map(id => (
          <View key={id} style={styles.labelCell}>
            <Text style={[styles.val, { color: rColors[id] }]}>{Math.round(currentVal(id))}%</Text>
            <Text style={[styles.lbl, { color: lTone }]}>{RING_LABELS_SHORT[id]}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { width: '100%', marginTop: -8, overflow: 'visible', alignItems: 'center' },
  canvas: { overflow: 'visible', alignSelf: 'center' },
  badge: { position: 'absolute', width: 72, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center', zIndex: 100 },
  badgeText: { color: '#FFF', fontSize: 14, fontWeight: '900' },
  labels: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: -2, paddingHorizontal: 2 },
  labelCell: { flex: 1, alignItems: 'center', gap: 1 },
  val: { fontSize: 13, fontWeight: '900', letterSpacing: -0.3 },
  lbl: { fontSize: 9.5, fontWeight: '900', letterSpacing: 1.7, textTransform: 'uppercase' },
});
