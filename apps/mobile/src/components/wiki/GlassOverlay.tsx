import React from 'react';
import { View, StyleSheet, Platform } from 'react-native';
import { BlurView } from '@react-native-community/blur';
import { useColors } from '@/theme';

interface GlassOverlayProps {
  children?: React.ReactNode;
  intensity?: number;
  style?: any;
  tint?: 'light' | 'dark' | 'default';
}

export const GlassOverlay: React.FC<GlassOverlayProps> = ({
  children,
  intensity = 0.7,
  style,
  tint = 'dark',
}) => {
  const colors = useColors();

  if (Platform.OS === 'ios') {
    return (
      <BlurView
        style={[styles.blurView, style]}
        blurType={tint === 'light' ? 'light' : 'dark'}
        blurAmount={10}
        reducedTransparencyFallbackColor={colors.surface}
      >
        {children}
      </BlurView>
    );
  }

  return (
    <View
      style={[
        styles.androidGlass,
        {
          backgroundColor: `rgba(${colors.surface === '#000' ? '30,30,40' : '255,255,255'}, ${0.85 * intensity})`,
          backdropFilter: 'blur(20px)',
        },
        style,
      ]}
    >
      {children}
    </View>
  );
};

interface GlassCardProps {
  children: React.ReactNode;
  style?: any;
  accent?: 'sky' | 'purple' | 'emerald' | 'amber';
}

export const GlassCard: React.FC<GlassCardProps> = ({ children, style, accent }) => {
  const colors = useColors();

  const accentBorderColors = {
    sky: 'rgba(14, 165, 233, 0.3)',
    purple: 'rgba(168, 85, 247, 0.3)',
    emerald: 'rgba(16, 185, 129, 0.3)',
    amber: 'rgba(251, 191, 36, 0.3)',
  };

  return (
    <View
      style={[
        styles.glassCard,
        {
          backgroundColor: colors.surfaceContainer + 'C0',
          borderColor: accent ? accentBorderColors[accent] : colors.outlineVariant,
        },
        style,
      ]}
    >
      {children}
    </View>
  );
};

const styles = StyleSheet.create({
  blurView: {
    borderRadius: 24,
    overflow: 'hidden',
  },
  androidGlass: {
    borderRadius: 24,
    overflow: 'hidden',
  },
  glassCard: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
    overflow: 'hidden',
  },
});
