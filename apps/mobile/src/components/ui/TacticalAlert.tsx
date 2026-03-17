import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import TacticalModal from './TacticalModal';
import { AlertTriangleIcon } from '../icons';

export type AlertVariant = 'failure' | 'pr' | 'default';

interface TacticalAlertProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  message: string;
  variant?: AlertVariant;
}

export const TacticalAlert: React.FC<TacticalAlertProps> = ({
  isOpen,
  onClose,
  title,
  message,
  variant = 'default'
}) => {
  const modalVariant = variant === 'failure' ? 'failure' : variant === 'pr' ? 'pr' : 'default';

  return (
    <TacticalModal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      variant={modalVariant}
    >
      <View style={styles.content}>
        {variant === 'failure' && (
          <AlertTriangleIcon size={24} color="#FF2E43" />
        )}
        <Text style={styles.message}>{message}</Text>
      </View>
    </TacticalModal>
  );
};

const styles = StyleSheet.create({
  content: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 16,
  },
  message: {
    color: '#A0A7B8',
    fontSize: 14,
    fontFamily: 'monospace',
    lineHeight: 22,
    flex: 1,
  },
});

export default TacticalAlert;