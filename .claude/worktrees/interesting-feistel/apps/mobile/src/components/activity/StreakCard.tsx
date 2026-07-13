import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';

interface StreakCardProps {
  datesWithWorkout: string[]; // YYYY-MM-DD or ISO date strings
  weekCompletionPct: number; // 0..100
}

const WEEKDAY_LABELS = ['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sá', 'Do'];

/**
 * Formats a date as YYYY-MM-DD in local time for comparison.
 */
function formatDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export const StreakCard: React.FC<StreakCardProps> = ({
  datesWithWorkout,
  weekCompletionPct,
}) => {
  const colors = useColors();

  const { streakDays, todayCompleted } = useMemo(() => {
    if (!datesWithWorkout.length) {
      return { streakDays: 0, todayCompleted: false };
    }

    const dateSet = new Set(datesWithWorkout.map((d) => formatDateKey(new Date(d))));
    let count = 0;
    const current = new Date();
    const todayKey = formatDateKey(current);
    const isTodayCompleted = dateSet.has(todayKey);

    // If today is not completed, check if yesterday was to continue streak
    let checkDate = new Date(current);
    if (!isTodayCompleted) {
      checkDate.setDate(checkDate.getDate() - 1);
      const yesterdayKey = formatDateKey(checkDate);
      if (!dateSet.has(yesterdayKey)) {
        return { streakDays: 0, todayCompleted: false };
      }
    }

    // Reset checkDate for counting
    checkDate = new Date(current);
    if (!isTodayCompleted) {
      checkDate.setDate(checkDate.getDate() - 1);
    }

    // Count consecutive days
    while (dateSet.has(formatDateKey(checkDate))) {
      count++;
      checkDate.setDate(checkDate.getDate() - 1);
    }

    return { streakDays: count, todayCompleted: isTodayCompleted };
  }, [datesWithWorkout]);

  const weekDays = useMemo(() => {
    const today = new Date();
    const currentDayOfWeek = today.getDay();
    const mondayOffset = currentDayOfWeek === 0 ? -6 : 1 - currentDayOfWeek;
    const monday = new Date(today);
    monday.setDate(monday.getDate() + mondayOffset);
    monday.setHours(0, 0, 0, 0);

    const dateSet = new Set(datesWithWorkout.map((d) => formatDateKey(new Date(d))));

    return WEEKDAY_LABELS.map((label, index) => {
      const dayDate = new Date(monday);
      dayDate.setDate(monday.getDate() + index);
      const dayKey = formatDateKey(dayDate);
      const isCompleted = dateSet.has(dayKey);
      const isToday = formatDateKey(today) === dayKey;

      return {
        label,
        isCompleted,
        isToday,
      };
    });
  }, [datesWithWorkout]);

  const progress = Math.min(Math.max(weekCompletionPct || 0, 0), 100);

  return (
    <View style={[styles.container, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>Racha</Text>
        {streakDays > 0 && (
          <View style={[styles.activeBadge, { backgroundColor: `${colors.cyberWarning}1A`, borderColor: `${colors.cyberWarning}33` }]}>
            <Text style={[styles.activeText, { color: colors.cyberWarning }]}>ACTIVA</Text>
          </View>
        )}
      </View>

      {/* Streak count */}
      <View style={styles.streakSection}>
        <View style={styles.streakRow}>
          <Text style={[styles.streakValue, { color: colors.onSurface }]}>{streakDays}</Text>
          <Text style={[styles.streakLabel, { color: colors.onSurfaceVariant }]}>
            {streakDays === 1 ? 'día consecutivo' : 'días consecutivos'}
          </Text>
        </View>
        {streakDays === 0 && (
          <Text style={[styles.streakHint, { color: colors.onSurfaceVariant }]}>
            ¡Entrena hoy para empezar tu racha!
          </Text>
        )}
      </View>

      {/* Week days visualization */}
      <View style={styles.weekDaysRow}>
        {weekDays.map((day, index) => (
          <View key={index} style={styles.dayColumn}>
            <View
              style={[
                styles.dayCircle,
                {
                  backgroundColor: day.isCompleted ? colors.primary : colors.surfaceContainer,
                  borderWidth: day.isCompleted ? 0 : 1.5,
                  borderColor: colors.outline,
                },
              ]}
            >
              {day.isCompleted && (
                <View style={[styles.innerDot, { backgroundColor: colors.onPrimary }]} />
              )}
            </View>
            <Text
              style={[
                styles.dayLabel,
                {
                  color: day.isCompleted ? colors.primary : colors.onSurfaceVariant,
                  fontWeight: day.isToday ? '700' : '400',
                },
              ]}
            >
              {day.label}
            </Text>
          </View>
        ))}
      </View>

      {/* Weekly progress */}
      <View style={[styles.progressSection, { borderTopColor: colors.outlineVariant }]}>
        <View style={styles.progressHeader}>
          <Text style={[styles.progressLabel, { color: colors.onSurfaceVariant }]}>Semanal</Text>
          <Text style={[styles.progressValue, { color: colors.primary }]}>{Math.round(progress)}%</Text>
        </View>

        <View style={[styles.progressTrack, { backgroundColor: colors.surfaceContainerHigh }]}>
          <View
            style={[
              styles.progressFill,
              { width: `${progress}%`, backgroundColor: colors.primary },
            ]}
          />
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  title: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  activeBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 16,
    borderWidth: 1,
  },
  activeText: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  streakSection: {
    marginBottom: 16,
  },
  streakRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  streakValue: {
    fontSize: 48,
    fontWeight: '800',
  },
  streakLabel: {
    fontSize: 15,
    lineHeight: 20,
    marginLeft: 12,
  },
  streakHint: {
    fontSize: 12,
    lineHeight: 16,
    marginTop: 4,
    fontStyle: 'italic',
  },
  weekDaysRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  dayColumn: {
    alignItems: 'center',
  },
  dayCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 6,
  },
  innerDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  dayLabel: {
    fontSize: 10,
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  progressSection: {
    paddingTop: 12,
    borderTopWidth: 1,
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginBottom: 8,
  },
  progressLabel: {
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
  },
  progressValue: {
    fontSize: 12,
    fontWeight: '700',
  },
  progressTrack: {
    height: 8,
    width: '100%',
    borderRadius: 4,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 4,
  },
});
