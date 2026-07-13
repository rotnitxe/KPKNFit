// Paso: Nombre del programa. Estética Tú.
import React from 'react';

interface ProgramNameStepProps {
  value: string;
  onChange: (name: string) => void;
  onNext: () => void;
  onSkip: () => void;
}

export const ProgramNameStep: React.FC<ProgramNameStepProps> = ({ value, onChange, onNext, onSkip }) => {
  const handleNext = () => {
    if (value.trim()) onNext();
  };

  return (
    <div className="flex flex-col min-h-0 flex-1">
      <div className="flex-1 overflow-y-auto px-4 py-6 custom-scrollbar">
        <h2 className="text-lg font-medium text-white mb-1">Nombre de tu programa</h2>
        <p className="text-sm text-[#a3a3a3] mb-6">Cómo quieres llamar a tu primer programa.</p>
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Ej: Rutina Principiante"
          className="w-full bg-[#252525] border border-[#3f3f3f] px-4 py-3 text-white text-sm placeholder-[#737373] focus:border-[#525252] outline-none"
          autoFocus
        />
      </div>
      <div className="shrink-0 p-4 flex flex-col gap-3 border-t border-[#2a2a2a]">
        <button
          onClick={handleNext}
          disabled={!value.trim()}
          className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Continuar
        </button>
        <button onClick={onSkip} className="text-sm text-[#737373] py-2">
          Omitir — Crear programa después
        </button>
      </div>
    </div>
  );
};
