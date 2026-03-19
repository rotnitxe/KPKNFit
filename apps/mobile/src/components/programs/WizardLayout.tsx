import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useColors } from '../../theme';
import { Button } from '../ui';
import { ArrowLeftIcon, ChevronRightIcon } from '../icons';
import WizardStepIndicator from './WizardStepIndicator';

interface WizardLayoutProps {
  title: string;
  steps: string[];
  currentStep: number;
  children: React.ReactNode;
  onBack: () => void;
  onNext: () => void;
  canNext: boolean;
  isLastStep: boolean;
}

const WizardLayout: React.FC<WizardLayoutProps> = ({
  title,
  steps,
  currentStep,
  children,
  onBack,
  onNext,
  canNext,
  isLastStep,
}) => {
  const colors = useColors();

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurface }]}>{title}</Text>
        <WizardStepIndicator
          steps={steps}
          currentStep={currentStep}
        />
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {children}
      </ScrollView>

      <View style={[styles.footer, { borderTopColor: colors.outlineVariant }]}>
        <Button
          variant="secondary"
          onPress={onBack}
          style={styles.backButton}
        >
          <ArrowLeftIcon size={16} color={colors.onSurfaceVariant} />
          <Text style={[styles.backButtonText, { color: colors.onSurfaceVariant }]}>
            {currentStep === 0 ? 'CANCELAR' : 'ANTERIOR'}
          </Text>
        </Button>

        <View style={styles.spacer} />

        <Button
          variant="primary"
          onPress={onNext}
          disabled={!canNext}
        >
          <Text style={[styles.nextButtonText, { color: colors.onPrimary }]}>
            {isLastStep ? 'CREAR PROGRAMA' : 'SIGUIENTE'}
          </Text>
          {!isLastStep && <ChevronRightIcon size={16} color={colors.onPrimary} />}
        </Button>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 10,
  },
  title: {
    fontSize: 24,
    fontWeight: '900',
    marginBottom: 16,
    textAlign: 'center',
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderTopWidth: 1,
    gap: 12,
  },
  backButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  backButtonText: {
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  spacer: {
    flex: 1,
  },
  nextButtonText: {
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 0.5,
    marginRight: 8,
  },
});

export default WizardLayout;