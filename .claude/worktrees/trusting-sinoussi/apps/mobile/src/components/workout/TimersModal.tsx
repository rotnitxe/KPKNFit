import React, { useEffect, useMemo, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { Button } from '@/components/ui';
import { useColors } from '@/theme';

type TimersTab = 'stopwatch' | 'timer';

interface TimersModalProps {
  visible: boolean;
  onClose: () => void;
  onStartTimer: (seconds: number) => Promise<void> | void;
  onCancelTimer: () => Promise<void> | void;
}

function formatClock(totalSeconds: number) {
  const safe = Math.max(0, totalSeconds);
  const minutes = Math.floor(safe / 60)
    .toString()
    .padStart(2, '0');
  const seconds = Math.floor(safe % 60)
    .toString()
    .padStart(2, '0');
  return `${minutes}:${seconds}`;
}

function StopwatchPanel() {
  const colors = useColors();
  const [isRunning, setIsRunning] = useState(false);
  const [elapsedMs, setElapsedMs] = useState(0);

  useEffect(() => {
    if (!isRunning) return undefined;
    const startedAt = Date.now() - elapsedMs;
    const id = setInterval(() => {
      setElapsedMs(Date.now() - startedAt);
    }, 100);
    return () => clearInterval(id);
  }, [elapsedMs, isRunning]);

  return (
    <View style={styles.panel}>
      <Text style={[styles.clockValue, { color: colors.onSurface }]}>
        {formatClock(Math.floor(elapsedMs / 1000))}
      </Text>
      <View style={styles.controls}>
        <Button onPress={() => setIsRunning(prev => !prev)} variant="primary">
          {isRunning ? 'Pausar' : 'Iniciar'}
        </Button>
        <Button
          onPress={() => {
            setIsRunning(false);
            setElapsedMs(0);
          }}
          variant="secondary"
        >
          Reset
        </Button>
      </View>
    </View>
  );
}

function TimerPanel({ onStartTimer, onCancelTimer }: Pick<TimersModalProps, 'onStartTimer' | 'onCancelTimer'>) {
  const colors = useColors();
  const [presetSeconds, setPresetSeconds] = useState(60);
  const [remainingSeconds, setRemainingSeconds] = useState(60);
  const [isRunning, setIsRunning] = useState(false);

  useEffect(() => {
    if (!isRunning) return undefined;
    const id = setInterval(() => {
      setRemainingSeconds(prev => {
        if (prev <= 1) {
          setIsRunning(false);
          void onCancelTimer();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(id);
  }, [isRunning, onCancelTimer]);

  const presetOptions = useMemo(() => [30, 60, 120, 180], []);

  const handlePreset = (seconds: number) => {
    if (isRunning) return;
    setPresetSeconds(seconds);
    setRemainingSeconds(seconds);
  };

  const handleStartPause = async () => {
    if (remainingSeconds <= 0) return;
    if (isRunning) {
      setIsRunning(false);
      await onCancelTimer();
      return;
    }
    await onStartTimer(remainingSeconds);
    setIsRunning(true);
  };

  const handleReset = async () => {
    setIsRunning(false);
    setRemainingSeconds(presetSeconds);
    await onCancelTimer();
  };

  return (
    <View style={styles.panel}>
      {!isRunning ? (
        <View style={styles.presets}>
          {presetOptions.map(option => (
            <Pressable
              key={option}
              onPress={() => handlePreset(option)}
              style={[
                styles.presetChip,
                {
                  borderColor: option === presetSeconds ? colors.primary : colors.outlineVariant,
                  backgroundColor: option === presetSeconds ? `${colors.primary}1A` : 'transparent',
                },
              ]}
            >
              <Text style={[styles.presetChipText, { color: option === presetSeconds ? colors.primary : colors.onSurfaceVariant }]}>
                {option < 60 ? `${option}s` : `${Math.floor(option / 60)}m`}
              </Text>
            </Pressable>
          ))}
        </View>
      ) : null}

      <Text style={[styles.clockValue, { color: colors.onSurface }]}>
        {formatClock(remainingSeconds)}
      </Text>

      <View style={styles.controls}>
        <Button onPress={handleStartPause} variant="primary" disabled={remainingSeconds <= 0}>
          {isRunning ? 'Pausar' : 'Iniciar'}
        </Button>
        <Button onPress={handleReset} variant="secondary">
          Reset
        </Button>
      </View>
    </View>
  );
}

export function TimersModal({ visible, onClose, onStartTimer, onCancelTimer }: TimersModalProps) {
  const colors = useColors();
  const [activeTab, setActiveTab] = useState<TimersTab>('stopwatch');

  useEffect(() => {
    if (!visible) {
      setActiveTab('stopwatch');
    }
  }, [visible]);

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable style={styles.backdrop} onPress={onClose} />
        <View style={[styles.modalCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <View style={styles.header}>
            <Text style={[styles.title, { color: colors.onSurface }]}>Cronometros</Text>
            <Pressable onPress={onClose} style={styles.closeButton}>
              <Text style={[styles.closeButtonText, { color: colors.onSurfaceVariant }]}>Cerrar</Text>
            </Pressable>
          </View>

          <View style={[styles.tabRow, { backgroundColor: colors.surfaceContainer }]}>
            <Pressable
              onPress={() => setActiveTab('stopwatch')}
              style={[
                styles.tabButton,
                activeTab === 'stopwatch' && { backgroundColor: colors.primary },
              ]}
            >
              <Text style={[styles.tabButtonText, { color: activeTab === 'stopwatch' ? colors.onPrimary : colors.onSurfaceVariant }]}>
                Cronometro
              </Text>
            </Pressable>
            <Pressable
              onPress={() => setActiveTab('timer')}
              style={[
                styles.tabButton,
                activeTab === 'timer' && { backgroundColor: colors.primary },
              ]}
            >
              <Text style={[styles.tabButtonText, { color: activeTab === 'timer' ? colors.onPrimary : colors.onSurfaceVariant }]}>
                Temporizador
              </Text>
            </Pressable>
          </View>

          {activeTab === 'stopwatch' ? (
            <StopwatchPanel />
          ) : (
            <TimerPanel onStartTimer={onStartTimer} onCancelTimer={onCancelTimer} />
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.55)',
  },
  modalCard: {
    width: '100%',
    maxWidth: 420,
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
    gap: 14,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    fontSize: 18,
    fontWeight: '800',
  },
  closeButton: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 999,
  },
  closeButtonText: {
    fontSize: 12,
    fontWeight: '700',
  },
  tabRow: {
    borderRadius: 999,
    padding: 4,
    flexDirection: 'row',
    gap: 4,
  },
  tabButton: {
    flex: 1,
    borderRadius: 999,
    paddingVertical: 9,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabButtonText: {
    fontSize: 12,
    fontWeight: '800',
  },
  panel: {
    gap: 12,
    alignItems: 'center',
  },
  clockValue: {
    fontSize: 52,
    fontWeight: '900',
    letterSpacing: 1,
  },
  controls: {
    width: '100%',
    gap: 8,
  },
  presets: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 8,
  },
  presetChip: {
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  presetChipText: {
    fontSize: 11,
    fontWeight: '800',
  },
});

