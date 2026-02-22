// components/nutrition/NutritionWizard.tsx
// Wizard lineal de objetivos nutricionales (9 pasos)

import React, { useState, useMemo } from 'react';
import { ChevronDownIcon, ChevronUpIcon } from '../icons';
import type { Settings, CalorieGoalConfig } from '../../types';
import { mifflinStJeor, katchMcArdle } from '../../utils/calorieFormulas';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { ChevronRightIcon, ChevronLeftIcon, InfoIcon } from '../icons';
import Button from '../ui/Button';

const ACTIVITY_FACTORS: Record<number, number> = {
    1: 1.2, 2: 1.375, 3: 1.55, 4: 1.725, 5: 1.9,
};

const GOAL_OPTIONS: { id: CalorieGoalConfig['goal']; label: string }[] = [
    { id: 'lose', label: 'Definición' },
    { id: 'maintain', label: 'Mantención' },
    { id: 'gain', label: 'Superávit' },
];

const GENDER_OPTIONS: { id: string; label: string }[] = [
    { id: 'male', label: 'Hombre' },
    { id: 'female', label: 'Mujer' },
    { id: 'transmale', label: 'Trans masculino' },
    { id: 'transfemale', label: 'Trans femenino' },
    { id: 'other', label: 'No binario' },
];

