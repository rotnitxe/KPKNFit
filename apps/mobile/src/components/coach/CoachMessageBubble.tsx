import React, { memo } from 'react';
import { Text, View, StyleSheet } from 'react-native';
import type { CoachChatMessage } from '../../types/coach';
import { useColors } from '../../theme';

interface CoachMessageBubbleProps {
  message: CoachChatMessage;
}

export const CoachMessageBubble = memo(({ message }: CoachMessageBubbleProps) => {
  const colors = useColors();
  const isUser = message.role === 'user';

  const time = new Date(message.createdAt).toLocaleTimeString('es-CL', {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <View style={[styles.container, isUser ? styles.userContainer : styles.coachContainer]}>
      <View
        style={[
          styles.bubble,
          isUser
            ? { borderColor: `${colors.primary}33`, backgroundColor: `${colors.primary}1A` }
            : { borderColor: `${colors.onSurface}1A`, backgroundColor: colors.surface }
        ]}
      >
        <Text style={[styles.messageText, { color: colors.onSurface }]}>{message.text}</Text>
        <Text style={[styles.timeText, { color: colors.onSurfaceVariant }]}>{time}</Text>
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  container: {
    marginBottom: 16,
    flexDirection: 'row',
    width: '100%',
  },
  userContainer: {
    justifyContent: 'flex-end',
  },
  coachContainer: {
    justifyContent: 'flex-start',
  },
  bubble: {
    maxWidth: '85%',
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  messageText: {
    fontSize: 15,
    lineHeight: 22,
  },
  timeText: {
    marginTop: 4,
    textAlign: 'right',
    fontSize: 10,
  },
});
