// components/nutrition/NutritionHeroBanner.tsx
// Hero estilo Tú: fondo #1a1a1a, anillos, fecha inline, link Progreso

import React from 'react';
import { NutritionTelemetryPanel } from './NutritionTelemetryPanel';
import { CalorieGoalCard } from './CalorieGoalCard';

interface NutritionHeroBannerProps {
    selectedDate: string;
    onDateChange: (date: string) => void;
    dailyCalories: number;
    calorieGoal: number;
    hasCalorieGoal: boolean;
    protein: number;
    proteinGoal: number;
    carbs: number;
    carbGoal: number;
    fats: number;
    fatGoal: number;
    onProgresoPress?: () => void;
    progressPct?: number | null;
    activePlanName?: string | null;
    goalLabel?: string | null;
    onEditCalories?: () => void;
}

const NutritionHeroBanner: React.FC<NutritionHeroBannerProps> = ({
    selectedDate,
    onDateChange,
    dailyCalories,
    calorieGoal,
    hasCalorieGoal,
    protein,
    proteinGoal,
    carbs,
    carbGoal,
    fats,
    fatGoal,
    onProgresoPress,
    progressPct,
    activePlanName,
    goalLabel,
    onEditCalories,
}) => {
    return (
        <div className="w-full shrink-0 bg-[#1a1a1a] px-4 py-4" style={{ paddingTop: 'max(1rem, env(safe-area-inset-top, 0px))' }}>
            <div className="flex justify-between items-start mb-3">
                <div className="min-w-0">
                    <h1 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em]">
                        Nutrición
                    </h1>
                    <input
                        type="date"
                        value={selectedDate}
                        onChange={e => onDateChange(e.target.value)}
                        className="bg-transparent border-none text-zinc-400 text-xs font-mono mt-0.5 focus:ring-0 p-0 cursor-pointer"
                    />
                </div>
                {onProgresoPress && (
                    <button
                        onClick={onProgresoPress}
                        className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider hover:text-white shrink-0"
                    >
                        Progreso
                    </button>
                )}
            </div>

            <div className="mb-4">
                <NutritionTelemetryPanel
                    dailyCalories={dailyCalories}
                    calorieGoal={calorieGoal}
                    protein={protein}
                    proteinGoal={proteinGoal}
                    carbs={carbs}
                    carbGoal={carbGoal}
                    fats={fats}
                    fatGoal={fatGoal}
                    hasCalorieGoal={hasCalorieGoal}
                />
            </div>

            {progressPct != null && onProgresoPress && (
                <button
                    onClick={onProgresoPress}
                    className="w-full mb-4 p-3 rounded-xl bg-white/5 border border-white/10 hover:bg-white/10 transition-colors text-left"
                >
                    <div className="flex justify-between text-xs text-zinc-400 mb-1">
                        <span>Progreso hacia meta</span>
                        <span>{progressPct}%</span>
                    </div>
                    <div className="h-1.5 bg-white/10 rounded-full overflow-hidden">
                        <div
                            className="h-full rounded-full transition-all bg-emerald-500"
                            style={{ width: `${Math.min(100, progressPct)}%` }}
                        />
                    </div>
                </button>
            )}

            <div className="flex flex-col gap-3">
                {onEditCalories && (
                    <CalorieGoalCard calorieGoal={calorieGoal} onEditClick={onEditCalories} />
                )}
                {activePlanName && goalLabel && (
                    <div className="p-3 rounded-xl bg-white/5 border border-white/10">
                        <p className="text-sm font-black text-white uppercase tracking-tight">{activePlanName}</p>
                        <p className="text-xs text-zinc-400 mt-0.5">Objetivo: {goalLabel}</p>
                    </div>
                )}
            </div>
        </div>
    );
};

export default NutritionHeroBanner;
