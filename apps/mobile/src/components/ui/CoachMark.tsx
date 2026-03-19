import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
} from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
  withDelay,
} from 'react-native-reanimated';
import { useColors } from '../../theme';
import Button from './Button';

interface CoachMarkProps {
  title: string;
  message: string;
  onDismiss?: () => void;
  delay?: number;
}

export function CoachMark({
  title,
  message,
  onDismiss,
  delay = 500,
}: CoachMarkProps) {
  const [visible, setVisible] = useState(false);
  const colors = useColors();
  const opacity = useSharedValue(0);
  const translateY = useSharedValue(20);

  useEffect(() => {
    const timer = setTimeout(() => {
      setVisible(true);
      opacity.value = withSpring(1);
      translateY.value = withSpring(0);
    }, delay);

    return () => clearTimeout(timer);
  }, [delay, opacity, translateY]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ translateY: translateY.value }],
  }));

  const handleDismiss = () => {
    opacity.value = withTiming(0);
    translateY.value = withTiming(20);
    setTimeout(() => {
      setVisible(false);
      onDismiss?.();
    }, 300);
  };

  if (!visible) return null;

  return (
    <Modal visible={visible} transparent animationType="none">
      <View style={styles.overlay}>
        <Animated.View
          style={[
            styles.container,
            { backgroundColor: colors.primaryContainer },
            animatedStyle,
          ]}
        >
          <Text style={[styles.title, { color: colors.onPrimaryContainer }]}>{title}</Text>
          <Text style={[styles.message, { color: colors.onPrimaryContainer }]}>{message}</Text>
          <View style={styles.footer}>
            <Button
              variant="secondary"
              onPress={handleDismiss}
              style={styles.button}
            >
              <Text style={[styles.buttonText, { color: colors.onPrimaryContainer }]}>Entendido</Text>
            </Button>
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  container: {
    width: '100%',
    maxWidth: 400,
    borderRadius: 16,
    padding: 24,
    elevation: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
  },
  title: {
    fontSize: 20,
    fontWeight: '800',
    marginBottom: 8,
  },
  message: {
    fontSize: 16,
    lineHeight: 24,
    marginBottom: 24,
  },
  footer: {
    alignItems: 'flex-end',
  },
  button: {
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    paddingHorizontal: 20,
    minWidth: 100,
  },
  buttonText: {
    fontWeight: '700',
  },
});

export default CoachMark;
