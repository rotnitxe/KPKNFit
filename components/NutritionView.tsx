import React, { useEffect, useMemo, useState } from 'react';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import type { BodyProgressLog, NutritionLog, NutritionRiskFlag } from '../types';
import { getDatePartFromString, getLocalDateString } from '../utils/dateUtils';
import { NutritionPlanEditorModal, NutritionWizard, useNutritionStats, FoodLoggerUnified } from './nutrition';
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
    carbs: '#6750A4',
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

const getViewportWidth = (): number => {
    if (typeof window === 'undefined') return 390;
    return window.innerWidth || 390;
};

const MacroRingStack: React.FC<{
    caloriesPct: number;
    proteinPct: number;
    carbsPct: number;
    fatsPct: number;
    size?: number;
}> = ({ caloriesPct, proteinPct, carbsPct, fatsPct, size = 136 }) => {
    const stroke = Math.max(6, Math.round(size * 0.052));
    const spacing = Math.max(3, Math.round(size * 0.018));

    const outer = size / 2 - stroke;
    const midOuter = outer - stroke - spacing;
    const midInner = midOuter - stroke - spacing;
    const inner = midInner - stroke - spacing;

    const renderRing = (radius: number, pct: number, color: string, id: string) => {
        const circumference = 2 * Math.PI * radius;
        const clamped = Math.max(0, Math.min(1.2, pct));
        const offset = circumference - Math.min(1, clamped) * circumference;

        return (
            <g key={id}>
                <defs>
                    <filter id={`nutri-ring-${id}`} x="-60%" y="-60%" width="220%" height="220%">
                        <feGaussianBlur in="SourceGraphic" stdDeviation="1.25" />
                    </filter>
                </defs>
                <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="rgba(0,0,0,0.08)" strokeWidth={stroke} />
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
                    filter={`url(#nutri-ring-${id})`}
                    className="transition-all duration-700 ease-out"
                />
            </g>
        );
    };

    return (
        <div className="relative shrink-0" style={{ width: size, height: size }}>
            <div className="absolute -left-12 top-7 w-20 h-20 rounded-full blur-3xl" style={{ background: 'rgba(101,85,143,0.18)' }} />
            <div className="absolute -right-14 bottom-3 w-24 h-24 rounded-full blur-3xl" style={{ background: 'rgba(0,106,106,0.15)' }} />
            <div className="absolute left-1/2 -translate-x-1/2 -top-9 w-20 h-20 rounded-full blur-3xl" style={{ background: 'rgba(179,38,30,0.12)' }} />

            <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
                {renderRing(outer, caloriesPct, MACRO_COLORS.calories, 'cal')}
                {renderRing(midOuter, proteinPct, MACRO_COLORS.protein, 'prot')}
                {renderRing(midInner, carbsPct, MACRO_COLORS.carbs, 'carb')}
                {renderRing(inner, fatsPct, MACRO_COLORS.fats, 'fat')}
            </svg>

            <div className="absolute inset-0 flex flex-col items-center justify-center">
                <p className="text-[9px] font-black uppercase tracking-[0.16em] text-[#49454F]">Macros</p>
                <p className="text-[12px] font-black text-[#1D1B20]">P/C/G</p>
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
        setNutritionLogs,
        handleSaveBodyLog,
        handleSaveNutritionLog,
        addToast,
    } = useAppDispatch();

    const [selectedDate, setSelectedDate] = useState(getLocalDateString());
    const [activeTab, setActiveTab] = useState<NutritionTab>(initialTab === 'progreso' ? 'metricas' : 'alimentacion');
    const [analyticsExpanded, setAnalyticsExpanded] = useState(true);
    const [showWizard, setShowWizard] = useState(false);
    const [editingBodyLog, setEditingBodyLog] = useState<BodyProgressLog | null>(null);
    const [isGoalModalOpen, setIsGoalModalOpen] = useState(false);
    const [viewportWidth, setViewportWidth] = useState(getViewportWidth);
    const [metricsExpanded, setMetricsExpanded] = useState({ summary: false, projection: false, evolution: false, history: false });

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

    useEffect(() => {
        const onResize = () => setViewportWidth(getViewportWidth());
        window.addEventListener('resize', onResize);
        return () => window.removeEventListener('resize', onResize);
    }, []);

    const isCompactPhone = viewportWidth <= 390;
    const isPlusPhone = viewportWidth >= 412;

    const layout = useMemo(
        () => ({
            pagePadX: isCompactPhone ? 12 : 16,
            topPad: isCompactPhone ? 10 : 14,
            topBottomPad: isCompactPhone ? 8 : 10,
            headerRadius: isCompactPhone ? 24 : 28,
            sectionRadius: isCompactPhone ? 22 : 26,
            headerPadX: isCompactPhone ? 12 : 14,
            headerTop: isCompactPhone ? 12 : 14,
            headerBottom: isCompactPhone ? 10 : 12,
            ringSize: isCompactPhone ? 112 : isPlusPhone ? 126 : 118,
            contentBottom: isCompactPhone ? 120 : 112,
            fabBottom: isCompactPhone
                ? 'calc(var(--tab-bar-safe-bottom, 132px) + 0.375rem)'
                : 'calc(var(--tab-bar-safe-bottom, 140px) + 0.5rem)',
        }),
        [isCompactPhone, isPlusPhone]
    );

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
            .slice(-7)
            .map(([date, calories]) => ({
                date: new Date(`${date}T00:00:00`).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                calories: Math.round(calories),
                goal: calorieGoal || null,
            }));
    }, [nutritionLogs, calorieGoal]);

    const historyByDay30 = useMemo(() => {
        const now = new Date();
        const cutoff = new Date(now);
        cutoff.setDate(now.getDate() - 29);

        const byDay = new Map<string, { calories: number; logs: number; foods: number }>();

        nutritionLogs.forEach((log) => {
            if (log.status === 'planned') return;
            const dayPart = getDatePartFromString(log.date);
            const dayDate = new Date(`${dayPart}T00:00:00`);
            if (dayDate < cutoff) return;

            const existing = byDay.get(dayPart) ?? { calories: 0, logs: 0, foods: 0 };
            const calories = (log.foods || []).reduce((sum, item) => sum + (item.calories || 0), 0);

            existing.calories += calories;
            existing.logs += 1;
            existing.foods += (log.foods || []).length;
            byDay.set(dayPart, existing);
        });

        return [...byDay.entries()]
            .sort(([a], [b]) => new Date(b).getTime() - new Date(a).getTime())
            .map(([day, info]) => ({ day, ...info }));
    }, [nutritionLogs]);

    const commonFoods30 = useMemo(() => {
        const now = new Date();
        const cutoff = new Date(now);
        cutoff.setDate(now.getDate() - 29);

        const foodsMap = new Map<string, { name: string; count: number; calories: number; lastDate: string }>();

        nutritionLogs.forEach((log) => {
            if (log.status === 'planned') return;
            const dayPart = getDatePartFromString(log.date);
            const dayDate = new Date(`${dayPart}T00:00:00`);
            if (dayDate < cutoff) return;

            (log.foods || []).forEach((food) => {
                const normalized = food.foodName.trim().toLowerCase();
                if (!normalized) return;
                const existing = foodsMap.get(normalized) ?? {
                    name: food.foodName,
                    count: 0,
                    calories: 0,
                    lastDate: dayPart,
                };

                existing.count += 1;
                existing.calories += food.calories || 0;
                if (new Date(`${dayPart}T00:00:00`).getTime() > new Date(`${existing.lastDate}T00:00:00`).getTime()) {
                    existing.lastDate = dayPart;
                }
                foodsMap.set(normalized, existing);
            });
        });

        return [...foodsMap.values()]
            .sort((a, b) => (b.count === a.count ? b.calories - a.calories : b.count - a.count))
            .slice(0, 8)
            .map((item) => ({
                name: item.name,
                count: item.count,
                avgCalories: item.count > 0 ? Math.round(item.calories / item.count) : 0,
                lastDate: item.lastDate,
            }));
    }, [nutritionLogs]);

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
            calories: calorieGoal > 0 ? dailyCalories / calorieGoal : 0,
            protein: proteinGoal > 0 ? dailyTotals.protein / proteinGoal : 0,
            carbs: carbGoal > 0 ? dailyTotals.carbs / carbGoal : 0,
            fats: fatGoal > 0 ? dailyTotals.fats / fatGoal : 0,
        }),
        [dailyCalories, calorieGoal, dailyTotals, proteinGoal, carbGoal, fatGoal]
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
        if (status === 'ahead') return { label: 'Excelente', classes: 'bg-[#006A6A]/10 text-[#005353]' };
        if (status === 'behind') return { label: 'Ajustar ritmo', classes: 'bg-[#B3261E]/12 text-[#8C1D18]' };
        if (status === 'on_track') return { label: 'En camino', classes: 'bg-[#7D5700]/12 text-[#6B4F00]' };
        return { label: 'Comienza a registrar', classes: 'bg-black/5 text-[#49454F]' };
    }, [planProjection?.trendStatus]);

    const primaryGoalCard = useMemo(() => {
        const metric = (activePlan?.primaryGoal?.metric ?? activePlan?.goalType ?? 'weight') as 'weight' | 'bodyFat' | 'muscleMass';
        const goal = activePlan?.primaryGoal?.value ?? activePlan?.goalValue ?? 0;

        const current =
            metric === 'weight'
                ? latestLog?.weight ?? settings.userVitals?.weight ?? 0
                : metric === 'bodyFat'
                  ? latestLog?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage ?? 0
                  : latestLog?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage ?? 0;

        const label = metric === 'weight' ? 'Peso' : metric === 'bodyFat' ? '% Grasa' : '% Músculo';
        const unit = metric === 'weight' ? settings.weightUnit : '%';

        return { metric, label, unit, current, goal };
    }, [activePlan, latestLog, settings]);
    const sevenDayTrend = useMemo(() => {
        if (!activePlan) return null;

        const metric = (activePlan.primaryGoal?.metric ?? activePlan.goalType) as 'weight' | 'bodyFat' | 'muscleMass';
        const now = new Date();
        const cutoff = new Date(now);
        cutoff.setDate(now.getDate() - 6);

        const points = [...bodyProgress]
            .filter((log) => {
                const day = new Date(log.date);
                if (Number.isNaN(day.getTime())) return false;
                if (day < cutoff) return false;
                if (metric === 'weight') return log.weight != null;
                if (metric === 'bodyFat') return log.bodyFatPercentage != null;
                return log.muscleMassPercentage != null;
            })
            .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());

        if (points.length < 2) return null;

        const first = points[0];
        const last = points[points.length - 1];
        const firstValue = metric === 'weight' ? first.weight! : metric === 'bodyFat' ? first.bodyFatPercentage! : first.muscleMassPercentage!;
        const lastValue = metric === 'weight' ? last.weight! : metric === 'bodyFat' ? last.bodyFatPercentage! : last.muscleMassPercentage!;

        const firstDate = new Date(first.date);
        const lastDate = new Date(last.date);
        const days = Math.max(1, Math.round((lastDate.getTime() - firstDate.getTime()) / 86400000));
        const weeklyDelta = ((lastValue - firstValue) / days) * 7;

        return {
            weeklyDelta,
            unit: metric === 'weight' ? `${settings.weightUnit}/sem` : '%/sem',
        };
    }, [activePlan, bodyProgress, settings.weightUnit]);

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

    const primaryKpis = useMemo(
        () => bodyKpis.filter((kpi) => ['Peso', '% Grasa', '% Músculo', 'FFMI'].includes(kpi.label)),
        [bodyKpis]
    );

    const hasActivePlan = !!activePlan;
    const viewState = hasActivePlan ? 'with-plan' : 'no-plan';
    const isFirstOpenWithoutPlan = !settings.hasSeenNutritionWizard || settings.nutritionWizardVersion !== 2;

    const toggleMetricsPanel = (key: keyof typeof metricsExpanded) => {
        setMetricsExpanded((prev) => ({ ...prev, [key]: !prev[key] }));
    };

    const toIsoAtNoon = (datePart: string): string => {
        const [year, month, day] = datePart.split('-').map((value) => Number(value));
        if (!year || !month || !day) return new Date().toISOString();
        return new Date(year, month - 1, day, 12, 0, 0, 0).toISOString();
    };

    const handleDeleteLog = (log: NutritionLog) => {
        if (!window.confirm('¿Eliminar este registro de comida?')) return;
        setNutritionLogs((prev) => prev.filter((item) => item.id !== log.id));
        addToast('Registro eliminado.', 'success');
    };

    const handleNutritionLogSaved = (log: NutritionLog) => {
        handleSaveNutritionLog(log);
    };

    const handleDuplicateLog = (log: NutritionLog) => {
        const duplicated: NutritionLog = {
            ...log,
            id: crypto.randomUUID(),
            date: toIsoAtNoon(selectedDate),
            foods: (log.foods || []).map((food) => ({ ...food, id: crypto.randomUUID() })),
            notes: log.notes ? `${log.notes} (duplicado)` : undefined,
            status: 'consumed',
        };
        setNutritionLogs((prev) => [...prev, duplicated]);
        addToast('Registro duplicado en la fecha seleccionada.', 'success');
    };

    const handleMoveLog = (log: NutritionLog) => {
        const currentDate = getDatePartFromString(log.date);
        const nextDate = window.prompt('Mover a fecha (AAAA-MM-DD)', currentDate) ?? currentDate;
        const nextMeal =
            (window.prompt('Mover a comida (breakfast, lunch, dinner, snack)', log.mealType) ??
                log.mealType) as NutritionLog['mealType'];

        if (!/^\d{4}-\d{2}-\d{2}$/.test(nextDate)) {
            addToast('La fecha debe estar en formato AAAA-MM-DD.', 'danger');
            return;
        }
        if (!MEAL_ORDER.includes(nextMeal)) {
            addToast('La comida destino no es válida.', 'danger');
            return;
        }

        setNutritionLogs((prev) =>
            prev.map((item) =>
                item.id === log.id
                    ? {
                          ...item,
                          date: toIsoAtNoon(nextDate),
                          mealType: nextMeal,
                      }
                    : item
            )
        );
        addToast('Registro movido correctamente.', 'success');
    };

    const handleEditLog = (log: NutritionLog) => {
        const nextNotes = window.prompt('Edita la nota de este registro', log.notes ?? '') ?? log.notes ?? '';
        setNutritionLogs((prev) =>
            prev.map((item) =>
                item.id === log.id
                    ? {
                          ...item,
                          notes: nextNotes.trim() ? nextNotes.trim() : undefined,
                      }
                    : item
            )
        );
        addToast('Registro actualizado.', 'success');
    };

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

            <div
                style={{
                    paddingLeft: layout.pagePadX,
                    paddingRight: layout.pagePadX,
                    paddingTop: layout.topPad,
                    paddingBottom: layout.topBottomPad,
                }}
            >
                <header
                    className="relative overflow-hidden rounded-[32px] border border-black/[0.04] bg-gradient-to-b from-white/90 to-white/70 shadow-[0_6px_60px_-26px_rgba(0,0,0,0.35)]"
                    style={{ minHeight: 200 }}
                >
                    <div className="pointer-events-none absolute inset-0">
                        <div className="absolute -top-6 -left-6 w-[200px] h-[200px] rounded-full bg-[#B3261E]/20 blur-[70px]" />
                        <div className="absolute top-10 right-[-40px] w-[220px] h-[220px] rounded-full bg-[#006A6A]/20 blur-[90px]" />
                        <div className="absolute bottom-[-30px] left-1/2 -translate-x-1/2 w-[260px] h-[260px] rounded-full bg-[#6750A4]/15 blur-[120px]" />
                    </div>

                    <div
                        className="relative"
                        style={{
                            paddingLeft: layout.headerPadX,
                            paddingRight: layout.headerPadX,
                            paddingTop: layout.headerTop,
                            paddingBottom: layout.headerBottom,
                        }}
                    >
                        <div className="flex items-start justify-between gap-3">
                            <div>
                                <p className="text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F]">Progreso físico y alimentación</p>
                                <h1 className="text-[26px] font-black leading-tight tracking-tight text-[#1D1B20] mt-1">Nutrición y progreso físico</h1>
                                <p className="text-[11px] text-[#49454F] mt-1">
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
                                    className="px-3.5 py-2 rounded-2xl bg-[var(--md-sys-color-surface-container)] border border-black/[0.06] text-[10px] font-black uppercase tracking-[0.16em] text-[#1D1B20]"
                                >
                                    Ajustar plan
                                </button>
                            ) : (
                                <button
                                    onClick={() => setShowWizard(true)}
                                    className="px-3.5 py-2 rounded-2xl bg-[var(--md-sys-color-primary)] text-white text-[10px] font-black uppercase tracking-[0.16em]"
                                >
                                    Crear plan
                                </button>
                            )}
                        </div>

                        <div className="mt-4 grid gap-3 lg:grid-cols-[1.05fr,0.95fr]">
                            <div className="rounded-[28px] border border-black/[0.05] bg-white/75 p-3.5 space-y-3">
                                <div className="flex justify-between items-baseline">
                                    <span className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Calorías</span>
                                    <span className="text-[12px] font-black text-[#1D1B20] tabular-nums">
                                        {Math.round(dailyCalories)} / {calorieGoal > 0 ? Math.round(calorieGoal) : '--'} kcal
                                    </span>
                                </div>
                                <div className="h-2 bg-black/5 rounded-full overflow-hidden">
                                    <div
                                        className="h-full rounded-full transition-all duration-700"
                                        style={{
                                            width: `${Math.min(100, calorieGoal > 0 ? (dailyCalories / calorieGoal) * 100 : 0)}%`,
                                            background: MACRO_COLORS.calories,
                                        }}
                                    />
                                </div>
                                <div className="space-y-2">
                                    {macroBarData.map((macro) => (
                                        <div key={macro.key}>
                                            <div className="flex justify-between text-[10px] mb-1">
                                                <span className="font-black uppercase tracking-[0.13em] text-[#49454F]">{macro.key}</span>
                                                <span className="font-semibold text-[#1D1B20] tabular-nums">
                                                    {macro.value}g / {macro.goal}g
                                                </span>
                                            </div>
                                            <div className="h-1.5 bg-black/[0.05] rounded-full overflow-hidden">
                                                <div className="h-full rounded-full transition-all duration-700" style={{ width: `${Math.min(100, (macro.value / macro.goal) * 100)}%`, background: macro.color }} />
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            <div className="space-y-3">
                                <div className="rounded-[28px] border border-black/[0.05] bg-white/75 p-3.5 flex flex-col items-center gap-2">
                                    <MacroRingStack
                                        caloriesPct={macroRingPct.calories}
                                        proteinPct={macroRingPct.protein}
                                        carbsPct={macroRingPct.carbs}
                                        fatsPct={macroRingPct.fats}
                                        size={layout.ringSize}
                                    />
                                    <div className="text-xs font-black uppercase tracking-[0.18em] text-[#49454F] text-center">
                                        {primaryGoalCard.label} · {primaryGoalCard.current.toFixed(1)} {primaryGoalCard.unit}
                                    </div>
                                </div>
                                <div className="rounded-[28px] border border-black/[0.05] bg-white/75 p-3.5 space-y-1">
                                    <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Meta y tendencia</p>
                                    <p className="text-sm font-black text-[#1D1B20]">
                                        Objetivo: {primaryGoalCard.goal.toFixed(1)} {primaryGoalCard.unit}
                                    </p>
                                    {sevenDayTrend ? (
                                        <p className="text-[12px] text-[#1D1B20]">
                                            +{sevenDayTrend.weeklyDelta.toFixed(2)} {sevenDayTrend.unit} / 7 días
                                        </p>
                                    ) : (
                                        <p className="text-[12px] text-[#49454F]">Sigue registrando para ver la tendencia.</p>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                </header>

                <div className="px-4 py-3 border-t border-black/[0.05] bg-white/60 flex flex-wrap items-center gap-2.5">
                    <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-[0.15em] ${trendBadge.classes}`}>
                        {trendBadge.label}
                    </span>
                    <span className="text-[11px] text-[#49454F]">
                        ETA:{' '}
                        <span className="font-black text-[#1D1B20]">
                            {formatEta(planProjection?.etaDate ?? activePlan?.estimatedEndDate ?? null)}
                        </span>
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

            {viewState === 'no-plan' ? (
                <div style={{ paddingLeft: layout.pagePadX, paddingRight: layout.pagePadX, paddingBottom: 24 }}>
                    <div className="mb-4">
                        <FoodLoggerUnified initialDate={selectedDate} onLogRegistered={handleNutritionLogSaved} />
                    </div>
                    <div className="w-full bg-white/60 border border-black/[0.04] p-6 shadow-sm" style={{ borderRadius: layout.headerRadius }}>
                        <p className="text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F] mb-2">Bienvenido a nutrición</p>
                        <h2 className="text-[24px] font-black leading-tight text-[#1D1B20] mb-2">
                            {isFirstOpenWithoutPlan ? 'Creemos tu plan de alimentación' : 'Activa un plan para continuar'}
                        </h2>
                        <p className="text-sm text-[#49454F]">
                            Puedes registrar comidas desde ya. Cuando actives tu plan, también se desbloquearán metas, métricas y seguimiento avanzado.
                        </p>
                        <button
                            onClick={() => setShowWizard(true)}
                            className="mt-6 inline-flex items-center justify-center rounded-2xl px-5 py-3 bg-[var(--md-sys-color-primary)] text-white text-sm font-black tracking-wide"
                        >
                            Comenzar mi plan
                        </button>
                    </div>
                </div>
            ) : (
                <>
                    {/* Food Logger Unified - Nuevo sistema de registro con IA */}
                    <div style={{ paddingLeft: layout.pagePadX, paddingRight: layout.pagePadX, paddingTop: 16 }}>
                        <FoodLoggerUnified initialDate={selectedDate} onLogRegistered={handleNutritionLogSaved} />
                    </div>
                
                    <div style={{ paddingLeft: layout.pagePadX, paddingRight: layout.pagePadX, paddingTop: 4, paddingBottom: 10 }}>
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

                    <div className="flex-1 min-h-0 overflow-y-auto" style={{ paddingLeft: layout.pagePadX, paddingRight: layout.pagePadX, paddingBottom: layout.contentBottom }}>
                        <div className="w-full space-y-3.5">
                            {activeTab === 'alimentacion' ? (
                                <>
                                    <section className="bg-white/60 border border-black/[0.04] overflow-hidden" style={{ borderRadius: layout.sectionRadius }}>
                                        <div className="px-4 py-3 border-b border-black/[0.05] flex items-center justify-between gap-3">
                                            <div>
                                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Registro de alimentos</p>
                                                <p className="text-sm font-bold text-[#1D1B20] mt-0.5">Comidas del día por bloque</p>
                                            </div>
                                            <input
                                                type="date"
                                                value={selectedDate}
                                                onChange={(event) => setSelectedDate(event.target.value)}
                                                className="bg-transparent text-xs text-[#1D1B20] border border-black/[0.1] rounded-xl px-3 py-1.5"
                                            />
                                        </div>

                                        <div className="divide-y divide-black/[0.05]">
                                            {MEAL_ORDER.map((mealType) => {
                                                const mealLogs = groupedMeals[mealType];
                                                const mealTotals = mealLogs.reduce(
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
                                                    <div key={mealType} className="px-4 py-3">
                                                        <div className="flex items-start justify-between gap-2">
                                                            <div>
                                                                <p className="text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F]">{MEAL_LABELS[mealType]}</p>
                                                                <p className="text-xs text-[#1D1B20] mt-0.5">
                                                                    {mealLogs.length > 0
                                                                        ? `${Math.round(mealTotals.calories)} kcal · P ${Math.round(mealTotals.protein)}g · C ${Math.round(mealTotals.carbs)}g · G ${Math.round(mealTotals.fats)}g`
                                                                        : 'Sin registros por ahora'}
                                                                </p>
                                                            </div>
                                                            {mealLogs.length > 0 && (
                                                                <span className="text-[10px] font-bold px-2 py-1 rounded-full bg-black/[0.04] text-[#49454F]">
                                                                    {mealLogs.length}
                                                                </span>
                                                            )}
                                                        </div>

                                                        {mealLogs.length > 0 && (
                                                            <div className="mt-2 space-y-2">
                                                                {mealLogs.map((log) => {
                                                                    const logTotals = (log.foods || []).reduce(
                                                                        (acc, food) => {
                                                                            acc.calories += food.calories || 0;
                                                                            acc.protein += food.protein || 0;
                                                                            acc.carbs += food.carbs || 0;
                                                                            acc.fats += food.fats || 0;
                                                                            return acc;
                                                                        },
                                                                        { calories: 0, protein: 0, carbs: 0, fats: 0 }
                                                                    );

                                                                    return (
                                                                        <div key={log.id} className="rounded-xl bg-white/75 border border-black/[0.05] px-3 py-2.5">
                                                                            <p className="text-[12px] font-semibold text-[#1D1B20] leading-snug">
                                                                                {(log.foods || []).map((food) => food.foodName).join(', ') || 'Comida registrada'}
                                                                            </p>
                                                                            <p className="text-[11px] text-[#49454F] mt-1 tabular-nums">
                                                                                {Math.round(logTotals.calories)} kcal · P {Math.round(logTotals.protein)}g · C {Math.round(logTotals.carbs)}g · G {Math.round(logTotals.fats)}g
                                                                            </p>
                                                                            {log.notes && <p className="text-[11px] text-[#49454F] mt-1">Nota: {log.notes}</p>}

                                                                            <div className="mt-2.5 flex items-center gap-1.5 flex-wrap">
                                                                                <button
                                                                                    onClick={() => handleEditLog(log)}
                                                                                    className="inline-flex items-center gap-1 rounded-lg border border-black/[0.09] px-2.5 py-1.5 text-[10px] font-black uppercase tracking-[0.12em] text-[#49454F]"
                                                                                >
                                                                                    <PencilIcon size={12} />
                                                                                    Editar
                                                                                </button>
                                                                                <button
                                                                                    onClick={() => handleDuplicateLog(log)}
                                                                                    className="rounded-lg border border-black/[0.09] px-2.5 py-1.5 text-[10px] font-black uppercase tracking-[0.12em] text-[#49454F]"
                                                                                >
                                                                                    Duplicar
                                                                                </button>
                                                                                <button
                                                                                    onClick={() => handleMoveLog(log)}
                                                                                    className="rounded-lg border border-black/[0.09] px-2.5 py-1.5 text-[10px] font-black uppercase tracking-[0.12em] text-[#49454F]"
                                                                                >
                                                                                    Mover
                                                                                </button>
                                                                                <button
                                                                                    onClick={() => handleDeleteLog(log)}
                                                                                    className="inline-flex items-center gap-1 rounded-lg border border-[#B3261E]/25 px-2.5 py-1.5 text-[10px] font-black uppercase tracking-[0.12em] text-[#8C1D18]"
                                                                                >
                                                                                    <TrashIcon size={12} />
                                                                                    Eliminar
                                                                                </button>
                                                                            </div>
                                                                        </div>
                                                                    );
                                                                })}
                                                            </div>
                                                        )}
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </section>

                                    <section className="bg-white/60 border border-black/[0.04] overflow-hidden" style={{ borderRadius: layout.sectionRadius }}>
                                        <div className="px-4 py-3 border-b border-black/[0.05]">
                                            <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Historial y hábitos</p>
                                            <p className="text-sm font-bold text-[#1D1B20] mt-0.5">Últimos 30 días</p>
                                        </div>

                                        <div className="p-4 space-y-3">
                                            <div className="rounded-xl bg-white/75 border border-black/[0.05] p-3">
                                                <p className="text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-2">Comidas registradas por día</p>
                                                {historyByDay30.length === 0 ? (
                                                    <p className="text-[12px] text-[#49454F]">¡Comienza a registrar tus alimentos y progreso para tener más información!</p>
                                                ) : (
                                                    <div className="space-y-2">
                                                        {historyByDay30.slice(0, 10).map((day) => (
                                                            <div key={day.day} className="flex items-center justify-between text-[12px]">
                                                                <span className="text-[#1D1B20] font-semibold">
                                                                    {new Date(`${day.day}T00:00:00`).toLocaleDateString('es-ES', {
                                                                        day: 'numeric',
                                                                        month: 'short',
                                                                    })}
                                                                </span>
                                                                <span className="text-[#49454F] tabular-nums">
                                                                    {Math.round(day.calories)} kcal · {day.logs} registros
                                                                </span>
                                                            </div>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>

                                            <div className="rounded-xl bg-white/75 border border-black/[0.05] p-3">
                                                <p className="text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-2">Alimentos más frecuentes</p>
                                                {commonFoods30.length === 0 ? (
                                                    <p className="text-[12px] text-[#49454F]">Aún no tenemos suficientes registros para detectar patrones.</p>
                                                ) : (
                                                    <div className="space-y-2">
                                                        {commonFoods30.map((food) => (
                                                            <div key={`${food.name}-${food.lastDate}`} className="flex items-center justify-between text-[12px]">
                                                                <span className="text-[#1D1B20] font-semibold truncate pr-2">{food.name}</span>
                                                                <span className="text-[#49454F] tabular-nums">{food.count}x · {food.avgCalories} kcal prom.</span>
                                                            </div>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </section>

                                    <section className="bg-white/60 border border-black/[0.04] overflow-hidden" style={{ borderRadius: layout.sectionRadius }}>
                                        <button
                                            onClick={() => setAnalyticsExpanded((current) => !current)}
                                            className="w-full px-4 py-3 flex items-center justify-between"
                                        >
                                            <div className="text-left">
                                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Análisis</p>
                                                <p className="text-sm font-bold text-[#1D1B20] mt-0.5">Tendencias, macros y microdeficiencias</p>
                                            </div>
                                            {analyticsExpanded ? (
                                                <ChevronUpIcon size={18} className="text-[#49454F]" />
                                            ) : (
                                                <ChevronDownIcon size={18} className="text-[#49454F]" />
                                            )}
                                        </button>

                                        {analyticsExpanded && (
                                            <div className="px-4 pb-4 pt-2 space-y-4 border-t border-black/[0.05]">
                                                <div className="h-44 rounded-2xl bg-white/75 border border-black/[0.04] p-2.5">
                                                    <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F] mb-1">Calorías (7 días)</p>
                                                    <ResponsiveContainer width="100%" height="100%">
                                                        <BarChart data={trendData}>
                                                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.08)" />
                                                            <XAxis dataKey="date" fontSize={10} stroke="#49454F" />
                                                            <YAxis fontSize={10} stroke="#49454F" />
                                                            <Tooltip />
                                                            {calorieGoal > 0 && (
                                                                <ReferenceLine y={Math.round(calorieGoal)} stroke="rgba(0,106,106,0.55)" strokeDasharray="4 4" />
                                                            )}
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
                                                <div className="grid grid-cols-1 gap-3">
                                                    <div className="rounded-2xl bg-white/75 border border-black/[0.04] p-3">
                                                        <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F] mb-2">Distribución de macros</p>
                                                        <div className="space-y-2.5">
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

                                                    <div className="rounded-2xl bg-white/75 border border-black/[0.04] p-3 flex items-center justify-center">
                                                        <ResponsiveContainer width="100%" height={128}>
                                                            <PieChart>
                                                                <Pie
                                                                    data={[
                                                                        { name: 'Prot', value: Math.max(0, dailyTotals.protein), color: MACRO_COLORS.protein },
                                                                        { name: 'Carb', value: Math.max(0, dailyTotals.carbs), color: MACRO_COLORS.carbs },
                                                                        { name: 'Grasa', value: Math.max(0, dailyTotals.fats), color: MACRO_COLORS.fats },
                                                                    ]}
                                                                    dataKey="value"
                                                                    innerRadius={34}
                                                                    outerRadius={52}
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

                                                <div className="rounded-2xl bg-white/75 border border-black/[0.04] p-3">
                                                    <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F] mb-2">Microdeficiencias detectadas</p>
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
                                    <section className="bg-white/60 border border-black/[0.04] overflow-hidden" style={{ borderRadius: layout.sectionRadius }}>
                                        <button
                                            onClick={() => toggleMetricsPanel('summary')}
                                            className="w-full px-4 py-3 flex items-center justify-between gap-3 text-left"
                                        >
                                            <div>
                                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Métricas corporales</p>
                                                <p className="text-sm font-bold text-[#1D1B20] mt-0.5">Peso, grasa, músculo y FFMI</p>
                                            </div>
                                            {metricsExpanded.summary ? (
                                                <ChevronUpIcon size={18} className="text-[#49454F]" />
                                            ) : (
                                                <ChevronDownIcon size={18} className="text-[#49454F]" />
                                            )}
                                        </button>
                                        {metricsExpanded.summary && (
                                            <div className="px-4 pb-4 border-t border-black/[0.05]">
                                                <div className="flex justify-end pt-3">
                                                    <button
                                                        onClick={() => setIsBodyLogModalOpen(true)}
                                                        className="rounded-xl px-4 py-2 text-[11px] font-black uppercase tracking-[0.14em] bg-[var(--md-sys-color-primary)] text-white"
                                                    >
                                                        Registrar métrica
                                                    </button>
                                                </div>
                                                <div className="grid grid-cols-2 gap-2.5 mt-3">
                                                    {bodyKpis.map((kpi) => (
                                                        <div key={kpi.label} className="rounded-xl bg-white/75 border border-black/[0.04] px-3 py-3">
                                                            <p className="text-[9px] font-black uppercase tracking-[0.14em] text-[#49454F]">{kpi.label}</p>
                                                            <p className="text-sm font-black text-[#1D1B20] mt-1">{kpi.value}</p>
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                    </section>

                                    <section className="bg-white/60 border border-black/[0.04] overflow-hidden" style={{ borderRadius: layout.sectionRadius }}>
                                        <button
                                            onClick={() => toggleMetricsPanel('projection')}
                                            className="w-full px-4 py-3 flex items-center justify-between gap-3 text-left"
                                        >
                                            <div>
                                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Proyección y tendencia</p>
                                                <p className="text-sm font-bold text-[#1D1B20] mt-0.5">ETA, confianza y alertas</p>
                                            </div>
                                            {metricsExpanded.projection ? (
                                                <ChevronUpIcon size={18} className="text-[#49454F]" />
                                            ) : (
                                                <ChevronDownIcon size={18} className="text-[#49454F]" />
                                            )}
                                        </button>
                                        {metricsExpanded.projection && (
                                            <div className="px-4 pb-4 border-t border-black/[0.05]">
                                                <div className="pt-3">
                                                    <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-[0.15em] ${trendBadge.classes}`}>
                                                        {trendBadge.label}
                                                    </span>
                                                </div>
                                                <p className="text-sm text-[#1D1B20] mt-2">
                                                    ETA objetivo:{' '}
                                                    <span className="font-black">{formatEta(planProjection?.etaDate ?? activePlan?.estimatedEndDate ?? null)}</span>
                                                    {planProjection?.confidence != null && (
                                                        <span className="text-[#49454F]"> · Confianza {Math.round(planProjection.confidence * 100)}%</span>
                                                    )}
                                                </p>
                                                {planRiskFlags.length > 0 ? (
                                                    <div className="mt-3 flex flex-wrap gap-2">
                                                        {planRiskFlags.slice(0, 3).map((flag) => (
                                                            <span key={flag.id} className={`px-2.5 py-1 rounded-full text-[10px] font-bold border ${getRiskClasses(flag.severity)}`}>
                                                                {flag.message}
                                                            </span>
                                                        ))}
                                                    </div>
                                                ) : (
                                                    <p className="text-[12px] text-[#49454F] mt-2">Sigue registrando para mejorar la precisión de esta proyección.</p>
                                                )}
                                            </div>
                                        )}
                                    </section>

                                    <section className="bg-white/60 border border-black/[0.04] overflow-hidden" style={{ borderRadius: layout.sectionRadius }}>
                                        <button
                                            onClick={() => toggleMetricsPanel('evolution')}
                                            className="w-full px-4 py-3 flex items-center justify-between gap-3 text-left"
                                        >
                                            <div>
                                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Evolución</p>
                                                <p className="text-sm font-bold text-[#1D1B20] mt-0.5">Gráficas de progreso corporal</p>
                                            </div>
                                            {metricsExpanded.evolution ? (
                                                <ChevronUpIcon size={18} className="text-[#49454F]" />
                                            ) : (
                                                <ChevronDownIcon size={18} className="text-[#49454F]" />
                                            )}
                                        </button>
                                        {metricsExpanded.evolution && (
                                            <div className="px-4 pb-4 border-t border-black/[0.05] space-y-3 pt-3">
                                                {sortedBodyLogs.length < 2 ? (
                                                    <p className="text-[12px] text-[#49454F]">¡Comienza a registrar tus alimentos y progreso para tener más información!</p>
                                                ) : (
                                                    <>
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
                                                    </>
                                                )}
                                            </div>
                                        )}
                                    </section>

                                    <section className="bg-white/60 border border-black/[0.04] overflow-hidden" style={{ borderRadius: layout.sectionRadius }}>
                                        <button
                                            onClick={() => toggleMetricsPanel('history')}
                                            className="w-full px-4 py-3 flex items-center justify-between gap-3 text-left"
                                        >
                                            <div>
                                                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Historial editable</p>
                                                <p className="text-sm font-bold text-[#1D1B20] mt-0.5">Peso, composición y medidas</p>
                                            </div>
                                            {metricsExpanded.history ? (
                                                <ChevronUpIcon size={18} className="text-[#49454F]" />
                                            ) : (
                                                <ChevronDownIcon size={18} className="text-[#49454F]" />
                                            )}
                                        </button>
                                        {metricsExpanded.history && (
                                            <div className="border-t border-black/[0.05]">
                                                {sortedBodyLogs.length === 0 ? (
                                                    <p className="px-5 py-6 text-sm text-[#49454F]">¡Comienza a registrar tus alimentos y progreso para tener más información!</p>
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
                            className="fixed z-20 rounded-2xl px-4 h-12 bg-[var(--md-sys-color-primary)] text-white text-sm font-black tracking-wide shadow-lg flex items-center gap-2 active:scale-[0.98] transition-transform"
                            style={{ bottom: layout.fabBottom, right: layout.pagePadX }}
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
