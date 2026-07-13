import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import type { Session } from '../../types/workout';

interface SessionCardProps {
  session: Session;
  dayIndex?: number;
  onPress?: () => void;
}

export const SessionCard: React.FC<SessionCardProps> = ({ session, dayIndex, onPress }) => {
  const colors = useColors();
  return (
    <Pressable 
      onPress={onPress}
      disabled={!onPress}
      style={({ pressed }) => [
        styles.container,
        { 
          backgroundColor: `${colors.onSurface}0D`,
          borderColor: `${colors.onSurface}1A`,
          opacity: pressed && onPress ? 0.7 : 1
        }
      ]}
    >
      <View style={styles.infoSection}>
        <Text style={[styles.sessionName, { color: colors.onSurface }]}>
          {session.name}
        </Text>
        {session.focus ? (
          <Text style={[styles.focusText, { color: colors.primary }]}>{session.focus}</Text>
        ) : null}
        <Text style={[styles.exerciseCount, { color: colors.onSurfaceVariant }]}>
          {session.exercises?.length || 0} ejercicios
        </Text>
      </View>
      
      <View style={[styles.iconBadge, { backgroundColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.playIcon, { color: colors.onSurface }]}>▶</Text>
      </View>
    </Pressable>
  );
};

const styles = StyleSheet.create({
  container: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    marginBottom: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  infoSection: {
    flex: 1,
    marginRight: 16,
  },
  sessionName: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 4,
    lineHeight: 20,
  },
  focusText: {
    fontSize: 14,
    marginBottom: 4,
  },
  exerciseCount: {
    fontSize: 12,
  },
  iconBadge: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  playIcon: {
    fontSize: 12,
  },
});
