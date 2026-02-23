// components/home/MiniNutritionWidget.tsx
// Mini-widget de nutrición: calorías + macros compactos

import React, { useMemo } from 'react';
import { useAppState } from '../../contexts/AppContext';
import { calculateDailyCalorieGoal } from '../../utils/calorieFormulas';
import { UtensilsIcon, ChevronRightIcon } from '../icons';

const MACRO_COLORS = { protein: '#3b82f6', carbs: '#22c55e', fats: '#f59e0b' };

export const MiniNutritionWidget: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { nutritionLogs, settings } = useAppState();

    const todayStr = useMemo(() => new Date().toISOString().split('T')[0], []);

    const calorieGoal = useMemo(
        () => calculateDailyCalorieGoal(settings, settings.calorieGoalConfig),
        [settings]
    );

    const dailyTotals = useMemo(() => {
        const logs = nutritionLogs.filter(l => l.date?.startsWith(todayStr) && (l.status === 'consumed' || !l.status));
        const acc = { calories: 0, protein: 0, carbs: 0, fats: 0 };
        logs.forEach(log => {
            (log.foods || []).forEach((f: any) => {
                acc.calories += f.calories || 0;
                acc.protein += f.protein || 0;
                acc.carbs += f.carbs || 0;
                acc.fats += f.fats || 0;
            });
        });
        return acc;
    }, [nutritionLogs, todayStr]);

    const hasGoal = calorieGoal > 0;
    const caloriePct = hasGoal ? Math.min(1.2, dailyTotals.calories / calorieGoal) : 0;
    const proteinGoal = settings.dailyProteinGoal || 150;
    const carbsGoal = settings.dailyCarbGoal || 250;
    const fatGoal = settings.dailyFatGoal || 70;

    return (
        <button
            onClick={onNavigate}
            className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4 text-left hover:border-white/20 transition-colors group"
        >
            <div className="flex justify-between items-center mb-3">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                    <UtensilsIcon size={10} className="text-cyber-copper" /> Nutrición
                </span>
                <ChevronRightIcon size={14} className="text-zinc-500 group-hover:text-white transition-colors" />
            </div>
            {hasGoal ? (
                <>
                    <div className="flex justify-between items-baseline mb-2">
                        <span className="text-lg font-black font-mono text-white">
                            {Math.round(dailyTotals.calories)}
                            <span className="text-[10px] font-bold text-zinc-500 ml-1">/ {calorieGoal} kcal</span>
                        </span>
                    </div>
                    <div className="w-full h-1.5 bg-white/5 rounded-full overflow-hidden mb-3">
                        <div
                            className="h-full rounded-full transition-all duration-500"
                            style={{
                                width: `${Math.min(100, caloriePct * 100)}%`,
                                backgroundColor: caloriePct > 1 ? '#ef4444' : caloriePct < 0.9 ? '#22c55e' : '#eab308',
                            }}
                        />
                    </div>
                    <div className="flex gap-4">
                        <div className="flex flex-col items-center">
                            <div className="w-10 h-10 relative">
                                <svg className="w-10 h-10 -rotate-90" viewBox="0 0 40 40">
                                    <circle cx="20" cy="20" r="16" fill="none" stroke="rgba(255,255,255,0.1)" strokeWidth="4" />
                                    <circle
                                        cx="20"
                                        cy="20"
                                        r="16"
                                        fill="none"
                                        stroke={MACRO_COLORS.protein}
                                        strokeWidth="4"
                                        strokeDasharray={`${Math.min(100, (dailyTotals.protein / proteinGoal) * 100) * 1.005} 100`}
                                        strokeLinecap="round"
                                    />
                                </svg>
                                <span className="absolute inset-0 flex items-center justify-center text-[9px] font-mono font-black text-white">
                                    {Math.round(dailyTotals.protein)}
                                </span>
                            </div>
                            <span className="text-[8px] text-zinc-500 font-bold mt-0.5">P</span>
                        </div>
                        <div className="flex flex-col items-center">
                            <div className="w-10 h-10 relative">
                                <svg className="w-10 h-10 -rotate-90" viewBox="0 0 40 40">
                                    <circle cx="20" cy="20" r="16" fill="none" stroke="rgba(255,255,255,0.1)" strokeWidth="4" />
                                    <circle
                                        cx="20"
                                        cy="20"
                                        r="16"
                                        fill="none"
                                        stroke={MACRO_COLORS.carbs}
                                        strokeWidth="4"
                                        strokeDasharray={`${Math.min(100, (dailyTotals.carbs / carbsGoal) * 100) * 1.005} 100`}
                                        strokeLinecap="round"
                                    />
                                </svg>
                                <span className="absolute inset-0 flex items-center justify-center text-[9px] font-mono font-black text-white">
                                    {Math.round(dailyTotals.carbs)}
                                </span>
                            </div>
                            <span className="text-[8px] text-zinc-500 font-bold mt-0.5">C</span>
                        </div>
                        <div className="flex flex-col items-center">
                            <div className="w-10 h-10 relative">
                                <svg className="w-10 h-10 -rotate-90" viewBox="0 0 40 40">
                                    <circle cx="20" cy="20" r="16" fill="none" stroke="rgba(255,255,255,0.1)" strokeWidth="4" />
                                    <circle
                                        cx="20"
                                        cy="20"
                                        r="16"
                                        fill="none"
                                        stroke={MACRO_COLORS.fats}
                                        strokeWidth="4"
                                        strokeDasharray={`${Math.min(100, (dailyTotals.fats / fatGoal) * 100) * 1.005} 100`}
                                        strokeLinecap="round"
                                    />
                                </svg>
                                <span className="absolute inset-0 flex items-center justify-center text-[9px] font-mono font-black text-white">
                                    {Math.round(dailyTotals.fats)}
                                </span>
                            </div>
                            <span className="text-[8px] text-zinc-500 font-bold mt-0.5">G</span>
                        </div>
                    </div>
                </>
            ) : (
                <p className="text-[10px] text-zinc-500 font-mono">Configura tu plan en Nutrición</p>
            )}
        </button>
    );
};
