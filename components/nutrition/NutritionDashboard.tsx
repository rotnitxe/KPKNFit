// components/nutrition/NutritionDashboard.tsx
// Dashboard NERD: calorías híbridas, mini-donuts macros, métricas, gráficos

import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import type { NutritionLog, LoggedFood } from '../../types';
import { calculateDailyCalorieGoal, getBMRAndTDEE } from '../../utils/calorieFormulas';
import { getMicronutrientDeficiencies } from '../../services/nutritionRecoveryService';
import { UtensilsIcon, TrashIcon, ChevronDownIcon, ChevronUpIcon, AlertTriangleIcon } from '../icons';
import { CalorieGoalCard } from './CalorieGoalCard';
import { NutritionPlanEditorModal } from './NutritionPlanEditorModal';
import { NutritionTooltip } from './NutritionTooltip';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, ReferenceLine } from 'recharts';
import { getDatePartFromString, formatDateForDisplay } from '../../utils/dateUtils';

const mealOrder: NutritionLog['mealType'][] = ['breakfast', 'lunch', 'dinner', 'snack'];
const mealNames: Record<NutritionLog['mealType'], string> = {
    breakfast: 'Desayuno',
    lunch: 'Almuerzo',
    dinner: 'Cena',
    snack: 'Snack',
};

const MACRO_COLORS = { protein: '#3b82f6', carbs: '#22c55e', fats: '#f59e0b' };
const MACRO_TOOLTIPS = {
    protein: 'Proteínas: esenciales para síntesis muscular, recuperación y saciedad. Objetivo típico: 1.6-2.2 g/kg.',
    carbs: 'Carbohidratos: principal fuente de energía para entrenamiento. Ajusta según actividad.',
    fats: 'Grasas: hormonas, absorción de vitaminas, saciedad. No bajar de 0.5 g/kg.',
};

const MacroMiniDonut: React.FC<{
    value: number;
    max: number;
    label: string;
    color: string;
    tooltip: string;
}> = ({ value, max, label, color, tooltip }) => {
    const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0;
    const circumference = 2 * Math.PI * 36;
    const strokeDash = (pct / 100) * circumference;
    return (
        <div className="flex flex-col items-center">
            <div className="relative w-20 h-20">
                <svg className="w-20 h-20 -rotate-90" viewBox="0 0 80 80">
                    <circle cx="40" cy="40" r="36" fill="none" stroke="rgba(255,255,255,0.1)" strokeWidth="6" />
                    <circle cx="40" cy="40" r="36" fill="none" stroke={color} strokeWidth="6" strokeDasharray={`${strokeDash} ${circumference}`} strokeLinecap="round" />
                </svg>
                <span className="absolute inset-0 flex items-center justify-center text-xs font-black text-white font-mono">{Math.round(value)}</span>
            </div>
            <span className="text-[9px] font-bold text-zinc-500 mt-1 flex items-center gap-0.5">
                {label}
                <NutritionTooltip content={tooltip} title={label} />
            </span>
        </div>
    );
};

