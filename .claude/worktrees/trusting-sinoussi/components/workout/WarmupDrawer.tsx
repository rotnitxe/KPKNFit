// components/workout/WarmupDrawer.tsx
// Drawer de calentamiento con estilo M3 + liquid glass.

import React, { useState, useEffect } from 'react';
import { Exercise, WarmupSetDefinition } from '../../types';
import { FlameIcon, CheckCircleIcon } from '../icons';
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
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Series de aproximacion" height="85vh">
      <div className="p-5 space-y-6">
        <div className="text-center">
          <div className="inline-flex items-center gap-2 px-4 py-3 rounded-[16px] bg-[var(--md-sys-color-secondary-container)] border border-[var(--md-sys-color-outline-variant)]">
            <FlameIcon size={20} className="text-[var(--md-sys-color-primary)]" />
            <span className="text-[10px] font-semibold uppercase tracking-wide text-[var(--md-sys-color-on-secondary-container)]">
              {isConsolidated ? 'Peso meta' : 'Peso objetivo'}
            </span>
            <input
              type="number"
              value={baseWeight || ''}
              onChange={(e) => onBaseWeightChange(parseFloat(e.target.value) || 0)}
              className="w-20 bg-transparent border-b border-[var(--md-sys-color-outline)] text-center font-semibold text-[var(--md-sys-color-on-surface)] text-lg focus:border-[var(--md-sys-color-primary)] outline-none"
              placeholder="0"
            />
            <span className="text-[10px] text-[var(--md-sys-color-on-surface-variant)]">{settings.weightUnit}</span>
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
                className={`flex items-center justify-between w-full px-4 py-4 rounded-[16px] border transition-all text-left group ${
                  isDone
                    ? 'bg-[var(--md-sys-color-primary-container)] border-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary-container)]'
                    : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface)] hover:border-[var(--md-sys-color-outline)]'
                }`}
              >
                <div className="flex items-center gap-4">
                  <div
                    className={`w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors ${
                      isDone ? 'bg-[var(--md-sys-color-primary)] border-[var(--md-sys-color-primary)]' : 'border-[var(--md-sys-color-outline)] group-hover:border-[var(--md-sys-color-primary)]'
                    }`}
                  >
                    {isDone && <CheckCircleIcon size={14} className="text-[var(--md-sys-color-on-primary)]" />}
                  </div>
                  <div>
                    <div className="font-semibold text-lg">
                      {calculatedWeight}
                      <span className="text-[10px] opacity-70 ml-1 font-medium">{settings.weightUnit}</span>
                    </div>
                    <span className="text-[10px] text-[var(--md-sys-color-on-surface-variant)]">
                      {set.percentageOfWorkingWeight}% carga
                    </span>
                  </div>
                </div>
                <div className="text-right">
                  <span className="text-2xl font-semibold text-[var(--md-sys-color-on-surface)] block leading-none">
                    {set.targetReps}
                  </span>
                  <span className="text-[10px] text-[var(--md-sys-color-on-surface-variant)]">Reps</span>
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
