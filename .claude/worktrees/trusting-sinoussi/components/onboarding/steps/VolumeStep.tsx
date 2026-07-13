import React from 'react';
import type { VolumeRecommendation as ProgramVolumeRec } from '../../../types';

interface VolumeStepProps {
  volumeRecommendations: ProgramVolumeRec[];
  onNext: () => void;
}

export const VolumeStep: React.FC<VolumeStepProps> = ({ volumeRecommendations, onNext }) => {
  return (
    <div className="flex flex-col min-h-0 flex-1">
      <div className="flex-1 overflow-y-auto px-4 py-6">
        <h2 className="text-lg font-medium text-white mb-1">Tu volumen recomendado</h2>
        <p className="text-sm text-[#a3a3a3] mb-6">Estas son tus series por semana según tu perfil.</p>
        <div className="space-y-3">
          {volumeRecommendations.map((rec) => {
            const opt = Math.round((rec.minEffectiveVolume + rec.maxRecoverableVolume) / 2);
            return (
              <div key={rec.muscleGroup} className="bg-[#252525] border border-[#3f3f3f] px-4 py-3 flex justify-between">
                <span className="text-white">{rec.muscleGroup}</span>
                <span className="text-[#a3a3a3]">{opt} series/semana</span>
              </div>
            );
          })}
        </div>
      </div>
      <div className="shrink-0 p-4 border-t border-[#2a2a2a]">
        <button onClick={onNext} className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm">
          Continuar
        </button>
      </div>
    </div>
  );
};