const ProgressBar: React.FC<{
    value: number;
    max: number;
    label: string;
    unit: string;
    color: string;
}> = ({ value, max, label, unit, color }) => {
    const percentage = max > 0 ? Math.min(100, (value / max) * 100) : 0;
    return (
        <div className="mb-3 last:mb-0">
            <div className="flex justify-between items-end mb-1.5">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest">{label}</span>
                <span className="text-[10px] font-mono text-zinc-400">
                    {Math.round(value)} <span className="text-zinc-600">/ {Math.round(max)} {unit}</span>
                </span>
            </div>
            <div className="w-full bg-white/5 rounded-full h-1.5 relative overflow-hidden border border-white/5">
                <div
                    className="h-full rounded-full transition-all duration-500"
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
    const dailyCalories = useMemo(() => consumedLogs.reduce((acc, log) => acc + (log.foods || []).reduce((s: number, f: LoggedFood) => s + (f.calories || 0), 0), 0), [consumedLogs]);
    return { dailyCalories, calorieGoal, hasCalorieGoal: calorieGoal > 0 };
};

export const NutritionDashboard: React.FC<{
    selectedDate: string;
    onDateChange: (date: string) => void;
    onOpenDrawer: () => void;
    showSetupBanner?: boolean;
    onOpenWizard?: () => void;
    hideHeader?: boolean;
}> = ({ selectedDate, onDateChange, onOpenDrawer, showSetupBanner, onOpenWizard, hideHeader }) => {
    const { nutritionLogs, settings } = useAppState();
    const { setNutritionLogs, addToast } = useAppDispatch();
    const [fatExpanded, setFatExpanded] = useState(false);
    const [isGoalModalOpen, setIsGoalModalOpen] = useState(false);

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
    const calorieStatus = hasCalorieGoal ? dailyTotals.calories / calorieGoal : 1;
    const calorieColor = calorieStatus > 1.1 ? '#ef4444' : calorieStatus < 0.9 ? '#22c55e' : '#eab308';

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
            { name: 'Proteínas', value: dailyTotals.protein, color: MACRO_COLORS.protein },
            { name: 'Carbohidratos', value: dailyTotals.carbs, color: MACRO_COLORS.carbs },
            { name: 'Grasas', value: dailyTotals.fats, color: MACRO_COLORS.fats },
        ];
    }, [dailyTotals]);

    const handleDelete = (logId: string) => {
        if (window.confirm('¿Eliminar registro?')) {
            setNutritionLogs(prev => prev.filter(log => log.id !== logId));
            addToast('Registro eliminado.', 'success');
        }
    };

    return (
        <div className="space-y-4 pb-[max(120px,calc(90px+env(safe-area-inset-bottom,0px)+24px))]">
            {showSetupBanner && onOpenWizard && (
                <div className="bg-cyber-copper/10 border border-cyber-copper/30 rounded-xl p-4">
                    <p className="text-sm text-zinc-300 mb-2">Aún no has configurado tu plan de alimentación.</p>
                    <button
                        onClick={onOpenWizard}
                        className="text-sm font-bold text-cyber-copper hover:text-cyber-copper/80 transition-colors"
                    >
                        Configurar ahora
                    </button>
                </div>
            )}
            {!hideHeader && (
                <header className="flex justify-between items-center">
                    <div>
                        <h1 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] font-mono">
                            Nutrición
                        </h1>
                        <input
                            type="date"
                            value={selectedDate}
                            onChange={e => onDateChange(e.target.value)}
                            className="bg-transparent border-none text-zinc-400 text-[9px] font-mono mt-0.5 focus:ring-0 p-0"
                        />
                    </div>
                </header>
            )}

            {/* Card Calorías */}
            <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5">
                <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-1">
                    Calorías
                </p>
                {hasCalorieGoal ? (
                    <>
                        <div className="flex items-baseline gap-2">
                            <span className="text-4xl font-black text-white font-mono tracking-tight">
                                {Math.round(dailyTotals.calories)}
                            </span>
                            <span className="text-sm font-mono text-zinc-500">
                                / {calorieGoal} kcal
                            </span>
                        </div>
                        <div className="mt-2 h-2 bg-white/5 rounded-full overflow-hidden border border-white/5">
                            <div
                                className="h-full rounded-full transition-all duration-500"
                                style={{
                                    width: `${Math.min(100, (dailyTotals.calories / calorieGoal) * 100)}%`,
                                    backgroundColor: calorieColor,
                                }}
                            />
                        </div>
                    </>
                ) : (
                    <p className="text-sm text-zinc-500">Configura tu plan de alimentación para ver tu progreso.</p>
                )}
            </div>

            {/* Mini-donuts Macros */}
            <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5">
                <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-4">
                    Macros
                </p>
                <div className="flex justify-around gap-4">
                    <MacroMiniDonut
                        value={dailyTotals.protein}
                        max={settings.dailyProteinGoal || 150}
                        label="Proteínas"
                        color={MACRO_COLORS.protein}
                        tooltip={MACRO_TOOLTIPS.protein}
                    />
                    <MacroMiniDonut
                        value={dailyTotals.carbs}
                        max={settings.dailyCarbGoal || 250}
                        label="Carbohidratos"
                        color={MACRO_COLORS.carbs}
                        tooltip={MACRO_TOOLTIPS.carbs}
                    />
                    <MacroMiniDonut
                        value={dailyTotals.fats}
                        max={settings.dailyFatGoal || 70}
                        label="Grasas"
                        color={MACRO_COLORS.fats}
                        tooltip={MACRO_TOOLTIPS.fats}
                    />
                </div>
                <div className="mt-4 space-y-3">
                    <ProgressBar value={dailyTotals.protein} max={settings.dailyProteinGoal || 150} label="Proteínas" unit="g" color={MACRO_COLORS.protein} />
                    <ProgressBar value={dailyTotals.carbs} max={settings.dailyCarbGoal || 250} label="Carbohidratos" unit="g" color={MACRO_COLORS.carbs} />
                    <div>
                        <button onClick={() => setFatExpanded(!fatExpanded)} className="w-full flex justify-between items-center mb-1.5">
                            <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest">Grasas</span>
                            {fatExpanded ? <ChevronUpIcon size={12} className="text-zinc-500" /> : <ChevronDownIcon size={12} className="text-zinc-500" />}
                        </button>
                        <div className="flex justify-between items-end mb-1.5">
                            <span />
                            <span className="text-[10px] font-mono text-zinc-400">{Math.round(dailyTotals.fats)} / {Math.round(settings.dailyFatGoal || 70)} g</span>
                        </div>
                        <div className="w-full bg-white/5 rounded-full h-1.5 overflow-hidden border border-white/5">
                            <div className="h-full rounded-full transition-all" style={{ width: `${Math.min(100, (dailyTotals.fats / (settings.dailyFatGoal || 70)) * 100)}%`, backgroundColor: MACRO_COLORS.fats }} />
                        </div>
                        {fatExpanded && (
                            <div className="mt-3 pl-2 border-l-2 border-white/10 space-y-1">
                                <p className="text-[9px] text-zinc-500">Saturadas: {dailyTotals.fatBreakdown.saturated.toFixed(1)}g</p>
                                <p className="text-[9px] text-zinc-500">Mono: {dailyTotals.fatBreakdown.monounsaturated.toFixed(1)}g</p>
                                <p className="text-[9px] text-zinc-500">Poli: {dailyTotals.fatBreakdown.polyunsaturated.toFixed(1)}g</p>
                                <p className="text-[9px] text-zinc-500">Trans: {dailyTotals.fatBreakdown.trans.toFixed(1)}g</p>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Métricas */}
            <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5">
                <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-3">
                    Métricas
                </p>
                <div className="grid grid-cols-2 gap-3 text-[10px] font-mono">
                    {bmr != null && <div><span className="text-zinc-500">TMB</span><span className="text-cyber-copper ml-1">{Math.round(bmr)} kcal</span></div>}
                    {tdee != null && <div><span className="text-zinc-500">TDEE</span><span className="text-cyber-copper ml-1">{tdee} kcal</span></div>}
                    {hasCalorieGoal && <div><span className="text-zinc-500">Déficit/Superávit</span><span className={deficitSurplus >= 0 ? 'text-emerald-400' : 'text-red-400'}>{deficitSurplus >= 0 ? '+' : ''}{deficitSurplus} kcal</span></div>}
                    {proteinPerKg && <div><span className="text-zinc-500">Proteína/kg</span><span className="text-cyber-copper ml-1">{proteinPerKg} g</span></div>}
                    <div><span className="text-zinc-500">Fibra</span><span className="text-cyber-copper ml-1">{fiberToday.toFixed(0)} g</span></div>
                </div>
            </div>

            {/* Gráfico tendencia */}
            {trendChartData.length >= 2 && (
                <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5">
                    <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-3">
                        Tendencia calorías
                    </p>
                    <ResponsiveContainer width="100%" height={160}>
                        <BarChart data={trendChartData}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                            <XAxis dataKey="date" stroke="#94a3b8" fontSize={10} />
                            <YAxis stroke="#94a3b8" fontSize={10} unit=" kcal" />
                            <Tooltip contentStyle={{ backgroundColor: '#0a0a0a', border: '1px solid rgba(249,115,22,0.3)' }} />
                            <Bar dataKey="Calorías" fill="#FF7B00" radius={[2, 2, 0, 0]} />
                            {hasCalorieGoal && <ReferenceLine y={calorieGoal} stroke="#FF7B00" strokeDasharray="3 3" />}
                        </BarChart>
                    </ResponsiveContainer>
                </div>
            )}

            {/* Gráfico distribución macros */}
            {macroPieData.length > 0 && macroPieData.some(d => d.value > 0) && (
                <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5">
                    <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-3">
                        Distribución macros
                    </p>
                    <ResponsiveContainer width="100%" height={120}>
                        <PieChart>
                            <Pie data={macroPieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={45} label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}>
                                {macroPieData.map((e, i) => <Cell key={i} fill={e.color} />)}
                            </Pie>
                            <Tooltip contentStyle={{ backgroundColor: '#0a0a0a', border: '1px solid rgba(249,115,22,0.3)' }} formatter={(v: number) => `${v.toFixed(0)}g`} />
                        </PieChart>
                    </ResponsiveContainer>
                </div>
            )}

            {/* Micronutrientes */}
            {dailyTotals.micronutrients.length > 0 && (
                <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5">
                    <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-3">
                        Micronutrientes
                    </p>
                    <div className="flex flex-wrap gap-2">
                        {dailyTotals.micronutrients.map((m, i) => (
                            <span
                                key={i}
                                className="px-2 py-1 rounded-lg bg-white/5 text-[10px] font-mono text-zinc-400 border border-white/5"
                            >
                                {m.name}: {m.amount.toFixed(0)}{m.unit}
                            </span>
                        ))}
                    </div>
                </div>
            )}

            {/* Reporte de micronutrientes faltantes (al final del día) */}
            {micronutrientDeficiencies.length > 0 && (
                <div className="bg-amber-950/30 border border-amber-500/30 rounded-2xl p-5">
                    <p className="text-[9px] font-black text-amber-500 uppercase tracking-widest mb-3 flex items-center gap-2">
                        <AlertTriangleIcon size={12} />
                        Posibles carencias hoy
                    </p>
                    <p className="text-[10px] text-zinc-400 mb-3">
                        Estos micronutrientes están por debajo del 70% del valor diario recomendado:
                    </p>
                    <div className="space-y-2">
                        {micronutrientDeficiencies.map((d, i) => (
                            <div key={i} className="flex justify-between items-center text-[10px]">
                                <span className="text-amber-300 font-medium">{d.name}</span>
                                <span className="text-zinc-500 font-mono">
                                    {d.amount.toFixed(0)}{d.unit} ({d.pct}% del objetivo)
                                </span>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Card Objetivo */}
            <CalorieGoalCard
                calorieGoal={calorieGoal}
                onEditClick={() => setIsGoalModalOpen(true)}
            />
            <NutritionPlanEditorModal
                isOpen={isGoalModalOpen}
                onClose={() => setIsGoalModalOpen(false)}
            />

            {/* Lista comidas */}
            <div>
                <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-3">
                    Comidas del día
                </p>
                <div className="space-y-3">
                    {mealOrder.map(mealType => {
                        const logs = groupedLogs[mealType] || [];
                        if (logs.length === 0) return null;
                        return (
                            <div
                                key={mealType}
                                className="bg-[#0a0a0a] border border-white/5 rounded-xl overflow-hidden"
                            >
                                <div className="px-4 py-2 border-b border-white/5">
                                    <h4 className="text-[10px] font-black text-zinc-400 uppercase">
                                        {mealNames[mealType]}
                                    </h4>
                                </div>
                                <div className="divide-y divide-white/5">
                                    {logs.map(log => {
                                        const cals = log.foods.reduce((s, f) => s + f.calories, 0);
                                        const prot = log.foods.reduce((s, f) => s + f.protein, 0);
                                        return (
                                            <div
                                                key={log.id}
                                                className="p-3 flex justify-between items-start group"
                                            >
                                                <div>
                                                    {log.foods.map(f => (
                                                        <p key={f.id} className="text-sm text-white font-medium">
                                                            {f.foodName}{' '}
                                                            <span className="text-zinc-500 text-xs">
                                                                ({f.amount}
                                                                {f.unit})
                                                            </span>
                                                        </p>
                                                    ))}
                                                </div>
                                                <div className="flex items-center gap-2">
                                                    <span className="text-xs font-mono text-zinc-400">
                                                        {Math.round(cals)} kcal · {prot.toFixed(0)}g P
                                                    </span>
                                                    <button
                                                        onClick={() => handleDelete(log.id)}
                                                        className="text-zinc-600 hover:text-red-400 p-1 opacity-0 group-hover:opacity-100"
                                                    >
                                                        <TrashIcon size={14} />
                                                    </button>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        );
                    })}
                </div>
                {consumedLogs.length === 0 && (
                    <div className="bg-[#0a0a0a] border border-white/5 rounded-xl p-8 text-center">
                        <UtensilsIcon size={32} className="mx-auto text-zinc-600 mb-2" />
                        <p className="text-[10px] text-zinc-600 font-bold uppercase tracking-widest">
                            Sin registros
                        </p>
                    </div>
                )}
            </div>

            {/* FAB NERD */}
            <button
                onClick={onOpenDrawer}
                className="fixed bottom-24 right-4 w-14 h-14 rounded-full bg-[#FF7B00] text-white flex items-center justify-center shadow-lg shadow-cyber-copper/20 hover:scale-105 transition-transform z-50 border border-cyber-copper/30"
            >
                <UtensilsIcon size={24} />
            </button>
        </div>
    );
};
