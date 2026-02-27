// components/nutrition/NutritionWizard.tsx
// Wizard de objetivos nutricionales (4 pasos). Estética NERD con fondo animado.

import React, { useState, useMemo, useEffect } from 'react';
import { ChevronDownIcon, ChevronUpIcon } from '../icons';
import type { Settings, CalorieGoalConfig, NutritionPlan } from '../../types';
import { mifflinStJeor, katchMcArdle, calculateCaloriesForBodyFatTrend } from '../../utils/calorieFormulas';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { ChevronRightIcon, ChevronLeftIcon, CheckIcon } from '../icons';
import { NutritionTooltip } from './NutritionTooltip';
import { AnimatedSvgBackground } from '../onboarding/AnimatedSvgBackground';

const ACTIVITY_FACTORS: Record<number, number> = {
    1: 1.2, 2: 1.375, 3: 1.55, 4: 1.725, 5: 1.9,
};

const GOAL_OPTIONS: { id: CalorieGoalConfig['goal']; label: string; why: string }[] = [
    { id: 'lose', label: 'Definición', why: 'Déficit calórico para reducir grasa manteniendo masa muscular.' },
    { id: 'maintain', label: 'Mantención', why: 'Calorías de equilibrio para mantener peso y composición.' },
    { id: 'gain', label: 'Superávit', why: 'Excedente controlado para ganar masa muscular.' },
];

const GENDER_OPTIONS: { id: string; label: string }[] = [
    { id: 'male', label: 'Hombre' },
    { id: 'female', label: 'Mujer' },
    { id: 'transmale', label: 'Trans masculino' },
    { id: 'transfemale', label: 'Trans femenino' },
    { id: 'other', label: 'No binario' },
];

const ACTIVITY_OPTIONS: { id: number; label: string; desc: string }[] = [
    { id: 1, label: 'Sedentario', desc: 'Poco o ningún ejercicio' },
    { id: 2, label: 'Ligero', desc: 'Ejercicio 1-3 días/semana' },
    { id: 3, label: 'Moderado', desc: 'Ejercicio 3-5 días/semana' },
    { id: 4, label: 'Activo', desc: 'Ejercicio 6-7 días/semana' },
    { id: 5, label: 'Muy activo', desc: 'Ejercicio intenso diario' },
];

const METABOLIC_CONDITIONS = [
    { id: 'diabetes', label: 'Diabetes tipo 1/2' },
    { id: 'hypothyroid', label: 'Hipotiroidismo' },
    { id: 'hyperthyroid', label: 'Hipertiroidismo' },
    { id: 'metabolic_syndrome', label: 'Síndrome metabólico' },
    { id: 'none', label: 'Ninguna' },
];

const DIET_OPTIONS: { id: string; label: string }[] = [
    { id: 'omnivore', label: 'Omnívoro' },
    { id: 'vegetarian', label: 'Vegetariano' },
    { id: 'vegan', label: 'Vegano' },
];

const KCAL_PER_GRAM = { protein: 4, carbs: 4, fat: 9 };

interface NutritionWizardProps {
    onComplete: () => void;
}

const OBJETIVO_OPTIONS: { id: NutritionPlan['goalType']; label: string; unit: string; placeholder: string }[] = [
    { id: 'weight', label: 'Peso objetivo', unit: 'kg', placeholder: 'Ej: 70' },
    { id: 'bodyFat', label: '% Grasa objetivo', unit: '%', placeholder: 'Ej: 15' },
    { id: 'muscleMass', label: '% Músculo objetivo', unit: '%', placeholder: 'Ej: 42' },
];

