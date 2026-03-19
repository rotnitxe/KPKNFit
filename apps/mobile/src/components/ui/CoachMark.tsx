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
import { LightbulbIcon, XIcon } from '../icons';
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
          <TouchableOpacity
            onPress={handleDismiss}
            style={styles.closeButton}
            hitSlop={8}
          >
            <XIcon size={16} color={colors.onPrimaryContainer} />
          </TouchableOpacity>
          <View style={styles.row}>
            <View style={[styles.iconWrap, { backgroundColor: 'rgba(255,255,255,0.15)' }]}>
              <LightbulbIcon size={22} color={colors.onPrimaryContainer} />
            </View>
            <View style={styles.textContent}>
              <Text style={[styles.title, { color: colors.onPrimaryContainer }]}>{title}</Text>
              <Text style={[styles.message, { color: colors.onPrimaryContainer }]}>{message}</Text>
            </View>
          </View>
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
    borderRadius: 24,
    padding: 20,
    elevation: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
  },
  closeButton: {
    position: 'absolute',
    top: 12,
    right: 12,
    padding: 4,
    zIndex: 10,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 16,
  },
  iconWrap: {
    padding: 10,
    borderRadius: 16,
    flexShrink: 0,
  },
  textContent: {
    flex: 1,
  },
  title: {
    fontSize: 18,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: -0.3,
    marginBottom: 4,
  },
  message: {
    fontSize: 14,
    lineHeight: 20,
  },
  footer: {
    alignItems: 'flex-end',
    marginTop: 20,
  },
  button: {
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    paddingHorizontal: 24,
    minWidth: 120,
  },
  buttonText: {
    fontWeight: '700',
    fontSize: 10,
    textTransform: 'uppercase',
  },
});

export default CoachMark;
