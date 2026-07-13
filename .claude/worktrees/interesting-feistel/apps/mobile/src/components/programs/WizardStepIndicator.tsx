import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useColors } from '../../theme';
import { CheckCircleIcon } from '../icons';

interface WizardStepIndicatorProps {
  steps: string[];
  currentStep: number;
  onStepPress?: (index: number) => void;
}

const WizardStepIndicator: React.FC<WizardStepIndicatorProps> = ({
  steps,
  currentStep,
  onStepPress,
}) => {
  const colors = useColors();

  const renderStep = (step: string, index: number) => {
    const isCompleted = index < currentStep;
    const isCurrent = index === currentStep;
    const isFuture = index > currentStep;

    return (
      <View key={index} style={styles.stepContainer}>
        {index > 0 && (
          <View
            style={[
              styles.connector,
              { backgroundColor: isCompleted ? colors.primary : colors.outlineVariant },
            ]}
          />
        )}
        <TouchableOpacity
          onPress={() => onStepPress?.(index)}
          style={[
            styles.stepCircle,
            {
              backgroundColor: isCurrent ? colors.primary : isCompleted ? colors.primary : colors.surfaceVariant,
              borderColor: isCurrent ? colors.primary : colors.outlineVariant,
            },
          ]}
          disabled={!onStepPress}
        >
          {isCompleted ? (
            <CheckCircleIcon size={20} color={colors.onPrimary} />
          ) : (
            <Text
              style={[
                styles.stepNumber,
                { color: isCurrent ? colors.onPrimary : colors.onSurfaceVariant },
              ]}
            >
              {index + 1}
            </Text>
          )}
        </TouchableOpacity>
        <Text
          style={[
            styles.stepLabel,
            { color: isCurrent ? colors.primary : colors.onSurfaceVariant },
          ]}
        >
          {step}
        </Text>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      {steps.map((step, index) => renderStep(step, index))}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  stepContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  connector: {
    flex: 1,
    height: 2,
    marginHorizontal: 4,
  },
  stepCircle: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
  },
  stepNumber: {
    fontSize: 12,
    fontWeight: '900',
  },
  stepLabel: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 0.5,
    marginLeft: 4,
    textAlign: 'center',
    flex: 1,
  },
});

export default WizardStepIndicator;