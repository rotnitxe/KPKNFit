
import React, { useEffect, useMemo, useState } from 'react';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import type { BodyProgressLog, NutritionLog, NutritionRiskFlag } from '../types';
import { getDatePartFromString, getLocalDateString } from '../utils/dateUtils';
import { NutritionPlanEditorModal, NutritionWizard, useNutritionStats } from './nutrition';
import { BodyProgressSheet } from './BodyProgressSheet';
import {
    AlertTriangleIcon,
    ChevronDownIcon,
    ChevronUpIcon,
    PencilIcon,
    TrashIcon,
    UtensilsIcon,
} from './icons';
import { getMicronutrientDeficiencies } from '../services/nutritionRecoveryService';
import { buildNutritionProjection, buildNutritionRiskFlags } from '../services/nutritionPlanEngine';
import { calculateDailyCalorieGoal } from '../utils/calorieFormulas';
import {
    Bar,
    CartesianGrid,
    Cell,
    Pie,
    PieChart,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
    BarChart,
} from 'recharts';
import ErrorBoundary from './ui/ErrorBoundary';
import WeightVsTargetChart from './WeightVsTargetChart';
import BodyFatChart from './BodyFatChart';
import MuscleMassChart from './MuscleMassChart';
import FFMIChart from './FFMIChart';

type NutritionTab = 'alimentacion' | 'metricas';
type LegacyInitialTab = 'nutricion' | 'progreso';

interface NutritionViewProps {
    initialTab?: LegacyInitialTab;
}

const MEAL_ORDER: NutritionLog['mealType'][] = ['breakfast', 'lunch', 'dinner', 'snack'];
const MEAL_LABELS: Record<NutritionLog['mealType'], string> = {
    breakfast: 'Desayuno',
    lunch: 'Almuerzo',
    dinner: 'Cena',
    snack: 'Snack',
};

const MACRO_COLORS = {
    calories: 'var(--md-sys-color-primary)',
    protein: '#B3261E',
    carbs: '#D0BCFF',
    fats: '#006A6A',
};

const getRiskClasses = (severity: NutritionRiskFlag['severity']): string => {
    if (severity === 'danger') return 'bg-[#B3261E]/12 text-[#8C1D18] border-[#B3261E]/25';
    if (severity === 'warning') return 'bg-[#7D5700]/12 text-[#6B4F00] border-[#7D5700]/25';
    return 'bg-[#006A6A]/10 text-[#005353] border-[#006A6A]/20';
};

const formatEta = (eta: string | null): string => {
    if (!eta) return 'Sin ETA';
    const d = new Date(`${eta}T00:00:00`);
    if (Number.isNaN(d.getTime())) return 'Sin ETA';
    return d.toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
};

const MacroRingStack: React.FC<{
    proteinPct: number;
    carbsPct: number;
    fatsPct: number;
}> = ({ proteinPct, carbsPct, fatsPct }) => {
    const size = 176;
    const stroke = 10;
    const spacing = 5;
    const outer = size / 2 - stroke;
    const middle = outer - stroke - spacing;
    const inner = middle - stroke - spacing;

    const renderRing = (radius: number, pct: number, color: string) => {
        const circumference = 2 * Math.PI * radius;
        const clamped = Math.max(0, Math.min(1.2, pct));
        const offset = circumference - Math.min(1, clamped) * circumference;
        return (
            <g key={`${radius}-${color}`}>
                <circle
                    cx={size / 2}
                    cy={size / 2}
                    r={radius}
                    fill="none"
                    stroke="rgba(0,0,0,0.08)"
                    strokeWidth={stroke}
                />
                <circle
                    cx={size / 2}
                    cy={size / 2}
                    r={radius}
                    fill="none"
                    stroke={color}
                    strokeWidth={stroke}
                    strokeDasharray={circumference}
                    strokeDashoffset={offset}
                    strokeLinecap="round"
                    transform={`rotate(-90 ${size / 2} ${size / 2})`}
                    className="transition-all duration-700"
                />
            </g>
        );
    };

    return (
        <div className="relative w-[152px] h-[152px] shrink-0">
            <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
                {renderRing(outer, carbsPct, MACRO_COLORS.carbs)}
                {renderRing(middle, proteinPct, MACRO_COLORS.protein)}
                {renderRing(inner, fatsPct, MACRO_COLORS.fats)}
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
                <p className="text-[30px] font-black leading-none text-[#1D1B20] font-['Roboto']">P/C/G</p>
                <p className="text-[9px] uppercase tracking-[0.22em] font-black text-[#49454F]">Balance</p>
            </div>
        </div>
    );
};

