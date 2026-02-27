// components/home/BodyProgressGoalWidget.tsx
// Widget de progreso corporal hacia meta: barra + CTA

import React, { useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { TargetIcon, ChevronRightIcon } from '../icons';

export const BodyProgressGoalWidget: React.FC<{
    onNavigate: () => void;
    onNavigateToSetup?: () => void;
}> = ({ onNavigate, onNavigateToSetup }) => {
    const { bodyProgress, settings, nutritionPlans, activeNutritionPlanId } = useAppState();
    const { setIsBodyLogModalOpen } = useAppDispatch();

    const activePlan = useMemo(
        () => nutritionPlans.find((p) => p.id === activeNutritionPlanId) ?? null,
        [nutritionPlans, activeNutritionPlanId]
    );

    const lastLog = useMemo(
        () => (bodyProgress.length > 0 ? bodyProgress[bodyProgress.length - 1] : null),
        [bodyProgress]
    );

    const { progressPct, label, currentStr, goalStr } = useMemo(() => {
        if (!activePlan) return { progressPct: null, label: '', currentStr: '', goalStr: '' };
        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const first = sorted[0];
        let current: number | undefined;
        let start: number | undefined;
        if (activePlan.goalType === 'weight') {
            current = lastLog?.weight ?? settings.userVitals?.weight;
            start = first?.weight ?? current;
            const goal = activePlan.goalValue;
            return {
                progressPct: current != null && start != null ? Math.min(100, Math.max(0, Math.round((1 - Math.abs(current - goal) / Math.abs(goal - start)) * 100))) : null,
                label: 'Peso',
                currentStr: current != null ? `${current} kg` : '--',
                goalStr: `${goal} kg`,
            };
        }
        if (activePlan.goalType === 'bodyFat') {
            current = lastLog?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage;
            start = first?.bodyFatPercentage ?? current;
            const goal = activePlan.goalValue;
            return {
                progressPct: current != null && start != null ? Math.min(100, Math.max(0, Math.round((1 - Math.abs(current - goal) / Math.abs(goal - start)) * 100))) : null,
                label: 'Grasa',
                currentStr: current != null ? `${current}%` : '--',
                goalStr: `${goal}%`,
            };
        }
        if (activePlan.goalType === 'muscleMass') {
            current = lastLog?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage;
            start = first?.muscleMassPercentage ?? current;
            const goal = activePlan.goalValue;
            return {
                progressPct: current != null && start != null ? Math.min(100, Math.max(0, Math.round((1 - Math.abs(current - goal) / Math.abs(goal - start)) * 100))) : null,
                label: 'Músculo',
                currentStr: current != null ? `${current}%` : '--',
                goalStr: `${goal}%`,
            };
        }
        return { progressPct: null, label: '', currentStr: '', goalStr: '' };
    }, [activePlan, lastLog, bodyProgress, settings.userVitals]);

    if (!activePlan) {
        const goTo = onNavigateToSetup ?? onNavigate;
        return (
            <button
                onClick={() => goTo()}
                className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4 text-left hover:border-white/20 transition-colors"
            >
                <div className="flex justify-between items-center">
                    <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                        <TargetIcon size={10} className="text-cyber-copper" /> Progreso corporal
                    </span>
                    <ChevronRightIcon size={14} className="text-zinc-500" />
                </div>
                <p className="text-[10px] text-zinc-500 mt-2">Configura un plan en Nutrición para ver tu progreso.</p>
            </button>
        );
    }

    return (
        <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4">
            <div className="flex justify-between items-center mb-3">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                    <TargetIcon size={10} className="text-cyber-copper" /> Progreso corporal
                </span>
                <button
                    onClick={(e) => {
                        e.stopPropagation();
                        onNavigate();
                    }}
                    className="text-zinc-500 hover:text-white transition-colors"
                >
                    <ChevronRightIcon size={14} />
                </button>
            </div>
            <div className="flex justify-between items-baseline mb-2">
                <span className="text-sm font-mono text-white">
                    {currentStr} → {goalStr}
                </span>
                {progressPct != null && (
                    <span className="text-xs font-bold text-zinc-500">{progressPct}%</span>
                )}
            </div>
            {progressPct != null && (
                <div className="w-full h-1.5 bg-white/5 rounded-full overflow-hidden mb-3">
                    <div
                        className="h-full bg-cyber-copper rounded-full transition-all"
                        style={{ width: `${progressPct}%` }}
                    />
                </div>
            )}
            <div className="flex gap-2">
                <button
                    onClick={(e) => {
                        e.stopPropagation();
                        setIsBodyLogModalOpen(true);
                    }}
                    className="flex-1 py-2 rounded-xl border border-white/10 bg-white/5 text-xs font-bold text-white hover:bg-white/10"
                >
                    Actualizar datos
                </button>
                <button
                    onClick={(e) => {
                        e.stopPropagation();
                        onNavigate();
                    }}
                    className="flex-1 py-2 rounded-xl border border-cyber-copper/30 bg-cyber-copper/10 text-xs font-bold text-cyber-copper"
                >
                    Ver progreso
                </button>
            </div>
        </div>
    );
};
