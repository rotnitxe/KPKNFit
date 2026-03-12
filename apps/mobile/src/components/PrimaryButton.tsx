import React from 'react';
import { Pressable, Text } from 'react-native';

interface PrimaryButtonProps {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  tone?: 'primary' | 'secondary';
  testID?: string;
}

export function PrimaryButton({ label, onPress, disabled = false, tone = 'primary', testID }: PrimaryButtonProps) {
  const className = tone === 'primary'
    ? 'bg-kpkn-brand'
    : 'bg-white/5 border border-white/10';

  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      className={`h-12 items-center justify-center rounded-full px-5 ${className} ${disabled ? 'opacity-50' : ''}`}
      onPress={onPress}
      disabled={disabled}
    >
      <Text className={`text-base font-semibold ${tone === 'primary' ? 'text-kpkn-bg' : 'text-kpkn-text'}`}>
        {label}
      </Text>
    </Pressable>
  );
}
