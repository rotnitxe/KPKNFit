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
import { useCoachStore } from '../../stores/coachStore';
import { buildCoachContextSnapshot } from '../../services/coachChatService';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useBodyStore } from '../../stores/bodyStore';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { useColors } from '../../theme';

export function CoachChatScreen() {
  const [inputText, setInputText] = useState('');
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

  const activeConv = useMemo(() => 
    conversations.find((c) => c.id === activeConversationId),
    [conversations, activeConversationId]
  );

  const workoutOverview = useWorkoutStore(state => state.overview);
  const bodyProgress = useBodyStore(state => state.bodyProgress);
  const savedNutritionLogs = useMobileNutritionStore(state => state.savedLogs);
  const wellbeingOverview = useWellbeingStore(state => state.overview);

  useEffect(() => {
    if (conversations.length === 0) {
      createConversation();
    } else if (!activeConversationId) {
      setActiveConversation(conversations[0].id);
    }
  }, [conversations.length, activeConversationId, createConversation, setActiveConversation]);

  useEffect(() => {
    if (activeConv?.messages.length) {
      setTimeout(() => scrollViewRef.current?.scrollToEnd({ animated: true }), 100);
    }
  }, [activeConv?.messages.length, isSending]);

  const handleSend = useCallback(async () => {
    if (!inputText.trim() || isSending) return;

    const context = buildCoachContextSnapshot(
      workoutOverview,
      bodyProgress,
      savedNutritionLogs,
      wellbeingOverview
    );

    const textToSend = inputText.trim();
    setInputText('');
    await sendMessage({ text: textToSend, context });
  }, [inputText, isSending, workoutOverview, bodyProgress, savedNutritionLogs, wellbeingOverview, sendMessage]);

  const handleNewConversation = useCallback(() => {
    createConversation();
  }, [createConversation]);

  return (
    <ScreenShell title="Coach IA" subtitle="Tu asistente de entrenamiento">
      <View style={styles.container}>
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
          onDelete={deleteConversation}
        />

        <View style={[styles.chatPanel, { borderColor: `${colors.onSurface}0D`, backgroundColor: `${colors.surface}80` }]}>
          <ScrollView
            ref={scrollViewRef}
            style={styles.scroll}
            showsVerticalScrollIndicator={false}
          >
            {activeConv?.messages.map((msg) => (
              <CoachMessageBubble key={msg.id} message={msg} />
            ))}
            {isSending && (
              <View style={styles.loadingMsgContainer}>
                <View style={[styles.loadingBubble, { borderColor: `${colors.onSurface}1A`, backgroundColor: colors.surface }]}>
                  <View style={styles.loadingContent}>
                    <ActivityIndicator size="small" color={colors.primary} />
                    <Text style={[styles.thinkingText, { color: colors.onSurfaceVariant }]}>Coach pensando...</Text>
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
              <View style={[styles.inputWrapper, { borderColor: `${colors.onSurface}1A`, backgroundColor: colors.surfaceContainer }]}>
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
                    : { backgroundColor: colors.primary }
                ]}
              >
                <Text style={[styles.sendIcon, { color: !inputText.trim() || isSending ? colors.onSurfaceVariant : colors.onPrimary }]}>➔</Text>
              </TouchableOpacity>
            </View>
          </KeyboardAvoidingView>
        </View>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  sectionHeading: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 2,
    fontWeight: '600',
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
