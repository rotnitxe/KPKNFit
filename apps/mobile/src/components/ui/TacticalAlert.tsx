import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import TacticalModal from './TacticalModal';
import { AlertTriangleIcon } from '../icons';
import { useColors } from '../../theme';

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
  const colors = useColors();
  const modalVariant = variant === 'failure' ? 'failure' : variant === 'pr' ? 'pr' : 'default';
  const iconColor = variant === 'failure' ? colors.error : colors.cyberWarning;

  return (
    <TacticalModal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      variant={modalVariant}
    >
      <View style={styles.content}>
        {variant === 'failure' && (
          <AlertTriangleIcon size={24} color={iconColor} />
        )}
        <Text style={[styles.message, { color: colors.onSurfaceVariant }]}>{message}</Text>
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
    fontSize: 14,
    lineHeight: 22,
    flex: 1,
  },
});

export default TacticalAlert;
