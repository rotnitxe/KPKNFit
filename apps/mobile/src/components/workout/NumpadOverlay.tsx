import React, { useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';
import { useColors } from '../../theme';

export interface NumpadOverlayProps {
  visible: boolean;
  value: string;
  mode: 'decimal' | 'integer';
  label: string;
  showNextButton?: boolean;
  onChange: (value: string) => void;
  onClose: () => void;
  onNext?: () => void;
}

function sanitizeValue(rawValue: string, mode: 'decimal' | 'integer') {
  let value = rawValue.replace(',', '.');
  if (mode === 'integer') {
    return value.replace(/[^0-9]/g, '');
  }
  value = value.replace(/[^0-9.]/g, '');
  const parts = value.split('.');
  if (parts.length > 2) {
    return `${parts[0]}.${parts.slice(1).join('')}`;
  }
  return value;
}

export function NumpadOverlay({
  visible,
  value,
  mode,
  label,
  showNextButton = true,
  onChange,
  onClose,
  onNext,
}: NumpadOverlayProps) {
  const colors = useColors();
  const [localValue, setLocalValue] = useState(value);

  useEffect(() => {
    if (visible) {
      setLocalValue(value);
    }
  }, [value, visible]);

  if (!visible) return null;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
        <View style={[styles.sheet, { backgroundColor: colors.surfaceContainerHigh, borderColor: `${colors.onSurface}24` }]}>
          <View style={styles.header}>
            <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>{label}</Text>
            <Pressable
              onPress={onClose}
              style={[styles.closeBtn, { borderColor: `${colors.onSurface}25`, backgroundColor: colors.surface }]}
            >
              <Text style={[styles.closeBtnText, { color: colors.onSurfaceVariant }]}>×</Text>
            </Pressable>
          </View>

          <TextInput
            autoFocus
            value={localValue}
            onChangeText={text => {
              const sanitized = sanitizeValue(text, mode);
              setLocalValue(sanitized);
              onChange(sanitized);
            }}
            keyboardType={mode === 'decimal' ? 'decimal-pad' : 'number-pad'}
            style={[styles.input, { color: colors.onSurface, backgroundColor: colors.surface, borderColor: `${colors.onSurface}1F` }]}
            placeholder="0"
            placeholderTextColor={`${colors.onSurfaceVariant}99`}
          />

          <View style={styles.actions}>
            {showNextButton && onNext ? (
              <Pressable
                onPress={() => {
                  ReactNativeHapticFeedback.trigger('selection');
                  onNext();
                }}
                style={[styles.secondaryBtn, { borderColor: `${colors.onSurface}2F`, backgroundColor: colors.surface }]}
              >
                <Text style={[styles.secondaryBtnText, { color: colors.onSurfaceVariant }]}>Siguiente</Text>
              </Pressable>
            ) : null}
            <Pressable
              onPress={() => {
                ReactNativeHapticFeedback.trigger('impactMedium');
                onClose();
              }}
              style={[styles.primaryBtn, { backgroundColor: colors.primary }]}
            >
              <Text style={[styles.primaryBtnText, { color: colors.onPrimary }]}>Confirmar</Text>
            </Pressable>
          </View>
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
  sheet: {
    borderTopLeftRadius: 26,
    borderTopRightRadius: 26,
    borderWidth: 1,
    padding: 14,
    gap: 12,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  label: {
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.4,
  },
  closeBtn: {
    width: 30,
    height: 30,
    borderRadius: 15,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeBtnText: {
    fontSize: 18,
    lineHeight: 18,
    fontWeight: '700',
  },
  input: {
    minHeight: 72,
    borderRadius: 18,
    borderWidth: 1,
    textAlign: 'center',
    fontSize: 36,
    fontWeight: '800',
    letterSpacing: -1,
  },
  actions: {
    flexDirection: 'row',
    gap: 10,
  },
  secondaryBtn: {
    flex: 1,
    minHeight: 46,
    borderRadius: 14,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryBtnText: {
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  primaryBtn: {
    flex: 1.2,
    minHeight: 46,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryBtnText: {
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.2,
  },
});
