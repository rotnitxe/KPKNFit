import React, { useMemo } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import type { Block, ProgramWeek, Mesocycle } from '../../types/workout';
import { CheckCircleIcon } from '../icons';
import { useColors } from '../../theme';

export interface BlockRoadmapProps {
  blocks: Block[];
  selectedBlockId?: string | null;
  onSelectBlock?: (blockId: string) => void;
  weekStatus?: Record<string, 'completed' | 'active' | 'pending'>; // weekId -> status
  currentWeekId?: string | null;
}

const mesocycleGoalColor = (goal: string) => {
  // Adaptar colores para metas comunes
  switch (goal) {
    case 'Acumulación':
      return '#60A5FA'; // azul
    case 'Intensificación':
      return '#F59E0B'; // amarillo
    case 'Realización':
      return '#10B981'; // verde
    case 'Descarga':
      return '#A78BFA'; // violeta
    default:
      return '#6B7280'; // gris
  }
};

const BlockRoadmap: React.FC<BlockRoadmapProps> = ({
  blocks,
  selectedBlockId,
  onSelectBlock,
  weekStatus = {},
  currentWeekId,
}) => {
  const colors = useColors();
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.roadmapRow}>
      {blocks.map((block, blockIdx) => (
        <View style={[styles.blockCell, block.id === selectedBlockId && { borderColor: colors.primary }]} key={block.id}>
          <TouchableOpacity onPress={() => onSelectBlock?.(block.id)} style={styles.blockNameBtn}>
            <Text style={[styles.blockName, { color: colors.primary }]}>{block.name.toUpperCase()}</Text>
          </TouchableOpacity>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.weeksRow}>
            {block.mesocycles.flatMap((meso, mesoIdx) =>
              meso.weeks.map((week, weekIdx) => {
                const isCurrent = week.id === currentWeekId;
                const status = weekStatus[week.id] || 'pending';
                return (
                  <View style={styles.weekChipShell} key={week.id}>
                    <TouchableOpacity style={[styles.weekChip,
                      isCurrent && { borderColor: colors.primary },
                      status === 'completed' && { backgroundColor: colors.cyberSuccess },
                    ]}>
                      <Text style={[styles.weekChipText, { color: colors.onSurface }]}>
                        S{weekIdx + 1 + mesoIdx * meso.weeks.length}
                      </Text>
                      {/* Completed checkmark */}
                      {status === 'completed' && (
                        <CheckCircleIcon size={16} color={colors.onSurfaceVariant} />
                      )}
                      {/* Mesocycle goal badge */}
                      {weekIdx === 0 && (
                        <View style={[styles.mesoGoalBadge, { backgroundColor: mesocycleGoalColor(meso.goal) }]}> 
                          <Text style={styles.mesoGoalText}>{meso.goal.charAt(0)}</Text>
                        </View>
                      )}
                    </TouchableOpacity>
                  </View>
                );
              })
            )}
          </ScrollView>
        </View>
      ))}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  roadmapRow: {
    flexDirection: 'row',
    gap: 16,
    paddingVertical: 8,
  },
  blockCell: {
    minWidth: 160,
    borderWidth: 2,
    borderRadius: 14,
    borderColor: 'rgba(0,0,0,0.07)',
    marginRight: 10,
    backgroundColor: 'rgba(255,255,255,0.7)',
    padding: 10,
  },
  blockNameBtn: {
    marginBottom: 6,
  },
  blockName: {
    fontWeight: '900',
    fontSize: 13,
    letterSpacing: 1,
    marginBottom: 3,
  },
  weeksRow: {
    flexDirection: 'row',
    gap: 10,
  },
  weekChipShell: {
    marginRight: 6,
    alignItems: 'center',
  },
  weekChip: {
    borderRadius: 8,
    borderWidth: 2,
    borderColor: 'rgba(0,0,0,0.12)',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 9,
    paddingVertical: 7,
    flexDirection: 'row',
    gap: 4,
    backgroundColor: 'rgba(255,255,255,0.75)',
    minWidth: 42,
  },
  weekChipText: {
    fontWeight: '700',
    fontSize: 12,
  },
  mesoGoalBadge: {
    position: 'absolute',
    top: -10,
    right: -10,
    width: 18,
    height: 18,
    borderRadius: 9,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#DDD',
    borderWidth: 1,
    borderColor: '#FFF',
    zIndex: 2,
  },
  mesoGoalText: {
    fontWeight: '900',
    fontSize: 10,
    color: '#FFF',
  },
});

export default React.memo(BlockRoadmap);
