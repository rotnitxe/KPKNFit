// components/nutrition/NutritionDashboard.tsx
// Dashboard NERD: calorías híbridas, mini-donuts macros, métricas, gráficos

import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import type { NutritionLog, LoggedFood } from '../../types';
import { calculateDailyCalorieGoal, getBMRAndTDEE } from '../../utils/calorieFormulas';
import { getMicronutrientDeficiencies } from '../../services/nutritionRecoveryService';
import { UtensilsIcon, TrashIcon, ChevronDownIcon, ChevronUpIcon, ChevronRightIcon, AlertTriangleIcon } from '../icons';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, ReferenceLine } from 'recharts';
import { getDatePartFromString, formatDateForDisplay } from '../../utils/dateUtils';

const mealOrder: NutritionLog['mealType'][] = ['breakfast', 'lunch', 'dinner', 'snack'];
const mealNames: Record<NutritionLog['mealType'], string> = {
    breakfast: 'Desayuno',
    lunch: 'Almuerzo',
    dinner: 'Cena',
    snack: 'Snack',
};

// Paleta Tú: emerald/amber/rose según cumplimiento
const getMacroBarColor = (pct: number): string => {
    if (pct >= 90) return '#10b981';
    if (pct >= 70) return '#f59e0b';
    return '#f43f5e';
};

const MacroProgressBar: React.FC<{
    value: number;
    max: number;
    label: string;
    unit: string;
}> = ({ value, max, label, unit }) => {
    const percentage = max > 0 ? Math.min(100, (value / max) * 100) : 0;
    const color = getMacroBarColor(percentage);
    return (
        <div className="mb-3 last:mb-0">
            <div className="flex justify-between items-end mb-1.5">
                <span className="text-[9px] font-black text-[#49454F] uppercase tracking-widest">{label}</span>
                <span className="text-[10px] font-mono text-[#49454F]">
                    {Math.round(value)} <span className="text-zinc-600">/ {Math.round(max)} {unit}</span>
                </span>
            </div>
            <div className="w-full bg-white/5 rounded-none h-1.5 relative overflow-hidden">
                <div
                    className="h-full rounded-none transition-all duration-500"
                    style={{ width: `${percentage}%`, backgroundColor: color }}
                />
            </div>
        </div>
    );
};

export const useNutritionStats = (selectedDate: string) => {
    const { nutritionLogs, settings } = useAppState();
    const calorieGoal = useMemo(() => calculateDailyCalorieGoal(settings, settings.calorieGoalConfig), [settings]);
    const logsForDate = useMemo(() => nutritionLogs.filter(log => log.date && log.date.startsWith(selectedDate)), [nutritionLogs, selectedDate]);
    const consumedLogs = useMemo(() => logsForDate.filter(l => l.status === 'consumed' || !l.status), [logsForDate]);
    const dailyTotals = useMemo(() => {
        const acc = { calories: 0, protein: 0, carbs: 0, fats: 0 };
        consumedLogs.forEach(log => {
            (log.foods || []).forEach((food: LoggedFood) => {
                acc.calories += food.calories || 0;
                acc.protein += food.protein || 0;
                acc.carbs += food.carbs || 0;
                acc.fats += food.fats || 0;
            });
        });
        return acc;
    }, [consumedLogs]);
    const proteinGoal = settings.dailyProteinGoal || 150;
    const carbGoal = settings.dailyCarbGoal || 250;
    const fatGoal = settings.dailyFatGoal || 70;
    return {
        dailyCalories: dailyTotals.calories,
        calorieGoal,
        hasCalorieGoal: calorieGoal > 0,
        dailyTotals,
        proteinGoal,
        carbGoal,
        fatGoal,
    };
};

