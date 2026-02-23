// components/nutrition/NutritionPlanEditorModal.tsx
// Editor completo del plan de alimentación - nivel nerd, sin wizard

import React, { useState, useMemo } from 'react';
import type { CalorieGoalConfig, Settings } from '../../types';
import { mifflinStJeor, katchMcArdle } from '../../utils/calorieFormulas';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { TacticalModal } from '../ui/TacticalOverlays';
import Button from '../ui/Button';
import { ChevronDownIcon, ChevronUpIcon } from '../icons';

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

const FORMULA_OPTIONS: { id: CalorieGoalConfig['formula']; label: string }[] = [
    { id: 'mifflin', label: 'Mifflin-St Jeor' },
    { id: 'harris', label: 'Harris-Benedict' },
    { id: 'katch', label: 'Katch-McArdle' },
];

const KCAL_PER_GRAM = { protein: 4, carbs: 4, fat: 9 };

interface NutritionPlanEditorModalProps {
    isOpen: boolean;
    onClose: () => void;
}

const Section: React.FC<{ title: string; children: React.ReactNode; defaultOpen?: boolean }> = ({ title, children, defaultOpen = true }) => {
    const [open, setOpen] = useState(defaultOpen);
    return (
        <div className="border-b border-white/10 last:border-0">
            <button onClick={() => setOpen(!open)} className="w-full py-4 flex justify-between items-center text-left">
                <span className="text-xs font-black text-cyan-500 uppercase tracking-widest">{title}</span>
                {open ? <ChevronUpIcon size={16} /> : <ChevronDownIcon size={16} />}
            </button>
            {open && <div className="pb-4 space-y-4">{children}</div>}
        </div>
    );
};

