import React from 'react';
import { View, Text, Modal, Pressable, ScrollView, StyleSheet } from 'react-native';
import type { MealTemplateSummary } from '../../stores/mealTemplateStore';
import { Button } from '../ui';
import { useColors } from '../../theme';

interface TemplatePickerModalProps {
  visible: boolean;
  onClose: () => void;
  templates: MealTemplateSummary[];
  onSelectTemplate: (templateId: string) => void;
  title?: string;
}

export const TemplatePickerModal: React.FC<TemplatePickerModalProps> = ({
  visible,
  onClose,
  templates,
  onSelectTemplate,
  title = 'Seleccionar Plantilla',
}) => {
  const colors = useColors();

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
    <View style={[styles.overlay, { backgroundColor: `${colors.background}CC` }]}>
        <View style={[styles.modalContainer, { backgroundColor: colors.surface, borderTopColor: colors.outlineVariant }]}>
          {/* Visual Handle */}
          <View style={[styles.handle, { backgroundColor: `${colors.onSurface}33` }]} />

          <Text style={[styles.title, { color: colors.onSurface }]}>
            {title}
          </Text>

          <ScrollView showsVerticalScrollIndicator={false} style={styles.scrollView}>
            {templates.length === 0 ? (
              <View style={styles.emptyContainer}>
                <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
                  No hay plantillas disponibles.
                </Text>
              </View>
            ) : (
              templates.map((template) => (
                <Pressable
                  key={template.id}
                  onPress={() => onSelectTemplate(template.id)}
                  style={({ pressed }) => [
                    styles.templateCard,
                    { 
                      backgroundColor: pressed ? `${colors.primary}1A` : colors.surfaceContainer,
                      borderColor: colors.outlineVariant,
                    }
                  ]}
                >
                  <Text style={[styles.templateTitle, { color: colors.onSurface }]}>
                    {template.name}
                  </Text>
                  <Text style={[styles.templateDescription, { color: colors.onSurfaceVariant }]} numberOfLines={2}>
                    {template.description}
                  </Text>
                  <View style={styles.macrosRow}>
                    <Text style={[styles.calories, { color: colors.primary }]}>
                      {Math.round(template.calories)} kcal
                    </Text>
                    <View style={styles.macrosBadges}>
                      <Text style={[styles.macroBadge, { color: colors.onSurfaceVariant }]}>
                        P: {Math.round(template.protein)}g
                      </Text>
                      <Text style={[styles.macroBadge, { color: colors.onSurfaceVariant }]}>
                        C: {Math.round(template.carbs)}g
                      </Text>
                      <Text style={[styles.macroBadge, { color: colors.onSurfaceVariant }]}>
                        G: {Math.round(template.fats)}g
                      </Text>
                      <Text style={[styles.macroBadge, { color: colors.onSurfaceVariant }]}>
                        {template.foodCount} alimentos
                      </Text>
                    </View>
                  </View>
                </Pressable>
              ))
            )}
          </ScrollView>

          <Button variant="secondary" onPress={onClose}>
            Cerrar
          </Button>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  modalContainer: {
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
    padding: 24,
    paddingBottom: 40,
    borderTopWidth: 1,
    maxHeight: '80%',
  },
  handle: {
    width: 48,
    height: 4,
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 24,
    lineHeight: 32,
    fontWeight: 'bold',
    marginBottom: 24,
  },
  scrollView: {
    marginBottom: 24,
  },
  emptyContainer: {
    paddingVertical: 40,
    alignItems: 'center',
  },
  emptyText: {
    fontStyle: 'italic',
    textAlign: 'center',
  },
  templateCard: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
  },
  templateTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  templateDescription: {
    fontSize: 12,
    marginBottom: 8,
  },
  macrosRow: {
    flexDirection: 'row',
    alignItems: 'center',
    columnGap: 16,
  },
  calories: {
    fontSize: 14,
    fontWeight: 'bold',
  },
  macrosBadges: {
    flexDirection: 'row',
    columnGap: 8,
  },
  macroBadge: {
    fontSize: 10,
    textTransform: 'uppercase',
    fontWeight: 'bold',
    letterSpacing: 1,
  },
});
