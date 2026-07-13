import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { useColors } from '../../../theme';
import { ProgramTemplateOption } from '../../../types/workout';
import { PROGRAM_TEMPLATES } from '../../../data/programTemplates';
import { TrendingUpIcon, BarChartIcon, StarIcon } from '../../icons';

interface TypeStepProps {
  selectedTemplateId: string | null;
  onSelectTemplate: (template: ProgramTemplateOption) => void;
}

const TypeStep: React.FC<TypeStepProps> = ({ selectedTemplateId, onSelectTemplate }) => {
  const colors = useColors();

  const getIcon = (iconType: string) => {
    switch (iconType) {
      case 'trending':
        return <TrendingUpIcon size={24} color={colors.primary} />;
      case 'barchart':
        return <BarChartIcon size={24} color={colors.primary} />;
      case 'star':
        return <StarIcon size={24} color={colors.primary} />;
      default:
        return <TrendingUpIcon size={24} color={colors.primary} />;
    }
  };

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      <Text style={[styles.title, { color: colors.onSurface }]}>
        Selecciona el tipo de programa
      </Text>
      <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
        Elige la estructura que mejor se adapte a tus objetivos
      </Text>

      <View style={styles.cardsContainer}>
        {PROGRAM_TEMPLATES.map((template) => (
          <TouchableOpacity
            key={template.id}
            onPress={() => onSelectTemplate(template)}
            style={[
              styles.card,
              {
                backgroundColor: colors.surfaceContainer,
                borderColor: colors.outlineVariant,
              },
              selectedTemplateId === template.id && {
                borderColor: colors.primary,
                borderWidth: 2,
              },
            ]}
          >
            <View style={[
              styles.iconCircle,
              {
                backgroundColor: selectedTemplateId === template.id ? colors.primary : colors.surfaceVariant,
              },
            ]}>
              {getIcon(template.iconType)}
            </View>
            <View style={styles.cardContent}>
              <Text style={[styles.cardTitle, { color: colors.onSurface }]}>
                {template.name}
              </Text>
              <Text style={[styles.cardDescription, { color: colors.onSurfaceVariant }]}>
                {template.description}
              </Text>
              <View style={styles.badges}>
                <View style={[
                  styles.badge,
                  { backgroundColor: template.type === 'simple' ? colors.primaryContainer : colors.secondaryContainer },
                ]}>
                  <Text style={[
                    styles.badgeText,
                    { color: template.type === 'simple' ? colors.onPrimaryContainer : colors.onSecondaryContainer },
                  ]}>
                    {template.type === 'simple' ? 'SIMPLE' : 'COMPLEJO'}
                  </Text>
                </View>
                <Text style={[styles.weeksText, { color: colors.onSurfaceVariant }]}>
                  {template.weeks} semanas
                </Text>
              </View>
            </View>
          </TouchableOpacity>
        ))}
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
  cardsContainer: {
    gap: 16,
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 20,
    borderWidth: 1,
    gap: 16,
  },
  iconCircle: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardContent: {
    flex: 1,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '900',
    marginBottom: 4,
  },
  cardDescription: {
    fontSize: 13,
    lineHeight: 18,
    opacity: 0.7,
    marginBottom: 8,
  },
  badges: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  weeksText: {
    fontSize: 11,
    fontWeight: '700',
  },
});

export default TypeStep;