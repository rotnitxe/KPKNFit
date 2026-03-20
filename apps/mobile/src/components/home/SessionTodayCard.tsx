import React, { useState } from 'react';
import {
  View,
  Text,
  Image,
  StyleSheet,
  Pressable,
  Dimensions,
} from 'react-native';
import { useColors, useTheme } from '../../theme';
import { PlayIcon, ChevronRightIcon, ChevronLeftIcon } from '../icons';
import { CaupolicanIcon } from '../CaupolicanIcon';
import type { Program, Session } from '../../types/workout';

const { width: SCREEN_W } = Dimensions.get('window');

export interface TodaySessionItem {
  session: Session;
  program: Program;
  location: { macroIndex: number; mesoIndex: number; weekId: string };
  isCompleted: boolean;
  dayOfWeek?: number;
  isOngoing?: boolean;
  log?: any;
}

interface SessionTodayCardProps {
  programName: string;
  sessions: TodaySessionItem[];
  currentDayOfWeek: number;
  onStartWorkout: (session: Session, program: Program, location: any) => void;
  onOpenStartWorkoutModal: () => void;
}

export const SessionTodayCard: React.FC<SessionTodayCardProps> = ({
  programName,
  sessions,
  currentDayOfWeek,
  onStartWorkout,
  onOpenStartWorkoutModal,
}) => {
  const [activeIndex, setActiveIndex] = useState(0);
  const colors = useColors();
  const { isDark } = useTheme();

  // ── Empty state ────────────────────────────────────────────────────
  if (sessions.length === 0) {
    return (
      <View style={styles.emptyWrap}>
        <Pressable
          onPress={onOpenStartWorkoutModal}
          style={({ pressed }) => [
            styles.emptyCard,
            { backgroundColor: colors.primaryContainer },
            pressed && { opacity: 0.9, transform: [{ scale: 0.98 }] },
          ]}
        >
          <CaupolicanIcon size={48} color={colors.onPrimaryContainer} />
          <View style={styles.emptyTextWrap}>
            <Text style={[styles.emptyTitle, { color: colors.onPrimaryContainer }]}>Día de descanso</Text>
            <Text style={[styles.emptySubtitle, { color: colors.onPrimaryContainer, opacity: 0.6 }]}>
              Configura un programa para hoy
            </Text>
          </View>
        </Pressable>
      </View>
    );
  }

  // ── Active session ────────────────────────────────────────────────
  const current = sessions[activeIndex];
  const coverImage = current.program.coverImage;
  const isToday = current.dayOfWeek === currentDayOfWeek;
  const isOngoing = current.isOngoing;

  return (
    <View style={styles.container}>
      {/* Card */}
      <View style={styles.cardWrap}>
        <View style={styles.card}>
          {/* Background */}
          {coverImage ? (
            <Image source={{ uri: coverImage }} style={StyleSheet.absoluteFill} resizeMode="cover" />
          ) : (
            <View style={[StyleSheet.absoluteFill, { backgroundColor: isDark ? '#2A2438' : '#6750A4' }]} />
          )}
          {/* Dark overlay */}
          <View style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(0,0,0,0.3)' }]} />

          {/* Badge */}
          <View style={styles.badgeWrap}>
            <View style={styles.badge}>
              <Text style={styles.badgeText}>
                {isToday ? 'SESIÓN DE HOY' : 'PRÓXIMA SESIÓN'}
              </Text>
            </View>
          </View>

          {/* Bottom content */}
          <View style={styles.cardBottom}>
            <View style={styles.textStack}>
              <Text style={styles.programLabel}>{programName.toUpperCase()}</Text>
              <Text style={styles.sessionName} numberOfLines={2}>{current.session.name}</Text>
            </View>

            <Pressable
              onPress={() => onStartWorkout(current.session, current.program, current.location)}
              style={({ pressed }) => [
                styles.playBtn,
                pressed && { transform: [{ scale: 0.9 }] },
              ]}
            >
              <PlayIcon size={28} color="black" fill="black" />
            </Pressable>
          </View>
        </View>
      </View>

      {/* Pagination */}
      {sessions.length > 1 && (
        <View style={styles.pagination}>
          <Pressable
            onPress={() => setActiveIndex(prev => (prev > 0 ? prev - 1 : sessions.length - 1))}
            style={({ pressed }) => [styles.arrowBtn, pressed && { opacity: 0.7 }]}
          >
            <ChevronLeftIcon size={20} color={colors.onSurfaceVariant} />
          </Pressable>
          <View style={styles.dots}>
            {sessions.map((_, i) => (
              <View
                key={i}
                style={[
                  styles.dot,
                  i === activeIndex
                    ? [styles.dotActive, { backgroundColor: colors.primary }]
                    : { backgroundColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)' },
                ]}
              />
            ))}
          </View>
          <Pressable
            onPress={() => setActiveIndex(prev => (prev < sessions.length - 1 ? prev + 1 : 0))}
            style={({ pressed }) => [styles.arrowBtn, pressed && { opacity: 0.7 }]}
          >
            <ChevronRightIcon size={20} color={colors.onSurfaceVariant} />
          </Pressable>
        </View>
      )}
    </View>
  );
};

const CARD_H = Math.round((Math.min(SCREEN_W - 48, 420)) * 9 / 16);

const styles = StyleSheet.create({
  container: {
    width: '100%',
    gap: 16,
    alignItems: 'center',
  },
  emptyWrap: {
    paddingHorizontal: 24,
  },
  emptyCard: {
    height: CARD_H,
    borderRadius: 40,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
  },
  emptyTextWrap: {
    alignItems: 'center',
    gap: 4,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '900',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  emptySubtitle: {
    fontSize: 12,
    fontWeight: '500',
  },
  cardWrap: {
    paddingHorizontal: 24,
    width: '100%',
  },
  card: {
    width: '100%',
    height: CARD_H,
    borderRadius: 40,
    overflow: 'hidden',
  },
  badgeWrap: {
    position: 'absolute',
    top: 20,
    left: 20,
  },
  badge: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
  },
  badgeText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.5,
  },
  cardBottom: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 24,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  textStack: {
    flex: 1,
    gap: 4,
    paddingRight: 16,
  },
  programLabel: {
    color: 'rgba(255,255,255,0.6)',
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 2,
  },
  sessionName: {
    color: '#FFFFFF',
    fontSize: 24,
    fontWeight: '900',
    lineHeight: 28,
    letterSpacing: -0.5,
  },
  playBtn: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.25,
    shadowRadius: 16,
    elevation: 8,
  },
  pagination: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 20,
  },
  arrowBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(0,0,0,0.04)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  dots: {
    flexDirection: 'row',
    gap: 6,
    alignItems: 'center',
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  dotActive: {
    width: 24,
  },
});
