
import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
    BodyProgressLog,
    CalorieGoalConfig,
    NutritionGoal,
    NutritionGoalMetric,
    NutritionPlan,
    NutritionRiskFlag,
    Settings,
} from '../../types';
import { useAppDispatch, useAppState } from '../../contexts/AppContext';
import { getDatePartFromString, getLocalDateString } from '../../utils/dateUtils';
import {
    buildCalculationSnapshot,
    buildNutritionProjection,
    buildNutritionRiskFlags,
    estimateBodyFatFromAnthropometrics,
    estimateMuscleMassFromBodyFat,
} from '../../services/nutritionPlanEngine';
import {
    calculateBMR,
    calculateCaloriesForBodyFatTrend,
} from '../../utils/calorieFormulas';
import { AlertTriangleIcon } from '../icons';

interface NutritionWizardProps {
    onComplete: () => void;
}

type GoalDirection = 'lose' | 'maintain' | 'gain';

const TOTAL_STEPS = 5;
const ACTIVITY_FACTORS: Record<number, number> = {
    1: 1.2,
    2: 1.375,
    3: 1.55,
    4: 1.725,
    5: 1.9,
};

const METRIC_META: Record<NutritionGoalMetric, { label: string; unit: string; min: number; max: number; step: number }> = {
    weight: { label: 'Peso', unit: 'kg', min: 35, max: 260, step: 0.1 },
    bodyFat: { label: '% Grasa', unit: '%', min: 4, max: 55, step: 0.1 },
    muscleMass: { label: '% Músculo', unit: '%', min: 20, max: 60, step: 0.1 },
};

const GOAL_DIRECTION_META: Record<GoalDirection, { title: string; subtitle: string }> = {
    lose: { title: 'Definición', subtitle: 'Reduciremos grasa de forma segura y sostenible' },
    maintain: { title: 'Mantención', subtitle: 'Mantendremos tu composición corporal estable' },
    gain: { title: 'Volumen limpio', subtitle: 'Subiremos masa muscular con control y progresión' },
};

const DIET_OPTIONS = [
    { value: 'omnivore', label: 'Omnívoro' },
    { value: 'vegetarian', label: 'Vegetariano' },
    { value: 'vegan', label: 'Vegano' },
    { value: 'keto', label: 'Keto' },
] as const;

const METABOLIC_OPTIONS = [
    'Diabetes',
    'Resistencia a la insulina',
    'Hipotiroidismo',
    'Hipertiroidismo',
    'Síndrome metabólico',
];

const getRiskClasses = (severity: NutritionRiskFlag['severity']): string => {
    if (severity === 'danger') return 'bg-[#B3261E]/12 text-[#8C1D18] border-[#B3261E]/25';
    if (severity === 'warning') return 'bg-[#7D5700]/12 text-[#6B4F00] border-[#7D5700]/25';
    return 'bg-[#006A6A]/10 text-[#005353] border-[#006A6A]/20';
};

const getViewportWidth = (): number => {
    if (typeof window === 'undefined') return 390;
    return window.innerWidth || 390;
};

