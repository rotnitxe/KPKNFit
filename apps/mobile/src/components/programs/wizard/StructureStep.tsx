import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useColors } from '../../../theme';
import { SplitTemplate } from '../../../types/workout';
import { SPLIT_TEMPLATES } from '../../../data/splitTemplates';

interface StructureStepProps {
  selectedSplit: SplitTemplate | null;
  onSelectSplit: (split: SplitTemplate) => void;
}

const StructureStep: React.FC<StructureStepProps> = ({ selectedSplit, onSelectSplit }) => {
  const colors = useColors();

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      <Text style={[styles.title, { color: colors.onSurface }]}>
        Selecciona el Split
      </Text>
      <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
        Elige la distribución de sesiones por día de la semana
      </Text>

      <View style={styles.gallery}>
        {SPLIT_TEMPLATES.map((split) => {
          const isSelected = selectedSplit?.id === split.id;
          return (
            <TouchableOpacity
              key={split.id}
              onPress={() => onSelectSplit(split)}
              style={[
                styles.splitCard,
                {
                  backgroundColor: colors.surfaceContainer,
                  borderColor: colors.outlineVariant,
                },
                isSelected && {
                  borderColor: colors.primary,
                  borderWidth: 2,
                },
              ]}
            >
              <View style={styles.splitHeader}>
                <Text style={[styles.splitName, { color: colors.onSurface }]}>
                  {split.name}
                </Text>
                <View style={[
                  styles.difficultyBadge,
                  { backgroundColor: colors.surfaceVariant },
                ]}>
                  <Text style={[styles.difficultyText, { color: colors.onSurfaceVariant }]}>
                    {split.difficulty}
                  </Text>
                </View>
              </View>

              <Text style={[styles.splitDescription, { color: colors.onSurfaceVariant }]}>
                {split.description}
              </Text>

              <View style={styles.patternBar}>
                {split.pattern.map((day, i) => (
                  <View key={i} style={styles.patternItem}>
                    <View
                      style={[
                        styles.patternDot,
                        {
                          backgroundColor:
                            day.toLowerCase() === 'descanso'
                              ? colors.surfaceVariant
                              : colors.primary,
                        },
                      ]}
                    />
                  </View>
                ))}
              </View>

              <View style={styles.tags}>
                {split.tags.map((tag, i) => (
                  <View key={i} style={[styles.tag, { backgroundColor: colors.primaryContainer }]}>
                    <Text style={[styles.tagText, { color: colors.onPrimaryContainer }]}>
                      {tag}
                    </Text>
                  </View>
                ))}
              </View>

              <View style={styles.footerInfo}>
                <Text style={[styles.daysText, { color: colors.onSurfaceVariant }]}>
                  {split.daysPerWeek} días/semana
                </Text>
                <Text style={[styles.sessionsText, { color: colors.onSurfaceVariant }]}>
                  {split.sessions?.filter(s => s.name.toLowerCase() !== 'descanso').length ?? 0} sesiones
                </Text>
              </View>
            </TouchableOpacity>
          );
        })}
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  title: {
    fontSize: 22,
    fontWeight: '900',
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 24,
    opacity: 0.7,
  },
  gallery: {
    gap: 16,
  },
  splitCard: {
    padding: 16,
    borderRadius: 20,
    borderWidth: 1,
  },
  splitHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  splitName: {
    fontSize: 16,
    fontWeight: '900',
  },
  difficultyBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  difficultyText: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  splitDescription: {
    fontSize: 13,
    lineHeight: 18,
    marginBottom: 12,
    opacity: 0.7,
  },
  patternBar: {
    flexDirection: 'row',
    gap: 4,
    marginBottom: 12,
  },
  patternItem: {
    flex: 1,
    alignItems: 'center',
  },
  patternDot: {
    width: '100%',
    height: 6,
    borderRadius: 3,
  },
  tags: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginBottom: 12,
  },
  tag: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  tagText: {
    fontSize: 10,
    fontWeight: '700',
  },
  footerInfo: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  daysText: {
    fontSize: 11,
    fontWeight: '700',
  },
  sessionsText: {
    fontSize: 11,
    fontWeight: '700',
  },
});

export default StructureStep;