// components/nutrition/NutritionHeroBanner.tsx
// Hero vistoso e interactivo: gradiente, anillos animados, cards con profundidad

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
    const calPct = hasCalorieGoal && calorieGoal > 0 ? Math.min(100, (dailyCalories / calorieGoal) * 100) : 0;

    return (
        <div className="w-full shrink-0 relative overflow-hidden" style={{ paddingTop: 'max(1rem, env(safe-area-inset-top, 0px))' }}>
            <div className="absolute inset-0 bg-gradient-to-br from-[#1e293b] via-[#0f172a] to-[#020617]" />
            <div className="absolute top-0 right-0 w-48 h-48 bg-emerald-500/10 blur-3xl rounded-full -translate-y-1/2 translate-x-1/2" />
            <div className="absolute bottom-0 left-0 w-32 h-32 bg-amber-500/10 blur-2xl rounded-full translate-y-1/2 -translate-x-1/2" />
            <div className="relative z-10 px-4 py-5">
                <div className="flex justify-between items-center mb-5">
                    <button
                        onClick={() => document.getElementById('hero-date-picker')?.showPicker?.()}
                        className="px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 transition-all active:scale-95"
                    >
                        <span className="text-white font-bold text-sm">
                            {new Date(selectedDate).toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short' })}
                        </span>
                    </button>
                    <input
                        id="hero-date-picker"
                        type="date"
                        value={selectedDate}
                        onChange={e => onDateChange(e.target.value)}
                        className="absolute opacity-0 w-0 h-0 pointer-events-none"
                    />
                    {onProgresoPress && (
                        <button
                            onClick={onProgresoPress}
                            className="px-4 py-2 rounded-xl bg-white/5 hover:bg-white/15 border border-white/10 text-white font-bold text-sm transition-all active:scale-95"
                        >
                            Progreso
                        </button>
                    )}
                </div>
                <div className="mb-5 p-5 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm">
                    <p className="text-xs font-bold text-zinc-400 uppercase tracking-wider mb-1">Calorías hoy</p>
                    <p className="text-4xl font-black text-white tracking-tight">
                        {dailyCalories}
                        <span className="text-xl font-bold text-zinc-500 ml-1">kcal</span>
                    </p>
                    {hasCalorieGoal && calorieGoal > 0 && (
                        <div className="mt-3">
                            <div className="flex justify-between text-xs text-zinc-500 mb-1">
                                <span>Objetivo {calorieGoal} kcal</span>
                                <span className="font-bold text-white">{Math.round(calPct)}%</span>
                            </div>
                            <div className="h-2 bg-white/10 rounded-full overflow-hidden">
                                <div
                                    className="h-full rounded-full bg-gradient-to-r from-emerald-500 to-emerald-400 transition-all duration-500"
                                    style={{ width: `${Math.min(100, calPct)}%` }}
                                />
                            </div>
                        </div>
                    )}
                </div>
                <div className="mb-5 p-4 rounded-2xl bg-black/30 border border-white/5 backdrop-blur-sm">
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
                        className="w-full mb-4 p-4 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/10 active:scale-[0.98] transition-all text-left"
                    >
                        <div className="flex justify-between items-center mb-2">
                            <span className="text-sm font-bold text-zinc-400">Progreso hacia meta</span>
                            <span className="text-lg font-black text-emerald-400">{progressPct}%</span>
                        </div>
                        <div className="h-3 bg-white/10 rounded-full overflow-hidden">
                            <div
                                className="h-full rounded-full bg-gradient-to-r from-emerald-600 to-emerald-400 transition-all duration-700"
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
                        <div className="p-4 rounded-2xl bg-white/5 border border-white/10 hover:bg-white/8 transition-colors">
                            <p className="text-base font-black text-white">{activePlanName}</p>
                            <p className="text-sm text-zinc-400 mt-0.5">Objetivo: {goalLabel}</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default NutritionHeroBanner;
