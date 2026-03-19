import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import {
  View,
  ScrollView,
  TextInput,
  TouchableOpacity,
  Text,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { CoachConversationList } from '../../components/coach/CoachConversationList';
import { CoachMessageBubble } from '../../components/coach/CoachMessageBubble';
import { CoachBriefingDrawer } from '../../components/coach/CoachBriefingDrawer';
import { TacticalConfirm } from '../../components/ui/TacticalConfirm';
import { useCoachStore } from '../../stores/coachStore';
import { buildCoachContextSnapshot, generateCoachBriefing } from '../../services/aiService';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useBodyStore } from '../../stores/bodyStore';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { useColors } from '../../theme';

export function CoachChatScreen() {
  const [inputText, setInputText] = useState('');
  const [briefingVisible, setBriefingVisible] = useState(false);
  const [pendingDeleteConversationId, setPendingDeleteConversationId] = useState<string | null>(null);
  const scrollViewRef = useRef<ScrollView>(null);
  const colors = useColors();

  const {
    conversations,
    activeConversationId,
    isSending,
    createConversation,
    setActiveConversation,
    deleteConversation,
    sendMessage,
  } = useCoachStore();

  const activeConv = useMemo(
    () => conversations.find(conversation => conversation.id === activeConversationId),
    [conversations, activeConversationId],
  );

  const workoutOverview = useWorkoutStore(state => state.overview);
  const bodyProgress = useBodyStore(state => state.bodyProgress);
  const savedNutritionLogs = useMobileNutritionStore(state => state.savedLogs);
  const wellbeingOverview = useWellbeingStore(state => state.overview);

  const coachContext = useMemo(
    () =>
      buildCoachContextSnapshot(
        workoutOverview,
        bodyProgress,
        savedNutritionLogs,
        wellbeingOverview,
      ),
    [bodyProgress, savedNutritionLogs, wellbeingOverview, workoutOverview],
  );

  const coachBriefing = useMemo(() => generateCoachBriefing(coachContext), [coachContext]);
  const readinessLabel = coachContext.readiness === null ? 'Sin dato' : `${Math.round(coachContext.readiness)}/10`;
  const sessionLabel =
    coachContext.plannedSetsThisWeek > 0
      ? `${coachContext.completedSetsThisWeek}/${coachContext.plannedSetsThisWeek} series`
      : `${coachContext.completedSetsThisWeek} series`;
  const nutritionLabel = `${Math.round(coachContext.todayCalories)} kcal · ${Math.round(coachContext.todayProtein)} g proteína`;

  useEffect(() => {
    if (conversations.length === 0) {
      createConversation();
    } else if (!activeConversationId) {
      setActiveConversation(conversations[0].id);
    }
  }, [conversations.length, activeConversationId, createConversation, setActiveConversation]);

  useEffect(() => {
    if (activeConv?.messages.length) {
      const timeout = setTimeout(() => scrollViewRef.current?.scrollToEnd({ animated: true }), 100);
      return () => clearTimeout(timeout);
    }
    return undefined;
  }, [activeConv?.messages.length, isSending]);

  const handleSend = useCallback(async () => {
    if (!inputText.trim() || isSending) return;

    const textToSend = inputText.trim();
    setInputText('');
    await sendMessage({ text: textToSend, context: coachContext });
  }, [coachContext, inputText, isSending, sendMessage]);

  const handleNewConversation = useCallback(() => {
    createConversation();
  }, [createConversation]);

  const handleDeleteRequest = useCallback((conversationId: string) => {
    setPendingDeleteConversationId(conversationId);
  }, []);

  const handleConfirmDelete = useCallback(() => {
    if (pendingDeleteConversationId) {
      void deleteConversation(pendingDeleteConversationId);
    }
    setPendingDeleteConversationId(null);
  }, [deleteConversation, pendingDeleteConversationId]);

  return (
    <>
      <ScreenShell title="Coach IA" subtitle="Tu asistente de entrenamiento">
        <View style={styles.container}>
          <View style={[styles.summaryCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <View style={styles.summaryHeader}>
              <View style={styles.summaryTextBlock}>
                <Text style={[styles.sectionHeading, { color: colors.onSurfaceVariant }]}>Briefing rápido</Text>
                <Text style={[styles.summaryTitle, { color: colors.onSurface }]}>
                  {coachContext.activeProgramName ?? 'Sin programa activo'}
                </Text>
                <Text style={[styles.summarySubtitle, { color: colors.onSurfaceVariant }]}>
                  El coach lee tu sesión, tu progreso corporal y tu nutrición antes de responder.
                </Text>
              </View>
              <TouchableOpacity
                onPress={() => setBriefingVisible(true)}
                style={[styles.briefingButton, { backgroundColor: colors.primary }]}
                accessibilityRole="button"
                accessibilityLabel="Ver briefing del coach"
              >
                <Text style={[styles.briefingButtonText, { color: colors.onPrimary }]}>Ver informe</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.summaryStats}>
              <View style={[styles.summaryChip, { backgroundColor: colors.surfaceContainer }]}>
                <Text style={[styles.summaryChipLabel, { color: colors.onSurfaceVariant }]}>Readiness</Text>
                <Text style={[styles.summaryChipValue, { color: colors.onSurface }]}>{readinessLabel}</Text>
              </View>
              <View style={[styles.summaryChip, { backgroundColor: colors.surfaceContainer }]}>
                <Text style={[styles.summaryChipLabel, { color: colors.onSurfaceVariant }]}>Series</Text>
                <Text style={[styles.summaryChipValue, { color: colors.onSurface }]}>{sessionLabel}</Text>
              </View>
              <View style={[styles.summaryChip, { backgroundColor: colors.surfaceContainer }]}>
                <Text style={[styles.summaryChipLabel, { color: colors.onSurfaceVariant }]}>Nutrición</Text>
                <Text style={[styles.summaryChipValue, { color: colors.onSurface }]}>{nutritionLabel}</Text>
              </View>
            </View>
          </View>

          <View style={styles.header}>
            <Text style={[styles.sectionHeading, { color: colors.onSurfaceVariant }]}>Tus conversaciones</Text>
            <TouchableOpacity onPress={handleNewConversation}>
              <Text style={[styles.newBtn, { color: colors.primary }]}>+ Nueva</Text>
            </TouchableOpacity>
          </View>

          <CoachConversationList
            conversations={conversations}
            activeConversationId={activeConversationId}
            onSelect={setActiveConversation}
            onDelete={handleDeleteRequest}
          />

          <View
            style={[
              styles.chatPanel,
              { borderColor: `${colors.onSurface}0D`, backgroundColor: `${colors.surface}80` },
            ]}
          >
            <ScrollView ref={scrollViewRef} style={styles.scroll} showsVerticalScrollIndicator={false}>
              {activeConv?.messages.map(msg => (
                <CoachMessageBubble key={msg.id} message={msg} />
              ))}
              {isSending && (
                <View style={styles.loadingMsgContainer}>
                  <View
                    style={[
                      styles.loadingBubble,
                      { borderColor: `${colors.onSurface}1A`, backgroundColor: colors.surface },
                    ]}
                  >
                    <View style={styles.loadingContent}>
                      <ActivityIndicator size="small" color={colors.primary} />
                      <Text style={[styles.thinkingText, { color: colors.onSurfaceVariant }]}>
                        Coach pensando...
                      </Text>
                    </View>
                  </View>
                </View>
              )}
              {!activeConv?.messages.length && !isSending && (
                <View style={styles.emptyContainer}>
                  <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>¡Hola!</Text>
                  <Text style={[styles.emptySubtitle, { color: colors.onSurfaceVariant }]}>
                    Pregúntame sobre tu progreso, fatiga o próximos entrenamientos.
                  </Text>
                </View>
              )}
            </ScrollView>

            <KeyboardAvoidingView
              behavior={Platform.OS === 'ios' ? 'padding' : undefined}
              keyboardVerticalOffset={Platform.OS === 'ios' ? 100 : 0}
            >
              <View style={styles.inputArea}>
                <View
                  style={[
                    styles.inputWrapper,
                    { borderColor: `${colors.onSurface}1A`, backgroundColor: colors.surfaceContainer },
                  ]}
                >
                  <TextInput
                    value={inputText}
                    onChangeText={setInputText}
                    placeholder="Escribe aquí..."
                    placeholderTextColor={colors.onSurfaceVariant}
                    style={[styles.input, { color: colors.onSurface }]}
                    multiline
                  />
                </View>
                <TouchableOpacity
                  onPress={handleSend}
                  disabled={!inputText.trim() || isSending}
                  style={[
                    styles.sendBtn,
                    !inputText.trim() || isSending
                      ? { backgroundColor: colors.surfaceContainer, opacity: 0.5 }
                      : { backgroundColor: colors.primary },
                  ]}
                >
                  <Text
                    style={[
                      styles.sendIcon,
                      { color: !inputText.trim() || isSending ? colors.onSurfaceVariant : colors.onPrimary },
                    ]}
                  >
                    ➔
                  </Text>
                </TouchableOpacity>
              </View>
            </KeyboardAvoidingView>
          </View>
        </View>
      </ScreenShell>

      <CoachBriefingDrawer
        isOpen={briefingVisible}
        onClose={() => setBriefingVisible(false)}
        briefing={coachBriefing}
      />

      <TacticalConfirm
        isOpen={pendingDeleteConversationId !== null}
        onClose={() => setPendingDeleteConversationId(null)}
        onConfirm={handleConfirmDelete}
        title="Eliminar conversación"
        message="Se eliminará la conversación seleccionada y su historial."
        confirmLabel="Eliminar"
        variant="destructive"
      />
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    gap: 16,
  },
  summaryCard: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
    gap: 14,
  },
  summaryHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 12,
  },
  summaryTextBlock: {
    flex: 1,
    gap: 4,
  },
  sectionHeading: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 2,
    fontWeight: '600',
  },
  summaryTitle: {
    fontSize: 20,
    fontWeight: '800',
  },
  summarySubtitle: {
    fontSize: 13,
    lineHeight: 18,
  },
  briefingButton: {
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 999,
  },
  briefingButtonText: {
    fontSize: 12,
    fontWeight: '800',
  },
  summaryStats: {
    flexDirection: 'row',
    gap: 8,
    flexWrap: 'wrap',
  },
  summaryChip: {
    flexGrow: 1,
    minWidth: 100,
    borderRadius: 18,
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 2,
  },
  summaryChipLabel: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1.4,
  },
  summaryChipValue: {
    fontSize: 13,
    fontWeight: '800',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  newBtn: {
    fontSize: 12,
    fontWeight: 'bold',
  },
  chatPanel: {
    flex: 1,
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
  },
  scroll: {
    flex: 1,
  },
  loadingMsgContainer: {
    marginBottom: 16,
    flexDirection: 'row',
    justifyContent: 'flex-start',
  },
  loadingBubble: {
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  loadingContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  thinkingText: {
    fontSize: 13,
    fontStyle: 'italic',
  },
  emptyContainer: {
    marginTop: 40,
    alignItems: 'center',
    justifyContent: 'center',
    opacity: 0.6,
  },
  emptyTitle: {
    textAlign: 'center',
    fontSize: 20,
    fontWeight: 'bold',
  },
  emptySubtitle: {
    textAlign: 'center',
    fontSize: 14,
    marginTop: 8,
    paddingHorizontal: 20,
  },
  inputArea: {
    marginTop: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  inputWrapper: {
    flex: 1,
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
  },
  input: {
    height: 48,
    fontSize: 15,
  },
  sendBtn: {
    height: 48,
    width: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 24,
  },
  sendIcon: {
    fontSize: 20,
  },
});

