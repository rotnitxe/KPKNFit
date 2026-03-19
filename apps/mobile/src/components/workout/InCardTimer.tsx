import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';
import { CheckCircleIcon, PauseIcon, PlayIcon } from '../icons';
import { useColors } from '../../theme';

interface InCardTimerProps {
  initialTime: number;
  onSave: (duration: number) => void;
}

function formatSeconds(totalSeconds: number) {
  const mins = Math.floor(totalSeconds / 60)
    .toString()
    .padStart(2, '0');
  const secs = (totalSeconds % 60).toString().padStart(2, '0');
  return `${mins}:${secs}`;
}

export function InCardTimer({ initialTime, onSave }: InCardTimerProps) {
  const colors = useColors();
  const [elapsed, setElapsed] = useState(Math.max(0, initialTime));
  const [running, setRunning] = useState(false);
  const startedAtRef = useRef<number | null>(null);
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    setElapsed(Math.max(0, initialTime));
  }, [initialTime]);

  useEffect(() => {
    if (!running) {
      if (tickRef.current) {
        clearInterval(tickRef.current);
        tickRef.current = null;
      }
      return;
    }
    startedAtRef.current = Date.now() - elapsed * 1000;
    tickRef.current = setInterval(() => {
      if (!startedAtRef.current) return;
      const nextElapsed = Math.floor((Date.now() - startedAtRef.current) / 1000);
      setElapsed(nextElapsed);
    }, 250);
    return () => {
      if (tickRef.current) {
        clearInterval(tickRef.current);
        tickRef.current = null;
      }
    };
    // Intentionally depend only on `running` to avoid recreating the interval every tick.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running]);

  const timerLabel = useMemo(() => formatSeconds(elapsed), [elapsed]);

  return (
    <View style={[styles.wrap, { borderColor: `${colors.onSurface}1F`, backgroundColor: colors.surface }]}>
      <Pressable
        onPress={() => {
          ReactNativeHapticFeedback.trigger('selection');
          setRunning(current => !current);
        }}
        style={[styles.iconBtn, { backgroundColor: running ? colors.secondaryContainer : colors.primaryContainer }]}
      >
        {running ? (
          <PauseIcon size={12} color={running ? colors.onSecondaryContainer : colors.onPrimaryContainer} />
        ) : (
          <PlayIcon size={12} color={running ? colors.onSecondaryContainer : colors.onPrimaryContainer} />
        )}
      </Pressable>
      <Text style={[styles.timerText, { color: colors.onSurface }]}>{timerLabel}</Text>
      <Pressable
        onPress={() => {
          ReactNativeHapticFeedback.trigger('impactMedium');
          setRunning(false);
          onSave(Math.max(0, elapsed));
        }}
        style={[styles.saveBtn, { backgroundColor: colors.surfaceContainer }]}
      >
        <CheckCircleIcon size={12} color={colors.onSurfaceVariant} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    minHeight: 34,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 6,
  },
  iconBtn: {
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
  },
  timerText: {
    minWidth: 48,
    textAlign: 'center',
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: -0.2,
  },
  saveBtn: {
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
