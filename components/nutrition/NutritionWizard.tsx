// components/nutrition/NutritionWizard.tsx
// Wizard de objetivos nutricionales (3 pasos)

import React, { useState, useMemo } from 'react';
import { ChevronDownIcon, ChevronUpIcon } from '../icons';
import type { Settings, CalorieGoalConfig } from '../../types';
import { mifflinStJeor, katchMcArdle } from '../../utils/calorieFormulas';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { ChevronRightIcon, ChevronLeftIcon } from '../icons';
import Button from '../ui/Button';

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

export const NutritionWizard: React.FC<NutritionWizardProps> = ({ onComplete }) => {
    const { settings } = useAppState();
    const { setSettings, addToast } = useAppDispatch();
    const [step, setStep] = useState(1);
    const [goal, setGoal] = useState<CalorieGoalConfig['goal']>(settings.calorieGoalObjective === 'deficit' ? 'lose' : settings.calorieGoalObjective === 'surplus' ? 'gain' : 'maintain');
    const [connectAuge, setConnectAuge] = useState(settings.algorithmSettings?.augeEnableNutritionTracking ?? true);
    const [height, setHeight] = useState(settings.userVitals?.height ?? 170);
    const [weight, setWeight] = useState(settings.userVitals?.weight ?? 70);
    const [age, setAge] = useState(settings.userVitals?.age ?? 30);
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
    const [fatsG, setFatsG] = useState(settings.dailyFatGoal ?? 70);
    const [otherCondition, setOtherCondition] = useState('');
    const [formulaExpanded, setFormulaExpanded] = useState(false);
    const [bioExpanded, setBioExpanded] = useState(false);

    const activityIdx = activityLevel;

    const bmr = useMemo(() => {
        if (!weight || !height || !age) return null;
        const w = Number(weight);
        const h = Number(height);
        const a = Number(age);
        const bf = bodyFat !== '' ? Number(bodyFat) : 15;
        const useKatch = bodyFat !== '' && bf > 0;
        if (useKatch) return katchMcArdle(w, bf);
        const g = gender === 'female' || gender === 'transfemale' ? 'female' : 'male';
        return mifflinStJeor(w, h, a, g);
    }, [weight, height, age, gender, bodyFat]);

    const tdee = useMemo(() => {
        if (bmr == null) return null;
        const factor = ACTIVITY_FACTORS[activityIdx] ?? 1.55;
        let t = bmr * factor;
        if (goal === 'lose') t -= (weeklyChangeKg * 7700) / 7;
        else if (goal === 'gain') t += (weeklyChangeKg * 7700) / 7;
        return Math.round(t * healthMultiplier);
    }, [bmr, activityIdx, goal, weeklyChangeKg, healthMultiplier]);

    const proteinMultiplier = dietPreference === 'vegan' ? 1.15 : dietPreference === 'vegetarian' ? 1.08 : 1;

    const caloriesFromMacros = useMemo(() => {
        const p = proteinG * KCAL_PER_GRAM.protein * proteinMultiplier;
        const c = carbsG * KCAL_PER_GRAM.carbs;
        const f = fatsG * KCAL_PER_GRAM.fat;
        return Math.round(p + c + f);
    }, [proteinG, carbsG, fatsG, proteinMultiplier]);

    const weeklyTrendKg = useMemo(() => {
        if (!tdee || !weight) return null;
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

    const handleFinish = () => {
        const config: CalorieGoalConfig = {
            formula: bodyFat !== '' ? 'katch' : 'mifflin',
            activityLevel: activityIdx,
            goal,
            weeklyChangeKg,
            healthMultiplier,
        };
        setSettings({
            hasSeenNutritionWizard: true,
            calorieGoalConfig: config,
            calorieGoalObjective: goal === 'lose' ? 'deficit' : goal === 'gain' ? 'surplus' : 'maintenance',
            dailyCalorieGoal: caloriesFromMacros,
            dailyProteinGoal: Math.round(proteinG * proteinMultiplier),
            dailyCarbGoal: carbsG,
            dailyFatGoal: fatsG,
            userVitals: {
                ...settings.userVitals,
                height,
                weight,
                age,
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
            case 1:
                return (
                    <div className="space-y-8">
                        <div>
                            <h2 className="text-xl font-black text-white uppercase tracking-tight">Objetivo</h2>
                            <p className="text-slate-400 text-sm mt-1">¿Qué buscas con tu alimentación?</p>
                            <div className="grid grid-cols-3 gap-3 mt-4">
                                {GOAL_OPTIONS.map(opt => (
                                    <button
                                        key={opt.id}
                                        onClick={() => setGoal(opt.id)}
                                        className={`p-4 rounded-xl border text-center transition-all ${
                                            goal === opt.id ? 'bg-white text-black border-white' : 'bg-white/5 border-white/10 text-slate-300 hover:border-white/30'
                                        }`}
                                    >
                                        <span className="font-bold block">{opt.label}</span>
                                        <span className="text-[10px] text-slate-500 mt-1 block">{opt.why}</span>
                                    </button>
                                ))}
                            </div>
                        </div>

                        <div>
                            <h2 className="text-xl font-black text-white uppercase tracking-tight">Conexión AUGE</h2>
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
                            <h2 className="text-xl font-black text-white uppercase tracking-tight">Datos corporales</h2>
                            <p className="text-slate-400 text-sm mt-1">Necesarios para calcular tu TMB y calorías diarias.</p>
                            <div className="space-y-4 mt-4">
                                <div>
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Estatura (cm)</label>
                                    <input type="number" value={height} onChange={e => setHeight(Number(e.target.value) || 170)} min={100} max={250}
                                        className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono" />
                                </div>
                                <div>
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Peso (kg)</label>
                                    <input type="number" value={weight} onChange={e => setWeight(Number(e.target.value) || 70)} min={30} max={300}
                                        className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono" />
                                </div>
                                <div>
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Edad</label>
                                    <input type="number" value={age} onChange={e => setAge(Number(e.target.value) || 30)} min={10} max={120}
                                        className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono" />
                                </div>
                                <div>
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-2">Género</label>
                                    <div className="flex flex-wrap gap-2">
                                        {GENDER_OPTIONS.map(opt => (
                                            <button key={opt.id} onClick={() => setGender(opt.id)}
                                                className={`px-3 py-2 rounded-lg text-sm font-bold ${
                                                    gender === opt.id ? 'bg-white text-black' : 'bg-white/5 text-slate-400'
                                                }`}>
                                                {opt.label}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            </div>
                            <details className="mt-4" open={bioExpanded}>
                                <summary onClick={() => setBioExpanded(!bioExpanded)} className="cursor-pointer flex items-center gap-2 text-amber-400/90 text-xs font-bold hover:text-amber-300">
                                    {bioExpanded ? <ChevronUpIcon size={14} /> : <ChevronDownIcon size={14} />}
                                    Bioimpedancia (opcional)
                                </summary>
                                <div className="grid grid-cols-2 gap-4 mt-3 pl-0">
                                    <div>
                                        <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">% Grasa corporal</label>
                                        <input type="number" value={bodyFat} onChange={e => setBodyFat(e.target.value === '' ? '' : Number(e.target.value))} min={5} max={60} step={0.5} placeholder="Ej: 18"
                                            className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono placeholder-slate-600" />
                                    </div>
                                    <div>
                                        <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">% Masa muscular</label>
                                        <input type="number" value={muscleMass} onChange={e => setMuscleMass(e.target.value === '' ? '' : Number(e.target.value))} min={20} max={50} step={0.5} placeholder="Ej: 42"
                                            className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono placeholder-slate-600" />
                                    </div>
                                </div>
                                {ffmi != null && <p className="text-xs text-cyan-400 font-mono mt-2">FFMI estimado: {ffmi}</p>}
                            </details>
                        </div>
                    </div>
                );
            case 2:
                return (
                    <div className="space-y-8">
                        <div>
                            <h2 className="text-xl font-black text-white uppercase tracking-tight">Nivel de actividad</h2>
                            <p className="text-slate-400 text-sm mt-1">¿Qué tan activo eres fuera del gimnasio?</p>
                            <div className="space-y-2 mt-4">
                                {ACTIVITY_OPTIONS.map(opt => (
                                    <button key={opt.id} onClick={() => setActivityLevel(opt.id)}
                                        className={`w-full p-4 rounded-xl border text-left transition-all ${
                                            activityIdx === opt.id ? 'bg-white text-black border-white' : 'bg-white/5 border-white/10 text-slate-300'
                                        }`}>
                                        <span className="font-bold block">{opt.label}</span>
                                        <span className="text-xs text-slate-500">{opt.desc}</span>
                                    </button>
                                ))}
                            </div>
                        </div>

                        <div>
                            <h2 className="text-xl font-black text-white uppercase tracking-tight">Enfermedades metabólicas</h2>
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
                            <h2 className="text-xl font-black text-white uppercase tracking-tight">Preferencia dietética</h2>
                            <p className="text-slate-400 text-sm mt-1">Vegano/vegetariano: ajustamos proteínas por biodisponibilidad.</p>
                            <div className="flex gap-3 mt-4">
                                {DIET_OPTIONS.map(opt => (
                                    <button key={opt.id} onClick={() => setDietPreference(opt.id)}
                                        className={`flex-1 p-4 rounded-xl border font-bold transition-all ${
                                            dietPreference === opt.id ? 'bg-white text-black border-white' : 'bg-white/5 border-white/10 text-slate-400'
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
                const weightNum = Number(weight) || 70;
                const pctWeekly = weightNum > 0 ? (trend / weightNum) * 100 : 0;
                const trendKgAbs = Math.abs(trend);
                const isDangerLow = goal === 'lose' && (pctWeekly < -1.2 || trendKgAbs > 1.2);
                const isDangerHigh = goal === 'gain' && (pctWeekly > 1.5 || trendKgAbs > 1.2);
                return (
                    <div className="space-y-8">
                        <div>
                            <h2 className="text-xl font-black text-white uppercase tracking-tight">Desglose</h2>
                            <div className="bg-slate-900/50 rounded-xl p-4 border border-cyan-500/20 space-y-2 font-mono text-sm mt-4">
                                <p className="text-cyan-400" title="Tasa Metabólica Basal">TMB: {bmr != null ? Math.round(bmr) : '—'} kcal</p>
                                <p className="text-cyan-400" title="Gasto energético diario total">TDEE: {tdee != null ? tdee : '—'} kcal</p>
                                <p className="text-slate-400 text-xs">Factores Atwater: P=4, C=4, G=9 kcal/g</p>
                                <button
                                    onClick={() => setFormulaExpanded(!formulaExpanded)}
                                    className="flex items-center gap-2 mt-2 text-amber-400/90 text-xs font-bold hover:text-amber-300"
                                >
                                    {formulaExpanded ? <ChevronUpIcon size={14} /> : <ChevronDownIcon size={14} />}
                                    {formulaExpanded ? 'Ocultar fórmulas' : 'Ver fórmulas'}
                                </button>
                                {formulaExpanded && (
                                    <div className="mt-3 pt-3 border-t border-white/10 space-y-2 text-[11px] text-amber-200/80 font-mono">
                                        <p>Mifflin-St Jeor: TMB = 10×peso + 6.25×altura − 5×edad + s</p>
                                        <p>Katch-McArdle: TMB = 370 + (21.6 × LBM)</p>
                                        <p>Calorías = P×4 + C×4 + G×9</p>
                                    </div>
                                )}
                            </div>
                        </div>

                        <div>
                            <h2 className="text-xl font-black text-white uppercase tracking-tight">Edición de macros</h2>
                            {(goal === 'lose' || goal === 'gain') && (
                                <div className="mt-4">
                                    <label className="block text-[10px] font-bold text-slate-500 uppercase mb-2">Cambio semanal (kg)</label>
                                    <select value={weeklyChangeKg} onChange={e => setWeeklyChangeKg(Number(e.target.value))}
                                        className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white">
                                        {[0.25, 0.5, 0.75, 1, 1.5, 2].map(v => <option key={v} value={v}>{v} kg/sem</option>)}
                                    </select>
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
                                    const fKcal = fatsG * 9;
                                    const totalKcal = pKcal + cKcal + fKcal || 1;
                                    const pPct = (pKcal / totalKcal) * 100;
                                    const cPct = (cKcal / totalKcal) * 100;
                                    const fPct = (fKcal / totalKcal) * 100;
                                    return (
                                        <>
                                            <div className="h-4 rounded-full overflow-hidden flex bg-white/10">
                                                <div className="h-full bg-rose-500/80 transition-all" style={{ width: `${pPct}%` }} title={`Proteína ${pPct.toFixed(0)}%`} />
                                                <div className="h-full bg-amber-500/80 transition-all" style={{ width: `${cPct}%` }} title={`Carbos ${cPct.toFixed(0)}%`} />
                                                <div className="h-full bg-sky-500/80 transition-all" style={{ width: `${fPct}%` }} title={`Grasas ${fPct.toFixed(0)}%`} />
                                            </div>
                                            <div className="flex gap-2 text-[10px] font-bold text-slate-500">
                                                <span className="text-rose-400">P {pPct.toFixed(0)}%</span>
                                                <span className="text-amber-400">C {cPct.toFixed(0)}%</span>
                                                <span className="text-sky-400">G {fPct.toFixed(0)}%</span>
                                            </div>
                                        </>
                                    );
                                })()}
                                <div className="grid gap-4">
                                    {[
                                        { label: 'Proteínas', value: proteinG, set: setProteinG, max: 500, color: 'rose', kcalPerG: 4 },
                                        { label: 'Carbohidratos', value: carbsG, set: setCarbsG, max: 800, color: 'amber', kcalPerG: 4 },
                                        { label: 'Grasas', value: fatsG, set: setFatsG, max: 300, color: 'sky', kcalPerG: 9 },
                                    ].map(({ label, value, set, max, color, kcalPerG }) => {
                                        const kcal = value * kcalPerG;
                                        const barColor = color === 'rose' ? 'bg-rose-500' : color === 'amber' ? 'bg-amber-500' : 'bg-sky-500';
                                        return (
                                            <div key={label} className="p-4 rounded-xl bg-white/5 border border-white/10">
                                                <div className="flex justify-between items-center mb-2">
                                                    <span className="text-sm font-bold text-white">{label}</span>
                                                    <span className="text-xs font-mono text-slate-400">{kcal} kcal</span>
                                                </div>
                                                <div className="h-2 rounded-full bg-white/10 overflow-hidden mb-3">
                                                    <div className={`h-full ${barColor} rounded-full transition-all`} style={{ width: `${Math.min(100, (value / max) * 100)}%` }} />
                                                </div>
                                                <div className="flex items-center gap-2">
                                                    <input
                                                        type="number"
                                                        value={value}
                                                        onChange={e => set(Number(e.target.value) || 0)}
                                                        min={0}
                                                        max={max}
                                                        className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-white font-mono text-sm"
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
                            <h2 className="text-xl font-black text-white uppercase tracking-tight">Tendencia</h2>
                            <div className="bg-slate-900/50 rounded-xl p-4 border border-white/10 mt-4 space-y-2">
                                <p className="text-sm text-slate-400">Con tus macros actuales ({caloriesFromMacros} kcal/día):</p>
                                <p className="text-2xl font-black font-mono text-white">{trend >= 0 ? '+' : ''}{trend.toFixed(2)} kg/sem</p>
                                <p className="text-xs text-slate-500">({pctWeekly >= 0 ? '+' : ''}{pctWeekly.toFixed(2)}% del peso corporal)</p>
                                {(goal === 'lose' || goal === 'gain') && tdee != null && (
                                    <p className="text-xs text-cyan-400/90 mt-2">
                                        Target: {weeklyChangeKg} kg/sem → {tdee} kcal/día. Ajusta macros para acercarte.
                                    </p>
                                )}
                            </div>
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
        <div className="min-h-screen bg-[#050505] flex flex-col">
            <div className="flex-1 overflow-y-auto px-4 py-8">
                <div className="max-w-md mx-auto">
                    <div className="flex items-center gap-2 mb-8">
                        <span className="text-[10px] font-black text-cyan-500 uppercase tracking-widest">Paso {step} de 3</span>
                        <div className="flex-1 h-1 bg-white/10 rounded-full overflow-hidden">
                            <div className="h-full bg-cyan-500 rounded-full transition-all" style={{ width: `${(step / 3) * 100}%` }} />
                        </div>
                    </div>
                    {renderStep()}
                </div>
            </div>
            <div className="p-4 pb-24 border-t border-white/10 flex gap-3">
                {step > 1 ? (
                    <Button onClick={() => setStep(s => s - 1)} variant="secondary" className="flex-1">
                        <ChevronLeftIcon size={18} className="mr-1" /> Atrás
                    </Button>
                ) : <div className="flex-1" />}
                {step < 3 ? (
                    <Button onClick={() => setStep(s => s + 1)} className="flex-1">
                        Siguiente <ChevronRightIcon size={18} className="ml-1" />
                    </Button>
                ) : (
                    <Button onClick={handleFinish} className="flex-1">
                        Guardar plan
                    </Button>
                )}
            </div>
        </div>
    );
};
