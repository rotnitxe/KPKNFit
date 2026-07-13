import React, { useEffect, useMemo, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import type { Session } from '../../types/workout';
import { useColors } from '../../theme';
import { getSessionExercises, getSessionSetCount } from '../../utils/workoutSession';

interface SessionVariantOption {
  key: 'A' | 'B' | 'C' | 'D';
  session: Session;
}

interface StartWorkoutModalProps {
  visible: boolean;
  session: Session | null;
  onClose: () => void;
  onStart: (variant: SessionVariantOption) => void;
}

export function StartWorkoutModal({ visible, session, onClose, onStart }: StartWorkoutModalProps) {
  const colors = useColors();
  const [selected, setSelected] = useState<'A' | 'B' | 'C' | 'D'>('A');

  const variants = useMemo<SessionVariantOption[]>(() => {
    if (!session) return [];
    const values: SessionVariantOption[] = [{ key: 'A', session }];
    if (session.sessionB) values.push({ key: 'B', session: session.sessionB });
    if (session.sessionC) values.push({ key: 'C', session: session.sessionC });
    if (session.sessionD) values.push({ key: 'D', session: session.sessionD });
    return values;
  }, [session]);

  useEffect(() => {
    setSelected('A');
  }, [visible, session?.id]);

  const selectedVariant = variants.find(variant => variant.key === selected) ?? variants[0];

  if (!visible || !session) return null;

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={[styles.sheet, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}22` }]}>
          <Text style={[styles.title, { color: colors.onSurface }]}>Iniciar sesión</Text>
          <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
            Elige variante para "{session.name}" y empieza el registro.
          </Text>

          <View style={styles.variantsRow}>
            {variants.map(variant => {
              const isSelected = selectedVariant?.key === variant.key;
              return (
                <Pressable
                  key={variant.key}
                  onPress={() => setSelected(variant.key)}
                  style={[
                    styles.variantChip,
                    {
                      backgroundColor: isSelected ? colors.primary : `${colors.onSurface}08`,
                      borderColor: isSelected ? colors.primary : `${colors.onSurface}22`,
                    },
                  ]}
                >
                  <Text style={[styles.variantText, { color: isSelected ? colors.onPrimary : colors.onSurface }]}>
                    Sesión {variant.key}
                  </Text>
                </Pressable>
              );
            })}
          </View>

          <View style={[styles.summaryCard, { backgroundColor: colors.surfaceContainer }]}>
            <Text style={[styles.summaryTitle, { color: colors.onSurface }]}>
              {selectedVariant?.session.name ?? session.name}
            </Text>
            <Text style={[styles.summaryMeta, { color: colors.onSurfaceVariant }]}>
              {getSessionExercises(selectedVariant?.session ?? session).length} ejercicios · {getSessionSetCount(selectedVariant?.session ?? session)} sets
            </Text>
          </View>

          <View style={styles.actionsRow}>
            <Pressable
              onPress={onClose}
              style={[styles.secondaryButton, { borderColor: `${colors.onSurface}33` }]}
            >
              <Text style={[styles.secondaryText, { color: colors.onSurface }]}>Cancelar</Text>
            </Pressable>
            <Pressable
              onPress={() => selectedVariant && onStart(selectedVariant)}
              style={[styles.primaryButton, { backgroundColor: colors.primary }]}
            >
              <Text style={[styles.primaryText, { color: colors.onPrimary }]}>Iniciar</Text>
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
    backgroundColor: 'rgba(0,0,0,0.45)',
    justifyContent: 'flex-end',
  },
  sheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    borderWidth: 1,
    padding: 18,
    gap: 12,
  },
  title: {
    fontSize: 20,
    fontWeight: '900',
  },
  subtitle: {
    fontSize: 13,
    lineHeight: 18,
  },
  variantsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  variantChip: {
    minHeight: 40,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  variantText: {
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.2,
  },
  summaryCard: {
    borderRadius: 14,
    padding: 12,
    gap: 4,
  },
  summaryTitle: {
    fontSize: 16,
    fontWeight: '800',
  },
  summaryMeta: {
    fontSize: 12,
  },
  actionsRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 6,
  },
  secondaryButton: {
    flex: 1,
    minHeight: 46,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryText: {
    fontSize: 13,
    fontWeight: '700',
  },
  primaryButton: {
    flex: 1,
    minHeight: 46,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryText: {
    fontSize: 13,
    fontWeight: '800',
  },
});