export const NutritionWizard: React.FC<NutritionWizardProps> = ({ onComplete }) => {
    const { settings, bodyProgress } = useAppState();
    const { setSettings, setNutritionPlans, setActiveNutritionPlanId, setBodyProgress, addToast } = useAppDispatch();

    const scrollRef = useRef<HTMLDivElement>(null);

    const [step, setStep] = useState(0);

    const [goalDirection, setGoalDirection] = useState<GoalDirection>(
        settings.calorieGoalObjective === 'deficit' ? 'lose' : settings.calorieGoalObjective === 'surplus' ? 'gain' : 'maintain'
    );
    const [primaryMetric, setPrimaryMetric] = useState<NutritionGoalMetric>('weight');
    const [primaryValue, setPrimaryValue] = useState<number | ''>(() => settings.userVitals?.targetWeight ?? settings.userVitals?.weight ?? 70);
    const [secondaryMetrics, setSecondaryMetrics] = useState<NutritionGoalMetric[]>([]);
    const [secondaryValues, setSecondaryValues] = useState<Record<NutritionGoalMetric, number | ''>>({
        weight: '',
        bodyFat: settings.userVitals?.bodyFatPercentage ?? '',
        muscleMass: settings.userVitals?.muscleMassPercentage ?? '',
    });

    const [age, setAge] = useState<number | ''>(settings.userVitals?.age ?? '');
    const [gender, setGender] = useState<NonNullable<typeof settings.userVitals.gender>>(settings.userVitals?.gender ?? 'male');
    const [height, setHeight] = useState<number | ''>(settings.userVitals?.height ?? '');
    const [weight, setWeight] = useState<number | ''>(settings.userVitals?.weight ?? '');

    const [bodyFat, setBodyFat] = useState<number | ''>(settings.userVitals?.bodyFatPercentage ?? '');
    const [muscleMass, setMuscleMass] = useState<number | ''>(settings.userVitals?.muscleMassPercentage ?? '');
    const [bodyFatQuality, setBodyFatQuality] = useState<'measured' | 'estimated'>('estimated');
    const [muscleMassQuality, setMuscleMassQuality] = useState<'measured' | 'estimated'>('estimated');
    const [autoEstimatedComposition, setAutoEstimatedComposition] = useState(false);
    const [compositionConfirmed, setCompositionConfirmed] = useState(false);

    const [activityLevel, setActivityLevel] = useState<number>(settings.calorieGoalConfig?.activityLevel ?? 3);
    const [useAdvancedActivity, setUseAdvancedActivity] = useState(false);
    const [activityDaysPerWeek, setActivityDaysPerWeek] = useState<number>(settings.calorieGoalConfig?.activityDaysPerWeek ?? 4);
    const [activityHoursPerDay, setActivityHoursPerDay] = useState<number>(settings.calorieGoalConfig?.activityHoursPerDay ?? 1);
    const [customActivityFactor, setCustomActivityFactor] = useState<number | ''>(settings.calorieGoalConfig?.customActivityFactor ?? '');

    const [formula, setFormula] = useState<CalorieGoalConfig['formula']>(settings.calorieGoalConfig?.formula ?? 'mifflin');
    const [trendMode, setTrendMode] = useState<'kg_per_week' | 'pct_fat_per_week'>('kg_per_week');
    const [weeklyChangeKg, setWeeklyChangeKg] = useState<number>(settings.calorieGoalConfig?.weeklyChangeKg ?? 0.45);
    const [bodyFatTrendPct, setBodyFatTrendPct] = useState<number>(0.25);
    const [healthMultiplier, setHealthMultiplier] = useState<number>(settings.calorieGoalConfig?.healthMultiplier ?? 1);
    const [dietPreference, setDietPreference] = useState<typeof DIET_OPTIONS[number]['value']>(
        (settings.dietaryPreference as typeof DIET_OPTIONS[number]['value']) ?? 'omnivore'
    );
    const [metabolicConditions, setMetabolicConditions] = useState<string[]>(settings.metabolicConditions ?? []);
    const [connectAuge, setConnectAuge] = useState<boolean>(settings.algorithmSettings?.augeEnableNutritionTracking ?? true);

    const [manualCalorieOverride, setManualCalorieOverride] = useState(false);
    const [manualCalories, setManualCalories] = useState<number | ''>('');
    const [manualMacros, setManualMacros] = useState(false);
    const [proteinGoal, setProteinGoal] = useState<number>(settings.dailyProteinGoal ?? 150);
    const [carbsGoal, setCarbsGoal] = useState<number>(settings.dailyCarbGoal ?? 220);
    const [fatsGoal, setFatsGoal] = useState<number>(settings.dailyFatGoal ?? 70);
    const [overrideAcknowledged, setOverrideAcknowledged] = useState(false);
    const [showTechnicalDetails, setShowTechnicalDetails] = useState(false);
    const [viewportWidth, setViewportWidth] = useState(getViewportWidth);

    useEffect(() => {
        scrollRef.current?.scrollTo({ top: 0, behavior: 'smooth' });
    }, [step]);

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
            headerPadY: isCompactPhone ? 12 : 16,
            sectionRadius: isCompactPhone ? 24 : 28,
            sectionPadding: isCompactPhone ? 16 : 20,
            headerTitleSize: isCompactPhone ? 20 : 22,
            footerPadY: isCompactPhone ? 10 : 12,
            contentBottom: isCompactPhone ? 120 : 112,
            stepLabelSize: isCompactPhone ? 8 : 9,
            stepGap: isPlusPhone ? 8 : 6,
        }),
        [isCompactPhone, isPlusPhone]
    );

    const sectionClass = 'bg-white/90 border border-black/[0.08] shadow-[0_14px_40px_-32px_rgba(0,0,0,0.5)]';

    useEffect(() => {
        if (step !== 2) return;
        if (bodyFat !== '' && muscleMass !== '') return;
        if (weight === '' || height === '' || age === '') return;

        const estimatedBodyFat = estimateBodyFatFromAnthropometrics({
            weightKg: Number(weight),
            heightCm: Number(height),
            age: Number(age),
            gender,
        });
        const estimatedMuscle = estimateMuscleMassFromBodyFat({
            bodyFatPct: estimatedBodyFat,
            gender,
        });

        if (bodyFat === '') {
            setBodyFat(estimatedBodyFat);
            setBodyFatQuality('estimated');
        }
        if (muscleMass === '') {
            setMuscleMass(estimatedMuscle);
            setMuscleMassQuality('estimated');
        }

        setAutoEstimatedComposition(true);
        setCompositionConfirmed(false);
    }, [step, bodyFat, muscleMass, weight, height, age, gender]);

    useEffect(() => {
        if (bodyFat !== '' && muscleMass !== '') {
            setFormula('katch');
        }
    }, [bodyFat, muscleMass]);

    const activityFactor = useMemo(() => {
        if (customActivityFactor !== '') return Number(customActivityFactor);
        if (!useAdvancedActivity) return ACTIVITY_FACTORS[activityLevel] ?? 1.55;

        const days = Math.min(7, Math.max(0, activityDaysPerWeek));
        const hours = Math.min(24, Math.max(0, activityHoursPerDay));
        return Math.min(2, Math.max(1, 1.2 + (days / 7) * 0.45 + (hours / 12) * 0.25));
    }, [activityDaysPerWeek, activityHoursPerDay, activityLevel, customActivityFactor, useAdvancedActivity]);

    const userVitalsDraft = useMemo(() => ({
        ...settings.userVitals,
        age: age === '' ? undefined : Number(age),
        gender,
        height: height === '' ? undefined : Number(height),
        weight: weight === '' ? undefined : Number(weight),
        bodyFatPercentage: bodyFat === '' ? undefined : Number(bodyFat),
        muscleMassPercentage: muscleMass === '' ? undefined : Number(muscleMass),
        activityLevel: (['sedentary', 'light', 'moderate', 'active', 'very_active'][Math.max(0, Math.min(4, activityLevel - 1))] as any),
    }), [activityLevel, age, bodyFat, gender, height, muscleMass, settings.userVitals, weight]);

    const calorieGoalConfig = useMemo<CalorieGoalConfig>(() => ({
        formula,
        activityLevel,
        goal: goalDirection,
        weeklyChangeKg,
        healthMultiplier,
        ...(customActivityFactor !== '' ? { customActivityFactor: Number(customActivityFactor) } : {}),
        ...(useAdvancedActivity ? { activityDaysPerWeek, activityHoursPerDay } : {}),
    }), [activityDaysPerWeek, activityHoursPerDay, activityLevel, customActivityFactor, formula, goalDirection, healthMultiplier, useAdvancedActivity, weeklyChangeKg]);

    const effectiveSettings = useMemo<Settings>(() => ({
        ...settings,
        userVitals: userVitalsDraft,
        calorieGoalObjective: goalDirection === 'lose' ? 'deficit' : goalDirection === 'gain' ? 'surplus' : 'maintenance',
    }), [goalDirection, settings, userVitalsDraft]);

    const bmr = useMemo(() => calculateBMR(effectiveSettings, calorieGoalConfig), [calorieGoalConfig, effectiveSettings]);

    const tdee = useMemo(() => {
        if (bmr == null) return null;
        return Math.round(bmr * activityFactor * healthMultiplier);
    }, [activityFactor, bmr, healthMultiplier]);

    const recommendedCalories = useMemo(() => {
        if (tdee == null) return 0;
        const weightKg = weight === '' ? (settings.userVitals?.weight ?? 70) : Number(weight);

        if (goalDirection === 'maintain') return Math.round(tdee);

        if (trendMode === 'pct_fat_per_week') {
            const signedTrend = goalDirection === 'lose' ? -Math.abs(bodyFatTrendPct) : Math.abs(bodyFatTrendPct);
            return calculateCaloriesForBodyFatTrend(tdee, weightKg, signedTrend);
        }

        const weekly = Math.abs(weeklyChangeKg);
        const kcalShift = (weekly * 7700) / 7;
        return Math.round(goalDirection === 'lose' ? tdee - kcalShift : tdee + kcalShift);
    }, [bodyFatTrendPct, goalDirection, settings.userVitals?.weight, tdee, trendMode, weeklyChangeKg, weight]);

    const calorieTarget = useMemo(() => {
        if (manualCalorieOverride && manualCalories !== '') return Number(manualCalories);
        return recommendedCalories;
    }, [manualCalorieOverride, manualCalories, recommendedCalories]);

    const recommendedMacros = useMemo(() => {
        const weightKg = weight === '' ? 70 : Number(weight);
        const proteinFactor = goalDirection === 'gain' ? 2.2 : goalDirection === 'lose' ? 2.0 : 1.8;
        const veganFactor = dietPreference === 'vegan' ? 1.12 : dietPreference === 'vegetarian' ? 1.06 : 1;
        const protein = Math.round(weightKg * proteinFactor * veganFactor);
        const fats = Math.max(45, Math.round(weightKg * 0.75));
        const carbs = Math.max(40, Math.round((calorieTarget - protein * 4 - fats * 9) / 4));

        return { protein, carbs, fats };
    }, [calorieTarget, dietPreference, goalDirection, weight]);

    useEffect(() => {
        if (manualMacros) return;
        setProteinGoal(recommendedMacros.protein);
        setCarbsGoal(recommendedMacros.carbs);
        setFatsGoal(recommendedMacros.fats);
    }, [manualMacros, recommendedMacros]);

    const weeklyEquivalentKg = useMemo(() => {
        if (trendMode === 'kg_per_week') return Math.abs(weeklyChangeKg);
        const weightKg = weight === '' ? (settings.userVitals?.weight ?? 70) : Number(weight);
        return Math.abs((bodyFatTrendPct / 100) * weightKg);
    }, [bodyFatTrendPct, settings.userVitals?.weight, trendMode, weeklyChangeKg, weight]);

    const riskFlags = useMemo(() => {
        return buildNutritionRiskFlags({
            settings: effectiveSettings,
            calorieTarget,
            goalMetric: primaryMetric,
            goalValue: primaryValue === '' ? 0 : Number(primaryValue),
            weeklyChangeKg: weeklyEquivalentKg,
        });
    }, [calorieTarget, effectiveSettings, primaryMetric, primaryValue, weeklyEquivalentKg]);

    const hasHardStop = riskFlags.some((flag) => flag.hardStop);
    const needsExplicitOverride = riskFlags.some((flag) => !flag.hardStop && (flag.severity === 'warning' || flag.severity === 'danger'));

    const macroCalories = useMemo(() => proteinGoal * 4 + carbsGoal * 4 + fatsGoal * 9, [proteinGoal, carbsGoal, fatsGoal]);
    const estimatedEndDate = useMemo(() => {
        if (primaryValue === '' || goalDirection === 'maintain') return null;

        const now = new Date();
        const goal = Number(primaryValue);
        let weeks = 0;

        if (primaryMetric === 'weight') {
            const current = weight === '' ? (settings.userVitals?.weight ?? goal) : Number(weight);
            const deltaPerWeek = trendMode === 'kg_per_week'
                ? (goalDirection === 'lose' ? -Math.abs(weeklyChangeKg) : Math.abs(weeklyChangeKg))
                : (goalDirection === 'lose' ? -0.35 : 0.35);
            if (deltaPerWeek === 0) return null;
            weeks = Math.abs((goal - current) / deltaPerWeek);
        } else if (primaryMetric === 'bodyFat') {
            const current = bodyFat === '' ? (settings.userVitals?.bodyFatPercentage ?? goal) : Number(bodyFat);
            const deltaPerWeek = trendMode === 'pct_fat_per_week'
                ? (goalDirection === 'lose' ? -Math.abs(bodyFatTrendPct) : Math.abs(bodyFatTrendPct))
                : (goalDirection === 'lose' ? -0.3 : 0.3);
            if (deltaPerWeek === 0) return null;
            weeks = Math.abs((goal - current) / deltaPerWeek);
        } else {
            const current = muscleMass === '' ? (settings.userVitals?.muscleMassPercentage ?? goal) : Number(muscleMass);
            const deltaPerWeek: number = goalDirection === 'lose' ? -0.12 : 0.18;
            weeks = Math.abs((goal - current) / deltaPerWeek);
        }

        if (!Number.isFinite(weeks) || weeks <= 0) return null;

        const end = new Date(now);
        end.setDate(end.getDate() + Math.ceil(weeks * 7));
        return end.toISOString().slice(0, 10);
    }, [
        bodyFat,
        bodyFatTrendPct,
        goalDirection,
        muscleMass,
        primaryMetric,
        primaryValue,
        settings.userVitals?.bodyFatPercentage,
        settings.userVitals?.muscleMassPercentage,
        settings.userVitals?.weight,
        trendMode,
        weeklyChangeKg,
        weight,
    ]);

    const stepValidity = useMemo(() => {
        const step0 = primaryValue !== '' && Number(primaryValue) > 0;
        const step1 =
            age !== '' &&
            Number(age) > 0 &&
            height !== '' &&
            Number(height) > 0 &&
            weight !== '' &&
            Number(weight) > 0 &&
            !!gender;
        const step2 = bodyFat !== '' && muscleMass !== '' && (!autoEstimatedComposition || compositionConfirmed);
        const step3 = activityLevel >= 1 && activityLevel <= 5;
        const step4 = calorieTarget > 0 && !hasHardStop && (!needsExplicitOverride || overrideAcknowledged);
        return [step0, step1, step2, step3, step4];
    }, [
        activityLevel,
        age,
        autoEstimatedComposition,
        bodyFat,
        calorieTarget,
        compositionConfirmed,
        gender,
        hasHardStop,
        height,
        muscleMass,
        needsExplicitOverride,
        overrideAcknowledged,
        primaryValue,
        weight,
    ]);

    const buildGoals = (): { primaryGoal: NutritionGoal; secondaryGoals: NutritionGoal[] } => {
        const primaryGoal: NutritionGoal = {
            metric: primaryMetric,
            value: Number(primaryValue),
            label: METRIC_META[primaryMetric].label,
            unit: METRIC_META[primaryMetric].unit,
            priority: 'primary',
        };

        const secondaryGoals: NutritionGoal[] = secondaryMetrics
            .filter((metric) => metric !== primaryMetric)
            .map((metric): NutritionGoal => ({
                metric,
                value: Number(secondaryValues[metric]),
                label: METRIC_META[metric].label,
                unit: METRIC_META[metric].unit,
                priority: 'secondary',
            }))
            .filter((goal) => Number.isFinite(goal.value) && goal.value > 0);

        return { primaryGoal, secondaryGoals };
    };

    const handleNext = () => {
        if (!stepValidity[step]) {
            if (step === 2 && autoEstimatedComposition && !compositionConfirmed) {
                addToast('Confirma o edita la composición corporal antes de continuar.', 'danger');
                return;
            }
            addToast('Completa los datos de este paso para continuar.', 'danger');
            return;
        }

        if (step < TOTAL_STEPS - 1) {
            setStep((prev) => prev + 1);
            return;
        }

        const { primaryGoal, secondaryGoals } = buildGoals();

        const estimatedWeekly = trendMode === 'kg_per_week' ? Math.abs(weeklyChangeKg) : weeklyEquivalentKg;
        const normalizedConfig: CalorieGoalConfig = {
            ...calorieGoalConfig,
            weeklyChangeKg: estimatedWeekly,
        };

        const draftPlanBase: NutritionPlan = {
            id: `np-${Date.now()}`,
            name: `${METRIC_META[primaryMetric].label} ${Number(primaryValue)}${METRIC_META[primaryMetric].unit}`,
            goalType: primaryMetric,
            goalValue: Number(primaryValue),
            trendMode,
            trendValue:
                trendMode === 'kg_per_week'
                    ? goalDirection === 'lose'
                        ? -Math.abs(weeklyChangeKg)
                        : Math.abs(weeklyChangeKg)
                    : goalDirection === 'lose'
                      ? -Math.abs(bodyFatTrendPct)
                      : Math.abs(bodyFatTrendPct),
            startDate: getLocalDateString(),
            estimatedEndDate: estimatedEndDate ?? undefined,
            calorieGoalConfig: normalizedConfig,
            isActive: true,
            createdAt: new Date().toISOString(),
            primaryGoal,
            secondaryGoals,
            calculationSnapshot: buildCalculationSnapshot({
                settings: {
                    ...effectiveSettings,
                    dailyCalorieGoal: calorieTarget,
                },
                calorieGoalConfig: normalizedConfig,
            }),
            riskFlags,
        };

        const projection = buildNutritionProjection({
            plan: draftPlanBase,
            bodyProgress,
            settings: {
                ...effectiveSettings,
                dailyCalorieGoal: calorieTarget,
            },
        });

        const finalPlan: NutritionPlan = {
            ...draftPlanBase,
            projection: {
                ...projection,
                etaDate: projection.etaDate ?? estimatedEndDate,
            },
        };

        setNutritionPlans((prev) => {
            const rest = prev.map((plan) => ({ ...plan, isActive: false }));
            return [...rest, finalPlan];
        });
        setActiveNutritionPlanId(finalPlan.id);

        const activityKey = ['sedentary', 'light', 'moderate', 'active', 'very_active'][
            Math.max(0, Math.min(4, activityLevel - 1))
        ] as any;

        setSettings({
            hasSeenNutritionWizard: true,
            nutritionWizardVersion: 2,
            hasDismissedNutritionSetup: true,
            calorieGoalObjective:
                goalDirection === 'lose' ? 'deficit' : goalDirection === 'gain' ? 'surplus' : 'maintenance',
            calorieGoalConfig: normalizedConfig,
            dailyCalorieGoal: calorieTarget,
            dailyProteinGoal: proteinGoal,
            dailyCarbGoal: carbsGoal,
            dailyFatGoal: fatsGoal,
            dietaryPreference: dietPreference,
            metabolicConditions,
            userVitals: {
                ...settings.userVitals,
                age: Number(age),
                gender,
                height: Number(height),
                weight: Number(weight),
                bodyFatPercentage: Number(bodyFat),
                muscleMassPercentage: Number(muscleMass),
                activityLevel: activityKey,
                ...(primaryMetric === 'weight' ? { targetWeight: Number(primaryValue) } : {}),
                targetDate: finalPlan.projection?.etaDate ?? estimatedEndDate ?? undefined,
            },
            algorithmSettings: {
                ...settings.algorithmSettings,
                augeEnableNutritionTracking: connectAuge,
            },
        });

        setBodyProgress((prev) => {
            const today = getLocalDateString();
            const hasTodayLog = prev.some((log) => getDatePartFromString(log.date) === today);
            if (hasTodayLog) return prev;

            const baselineLog: BodyProgressLog = {
                id: crypto.randomUUID(),
                date: new Date().toISOString(),
                weight: Number(weight),
                bodyFatPercentage: Number(bodyFat),
                muscleMassPercentage: Number(muscleMass),
                bodyFatQuality,
                muscleMassQuality,
            };

            return [...prev, baselineLog];
        });

        addToast('Plan nutricional creado y activado.', 'success');
        onComplete();
    };

    const toggleSecondaryMetric = (metric: NutritionGoalMetric) => {
        if (metric === primaryMetric) return;
        setSecondaryMetrics((prev) =>
            prev.includes(metric) ? prev.filter((item) => item !== metric) : [...prev, metric]
        );
    };

    const renderHeader = () => (
        <div className="sticky top-0 z-20 backdrop-blur-2xl bg-white/95 border-b border-black/[0.08] shadow-lg">
            <div style={{ paddingLeft: layout.pagePadX, paddingRight: layout.pagePadX, paddingTop: layout.headerPadY, paddingBottom: layout.headerPadY }}>
                <p className="text-[10px] uppercase tracking-[0.18em] font-black text-[#49454F]">Configurar plan de alimentación</p>
                <h2 className="font-black text-[#1D1B20] mt-1" style={{ fontSize: layout.headerTitleSize }}>Asistente de plan nutricional</h2>
                <div className="grid grid-cols-5 mt-4" style={{ gap: layout.stepGap }}>
                    {Array.from({ length: TOTAL_STEPS }).map((_, index) => (
                        <div key={index} className="space-y-1">
                            <div
                                className="h-1.5 rounded-full"
                                style={{
                                    background:
                                        index < step
                                            ? 'var(--md-sys-color-primary)'
                                            : index === step
                                              ? 'rgba(0,106,106,0.5)'
                                              : 'rgba(0,0,0,0.12)',
                                }}
                            />
                            <p className="font-black uppercase tracking-[0.16em] text-[#49454F]" style={{ fontSize: layout.stepLabelSize }}>
                                Paso {index + 1}
                            </p>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
    const renderStep0 = () => (
        <div className="space-y-4">
            <section className={sectionClass} style={{ borderRadius: layout.sectionRadius, padding: layout.sectionPadding }}>
                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Meta principal</p>
                <div className="grid grid-cols-1 gap-2 mt-3">
                    {(Object.keys(METRIC_META) as NutritionGoalMetric[]).map((metric) => (
                        <button
                            key={metric}
                            onClick={() => {
                                setPrimaryMetric(metric);
                                setSecondaryMetrics((prev) => prev.filter((item) => item !== metric));
                            }}
                            className={`rounded-2xl border px-4 py-3 text-left transition-colors ${
                                primaryMetric === metric
                                    ? 'bg-[#006A6A]/10 border-[#006A6A]/40'
                                    : 'bg-white/70 border-black/[0.08] hover:bg-white'
                            }`}
                        >
                            <p className="text-sm font-black text-[#1D1B20]">{METRIC_META[metric].label}</p>
                            <p className="text-xs text-[#49454F] mt-1">Objetivo en {METRIC_META[metric].unit}</p>
                        </button>
                    ))}
                </div>

                <div className="mt-4 grid grid-cols-1 gap-3 items-end">
                    <div>
                        <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Objetivo</label>
                        <input
                            type="number"
                            value={primaryValue}
                            onChange={(event) => {
                                const value = event.target.value;
                                setPrimaryValue(value === '' ? '' : Number(value));
                            }}
                            min={METRIC_META[primaryMetric].min}
                            max={METRIC_META[primaryMetric].max}
                            step={METRIC_META[primaryMetric].step}
                            className="w-full rounded-xl border border-black/[0.12] bg-white px-3 py-2.5 text-sm text-[#1D1B20]"
                        />
                    </div>
                    <p className="text-sm text-[#49454F]">Meta actual: <span className="font-bold text-[#1D1B20]">{METRIC_META[primaryMetric].label}</span> en {METRIC_META[primaryMetric].unit}</p>
                </div>
            </section>

            <section className={sectionClass} style={{ borderRadius: layout.sectionRadius, padding: layout.sectionPadding }}>
                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Dirección del plan</p>
                <div className="grid grid-cols-1 gap-2 mt-3">
                    {(Object.keys(GOAL_DIRECTION_META) as GoalDirection[]).map((direction) => (
                        <button
                            key={direction}
                            onClick={() => setGoalDirection(direction)}
                            className={`rounded-2xl border px-4 py-3 text-left transition-colors ${
                                goalDirection === direction
                                    ? 'bg-[#006A6A]/10 border-[#006A6A]/40'
                                    : 'bg-white/70 border-black/[0.08] hover:bg-white'
                            }`}
                        >
                            <p className="text-sm font-black text-[#1D1B20]">{GOAL_DIRECTION_META[direction].title}</p>
                            <p className="text-xs text-[#49454F] mt-1">{GOAL_DIRECTION_META[direction].subtitle}</p>
                        </button>
                    ))}
                </div>
            </section>

            <section className={sectionClass} style={{ borderRadius: layout.sectionRadius, padding: layout.sectionPadding }}>
                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Metas secundarias</p>
                <div className="grid grid-cols-1 gap-2 mt-3">
                    {(Object.keys(METRIC_META) as NutritionGoalMetric[])
                        .filter((metric) => metric !== primaryMetric)
                        .map((metric) => {
                            const active = secondaryMetrics.includes(metric);
                            return (
                                <div key={metric} className="rounded-2xl border border-black/[0.08] bg-white p-3">
                                    <label className="flex items-center gap-2 text-sm font-bold text-[#1D1B20]">
                                        <input type="checkbox" checked={active} onChange={() => toggleSecondaryMetric(metric)} />
                                        {METRIC_META[metric].label}
                                    </label>
                                    {active && (
                                        <input
                                            type="number"
                                            value={secondaryValues[metric]}
                                            onChange={(event) => {
                                                const value = event.target.value;
                                                setSecondaryValues((prev) => ({
                                                    ...prev,
                                                    [metric]: value === '' ? '' : Number(value),
                                                }));
                                            }}
                                            min={METRIC_META[metric].min}
                                            max={METRIC_META[metric].max}
                                            step={METRIC_META[metric].step}
                                            className="w-full mt-2 rounded-xl border border-black/[0.12] px-3 py-2 text-sm"
                                        />
                                    )}
                                </div>
                            );
                        })}
                </div>
            </section>
        </div>
    );

    const renderStep1 = () => (
        <div className="space-y-4">
            <section className={sectionClass} style={{ borderRadius: layout.sectionRadius, padding: layout.sectionPadding }}>
                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Datos base obligatorios</p>
                <div className="grid grid-cols-1 gap-3 mt-3">
                    <div>
                        <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Edad</label>
                        <input type="number" value={age} onChange={(event) => { const value = event.target.value; setAge(value === '' ? '' : Number(value)); }} min={10} max={100} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                    </div>
                    <div>
                        <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Estatura (cm)</label>
                        <input type="number" value={height} onChange={(event) => { const value = event.target.value; setHeight(value === '' ? '' : Number(value)); }} min={120} max={230} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                    </div>
                    <div>
                        <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Peso (kg)</label>
                        <input type="number" value={weight} onChange={(event) => { const value = event.target.value; setWeight(value === '' ? '' : Number(value)); }} min={30} max={260} step={0.1} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                    </div>
                    <div>
                        <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Sexo</label>
                        <select value={gender} onChange={(event) => setGender(event.target.value as any)} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm">
                            <option value="male">Hombre</option>
                            <option value="female">Mujer</option>
                            <option value="transmale">Trans masculino</option>
                            <option value="transfemale">Trans femenino</option>
                            <option value="other">Otro</option>
                        </select>
                    </div>
                </div>
            </section>
        </div>
    );

    const renderStep2 = () => (
        <div className="space-y-4">
            <section className={sectionClass} style={{ borderRadius: layout.sectionRadius, padding: layout.sectionPadding }}>
                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Composición corporal obligatoria</p>
                <div className="grid grid-cols-1 gap-3 mt-3">
                    <div className="rounded-2xl border border-black/[0.08] bg-white p-4">
                        <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">% Grasa</label>
                        <input type="number" value={bodyFat} onChange={(event) => { const value = event.target.value; setBodyFat(value === '' ? '' : Number(value)); setCompositionConfirmed(true); }} min={4} max={55} step={0.1} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                        <select value={bodyFatQuality} onChange={(event) => setBodyFatQuality(event.target.value as any)} className="w-full mt-2 rounded-xl border border-black/[0.12] px-3 py-2 text-xs">
                            <option value="measured">Dato medido</option>
                            <option value="estimated">Dato estimado</option>
                        </select>
                    </div>

                    <div className="rounded-2xl border border-black/[0.08] bg-white p-4">
                        <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">% Músculo</label>
                        <input type="number" value={muscleMass} onChange={(event) => { const value = event.target.value; setMuscleMass(value === '' ? '' : Number(value)); setCompositionConfirmed(true); }} min={20} max={60} step={0.1} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                        <select value={muscleMassQuality} onChange={(event) => setMuscleMassQuality(event.target.value as any)} className="w-full mt-2 rounded-xl border border-black/[0.12] px-3 py-2 text-xs">
                            <option value="measured">Dato medido</option>
                            <option value="estimated">Dato estimado</option>
                        </select>
                    </div>
                </div>

                {autoEstimatedComposition && (
                    <div className="mt-4 rounded-2xl border border-[#7D5700]/20 bg-[#7D5700]/10 p-4">
                        <p className="text-sm font-bold text-[#6B4F00]">Te sugerimos una estimación inicial para ayudarte a partir. Revísala y confírmala antes de continuar.</p>
                        <button onClick={() => setCompositionConfirmed(true)} className="mt-3 px-4 py-2 rounded-xl text-xs font-black uppercase tracking-[0.14em] bg-[#7D5700]/20 text-[#6B4F00]">Confirmar estimación</button>
                    </div>
                )}
            </section>
        </div>
    );
    const renderStep3 = () => (
        <div className="space-y-4">
            <section className={sectionClass} style={{ borderRadius: layout.sectionRadius, padding: layout.sectionPadding }}>
                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Actividad y estilo de alimentación</p>
                <p className="text-sm text-[#49454F] mt-1">Ajustamos tu plan según tu semana real, no según un escenario ideal.</p>

                <div className="grid grid-cols-1 gap-2 mt-3">
                    {[
                        { level: 1, label: 'Muy baja actividad' },
                        { level: 2, label: 'Actividad ligera' },
                        { level: 3, label: 'Actividad moderada' },
                        { level: 4, label: 'Actividad alta' },
                        { level: 5, label: 'Actividad muy alta' },
                    ].map((item) => (
                        <button
                            key={item.level}
                            onClick={() => setActivityLevel(item.level)}
                            className={`rounded-xl border px-3 py-2.5 text-left text-xs font-black uppercase tracking-[0.12em] ${
                                activityLevel === item.level
                                    ? 'bg-[#006A6A]/10 border-[#006A6A]/40 text-[#005353]'
                                    : 'bg-white border-black/[0.08] text-[#49454F]'
                            }`}
                        >
                            {item.label}
                        </button>
                    ))}
                </div>

                <div className="mt-4 flex items-center gap-2">
                    <input
                        id="advanced-activity"
                        type="checkbox"
                        checked={useAdvancedActivity}
                        onChange={(event) => setUseAdvancedActivity(event.target.checked)}
                    />
                    <label htmlFor="advanced-activity" className="text-sm text-[#1D1B20]">Quiero ajustar mi actividad con más detalle</label>
                </div>

                {useAdvancedActivity && (
                    <div className="grid grid-cols-1 gap-3 mt-3">
                        <div>
                            <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Días activos por semana</label>
                            <input type="number" value={activityDaysPerWeek} onChange={(event) => setActivityDaysPerWeek(Number(event.target.value) || 0)} min={0} max={7} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                        </div>
                        <div>
                            <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Horas activas por día</label>
                            <input type="number" value={activityHoursPerDay} onChange={(event) => setActivityHoursPerDay(Number(event.target.value) || 0)} min={0} max={24} step={0.5} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                        </div>
                        <div>
                            <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Factor personalizado (opcional)</label>
                            <input type="number" value={customActivityFactor} onChange={(event) => { const value = event.target.value; setCustomActivityFactor(value === '' ? '' : Number(value)); }} min={1} max={2} step={0.01} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                        </div>
                    </div>
                )}

                <div className="mt-4 grid grid-cols-1 gap-3">
                    <div>
                        <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Preferencia alimentaria</label>
                        <select value={dietPreference} onChange={(event) => setDietPreference(event.target.value as any)} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm">
                            {DIET_OPTIONS.map((option) => (
                                <option key={option.value} value={option.value}>{option.label}</option>
                            ))}
                        </select>
                    </div>
                </div>

                <div className="mt-4 rounded-2xl border border-black/[0.08] bg-white p-4">
                    <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Ritmo de progreso deseado</p>
                    <div className="flex gap-2 mt-2">
                        <button onClick={() => setTrendMode('kg_per_week')} className={`px-3 py-1.5 rounded-xl text-xs font-black uppercase ${trendMode === 'kg_per_week' ? 'bg-[#006A6A]/10 text-[#005353]' : 'bg-black/[0.06] text-[#49454F]'}`}>kg/semana</button>
                        <button onClick={() => setTrendMode('pct_fat_per_week')} className={`px-3 py-1.5 rounded-xl text-xs font-black uppercase ${trendMode === 'pct_fat_per_week' ? 'bg-[#006A6A]/10 text-[#005353]' : 'bg-black/[0.06] text-[#49454F]'}`}>% grasa/sem</button>
                    </div>

                    {trendMode === 'kg_per_week' ? (
                        <div className="mt-3">
                            <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Cambio semanal (kg)</label>
                            <input type="number" value={weeklyChangeKg} onChange={(event) => setWeeklyChangeKg(Math.abs(Number(event.target.value) || 0))} min={0.1} max={2} step={0.05} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                        </div>
                    ) : (
                        <div className="mt-3">
                            <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Cambio % grasa/semana</label>
                            <input type="number" value={bodyFatTrendPct} onChange={(event) => setBodyFatTrendPct(Math.abs(Number(event.target.value) || 0))} min={0.05} max={1} step={0.05} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                        </div>
                    )}
                </div>

                <div className="mt-4">
                    <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F] mb-2">Condiciones metabólicas (opcional)</p>
                    <div className="flex flex-wrap gap-2">
                        {METABOLIC_OPTIONS.map((condition) => {
                            const active = metabolicConditions.includes(condition);
                            return (
                                <button
                                    key={condition}
                                    onClick={() => {
                                        setMetabolicConditions((prev) => active ? prev.filter((item) => item !== condition) : [...prev, condition]);
                                    }}
                                    className={`px-3 py-1.5 rounded-full text-xs font-bold border ${active ? 'bg-[#006A6A]/10 border-[#006A6A]/30 text-[#005353]' : 'bg-white border-black/[0.12] text-[#49454F]'}`}
                                >
                                    {condition}
                                </button>
                            );
                        })}
                    </div>
                </div>

                <div className="mt-4 flex items-center gap-2">
                    <input id="connect-auge" type="checkbox" checked={connectAuge} onChange={(event) => setConnectAuge(event.target.checked)} />
                    <label htmlFor="connect-auge" className="text-sm text-[#1D1B20]">Vincular nutrición con el sistema AUGE</label>
                </div>
            </section>
        </div>
    );

    const renderStep4 = () => (
        <div className="space-y-4">
            <section className={sectionClass} style={{ borderRadius: layout.sectionRadius, padding: layout.sectionPadding }}>
                <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Resumen final y seguridad</p>
                <p className="text-sm text-[#49454F] mt-1">Revisa tu plan y, si quieres, ajusta manualmente antes de activarlo.</p>

                <div className="grid grid-cols-1 gap-2 mt-3">
                    <div className="rounded-2xl bg-white border border-black/[0.08] px-3 py-3"><p className="text-[10px] uppercase font-black tracking-[0.14em] text-[#49454F]">Calorías objetivo</p><p className="text-lg font-black text-[#1D1B20] mt-1">{Math.round(calorieTarget)} kcal</p></div>
                    <div className="rounded-2xl bg-white border border-black/[0.08] px-3 py-3"><p className="text-[10px] uppercase font-black tracking-[0.14em] text-[#49454F]">Macros diarios</p><p className="text-sm font-black text-[#1D1B20] mt-1">P {proteinGoal}g · C {carbsGoal}g · G {fatsGoal}g</p></div>
                </div>

                <div className="mt-4 rounded-2xl border border-black/[0.08] bg-white p-4">
                    <label className="flex items-center gap-2 text-sm font-semibold text-[#1D1B20]"><input type="checkbox" checked={manualCalorieOverride} onChange={(event) => { const checked = event.target.checked; setManualCalorieOverride(checked); if (!checked) setManualCalories(''); }} />Ajustar calorías manualmente</label>
                    {manualCalorieOverride && (
                        <input type="number" value={manualCalories} onChange={(event) => { const value = event.target.value; setManualCalories(value === '' ? '' : Number(value)); }} min={800} max={6000} className="w-full mt-2 rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                    )}
                </div>

                <div className="mt-4 rounded-2xl border border-black/[0.08] bg-white p-4">
                    <label className="flex items-center gap-2 text-sm font-semibold text-[#1D1B20]"><input type="checkbox" checked={manualMacros} onChange={(event) => setManualMacros(event.target.checked)} />Ajustar macros manualmente</label>
                    <div className="grid grid-cols-1 gap-3 mt-3">
                        <div><label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Proteína (g)</label><input type="number" value={proteinGoal} onChange={(event) => setProteinGoal(Number(event.target.value) || 0)} disabled={!manualMacros} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm disabled:opacity-60" /></div>
                        <div><label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Carbohidratos (g)</label><input type="number" value={carbsGoal} onChange={(event) => setCarbsGoal(Number(event.target.value) || 0)} disabled={!manualMacros} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm disabled:opacity-60" /></div>
                        <div><label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Grasas (g)</label><input type="number" value={fatsGoal} onChange={(event) => setFatsGoal(Number(event.target.value) || 0)} disabled={!manualMacros} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm disabled:opacity-60" /></div>
                    </div>
                    <p className="text-xs text-[#49454F] mt-2">Macros totalizan {Math.round(macroCalories)} kcal</p>
                </div>

                <button onClick={() => setShowTechnicalDetails((prev) => !prev)} className="mt-4 w-full rounded-xl border border-black/[0.12] px-3 py-2 text-xs font-black uppercase tracking-[0.14em] text-[#49454F]">
                    {showTechnicalDetails ? 'Ocultar detalles técnicos' : 'Ver detalles técnicos'}
                </button>

                {showTechnicalDetails && (
                    <div className="mt-3 rounded-2xl border border-black/[0.08] bg-white p-4 space-y-3">
                        <div>
                            <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Método de cálculo</label>
                            <select value={formula} onChange={(event) => setFormula(event.target.value as any)} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm">
                                <option value="mifflin">Mifflin-St Jeor</option>
                                <option value="harris">Harris-Benedict</option>
                                <option value="katch">Katch-McArdle</option>
                            </select>
                        </div>
                        <div>
                            <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Factor de salud</label>
                            <input type="number" value={healthMultiplier} onChange={(event) => setHealthMultiplier(Number(event.target.value) || 1)} min={0.8} max={1.2} step={0.01} className="w-full rounded-xl border border-black/[0.12] px-3 py-2.5 text-sm" />
                        </div>
                        <div className="grid grid-cols-1 gap-2">
                            <div className="rounded-xl border border-black/[0.08] px-3 py-2"><p className="text-[10px] uppercase font-black tracking-[0.14em] text-[#49454F]">BMR</p><p className="text-sm font-black text-[#1D1B20] mt-0.5">{bmr != null ? Math.round(bmr) : '—'} kcal</p></div>
                            <div className="rounded-xl border border-black/[0.08] px-3 py-2"><p className="text-[10px] uppercase font-black tracking-[0.14em] text-[#49454F]">TDEE</p><p className="text-sm font-black text-[#1D1B20] mt-0.5">{tdee != null ? Math.round(tdee) : '—'} kcal</p></div>
                        </div>
                    </div>
                )}

                <div className="mt-4 rounded-2xl border border-black/[0.08] bg-white p-4">
                    <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Proyección de meta</p>
                    <p className="text-sm text-[#1D1B20] mt-1">ETA estimada: <span className="font-black">{estimatedEndDate ? new Date(`${estimatedEndDate}T00:00:00`).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' }) : 'Sin ETA'}</span></p>
                </div>

                {riskFlags.length > 0 && (
                    <div className="mt-4 space-y-2">
                        <p className="text-[10px] font-black uppercase tracking-[0.16em] text-[#49454F]">Alertas de seguridad</p>
                        {riskFlags.map((flag) => (
                            <div key={flag.id} className={`rounded-xl border px-3 py-2 text-sm font-semibold ${getRiskClasses(flag.severity)}`}>
                                <div className="flex items-center gap-2"><AlertTriangleIcon size={14} />{flag.message}</div>
                            </div>
                        ))}
                    </div>
                )}

                {needsExplicitOverride && !hasHardStop && (
                    <label className="mt-4 flex items-start gap-2 text-sm text-[#1D1B20]"><input type="checkbox" checked={overrideAcknowledged} onChange={(event) => setOverrideAcknowledged(event.target.checked)} />Entiendo las alertas y quiero guardar este plan igualmente.</label>
                )}

                {hasHardStop && (
                    <div className="mt-4 rounded-2xl border border-[#B3261E]/35 bg-[#B3261E]/12 px-4 py-3 text-sm font-bold text-[#8C1D18]">Hay un riesgo alto. Ajusta objetivo, ritmo o calorías para continuar.</div>
                )}
            </section>
        </div>
    );

    const renderContent = () => {
        if (step === 0) return renderStep0();
        if (step === 1) return renderStep1();
        if (step === 2) return renderStep2();
        if (step === 3) return renderStep3();
        return renderStep4();
    };

    return (
        <div className="min-h-full flex flex-col bg-gradient-to-b from-[var(--md-sys-color-surface)] via-white to-white relative overflow-hidden">
            {renderHeader()}

            <div ref={scrollRef} className="flex-1 overflow-y-auto">
                <div style={{ paddingLeft: layout.pagePadX, paddingRight: layout.pagePadX, paddingTop: 16, paddingBottom: layout.contentBottom }}>{renderContent()}</div>
            </div>

            <div className="sticky bottom-0 z-20 bg-[var(--md-sys-color-surface)]/90 backdrop-blur-xl border-t border-black/[0.05]">
                <div className="w-full flex items-center justify-between gap-3" style={{ paddingLeft: layout.pagePadX, paddingRight: layout.pagePadX, paddingTop: layout.footerPadY, paddingBottom: layout.footerPadY }}>
                    <button onClick={() => setStep((prev) => Math.max(0, prev - 1))} disabled={step === 0} className="rounded-xl px-4 py-2.5 text-xs font-black uppercase tracking-[0.14em] border border-black/[0.15] text-[#49454F] disabled:opacity-50">Atrás</button>
                    <button onClick={handleNext} className="rounded-xl px-5 py-2.5 text-xs font-black uppercase tracking-[0.14em] bg-[var(--md-sys-color-primary)] text-white">{step === TOTAL_STEPS - 1 ? 'Confirmar plan' : 'Continuar'}</button>
                </div>
            </div>
        </div>
    );
};


