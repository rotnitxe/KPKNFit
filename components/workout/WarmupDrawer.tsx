// components/workout/WarmupDrawer.tsx
// Drawer de calentamiento - migración de WarmupDashboard

import React, { useState, useEffect } from 'react';
import { Exercise, WarmupSetDefinition } from '../../types';
import { FlameIcon, ActivityIcon, CheckCircleIcon } from '../icons';
import { hapticImpact } from '../../services/hapticsService';
import { ImpactStyle } from '../../services/hapticsService';
import { roundWeight } from '../../utils/calculations';
import WorkoutDrawer from './WorkoutDrawer';

interface WarmupDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  exercise: Exercise;
  baseWeight: number;
  onBaseWeightChange: (weight: number) => void;
  settings: { weightUnit: 'kg' | 'lbs' };
  isConsolidated: boolean;
  onComplete: () => void;
}

const WarmupDrawer: React.FC<WarmupDrawerProps> = ({
  isOpen,
  onClose,
  exercise,
  baseWeight,
  onBaseWeightChange,
  settings,
  isConsolidated,
  onComplete,
}) => {
  const [checkedSets, setCheckedSets] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (
      exercise.warmupSets &&
      checkedSets.size === exercise.warmupSets.length &&
      exercise.warmupSets.length > 0
    ) {
      const timer = setTimeout(() => {
        onComplete();
        onClose();
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [checkedSets, exercise.warmupSets, onComplete, onClose]);

  if (!exercise.warmupSets || exercise.warmupSets.length === 0) return null;

  const toggleSet = (id: string) => {
    setCheckedSets((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(id)) newSet.delete(id);
      else newSet.add(id);
      return newSet;
    });
    hapticImpact(ImpactStyle.Light);
  };

  return (
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Calentamiento" height="85vh">
      <div className="p-5 space-y-6">
        <div className="text-center">
          <div className="inline-flex items-center gap-2 p-3 rounded-xl bg-sky-500/10 border border-sky-500/20">
            <FlameIcon size={20} className="text-sky-400" />
            <span className="text-[10px] font-mono font-black uppercase tracking-widest text-sky-400">
              {isConsolidated ? 'Peso Meta' : 'Peso Objetivo'}
            </span>
            <input
              type="number"
              value={baseWeight || ''}
              onChange={(e) => onBaseWeightChange(parseFloat(e.target.value) || 0)}
              className="w-20 bg-transparent border-b border-slate-600 text-center font-bold text-white text-lg focus:border-sky-400 outline-none"
              placeholder="0"
            />
            <span className="text-[10px] text-slate-500 font-mono">{settings.weightUnit}</span>
          </div>
        </div>

        <div className="space-y-2">
          {(exercise.warmupSets as WarmupSetDefinition[]).map((set) => {
            const calculatedWeight = roundWeight(
              (baseWeight * (set.percentageOfWorkingWeight / 100)),
              settings.weightUnit
            );
            const isDone = checkedSets.has(set.id);

            return (
              <button
                key={set.id}
                onClick={() => toggleSet(set.id)}
                className={`flex items-center justify-between w-full px-4 py-4 rounded-xl border transition-all text-left group ${
                  isDone
                    ? 'bg-sky-600/20 border-sky-500/50 text-white'
                    : 'bg-[#0d0d0d] border-cyber-cyan/20 text-slate-300 hover:border-sky-500/30'
                }`}
              >
                <div className="flex items-center gap-4">
                  <div
                    className={`w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors ${
                      isDone ? 'bg-sky-500 border-sky-500' : 'border-slate-600 group-hover:border-sky-500/50'
                    }`}
                  >
                    {isDone && <CheckCircleIcon size={14} className="text-white" />}
                  </div>
                  <div>
                    <div className="font-mono font-black text-lg">
                      {calculatedWeight}
                      <span className="text-[10px] opacity-70 ml-1 font-bold">{settings.weightUnit}</span>
                    </div>
                    <span className="text-[9px] font-mono text-sky-400 uppercase tracking-wider">
                      {set.percentageOfWorkingWeight}% Carga
                    </span>
                  </div>
                </div>
                <div className="text-right">
                  <span className="text-2xl font-black text-white block leading-none">
                    {set.targetReps}
                  </span>
                  <span className="text-[9px] font-mono text-slate-500 uppercase font-bold">Reps</span>
                </div>
              </button>
            );
          })}
        </div>
      </div>
    </WorkoutDrawer>
  );
};

export default WarmupDrawer;