const ACTIVITY_OPTIONS: { id: number; label: string }[] = [
    { id: 1, label: 'Sedentario' },
    { id: 2, label: 'Ligero' },
    { id: 3, label: 'Moderado' },
    { id: 4, label: 'Activo' },
    { id: 5, label: 'Muy activo' },
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
                    <div className="space-y-6">
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Objetivo</h2>
                        <p className="text-sm text-slate-400">¿Qué buscas con tu alimentación?</p>
                        <div className="grid grid-cols-3 gap-3">
                            {GOAL_OPTIONS.map(opt => (
                                <button
                                    key={opt.id}
                                    onClick={() => setGoal(opt.id)}
                                    className={`p-4 rounded-xl border text-center font-bold transition-all ${
                                        goal === opt.id ? 'bg-white text-black border-white' : 'bg-white/5 border-white/10 text-slate-300 hover:border-white/30'
                                    }`}
                                >
                                    {opt.label}
                                </button>
                            ))}
                        </div>
                    </div>
                );
            case 2:
                return (
                    <div className="space-y-6">
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Conexión AUGE</h2>
                        <p className="text-sm text-slate-400">¿Conectar calorías y macros a la batería muscular para el cálculo de recuperación?</p>
                        <div className="flex gap-4">
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
                        <p className="text-xs text-slate-500">Si no trackeas siempre, desconectar evita deformar el cálculo de la batería.</p>
                    </div>
                );
            case 3:
                return (
                    <div className="space-y-6">
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Datos corporales</h2>
                        <div className="space-y-4">
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
                    </div>
                );
            case 4:
                return (
                    <div className="space-y-6">
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Bioimpedancia (opcional)</h2>
                        <p className="text-sm text-slate-400">Si tienes datos de báscula o informe, ingrésalos. Si no, usaremos valores genéricos.</p>
                        <div className="grid grid-cols-2 gap-4">
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
                        {ffmi != null && <p className="text-xs text-cyan-400 font-mono">FFMI estimado: {ffmi}</p>}
                    </div>
                );
            case 5:
                return (
                    <div className="space-y-6">
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Nivel de actividad</h2>
                        <p className="text-sm text-slate-400">¿Qué tan activo eres fuera del gimnasio?</p>
                        <div className="space-y-2">
                            {ACTIVITY_OPTIONS.map(opt => (
                                <button key={opt.id} onClick={() => setActivityLevel(opt.id)}
                                    className={`w-full p-4 rounded-xl border text-left font-bold transition-all ${
                                        activityIdx === opt.id ? 'bg-white text-black border-white' : 'bg-white/5 border-white/10 text-slate-300'
                                    }`}>
                                    {opt.label}
                                </button>
                            ))}
                        </div>
                    </div>
                );
            case 6:
                return (
                    <div className="space-y-6">
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Enfermedades metabólicas</h2>
                        <p className="text-sm text-slate-400">¿Alguna condición que afecte tu metabolismo? (para ajuste de calorías)</p>
                        <div className="space-y-2">
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
                        </div>
                        <div>
                            <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Otra (opcional)</label>
                            <input type="text" value={otherCondition} onChange={e => setOtherCondition(e.target.value)} placeholder="Ej: SOP"
                                className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white placeholder-slate-600" />
                        </div>
                    </div>
                );
            case 7:
                return (
                    <div className="space-y-6">
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Preferencia dietética</h2>
                        <p className="text-sm text-slate-400">Si eres vegano, ajustamos el requerimiento de proteínas (+15%) por biodisponibilidad.</p>
                        <div className="flex gap-3">
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
                );
            case 8:
                return (
                    <div className="space-y-6">
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Desglose y edición</h2>
                        <div className="bg-slate-900/50 rounded-xl p-4 border border-cyan-500/20 space-y-2 font-mono text-sm">
                            <p className="text-cyan-400" title="Tasa Metabólica Basal: calorías en reposo total">TMB: {bmr != null ? Math.round(bmr) : '—'} kcal</p>
                            <p className="text-cyan-400" title="Gasto energético diario total (TMB × factor actividad ± déficit/superávit)">TDEE: {tdee != null ? tdee : '—'} kcal</p>
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
                                    <p>Katch-McArdle: TMB = 370 + (21.6 × LBM), LBM = peso×(1−%grasa/100)</p>
                                    <p>Calorías = P×4 + C×4 + G×9</p>
                                </div>
                            )}
                        </div>
                        {(goal === 'lose' || goal === 'gain') && (
                            <div>
                                <label className="block text-[10px] font-bold text-slate-500 uppercase mb-2">Cambio semanal (kg)</label>
                                <select value={weeklyChangeKg} onChange={e => setWeeklyChangeKg(Number(e.target.value))}
                                    className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white">
                                    {[0.25, 0.5, 0.75, 1, 1.5, 2].map(v => <option key={v} value={v}>{v} kg/sem</option>)}
                                </select>
                            </div>
                        )}
                        <div>
                            <label className="block text-[10px] font-bold text-slate-500 uppercase mb-2">Multiplicador salud (0.8–1.2)</label>
                            <input type="number" value={healthMultiplier} onChange={e => setHealthMultiplier(Number(e.target.value) || 1)} min={0.8} max={1.2} step={0.05}
                                className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono" />
                        </div>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Proteínas (g)</label>
                                <input type="number" value={proteinG} onChange={e => setProteinG(Number(e.target.value) || 0)} min={0} max={500}
                                    className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono" />
                            </div>
                            <div>
                                <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Carbohidratos (g)</label>
                                <input type="number" value={carbsG} onChange={e => setCarbsG(Number(e.target.value) || 0)} min={0} max={800}
                                    className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono" />
                            </div>
                            <div>
                                <label className="block text-[10px] font-bold text-slate-500 uppercase mb-1">Grasas (g)</label>
                                <input type="number" value={fatsG} onChange={e => setFatsG(Number(e.target.value) || 0)} min={0} max={300}
                                    className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white font-mono" />
                            </div>
                        </div>
                        <p className="text-2xl font-black text-white font-mono">{caloriesFromMacros} kcal/día</p>
                    </div>
                );
            case 9:
                const trend = weeklyTrendKg ?? 0;
                const weightNum = Number(weight) || 70;
                const pctWeekly = weightNum > 0 ? (trend / weightNum) * 100 : 0;
                const isDangerLow = goal === 'lose' && pctWeekly < -0.5;
                const isDangerHigh = goal === 'gain' && pctWeekly > 1;
                return (
                    <div className="space-y-6">
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Tendencia y alertas</h2>
                        <div className="bg-slate-900/50 rounded-xl p-4 border border-white/10">
                            <p className="text-sm text-slate-400">Tendencia semanal estimada</p>
                            <p className="text-2xl font-black font-mono text-white">{trend >= 0 ? '+' : ''}{trend.toFixed(2)} kg/sem</p>
                            <p className="text-xs text-slate-500 mt-1">({pctWeekly >= 0 ? '+' : ''}{pctWeekly.toFixed(2)}% del peso)</p>
                        </div>
                        {(isDangerLow || isDangerHigh) && (
                            <div className={`p-4 rounded-xl border ${isDangerLow ? 'bg-red-950/30 border-red-500/50' : 'bg-amber-950/30 border-amber-500/50'}`}>
                                <p className="text-sm font-bold text-white">
                                    {isDangerLow ? 'Ritmo muy agresivo: riesgo de pérdida de masa muscular.' : 'Ritmo muy alto: considera reducir el superávit.'}
                                </p>
                                <p className="text-xs text-slate-400 mt-1">Ajusta calorías o cambio semanal en el paso anterior.</p>
                            </div>
                        )}
                        <p className="text-sm text-slate-400">Revisa el resumen y confirma para guardar tu configuración.</p>
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
                        <span className="text-[10px] font-black text-cyan-500 uppercase tracking-widest">Paso {step} de 9</span>
                        <div className="flex-1 h-1 bg-white/10 rounded-full overflow-hidden">
                            <div className="h-full bg-cyan-500 rounded-full transition-all" style={{ width: `${(step / 9) * 100}%` }} />
                        </div>
                    </div>
                    {renderStep()}
                </div>
            </div>
            <div className="p-4 border-t border-white/10 flex gap-3">
                {step > 1 ? (
                    <Button onClick={() => setStep(s => s - 1)} variant="secondary" className="flex-1">
                        <ChevronLeftIcon size={18} className="mr-1" /> Atrás
                    </Button>
                ) : <div className="flex-1" />}
                {step < 9 ? (
                    <Button onClick={() => setStep(s => s + 1)} className="flex-1">
                        Siguiente <ChevronRightIcon size={18} className="ml-1" />
                    </Button>
                ) : (
                    <Button onClick={handleFinish} className="flex-1">
                        Finalizar
                    </Button>
                )}
            </div>
        </div>
    );
};
