// components/home/cards/ProgresoFisicoCards.tsx
// Material 3 — Cards de progreso físico y alimentación
// Figma-exact: typography Roboto font-normal/medium, progress bars 8dp thick
// Bar colors: Calorías=Schemes-Primary, Carbs=teal-400, Proteínas=pink-500, Grasas=green-500

import React, { useMemo } from 'react';
import { SquareCard } from '../SquareCard';
import { useAppState } from '../../../contexts/AppContext';
import { calculateDailyCalorieGoal } from '../../../utils/calorieFormulas';
import { getLocalDateString } from '../../../utils/dateUtils';
import { TargetIcon, UtensilsIcon, RulerIcon, BarChartIcon } from '../../icons';

// ─── MacrosWidgetCard: Figma-exact nutrition rows ───────────────────────────

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

    const hasNutrition = !!activePlan || dailyTotals.calories > 0;
    const lastLog = bodyProgress.length > 0 ? bodyProgress[bodyProgress.length - 1] : null;
    const hasBody = (lastLog?.weight != null) || (settings?.userVitals?.weight != null);
    const isEmpty = !hasNutrition && !hasBody;

    const macroGoals = useMemo(() => {
        if (!activePlan) return { carbs: 133, protein: 100, fats: 55 };
        const plan = activePlan as any;
        return {
            carbs: plan.macros?.carbs || plan.macroGoals?.carbs || 133,
            protein: plan.macros?.protein || plan.macroGoals?.protein || 100,
            fats: plan.macros?.fats || plan.macroGoals?.fats || 55,
        };
    }, [activePlan]);

    if (isEmpty) {
        return (
            <button onClick={onNavigate}
                className="w-full bg-white rounded-[20px] p-4 text-left shadow-[0_1px_3px_rgba(0,0,0,0.06)] hover:shadow-[0_2px_8px_rgba(0,0,0,0.1)] transition-all">
                <span className="text-xs font-medium text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-wider font-['Roboto']">Progreso físico</span>
                <p className="text-sm font-normal text-[var(--md-sys-color-on-surface-variant)] mt-1.5 font-['Roboto'] leading-5 tracking-tight">Configura tu plan de nutrición</p>
            </button>
        );
    }

    const calPct = calorieGoal > 0 ? Math.min(100, (dailyTotals.calories / calorieGoal) * 100) : 0;

    // Figma colors: Carbs=teal-400, Proteínas=pink-500, Grasas=green-500
    const macros = [
        { label: 'Carbohidratos', current: Math.round(dailyTotals.carbs), goal: macroGoals.carbs, color: '#2dd4bf' },  // teal-400
        { label: 'Proteínas', current: Math.round(dailyTotals.protein), goal: macroGoals.protein, color: '#ec4899' },   // pink-500
        { label: 'Grasas', current: Math.round(dailyTotals.fats), goal: macroGoals.fats, color: '#22c55e' },            // green-500
    ];

    return (
        <button onClick={onNavigate} className="w-full text-left">
            {/* Calorías de hoy — Figma: text-base font-normal tracking-wide + values on right */}
            <div className="flex items-baseline justify-between mb-1">
                <span className="text-[var(--md-sys-color-on-surface)] text-base font-normal font-['Roboto'] leading-6 tracking-wide">Calorías de hoy</span>
                <span className="text-[var(--md-sys-color-on-surface)] text-base font-normal font-['Roboto'] leading-6 tracking-wide tabular-nums">{Math.round(dailyTotals.calories)}/{calorieGoal || '--'} kcal</span>
            </div>
            {/* Figma progress bar: 8dp thick, Schemes-Primary color */}
            <div className="w-full h-2 bg-[var(--md-sys-color-secondary-container,#E0D8D5)] rounded-full mb-3 overflow-hidden">
                <div className="h-full rounded-full bg-[var(--md-sys-color-primary)] transition-all duration-700" style={{ width: `${calPct}%` }} />
            </div>

            {/* Macros — Figma: text-xs font-medium tracking-wide + colored progress bars */}
            <div className="space-y-2">
                {macros.map(m => {
                    const pct = m.goal > 0 ? Math.min(100, (m.current / m.goal) * 100) : 0;
                    return (
                        <div key={m.label}>
                            <div className="flex items-baseline justify-between mb-0.5">
                                <span className="text-[var(--md-sys-color-on-surface)] text-xs font-medium font-['Roboto'] leading-4 tracking-wide">{m.label}</span>
                                <span className="text-[var(--md-sys-color-on-surface)] text-xs font-medium font-['Roboto'] leading-4 tracking-wide tabular-nums">{m.current}g/{m.goal}g</span>
                            </div>
                            {/* Figma: 8dp progress bar (h-2) */}
                            <div className="w-full h-2 bg-[var(--md-sys-color-secondary-container,#E0D8D5)] rounded-full overflow-hidden">
                                <div className="h-full rounded-full transition-all duration-700" style={{ width: `${pct}%`, backgroundColor: m.color }} />
                            </div>
                        </div>
                    );
                })}
            </div>
        </button>
    );
};

