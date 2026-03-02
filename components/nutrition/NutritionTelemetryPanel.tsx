// components/nutrition/NutritionTelemetryPanel.tsx
// Anillos calorías + macros — estilo AugeTelemetryPanel, paleta Tú

import React from 'react';

const RING_RADIUS = 15;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

// Paleta Tú: emerald/amber/rose según cumplimiento
const getStatusStroke = (pct: number): string => {
    if (pct >= 90) return '#10b981';
    if (pct >= 70) return '#f59e0b';
    return '#f43f5e';
};

const getTextColor = (pct: number): string => {
    if (pct >= 90) return 'text-emerald-500';
    if (pct >= 70) return 'text-amber-500';
    return 'text-rose-500';
};

export interface NutritionTelemetryPanelProps {
    dailyCalories: number;
    calorieGoal: number;
    protein: number;
    proteinGoal: number;
    carbs: number;
    carbGoal: number;
    fats: number;
    fatGoal: number;
    hasCalorieGoal: boolean;
}

export const NutritionTelemetryPanel: React.FC<NutritionTelemetryPanelProps> = ({
    dailyCalories,
    calorieGoal,
    protein,
    proteinGoal,
    carbs,
    carbGoal,
    fats,
    fatGoal,
    hasCalorieGoal,
}) => {
    const calPct = hasCalorieGoal && calorieGoal > 0 ? Math.min(150, (dailyCalories / calorieGoal) * 100) : 0;
    const protPct = proteinGoal > 0 ? Math.min(150, (protein / proteinGoal) * 100) : 0;
    const carbPct = carbGoal > 0 ? Math.min(150, (carbs / carbGoal) * 100) : 0;
    const fatPct = fatGoal > 0 ? Math.min(150, (fats / fatGoal) * 100) : 0;

    const rings = [
        { id: 'cal', label: 'kcal', pct: calPct, value: dailyCalories, goal: calorieGoal },
        { id: 'prot', label: 'P', pct: protPct, value: protein, goal: proteinGoal },
        { id: 'carb', label: 'C', pct: carbPct, value: carbs, goal: carbGoal },
        { id: 'fat', label: 'G', pct: fatPct, value: fats, goal: fatGoal },
    ];

    return (
        <div className="grid grid-cols-4 gap-2">
            {rings.map(({ id, label, pct, value, goal }) => {
                const strokeColor = getStatusStroke(Math.min(100, pct));
                const textColor = getTextColor(Math.min(100, pct));
                const dashOffset = Math.min(1, pct / 100) * RING_CIRCUMFERENCE;
                return (
                    <div key={id} className="flex flex-col items-center">
                        <div className="relative w-12 h-12">
                            <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                                <circle r={RING_RADIUS} cx="18" cy="18" fill="none" stroke="#27272a" strokeWidth="2.5" />
                                <circle
                                    r={RING_RADIUS}
                                    cx="18"
                                    cy="18"
                                    fill="none"
                                    stroke={strokeColor}
                                    strokeWidth="2.5"
                                    strokeDasharray={`${dashOffset} ${RING_CIRCUMFERENCE}`}
                                    strokeLinecap="round"
                                    className="transition-all duration-500"
                                />
                            </svg>
                            <div className="absolute inset-0 flex items-center justify-center">
                                <span className={`text-xs font-black font-mono tabular-nums ${textColor}`}>
                                    {Math.round(value)}
                                </span>
                            </div>
                        </div>
                        <span className="text-[8px] font-bold text-zinc-500 uppercase tracking-wider mt-0.5">
                            {label}
                        </span>
                        {goal > 0 && (
                            <span className="text-[7px] text-zinc-600 font-mono">{Math.round(goal)}</span>
                        )}
                    </div>
                );
            })}
        </div>
    );
};