export const NutritionPlanEditorModal: React.FC<NutritionPlanEditorModalProps> = ({ isOpen, onClose }) => {
    const { settings } = useAppState();
    const { setSettings, addToast } = useAppDispatch();

    const config = settings.calorieGoalConfig || ({} as Partial<CalorieGoalConfig>);
    const activityMap: Record<string, number> = { sedentary: 1, light: 2, moderate: 3, active: 4, very_active: 5 };

    const [goal, setGoal] = useState<CalorieGoalConfig['goal']>(
        settings.calorieGoalObjective === 'deficit' ? 'lose' : settings.calorieGoalObjective === 'surplus' ? 'gain' : 'maintain'
    );
    const [connectAuge, setConnectAuge] = useState(settings.algorithmSettings?.augeEnableNutritionTracking ?? true);
    const [height, setHeight] = useState(settings.userVitals?.height ?? 170);
    const [weight, setWeight] = useState(settings.userVitals?.weight ?? 70);
    const [age, setAge] = useState(settings.userVitals?.age ?? 30);
    const [gender, setGender] = useState(settings.userVitals?.gender ?? 'male');
    const [bodyFat, setBodyFat] = useState<number | ''>(settings.userVitals?.bodyFatPercentage ?? '');
    const [muscleMass, setMuscleMass] = useState<number | ''>(settings.userVitals?.muscleMassPercentage ?? '');
    const [activityLevel, setActivityLevel] = useState(config.activityLevel ?? (activityMap[settings.userVitals?.activityLevel || ''] ?? 3));
    const [metabolicConditions, setMetabolicConditions] = useState<string[]>(settings.metabolicConditions ?? []);
    const [dietPreference, setDietPreference] = useState(settings.dietaryPreference ?? 'omnivore');
    const [formula, setFormula] = useState<CalorieGoalConfig['formula']>(config.formula ?? (bodyFat !== '' ? 'katch' : 'mifflin'));
    const [weeklyChangeKg, setWeeklyChangeKg] = useState(config.weeklyChangeKg ?? 0.5);
    const [healthMultiplier, setHealthMultiplier] = useState(config.healthMultiplier ?? 1);
    const initProteinMult = (settings.dietaryPreference === 'vegan' ? 1.15 : settings.dietaryPreference === 'vegetarian' ? 1.08 : 1);
    const [proteinG, setProteinG] = useState(Math.round((settings.dailyProteinGoal ?? 150) / initProteinMult));
    const [carbsG, setCarbsG] = useState(settings.dailyCarbGoal ?? 250);
    const [fatsG, setFatsG] = useState(settings.dailyFatGoal ?? 70);
    const [otherCondition, setOtherCondition] = useState(() => {
        const known = new Set(METABOLIC_CONDITIONS.map(m => m.id));
        return (settings.metabolicConditions ?? []).filter(c => !known.has(c))[0] || '';
    });
    const [formulaExpanded, setFormulaExpanded] = useState(false);

    const activityIdx = activityLevel;
    const proteinMultiplier = dietPreference === 'vegan' ? 1.15 : dietPreference === 'vegetarian' ? 1.08 : 1;

    const bmr = useMemo(() => {
        if (!weight || !height || !age) return null;
        const w = Number(weight);
        const h = Number(height);
        const a = Number(age);
        const bf = bodyFat !== '' ? Number(bodyFat) : 15;
        const useKatch = (formula === 'katch' || bodyFat !== '') && bf > 0;
        if (useKatch) return katchMcArdle(w, bf);
        const g = gender === 'female' || gender === 'transfemale' ? 'female' : 'male';
        return mifflinStJeor(w, h, a, g);
    }, [weight, height, age, gender, bodyFat, formula]);

    const tdee = useMemo(() => {
        if (bmr == null) return null;
        const factor = ACTIVITY_FACTORS[activityIdx] ?? 1.55;
        let t = bmr * factor;
        if (goal === 'lose') t -= (weeklyChangeKg * 7700) / 7;
        else if (goal === 'gain') t += (weeklyChangeKg * 7700) / 7;
        return Math.round(t * healthMultiplier);
    }, [bmr, activityIdx, goal, weeklyChangeKg, healthMultiplier]);

    const caloriesFromMacros = useMemo(() => {
        const p = proteinG * KCAL_PER_GRAM.protein * proteinMultiplier;
        const c = carbsG * KCAL_PER_GRAM.carbs;
        const f = fatsG * KCAL_PER_GRAM.fat;
        return Math.round(p + c + f);
    }, [proteinG, carbsG, fatsG, proteinMultiplier]);

    const weeklyTrendKg = useMemo(() => {
        if (!tdee || !weight) return null;
        const diff = caloriesFromMacros - tdee;
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

    const handleSave = () => {
        const newConfig: CalorieGoalConfig = {
            formula: bodyFat !== '' ? 'katch' : formula,
            activityLevel: activityIdx,
            goal,
            weeklyChangeKg,
            healthMultiplier,
        };
        const otherConds = metabolicConditions.filter(c => !METABOLIC_CONDITIONS.some(m => m.id === c));
        if (otherCondition.trim()) otherConds.push(otherCondition.trim());

        setSettings({
            calorieGoalConfig: newConfig,
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
            metabolicConditions: metabolicConditions.filter(c => c !== 'none').concat(otherConds),
        });
        addToast('Plan de alimentación actualizado.', 'success');
        onClose();
    };

    const inputClass = 'w-full bg-white/5 border border-white/10 rounded-xl px-4 py-2.5 text-white font-mono text-sm focus:border-cyan-500/50 outline-none';
    const labelClass = 'block text-[10px] font-bold text-slate-500 uppercase mb-1';

    return (
        <TacticalModal isOpen={isOpen} onClose={onClose} title="Editor de plan de alimentación" useCustomContent>
            <div className="flex flex-col flex-1 min-h-0">
            <div className="flex-1 min-h-0 overflow-y-auto px-6 py-4 custom-scrollbar space-y-2">
                <Section title="Objetivo">
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                        {GOAL_OPTIONS.map(opt => (
                            <button
                                key={opt.id}
                                onClick={() => setGoal(opt.id)}
                                className={`p-3 rounded-xl border text-center text-sm font-bold transition-all min-w-0 ${
                                    goal === opt.id ? 'bg-white text-black border-white' : 'bg-white/5 border-white/10 text-slate-400'
                                }`}
                            >
                                {opt.label}
                            </button>
                        ))}
                    </div>
                </Section>

                <Section title="Conexión AUGE">
                    <p className="text-xs text-slate-400 mb-2">Conecta calorías y macros con la batería muscular.</p>
                    <div className="flex gap-3">
                        <button
                            onClick={() => setConnectAuge(true)}
                            className={`flex-1 p-3 rounded-xl border font-bold text-sm ${connectAuge ? 'bg-cyan-500/20 border-cyan-500 text-cyan-300' : 'bg-white/5 border-white/10 text-slate-500'}`}
                        >
                            Sí
                        </button>
                        <button
                            onClick={() => setConnectAuge(false)}
                            className={`flex-1 p-3 rounded-xl border font-bold text-sm ${!connectAuge ? 'bg-cyan-500/20 border-cyan-500 text-cyan-300' : 'bg-white/5 border-white/10 text-slate-500'}`}
                        >
                            No
                        </button>
                    </div>
                </Section>

                <Section title="Datos corporales">
                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className={labelClass}>Estatura (cm)</label>
                            <input type="number" value={height} onChange={e => setHeight(Number(e.target.value) || 170)} min={100} max={250} className={inputClass} />
                        </div>
                        <div>
                            <label className={labelClass}>Peso (kg)</label>
                            <input type="number" value={weight} onChange={e => setWeight(Number(e.target.value) || 70)} min={30} max={300} className={inputClass} />
                        </div>
                        <div>
                            <label className={labelClass}>Edad</label>
                            <input type="number" value={age} onChange={e => setAge(Number(e.target.value) || 30)} min={10} max={120} className={inputClass} />
                        </div>
                        <div>
                            <label className={labelClass}>Género</label>
                            <select value={gender} onChange={e => setGender(e.target.value)} className={inputClass}>
                                {GENDER_OPTIONS.map(opt => (
                                    <option key={opt.id} value={opt.id}>{opt.label}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                </Section>

                <Section title="Bioimpedancia" defaultOpen={false}>
                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className={labelClass}>% Grasa corporal</label>
                            <input type="number" value={bodyFat} onChange={e => setBodyFat(e.target.value === '' ? '' : Number(e.target.value))} min={5} max={60} step={0.5} placeholder="—" className={inputClass} />
                        </div>
                        <div>
                            <label className={labelClass}>% Masa muscular</label>
                            <input type="number" value={muscleMass} onChange={e => setMuscleMass(e.target.value === '' ? '' : Number(e.target.value))} min={20} max={50} step={0.5} placeholder="—" className={inputClass} />
                        </div>
                    </div>
                    {ffmi != null && <p className="text-xs text-cyan-400 font-mono mt-1">FFMI: {ffmi}</p>}
                </Section>

                <Section title="Nivel de actividad">
                    <div className="flex flex-wrap gap-2">
                        {ACTIVITY_OPTIONS.map(opt => (
                            <button key={opt.id} onClick={() => setActivityLevel(opt.id)}
                                className={`px-3 py-2 rounded-xl text-sm font-bold ${activityIdx === opt.id ? 'bg-white text-black' : 'bg-white/5 text-slate-400'}`}>
                                {opt.label}
                            </button>
                        ))}
                    </div>
                </Section>

                <Section title="Enfermedades metabólicas" defaultOpen={false}>
                    <div className="space-y-2">
                        {METABOLIC_CONDITIONS.map(opt => (
                            <label key={opt.id} className="flex items-center gap-2 p-2 rounded-lg bg-white/5 cursor-pointer">
                                <input type="checkbox" checked={metabolicConditions.includes(opt.id)}
                                    onChange={() => {
                                        if (opt.id === 'none') setMetabolicConditions(['none']);
                                        else setMetabolicConditions(prev => prev.includes(opt.id) ? prev.filter(x => x !== opt.id) : [...prev.filter(x => x !== 'none'), opt.id]);
                                    }} className="rounded" />
                                <span className="text-sm text-white">{opt.label}</span>
                            </label>
                        ))}
                        <div>
                            <label className={labelClass}>Otra</label>
                            <input type="text" value={otherCondition} onChange={e => setOtherCondition(e.target.value)} placeholder="Ej: SOP" className={inputClass} />
                        </div>
                    </div>
                </Section>

                <Section title="Preferencia dietética">
                    <div className="flex gap-2">
                        {DIET_OPTIONS.map(opt => (
                            <button key={opt.id} onClick={() => setDietPreference(opt.id)}
                                className={`flex-1 p-3 rounded-xl border font-bold text-sm ${dietPreference === opt.id ? 'bg-white text-black border-white' : 'bg-white/5 border-white/10 text-slate-400'}`}>
                                {opt.label}
                            </button>
                        ))}
                    </div>
                    {dietPreference !== 'omnivore' && (
                        <p className="text-[10px] text-slate-500">Multiplicador proteína: {dietPreference === 'vegan' ? '1.15' : '1.08'}x</p>
                    )}
                </Section>

                <Section title="Fórmula TMB">
                    <div className="flex flex-wrap gap-2">
                        {FORMULA_OPTIONS.map(opt => (
                            <button key={opt.id} onClick={() => setFormula(opt.id)}
                                className={`px-3 py-2 rounded-xl text-xs font-bold ${formula === opt.id ? 'bg-amber-500/20 text-amber-400 border border-amber-500/50' : 'bg-white/5 text-slate-400 border border-white/10'}`}>
                                {opt.label}
                            </button>
                        ))}
                    </div>
                    <button onClick={() => setFormulaExpanded(!formulaExpanded)} className="text-[10px] text-amber-400/80 font-bold mt-1">
                        {formulaExpanded ? 'Ocultar fórmulas' : 'Ver fórmulas'}
                    </button>
                    {formulaExpanded && (
                        <div className="mt-2 p-3 rounded-lg bg-black/30 text-[10px] font-mono text-amber-200/80 space-y-1">
                            <p>Mifflin: TMB = 10×peso + 6.25×altura − 5×edad + s</p>
                            <p>Harris: ver constants</p>
                            <p>Katch: TMB = 370 + (21.6 × LBM), LBM = peso×(1−%grasa/100)</p>
                        </div>
                    )}
                </Section>

                <Section title="Ajustes de calorías">
                    {(goal === 'lose' || goal === 'gain') && (
                        <div>
                            <label className={labelClass}>Cambio semanal (kg)</label>
                            <select value={weeklyChangeKg} onChange={e => setWeeklyChangeKg(Number(e.target.value))} className={inputClass}>
                                {[0.25, 0.5, 0.75, 1, 1.5, 2].map(v => <option key={v} value={v}>{v} kg/sem</option>)}
                            </select>
                        </div>
                    )}
                    <div>
                        <label className={labelClass}>Multiplicador salud (0.5–1.5)</label>
                        <input type="number" value={healthMultiplier} onChange={e => setHealthMultiplier(Number(e.target.value) || 1)} min={0.5} max={1.5} step={0.05} className={inputClass} />
                    </div>
                </Section>

                <Section title="Macros (g/día)">
                    <div className="grid grid-cols-3 gap-3">
                        <div>
                            <label className={labelClass}>Proteínas</label>
                            <input type="number" value={proteinG} onChange={e => setProteinG(Number(e.target.value) || 0)} min={0} max={500} className={inputClass} />
                        </div>
                        <div>
                            <label className={labelClass}>Carbohidratos</label>
                            <input type="number" value={carbsG} onChange={e => setCarbsG(Number(e.target.value) || 0)} min={0} max={800} className={inputClass} />
                        </div>
                        <div>
                            <label className={labelClass}>Grasas</label>
                            <input type="number" value={fatsG} onChange={e => setFatsG(Number(e.target.value) || 0)} min={0} max={300} className={inputClass} />
                        </div>
                    </div>
                    <p className="text-sm font-mono text-white mt-2">{caloriesFromMacros} kcal/día (P×4 + C×4 + G×9)</p>
                </Section>

                <Section title="Resumen calculado">
                    <div className="bg-slate-900/50 rounded-xl p-4 border border-cyan-500/20 space-y-1 font-mono text-sm">
                        <p className="text-cyan-400">TMB: {bmr != null ? Math.round(bmr) : '—'} kcal</p>
                        <p className="text-cyan-400">TDEE: {tdee != null ? tdee : '—'} kcal</p>
                        <p className="text-slate-400">Tendencia: {weeklyTrendKg != null ? `${weeklyTrendKg >= 0 ? '+' : ''}${weeklyTrendKg.toFixed(2)} kg/sem` : '—'}</p>
                    </div>
                </Section>
            </div>

            <div className="flex gap-3 px-6 py-4 border-t border-white/10 flex-shrink-0">
                <Button onClick={onClose} variant="secondary" className="flex-1">Cancelar</Button>
                <Button onClick={handleSave} className="flex-1">Guardar cambios</Button>
            </div>
            </div>
        </TacticalModal>
    );
};
