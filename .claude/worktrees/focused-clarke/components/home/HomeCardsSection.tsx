// components/home/HomeCardsSection.tsx
import React, { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useAppState } from '../../contexts/AppContext';
import { calculateFFMI, calculateIPFGLPoints, calculateBrzycki1RM } from '../../utils/calculations';
import { BabushkaRings } from './BabushkaRings';
import { StarIcon, TrophyIcon, ActivityIcon, HistoryIcon, ArrowUpIcon, ArrowDownIcon, ChevronRightIcon } from '../icons';
import { TodaySessionItem } from './SessionTodayCard';

export const SectionHeader: React.FC<{ title: string }> = ({ title }) => (
    <div className="w-full h-12 px-6 inline-flex justify-between items-center bg-transparent mb-2">
        <div className="text-[#1D1B20] text-[22px] font-black font-['Roboto'] leading-[28px] uppercase tracking-tighter">{title}</div>
    </div>
);

interface HomeCardsSectionProps {
    onNavigateToCard: (view: any) => void;
}

export const HomeCardsSection: React.FC<HomeCardsSectionProps> = ({ onNavigateToCard }) => {
    const { settings, bodyProgress, nutritionLogs, nutritionPlans, activeNutritionPlanId, history, programs, activeProgramState } = useAppState();
    const [ipfFederation, setIpfFederation] = useState<'ipf' | 'wrpf' | 'ipl' | 'custom'>('ipf');

    // ─── Nutrition Calculations ─────────────────────────────────────────────
    const todayStr = useMemo(() => new Date().toISOString().split('T')[0], []);
    const dailyTotals = useMemo(() => {
        const logs = nutritionLogs.filter(l => l.date?.startsWith(todayStr));
        const acc = { calories: 0, protein: 0, carbs: 0, fats: 0 };
        logs.forEach(log => {
            (log.foods || []).forEach((f: any) => {
                acc.calories += f.calories || 0;
                acc.protein += f.protein || 0;
                acc.carbs += f.carbs || 0;
                acc.fats += f.fats || 0;
            });
        });
        return acc;
    }, [nutritionLogs, todayStr]);

    const macroGoals = {
        calories: settings.dailyCalorieGoal || 2500,
        protein: settings.dailyProteinGoal || 150,
        carbs: settings.dailyCarbGoal || 250,
        fats: settings.dailyFatGoal || 70
    };

    // ─── Body Progress Goal ────────────────────────────────────────────────
    const activePlan = useMemo(() => nutritionPlans.find(p => p.id === activeNutritionPlanId) || null, [nutritionPlans, activeNutritionPlanId]);
    const lastLog = bodyProgress.length > 0 ? bodyProgress[bodyProgress.length - 1] : null;
    const prevLog = bodyProgress.length > 1 ? bodyProgress[bodyProgress.length - 2] : null;

    const goalProgress = useMemo(() => {
        if (!activePlan) return null;
        const current = activePlan.goalType === 'weight' ? (lastLog?.weight || settings.userVitals?.weight) :
            activePlan.goalType === 'bodyFat' ? (lastLog?.bodyFatPercentage || settings.userVitals?.bodyFatPercentage) :
                (lastLog?.muscleMassPercentage || settings.userVitals?.muscleMassPercentage);

        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const start = activePlan.goalType === 'weight' ? (sorted[0]?.weight || current) :
            activePlan.goalType === 'bodyFat' ? (sorted[0]?.bodyFatPercentage || current) :
                (sorted[0]?.muscleMassPercentage || current);

        const goal = activePlan.goalValue;
        const pct = (current != null && start != null && goal != null)
            ? Math.min(100, Math.max(0, Math.round((1 - Math.abs(current - goal) / Math.abs(goal - start)) * 100)))
            : 0;

        return { pct, current, goal, label: activePlan.goalType === 'weight' ? 'Peso' : activePlan.goalType === 'bodyFat' ? 'Grasa' : 'Músculo' };
    }, [activePlan, lastLog, bodyProgress, settings.userVitals]);

    // ─── Biometry Metrics ──────────────────────────────────────────────────
    const weightVariation = (lastLog?.weight && prevLog?.weight) ? (lastLog.weight - prevLog.weight) : 0;
    const height = settings.userVitals?.height || 170;
    const imc = (lastLog?.weight && height) ? (lastLog.weight / ((height / 100) ** 2)) : 0;
    const ffmiData = (lastLog?.weight && lastLog?.bodyFatPercentage && height)
        ? calculateFFMI(height, lastLog.weight, lastLog.bodyFatPercentage)
        : null;

    // ─── Exercise Metrics ───────────────────────────────────────────────────
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);
    const starTargets = useMemo(() => {
        let count = 0;
        activeProgram?.macrocycles?.forEach(m => m.blocks?.forEach(b => b.mesocycles?.forEach(me => me.weeks?.forEach(w => w.sessions?.forEach(s => {
            const exs = s.parts?.flatMap((p: any) => p.exercises || []) || s.exercises || [];
            exs.forEach((ex: any) => { if (ex.isStarTarget) count++; });
        })))));
        return count;
    }, [activeProgram]);

    const BASIC_PATTERNS = {
        squat: ['sentadilla', 'squat'],
        bench: ['press banca', 'bench press'],
        deadlift: ['peso muerto', 'deadlift']
    };

    const findBest1RM = (patterns: string[]) => {
        let best = 0;
        history.forEach(log => log.completedExercises?.forEach((ex: any) => {
            if (patterns.some(p => ex.exerciseName?.toLowerCase().includes(p))) {
                ex.sets?.forEach((s: any) => {
                    const rm = calculateBrzycki1RM(s.weight, s.completedReps || s.reps);
                    if (rm > best) best = rm;
                });
            }
        }));
        return best;
    };

    const sVal = findBest1RM(BASIC_PATTERNS.squat);
    const bVal = findBest1RM(BASIC_PATTERNS.bench);
    const dVal = findBest1RM(BASIC_PATTERNS.deadlift);
    const totalPL = sVal + bVal + dVal;
    const relativeStrength = (lastLog?.weight || settings.userVitals?.weight) ? (totalPL / (lastLog?.weight || settings.userVitals?.weight!)) : 0;

    const ipfPoints = (totalPL > 0 && (lastLog?.weight || settings.userVitals?.weight))
        ? calculateIPFGLPoints(totalPL, (lastLog?.weight || settings.userVitals?.weight!), {
            gender: settings.userVitals?.gender || 'male',
            equipment: 'classic',
            lift: 'total',
            weightUnit: settings.weightUnit as 'kg' | 'lbs'
        })
        : 0;

    return (
        <div className="w-full flex flex-col gap-4 overflow-visible">
            {/* ═══ Progreso físico y alimentación ═══ */}
            <div className="flex flex-col gap-4 overflow-visible">
                <SectionHeader title="Progreso físico y alimentación" />

                {/* Goal Progress Bar */}
                {goalProgress && (
                    <div className="px-6 mb-2">
                        <div className="bg-white/40 backdrop-blur-md rounded-3xl p-5 border border-black/[0.03] shadow-sm">
                            <div className="flex justify-between items-end mb-3">
                                <span className="text-[10px] font-black uppercase tracking-[0.2em] text-black/40">Meta de {goalProgress.label}</span>
                                <span className="text-sm font-black text-primary">{goalProgress.pct}%</span>
                            </div>
                            <div className="w-full h-3 bg-black/5 rounded-full overflow-hidden">
                                <motion.div
                                    initial={{ width: 0 }}
                                    animate={{ width: `${goalProgress.pct}%` }}
                                    className="h-full bg-primary rounded-full"
                                />
                            </div>
                            <div className="flex justify-between mt-2 text-[10px] font-bold text-black/30">
                                <span>{goalProgress.current} {settings.weightUnit}</span>
                                <span>Meta: {goalProgress.goal} {settings.weightUnit}</span>
                            </div>
                        </div>
                    </div>
                )}

                {/* Macro Split: Bars vs Babushka */}
                {/* Macro Split: Bars vs Babushka Integrated */}
                <div className="px-6 flex items-center justify-between gap-6 relative overflow-visible">
                    {/* Left: Linear Bars */}
                    <div className="flex-1 bg-white/40 backdrop-blur-md rounded-[32px] p-6 border border-black/[0.03] flex flex-col justify-between shadow-sm z-10">
                        <div className="space-y-4">
                            {[
                                { label: 'Cal', cur: dailyTotals.calories, goal: macroGoals.calories, color: 'bg-primary' },
                                { label: 'Prot', cur: dailyTotals.protein, goal: macroGoals.protein, color: 'bg-[#B3261E]' },
                                { label: 'Carb', cur: dailyTotals.carbs, goal: macroGoals.carbs, color: 'bg-[#D0BCFF]' },
                                { label: 'Fat', cur: dailyTotals.fats, goal: macroGoals.fats, color: 'bg-[#006A6A]' }
                            ].map(m => (
                                <div key={m.label} className="flex flex-col gap-1.5">
                                    <div className="flex justify-between text-[8px] font-black uppercase tracking-widest text-black/40">
                                        <span>{m.label}</span>
                                        <span>{Math.round(m.cur)}g</span>
                                    </div>
                                    <div className="w-full h-1 bg-black/5 rounded-full overflow-hidden">
                                        <div className={`h-full ${m.color} rounded-full`} style={{ width: `${Math.min(100, (m.cur / m.goal) * 100)}%` }} />
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Right: Babushka Rings (Larger, integrated) */}
                    <div className="flex-shrink-0 drop-shadow-2xl">
                        <BabushkaRings
                            carbsPct={dailyTotals.carbs / macroGoals.carbs}
                            proteinPct={dailyTotals.protein / macroGoals.protein}
                            fatPct={dailyTotals.fats / macroGoals.fats}
                            size={160}
                        />
                    </div>
                </div>

                {/* Biometry Cards Carousel */}
                <div className="pl-6 overflow-x-auto no-scrollbar flex gap-4 pb-4 pr-6">
                    {/* Weight Card */}
                    <motion.div onClick={() => onNavigateToCard('body-progress')} className="w-40 h-44 flex-shrink-0 bg-white rounded-[40px] p-6 shadow-sm border border-black/[0.02] flex flex-col justify-between items-start active:scale-95 transition-all">
                        <div className="text-[10px] font-black text-black/30 uppercase tracking-[0.2em]">Peso</div>
                        <div className="flex items-baseline gap-1">
                            <span className="text-3xl font-black text-black">{lastLog?.weight || '--'}</span>
                            <span className="text-[10px] font-bold text-black/30">kg</span>
                        </div>
                        <div className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-black ${weightVariation > 0 ? 'bg-red-50 text-red-600' : weightVariation < 0 ? 'bg-emerald-50 text-emerald-600' : 'bg-black/5 text-black/40'}`}>
                            {weightVariation > 0 ? <ArrowUpIcon size={10} /> : weightVariation < 0 ? <ArrowDownIcon size={10} /> : null}
                            {Math.abs(weightVariation).toFixed(1)}
                        </div>
                    </motion.div>

                    {/* FFMI Card */}
                    <motion.div onClick={() => onNavigateToCard('ffmi')} className="w-40 h-44 flex-shrink-0 bg-white rounded-[40px] p-6 shadow-sm border border-black/[0.02] flex flex-col justify-between items-start active:scale-95 transition-all">
                        <div className="text-[10px] font-black text-black/30 uppercase tracking-[0.2em]">FFMI</div>
                        <div className="text-3xl font-black text-black">{ffmiData ? parseFloat(ffmiData.normalizedFfmi).toFixed(1) : '--'}</div>
                        <div className="text-[10px] font-bold text-black/40 uppercase tracking-widest">{ffmiData ? ffmiData.interpretation : 'S/D'}</div>
                    </motion.div>

                    {/* IMC Card */}
                    <motion.div onClick={() => onNavigateToCard('imc')} className="w-40 h-44 flex-shrink-0 bg-white rounded-[40px] p-6 shadow-sm border border-black/[0.02] flex flex-col justify-between items-start active:scale-95 transition-all">
                        <div className="text-[10px] font-black text-black/30 uppercase tracking-[0.2em]">IMC</div>
                        <div className="text-3xl font-black text-black">{imc ? imc.toFixed(1) : '--'}</div>
                        <div className="text-[10px] font-bold text-black/40 uppercase tracking-widest">Normal</div>
                    </motion.div>

                    {/* % Grasa Card */}
                    <motion.div onClick={() => onNavigateToCard('fat')} className="w-40 h-44 flex-shrink-0 bg-white rounded-[40px] p-6 shadow-sm border border-black/[0.02] flex flex-col justify-between items-start active:scale-95 transition-all">
                        <div className="text-[10px] font-black text-black/30 uppercase tracking-[0.2em]">% Grasa</div>
                        <div className="text-3xl font-black text-black">{lastLog?.bodyFatPercentage || '--'}<span className="text-sm ml-0.5">%</span></div>
                        <div className="w-full h-1 bg-black/5 rounded-full mt-auto overflow-hidden">
                            <div className="h-full bg-amber-500 rounded-full" style={{ width: `${lastLog?.bodyFatPercentage || 0}%` }} />
                        </div>
                    </motion.div>

                    {/* % Musculo Card */}
                    <motion.div onClick={() => onNavigateToCard('muscle')} className="w-40 h-44 flex-shrink-0 bg-white rounded-[40px] p-6 shadow-sm border border-black/[0.02] flex flex-col justify-between items-start active:scale-95 transition-all">
                        <div className="text-[10px] font-black text-black/30 uppercase tracking-[0.2em]">% Músculo</div>
                        <div className="text-3xl font-black text-black">{lastLog?.muscleMassPercentage || '--'}<span className="text-sm ml-0.5">%</span></div>
                        <div className="w-full h-1 bg-black/5 rounded-full mt-auto overflow-hidden">
                            <div className="h-full bg-emerald-500 rounded-full" style={{ width: `${lastLog?.muscleMassPercentage || 0}%` }} />
                        </div>
                    </motion.div>
                </div>
            </div>

            {/* ═══ Tus ejercicios ═══ */}
            <div className="flex flex-col gap-4 overflow-visible">
                <SectionHeader title="Tus ejercicios" />

                <div className="pl-6 overflow-x-auto no-scrollbar flex gap-4 pb-4 pr-6">
                    {/* Meta 1RM Card */}
                    <motion.div
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.95 }}
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.1 }}
                        onClick={() => onNavigateToCard('star-targets')}
                        className="w-56 h-36 flex-shrink-0 bg-white rounded-[40px] p-6 shadow-sm border border-black/[0.02] flex items-center gap-5 active:scale-95 transition-all"
                    >
                        <div className="w-14 h-14 rounded-3xl bg-amber-50 flex items-center justify-center text-amber-500">
                            <StarIcon size={28} filled />
                        </div>
                        <div className="flex-1 flex flex-col">
                            <span className="text-sm font-black text-black uppercase tracking-tight">Metas 1RM</span>
                            <span className="text-xl font-black text-black/80">{starTargets}</span>
                            <span className="text-[10px] font-bold text-black/30 uppercase tracking-widest mt-0.5">Pendientes</span>
                        </div>
                    </motion.div>

                    {/* Historiales Card */}
                    <motion.div
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.95 }}
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.2 }}
                        onClick={() => onNavigateToCard('history')}
                        className="w-56 h-36 flex-shrink-0 bg-white rounded-[40px] p-6 shadow-sm border border-black/[0.02] flex items-center gap-5 active:scale-95 transition-all"
                    >
                        <div className="w-14 h-14 rounded-3xl bg-black/5 flex items-center justify-center text-black/30">
                            <HistoryIcon size={28} />
                        </div>
                        <div className="flex-1 flex flex-col">
                            <span className="text-sm font-black text-black uppercase tracking-tight">Historiales</span>
                            <span className="text-[10px] font-bold text-black/30 uppercase tracking-widest mt-1">Explorar todo</span>
                        </div>
                    </motion.div>

                    {/* Fuerza Relativa Card */}
                    <motion.div
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.95 }}
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.3 }}
                        onClick={() => onNavigateToCard('relative-strength')}
                        className="w-56 h-36 flex-shrink-0 bg-white rounded-[40px] p-6 shadow-sm border border-black/[0.02] flex items-center gap-5 active:scale-95 transition-all"
                    >
                        <div className="w-14 h-14 rounded-3xl bg-emerald-50 flex items-center justify-center text-emerald-500">
                            <ActivityIcon size={28} />
                        </div>
                        <div className="flex-1 flex flex-col">
                            <span className="text-sm font-black text-black uppercase tracking-tight">Fuerza Relativa</span>
                            <span className="text-xl font-black text-black/80">{relativeStrength ? relativeStrength.toFixed(2) : '--'}x</span>
                            <span className="text-[10px] font-bold text-black/30 uppercase tracking-widest mt-0.5">Total: {totalPL}kg</span>
                        </div>
                    </motion.div>

                    {/* IPF GL Points Card */}
                    <div className="w-64 h-36 flex-shrink-0 bg-white rounded-[32px] p-5 shadow-sm border border-black/[0.02] flex flex-col justify-between active:scale-[0.98] transition-all">
                        <div className="flex justify-between items-center">
                            <select
                                value={ipfFederation}
                                onChange={(e) => setIpfFederation(e.target.value as any)}
                                className="bg-transparent border-none text-[10px] font-black uppercase tracking-widest text-primary focus:ring-0 cursor-pointer p-0"
                            >
                                <option value="ipf">Puntos IPF GL</option>
                                <option value="wrpf">Puntos WRPF</option>
                                <option value="ipl">Puntos IPL</option>
                                <option value="custom">Manual</option>
                            </select>
                            <TrophyIcon size={16} className="text-amber-500" />
                        </div>
                        <div className="flex items-center gap-2">
                            <span className="text-3xl font-black text-black">{Math.round(ipfPoints)}</span>
                            <span className="text-[10px] font-bold text-black/30 uppercase tracking-widest">Puntos</span>
                        </div>
                        <button onClick={() => onNavigateToCard('ipf-gl')} className="text-[10px] font-black text-primary uppercase flex items-center gap-1">
                            Ver detalles <ChevronRightIcon size={12} />
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};
