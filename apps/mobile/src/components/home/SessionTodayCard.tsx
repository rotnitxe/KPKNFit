import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  Image,
  StyleSheet,
  Dimensions,
  Pressable,
} from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { PlayIcon, ChevronRightIcon, ChevronLeftIcon } from '../icons';
import { CaupolicanIcon } from '../CaupolicanIcon';
import type { Program, Session } from '../../types/workout';

const { width } = Dimensions.get('window');

export interface TodaySessionItem {
  session: Session;
  program: Program;
  location: { macroIndex: number; mesoIndex: number; weekId: string };
  isCompleted: boolean;
  dayOfWeek?: number;
  isOngoing?: boolean;
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

  if (sessions.length === 0) {
    return (
      <TouchableOpacity 
        onPress={onOpenStartWorkoutModal}
        activeOpacity={0.9}
        style={{ paddingHorizontal: 24 }}
      >
        <LiquidGlassCard style={styles.emptyCard}>
          <CaupolicanIcon size={48} color={`${colors.onSurfaceVariant}4D`} />
          <View style={styles.emptyContent}>
            <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>DÍA DE DESCANSO</Text>
            <Text style={[styles.emptySubtitle, { color: colors.onSurfaceVariant }]}>Configura un programa para hoy</Text>
          </View>
        </LiquidGlassCard>
      </TouchableOpacity>
    );
  }

  const currentItem = sessions[activeIndex];
  const isToday = currentItem.dayOfWeek === currentDayOfWeek;
  const isOngoing = currentItem.isOngoing;

  return (
    <View style={styles.container}>
      <View style={styles.pagingWrapper}>
        <LiquidGlassCard style={styles.sessionCard}>
          {currentItem.program.coverImage ? (
            <Image 
              source={{ uri: currentItem.program.coverImage }} 
              style={[StyleSheet.absoluteFill, { opacity: 0.6 }]} 
              resizeMode="cover"
            />
          ) : (
            <View style={[StyleSheet.absoluteFill, { backgroundColor: colors.primaryContainer, opacity: 0.2 }]} />
          )}
          
          <View style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(0,0,0,0.4)' }]} />

          <View style={styles.cardHeader}>
            <View style={[styles.statusBadge, { backgroundColor: isOngoing ? colors.primary : 'rgba(255,255,255,0.1)' }]}>
              <Text style={[styles.statusBadgeText, { color: isOngoing ? 'white' : 'white' }]}>
                {isOngoing ? 'SESIÓN EN CURSO' : isToday ? 'SESIÓN DE HOY' : 'PRÓXIMA SESIÓN'}
              </Text>
            </View>
          </View>

          <View style={styles.cardMain}>
            <View style={styles.textStack}>
              <Text style={styles.programLabel}>{programName.toUpperCase()}</Text>
              <Text style={styles.sessionTitle} numberOfLines={2}>{currentItem.session.name}</Text>
            </View>
            
            <Pressable 
              onPress={() => onStartWorkout(currentItem.session, currentItem.program, currentItem.location)}
              style={({ pressed }) => [
                styles.actionButton, 
                { backgroundColor: isOngoing ? colors.primary : 'white' },
                pressed && { opacity: 0.8 }
              ]}
            >
              <PlayIcon size={32} color={isOngoing ? 'white' : 'black'} fill={isOngoing ? 'white' : 'black'} />
            </Pressable>
          </View>
        </LiquidGlassCard>

        {sessions.length > 1 && (
          <View style={styles.navArrows}>
             <Pressable 
              onPress={() => setActiveIndex(prev => (prev > 0 ? prev - 1 : sessions.length - 1))}
              style={styles.arrowBtn}
            >
              <ChevronLeftIcon size={24} color="white" />
            </Pressable>
            <Pressable 
              onPress={() => setActiveIndex(prev => (prev < sessions.length - 1 ? prev + 1 : 0))}
              style={styles.arrowBtn}
            >
              <ChevronRightIcon size={24} color="white" />
            </Pressable>
          </View>
        )}
      </View>

      {sessions.length > 1 && (
        <View style={styles.dotContainer}>
          {sessions.map((_, i) => (
            <View 
              key={i} 
              style={[
                styles.dot, 
                i === activeIndex ? [styles.activeDot, { backgroundColor: colors.primary }] : { backgroundColor: `${colors.onSurface}20` }
              ]} 
            />
          ))}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    gap: 16,
  },
  pagingWrapper: {
    paddingHorizontal: 24,
    position: 'relative',
  },
  sessionCard: {
    height: 200,
    borderRadius: 40,
    overflow: 'hidden',
    justifyContent: 'space-between',
    padding: 24,
  },
  cardHeader: {
    flexDirection: 'row',
  },
  statusBadge: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
  },
  statusBadgeText: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.5,
  },
  cardMain: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  textStack: {
    flex: 1,
    gap: 4,
    marginRight: 16,
  },
  programLabel: {
    color: 'rgba(255,255,255,0.6)',
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 2,
  },
  sessionTitle: {
    color: 'white',
    fontSize: 28,
    fontWeight: '900',
    lineHeight: 32,
    letterSpacing: -0.5,
  },
  actionButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
  },
  navArrows: {
    position: 'absolute',
    left: 40,
    right: 40,
    top: '50%',
    marginTop: -20,
    flexDirection: 'row',
    justifyContent: 'space-between',
    pointerEvents: 'box-none',
  },
  arrowBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(0,0,0,0.3)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  dotContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  activeDot: {
    width: 24,
  },
  emptyCard: {
    height: 200,
    borderRadius: 40,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
  },
  emptyContent: {
    alignItems: 'center',
    gap: 4,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '900',
    letterSpacing: 2,
  },
  emptySubtitle: {
    fontSize: 12,
    fontWeight: '600',
    opacity: 0.5,
  },
});
