import React, { useEffect, useState } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import Animated, { FadeInDown, FadeOutRight } from 'react-native-reanimated';
import { useColors } from '../../theme';
import { CheckCircleIcon, TargetIcon, InfoIcon, AlertTriangleIcon, XIcon } from '../icons';

interface ToastData {
  id: number;
  message: string;
  type: 'success' | 'achievement' | 'suggestion' | 'danger';
  title?: string;
  duration?: number;
  why?: string;
}

interface ToastProps {
  toast: ToastData;
  onDismiss: (id: number) => void;
}

const getIcon = (type: ToastData['type'], colors: any) => {
  const size = 18;
  switch (type) {
    case 'success':
      return <CheckCircleIcon size={size} color={colors.primary} />;
    case 'achievement':
      return <TargetIcon size={size} color={colors.cyberWarning} />;
    case 'suggestion':
      return <InfoIcon size={size} color={colors.secondary} />;
    case 'danger':
      return <AlertTriangleIcon size={size} color={colors.error} />;
  }
};

const getThemeColors = (type: ToastData['type'], colors: any) => {
  switch (type) {
    case 'success':
      return {
        accent: colors.primary,
        border: `${colors.primary}80`,
      };
    case 'achievement':
      return {
        accent: colors.cyberWarning,
        border: `${colors.cyberWarning}80`,
      };
    case 'suggestion':
      return {
        accent: colors.secondary,
        border: `${colors.secondary}80`,
      };
    case 'danger':
      return {
        accent: colors.error,
        border: `${colors.error}80`,
      };
  }
};

export const Toast: React.FC<ToastProps> = ({ toast, onDismiss }) => {
  const [showWhy, setShowWhy] = useState(false);
  const colors = useColors();
  const theme = getThemeColors(toast.type, colors);

  const handleDismiss = () => {
    onDismiss(toast.id);
  };

  useEffect(() => {
    const timer = setTimeout(handleDismiss, toast.duration || 4000);
    return () => clearTimeout(timer);
  }, [toast, handleDismiss]);

  return (
    <Animated.View
      style={[
        styles.container, 
        { 
          backgroundColor: colors.surfaceContainerHigh, 
          borderColor: theme.border 
        }
      ]}
      entering={FadeInDown.duration(500).springify()}
      exiting={FadeOutRight.duration(400)}
    >
      <View style={[styles.accentBar, { backgroundColor: theme.accent }]} />
      <Pressable style={styles.inner} onPress={handleDismiss}>
        <View style={styles.iconContainer}>
          {getIcon(toast.type, colors)}
        </View>
        <View style={styles.content}>
          {toast.title && (
            <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>{toast.title}</Text>
          )}
          <Text style={[styles.message, { color: colors.onSurface }]}>{toast.message}</Text>
          {toast.type === 'danger' && toast.why && (
            <Pressable onPress={() => setShowWhy(!showWhy)} style={styles.moreButton}>
              <Text style={[styles.moreText, { color: colors.error }]}>Ver más</Text>
            </Pressable>
          )}
          {showWhy && toast.why && (
            <View style={[styles.whyContainer, { backgroundColor: colors.surface, borderColor: `${colors.error}33` }]}>
              <Text style={[styles.whyText, { color: colors.onSurfaceVariant }]}>{toast.why}</Text>
            </View>
          )}
        </View>
        <Pressable onPress={handleDismiss} style={styles.closeButton}>
          <XIcon size={14} color={colors.onSurfaceVariant} />
        </Pressable>
      </Pressable>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    borderRadius: 12,
    borderWidth: 1,
    minWidth: 280,
    maxWidth: '90%',
    marginBottom: 8,
    position: 'relative',
    overflow: 'hidden',
  },
  accentBar: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    width: 4,
  },
  inner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    paddingLeft: 20,
  },
  iconContainer: {
    marginTop: 2,
  },
  content: {
    flex: 1,
  },
  title: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 4,
    opacity: 0.7,
  },
  message: {
    fontSize: 13,
    fontWeight: '500',
    lineHeight: 18,
  },
  moreButton: {
    marginTop: 8,
  },
  moreText: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  whyContainer: {
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginTop: 8,
  },
  whyText: {
    fontSize: 12,
    lineHeight: 18,
  },
  closeButton: {
    padding: 4,
  },
});

export type { ToastData };
export default Toast;