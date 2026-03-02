// components/workout/PostExerciseDrawer.tsx
// Drawer de feedback post-ejercicio - nutre el modal de finalización
// Estética "Tú": selectores de puntos 1–10, gris, sin accent-*

import React, { useState, useMemo } from 'react';
import { CheckCircleIcon, SearchIcon, ChevronDownIcon, ChevronRightIcon } from '../icons';
import { hapticImpact } from '../../services/hapticsService';
import { ImpactStyle } from '../../services/hapticsService';
import WorkoutDrawer from './WorkoutDrawer';
import { DISCOMFORT_DATABASE } from '../../data/discomfortList';

/** Selector de puntos 1–10 en fila, estilo ReadinessDrawer */
const PointSelector: React.FC<{
  value: number;
  onChange: (v: number) => void;
  labels?: [string, string];
}> = ({ value, onChange, labels }) => (
  <div className="flex flex-col">
    <div className="flex flex-wrap gap-1">
      {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((v) => (
        <button
          key={v}
          type="button"
          onClick={() => onChange(v)}
          className={`w-7 h-7 rounded-full border transition-colors text-[10px] font-medium ${
            value === v ? 'bg-[#525252] border-[#525252] text-white' : 'bg-white border-[#a3a3a3] text-[#1a1a1a] hover:border-[#737373]'
          }`}
          aria-label={`${v} de 10`}
        >
          {v}
        </button>
      ))}
    </div>
    {labels && (
      <div className="flex justify-between w-full mt-0.5 text-[9px] text-[#737373]">
        <span>{labels[0]}</span>
        <span>{labels[1]}</span>
      </div>
    )}
  </div>
);

export interface PostExerciseFeedback {
  technicalQuality: number;
  discomforts: string[];
  perceivedFatigue?: number;
  jointLoad?: number; // deprecated, mantener por compatibilidad
}

interface PostExerciseDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  exerciseName: string;
  onSave: (feedback: PostExerciseFeedback) => void;
}

const PostExerciseDrawer: React.FC<PostExerciseDrawerProps> = ({
  isOpen,
  onClose,
  exerciseName,
  onSave,
}) => {
  const [technicalQuality, setTechnicalQuality] = useState(8);
  const [perceivedFatigue, setPerceivedFatigue] = useState(5);
  const [selectedDiscomforts, setSelectedDiscomforts] = useState<string[]>([]);
  const [showDiscomfortSearch, setShowDiscomfortSearch] = useState(false);
  const [discomfortSearchQuery, setDiscomfortSearchQuery] = useState('');

  const filteredDiscomforts = useMemo(() => {
    const q = discomfortSearchQuery.trim().toLowerCase();
    if (!q) return DISCOMFORT_DATABASE.slice(0, 12);
    return DISCOMFORT_DATABASE.filter(
      d =>
        d.name.toLowerCase().includes(q) ||
        d.description.toLowerCase().includes(q)
    ).slice(0, 15);
  }, [discomfortSearchQuery]);

  const toggleDiscomfort = (name: string) => {
    setSelectedDiscomforts(prev =>
      prev.includes(name) ? prev.filter(n => n !== name) : [...prev, name]
    );
  };

  const handleSave = () => {
    hapticImpact(ImpactStyle.Medium);
    onSave({
      technicalQuality,
      discomforts: selectedDiscomforts,
      perceivedFatigue,
      jointLoad: 5,
    });
    onClose();
  };

  return (
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Feedback Post-Ejercicio" height="80vh">
      <div className="px-4 pb-[max(1rem,env(safe-area-inset-bottom))] flex flex-col gap-4">
        <div className="text-center shrink-0">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-[#525252]">
            ¿Cómo sentiste el {exerciseName}?
          </p>
        </div>

        <div className="space-y-4 shrink-0">
          <div>
            <p className="text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-1">Calidad técnica</p>
            <PointSelector value={technicalQuality} onChange={setTechnicalQuality} labels={['Baja', 'Alta']} />
          </div>

          <div>
            <p className="text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-1">Fatiga percibida</p>
            <PointSelector value={perceivedFatigue} onChange={setPerceivedFatigue} labels={['Nada', 'Mucha']} />
          </div>

          <div>
            <button
              type="button"
              onClick={() => setShowDiscomfortSearch(!showDiscomfortSearch)}
              className="flex items-center gap-2 text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-2"
            >
              ¿Tuviste alguna molestia?
              {showDiscomfortSearch ? <ChevronDownIcon size={14} /> : <ChevronRightIcon size={14} />}
            </button>
            {showDiscomfortSearch && (
              <div className="animate-fade-in space-y-3">
                <div className="flex items-center gap-2 bg-white p-2 border border-[#a3a3a3]">
                  <SearchIcon size={14} className="text-[#737373]" />
                  <input
                    type="text"
                    value={discomfortSearchQuery}
                    onChange={(e) => setDiscomfortSearchQuery(e.target.value)}
                    placeholder="Describe o busca..."
                    className="bg-transparent border-none text-sm w-full focus:ring-0 text-[#1a1a1a] placeholder:text-[#a3a3a3]"
                  />
                </div>
                <div className="max-h-36 overflow-y-auto custom-scrollbar space-y-2">
                  {filteredDiscomforts.map((d) => (
                    <button
                      key={d.id}
                      type="button"
                      onClick={() => toggleDiscomfort(d.name)}
                      className={`w-full text-left p-3 border transition-all ${
                        selectedDiscomforts.includes(d.name)
                          ? 'bg-[#525252] border-[#525252] text-white'
                          : 'bg-white border-[#a3a3a3] hover:border-[#737373] text-[#1a1a1a]'
                      }`}
                    >
                      <span className="font-semibold text-sm">{d.name}</span>
                      <p className={`text-[10px] mt-1 ${selectedDiscomforts.includes(d.name) ? 'text-white/80' : 'text-[#737373]'}`}>{d.description}</p>
                    </button>
                  ))}
                </div>
                {selectedDiscomforts.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {selectedDiscomforts.map((name) => (
                      <span
                        key={name}
                        className="px-2 py-0.5 bg-[#525252] text-white text-[10px] font-medium flex items-center gap-1 inline-flex"
                      >
                        {name}
                        <button
                          type="button"
                          onClick={() => toggleDiscomfort(name)}
                          className="opacity-80 hover:opacity-100"
                        >
                          ×
                        </button>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        <p className="text-[9px] text-[#737373] shrink-0">
          Estos datos se sumarán al resumen final para que puedas editarlos al terminar.
        </p>

        <button
          onClick={handleSave}
          className="w-full py-3.5 text-[10px] font-semibold uppercase tracking-wide bg-white text-[#1a1a1a] border border-[#a3a3a3] flex items-center justify-center gap-2 shrink-0"
        >
          <CheckCircleIcon size={18} />
          Completar Ejercicio
        </button>
      </div>
    </WorkoutDrawer>
  );
};

export default PostExerciseDrawer;
