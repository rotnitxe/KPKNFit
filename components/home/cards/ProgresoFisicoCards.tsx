// components/home/cards/ProgresoFisicoCards.tsx
// Tarjetas: macros+composición, evolución hacia meta, historial calorías

import React, { useMemo } from 'react';
import { SquareCard } from '../SquareCard';
import { useAppState } from '../../../contexts/AppContext';
import { calculateDailyCalorieGoal } from '../../../utils/calorieFormulas';
import { getLocalDateString } from '../../../utils/dateUtils';
import { TargetIcon, UtensilsIcon, RulerIcon, BarChartIcon } from '../../icons';

export const MacrosWidgetCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { nutritionPlans, activeNutritionPlanId, nutritionLogs, bodyProgress, settings } = useAppState();

    const activePlan = useMemo(
        () => nutritionPlans.find((p) => p.id === activeNutritionPlanId) ?? null,
        [nutritionPlans, activeNutritionPlanId]
    );

    const todayStr = getLocalDateString();
    const dailyTotals = useMemo(() => {
        const logs = nutritionLogs.filter(l => l.date?.startsWith(todayStr) && (l.status === 'consumed' || !l.status));
        const acc = { calories: 0, protein: 0, carbs: 0, fats: 0 };
        logs.forEach((log: any) => {
            (log.foods || []).forEach((f: any) => {
                acc.calories += f.calories || 0;
                acc.protein += f.protein || 0;
                acc.carbs += f.carbs || 0;
                acc.fats += f.fats || 0;
            });
        });
        return acc;
    }, [nutritionLogs, todayStr]);

    const calorieGoal = useMemo(
        () => calculateDailyCalorieGoal(settings, settings.calorieGoalConfig),
        [settings]
    );

    const lastLog = bodyProgress.length > 0 ? bodyProgress[bodyProgress.length - 1] : null;
    const hasNutrition = !!activePlan || dailyTotals.calories > 0;
    const hasBody = (lastLog?.weight != null) || (settings?.userVitals?.weight != null);

    const isEmpty = !hasNutrition && !hasBody;

    if (isEmpty) {
        return (
            <button
                onClick={onNavigate}
                className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4 text-left hover:border-white/20 transition-colors"
            >
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-wider">Progreso físico</span>
                <p className="text-[10px] text-zinc-500 mt-2">Configura tu plan de nutrición</p>
            </button>
        );
    }

    return (
        <button
            onClick={onNavigate}
            className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4 text-left hover:border-white/20 transition-colors"
        >
            <div className="flex justify-between items-center mb-2">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-wider flex items-center gap-2">
                    <UtensilsIcon size={10} /> <TargetIcon size={10} /> Macros · Composición
                </span>
            </div>
            {hasNutrition && (
                <div className="mb-2">
                    <span className="text-sm font-black text-white">{Math.round(dailyTotals.calories)}</span>
                    <span className="text-[10px] text-zinc-500 ml-1">/ {calorieGoal || '--'} kcal</span>
                </div>
            )}
            {hasBody && lastLog && (
                <span className="text-[10px] text-zinc-400">{lastLog.weight} kg</span>
            )}
        </button>
    );
};

