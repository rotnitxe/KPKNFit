import React from 'react';
import AthleteProfilingWizard from '../../AthleteProfilingWizard';
import type { AthleteProfileScore } from '../../../types';

interface AthleteTypeStepProps {
  onComplete: (score: AthleteProfileScore) => void;
  onSkip: () => void;
}

export const AthleteTypeStep: React.FC<AthleteTypeStepProps> = ({ onComplete, onSkip }) => {
  return <AthleteProfilingWizard embedded onComplete={onComplete} onCancel={onSkip} />;
};
