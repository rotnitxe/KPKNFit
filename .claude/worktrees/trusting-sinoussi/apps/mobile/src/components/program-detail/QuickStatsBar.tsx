import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import type { Program } from '../../types/workout';

interface QuickStatsBarProps {
  program: Program;
  authorLabel: string;
  statusLabel: string;
}

interface StatItem {
  label: string;
  value: string | number;
  icon?: React.ReactNode;
}

export const QuickStatsBar: React.FC<QuickStatsBarProps> = ({ program, authorLabel, statusLabel }) => {
  const colors = useColors();

  let totalWeeks = 0;
  let totalSessions = 0;

  program.macrocycles?.forEach((macro) => {
    macro.blocks?.forEach((block) => {
      block.mesocycles?.forEach((meso) => {
        meso.weeks?.forEach((week) => {
          totalWeeks += 1;
          totalSessions += week.sessions?.length ?? 0;
        });
      });
    });
  });

  const stats: StatItem[] = [
    { label: 'Semanas', value: totalWeeks },
    { label: 'Sesiones', value: totalSessions },
    { label: 'Autor', value: authorLabel },
    { label: 'Status', value: statusLabel },
  ];

  return (
    <View 
      style={[styles.container, { backgroundColor: colors.surfaceContainerHigh }]}
    >
      {stats.map((stat, idx) => (
        <View key={idx} style={styles.statItem}>
          <Text 
            style={[styles.label, { color: colors.onSurfaceVariant }]}
          >
            {stat.label}
          </Text>
          <View style={styles.valueRow}>
            {stat.icon && <View style={styles.iconContainer}>{stat.icon}</View>}
            <Text 
              style={[styles.value, { color: colors.onSurface }]}
            >
              {stat.value}
            </Text>
          </View>
        </View>
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    paddingVertical: 16,
    marginTop: -24,
    marginHorizontal: 16,
    borderRadius: 16,
    // shadow elevation simplified
  },
  statItem: {
    alignItems: 'center',
    paddingHorizontal: 16,
    flex: 1,
  },
  label: {
    fontSize: 12,
    fontWeight: '500',
    marginBottom: 4,
  },
  valueRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  iconContainer: {
    marginRight: 4,
  },
  value: {
    fontSize: 16,
    fontWeight: 'bold',
  },
});