export const EvolutionCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { bodyProgress, settings, nutritionPlans, activeNutritionPlanId } = useAppState();

    const activePlan = useMemo(
        () => nutritionPlans.find((p) => p.id === activeNutritionPlanId) ?? null,
        [nutritionPlans, activeNutritionPlanId]
    );

    const { progressPct, label } = useMemo(() => {
        if (!activePlan || !bodyProgress.length) return { progressPct: null, label: '' };
        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const first = sorted[0];
        const last = sorted[sorted.length - 1];
        const goal = activePlan.goalValue;
        let current: number | undefined, start: number | undefined;
        if (activePlan.goalType === 'weight') {
            current = last?.weight ?? settings.userVitals?.weight;
            start = first?.weight ?? current;
        } else if (activePlan.goalType === 'bodyFat') {
            current = last?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage;
            start = first?.bodyFatPercentage ?? current;
        } else if (activePlan.goalType === 'muscleMass') {
            current = last?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage;
            start = first?.muscleMassPercentage ?? current;
        }
        if (current == null || start == null || goal == null) return { progressPct: null, label: '' };
        const pct = Math.min(100, Math.max(0, Math.round((1 - Math.abs(current - goal) / Math.abs(goal - start)) * 100)));
        const lbl = activePlan.goalType === 'weight' ? 'Peso' : activePlan.goalType === 'bodyFat' ? 'Grasa' : 'Músculo';
        return { progressPct: pct, label: lbl };
    }, [activePlan, bodyProgress, settings.userVitals]);

    const isEmpty = !activePlan || progressPct == null;

    return (
        <SquareCard onClick={onNavigate} isEmpty={isEmpty} emptyLabel="Configura plan para ver evolución">
            <TargetIcon size={14} className="text-cyan-400 mb-1" />
            <span className="text-[9px] font-black text-zinc-500 uppercase tracking-wider">Evolución {label}</span>
            {progressPct != null && (
                <span className="text-sm font-black text-white mt-0.5">{progressPct}%</span>
            )}
        </SquareCard>
    );
};

export const BodyMeasuresCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { bodyProgress } = useAppState();

    const { count, hasMeasurements } = useMemo(() => {
        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const withWeight = sorted.filter(l => l.weight != null);
        const withCustom = sorted.filter(l => l.measurements && Object.keys(l.measurements).length > 0);
        return {
            count: withWeight.length,
            hasMeasurements: withCustom.length > 0,
        };
    }, [bodyProgress]);

    const isEmpty = count < 2 && !hasMeasurements;

    return (
        <SquareCard onClick={onNavigate} isEmpty={isEmpty} emptyLabel="Registra peso y medidas para ver evolución">
            <RulerIcon size={14} className="text-violet-400 mb-1" />
            <span className="text-[9px] font-black text-zinc-500 uppercase tracking-wider">Medidas corporales</span>
            {!isEmpty && (
                <span className="text-[10px] text-zinc-400 mt-0.5">{count} registros</span>
            )}
        </SquareCard>
    );
};

export const FFMIBMICard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { bodyProgress, settings } = useAppState();

    const count = useMemo(() => {
        const height = settings.userVitals?.height;
        if (!height || height <= 0) return 0;
        return bodyProgress.filter(l => l.weight && l.bodyFatPercentage != null).length;
    }, [bodyProgress, settings.userVitals?.height]);

    const isEmpty = count < 2;

    return (
        <SquareCard onClick={onNavigate} isEmpty={isEmpty} emptyLabel="Configura estatura y registra peso + % grasa">
            <BarChartIcon size={14} className="text-emerald-400 mb-1" />
            <span className="text-[9px] font-black text-zinc-500 uppercase tracking-wider">FFMI · IMC</span>
            {!isEmpty && (
                <span className="text-[10px] text-zinc-400 mt-0.5">{count} puntos</span>
            )}
        </SquareCard>
    );
};

export const CaloriesHistoryCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { nutritionLogs } = useAppState();

    const { total, daysCount } = useMemo(() => {
        const last7 = nutritionLogs
            .filter((l: any) => l.date && (l.status === 'consumed' || !l.status))
            .sort((a: any, b: any) => new Date(b.date).getTime() - new Date(a.date).getTime())
            .slice(0, 7);
        let cal = 0;
        last7.forEach((log: any) => {
            (log.foods || []).forEach((f: any) => { cal += f.calories || 0; });
        });
        return { total: cal, daysCount: last7.length };
    }, [nutritionLogs]);

    const isEmpty = daysCount === 0;

    return (
        <SquareCard onClick={onNavigate} isEmpty={isEmpty} emptyLabel="Registra comidas para ver historial">
            <UtensilsIcon size={14} className="text-amber-400 mb-1" />
            <span className="text-[9px] font-black text-zinc-500 uppercase tracking-wider">Historial calorías</span>
            <span className="text-[10px] text-zinc-400 mt-0.5">{daysCount} días · {Math.round(total)} kcal</span>
        </SquareCard>
    );
};
