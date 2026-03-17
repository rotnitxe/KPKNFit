import React, { useEffect, useState, useMemo } from 'react';
import { View, Text, ScrollView, Pressable, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '../../components/ScreenShell';
import { Button } from '../../components/ui';
import { useMealPlannerStore } from '../../stores/mealPlannerStore';
import { useMealTemplateStore, MealTemplateSummary } from '../../stores/mealTemplateStore';
import { MealSlotCard } from '../../components/nutrition/MealSlotCard';
import { TemplatePickerModal } from '../../components/nutrition/TemplatePickerModal';
import { NutritionStackParamList } from '../../navigation/AppNavigator';
import { MealSlot } from '../../types/mealPlanner';
import { useColors } from '../../theme';

type NavProp = NativeStackNavigationProp<NutritionStackParamList>;

export const MealPlannerScreen: React.FC = () => {
  const colors = useColors();
  const navigation = useNavigation<NavProp>();

  const {
    status: plannerStatus,
    activeWeekPlan,
    summary,
    suggestions,
    notice,
    hydrateFromStorage,
    generateSuggestionsForDay,
    setTemplateForSlot,
    clearDayPlan,
    clearNotice,
  } = useMealPlannerStore();

  const {
    status: templatesStatus,
    templates,
    hydrateFromMigration,
  } = useMealTemplateStore();

  const [pickerVisible, setPickerVisible] = useState(false);
  const [activeSlot, setActiveSlot] = useState<MealSlot | null>(null);

  const todayKey = useMemo(() => new Date().toISOString().slice(0, 10), []);
  const todayPlan = useMemo(
    () => activeWeekPlan?.days.find((d) => d.dateKey === todayKey),
    [activeWeekPlan, todayKey]
  );

  useEffect(() => {
    if (plannerStatus === 'idle') {
      void hydrateFromStorage();
    }
    if (templatesStatus === 'idle') {
      void hydrateFromMigration();
    }
  }, [plannerStatus, templatesStatus, hydrateFromStorage, hydrateFromMigration]);

  useEffect(() => {
    if (notice) {
      const timer = setTimeout(() => clearNotice(), 3000);
      return () => clearTimeout(timer);
    }
  }, [notice, clearNotice]);

  const handleOpenPicker = (slot: MealSlot) => {
    setActiveSlot(slot);
    setPickerVisible(true);
  };

  const handleSelectTemplate = (templateId: string) => {
    if (activeSlot) {
      void setTemplateForSlot(todayKey, activeSlot, templateId);
      setPickerVisible(false);
      setActiveSlot(null);
    }
  };

  const handleGenerateSuggestions = () => {
    const target = summary?.dayCaloriesTarget || 2200;
    generateSuggestionsForDay(todayKey, target);
  };

  const handleApplySuggestion = (slot: MealSlot, templateId: string) => {
    void setTemplateForSlot(todayKey, slot, templateId);
  };

  if (templatesStatus === 'empty') {
    return (
      <ScreenShell title="Meal Planner" subtitle="Organiza tus comidas diarias.">
        <View style={styles.emptyContainer}>
          <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>
            No hay plantillas
          </Text>
          <Text
            style={[styles.emptyDescription, { color: colors.onSurfaceVariant }]}
            numberOfLines={3}
          >
            Necesitas tener plantillas de comidas guardadas para usar el planificador.
          </Text>
          <Button onPress={() => navigation.navigate('NutritionLog')}>
            Ir a registrar comida
          </Button>
        </View>
      </ScreenShell>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <ScreenShell title="Meal Planner" subtitle="Organiza tus comidas con plantillas y objetivo diario.">
        <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
          {/* Daily Summary */}
          {summary && (
            <View style={[styles.summaryCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
              <View style={styles.summaryHeader}>
                <View style={styles.summaryTitleSection}>
                  <Text style={[styles.summaryTitle, { color: colors.onSurfaceVariant }]}>
                    Calorías Planificadas
                  </Text>
                  <View style={styles.summaryValueRow}>
                    <Text style={[styles.summaryValue, { color: colors.onSurface }]}>
                      {Math.round(summary.dayCaloriesPlanned)}
                    </Text>
                    <Text style={[styles.summaryTarget, { color: colors.onSurfaceVariant }]}>
                      {' '}/ {summary.dayCaloriesTarget} kcal
                    </Text>
                  </View>
                </View>
                <Text style={[styles.summaryPercentage, { color: colors.primary }]}>
                  {summary.dayCompletionPct}%
                </Text>
              </View>

              <View style={[styles.progressBarTrack, { backgroundColor: `${colors.onSurface}1A` }]}>
                <View
                  style={[
                    styles.progressBarFill,
                    { width: `${summary.dayCompletionPct}%`, backgroundColor: colors.primary },
                  ]}
                />
              </View>
              <Text style={[styles.summaryFooter, { color: colors.onSurfaceVariant }]}>
                {summary.selectedTemplateCount} de 4 slots configurados
              </Text>
            </View>
          )}

          {/* Action Buttons */}
          <View style={styles.actionRow}>
            <View style={styles.actionButton}>
              <Button onPress={handleGenerateSuggestions} variant="primary">
                Sugerir día
              </Button>
            </View>
            <View style={styles.actionButton}>
              <Button onPress={() => clearDayPlan(todayKey)} variant="secondary">
                Limpiar día
              </Button>
            </View>
          </View>

          {/* Quick Suggestions */}
          {suggestions.length > 0 && (
            <View style={styles.suggestionsSection}>
              <Text style={[styles.suggestionsTitle, { color: colors.onSurfaceVariant }]}>
                Sugerencias de AUGE
              </Text>
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                style={styles.suggestionsScroll}
                contentContainerStyle={styles.suggestionsContent}
              >
                {suggestions.map((s, idx) => (
                  <Pressable
                    key={`${s.slot}-${idx}`}
                    onPress={() => handleApplySuggestion(s.slot, s.templateId)}
                    style={[
                      styles.suggestionCard,
                      { backgroundColor: colors.primaryContainer, borderColor: colors.outlineVariant },
                    ]}
                  >
                    <Text style={[styles.suggestionSlot, { color: colors.primary }]}>
                      {s.slot}
                    </Text>
                    <Text
                      style={[styles.suggestionName, { color: colors.onSurface }]}
                      numberOfLines={1}
                    >
                      {s.templateName}
                    </Text>
                    <Text
                      style={[styles.suggestionReason, { color: colors.onSurfaceVariant }]}
                      numberOfLines={2}
                    >
                      {s.reason}
                    </Text>
                  </Pressable>
                ))}
              </ScrollView>
            </View>
          )}

          {/* Meal Slots */}
          <View style={styles.slotsSection}>
            {(['breakfast', 'lunch', 'dinner', 'snack'] as MealSlot[]).map((slotKey) => {
              const selection = todayPlan?.slots.find((s) => s.slot === slotKey);
              const template = selection?.templateId
                ? templates.find((t) => t.id === selection.templateId) || null
                : null;

              return (
                <MealSlotCard
                  key={slotKey}
                  slot={slotKey}
                  selectedTemplate={template}
                  onPressSelect={() => handleOpenPicker(slotKey)}
                  onPressClear={() => setTemplateForSlot(todayKey, slotKey, null)}
                />
              );
            })}
          </View>
        </ScrollView>

        {/* Success Notice */}
        {notice && (
          <View style={[styles.noticeCard, { backgroundColor: colors.batteryHigh }]}>
            <Text style={[styles.noticeText, { color: colors.onPrimary }]} numberOfLines={1}>
              {notice}
            </Text>
          </View>
        )}
      </ScreenShell>

      <TemplatePickerModal
        visible={pickerVisible}
        onClose={() => setPickerVisible(false)}
        templates={templates}
        onSelectTemplate={handleSelectTemplate}
        title={`Elegir para ${activeSlot ? activeSlot : ''}`}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 8,
  },
  emptyDescription: {
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
    marginBottom: 24,
  },
  scrollView: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  summaryCard: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
  },
  summaryHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginBottom: 12,
  },
  summaryTitleSection: {
    flex: 1,
  },
  summaryTitle: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  summaryValueRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  summaryValue: {
    fontSize: 28,
    fontWeight: '700',
  },
  summaryTarget: {
    fontSize: 13,
  },
  summaryPercentage: {
    fontSize: 18,
    fontWeight: '700',
  },
  progressBarTrack: {
    height: 8,
    width: '100%',
    borderRadius: 4,
    overflow: 'hidden',
    marginBottom: 8,
  },
  progressBarFill: {
    height: '100%',
    borderRadius: 4,
  },
  summaryFooter: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
    textAlign: 'right',
  },
  actionRow: {
    flexDirection: 'row',
    marginBottom: 16,
  },
  actionButton: {
    flex: 1,
  },
  suggestionsSection: {
    marginBottom: 16,
  },
  suggestionsTitle: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
    marginBottom: 12,
    paddingLeft: 4,
  },
  suggestionsScroll: {
    flexGrow: 0,
  },
  suggestionsContent: {
    paddingRight: 16,
  },
  suggestionCard: {
    borderRadius: 12,
    borderWidth: 1,
    padding: 12,
    marginRight: 12,
    width: 160,
  },
  suggestionSlot: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  suggestionName: {
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 4,
  },
  suggestionReason: {
    fontSize: 11,
    lineHeight: 14,
  },
  slotsSection: {
    paddingBottom: 24,
  },
  noticeCard: {
    position: 'absolute',
    bottom: 16,
    left: 16,
    right: 16,
    borderRadius: 16,
    padding: 16,
  },
  noticeText: {
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
  },
});
