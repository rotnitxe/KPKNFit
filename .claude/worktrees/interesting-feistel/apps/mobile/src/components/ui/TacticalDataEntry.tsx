import React from 'react';
import { View, ScrollView, Pressable, Text, StyleSheet } from 'react-native';
import TacticalModal from './TacticalModal';

import { useColors } from '../../theme';

interface TacticalDataEntryProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: () => void;
  title: string;
  submitLabel?: string;
  children: React.ReactNode;
}

export const TacticalDataEntry: React.FC<TacticalDataEntryProps> = ({
  isOpen,
  onClose,
  onSubmit,
  title,
  submitLabel = 'Guardar',
  children
}) => {
  const colors = useColors();

  return (
    <TacticalModal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      variant="default"
      useCustomContent={true}
    >
      <View style={[styles.container, { backgroundColor: colors.surface }]}>
        <ScrollView style={styles.scrollArea} contentContainerStyle={styles.scrollContent}>
          {children}
        </ScrollView>
        <View style={[styles.footer, { borderTopColor: colors.outlineVariant }]}>
          <Pressable
            style={[styles.button, styles.cancelButton, { borderColor: colors.outlineVariant }]}
            onPress={onClose}
          >
            <Text style={[styles.cancelText, { color: colors.onSurfaceVariant }]}>Cancelar</Text>
          </Pressable>
          <Pressable
            style={[styles.button, styles.submitButton, { backgroundColor: colors.primary, borderColor: colors.primary }]}
            onPress={onSubmit}
          >
            <Text style={[styles.submitText, { color: colors.onPrimary }]}>{submitLabel}</Text>
          </Pressable>
        </View>
      </View>
    </TacticalModal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingVertical: 16,
  },
  footer: {
    flexShrink: 0,
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderTopWidth: 1,
    flexDirection: 'row',
    gap: 12,
  },
  button: {
    flex: 1,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 8,
  },
  cancelButton: {
    backgroundColor: 'transparent',
  },
  submitButton: {
    // Dynamic styles applied
  },
  cancelText: {
    fontWeight: '600',
    fontSize: 14,
  },
  submitText: {
    fontWeight: '600',
    fontSize: 14,
  },
});

export default TacticalDataEntry;