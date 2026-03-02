import React from 'react';
import AthleteProfilingWizard from '../../AthleteProfilingWizard';
import type { AthleteProfileScore } from '../../../types';

interface AthleteTypeStepProps {
  onComplete: (score: AthleteProfileScore) => void;
  onSkip: () => void;
}

export const AthleteTypeStep: React.FC<AthleteTypeStepProps> = ({ onComplete, onSkip }) => {
  return (
    <div className="flex flex-col min-h-0 flex-1 bg-[#1a1a1a]">
      <div className="flex justify-end px-4 pt-4 shrink-0">
        <button onClick={onSkip} className="text-sm text-[#737373] py-2">Omitir</button>
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <AthleteProfilingWizard inline={false} onComplete={onComplete} onCancel={onSkip} />
      </div>
    </div>
  );
};
