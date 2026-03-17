import React from 'react';
import { TouchableOpacity, StyleSheet } from 'react-native';

interface TacticalBackdropProps {
  onPress: () => void;
  variant?: 'modal' | 'overlay';
}

const TacticalBackdrop: React.FC<TacticalBackdropProps> = ({ onPress, variant = 'modal' }) => {
  return (
    <TouchableOpacity
      style={[styles.backdrop, variant === 'overlay' && styles.overlay]}
      onPress={onPress}
      activeOpacity={1}
    />
  );
};

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  overlay: {
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
  },
});

export default TacticalBackdrop;