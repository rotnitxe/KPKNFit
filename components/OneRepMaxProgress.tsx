

import React, { useMemo } from 'react';
import { Program, WorkoutLog, Settings } from '../types';
import Card from './ui/Card';
import { TrophyIcon } from './icons';

interface OneRepMaxProgressProps {
  programs: Program[];
  history: WorkoutLog[];
  settings: Settings;
}

const OneRepMaxProgress: React.FC<OneRepMaxProgressProps> = ({ programs, history, settings }) => {

  const goals = useMemo(() => {
    const allGoals: { name: string, goal: number, current: number }[] = [];
    const exerciseMaxes = new Map<string, number>();

    // Calculate the max weight ever lifted for each exercise
    (history || []).forEach(log => {
      (log.completedExercises || []).forEach(ex => {
        const maxWeight = Math.max(...(ex.sets || []).map(s => s.weight || 0));
        const currentMax = exerciseMaxes.get(ex.exerciseName) || 0;
        if (maxWeight > currentMax) {
          exerciseMaxes.set(ex.exerciseName, maxWeight);
        }
      });
    });

    // Find all exercises with a goal1RM and map them to their max lift
    (programs || []).forEach(p => {
      (p.macrocycles || []).forEach(macro => {
        (macro.blocks || []).flatMap(b => b.mesocycles).forEach(meso => {
          (meso.weeks || []).forEach(w => {
            (w.sessions || []).forEach(s => {
                (s.exercises || []).forEach(ex => {
                  if (ex.goal1RM && ex.goal1RM > 0) {
                    // Avoid duplicate goals for the same exercise
                    if (!allGoals.some(g => g.name === ex.name)) {
                        allGoals.push({
                            name: ex.name,
                            goal: ex.goal1RM,
                            current: exerciseMaxes.get(ex.name) || 0,
                        });
                    }
                  }
                });
            });
          });
        });
      });
    });

    return allGoals;
  }, [programs, history]);

  if (goals.length === 0) {
    return null; // Don't render the card if there are no goals set
  }

  return (
    <Card>
      <h3 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
        <TrophyIcon /> Progreso de Metas 1RM
      </h3>
       <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-700 text-slate-400">
                <tr>
                    <th className="p-2">Ejercicio</th>
                    <th className="p-2 text-right">Actual</th>
                    <th className="p-2 text-right">Meta</th>
                    <th className="p-2">Progreso</th>
                </tr>
            </thead>
            <tbody>
                {goals.map(goal => {
                    const percentage = goal.goal > 0 ? Math.min(100, (goal.current / goal.goal) * 100) : 0;
                    return (
                        <tr key={goal.name} className="border-b border-slate-800 last:border-b-0">
                            <td className="p-2 font-semibold">{goal.name}</td>
                            <td className="p-2 text-right font-mono">{goal.current}{settings.weightUnit}</td>
                            <td className="p-2 text-right font-mono text-slate-300">{goal.goal}{settings.weightUnit}</td>
                            <td className="p-2">
                                <div className="flex items-center gap-2">
                                    <div className="w-full bg-slate-700 rounded-full h-2 flex-grow">
                                        <div 
                                            className="bg-[var(--primary-color-500)] h-2 rounded-full"
                                            style={{ width: `${percentage}%` }}
                                        ></div>
                                    </div>
                                    <span className="text-xs text-slate-400 font-mono w-10 text-right">{percentage.toFixed(0)}%</span>
                                </div>
                            </td>
                        </tr>
                    )
                })}
            </tbody>
        </table>
    </div>
    </Card>
  );
};

export default OneRepMaxProgress;