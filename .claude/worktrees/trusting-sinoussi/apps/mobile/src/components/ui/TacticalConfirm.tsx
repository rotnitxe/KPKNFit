import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import TacticalModal from './TacticalModal';
import { AlertTriangleIcon } from '../icons';
import { useColors } from '../../theme';

export type ConfirmVariant = 'default' | 'destructive' | 'pr';

interface TacticalConfirmProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title?: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: ConfirmVariant;
}

export const TacticalConfirm: React.FC<TacticalConfirmProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  variant = 'default'
}) => {
  const colors = useColors();
  const modalVariant = variant === 'destructive' ? 'failure' : variant === 'pr' ? 'pr' : 'default';

  const handleConfirm = () => {
    onConfirm();
    onClose();
  };

  const getConfirmButtonStyle = () => {
    switch (variant) {
      case 'destructive':
        return { backgroundColor: colors.error, borderColor: colors.error };
      case 'pr':
        return { backgroundColor: colors.cyberWarning, borderColor: colors.cyberWarning };
      default:
        return { backgroundColor: colors.primary, borderColor: colors.primary };
    }
  };

  const getConfirmTextColor = () => {
    switch (variant) {
      case 'destructive':
        return colors.onError;
      case 'pr':
        return colors.onSurface;
      default:
        return colors.onPrimary;
    }
  };

  return (
    <TacticalModal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      variant={modalVariant}
    >
      <View style={styles.content}>
        {variant === 'destructive' && (
          <AlertTriangleIcon size={32} color={colors.error} />
        )}
        <Text style={[styles.message, { color: colors.onSurfaceVariant }]}>{message}</Text>
        <View style={styles.buttonRow}>
          <Pressable
            style={[styles.button, styles.cancelButton, { borderColor: colors.outlineVariant }]}
            onPress={onClose}
          >
            <Text style={[styles.cancelText, { color: colors.onSurfaceVariant }]}>{cancelLabel}</Text>
          </Pressable>
          <Pressable
            style={[styles.button, styles.confirmButton, getConfirmButtonStyle()]}
            onPress={handleConfirm}
          >
            <Text style={[styles.confirmText, { color: getConfirmTextColor() }]}>
              {confirmLabel}
            </Text>
          </Pressable>
        </View>
      </View>
    </TacticalModal>
  );
};

const styles = StyleSheet.create({
  content: {
    gap: 20,
  },
  message: {
    fontSize: 14,
    lineHeight: 22,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 8,
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
  confirmButton: {
    // Dynamic styles applied
  },
  cancelText: {
    fontWeight: '600',
    fontSize: 14,
  },
  confirmText: {
    fontWeight: '600',
    fontSize: 14,
  },
});

export default TacticalConfirm;