const NutritionView: React.FC<NutritionViewProps> = ({ initialTab }) => {
    const {
        settings,
        nutritionLogs,
        nutritionPlans,
        activeNutritionPlanId,
        bodyProgress,
        isNutritionLogModalOpen,
    } = useAppState();
    const {
        setSettings,
        setActiveNutritionPlanId,
        setIsNutritionLogModalOpen,
        setIsBodyLogModalOpen,
        setBodyProgress,
        handleSaveBodyLog,
        addToast,
    } = useAppDispatch();

    const [selectedDate, setSelectedDate] = useState(getLocalDateString());
    const [activeTab, setActiveTab] = useState<NutritionTab>(initialTab === 'progreso' ? 'metricas' : 'alimentacion');
    const [analyticsExpanded, setAnalyticsExpanded] = useState(true);
    const [showWizard, setShowWizard] = useState(false);
    const [editingBodyLog, setEditingBodyLog] = useState<BodyProgressLog | null>(null);
    const [isGoalModalOpen, setIsGoalModalOpen] = useState(false);

    const { dailyCalories, calorieGoal, dailyTotals, proteinGoal, carbGoal, fatGoal } = useNutritionStats(selectedDate);

    const activePlan = useMemo(() => {
        const explicit = nutritionPlans.find((plan) => plan.id === activeNutritionPlanId);
        if (explicit) return explicit;
        return nutritionPlans.find((plan) => plan.isActive) ?? nutritionPlans[nutritionPlans.length - 1] ?? null;
    }, [nutritionPlans, activeNutritionPlanId]);

    useEffect(() => {
        if (!activeNutritionPlanId && activePlan) {
            setActiveNutritionPlanId(activePlan.id);
        }
    }, [activeNutritionPlanId, activePlan, setActiveNutritionPlanId]);

    useEffect(() => {
        if (initialTab === 'nutricion') setActiveTab('alimentacion');
        if (initialTab === 'progreso') setActiveTab('metricas');
    }, [initialTab]);

    useEffect(() => {
        if (isNutritionLogModalOpen) setActiveTab('alimentacion');
    }, [isNutritionLogModalOpen]);

    useEffect(() => {
        try {
            if (sessionStorage.getItem('kpkn_open_nutrition_wizard')) {
                sessionStorage.removeItem('kpkn_open_nutrition_wizard');
                setShowWizard(true);
            }
        } catch (_) {
            // noop
        }
    }, []);

    const latestLog = useMemo(
        () => [...bodyProgress].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())[0] ?? null,
        [bodyProgress]
    );

    const sortedBodyLogs = useMemo(
        () => [...bodyProgress].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()),
        [bodyProgress]
    );

    const todayLogs = useMemo(
        () =>
            nutritionLogs.filter(
                (log) => getDatePartFromString(log.date) === selectedDate && (log.status === 'consumed' || !log.status)
            ),
        [nutritionLogs, selectedDate]
    );

    const groupedMeals = useMemo(() => {
        const groups: Record<NutritionLog['mealType'], NutritionLog[]> = {
            breakfast: [],
            lunch: [],
            dinner: [],
            snack: [],
        };
        todayLogs.forEach((log) => groups[log.mealType].push(log));
        return groups;
    }, [todayLogs]);

    const micronutrientDeficiencies = useMemo(() => {
        const micronutrients: { name: string; amount: number; unit: string }[] = [];
        todayLogs.forEach((log) => {
            (log.foods || []).forEach((food) => {
                (food.micronutrients || []).forEach((m) => {
                    const existing = micronutrients.find((entry) => entry.name === m.name && entry.unit === m.unit);
                    if (existing) existing.amount += m.amount;
                    else micronutrients.push({ ...m });
                });
            });
        });
        return getMicronutrientDeficiencies(micronutrients);
    }, [todayLogs]);
    const trendData = useMemo(() => {
        const dailyCaloriesMap: Record<string, number> = {};
        nutritionLogs.forEach((log) => {
            if (log.status === 'planned') return;
            const day = getDatePartFromString(log.date);
            const dayCalories = (log.foods || []).reduce((sum, food) => sum + (food.calories || 0), 0);
            dailyCaloriesMap[day] = (dailyCaloriesMap[day] || 0) + dayCalories;
        });
        return Object.entries(dailyCaloriesMap)
            .sort(([a], [b]) => new Date(a).getTime() - new Date(b).getTime())
            .slice(-14)
            .map(([date, calories]) => ({
                date: new Date(`${date}T00:00:00`).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                calories: Math.round(calories),
                goal: calorieGoal || null,
            }));
    }, [nutritionLogs, calorieGoal]);

    const macroBarData = useMemo(
        () => [
            {
                key: 'Proteínas',
                value: Math.round(dailyTotals.protein),
                goal: Math.max(1, Math.round(proteinGoal)),
                color: MACRO_COLORS.protein,
            },
            {
                key: 'Carbohidratos',
                value: Math.round(dailyTotals.carbs),
                goal: Math.max(1, Math.round(carbGoal)),
                color: MACRO_COLORS.carbs,
            },
            {
                key: 'Grasas',
                value: Math.round(dailyTotals.fats),
                goal: Math.max(1, Math.round(fatGoal)),
                color: MACRO_COLORS.fats,
            },
        ],
        [dailyTotals, proteinGoal, carbGoal, fatGoal]
    );

    const macroRingPct = useMemo(
        () => ({
            protein: proteinGoal > 0 ? dailyTotals.protein / proteinGoal : 0,
            carbs: carbGoal > 0 ? dailyTotals.carbs / carbGoal : 0,
            fats: fatGoal > 0 ? dailyTotals.fats / fatGoal : 0,
        }),
        [dailyTotals, proteinGoal, carbGoal, fatGoal]
    );

    const planProjection = useMemo(() => {
        if (!activePlan) return null;
        if (activePlan.projection) return activePlan.projection;
        return buildNutritionProjection({ plan: activePlan, bodyProgress, settings });
    }, [activePlan, bodyProgress, settings]);

    const planRiskFlags = useMemo(() => {
        if (!activePlan) return [];
        if (activePlan.riskFlags?.length) return activePlan.riskFlags;

        const calorieTarget = calculateDailyCalorieGoal(settings, activePlan.calorieGoalConfig);
        const weeklyChangeKg =
            activePlan.calorieGoalConfig.weeklyChangeKg != null
                ? Math.abs(activePlan.calorieGoalConfig.weeklyChangeKg)
                : activePlan.trendMode === 'kg_per_week'
                    ? Math.abs(activePlan.trendValue)
                    : 0.5;

        return buildNutritionRiskFlags({
            settings,
            calorieTarget,
            goalMetric: activePlan.primaryGoal?.metric ?? activePlan.goalType,
            goalValue: activePlan.primaryGoal?.value ?? activePlan.goalValue,
            weeklyChangeKg,
        });
    }, [activePlan, settings]);

    const progressPct = useMemo(() => {
        if (!activePlan) return 0;

        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const first = sorted[0];
        const last = sorted[sorted.length - 1];
        const metric = activePlan.primaryGoal?.metric ?? activePlan.goalType;
        const goal = activePlan.primaryGoal?.value ?? activePlan.goalValue;

        const start =
            metric === 'weight'
                ? first?.weight ?? settings.userVitals?.weight
                : metric === 'bodyFat'
                    ? first?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage
                    : first?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage;
        const current =
            metric === 'weight'
                ? last?.weight ?? settings.userVitals?.weight
                : metric === 'bodyFat'
                    ? last?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage
                    : last?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage;

        if (start == null || current == null) return 0;

        const totalDistance = Math.abs(goal - start);
        if (totalDistance === 0) return 100;

        const remaining = Math.abs(goal - current);
        return Math.max(0, Math.min(100, Math.round((1 - remaining / totalDistance) * 100)));
    }, [activePlan, bodyProgress, settings.userVitals]);

    const trendBadge = useMemo(() => {
        const status = planProjection?.trendStatus ?? 'unknown';
        if (status === 'ahead') return { label: 'Adelantado', classes: 'bg-[#006A6A]/10 text-[#005353]' };
        if (status === 'behind') return { label: 'Atrasado', classes: 'bg-[#B3261E]/12 text-[#8C1D18]' };
        if (status === 'on_track') return { label: 'En camino', classes: 'bg-[#7D5700]/12 text-[#6B4F00]' };
        return { label: 'Insuficiente data', classes: 'bg-black/5 text-[#49454F]' };
    }, [planProjection?.trendStatus]);

    const bodyKpis = useMemo(() => {
        const weight = latestLog?.weight ?? settings.userVitals?.weight ?? null;
        const bodyFat = latestLog?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage ?? null;
        const muscle = latestLog?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage ?? null;
        const height = settings.userVitals?.height ?? null;
        const age = settings.userVitals?.age ?? null;
        const gender = settings.userVitals?.gender ?? null;
        const bmi = weight && height ? weight / ((height / 100) * (height / 100)) : null;
        const ffmi =
            weight && height && bodyFat != null
                ? (weight * (1 - bodyFat / 100)) / ((height / 100) * (height / 100))
                : null;

        return [
            { label: 'Peso', value: weight != null ? `${weight.toFixed(1)} ${settings.weightUnit}` : '—' },
            { label: '% Grasa', value: bodyFat != null ? `${bodyFat.toFixed(1)}%` : '—' },
            { label: '% Músculo', value: muscle != null ? `${muscle.toFixed(1)}%` : '—' },
            { label: 'FFMI', value: ffmi != null ? ffmi.toFixed(1) : '—' },
            { label: 'IMC', value: bmi != null ? bmi.toFixed(1) : '—' },
            { label: 'Edad', value: age != null ? `${age}` : '—' },
            {
                label: 'Sexo',
                value: gender ? (gender === 'male' ? 'Hombre' : gender === 'female' ? 'Mujer' : 'Otro') : '—',
            },
            { label: 'Estatura', value: height != null ? `${height} cm` : '—' },
        ];
    }, [latestLog, settings]);

    const hasActivePlan = !!activePlan;
    const viewState = hasActivePlan ? 'with-plan' : 'no-plan';

    if (showWizard) {
        return (
            <NutritionWizard
                onComplete={() => {
                    setShowWizard(false);
                    setSettings({
                        hasSeenNutritionWizard: true,
                        hasDismissedNutritionSetup: true,
                        nutritionWizardVersion: 2,
                    });
                }}
            />
        );
    }

    return (
        <div className="min-h-full flex flex-col bg-[var(--md-sys-color-surface)]">
            <div className="px-4 pt-4 pb-3">
                <div className="rounded-[32px] bg-white/45 backdrop-blur-xl border border-black/[0.04] shadow-[0_16px_50px_-20px_rgba(0,0,0,0.35)] overflow-hidden">
                    <div className="px-6 pt-6 pb-5">
                        <div className="flex items-start justify-between gap-4 mb-4">
                            <div>
                                <p className="text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F]">
                                    Progreso físico y alimentación
                                </p>
                                <h1 className="text-[24px] font-black leading-tight tracking-tight text-[#1D1B20] mt-1">
                                    Panel Nutrición PRO
                                </h1>
                                <p className="text-xs text-[#49454F] mt-1">
                                    {new Date(`${selectedDate}T00:00:00`).toLocaleDateString('es-ES', {
                                        weekday: 'long',
                                        day: 'numeric',
                                        month: 'long',
                                    })}
                                </p>
                            </div>
                            {hasActivePlan ? (
                                <button
                                    onClick={() => setIsGoalModalOpen(true)}
                                    className="px-4 py-2 rounded-2xl bg-[var(--md-sys-color-surface-container)] border border-black/[0.06] text-[10px] font-black uppercase tracking-[0.16em] text-[#1D1B20] hover:bg-[var(--md-sys-color-surface-container-high)] transition-colors"
                                >
                                    Ajustar plan
                                </button>
                            ) : (
                                <button
                                    onClick={() => setShowWizard(true)}
                                    className="px-4 py-2 rounded-2xl bg-[var(--md-sys-color-primary)] text-white text-[10px] font-black uppercase tracking-[0.16em] hover:opacity-90 transition-opacity"
                                >
                                    Crear plan
                                </button>
                            )}
                        </div>

                        <div className="grid grid-cols-1 gap-4 items-center">
                            <div className="rounded-[28px] bg-white/55 border border-black/[0.04] p-5">
                                <div className="flex justify-between items-baseline mb-3">
                                    <span className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Calorías</span>
                                    <span className="text-[12px] font-black text-[#1D1B20] tabular-nums">
                                        {Math.round(dailyCalories)} / {calorieGoal > 0 ? Math.round(calorieGoal) : '--'} kcal
                                    </span>
                                </div>
                                <div className="h-2.5 bg-black/5 rounded-full overflow-hidden mb-4">
                                    <div
                                        className="h-full rounded-full transition-all duration-700"
                                        style={{
                                            width: `${Math.min(100, calorieGoal > 0 ? (dailyCalories / calorieGoal) * 100 : 0)}%`,
                                            background: MACRO_COLORS.calories,
                                        }}
                                    />
                                </div>
                                <div className="space-y-3">
                                    {macroBarData.map((macro) => (
                                        <div key={macro.key}>
                                            <div className="flex justify-between items-baseline mb-1">
                                                <span className="text-[10px] font-black uppercase tracking-[0.15em] text-[#49454F]">
                                                    {macro.key}
                                                </span>
                                                <span className="text-[11px] font-black tabular-nums text-[#1D1B20]">
                                                    {macro.value}g / {macro.goal}g
                                                </span>
                                            </div>
                                            <div className="h-1.5 bg-black/5 rounded-full overflow-hidden">
                                                <div
                                                    className="h-full rounded-full transition-all duration-700"
                                                    style={{
                                                        width: `${Math.min(100, (macro.value / macro.goal) * 100)}%`,
                                                        background: macro.color,
                                                    }}
                                                />
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            <div className="flex flex-col items-center">
                                <MacroRingStack
                                    proteinPct={macroRingPct.protein}
                                    carbsPct={macroRingPct.carbs}
                                    fatsPct={macroRingPct.fats}
                                />
                                <div className="flex items-center gap-3 mt-2 text-[9px] font-black uppercase tracking-[0.14em] text-[#49454F]">
                                    <span className="inline-flex items-center gap-1">
                                        <span className="w-2 h-2 rounded-full" style={{ background: MACRO_COLORS.protein }} />
                                        Prot
                                    </span>
                                    <span className="inline-flex items-center gap-1">
                                        <span className="w-2 h-2 rounded-full" style={{ background: MACRO_COLORS.carbs }} />
                                        Carb
                                    </span>
                                    <span className="inline-flex items-center gap-1">
                                        <span className="w-2 h-2 rounded-full" style={{ background: MACRO_COLORS.fats }} />
                                        Gras
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="px-6 py-4 border-t border-black/[0.05] bg-white/60 flex flex-wrap items-center gap-3">
                        <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-[0.15em] ${trendBadge.classes}`}>
                            {trendBadge.label}
                        </span>
                        <span className="text-[11px] text-[#49454F]">
                            ETA: <span className="font-black text-[#1D1B20]">{formatEta(planProjection?.etaDate ?? activePlan?.estimatedEndDate ?? null)}</span>
                        </span>
                        <span className="text-[11px] text-[#49454F]">
                            Avance: <span className="font-black text-[#1D1B20]">{progressPct}%</span>
                        </span>
                        {planRiskFlags[0] && (
                            <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-bold border ${getRiskClasses(planRiskFlags[0].severity)}`}>
                                <AlertTriangleIcon size={12} />
                                {planRiskFlags[0].message}
                            </span>
                        )}
                    </div>
                </div>
            </div>
            {viewState === 'no-plan' ? (
                <div className="px-4 pb-8">
                    <div className="w-full rounded-[32px] bg-white/60 border border-black/[0.04] p-8 shadow-sm">
                        <p className="text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F] mb-2">Plan-first</p>
                        <h2 className="text-[26px] font-black leading-tight text-[#1D1B20] mb-2">Primero definamos tu plan de alimentación</h2>
                        <p className="text-sm text-[#49454F] max-w-2xl">
                            Esta vista responde a un plan activo. Completa el wizard clínico para desbloquear el registro de alimentos, el análisis de macronutrientes y las métricas corporales con proyección de meta.
                        </p>
                        <div className="mt-6 grid grid-cols-1 gap-3">
                            <div className="rounded-2xl border border-black/[0.05] bg-white/70 p-4">
                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Objetivos</p>
                                <p className="text-sm font-bold text-[#1D1B20] mt-1">Meta primaria + secundarias</p>
                            </div>
                            <div className="rounded-2xl border border-black/[0.05] bg-white/70 p-4">
                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Riesgo</p>
                                <p className="text-sm font-bold text-[#1D1B20] mt-1">Alertas clínicas con severidad</p>
                            </div>
                            <div className="rounded-2xl border border-black/[0.05] bg-white/70 p-4">
                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Proyección</p>
                                <p className="text-sm font-bold text-[#1D1B20] mt-1">ETA y tendencia a la meta</p>
                            </div>
                        </div>
                        <button
                            onClick={() => setShowWizard(true)}
                            className="mt-7 inline-flex items-center justify-center rounded-2xl px-6 py-3 bg-[var(--md-sys-color-primary)] text-white text-sm font-black tracking-wide hover:opacity-95 transition-opacity"
                        >
                            Crear plan de alimentación
                        </button>
                    </div>
                </div>
            ) : (
                <>
                    <div className="px-4 pt-1 pb-3">
                        <div className="w-full flex gap-2 rounded-2xl p-1 bg-[var(--md-sys-color-surface-container-low)] border border-black/[0.03]">
                            <button
                                onClick={() => setActiveTab('alimentacion')}
                                className={`flex-1 rounded-xl px-4 py-2.5 text-sm font-black tracking-wide transition-all ${
                                    activeTab === 'alimentacion'
                                        ? 'bg-white text-[#1D1B20] shadow-sm'
                                        : 'text-[#49454F] hover:bg-white/40'
                                }`}
                            >
                                Alimentación
                            </button>
                            <button
                                onClick={() => setActiveTab('metricas')}
                                className={`flex-1 rounded-xl px-4 py-2.5 text-sm font-black tracking-wide transition-all ${
                                    activeTab === 'metricas'
                                        ? 'bg-white text-[#1D1B20] shadow-sm'
                                        : 'text-[#49454F] hover:bg-white/40'
                                }`}
                            >
                                Métricas corporales
                            </button>
                        </div>
                    </div>

                    <div className="flex-1 min-h-0 overflow-y-auto px-4 pb-28">
                        <div className="w-full space-y-4">
                            {activeTab === 'alimentacion' ? (
                                <>
                                    <section className="rounded-[28px] bg-white/60 border border-black/[0.04] overflow-hidden">
                                        <div className="px-5 py-4 border-b border-black/[0.05] flex items-center justify-between">
                                            <div>
                                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Registro de alimentos</p>
                                                <p className="text-sm font-bold text-[#1D1B20] mt-0.5">Timeline por comida</p>
                                            </div>
                                            <input
                                                type="date"
                                                value={selectedDate}
                                                onChange={(event) => setSelectedDate(event.target.value)}
                                                className="bg-transparent text-sm text-[#1D1B20] border border-black/[0.08] rounded-xl px-3 py-1.5"
                                            />
                                        </div>

                                        <div className="divide-y divide-black/[0.05]">
                                            {MEAL_ORDER.map((mealType) => {
                                                const mealLogs = groupedMeals[mealType];
                                                const totals = mealLogs.reduce(
                                                    (acc, log) => {
                                                        (log.foods || []).forEach((food) => {
                                                            acc.calories += food.calories || 0;
                                                            acc.protein += food.protein || 0;
                                                            acc.carbs += food.carbs || 0;
                                                            acc.fats += food.fats || 0;
                                                        });
                                                        return acc;
                                                    },
                                                    { calories: 0, protein: 0, carbs: 0, fats: 0 }
                                                );

                                                return (
                                                    <div key={mealType} className="px-5 py-4">
                                                        <div className="flex items-start justify-between gap-3">
                                                            <div>
                                                                <p className="text-[11px] font-black uppercase tracking-[0.14em] text-[#49454F]">
                                                                    {MEAL_LABELS[mealType]}
                                                                </p>
                                                                {mealLogs.length > 0 ? (
                                                                    <p className="text-sm text-[#1D1B20] mt-1">
                                                                        {Math.round(totals.calories)} kcal · P {Math.round(totals.protein)}g · C {Math.round(totals.carbs)}g · G {Math.round(totals.fats)}g
                                                                    </p>
                                                                ) : (
                                                                    <p className="text-sm text-[#49454F] mt-1">Sin registros</p>
                                                                )}
                                                            </div>
                                                            {mealLogs.length > 0 && (
                                                                <span className="text-[10px] font-bold px-2.5 py-1 rounded-full bg-black/[0.04] text-[#49454F]">
                                                                    {mealLogs.length} registro{mealLogs.length > 1 ? 's' : ''}
                                                                </span>
                                                            )}
                                                        </div>

                                                        {mealLogs.length > 0 && (
                                                            <div className="mt-3 space-y-2">
                                                                {mealLogs.slice(0, 2).map((meal) => (
                                                                    <div key={meal.id} className="rounded-xl bg-white/70 border border-black/[0.04] px-3 py-2">
                                                                        <p className="text-xs font-semibold text-[#1D1B20]">
                                                                            {(meal.foods || []).slice(0, 3).map((food) => food.foodName).join(', ') || 'Comida registrada'}
                                                                        </p>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        )}
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </section>

                                    <section className="rounded-[28px] bg-white/60 border border-black/[0.04] overflow-hidden">
                                        <button
                                            onClick={() => setAnalyticsExpanded((current) => !current)}
                                            className="w-full px-5 py-4 flex items-center justify-between"
                                        >
                                            <div className="text-left">
                                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Análisis</p>
                                                <p className="text-sm font-bold text-[#1D1B20] mt-0.5">Tendencia + distribución + cumplimiento</p>
                                            </div>
                                            {analyticsExpanded ? <ChevronUpIcon size={18} className="text-[#49454F]" /> : <ChevronDownIcon size={18} className="text-[#49454F]" />}
                                        </button>

                                        {analyticsExpanded && (
                                            <div className="px-5 pb-5 space-y-5 border-t border-black/[0.05]">
                                                <div className="h-52 rounded-2xl bg-white/70 border border-black/[0.04] p-3">
                                                    <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F] mb-2">Calorías diario/semanal</p>
                                                    <ResponsiveContainer width="100%" height="100%">
                                                        <BarChart data={trendData}>
                                                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.08)" />
                                                            <XAxis dataKey="date" fontSize={10} stroke="#49454F" />
                                                            <YAxis fontSize={10} stroke="#49454F" />
                                                            <Tooltip />
                                                            {calorieGoal > 0 && <ReferenceLine y={Math.round(calorieGoal)} stroke="rgba(0,106,106,0.55)" strokeDasharray="4 4" />}
                                                            <Bar dataKey="calories" radius={[6, 6, 0, 0]}>
                                                                {trendData.map((item, idx) => (
                                                                    <Cell
                                                                        key={`${item.date}-${idx}`}
                                                                        fill={item.goal && item.calories > item.goal ? 'rgba(179,38,30,0.75)' : 'rgba(0,106,106,0.75)'}
                                                                    />
                                                                ))}
                                                            </Bar>
                                                        </BarChart>
                                                    </ResponsiveContainer>
                                                </div>

                                                <div className="grid grid-cols-1 gap-4">
                                                    <div className="rounded-2xl bg-white/70 border border-black/[0.04] p-4">
                                                        <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F] mb-3">Distribución de macros</p>
                                                        <div className="space-y-3">
                                                            {macroBarData.map((macro) => (
                                                                <div key={macro.key}>
                                                                    <div className="flex justify-between text-xs mb-1">
                                                                        <span className="font-bold text-[#1D1B20]">{macro.key}</span>
                                                                        <span className="font-semibold text-[#49454F]">
                                                                            {macro.value}g / {macro.goal}g
                                                                        </span>
                                                                    </div>
                                                                    <div className="h-2 bg-black/[0.06] rounded-full overflow-hidden">
                                                                        <div
                                                                            className="h-full rounded-full"
                                                                            style={{ width: `${Math.min(100, (macro.value / macro.goal) * 100)}%`, background: macro.color }}
                                                                        />
                                                                    </div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>

                                                    <div className="rounded-2xl bg-white/70 border border-black/[0.04] p-4 flex items-center justify-center">
                                                        <ResponsiveContainer width="100%" height={150}>
                                                            <PieChart>
                                                                <Pie
                                                                    data={[
                                                                        { name: 'Prot', value: Math.max(0, dailyTotals.protein), color: MACRO_COLORS.protein },
                                                                        { name: 'Carb', value: Math.max(0, dailyTotals.carbs), color: MACRO_COLORS.carbs },
                                                                        { name: 'Grasa', value: Math.max(0, dailyTotals.fats), color: MACRO_COLORS.fats },
                                                                    ]}
                                                                    dataKey="value"
                                                                    innerRadius={38}
                                                                    outerRadius={58}
                                                                    strokeWidth={1}
                                                                >
                                                                    {['protein', 'carbs', 'fats'].map((key) => (
                                                                        <Cell key={key} fill={MACRO_COLORS[key as keyof typeof MACRO_COLORS]} />
                                                                    ))}
                                                                </Pie>
                                                            </PieChart>
                                                        </ResponsiveContainer>
                                                    </div>
                                                </div>

                                                <div className="rounded-2xl bg-white/70 border border-black/[0.04] p-4">
                                                    <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F] mb-2">
                                                        Microdeficiencias detectadas
                                                    </p>
                                                    {micronutrientDeficiencies.length === 0 ? (
                                                        <p className="text-sm text-[#1D1B20]">Sin déficit relevante hoy.</p>
                                                    ) : (
                                                        <div className="flex flex-wrap gap-2">
                                                            {micronutrientDeficiencies.slice(0, 6).map((item) => (
                                                                <span
                                                                    key={item.name}
                                                                    className="px-2.5 py-1 rounded-full text-[10px] font-bold border bg-[#7D5700]/10 border-[#7D5700]/20 text-[#6B4F00]"
                                                                >
                                                                    {item.name} ({item.pct}% RDA)
                                                                </span>
                                                            ))}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        )}
                                    </section>
                                </>
                            ) : (
                                <>
                                    <section className="rounded-[28px] bg-white/60 border border-black/[0.04] p-4">
                                        <div className="flex flex-wrap justify-between gap-3 items-center mb-3">
                                            <div>
                                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Métricas corporales</p>
                                                <p className="text-sm font-bold text-[#1D1B20] mt-0.5">KPIs y proyección a meta</p>
                                            </div>
                                            <button
                                                onClick={() => setIsBodyLogModalOpen(true)}
                                                className="rounded-xl px-4 py-2 text-[11px] font-black uppercase tracking-[0.14em] bg-[var(--md-sys-color-primary)] text-white hover:opacity-95 transition-opacity"
                                            >
                                                Registrar métrica
                                            </button>
                                        </div>
                                        <div className="grid grid-cols-2 gap-2.5">
                                            {bodyKpis.map((kpi) => (
                                                <div key={kpi.label} className="rounded-xl bg-white/75 border border-black/[0.04] px-3 py-3">
                                                    <p className="text-[9px] font-black uppercase tracking-[0.14em] text-[#49454F]">{kpi.label}</p>
                                                    <p className="text-sm font-black text-[#1D1B20] mt-1">{kpi.value}</p>
                                                </div>
                                            ))}
                                        </div>
                                    </section>

                                    <section className="rounded-[28px] bg-white/60 border border-black/[0.04] p-4">
                                        <div className="flex flex-wrap gap-2 items-center justify-between">
                                            <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Proyección y tendencia</p>
                                            <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-[0.15em] ${trendBadge.classes}`}>
                                                {trendBadge.label}
                                            </span>
                                        </div>
                                        <p className="text-sm text-[#1D1B20] mt-2">
                                            ETA objetivo: <span className="font-black">{formatEta(planProjection?.etaDate ?? activePlan?.estimatedEndDate ?? null)}</span>
                                            {planProjection?.confidence != null && (
                                                <span className="text-[#49454F]"> · Confianza {Math.round(planProjection.confidence * 100)}%</span>
                                            )}
                                        </p>
                                        {planRiskFlags.length > 0 && (
                                            <div className="mt-3 flex flex-wrap gap-2">
                                                {planRiskFlags.slice(0, 3).map((flag) => (
                                                    <span
                                                        key={flag.id}
                                                        className={`px-2.5 py-1 rounded-full text-[10px] font-bold border ${getRiskClasses(flag.severity)}`}
                                                    >
                                                        {flag.message}
                                                    </span>
                                                ))}
                                            </div>
                                        )}
                                    </section>

                                    <section className="space-y-4">
                                        <ErrorBoundary fallbackLabel="WeightVsTargetChart">
                                            <WeightVsTargetChart />
                                        </ErrorBoundary>
                                        <ErrorBoundary fallbackLabel="BodyFatChart">
                                            <BodyFatChart />
                                        </ErrorBoundary>
                                        <ErrorBoundary fallbackLabel="MuscleMassChart">
                                            <MuscleMassChart />
                                        </ErrorBoundary>
                                        <ErrorBoundary fallbackLabel="FFMIChart">
                                            <FFMIChart />
                                        </ErrorBoundary>
                                    </section>

                                    <section className="rounded-[28px] bg-white/60 border border-black/[0.04] overflow-hidden">
                                        <div className="px-5 py-4 border-b border-black/[0.05]">
                                            <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Historial editable</p>
                                            <p className="text-sm font-bold text-[#1D1B20] mt-0.5">Peso, composición y medidas</p>
                                        </div>
                                        {sortedBodyLogs.length === 0 ? (
                                            <p className="px-5 py-6 text-sm text-[#49454F]">Aún no hay registros corporales.</p>
                                        ) : (
                                            <div className="divide-y divide-black/[0.05]">
                                                {sortedBodyLogs.map((log) => (
                                                    <div key={log.id} className="px-5 py-3 flex items-start justify-between gap-3">
                                                        <div>
                                                            <p className="text-sm font-bold text-[#1D1B20]">
                                                                {log.weight != null ? `${log.weight} ${settings.weightUnit}` : 'Sin peso'}
                                                                {log.bodyFatPercentage != null ? ` · ${log.bodyFatPercentage}% grasa` : ''}
                                                                {log.muscleMassPercentage != null ? ` · ${log.muscleMassPercentage}% músculo` : ''}
                                                            </p>
                                                            <p className="text-[11px] text-[#49454F] mt-0.5">
                                                                {new Date(log.date).toLocaleDateString('es-ES', {
                                                                    day: 'numeric',
                                                                    month: 'short',
                                                                    year: 'numeric',
                                                                })}
                                                            </p>
                                                        </div>
                                                        <div className="flex items-center gap-1">
                                                            <button
                                                                onClick={() => setEditingBodyLog(log)}
                                                                className="w-8 h-8 rounded-lg border border-black/[0.08] text-[#49454F] hover:bg-black/[0.04] flex items-center justify-center"
                                                                aria-label="Editar registro"
                                                            >
                                                                <PencilIcon size={14} />
                                                            </button>
                                                            <button
                                                                onClick={() => {
                                                                    if (!window.confirm('¿Eliminar este registro corporal?')) return;
                                                                    setBodyProgress((prev) => prev.filter((entry) => entry.id !== log.id));
                                                                    addToast('Registro eliminado.', 'success');
                                                                }}
                                                                className="w-8 h-8 rounded-lg border border-black/[0.08] text-[#8C1D18] hover:bg-[#B3261E]/10 flex items-center justify-center"
                                                                aria-label="Eliminar registro"
                                                            >
                                                                <TrashIcon size={14} />
                                                            </button>
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </section>
                                </>
                            )}
                        </div>
                    </div>

                    {activeTab === 'alimentacion' && (
                        <button
                            onClick={() => setIsNutritionLogModalOpen(true)}
                            className="fixed right-4 z-20 rounded-2xl px-4 h-12 bg-[var(--md-sys-color-primary)] text-white text-sm font-black tracking-wide shadow-lg flex items-center gap-2 active:scale-[0.98] transition-transform"
                            style={{ bottom: 'calc(var(--tab-bar-safe-bottom, 140px) + 0.5rem)' }}
                            aria-label="Registrar alimento"
                        >
                            <UtensilsIcon size={16} />
                            Registrar alimento
                        </button>
                    )}
                </>
            )}

            <NutritionPlanEditorModal isOpen={isGoalModalOpen} onClose={() => setIsGoalModalOpen(false)} />

            {editingBodyLog && (
                <BodyProgressSheet
                    isOpen
                    onClose={() => setEditingBodyLog(null)}
                    onSave={(log) => {
                        handleSaveBodyLog(log);
                        setEditingBodyLog(null);
                    }}
                    settings={settings}
                    initialLog={editingBodyLog}
                    activePlan={activePlan}
                />
            )}
        </div>
    );
};

export default NutritionView;

