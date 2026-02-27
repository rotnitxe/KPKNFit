// components/workout/PostExerciseDrawer.tsx
// Drawer de feedback post-ejercicio - nutre el modal de finalización

import React, { useState, useMemo } from 'react';
import { CheckCircleIcon, SearchIcon, ChevronDownIcon, ChevronRightIcon } from '../icons';
import { hapticImpact } from '../../services/hapticsService';
import { ImpactStyle } from '../../services/hapticsService';
import WorkoutDrawer from './WorkoutDrawer';
import { DISCOMFORT_DATABASE } from '../../data/discomfortList';

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
      <div className="p-5 space-y-6">
        <div className="text-center">
          <p className="text-[10px] font-mono font-black uppercase tracking-widest text-slate-500">
            ¿Cómo sentiste el {exerciseName}?
          </p>
        </div>

        <div className="space-y-6">
          <div>
            <div className="flex justify-between text-[10px] font-mono font-bold text-slate-400 mb-2">
              <span>Calidad técnica</span>
              <span className={technicalQuality < 5 ? 'text-red-400' : 'text-green-400'}>
                {technicalQuality}/10
              </span>
            </div>
            <input
              type="range"
              min="1"
              max="10"
              value={technicalQuality}
              onChange={(e) => setTechnicalQuality(parseInt(e.target.value))}
              className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-purple-500"
            />
          </div>

          <div>
            <div className="flex justify-between text-[10px] font-mono font-bold text-slate-400 mb-2">
              <span>Fatiga percibida</span>
              <span className="text-cyber-cyan">{perceivedFatigue}/10</span>
            </div>
            <input
              type="range"
              min="1"
              max="10"
              value={perceivedFatigue}
              onChange={(e) => setPerceivedFatigue(parseInt(e.target.value))}
              className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-[#00F0FF]"
            />
          </div>

          <div>
            <button
              type="button"
              onClick={() => setShowDiscomfortSearch(!showDiscomfortSearch)}
              className="flex items-center gap-2 text-sm font-medium text-slate-300 mb-2"
            >
              ¿Tuviste alguna molestia en este ejercicio?
              {showDiscomfortSearch ? <ChevronDownIcon size={14} /> : <ChevronRightIcon size={14} />}
            </button>
            {showDiscomfortSearch && (
              <div className="animate-fade-in space-y-3">
                <div className="flex items-center gap-2 bg-slate-800/50 p-2 rounded-lg border border-white/5">
                  <SearchIcon size={14} className="text-slate-500" />
                  <input
                    type="text"
                    value={discomfortSearchQuery}
                    onChange={(e) => setDiscomfortSearchQuery(e.target.value)}
                    placeholder="Describe tu molestia o busca..."
                    className="bg-transparent border-none text-sm w-full focus:ring-0 text-white"
                  />
                </div>
                <div className="max-h-36 overflow-y-auto custom-scrollbar space-y-2">
                  {filteredDiscomforts.map((d) => (
                    <button
                      key={d.id}
                      type="button"
                      onClick={() => toggleDiscomfort(d.name)}
                      className={`w-full text-left p-3 rounded-lg border transition-all ${
                        selectedDiscomforts.includes(d.name)
                          ? 'bg-primary-color/20 border-primary-color/50'
                          : 'bg-slate-800/50 border-white/5 hover:border-white/10'
                      }`}
                    >
                      <span className="font-bold text-sm text-white">{d.name}</span>
                      <p className="text-[10px] text-slate-400 mt-1">{d.description}</p>
                    </button>
                  ))}
                </div>
                {selectedDiscomforts.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {selectedDiscomforts.map((name) => (
                      <span
                        key={name}
                        className="px-2 py-0.5 rounded-full bg-primary-color/30 text-primary-color text-[10px] font-bold"
                      >
                        {name}{' '}
                        <button
                          type="button"
                          onClick={() => toggleDiscomfort(name)}
                          className="ml-1 opacity-70"
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

        <p className="text-[9px] text-slate-500 italic">
          Estos datos se sumarán al modal de finalización para que puedas editarlos al terminar.
        </p>

        <button
          onClick={handleSave}
          className="w-full py-4 rounded-xl bg-cyber-cyan text-white font-mono font-black text-[10px] uppercase tracking-widest hover:bg-cyber-cyan/90 transition-colors border border-cyber-cyan/30 flex items-center justify-center gap-2"
        >
          <CheckCircleIcon size={18} />
          Completar Ejercicio
        </button>
      </div>
    </WorkoutDrawer>
  );
};

export default PostExerciseDrawer;
