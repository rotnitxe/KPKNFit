import React, { useEffect, useMemo, useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Dimensions } from 'react-native';
import { CheckCircleIcon, ClockIcon, ActivityIcon } from '../icons';
import { useColors } from '../../theme';
import { Canvas, Rect, LinearGradient, vec, Blur } from '@shopify/react-native-skia';
import Animated, { useAnimatedStyle, withSpring, useSharedValue } from 'react-native-reanimated';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface WorkoutHeaderProps {
  sessionName: string;
  startTime: number;
  completedSetsCount: number;
  totalSetsCount: number;
  onFinishPress: () => void;
  activePartName?: string;
  sessionFocus?: string;
  isResting?: boolean;
  restTimerRemaining?: number;
}

export const WorkoutHeader: React.FC<WorkoutHeaderProps> = ({
  sessionName,
  startTime,
  completedSetsCount,
  totalSetsCount,
  onFinishPress,
  activePartName = 'Sesion en curso',
  sessionFocus,
  isResting = false,
  restTimerRemaining = 0,
}) => {
  const colors = useColors();
  const [elapsed, setElapsed] = useState(0);
  const progressWidth = useSharedValue(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setElapsed(Math.floor((Date.now() - startTime) / 1000));
    }, 1000);
    return () => clearInterval(timer);
  }, [startTime]);

  const progressPercent = useMemo(() => {
    if (!totalSetsCount) return 0;
    return (completedSetsCount / totalSetsCount);
  }, [completedSetsCount, totalSetsCount]);

  useEffect(() => {
    progressWidth.value = withSpring(progressPercent);
  }, [progressPercent]);

  const animatedProgressStyle = useAnimatedStyle(() => ({
    width: `${progressWidth.value * 100}%`,
  }));

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
    const secs = (seconds % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
  };

  return (
    <View style={styles.wrapper}>
      {/* Skia Glassmorphism Background for Header Area if needed, 
          but usually we keep it integrated into the shell. 
          Let's just use premium styling here. */}
      
      <View style={styles.statusRow}>
        <View style={styles.statusLeft}>
          <View style={[styles.pill, { backgroundColor: `${colors.primary}15`, borderColor: `${colors.primary}30` }]}>
            <ClockIcon size={14} color={colors.primary} />
            <Text style={[styles.pillText, { color: colors.primary }]}>{formatTime(elapsed)}</Text>
          </View>
          
          {restTimerRemaining > 0 && (
            <View style={[styles.pill, { backgroundColor: `${colors.tertiary}15`, borderColor: `${colors.tertiary}30` }]}>
              <ActivityIcon size={14} color={colors.tertiary} />
              <Text style={[styles.pillText, { color: colors.tertiary }]}>Rest {formatTime(restTimerRemaining)}</Text>
            </View>
          )}
        </View>

        <View style={[styles.pill, { backgroundColor: `${colors.onSurface}08`, borderColor: `${colors.onSurface}15` }]}>
          <Text style={[styles.pillText, { color: colors.onSurfaceVariant, fontSize: 10 }]}>
            {completedSetsCount}/{totalSetsCount} SERIES
          </Text>
        </View>
      </View>

      <View style={styles.mainRow}>
        <View style={styles.titleWrap}>
          <Text style={[styles.sessionSubtitle, { color: colors.primary }]} numberOfLines={1}>
            {isResting ? 'DESCANSO ACTIVO' : activePartName.toUpperCase()}
          </Text>
          <Text style={[styles.sessionTitle, { color: colors.onSurface }]} numberOfLines={1}>
            {sessionName}
          </Text>
          {sessionFocus && (
            <Text style={[styles.focusText, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
              {sessionFocus}
            </Text>
          )}
        </View>

        <TouchableOpacity 
          style={[styles.finishBtn, { backgroundColor: colors.primary }]} 
          onPress={onFinishPress}
          activeOpacity={0.8}
        >
          <CheckCircleIcon size={28} color={colors.onPrimary} />
        </TouchableOpacity>
      </View>

      <View style={styles.progressContainer}>
        <View style={[styles.progressTrack, { backgroundColor: `${colors.onSurface}10` }]}>
          <Animated.View style={[styles.progressFill, { backgroundColor: colors.primary }, animatedProgressStyle]} />
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  wrapper: {
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 20,
    gap: 16,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  statusLeft: {
    flexDirection: 'row',
    gap: 8,
  },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
  },
  pillText: {
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 0.2,
  },
  mainRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  titleWrap: {
    flex: 1,
  },
  sessionSubtitle: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.5,
    marginBottom: 4,
  },
  sessionTitle: {
    fontSize: 32,
    fontWeight: '900',
    letterSpacing: -1,
  },
  focusText: {
    fontSize: 13,
    fontWeight: '500',
    marginTop: 4,
    opacity: 0.7,
  },
  finishBtn: {
    width: 56,
    height: 56,
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
  },
  progressContainer: {
    height: 6,
    width: '100%',
  },
  progressTrack: {
    flex: 1,
    borderRadius: 3,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 3,
  },
});
