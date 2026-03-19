import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SparklesIcon } from '@/components/icons';
import TacticalModal, { type TacticalVariant } from '@/components/ui/TacticalModal';
import { useColors } from '@/theme';

interface CoachBriefingModalProps {
  isOpen: boolean;
  onClose: () => void;
  briefing: string;
  variant?: TacticalVariant;
}

function CoachBriefingContent({ briefing, onClose }: { briefing: string; onClose: () => void }) {
  const colors = useColors();

  return (
    <View style={styles.content}>
      <View style={styles.header}>
        <SparklesIcon size={40} color={colors.primary} />
        <Text style={[styles.title, { color: colors.onSurface }]}>Informe de tu Coach IA</Text>
      </View>
      <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
        <Text style={[styles.briefing, { color: colors.onSurfaceVariant }]}>{briefing}</Text>
      </ScrollView>
      <Pressable
        onPress={onClose}
        style={[styles.button, { backgroundColor: colors.primary }]}
        accessibilityRole="button"
        accessibilityLabel="Cerrar briefing"
      >
        <Text style={[styles.buttonText, { color: colors.onPrimary }]}>Entendido, ¡vamos!</Text>
      </Pressable>
    </View>
  );
}

export function CoachBriefingModal({ isOpen, onClose, briefing, variant = 'default' }: CoachBriefingModalProps) {
  return (
    <TacticalModal
      isOpen={isOpen}
      onClose={onClose}
      variant={variant}
      useCustomContent
    >
      <CoachBriefingContent briefing={briefing} onClose={onClose} />
    </TacticalModal>
  );
}

export default CoachBriefingModal;

const styles = StyleSheet.create({
  content: {
    flex: 1,
    gap: 16,
  },
  header: {
    alignItems: 'center',
    gap: 10,
  },
  title: {
    fontSize: 20,
    fontWeight: '800',
    textAlign: 'center',
  },
  scroll: {
    maxHeight: 360,
  },
  scrollContent: {
    paddingBottom: 4,
  },
  briefing: {
    fontSize: 14,
    lineHeight: 22,
    textAlign: 'center',
  },
  button: {
    borderRadius: 999,
    paddingVertical: 14,
    alignItems: 'center',
  },
  buttonText: {
    fontSize: 14,
    fontWeight: '800',
  },
});