export const NutritionWizard: React.FC<NutritionWizardProps> = ({ onComplete }) => {
    const { settings } = useAppState();
    const { setSettings, addToast, setNutritionPlans, setActiveNutritionPlanId } = useAppDispatch();
    const [step, setStep] = useState(0);
    const [goalType, setGoalType] = useState<NutritionPlan['goalType']>('weight');
    const [goalValue, setGoalValue] = useState<number | ''>(() => {
        const w = settings.userVitals?.weight;
        return typeof w === 'number' ? w : 70;
    });
    const [trendMode, setTrendMode] = useState<NutritionPlan['trendMode']>('kg_per_week');
    const [trendValue, setTrendValue] = useState(0.3);
    const [goal, setGoal] = useState<CalorieGoalConfig['goal']>(settings.calorieGoalObjective === 'deficit' ? 'lose' : settings.calorieGoalObjective === 'surplus' ? 'gain' : 'maintain');
    useEffect(() => {
        if (trendMode === 'pct_fat_per_week') {
            setTrendValue((v) => (goal === 'lose' && v > 0 ? -0.3 : goal === 'gain' && v < 0 ? 0.3 : v));
        }
    }, [trendMode, goal]);
    const [connectAuge, setConnectAuge] = useState(settings.algorithmSettings?.augeEnableNutritionTracking ?? true);
    const [height, setHeight] = useState<number | ''>(settings.userVitals?.height ?? 170);
    const [weight, setWeight] = useState<number | ''>(settings.userVitals?.weight ?? 70);
    const [age, setAge] = useState<number | ''>(settings.userVitals?.age ?? 30);
    const [gender, setGender] = useState(settings.userVitals?.gender ?? 'male');
    const [bodyFat, setBodyFat] = useState<number | ''>(settings.userVitals?.bodyFatPercentage ?? '');
    const [muscleMass, setMuscleMass] = useState<number | ''>(settings.userVitals?.muscleMassPercentage ?? '');
    const activityMap: Record<string, number> = { sedentary: 1, light: 2, moderate: 3, active: 4, very_active: 5 };
    const [activityLevel, setActivityLevel] = useState(settings.calorieGoalConfig?.activityLevel ?? (activityMap[settings.userVitals?.activityLevel || ''] ?? 3));
    const [metabolicConditions, setMetabolicConditions] = useState<string[]>(settings.metabolicConditions ?? []);
    const [dietPreference, setDietPreference] = useState(settings.dietaryPreference ?? 'omnivore');
    const [weeklyChangeKg, setWeeklyChangeKg] = useState(settings.calorieGoalConfig?.weeklyChangeKg ?? 0.5);
    const [healthMultiplier, setHealthMultiplier] = useState(settings.calorieGoalConfig?.healthMultiplier ?? 1);
    const [proteinG, setProteinG] = useState(settings.dailyProteinGoal ?? 150);
    const [carbsG, setCarbsG] = useState(settings.dailyCarbGoal ?? 250);
    const [fatsG, setFatsG] = useState<number | ''>(settings.dailyFatGoal ?? 70);
    const [otherCondition, setOtherCondition] = useState('');
    const [formulaExpanded, setFormulaExpanded] = useState(false);
    const [advancedActivityExpanded, setAdvancedActivityExpanded] = useState(false);
    const [customActivityFactor, setCustomActivityFactor] = useState<number | ''>(() => {
        const custom = settings.calorieGoalConfig?.customActivityFactor;
        return typeof custom === 'number' ? custom : '';
    });
    const [activityDaysPerWeek, setActivityDaysPerWeek] = useState(settings.calorieGoalConfig?.activityDaysPerWeek ?? 3);
    const [activityHoursPerDay, setActivityHoursPerDay] = useState(settings.calorieGoalConfig?.activityHoursPerDay ?? 1);

    const activityIdx = activityLevel;
    const derivedActivityFactor = useMemo(() => {
        if (customActivityFactor !== '' && typeof customActivityFactor === 'number') return customActivityFactor;
        if (!advancedActivityExpanded) return ACTIVITY_FACTORS[activityIdx] ?? 1.55;
        const days = Math.min(7, Math.max(0, activityDaysPerWeek));
        const hours = Math.min(24, Math.max(0, activityHoursPerDay));
        return 1.2 + (days / 7) * 0.4 + (hours / 12) * 0.3;
    }, [activityIdx, advancedActivityExpanded, customActivityFactor, activityDaysPerWeek, activityHoursPerDay]);

    const bmr = useMemo(() => {
        const w = weight === '' ? NaN : Number(weight);
        const h = height === '' ? NaN : Number(height);
        const a = age === '' ? NaN : Number(age);
        if (Number.isNaN(w) || Number.isNaN(h) || Number.isNaN(a) || !w || !h || !a) return null;
        const bf = bodyFat !== '' ? Number(bodyFat) : 15;
        const useKatch = bodyFat !== '' && bf > 0;
        if (useKatch) return katchMcArdle(w, bf);
        const g = gender === 'female' || gender === 'transfemale' ? 'female' : 'male';
        return mifflinStJeor(w, h, a, g);
    }, [weight, height, age, gender, bodyFat]);

    const tdeeBase = useMemo(() => {
        if (bmr == null) return null;
        return Math.round(bmr * derivedActivityFactor * healthMultiplier);
    }, [bmr, derivedActivityFactor, healthMultiplier]);

    const tdee = useMemo(() => {
        if (tdeeBase == null) return null;
        if (goal === 'maintain') return tdeeBase;
        if (trendMode === 'kg_per_week') {
            if (goal === 'lose') return tdeeBase - (weeklyChangeKg * 7700) / 7;
            if (goal === 'gain') return tdeeBase + (weeklyChangeKg * 7700) / 7;
            return tdeeBase;
        }
        const weightNum = weight === '' ? (settings.userVitals?.weight ?? 70) : Number(weight) || 70;
        return calculateCaloriesForBodyFatTrend(tdeeBase, weightNum, trendValue);
    }, [tdeeBase, goal, weeklyChangeKg, trendMode, trendValue, weight, settings.userVitals?.weight]);

    const proteinMultiplier = dietPreference === 'vegan' ? 1.15 : dietPreference === 'vegetarian' ? 1.08 : 1;

    const caloriesFromMacros = useMemo(() => {
        const p = proteinG * KCAL_PER_GRAM.protein * proteinMultiplier;
        const c = carbsG * KCAL_PER_GRAM.carbs;
        const f = (fatsG === '' ? 0 : Number(fatsG)) * KCAL_PER_GRAM.fat;
        return Math.round(p + c + f);
    }, [proteinG, carbsG, fatsG, proteinMultiplier]);

    const weeklyTrendKg = useMemo(() => {
        if (!tdee || weight === '') return null;
        const targetCals = caloriesFromMacros;
        const diff = targetCals - tdee;
        return (diff * 7) / 7700;
    }, [tdee, weight, caloriesFromMacros]);

    const ffmi = useMemo(() => {
        if (!weight || !height || bodyFat === '') return null;
        const w = Number(weight);
        const h = Number(height) / 100;
        const bf = Number(bodyFat);
        const lbm = w * (1 - bf / 100);
        return (lbm / (h * h)).toFixed(1);
    }, [weight, height, bodyFat]);

    const goalAlert = useMemo(() => {
        const gv = goalValue === '' ? null : Number(goalValue);
        if (gv == null) return null;
        if (goalType === 'weight') {
            const h = height === '' ? 170 : Number(height) || 170;
            const imc = gv / ((h / 100) ** 2);
            if (imc < 18.5 || imc > 24.9) return `IMC objetivo ${imc.toFixed(1)} fuera del rango saludable OMS (18.5–24.9).`;
        }
        if (goalType === 'bodyFat') {
            if (gv < 5 || gv > 50) return '% grasa fuera de rango razonable (5–50%).';
        }
        if (goalType === 'muscleMass') {
            const isFemale = gender === 'female' || gender === 'transfemale';
            if (isFemale && (gv < 25 || gv > 45)) return 'Rango típico mujer: 25–45%.';
            if (!isFemale && (gv < 30 || gv > 55)) return 'Rango típico hombre: 30–55%.';
        }
        return null;
    }, [goalType, goalValue, height, gender]);

    const estimatedEndDate = useMemo(() => {
        const now = new Date();
        const currentWeight = weight === '' ? (settings.userVitals?.weight ?? 70) : Number(weight) || 70;
        const currentBodyFat = bodyFat !== '' ? Number(bodyFat) : 20;
        const currentMuscle = muscleMass !== '' ? Number(muscleMass) : 38;
        const gv = goalValue === '' ? null : Number(goalValue);
        if (gv == null) return null;
        let weeks = 0;
        if (goalType === 'weight') {
            const trend = trendMode === 'kg_per_week' ? (goal === 'lose' ? -weeklyChangeKg : goal === 'gain' ? weeklyChangeKg : 0) : 0;
            if (trend === 0) return null;
            weeks = Math.abs((gv - currentWeight) / trend);
        } else if (goalType === 'bodyFat') {
            const trend = trendMode === 'pct_fat_per_week' ? trendValue : 0;
            if (trend === 0) return null;
            weeks = Math.abs((gv - currentBodyFat) / trend);
        } else {
            const trendPct = trendMode === 'pct_fat_per_week' ? trendValue : 0.15;
            weeks = Math.abs((gv - currentMuscle) / trendPct);
        }
        const end = new Date(now);
        end.setDate(end.getDate() + Math.ceil(weeks * 7));
        return end.toISOString().slice(0, 10);
    }, [goalType, goalValue, trendMode, trendValue, weeklyChangeKg, goal, weight, bodyFat, muscleMass, settings.userVitals?.weight]);

    const caloriesAlert = useMemo(() => {
        if (!tdee || tdee >= 1200) return null;
        const isFemale = gender === 'female' || gender === 'transfemale';
        const min = isFemale ? 1200 : 1500;
        if (tdee < min) return `Calorías por debajo de ${min} kcal/día pueden afectar la salud.`;
        return null;
    }, [tdee, gender]);

    const handleFinish = () => {
        const config: CalorieGoalConfig = {
            formula: bodyFat !== '' ? 'katch' : 'mifflin',
            activityLevel: activityIdx,
            goal,
            weeklyChangeKg,
            healthMultiplier,
            ...(customActivityFactor !== '' && { customActivityFactor: Number(customActivityFactor) }),
            ...(advancedActivityExpanded && { activityDaysPerWeek, activityHoursPerDay }),
        };
        const plan: NutritionPlan = {
            id: `np-${Date.now()}`,
            name: goalType === 'weight' ? `Peso ${goalValue} kg` : goalType === 'bodyFat' ? `${goalValue}% grasa` : `${goalValue}% músculo`,
            goalType,
            goalValue: goalValue === '' ? (goalType === 'weight' ? 70 : goalType === 'bodyFat' ? 15 : 42) : Number(goalValue),
            trendMode,
            trendValue,
            startDate: new Date().toISOString().slice(0, 10),
            estimatedEndDate: estimatedEndDate ?? undefined,
            calorieGoalConfig: config,
            isActive: true,
            createdAt: new Date().toISOString(),
        };
        setNutritionPlans((prev) => {
            const rest = prev.map((p) => ({ ...p, isActive: false }));
            return [...rest, plan];
        });
        setActiveNutritionPlanId(plan.id);
        setSettings({
            hasSeenNutritionWizard: true,
            calorieGoalConfig: config,
            calorieGoalObjective: goal === 'lose' ? 'deficit' : goal === 'gain' ? 'surplus' : 'maintenance',
            dailyCalorieGoal: caloriesFromMacros,
            dailyProteinGoal: Math.round(proteinG * proteinMultiplier),
            dailyCarbGoal: carbsG,
            dailyFatGoal: fatsG === '' ? (settings.dailyFatGoal ?? 70) : Number(fatsG),
            userVitals: {
                ...settings.userVitals,
                height: height === '' ? (settings.userVitals?.height ?? 170) : Number(height),
                weight: weight === '' ? (settings.userVitals?.weight ?? 70) : Number(weight),
                age: age === '' ? (settings.userVitals?.age ?? 30) : Number(age),
                gender: gender as any,
                bodyFatPercentage: bodyFat !== '' ? Number(bodyFat) : undefined,
                muscleMassPercentage: muscleMass !== '' ? Number(muscleMass) : undefined,
                activityLevel: ['sedentary', 'light', 'moderate', 'active', 'very_active'][activityIdx - 1] as any,
            },
            algorithmSettings: {
                ...settings.algorithmSettings,
                augeEnableNutritionTracking: connectAuge,
            },
            dietaryPreference: dietPreference as any,
            metabolicConditions: metabolicConditions.filter(c => c !== 'none').concat(otherCondition ? [otherCondition] : []),
        });
        addToast('Objetivos nutricionales configurados.', 'success');
        onComplete();
    };

    const renderStep = () => {
        switch (step) {
            case 0:
                return (
                    <div className="space-y-8">
                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono flex items-center gap-1">
                                ¿Cuál es tu objetivo?
                                <NutritionTooltip content="Define una meta concreta: peso objetivo, %grasa o %músculo. Solo uno a la vez." title="Objetivo" />
                            </h2>
                            <p className="text-slate-400 text-sm mt-1">Elige qué quieres lograr y el valor objetivo.</p>
                            <div className="space-y-4 mt-4">
                                <div className="flex flex-wrap gap-2">
                                    {OBJETIVO_OPTIONS.map((opt) => (
                                        <button
                                            key={opt.id}
                                            onClick={() => setGoalType(opt.id)}
                                            className={`px-4 py-2 rounded-xl font-bold transition-all ${
                                                goalType === opt.id ? 'bg-[#FF7B00] text-white border border-cyber-copper' : 'bg-white/5 text-slate-400 border border-white/10 hover:border-cyber-copper/30'
                                            }`}
                                        >
                                            {opt.label}
                                        </button>
                                    ))}
                                </div>
                                <div>
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">
                                        {OBJETIVO_OPTIONS.find((o) => o.id === goalType)?.label} ({OBJETIVO_OPTIONS.find((o) => o.id === goalType)?.unit})
                                    </label>
                                    <input
                                        type="number"
                                        value={goalValue === '' ? '' : goalValue}
                                        onChange={(e) => {
                                            const v = e.target.value;
                                            setGoalValue(v === '' ? '' : Number(v) || 0);
                                        }}
                                        placeholder={OBJETIVO_OPTIONS.find((o) => o.id === goalType)?.placeholder}
                                        min={goalType === 'weight' ? 30 : goalType === 'bodyFat' ? 5 : 20}
                                        max={goalType === 'weight' ? 300 : goalType === 'bodyFat' ? 60 : 60}
                                        step={goalType === 'weight' ? 1 : 0.5}
                                        className="w-full bg-white/5 border border-cyber-copper/20 rounded-xl px-4 py-3 text-white font-mono focus:border-cyber-copper/50 outline-none placeholder-slate-600"
                                    />
                                </div>
                                {goalAlert && (
                                    <div className="p-4 rounded-xl bg-amber-950/30 border border-amber-500/50">
                                        <p className="text-sm font-bold text-amber-200">{goalAlert}</p>
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                );
            case 1:
                return (
                    <div className="space-y-8">
                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono flex items-center gap-1">
                                Objetivo
                                <NutritionTooltip content="Define si buscas perder grasa (déficit), mantener peso o ganar masa (superávit). Cada objetivo ajusta las calorías recomendadas." title="Objetivo" />
                            </h2>
                            <p className="text-slate-400 text-sm mt-1">¿Qué buscas con tu alimentación?</p>
                            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-4">
                                {GOAL_OPTIONS.map(opt => (
                                    <button
                                        key={opt.id}
                                        onClick={() => setGoal(opt.id)}
                                        className={`p-4 rounded-xl border text-left sm:text-center transition-all min-w-0 ${
                                            goal === opt.id ? 'bg-[#FF7B00]/20 border-cyber-copper text-white' : 'bg-white/5 border-white/10 text-slate-300 hover:border-cyber-copper/30'
                                        }`}
                                    >
                                        <span className="font-bold block">{opt.label}</span>
                                        <span className="text-[10px] text-slate-500 mt-1 block">{opt.why}</span>
                                    </button>
                                ))}
                            </div>
                        </div>

                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono">Conexión AUGE</h2>
                            <p className="text-slate-400 text-sm mt-1">Conecta calorías y macros con la batería muscular para el cálculo de recuperación.</p>
                            <div className="flex gap-4 mt-4">
                                <button
                                    onClick={() => setConnectAuge(true)}
                                    className={`flex-1 p-4 rounded-xl border font-bold transition-all ${
                                        connectAuge ? 'bg-cyan-500/20 border-cyan-500 text-cyan-300' : 'bg-white/5 border-white/10 text-slate-400'
                                    }`}
                                >
                                    Sí
                                </button>
                                <button
                                    onClick={() => setConnectAuge(false)}
                                    className={`flex-1 p-4 rounded-xl border font-bold transition-all ${
                                        !connectAuge ? 'bg-cyan-500/20 border-cyan-500 text-cyan-300' : 'bg-white/5 border-white/10 text-slate-400'
                                    }`}
                                >
                                    No
                                </button>
                            </div>
                            <p className="text-xs text-slate-500 mt-2">Si no trackeas siempre, desconectar evita deformar el cálculo.</p>
                        </div>

                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono flex items-center gap-1">
                                Datos corporales
                                <NutritionTooltip content="Peso, estatura y edad son la base para calcular tu TMB (tasa metabólica basal). Con %grasa usamos Katch-McArdle, más preciso para atletas." title="Datos corporales" />
                            </h2>
                            <p className="text-slate-400 text-sm mt-1">Necesarios para calcular tu TMB y calorías diarias.</p>
                            <div className="space-y-4 mt-4">
                                <div>
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Estatura (cm)</label>
                                    <input type="number" value={height === '' ? '' : height} onChange={e => { const v = e.target.value; setHeight(v === '' ? '' : (Number(v) || 0)); }} min={100} max={250}
                                        className="w-full bg-white/5 border border-cyber-copper/20 rounded-xl px-4 py-3 text-white font-mono focus:border-cyber-copper/50 outline-none" />
                                </div>
                                <div>
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Peso (kg)</label>
                                    <input type="number" value={weight === '' ? '' : weight} onChange={e => { const v = e.target.value; setWeight(v === '' ? '' : (Number(v) || 0)); }} min={30} max={300}
                                        className="w-full bg-white/5 border border-cyber-copper/20 rounded-xl px-4 py-3 text-white font-mono focus:border-cyber-copper/50 outline-none" />
                                </div>
                                <div>
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Edad</label>
                                    <input type="number" value={age === '' ? '' : age} onChange={e => { const v = e.target.value; setAge(v === '' ? '' : (Number(v) || 0)); }} min={10} max={120}
                                        className="w-full bg-white/5 border border-cyber-copper/20 rounded-xl px-4 py-3 text-white font-mono focus:border-cyber-copper/50 outline-none" />
                                </div>
                                <div>
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-2">Género</label>
                                    <div className="flex flex-wrap gap-2">
                                        {GENDER_OPTIONS.map(opt => (
                                            <button key={opt.id} onClick={() => setGender(opt.id)}
                                                className={`px-3 py-2 rounded-lg text-sm font-bold transition-all ${
                                                    gender === opt.id ? 'bg-[#FF7B00] text-white border border-cyber-copper' : 'bg-white/5 text-slate-400 border border-white/10 hover:border-cyber-copper/30'
                                                }`}>
                                                {opt.label}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                                <div className="pt-4 border-t border-white/10">
                                    <p className="text-[10px] font-bold text-cyber-copper uppercase mb-2 flex items-center gap-1">
                                        Bioimpedancia (altamente recomendado)
                                        <NutritionTooltip content="Si tienes %grasa (báscula, plicómetro, DEXA), usamos Katch-McArdle: más preciso que Mifflin para atletas. LBM = peso × (1 - %grasa/100)." title="Katch-McArdle" />
                                    </p>
                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">% Grasa corporal</label>
                                            <input type="number" value={bodyFat} onChange={e => setBodyFat(e.target.value === '' ? '' : Number(e.target.value))} min={5} max={60} step={0.5} placeholder="Ej: 18"
                                                className="w-full bg-white/5 border border-cyber-copper/20 rounded-xl px-4 py-3 text-white font-mono placeholder-slate-600 focus:border-cyber-copper/50 outline-none" />
                                        </div>
                                        <div>
                                            <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">% Masa muscular</label>
                                            <input type="number" value={muscleMass} onChange={e => setMuscleMass(e.target.value === '' ? '' : Number(e.target.value))} min={20} max={50} step={0.5} placeholder="Ej: 42"
                                                className="w-full bg-white/5 border border-cyber-copper/20 rounded-xl px-4 py-3 text-white font-mono placeholder-slate-600 focus:border-cyber-copper/50 outline-none" />
                                        </div>
                                    </div>
                                    {ffmi != null && <p className="text-xs text-cyber-copper font-mono mt-2">FFMI estimado: {ffmi}</p>}
                                </div>
                            </div>
                        </div>
                    </div>
                );
            case 2:
                return (
                    <div className="space-y-8">
                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono flex items-center gap-1">
                                Nivel de actividad
                                <NutritionTooltip content="El TDEE se calcula como TMB × factor de actividad. Sedentario 1.2, muy activo 1.9. En Avanzado puedes afinar con días/horas o factor personalizado." title="Actividad" />
                            </h2>
                            <p className="text-slate-400 text-sm mt-1">¿Qué tan activo eres fuera del gimnasio?</p>
                            <div className="space-y-2 mt-4">
                                {ACTIVITY_OPTIONS.map(opt => (
                                    <button key={opt.id} onClick={() => setActivityLevel(opt.id)}
                                        className={`w-full p-4 rounded-xl border text-left transition-all ${
                                            activityIdx === opt.id ? 'bg-[#FF7B00]/20 border-cyber-copper text-white' : 'bg-white/5 border-white/10 text-slate-300 hover:border-cyber-copper/30'
                                        }`}>
                                        <span className="font-bold block">{opt.label}</span>
                                        <span className="text-xs text-slate-500">{opt.desc}</span>
                                    </button>
                                ))}
                            </div>
                            <button
                                onClick={() => setAdvancedActivityExpanded(!advancedActivityExpanded)}
                                className="flex items-center gap-2 mt-4 text-cyber-copper/90 text-xs font-bold hover:text-cyber-copper/80"
                            >
                                {advancedActivityExpanded ? <ChevronUpIcon size={14} /> : <ChevronDownIcon size={14} />}
                                Avanzado
                            </button>
                            {advancedActivityExpanded && (
                                <div className="mt-3 p-4 rounded-xl bg-white/5 border border-cyber-copper/20 space-y-4">
                                    <div>
                                        <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Días de ejercicio/semana</label>
                                        <input type="number" value={activityDaysPerWeek} onChange={e => setActivityDaysPerWeek(Number(e.target.value) || 0)} min={0} max={7}
                                            className="w-full bg-white/5 border border-cyber-copper/20 rounded-xl px-4 py-3 text-white font-mono focus:border-cyber-copper/50 outline-none" />
                                    </div>
                                    <div>
                                        <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Horas activas/día (promedio)</label>
                                        <input type="number" value={activityHoursPerDay} onChange={e => setActivityHoursPerDay(Number(e.target.value) || 0)} min={0} max={24} step={0.5}
                                            className="w-full bg-white/5 border border-cyber-copper/20 rounded-xl px-4 py-3 text-white font-mono focus:border-cyber-copper/50 outline-none" />
                                    </div>
                                    <div>
                                        <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Factor personalizado (opcional, 1.0–2.0)</label>
                                        <input type="number" value={customActivityFactor} onChange={e => setCustomActivityFactor(e.target.value === '' ? '' : Number(e.target.value))} min={1} max={2} step={0.05} placeholder="Ej: 1.55"
                                            className="w-full bg-white/5 border border-cyber-copper/20 rounded-xl px-4 py-3 text-white font-mono placeholder-slate-600 focus:border-cyber-copper/50 outline-none" />
                                    </div>
                                    <p className="text-xs text-cyber-copper font-mono">Factor efectivo: {derivedActivityFactor.toFixed(2)}</p>
                                </div>
                            )}
                        </div>

                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono">Enfermedades metabólicas</h2>
                            <p className="text-slate-400 text-sm mt-1">Para ajuste de calorías si aplica.</p>
                            <div className="space-y-2 mt-4">
                                {METABOLIC_CONDITIONS.map(opt => (
                                    <label key={opt.id} className="flex items-center gap-3 p-3 rounded-xl bg-white/5 border border-white/10 cursor-pointer">
                                        <input type="checkbox" checked={metabolicConditions.includes(opt.id)}
                                            onChange={() => {
                                                if (opt.id === 'none') setMetabolicConditions(['none']);
                                                else setMetabolicConditions(prev => prev.includes(opt.id) ? prev.filter(x => x !== opt.id) : [...prev.filter(x => x !== 'none'), opt.id]);
                                            }} className="rounded" />
                                        <span className="text-sm font-medium text-white">{opt.label}</span>
                                    </label>
                                ))}
                                <div className="mt-2">
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Otra (opcional)</label>
                                    <input type="text" value={otherCondition} onChange={e => setOtherCondition(e.target.value)} placeholder="Ej: SOP"
                                        className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white placeholder-slate-600" />
                                </div>
                            </div>
                        </div>

                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono">Preferencia dietética</h2>
                            <p className="text-slate-400 text-sm mt-1">Vegano/vegetariano: ajustamos proteínas por biodisponibilidad.</p>
                            <div className="flex gap-3 mt-4">
                                {DIET_OPTIONS.map(opt => (
                                    <button key={opt.id} onClick={() => setDietPreference(opt.id)}
                                        className={`flex-1 p-4 rounded-xl border font-bold transition-all ${
                                            dietPreference === opt.id ? 'bg-[#FF7B00]/20 border-cyber-copper text-white' : 'bg-white/5 border-white/10 text-slate-400 hover:border-cyber-copper/30'
                                        }`}>
                                        {opt.label}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>
                );
            case 3:
                const trend = weeklyTrendKg ?? 0;
                const weightNum = weight === '' ? (settings.userVitals?.weight ?? 70) : Number(weight) || 70;
                const pctWeekly = weightNum > 0 ? (trend / weightNum) * 100 : 0;
                const trendKgAbs = Math.abs(trend);
                const isDangerLow = goal === 'lose' && (pctWeekly < -1.2 || trendKgAbs > 1.2);
                const isDangerHigh = goal === 'gain' && (pctWeekly > 1.5 || trendKgAbs > 1.2);
                return (
                    <div className="space-y-8">
                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono flex items-center gap-1">
                                Desglose
                                <NutritionTooltip content="TMB: calorías en reposo. TDEE: TMB × factor actividad ± ajuste objetivo. Las fórmulas Mifflin y Katch calculan el TMB." title="TMB y TDEE" />
                            </h2>
                            <div className="bg-slate-900/50 rounded-xl p-4 border border-cyber-copper/20 space-y-2 font-mono text-sm mt-4">
                                <p className="text-cyber-copper flex items-center gap-1">
                                    TMB: {bmr != null ? Math.round(bmr) : '—'} kcal
                                    <NutritionTooltip content="Tasa Metabólica Basal: calorías que quemas en reposo. Base para calcular tu gasto diario." title="TMB" />
                                </p>
                                <p className="text-cyber-copper flex items-center gap-1">
                                    TDEE: {tdee != null ? tdee : '—'} kcal
                                    <NutritionTooltip content="Gasto energético diario total: TMB × factor de actividad. Incluye ejercicio y NEAT." title="TDEE" />
                                </p>
                                <p className="text-slate-400 text-xs">Factores Atwater: P=4, C=4, G=9 kcal/g</p>
                                <button
                                    onClick={() => setFormulaExpanded(!formulaExpanded)}
                                    className="flex items-center gap-2 mt-2 text-cyber-copper/90 text-xs font-bold hover:text-cyber-copper/80"
                                >
                                    {formulaExpanded ? <ChevronUpIcon size={14} /> : <ChevronDownIcon size={14} />}
                                    {formulaExpanded ? 'Ocultar fórmulas' : 'Ver fórmulas'}
                                </button>
                                {formulaExpanded && (
                                    <div className="mt-3 pt-3 border-t border-white/10 space-y-2 text-[11px] text-cyber-copper/80 font-mono">
                                        <p>Mifflin-St Jeor: TMB = 10×peso + 6.25×altura − 5×edad + s</p>
                                        <p>Katch-McArdle: TMB = 370 + (21.6 × LBM)</p>
                                        <p>Calorías = P×4 + C×4 + G×9</p>
                                    </div>
                                )}
                            </div>
                        </div>

                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono flex items-center gap-1">
                                Edición de macros
                                <NutritionTooltip content="Proteínas: 4 kcal/g, esenciales para músculo. Carbohidratos: 4 kcal/g, energía. Grasas: 9 kcal/g, hormonas y saciedad." title="Macros" />
                            </h2>
                            {(goal === 'lose' || goal === 'gain') && (
                                <div className="mt-4 space-y-4">
                                    <div>
                                        <label className="block text-[10px] font-bold text-slate-500 uppercase mb-2">Modo de tendencia</label>
                                        <div className="flex gap-2">
                                            <button
                                                onClick={() => setTrendMode('kg_per_week')}
                                                className={`flex-1 p-3 rounded-xl border font-bold text-sm transition-all ${
                                                    trendMode === 'kg_per_week' ? 'bg-[#FF7B00]/20 border-cyber-copper text-white' : 'bg-white/5 border-white/10 text-slate-400 hover:border-cyber-copper/30'
                                                }`}
                                            >
                                                kg/semana
                                            </button>
                                            <button
                                                onClick={() => setTrendMode('pct_fat_per_week')}
                                                className={`flex-1 p-3 rounded-xl border font-bold text-sm transition-all ${
                                                    trendMode === 'pct_fat_per_week' ? 'bg-[#FF7B00]/20 border-cyber-copper text-white' : 'bg-white/5 border-white/10 text-slate-400 hover:border-cyber-copper/30'
                                                }`}
                                            >
                                                % grasa/sem
                                            </button>
                                        </div>
                                    </div>
                                    {trendMode === 'kg_per_week' ? (
                                        <div>
                                            <label className="block text-[10px] font-bold text-slate-500 uppercase mb-2">Cambio semanal (kg)</label>
                                            <select value={weeklyChangeKg} onChange={e => setWeeklyChangeKg(Number(e.target.value))}
                                                className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white">
                                                {[0.25, 0.5, 0.75, 1, 1.5, 2].map(v => <option key={v} value={v}>{goal === 'lose' ? `-${v}` : `+${v}`} kg/sem</option>)}
                                            </select>
                                        </div>
                                    ) : (
                                        <div>
                                            <label className="block text-[10px] font-bold text-slate-500 uppercase mb-2">Cambio % grasa/semana</label>
                                            <input
                                                type="range"
                                                min={goal === 'lose' ? -0.5 : 0}
                                                max={goal === 'lose' ? 0 : 0.5}
                                                step={0.05}
                                                value={trendValue}
                                                onChange={e => setTrendValue(Number(e.target.value))}
                                                className="w-full accent-cyber-copper"
                                            />
                                            <p className="text-xs text-slate-400 mt-1 font-mono">{trendValue >= 0 ? '+' : ''}{trendValue} %/sem</p>
                                        </div>
                                    )}
                                </div>
                            )}
                            <div className="mt-4">
                                <label className="block text-[10px] font-bold text-slate-500 uppercase mb-2">Multiplicador salud (0.8–1.2)</label>
                                <input type="number" value={healthMultiplier} onChange={e => setHealthMultiplier(Number(e.target.value) || 1)} min={0.8} max={1.2} step={0.05}
                                    className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono" />
                            </div>
                            <div className="mt-6 space-y-5">
                                <div className="flex items-center justify-between">
                                    <span className="text-[10px] font-bold text-slate-500 uppercase">Total diario</span>
                                    <span className="text-2xl font-black text-white font-mono">{caloriesFromMacros} kcal</span>
                                </div>
                                {(() => {
                                    const pKcal = proteinG * 4;
                                    const cKcal = carbsG * 4;
                                    const fKcal = (fatsG === '' ? 0 : Number(fatsG)) * 9;
                                    const totalKcal = pKcal + cKcal + fKcal || 1;
                                    const pPct = (pKcal / totalKcal) * 100;
                                    const cPct = (cKcal / totalKcal) * 100;
                                    const fPct = (fKcal / totalKcal) * 100;
                                    return (
                                        <>
                                                <div className="h-4 rounded-full overflow-hidden flex bg-white/10">
                                                <div className="h-full bg-blue-500/80 transition-all" style={{ width: `${pPct}%` }} title={`Proteína ${pPct.toFixed(0)}%`} />
                                                <div className="h-full bg-green-500/80 transition-all" style={{ width: `${cPct}%` }} title={`Carbos ${cPct.toFixed(0)}%`} />
                                                <div className="h-full bg-amber-500/80 transition-all" style={{ width: `${fPct}%` }} title={`Grasas ${fPct.toFixed(0)}%`} />
                                            </div>
                                            <div className="flex gap-2 text-[10px] font-bold text-slate-500">
                                                <span className="text-blue-400">P {pPct.toFixed(0)}%</span>
                                                <span className="text-green-400">C {cPct.toFixed(0)}%</span>
                                                <span className="text-amber-400">G {fPct.toFixed(0)}%</span>
                                            </div>
                                        </>
                                    );
                                })()}
                                <div className="grid gap-4">
                                    {[
                                        { label: 'Proteínas', value: proteinG, set: setProteinG, max: 500, color: 'blue', kcalPerG: 4, tooltip: '4 kcal/g. Objetivo típico: 1.6–2.2 g/kg para ganancia muscular.', allowEmpty: false },
                                        { label: 'Carbohidratos', value: carbsG, set: setCarbsG, max: 800, color: 'green', kcalPerG: 4, tooltip: '4 kcal/g. Principal fuente de energía para entrenamiento.', allowEmpty: false },
                                        { label: 'Grasas', value: fatsG, set: setFatsG, max: 300, color: 'amber', kcalPerG: 9, tooltip: '9 kcal/g. Esenciales para hormonas y saciedad. Mínimo ~0.5 g/kg.', allowEmpty: true },
                                    ].map(({ label, value, set, max, color, kcalPerG, tooltip, allowEmpty }) => {
                                        const numVal = value === '' ? 0 : Number(value);
                                        const kcal = numVal * kcalPerG;
                                        const barColor = color === 'blue' ? 'bg-blue-500' : color === 'green' ? 'bg-green-500' : 'bg-amber-500';
                                        return (
                                            <div key={label} className="p-4 rounded-xl bg-white/5 border border-cyber-copper/20">
                                                <div className="flex justify-between items-center mb-2">
                                                    <span className="text-sm font-bold text-white flex items-center gap-1">{label}<NutritionTooltip content={tooltip} title={label} /></span>
                                                    <span className="text-xs font-mono text-slate-400">{kcal} kcal</span>
                                                </div>
                                                <div className="h-2 rounded-full bg-white/10 overflow-hidden mb-3">
                                                    <div className={`h-full ${barColor} rounded-full transition-all`} style={{ width: `${Math.min(100, (numVal / max) * 100)}%` }} />
                                                </div>
                                                <div className="flex items-center gap-2">
                                                    <input
                                                        type="number"
                                                        value={allowEmpty && value === '' ? '' : value}
                                                        onChange={e => { const v = e.target.value; if (allowEmpty && v === '') (set as (x: number | '') => void)(''); else (set as (x: number) => void)(Number(v) || 0); }}
                                                        min={0}
                                                        max={max}
                                                        className="flex-1 bg-white/5 border border-cyber-copper/20 rounded-lg px-3 py-2 text-white font-mono text-sm focus:border-cyber-copper/50 outline-none"
                                                    />
                                                    <span className="text-[10px] text-slate-500">g</span>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        </div>

                        <div>
                            <h2 className="text-lg font-black text-white uppercase tracking-tight font-mono">Tendencia</h2>
                            <div className="bg-slate-900/50 rounded-xl p-4 border border-cyber-copper/20 mt-4 space-y-2">
                                <p className="text-sm text-slate-400">Con tus macros actuales ({caloriesFromMacros} kcal/día):</p>
                                <p className="text-2xl font-black font-mono text-white">{trend >= 0 ? '+' : ''}{trend.toFixed(2)} kg/sem</p>
                                <p className="text-xs text-slate-500">({pctWeekly >= 0 ? '+' : ''}{pctWeekly.toFixed(2)}% del peso corporal)</p>
                                {(goal === 'lose' || goal === 'gain') && tdee != null && (
                                    <p className="text-xs text-cyber-copper/90 mt-2">
                                        {trendMode === 'kg_per_week' ? `Target: ${goal === 'lose' ? '-' : '+'}${weeklyChangeKg} kg/sem` : `Target: ${trendValue >= 0 ? '+' : ''}${trendValue} % grasa/sem`} → {tdee} kcal/día. Ajusta macros para acercarte.
                                    </p>
                                )}
                                {estimatedEndDate && (
                                    <p className="text-xs text-cyber-copper/90 mt-2 font-mono">
                                        Fecha estimada para alcanzar meta: {estimatedEndDate}
                                    </p>
                                )}
                            </div>
                            {caloriesAlert && (
                                <div className="p-4 rounded-xl border mt-4 bg-red-950/30 border-red-500/50">
                                    <p className="text-sm font-bold text-white">{caloriesAlert}</p>
                                </div>
                            )}
                            {(isDangerLow || isDangerHigh) && (
                                <div className={`p-4 rounded-xl border mt-4 ${isDangerLow ? 'bg-red-950/30 border-red-500/50' : 'bg-amber-950/30 border-amber-500/50'}`}>
                                    <p className="text-sm font-bold text-white">
                                        {isDangerLow ? 'Ritmo muy agresivo: más de 1.2 kg/sem puede afectar masa muscular.' : 'Ritmo muy alto: más de 1.2 kg/sem suele acumular más grasa que músculo.'}
                                    </p>
                                    <p className="text-xs text-slate-400 mt-1">Considera reducir el cambio semanal o ajustar calorías.</p>
                                </div>
                            )}
                            <p className="text-sm text-slate-400 mt-4">Revisa y guarda. Podrás editar todo después en el editor de plan.</p>
                        </div>
                    </div>
                );
            default:
                return null;
        }
    };

    return (
        <div className="min-h-screen bg-black flex flex-col relative overflow-hidden">
            <AnimatedSvgBackground
                src="/fondo-wizards.svg"
                variant="horizontal"
                animation="zoom"
                opacity={0.28}
            />
            <div className="relative z-10 flex-1 flex flex-col min-h-0">
                <div className="flex-1 overflow-y-auto px-4 py-8 pb-28 custom-scrollbar">
                    <div className="max-w-md mx-auto">
                        <div className="flex items-center gap-3 mb-8">
                            <span className="text-[9px] font-black text-cyber-copper uppercase tracking-[0.2em] font-mono shrink-0">
                                Paso {step + 1} de 4
                            </span>
                            <div className="flex-1 h-1.5 bg-white/10 rounded-full overflow-hidden">
                                <div
                                    className="h-full bg-[#FF7B00] rounded-full transition-all duration-300"
                                    style={{ width: `${((step + 1) / 4) * 100}%` }}
                                />
                            </div>
                        </div>
                        <div className="animate-fade-in">{renderStep()}</div>
                    </div>
                </div>
                {/* FAB Atrás */}
                {step > 0 && (
                    <button
                        onClick={() => setStep(s => s - 1)}
                        aria-label="Atrás"
                        className="absolute bottom-6 left-6 z-20 w-12 h-12 rounded-full border border-white/20 bg-black/40 text-zinc-400 hover:text-white hover:bg-black/60 flex items-center justify-center backdrop-blur-sm transition-all"
                    >
                        <ChevronLeftIcon size={22} />
                    </button>
                )}

                {/* FAB Siguiente / Guardar */}
                <button
                    onClick={step < 3 ? () => setStep((s) => s + 1) : handleFinish}
                    aria-label={step < 3 ? 'Siguiente' : 'Guardar plan'}
                    className="absolute bottom-6 right-6 z-20 w-12 h-12 rounded-full border border-cyber-copper/50 bg-cyber-copper/20 text-cyber-copper hover:bg-cyber-copper/30 flex items-center justify-center shadow-lg transition-all"
                >
                    {step < 3 ? <ChevronRightIcon size={22} /> : <CheckIcon size={22} strokeWidth={2.5} />}
                </button>
            </div>
        </div>
    );
};
