import React, { useState } from 'react';
import type { ExerciseMuscleInfo } from '../../../types';
import type { PrecalibrationExerciseInput } from '../../../services/auge';

const INTENSITY_OPTIONS: { id: PrecalibrationExerciseInput['intensity']; label: string }[] = [
  { id: 'LIGERO', label: 'Ligero' },
  { id: 'MEDIO', label: 'Medio' },
  { id: 'ALTO', label: 'Alto' },
  { id: 'MUY_ALTO', label: 'Muy alto' },
  { id: 'EXTREMO', label: 'Extremo' },
];

interface RecentWorkoutsStepProps {
  exerciseList: ExerciseMuscleInfo[];
  onNext: (exercises: PrecalibrationExerciseInput[]) => void;
  onSkip: () => void;
}

export const RecentWorkoutsStep: React.FC<RecentWorkoutsStepProps> = ({ exerciseList, onNext, onSkip }) => {
  const [exercises, setExercises] = useState<PrecalibrationExerciseInput[]>([{ exerciseName: '', intensity: 'ALTO' }]);
  const [searchOpen, setSearchOpen] = useState<number | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const filteredExercises = searchQuery.trim()
    ? exerciseList.filter((ex) => ex.name.toLowerCase().includes(searchQuery.toLowerCase()) || (ex.id && String(ex.id).toLowerCase().includes(searchQuery.toLowerCase()))).slice(0, 12)
    : [];

  const handleSelectExercise = (idx: number, name: string, dbId?: string) => {
    setExercises((prev) => {
      const next = [...prev];
      next[idx] = { ...next[idx], exerciseName: name, exerciseDbId: dbId };
      return next;
    });
    setSearchOpen(null);
    setSearchQuery('');
  };

  const inputCls = 'w-full bg-[#252525] border border-[#3f3f3f] px-4 py-3 text-white text-sm placeholder-[#737373] focus:border-[#525252] outline-none';

  return (
    <div className="flex flex-col min-h-0 flex-1">
      <div className="flex-1 overflow-y-auto px-4 py-6 custom-scrollbar">
        <h2 className="text-lg font-medium text-white mb-1">Has entrenado en los últimos 7 días?</h2>
        <p className="text-sm text-[#a3a3a3] mb-4">Mas datos = mejor calibración. Con 1 ejercicio basta.</p>
        <div className="space-y-3 mb-4">
          {exercises.map((ex, idx) => (
            <div key={idx} className="bg-[#252525] border border-[#3f3f3f] p-3">
              <div className="flex gap-2">
                <div className="flex-1 relative">
                  <input
                    type="text"
                    value={searchOpen === idx ? searchQuery : ex.exerciseName}
                    onChange={(e) => { if (searchOpen === idx) setSearchQuery(e.target.value); else { setSearchOpen(idx); setSearchQuery(e.target.value); } }}
                    onFocus={() => setSearchOpen(idx)}
                    placeholder="Buscar ejercicio..."
                    className={inputCls}
                  />
                  {searchOpen === idx && filteredExercises.length > 0 && (
                    <div className="absolute top-full left-0 right-0 mt-1 max-h-40 overflow-y-auto bg-[#1a1a1a] border border-[#3f3f3f] z-20">
                      {filteredExercises.map((e) => (
                        <button key={e.id || e.name} onClick={() => handleSelectExercise(idx, e.name, e.id)} className="w-full px-4 py-3 text-left text-sm text-white hover:bg-[#252525]">
                          {e.name}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <select
                  value={ex.intensity}
                  onChange={(e) => setExercises((prev) => { const next = [...prev]; next[idx] = { ...next[idx], intensity: e.target.value as PrecalibrationExerciseInput['intensity'] }; return next; })}
                  className="bg-[#252525] border border-[#3f3f3f] px-3 py-2 text-xs text-white"
                >
                  {INTENSITY_OPTIONS.map((o) => <option key={o.id} value={o.id}>{o.label}</option>)}
                </select>
                {exercises.length > 1 && <button onClick={() => setExercises((prev) => prev.filter((_, i) => i !== idx))} className="text-[#737373] px-2">x</button>}
              </div>
            </div>
          ))}
        </div>
        <button onClick={() => setExercises((prev) => [...prev, { exerciseName: '', intensity: 'ALTO' }])} className="text-sm text-[#a3a3a3] underline">+ Añadir ejercicio</button>
      </div>
      <div className="shrink-0 p-4 flex flex-col gap-3 border-t border-[#2a2a2a]">
        <button onClick={() => onNext(exercises.filter((e) => e.exerciseName.trim()))} className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm">Continuar</button>
        <button onClick={onSkip} className="text-sm text-[#737373] py-2">Omitir</button>
      </div>
    </div>
  );
};
