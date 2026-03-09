// components/workout/PostExerciseDrawer.tsx
// Drawer de feedback post-ejercicio con estilo Material 3 + liquid glass.

import React, { useState, useMemo } from 'react';
import { CheckCircleIcon, SearchIcon, ChevronDownIcon, ChevronRightIcon } from '../icons';
import { hapticImpact } from '../../services/hapticsService';
import { ImpactStyle } from '../../services/hapticsService';
import WorkoutDrawer from './WorkoutDrawer';
import { DISCOMFORT_DATABASE } from '../../data/discomfortList';

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
            value === v
              ? 'bg-[var(--md-sys-color-primary)] border-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)]'
              : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface)] hover:border-[var(--md-sys-color-outline)]'
          }`}
          aria-label={`${v} de 10`}
        >
          {v}
        </button>
      ))}
    </div>
    {labels && (
      <div className="flex justify-between w-full mt-0.5 text-[10px] text-[var(--md-sys-color-on-surface-variant)]">
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
  jointLoad?: number;
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
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Feedback post-ejercicio" height="80vh">
      <div className="px-4 pb-[max(1rem,env(safe-area-inset-bottom))] flex flex-col gap-4">
        <div className="rounded-[12px] border border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface)] px-4 py-3">
          <p className="text-[16px] font-medium text-[var(--md-sys-color-on-surface)]">
            {exerciseName}
          </p>
          <p className="text-[14px] text-[var(--md-sys-color-on-surface-variant)]">
            Completa el cierre del ejercicio
          </p>
        </div>

        <div className="space-y-4">
          <div>
            <p className="text-[11px] font-semibold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-wide mb-1">Calidad tecnica</p>
            <PointSelector value={technicalQuality} onChange={setTechnicalQuality} labels={['Baja', 'Alta']} />
          </div>

          <div>
            <p className="text-[11px] font-semibold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-wide mb-1">Fatiga percibida</p>
            <PointSelector value={perceivedFatigue} onChange={setPerceivedFatigue} labels={['Nada', 'Mucha']} />
          </div>

          <div>
            <button
              type="button"
              onClick={() => setShowDiscomfortSearch(!showDiscomfortSearch)}
              className="flex items-center gap-2 text-[11px] font-semibold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-wide mb-2"
            >
              ¿Tuviste alguna molestia?
              {showDiscomfortSearch ? <ChevronDownIcon size={14} /> : <ChevronRightIcon size={14} />}
            </button>
            {showDiscomfortSearch && (
              <div className="animate-fade-in space-y-3">
                <div className="flex items-center gap-2 bg-[var(--md-sys-color-surface)] p-2 border border-[var(--md-sys-color-outline-variant)] rounded-[12px]">
                  <SearchIcon size={14} className="text-[var(--md-sys-color-on-surface-variant)]" />
                  <input
                    type="text"
                    value={discomfortSearchQuery}
                    onChange={(e) => setDiscomfortSearchQuery(e.target.value)}
                    placeholder="Describe o busca..."
                    className="bg-transparent border-none text-sm w-full focus:ring-0 text-[var(--md-sys-color-on-surface)] placeholder:text-[var(--md-sys-color-on-surface-variant)]"
                  />
                </div>
                <div className="max-h-36 overflow-y-auto custom-scrollbar space-y-2">
                  {filteredDiscomforts.map((d) => (
                    <button
                      key={d.id}
                      type="button"
                      onClick={() => toggleDiscomfort(d.name)}
                      className={`w-full text-left p-3 rounded-[12px] border transition-all ${
                        selectedDiscomforts.includes(d.name)
                          ? 'bg-[var(--md-sys-color-primary-container)] border-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary-container)]'
                          : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)] hover:border-[var(--md-sys-color-outline)] text-[var(--md-sys-color-on-surface)]'
                      }`}
                    >
                      <span className="font-semibold text-sm">{d.name}</span>
                      <p className="text-[11px] mt-1 text-[var(--md-sys-color-on-surface-variant)]">{d.description}</p>
                    </button>
                  ))}
                </div>
                {selectedDiscomforts.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {selectedDiscomforts.map((name) => (
                      <span
                        key={name}
                        className="px-2 py-0.5 bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] text-[10px] font-medium rounded-[999px] flex items-center gap-1 inline-flex"
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

        <p className="text-[10px] text-[var(--md-sys-color-on-surface-variant)]">
          Estos datos se sumaran al resumen final para que puedas editarlos al terminar.
        </p>

        <button
          onClick={handleSave}
          className="w-full py-3.5 text-[11px] font-semibold uppercase tracking-wide bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] rounded-[999px] border border-[var(--md-sys-color-primary)] flex items-center justify-center gap-2"
        >
          <CheckCircleIcon size={18} />
          Completar ejercicio
        </button>
      </div>
    </WorkoutDrawer>
  );
};

export default PostExerciseDrawer;
