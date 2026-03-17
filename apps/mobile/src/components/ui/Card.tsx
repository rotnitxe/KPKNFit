import React from 'react';
import { View, Pressable, ViewStyle, StyleSheet } from 'react-native';
import { useColors } from '../../theme';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  style?: ViewStyle;
  onPress?: () => void;
  testID?: string;
}

export function Card({ children, style, onPress, testID }: CardProps) {
  const colors = useColors();

  const cardStyle = StyleSheet.flatten([
    {
      backgroundColor: colors.surfaceContainerHigh,
      borderRadius: 16,
      padding: 16,
      borderWidth: 1,
      borderColor: colors.outlineVariant,
    },
    style,
  ]);

  if (onPress) {
    return (
      <Pressable onPress={onPress} testID={testID}>
        {({ pressed }) => (
          <View style={[cardStyle, pressed && { opacity: 0.8 }]}>
            {children}
          </View>
        )}
      </Pressable>
    );
  }

  return (
    <View style={cardStyle} testID={testID}>
      {children}
    </View>
  );
}

export default Card;