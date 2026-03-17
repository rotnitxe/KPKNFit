import React, { useState } from 'react';
import {
  TouchableOpacity,
  Modal,
  View,
  Text,
  StyleSheet,
  Pressable,
} from 'react-native';
import { InfoIcon, XIcon } from '../icons';
import { useColors } from '../../theme';

interface InfoTooltipProps {
  term: string;
  description: string;
  children?: React.ReactNode;
}

export function InfoTooltip({ term, description, children }: InfoTooltipProps) {
  const [visible, setVisible] = useState(false);
  const colors = useColors();

  return (
    <>
      <TouchableOpacity
        onPress={() => setVisible(true)}
        style={styles.trigger}
        hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
      >
        {children || <InfoIcon size={18} color={colors.primary} />}
      </TouchableOpacity>

      <Modal
        visible={visible}
        transparent
        animationType="fade"
        onRequestClose={() => setVisible(false)}
      >
        <Pressable
          style={styles.backdrop}
          onPress={() => setVisible(false)}
        >
          <View
            style={[
              styles.modalContainer,
              { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant },
            ]}
            onStartShouldSetResponder={() => true}
            onTouchEnd={(e) => e.stopPropagation()}
          >
            <View style={styles.header}>
              <Text style={[styles.title, { color: colors.onSurface }]}>{term}</Text>
              <TouchableOpacity onPress={() => setVisible(false)}>
                <XIcon size={20} color={colors.onSurfaceVariant} />
              </TouchableOpacity>
            </View>
            <Text style={[styles.description, { color: colors.onSurfaceVariant }]}>
              {description}
            </Text>
          </View>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  trigger: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  modalContainer: {
    width: '100%',
    maxWidth: 400,
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
  },
  description: {
    fontSize: 14,
    lineHeight: 20,
  },
});

export default InfoTooltip;
