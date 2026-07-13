import React from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { XIcon } from '../icons';
import { useColors } from '../../theme';

interface WorkoutDrawerProps {
  visible: boolean;
  title: string;
  onClose: () => void;
  children: React.ReactNode;
  maxHeightPercent?: number;
}

export function WorkoutDrawer({
  visible,
  title,
  onClose,
  children,
  maxHeightPercent = 0.88,
}: WorkoutDrawerProps) {
  const colors = useColors();
  const insets = useSafeAreaInsets();

  if (!visible) return null;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable style={styles.backdrop} onPress={onClose} />
        <View
          style={[
            styles.sheet,
            {
              maxHeight: `${Math.round(maxHeightPercent * 100)}%`,
              backgroundColor: colors.surface,
              borderColor: `${colors.onSurface}22`,
              paddingBottom: Math.max(14, insets.bottom + 8),
            },
          ]}
        >
          <View style={[styles.header, { borderBottomColor: `${colors.onSurface}1A` }]}>
            <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>{title}</Text>
            <Pressable onPress={onClose} style={[styles.closeButton, { borderColor: `${colors.onSurface}20` }]}>
              <XIcon size={16} color={colors.onSurfaceVariant} />
            </Pressable>
          </View>
          <ScrollView
            style={styles.scroll}
            contentContainerStyle={styles.content}
            showsVerticalScrollIndicator={false}
          >
            {children}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  sheet: {
    borderTopLeftRadius: 26,
    borderTopRightRadius: 26,
    borderWidth: 1,
    overflow: 'hidden',
  },
  header: {
    minHeight: 56,
    borderBottomWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
  },
  title: {
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.6,
  },
  closeButton: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  scroll: {
    flexGrow: 0,
  },
  content: {
    padding: 14,
    gap: 10,
  },
});
