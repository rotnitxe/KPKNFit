import React from 'react';
import { TextInput, TextInputProps, StyleSheet } from 'react-native';

import { useColors } from '../../theme';

interface TacticalTextInputProps extends TextInputProps {
  // inherits all TextInput props
}

const TacticalTextInput: React.FC<TacticalTextInputProps> = (props) => {
  const colors = useColors();
  return (
    <TextInput
      {...props}
      placeholderTextColor={`${colors.onSurfaceVariant}99`}
      style={[
        styles.input,
        {
          backgroundColor: colors.surfaceContainer,
          borderColor: colors.outlineVariant,
          color: colors.onSurface,
        },
        props.style
      ]}
    />
  );
};

const styles = StyleSheet.create({
  input: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
  },
});

export default TacticalTextInput;