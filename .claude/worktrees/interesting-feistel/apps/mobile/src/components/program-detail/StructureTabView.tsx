import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import type { Program, Session } from '../../types/workout';
import { WeekView } from './WeekView';

interface StructureTabViewProps {
  program: Program;
  onSessionPress?: (session: Session) => void;
}

export const StructureTabView: React.FC<StructureTabViewProps> = ({ program, onSessionPress }) => {
  const colors = useColors();
  let absoluteWeekNumber = 1;

  if (!program.macrocycles || program.macrocycles.length === 0) {
    return (
      <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
        Este programa no tiene estructura definida.
      </Text>
    );
  }

  return (
    <View style={styles.container}>
      {program.macrocycles?.map((macro) => (
        <View key={macro.id}>
          {macro.blocks?.map((block) => (
            <View key={block.id} style={styles.blockContainer}>
              <Text style={[styles.blockTitle, { color: colors.onSurface, borderBottomColor: `${colors.onSurface}1A` }]}>
                {block.name}
              </Text>
              
              {block.mesocycles?.map((meso) => (
                <View key={meso.id} style={styles.mesoContainer}>
                  {/* Etiqueta del Mesociclo */}
                  <View style={[styles.mesoCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}0D` }]}>
                    <Text style={[styles.mesoBadge, { color: colors.primary }]}>
                      Mesociclo: {meso.goal}
                    </Text>
                    <Text style={[styles.mesoName, { color: colors.onSurface }]}>
                      {meso.name}
                    </Text>
                  </View>

                  {/* Semanas del Mesociclo */}
                  {meso.weeks?.map((week) => {
                    const currentWeekNum = absoluteWeekNumber++;
                    return (
                      <WeekView 
                        key={week.id} 
                        week={week} 
                        weekNumber={currentWeekNum} 
                        onSessionPress={onSessionPress}
                      />
                    );
                  })}
                </View>
              ))}
            </View>
          ))}
        </View>
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  emptyText: {
    textAlign: 'center',
    padding: 24,
  },
  blockContainer: {
    marginBottom: 32,
  },
  blockTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
    borderBottomWidth: 1,
    paddingBottom: 8,
  },
  mesoContainer: {
    marginBottom: 16,
  },
  mesoCard: {
    borderWidth: 1,
    padding: 12,
    borderRadius: 12,
    marginBottom: 16,
  },
  mesoBadge: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    fontWeight: 'bold',
  },
  mesoName: {
    fontSize: 14,
    fontWeight: '600',
    marginTop: 4,
    lineHeight: 20,
  },
});
