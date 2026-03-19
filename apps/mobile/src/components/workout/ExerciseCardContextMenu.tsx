import React from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { MinusIcon, SwapIcon } from '../icons';
import { useColors } from '../../theme';

interface ExerciseCardContextMenuProps {
  visible: boolean;
  onClose: () => void;
  onReplace: () => void;
  onSkip: () => void;
}

export function ExerciseCardContextMenu({
  visible,
  onClose,
  onReplace,
  onSkip,
}: ExerciseCardContextMenuProps) {
  const colors = useColors();

  if (!visible) return null;

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
        <View style={[styles.menu, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}24` }]}>
          <Pressable
            onPress={() => {
              onReplace();
              onClose();
            }}
            style={styles.item}
          >
            <SwapIcon size={16} color={colors.primary} />
            <Text style={[styles.itemText, { color: colors.onSurface }]}>Cambiar ejercicio</Text>
          </Pressable>
          <Pressable
            onPress={() => {
              onSkip();
              onClose();
            }}
            style={[styles.item, { borderTopColor: `${colors.onSurface}1F`, borderTopWidth: 1 }]}
          >
            <MinusIcon size={16} color={colors.onSurfaceVariant} />
            <Text style={[styles.itemText, { color: colors.onSurface }]}>Omitir bloque</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.3)',
    justifyContent: 'flex-end',
    alignItems: 'center',
    paddingBottom: 96,
  },
  menu: {
    width: 240,
    borderRadius: 16,
    borderWidth: 1,
    overflow: 'hidden',
  },
  item: {
    minHeight: 46,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  itemText: {
    fontSize: 14,
    fontWeight: '600',
  },
});
