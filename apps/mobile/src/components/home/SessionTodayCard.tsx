import React, { useState } from 'react';
import { View, Text, TouchableOpacity, Image, StyleSheet, Dimensions } from 'react-native';
import { useNavigation } from '@react-navigation/native';
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

  const currentSession = sessions[activeIndex];
  const coverImage = currentSession.program.coverImage;

  return (
    <View style={styles.container}>
      <LiquidGlassCard style={styles.sessionCard}>
        {coverImage ? (
          <Image source={{ uri: coverImage }} style={StyleSheet.absoluteFill} />
        ) : (
          <View style={[StyleSheet.absoluteFill, { backgroundColor: colors.primaryContainer, opacity: 0.6 }]} />
        )}
        
        {/* Overlay for glass effect */}
        <View style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(0,0,0,0.4)' }]} />

        <View style={styles.cardInfo}>
          <View style={styles.textContainer}>
            <Text style={styles.eyebrow}>{programName.toUpperCase()}</Text>
            <Text style={styles.sessionName} numberOfLines={2}>
              {currentSession.session.name}
            </Text>
          </View>

          <TouchableOpacity 
            style={styles.playButton}
            onPress={() => onStartWorkout(currentSession.session, currentSession.program, currentSession.location)}
          >
            <PlayIcon size={28} color="#000" fill="#000" />
          </TouchableOpacity>
        </View>

        {/* Badge */}
        <View style={styles.badge}>
          <Text style={styles.badgeText}>
            {currentSession.dayOfWeek === currentDayOfWeek ? 'SESIÓN DE HOY' : 'PRÓXIMA SESIÓN'}
          </Text>
        </View>
      </LiquidGlassCard>

      {/* Pagination */}
      {sessions.length > 1 && (
        <View style={styles.pagination}>
          <TouchableOpacity onPress={() => setActiveIndex(prev => (prev > 0 ? prev - 1 : sessions.length - 1))}>
            <ChevronLeftIcon size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>
          
          <View style={styles.dots}>
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

          <TouchableOpacity onPress={() => setActiveIndex(prev => (prev < sessions.length - 1 ? prev + 1 : 0))}>
            <ChevronRightIcon size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    alignItems: 'center',
    gap: 16,
  },
  emptyCard: {
    height: 192,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
  },
  emptyContent: {
    alignItems: 'center',
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '900',
    letterSpacing: 2,
  },
  emptySubtitle: {
    fontSize: 12,
    fontWeight: '500',
    opacity: 0.6,
  },
  sessionCard: {
    width: width - 48,
    aspectRatio: 16 / 9,
    justifyContent: 'flex-end',
  },
  cardInfo: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    padding: 24,
  },
  textContainer: {
    flex: 1,
    marginRight: 16,
  },
  eyebrow: {
    color: 'rgba(255,255,255,0.6)',
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 2,
  },
  sessionName: {
    color: '#FFF',
    fontSize: 24,
    fontWeight: '900',
    marginTop: 4,
  },
  playButton: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#FFF',
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },
  badge: {
    position: 'absolute',
    top: 24,
    left: 24,
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
  },
  badgeText: {
    color: '#FFF',
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 2,
  },
  pagination: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 24,
  },
  dots: {
    flexDirection: 'row',
    gap: 6,
  },
  dot: {
    height: 6,
    width: 6,
    borderRadius: 3,
  },
  activeDot: {
    width: 24,
  },
});