export const NutritionDashboard: React.FC<{
    selectedDate: string;
    onDateChange: (date: string) => void;
    onOpenDrawer: () => void;
    showSetupBanner?: boolean;
    onOpenWizard?: () => void;
    hideHeader?: boolean;
    activePlan?: { id: string; name: string; goalType: string; goalValue: number; estimatedEndDate?: string } | null;
    onUpdateBodyData?: () => void;
    analyticsExpanded?: boolean;
    onAnalyticsExpand?: () => void;
    renderAnalytics?: () => React.ReactNode;
}> = ({
    selectedDate,
    onDateChange,
    onOpenDrawer,
    showSetupBanner,
    onOpenWizard,
    hideHeader,
    activePlan,
    onUpdateBodyData,
    analyticsExpanded = false,
    onAnalyticsExpand,
    renderAnalytics,
}) => {
    const { nutritionLogs, settings } = useAppState();
    const { setNutritionLogs, addToast } = useAppDispatch();
    const [fatExpanded, setFatExpanded] = useState(false);
    const [mealsExpanded, setMealsExpanded] = useState<Record<string, boolean>>({});

    const calorieGoal = useMemo(
        () => calculateDailyCalorieGoal(settings, settings.calorieGoalConfig),
        [settings]
    );

    const logsForDate = useMemo(
        () => nutritionLogs.filter(log => log.date && log.date.startsWith(selectedDate)),
        [nutritionLogs, selectedDate]
    );

    const consumedLogs = useMemo(
        () => logsForDate.filter(l => l.status === 'consumed' || !l.status),
        [logsForDate]
    );

    const dailyTotals = useMemo(() => {
        const acc = {
            calories: 0,
            protein: 0,
            carbs: 0,
            fats: 0,
            fatBreakdown: { saturated: 0, monounsaturated: 0, polyunsaturated: 0, trans: 0 },
            micronutrients: [] as { name: string; amount: number; unit: string }[],
        };
        consumedLogs.forEach(log => {
            (log.foods || []).forEach((food: LoggedFood) => {
                acc.calories += food.calories || 0;
                acc.protein += food.protein || 0;
                acc.carbs += food.carbs || 0;
                acc.fats += food.fats || 0;
                if (food.fatBreakdown) {
                    acc.fatBreakdown.saturated += food.fatBreakdown.saturated || 0;
                    acc.fatBreakdown.monounsaturated += food.fatBreakdown.monounsaturated || 0;
                    acc.fatBreakdown.polyunsaturated += food.fatBreakdown.polyunsaturated || 0;
                    acc.fatBreakdown.trans += food.fatBreakdown.trans || 0;
                }
                (food.micronutrients || []).forEach(m => {
                    const existing = acc.micronutrients.find(x => x.name === m.name);
                    if (existing) existing.amount += m.amount;
                    else acc.micronutrients.push({ ...m });
                });
            });
        });
        return acc;
    }, [consumedLogs]);

    const groupedLogs = useMemo(() => {
        const g: Record<NutritionLog['mealType'], NutritionLog[]> = {} as any;
        mealOrder.forEach(m => (g[m] = []));
        consumedLogs.forEach(log => {
            if (g[log.mealType]) g[log.mealType].push(log);
        });
        return g;
    }, [consumedLogs]);

    const micronutrientDeficiencies = useMemo(
        () => getMicronutrientDeficiencies(dailyTotals.micronutrients),
        [dailyTotals.micronutrients]
    );

    const hasCalorieGoal = calorieGoal > 0;

    const { bmr, tdee } = useMemo(() => getBMRAndTDEE(settings, settings.calorieGoalConfig), [settings]);
    const deficitSurplus = hasCalorieGoal ? dailyTotals.calories - calorieGoal : 0;
    const weightKg = settings.userVitals?.weight;
    const proteinPerKg = weightKg && weightKg > 0 ? (dailyTotals.protein / weightKg).toFixed(1) : null;
    const fiberToday = useMemo(() => {
        const fromMicro = dailyTotals.micronutrients.find(m => /fiber|fibra/i.test(m.name));
        if (fromMicro) return fromMicro.amount;
        return consumedLogs.flatMap(l => (l.foods || []).map(f => (f as any).carbBreakdown?.fiber ?? 0)).reduce((a, b) => a + b, 0) || 0;
    }, [consumedLogs, dailyTotals.micronutrients]);

    const trendChartData = useMemo(() => {
        const dailyCalories: Record<string, number> = {};
        nutritionLogs.forEach(log => {
            const date = getDatePartFromString(log.date);
            const totalCals = log.foods.reduce((sum, food) => sum + (food.calories || 0), 0);
            dailyCalories[date] = (dailyCalories[date] || 0) + totalCals;
        });
        return Object.entries(dailyCalories)
            .sort(([a], [b]) => new Date(a).getTime() - new Date(b).getTime())
            .slice(-14)
            .map(([date, cal]) => ({ date: formatDateForDisplay(date, { day: '2-digit', month: 'short' }), Calorías: Math.round(cal) }));
    }, [nutritionLogs]);

    const macroPieData = useMemo(() => {
        const total = dailyTotals.protein + dailyTotals.carbs + dailyTotals.fats;
        if (total === 0) return [];
        return [
            { name: 'Proteínas', value: dailyTotals.protein, color: '#10b981' },
            { name: 'Carbohidratos', value: dailyTotals.carbs, color: '#f59e0b' },
            { name: 'Grasas', value: dailyTotals.fats, color: '#f43f5e' },
        ];
    }, [dailyTotals]);

    const handleDelete = (logId: string) => {
        if (window.confirm('¿Eliminar registro?')) {
            setNutritionLogs(prev => prev.filter(log => log.id !== logId));
            addToast('Registro eliminado.', 'success');
        }
    };

    const toggleMeal = (mealType: string) => setMealsExpanded(prev => ({ ...prev, [mealType]: !prev[mealType] }));

    return (
        <div className="space-y-4">
            {showSetupBanner && onOpenWizard && (
                <button
                    onClick={onOpenWizard}
                    className="w-full py-4 text-left hover:bg-white/[0.03] transition-colors rounded-none -mx-1 px-1"
                >
                    <span className="text-[9px] font-black text-[#49454F] uppercase tracking-wider">Plan de alimentación</span>
                    <p className="text-[10px] text-[#49454F] mt-1">Configura tu plan de nutrición</p>
                </button>
            )}
            {!hideHeader && (
                <header className="flex justify-between items-center">
                    <div>
                        <h1 className="text-[10px] font-black text-[#49454F] uppercase tracking-[0.2em] font-mono">
                            Nutrición
                        </h1>
                        <input
                            type="date"
                            value={selectedDate}
                            onChange={e => onDateChange(e.target.value)}
                            className="bg-transparent border-none text-[#49454F] text-[9px] font-mono mt-0.5 focus:ring-0 p-0"
                        />
                    </div>
                </header>
            )}

            {/* Hoy — métricas integradas sin tarjetas */}
            <section className="space-y-3">
                <h2 className="text-[10px] font-black uppercase tracking-widest text-[#49454F]">
                    Hoy
                </h2>
                <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-[10px]">
                    <div className="flex justify-between items-baseline py-1.5 border-b border-[#E6E0E9]">
                        <span className="text-[#49454F] font-medium">TMB</span>
                        {bmr != null ? <span className="font-mono text-emerald-400">{Math.round(bmr)} kcal</span> : <span className="text-zinc-600">—</span>}
                    </div>
                    <div className="flex justify-between items-baseline py-1.5 border-b border-[#E6E0E9]">
                        <span className="text-[#49454F] font-medium">TDEE</span>
                        {tdee != null ? <span className="font-mono text-emerald-400">{tdee} kcal</span> : <span className="text-zinc-600">—</span>}
                    </div>
                    <div className="flex justify-between items-baseline py-1.5 border-b border-[#E6E0E9]">
                        <span className="text-[#49454F] font-medium">Déficit/Superávit</span>
                        {hasCalorieGoal ? <span className={`font-mono ${deficitSurplus >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{deficitSurplus >= 0 ? '+' : ''}{deficitSurplus} kcal</span> : <span className="text-zinc-600">—</span>}
                    </div>
                    <div className="flex justify-between items-baseline py-1.5 border-b border-[#E6E0E9]">
                        <span className="text-[#49454F] font-medium">Fibra</span>
                        <span className="font-mono text-emerald-400">{fiberToday.toFixed(0)} g</span>
                    </div>
                </div>
            </section>

            {/* Estado nutrición — barras macros integradas */}
            <section className="space-y-3 pt-2">
                <p className="text-[9px] font-black text-[#49454F] uppercase tracking-widest">
                    Estado nutrición
                </p>
                <div className="space-y-3">
                    <MacroProgressBar value={dailyTotals.protein} max={settings.dailyProteinGoal || 150} label="Proteínas" unit="g" />
                    <MacroProgressBar value={dailyTotals.carbs} max={settings.dailyCarbGoal || 250} label="Carbohidratos" unit="g" />
                    <div>
                        <button onClick={() => setFatExpanded(!fatExpanded)} className="w-full flex justify-between items-center mb-1.5">
                            <span className="text-[9px] font-black text-[#49454F] uppercase tracking-widest">Grasas</span>
                            {fatExpanded ? <ChevronUpIcon size={12} className="text-[#49454F]" /> : <ChevronDownIcon size={12} className="text-[#49454F]" />}
                        </button>
                        <div className="flex justify-between items-end mb-1.5">
                            <span />
                            <span className="text-[10px] font-mono text-[#49454F]">{Math.round(dailyTotals.fats)} / {Math.round(settings.dailyFatGoal || 70)} g</span>
                        </div>
                        <div className="w-full bg-white/5 rounded-none h-1.5 overflow-hidden">
                            <div className="h-full rounded-none transition-all" style={{ width: `${Math.min(100, (dailyTotals.fats / (settings.dailyFatGoal || 70)) * 100)}%`, backgroundColor: getMacroBarColor(Math.min(100, (dailyTotals.fats / (settings.dailyFatGoal || 70)) * 100)) }} />
                        </div>
                        {fatExpanded && (
                            <div className="mt-3 pl-2 border-l-2 border-[#E6E0E9] space-y-1">
                                <p className="text-[9px] text-[#49454F]">Saturadas: {dailyTotals.fatBreakdown.saturated.toFixed(1)}g</p>
                                <p className="text-[9px] text-[#49454F]">Mono: {dailyTotals.fatBreakdown.monounsaturated.toFixed(1)}g</p>
                                <p className="text-[9px] text-[#49454F]">Poli: {dailyTotals.fatBreakdown.polyunsaturated.toFixed(1)}g</p>
                                <p className="text-[9px] text-[#49454F]">Trans: {dailyTotals.fatBreakdown.trans.toFixed(1)}g</p>
                            </div>
                        )}
                    </div>
                </div>
                {proteinPerKg && (
                    <div className="flex justify-between items-baseline pt-1 text-[10px] border-t border-[#E6E0E9]">
                        <span className="text-[#49454F]">Proteína/kg</span>
                        <span className="font-mono text-emerald-400">{proteinPerKg} g</span>
                    </div>
                )}
            </section>

            {/* Gráfico tendencia */}
            {trendChartData.length >= 2 && (
                <section className="space-y-3 pt-2">
                    <p className="text-[9px] font-black text-[#49454F] uppercase tracking-widest">
                        Tendencia calorías
                    </p>
                    <ResponsiveContainer width="100%" height={160}>
                        <BarChart data={trendChartData}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                            <XAxis dataKey="date" stroke="#94a3b8" fontSize={10} />
                            <YAxis stroke="#94a3b8" fontSize={10} unit=" kcal" />
                            <Tooltip contentStyle={{ backgroundColor: '#0a0a0a', border: '1px solid rgba(16,185,129,0.3)' }} />
                            <Bar dataKey="Calorías" fill="#10b981" radius={[2, 2, 0, 0]} />
                            {hasCalorieGoal && <ReferenceLine y={calorieGoal} stroke="#10b981" strokeDasharray="3 3" />}
                        </BarChart>
                    </ResponsiveContainer>
                </section>
            )}

            {/* Gráfico distribución macros */}
            {macroPieData.length > 0 && macroPieData.some(d => d.value > 0) && (
                <section className="space-y-3 pt-2">
                    <p className="text-[9px] font-black text-[#49454F] uppercase tracking-widest">
                        Distribución macros
                    </p>
                    <ResponsiveContainer width="100%" height={120}>
                        <PieChart>
                            <Pie data={macroPieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={45} label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}>
                                {macroPieData.map((e, i) => <Cell key={i} fill={e.color} />)}
                            </Pie>
                            <Tooltip contentStyle={{ backgroundColor: '#0a0a0a', border: '1px solid rgba(16,185,129,0.3)' }} formatter={(v: number) => `${v.toFixed(0)}g`} />
                        </PieChart>
                    </ResponsiveContainer>
                </section>
            )}

            {/* Micronutrientes */}
            {dailyTotals.micronutrients.length > 0 && (
                <section className="space-y-3 pt-2">
                    <p className="text-[9px] font-black text-[#49454F] uppercase tracking-widest">
                        Micronutrientes
                    </p>
                    <div className="flex flex-wrap gap-2">
                        {dailyTotals.micronutrients.map((m, i) => (
                            <span
                                key={i}
                                className="text-[10px] font-mono text-[#49454F]"
                            >
                                {m.name}: {m.amount.toFixed(0)}{m.unit}
                            </span>
                        ))}
                    </div>
                </section>
            )}

            {/* Reporte de micronutrientes faltantes */}
            {micronutrientDeficiencies.length > 0 && (
                <section className="space-y-3 pt-2 pl-2 border-l-2 border-amber-500/30">
                    <p className="text-[9px] font-black text-amber-500 uppercase tracking-widest flex items-center gap-2">
                        <AlertTriangleIcon size={12} />
                        Posibles carencias hoy
                    </p>
                    <div className="space-y-1.5">
                        {micronutrientDeficiencies.map((d, i) => (
                            <div key={i} className="flex justify-between items-center text-[10px]">
                                <span className="text-amber-300 font-medium">{d.name}</span>
                                <span className="text-[#49454F] font-mono">
                                    {d.amount.toFixed(0)}{d.unit} ({d.pct}%)
                                </span>
                            </div>
                        ))}
                    </div>
                </section>
            )}

            {/* Comidas del día — timeline vertical */}
            <section className="space-y-0 pt-2">
                <p className="text-[9px] font-black text-[#49454F] uppercase tracking-widest mb-3">
                    Comidas del día
                </p>
                {consumedLogs.length > 0 ? (
                    <div className="relative pl-4 border-l-2 border-[#E6E0E9]">
                        {mealOrder.map((mealType) => {
                            const logs = groupedLogs[mealType] || [];
                            if (logs.length === 0) return null;
                            const isExpanded = mealsExpanded[mealType] ?? true;
                            return (
                                <div key={mealType} className="relative -ml-[9px]">
                                    <div className="absolute left-0 top-3 w-3 h-3 rounded-full bg-emerald-500/80 border-2 border-[#121212]" />
                                    <div className="pl-5 pb-6 last:pb-0">
                                        <button
                                            onClick={() => toggleMeal(mealType)}
                                            className="flex items-center justify-between w-full text-left mb-1"
                                        >
                                            <span className="text-[10px] font-black text-white uppercase tracking-widest">
                                                {mealNames[mealType]}
                                            </span>
                                            {isExpanded ? <ChevronDownIcon size={12} className="text-[#49454F]" /> : <ChevronRightIcon size={12} className="text-[#49454F]" />}
                                        </button>
                                        {isExpanded && (
                                            <div className="space-y-2 pt-1">
                                                {logs.map(log => {
                                                    const cals = log.foods.reduce((s, f) => s + (f.calories || 0), 0);
                                                    const prot = log.foods.reduce((s, f) => s + (f.protein || 0), 0);
                                                    return (
                                                        <div key={log.id} className="flex justify-between items-start gap-2 group py-1">
                                                            <div className="min-w-0 flex-1">
                                                                {log.foods.map(f => (
                                                                    <p key={f.id} className="text-xs text-white font-medium truncate">
                                                                        {f.foodName}{' '}
                                                                        <span className="text-[#49454F]">({f.amount}{f.unit})</span>
                                                                    </p>
                                                                ))}
                                                            </div>
                                                            <div className="flex items-center gap-1 shrink-0">
                                                                <span className="text-[10px] font-mono text-[#49454F]">
                                                                    {Math.round(cals)} kcal · {prot.toFixed(0)}g P
                                                                </span>
                                                                <button onClick={() => handleDelete(log.id)} className="text-zinc-600 hover:text-rose-400 p-1" aria-label="Eliminar">
                                                                    <TrashIcon size={12} />
                                                                </button>
                                                            </div>
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <div className="py-8 text-center">
                        <UtensilsIcon size={24} className="mx-auto text-zinc-600 mb-2" />
                        <p className="text-[10px] text-zinc-600 font-bold uppercase tracking-widest">Sin registros</p>
                    </div>
                )}
            </section>

            {/* Sección Plan */}
            {activePlan && (
                <section className="space-y-3 pt-2">
                    <p className="text-[9px] font-black text-[#49454F] uppercase tracking-widest mb-3">
                        Plan activo
                    </p>
                    <p className="text-sm font-black text-white uppercase tracking-tight">{activePlan.name}</p>
                    <p className="text-xs text-[#49454F] mt-1">
                        Objetivo: {activePlan.goalType === 'weight' ? `${activePlan.goalValue} kg` : activePlan.goalType === 'bodyFat' ? `${activePlan.goalValue}% grasa` : `${activePlan.goalValue}% músculo`}
                    </p>
                    {activePlan.estimatedEndDate && (
                        <p className="text-xs text-[#49454F] mt-1 font-mono">Fecha est.: {activePlan.estimatedEndDate}</p>
                    )}
                    {onUpdateBodyData && (
                        <button
                            onClick={onUpdateBodyData}
                            className="mt-3 w-full py-2.5 rounded-none border border-[#E6E0E9] bg-white/[0.03] text-zinc-300 font-bold text-xs hover:bg-white/5 transition-colors"
                        >
                            Actualizar datos corporales
                        </button>
                    )}
                </section>
            )}

            {onAnalyticsExpand && renderAnalytics && (
                <section className="space-y-0 pt-2">
                    <button
                        onClick={onAnalyticsExpand}
                        className="w-full flex items-center justify-between py-3 text-left hover:bg-white/[0.03] transition-colors"
                    >
                        <span className="text-[9px] font-black text-[#49454F] uppercase tracking-widest">Analytics</span>
                        {analyticsExpanded ? <ChevronDownIcon size={14} className="text-[#49454F]" /> : <ChevronRightIcon size={14} className="text-[#49454F]" />}
                    </button>
                    {analyticsExpanded && (
                        <div className="pb-4 pt-2 space-y-6 border-t border-[#E6E0E9] mt-2">
                            {renderAnalytics()}
                        </div>
                    )}
                </section>
            )}
        </div>
    );
};