// ─── Grid Cards (Figma: w-24 h-24 with text-sm font-medium labels) ─────────

export const EvolutionCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { bodyProgress, settings, nutritionPlans, activeNutritionPlanId } = useAppState();
    const activePlan = useMemo(() => nutritionPlans.find(p => p.id === activeNutritionPlanId) ?? null, [nutritionPlans, activeNutritionPlanId]);

    const { progressPct, label } = useMemo(() => {
        if (!activePlan || !bodyProgress.length) return { progressPct: null, label: '' };
        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const first = sorted[0], last = sorted[sorted.length - 1];
        const goal = activePlan.goalValue;
        let current: number | undefined, start: number | undefined;
        if (activePlan.goalType === 'weight') { current = last?.weight ?? settings.userVitals?.weight; start = first?.weight ?? current; }
        else if (activePlan.goalType === 'bodyFat') { current = last?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage; start = first?.bodyFatPercentage ?? current; }
        else if (activePlan.goalType === 'muscleMass') { current = last?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage; start = first?.muscleMassPercentage ?? current; }
        if (current == null || start == null || goal == null) return { progressPct: null, label: '' };
        const pct = Math.min(100, Math.max(0, Math.round((1 - Math.abs(current - goal) / Math.abs(goal - start)) * 100)));
        return { progressPct: pct, label: activePlan.goalType === 'weight' ? 'Peso' : activePlan.goalType === 'bodyFat' ? 'Grasa' : 'Músculo' };
    }, [activePlan, bodyProgress, settings.userVitals]);

    return (
        <SquareCard onClick={onNavigate} isEmpty={!activePlan || progressPct == null} emptyLabel="Configura plan para ver evolución">
            <TargetIcon size={14} className="text-sky-500 mb-1" />
            <span className="text-[var(--md-sys-color-on-surface)] text-sm font-medium font-['Roboto'] leading-5 tracking-tight line-clamp-2">Peso (kg)</span>
        </SquareCard>
    );
};

export const BodyMeasuresCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { bodyProgress } = useAppState();
    const { count, hasMeasurements } = useMemo(() => {
        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        return { count: sorted.filter(l => l.weight != null).length, hasMeasurements: sorted.some(l => l.measurements && Object.keys(l.measurements).length > 0) };
    }, [bodyProgress]);
    const isEmpty = count < 2 && !hasMeasurements;
    return (
        <SquareCard onClick={onNavigate} isEmpty={isEmpty} emptyLabel="Registra peso y medidas para ver evolución">
            <RulerIcon size={14} className="text-violet-500 mb-1" />
            <span className="text-[var(--md-sys-color-on-surface)] text-sm font-medium font-['Roboto'] leading-5 tracking-tight line-clamp-2">Medidas</span>
        </SquareCard>
    );
};

export const FFMIBMICard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { bodyProgress, settings } = useAppState();
    const count = useMemo(() => {
        const height = settings.userVitals?.height;
        return (!height || height <= 0) ? 0 : bodyProgress.filter(l => l.weight && l.bodyFatPercentage != null).length;
    }, [bodyProgress, settings.userVitals?.height]);
    const isEmpty = count < 2;
    return (
        <SquareCard onClick={onNavigate} isEmpty={isEmpty} emptyLabel="Configura estatura y registra peso + % grasa">
            <BarChartIcon size={14} className="text-emerald-500 mb-1" />
            <span className="text-[var(--md-sys-color-on-surface)] text-sm font-medium font-['Roboto'] leading-5 tracking-tight line-clamp-2">FFMI</span>
        </SquareCard>
    );
};

export const CaloriesHistoryCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { nutritionLogs } = useAppState();
    const { total, daysCount } = useMemo(() => {
        const last7 = nutritionLogs.filter((l: any) => l.date && (l.status === 'consumed' || !l.status))
            .sort((a: any, b: any) => new Date(b.date).getTime() - new Date(a.date).getTime()).slice(0, 7);
        let cal = 0;
        last7.forEach((log: any) => { (log.foods || []).forEach((f: any) => { cal += f.calories || 0; }); });
        return { total: cal, daysCount: last7.length };
    }, [nutritionLogs]);
    const isEmpty = daysCount === 0;
    return (
        <SquareCard onClick={onNavigate} isEmpty={isEmpty} emptyLabel="Registra comidas para ver historial">
            <UtensilsIcon size={14} className="text-amber-500 mb-1" />
            <span className="text-[var(--md-sys-color-on-surface)] text-sm font-medium font-['Roboto'] leading-5 tracking-tight line-clamp-2">% Grasa</span>
        </SquareCard>
    );
};
