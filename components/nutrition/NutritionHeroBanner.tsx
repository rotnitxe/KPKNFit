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
        <div className="w-full shrink-0 relative overflow-hidden bg-[var(--md-sys-color-surface-container-low)]" style={{ paddingTop: 'max(1rem, env(safe-area-inset-top, 0px))' }}>
            <div className="absolute inset-0 bg-gradient-to-br from-[var(--md-sys-color-surface-container-low)] via-[var(--md-sys-color-surface-container)] to-[var(--md-sys-color-surface-container-high)]" />
            <div className="absolute top-0 right-0 w-48 h-48 bg-[var(--md-sys-color-primary)]/5 blur-3xl rounded-full -translate-y-1/2 translate-x-1/2" />
            <div className="absolute bottom-0 left-0 w-32 h-32 bg-[var(--md-sys-color-secondary)]/5 blur-2xl rounded-full translate-y-1/2 -translate-x-1/2" />
            <div className="relative z-10 px-4 py-6">
                <div className="flex justify-between items-center mb-5">
                    <button
                        onClick={() => (document.getElementById('hero-date-picker') as any)?.showPicker?.()}
                        className="px-4 py-2 rounded-xl bg-[var(--md-sys-color-surface-container)]/50 hover:bg-[var(--md-sys-color-surface-container-highest)] border border-[var(--md-sys-color-outline-variant)] transition-all active:scale-95"
                    >
                        <span className="text-[var(--md-sys-color-on-surface)] font-bold text-sm">
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
                            className="px-4 py-2 rounded-xl bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)] font-black uppercase text-[10px] tracking-widest transition-all active:scale-95 shadow-sm"
                        >
                            Progreso
                        </button>
                    )}
                </div>
                <div className="mb-5 p-6 rounded-[28px] bg-[var(--md-sys-color-surface-container-highest)] shadow-sm border border-[var(--md-sys-color-outline-variant)]/30">
                    <p className="text-[10px] font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-[0.2em] mb-2">Calorías consumidas</p>
                    <p className="text-5xl font-black text-[var(--md-sys-color-on-surface)] tracking-tighter">
                        {dailyCalories}
                        <span className="text-xl font-bold text-[var(--md-sys-color-on-surface-variant)]/60 ml-2">kcal</span>
                    </p>
                    {hasCalorieGoal && calorieGoal > 0 && (
                        <div className="mt-4">
                            <div className="flex justify-between text-[10px] font-bold text-[var(--md-sys-color-on-surface-variant)] mb-2 uppercase tracking-widest">
                                <span>Objetivo {calorieGoal} kcal</span>
                                <span className="text-[var(--md-sys-color-on-surface)]">{Math.round(calPct)}%</span>
                            </div>
                            <div className="h-2.5 bg-[var(--md-sys-color-surface-container-low)] rounded-full overflow-hidden">
                                <div
                                    className="h-full rounded-full bg-gradient-to-r from-[var(--md-sys-color-primary)] to-[var(--md-sys-color-primary-container)] transition-all duration-700"
                                    style={{ width: `${Math.min(100, calPct)}%` }}
                                />
                            </div>
                        </div>
                    )}
                </div>
                <div className="mb-5 p-6 rounded-[28px] bg-[var(--md-sys-color-surface)]/40 border border-[var(--md-sys-color-outline-variant)]/20 backdrop-blur-md">
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
                        className="w-full mb-4 p-5 rounded-[28px] bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)]/30 active:scale-[0.98] transition-all text-left shadow-sm"
                    >
                        <div className="flex justify-between items-center mb-3">
                            <span className="text-[10px] font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest">Progreso hacia meta</span>
                            <span className="text-xl font-black text-[var(--md-sys-color-primary)]">{progressPct}%</span>
                        </div>
                        <div className="h-3 bg-[var(--md-sys-color-surface-container)] rounded-full overflow-hidden">
                            <div
                                className="h-full rounded-full bg-gradient-to-r from-[var(--md-sys-color-primary)] to-[var(--md-sys-color-primary-container)] transition-all duration-1000"
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
                        <div className="p-5 rounded-[28px] bg-[var(--md-sys-color-surface-container)]/30 border border-[var(--md-sys-color-outline-variant)]/20">
                            <p className="text-base font-black text-[var(--md-sys-color-on-surface)] leading-none uppercase tracking-tight">{activePlanName}</p>
                            <p className="text-[10px] font-black text-[var(--md-sys-color-on-surface-variant)]/50 mt-2 uppercase tracking-wide">Objetivo: {goalLabel}</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default NutritionHeroBanner;
