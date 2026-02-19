
// components/MuscleRecoveryWidget.tsx
import React, { useMemo, useState, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateMuscleBattery, calculateSystemicFatigue, learnRecoveryRate } from '../services/recoveryService';
import { calculateCompletedSessionStress, calculateSetStress } from '../services/fatigueService';
import { ActivityIcon, BrainIcon, ChevronRightIcon, DumbbellIcon, ClockIcon, ZapIcon, ChevronDownIcon, FlameIcon, SaveIcon, RefreshCwIcon, XIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';
import { MuscleRecoveryStatus, WorkoutLog, ExerciseMuscleInfo, SleepLog, Program } from '../types';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { calculateAverageVolumeForWeeks } from '../services/analysisService';
import { formatLargeNumber, getWeekId, calculateStreak } from '../utils/calculations'; // Imported utils

const BODY_PART_GROUPS: Record<string, string[]> = {
    'Pecho': ['Pectorales'],
    'Espalda': ['Dorsales', 'Trapecio', 'Romboides', 'Core y Espalda Baja'],
    'Piernas': ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'],
    'Hombros': ['Deltoides'],
    'Brazos': ['Bíceps', 'Tríceps'],
    'Abdomen': ['Abdomen']
};

const ITEMS_PER_PAGE = 6;

const MuscleDetailModal: React.FC<{ 
    status: MuscleRecoveryStatus | null; 
    onClose: () => void;
    programs: Program[];
    exerciseList: ExerciseMuscleInfo[];
}> = ({ status, onClose, programs, exerciseList }) => {
    const { settings, muscleHierarchy, history } = useAppState();
    const { setSettings, addToast } = useAppDispatch();
    
    // Local state for the slider
    const [manualFeel, setManualFeel] = useState(status?.recoveryScore || 50);
    const [isCalibrating, setIsCalibrating] = useState(false);

    // Sync slider when status changes
    useEffect(() => {
        if (status) setManualFeel(status.recoveryScore);
    }, [status]);

    // --- ANALYTICS MEMO ---
    const analysis = useMemo(() => {
        if (!status) return null;

        const allWeeks = programs.flatMap(p => 
            p.macrocycles.flatMap(m => 
                (m.blocks || []).flatMap(b => 
                    b.mesocycles.flatMap(me => me.weeks)
                )
            )
        );
        
        let programmedVolume = 0;
        if (allWeeks.length > 0) {
            const volumeData = calculateAverageVolumeForWeeks(allWeeks, exerciseList, muscleHierarchy, 'complex');
            const targetData = volumeData.find(v => v.muscleGroup.toLowerCase() === status.muscleName.toLowerCase());
            if (targetData) programmedVolume = targetData.displayVolume;
        }

        const drainMap: Record<string, number> = {};
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

        history.filter(log => new Date(log.date) >= thirtyDaysAgo).forEach(log => {
            log.completedExercises.forEach(ex => {
                const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.exerciseName);
                if (!info) return;

                const muscleImpact = info.involvedMuscles.find(m => m.muscle.toLowerCase() === status.muscleName.toLowerCase());
                
                if (muscleImpact && (muscleImpact.role === 'primary' || muscleImpact.role === 'secondary')) {
                     const stress = ex.sets.reduce((acc, s) => acc + calculateSetStress(s, info, 90), 0);
                     const weightedStress = stress * muscleImpact.activation;
                     
                     drainMap[ex.exerciseName] = (drainMap[ex.exerciseName] || 0) + weightedStress;
                }
            });
        });

        const topDrainer = Object.entries(drainMap)
            .sort((a, b) => b[1] - a[1])
            .map(([name, score]) => ({ name, score }))[0];

        return {
            programmedVolume,
            topDrainer: topDrainer || { name: 'N/A', score: 0 }
        };

    }, [status, programs, exerciseList, muscleHierarchy, history]);

    if (!status) return null;

    const handleApplyFeedback = () => {
        setIsCalibrating(true);
        const currentMultiplier = settings.muscleRecoveryMultipliers?.[status.muscleName] || 1.0;
        const newMultiplier = learnRecoveryRate(currentMultiplier, status.recoveryScore, manualFeel);
        setSettings({
            muscleRecoveryMultipliers: {
                ...(settings.muscleRecoveryMultipliers || {}),
                [status.muscleName]: newMultiplier
            }
        });
        setTimeout(() => {
            addToast(`Algoritmo recalibrado para ${status.muscleName}.`, "success");
            setIsCalibrating(false);
            onClose();
        }, 600);
    };

    const getStatusColor = (score: number) => {
        if (score < 40) return 'text-red-500';
        if (score < 85) return 'text-yellow-400';
        return 'text-emerald-400';
    };

    return (
        <Modal isOpen={!!status} onClose={onClose} title={`Bio-Feedback: ${status.muscleName}`}>
            <div className="space-y-6 p-2">
                <div className="flex flex-col items-center justify-center py-6 bg-slate-900/80 rounded-3xl border border-white/5 relative overflow-hidden shadow-2xl">
                    <div className="absolute inset-0 bg-gradient-to-b from-white/5 to-transparent pointer-events-none" />
                    <div className="relative w-40 h-40 flex items-center justify-center z-10">
                        <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                            <path className="text-slate-800" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" strokeWidth="2" />
                            <path className={`${getStatusColor(status.recoveryScore)} transition-all duration-1000`} d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" strokeWidth="2" strokeDasharray={`${status.recoveryScore}, 100`} strokeLinecap="round" />
                        </svg>
                        <div className="absolute inset-0 flex flex-col items-center justify-center">
                            <span className="text-[10px] text-slate-500 uppercase font-black tracking-widest mb-1">Batería</span>
                            <span className={`text-5xl font-black ${getStatusColor(status.recoveryScore)} tracking-tighter`}>{status.recoveryScore}%</span>
                            <p className="text-[9px] text-slate-400 mt-1 font-bold uppercase tracking-widest bg-black/40 px-2 py-0.5 rounded-full border border-white/5">
                                {status.status === 'optimal' ? 'Listo' : status.status === 'recovering' ? 'Recuperando' : 'Agotado'}
                            </p>
                        </div>
                    </div>
                </div>

                <div className="bg-slate-900 p-5 rounded-3xl border border-white/10 space-y-4 shadow-lg">
                    <div className="flex justify-between items-center">
                         <h4 className="text-[10px] font-black text-slate-300 uppercase tracking-widest flex items-center gap-2">
                            <BrainIcon size={14} className="text-primary-color"/> Calibración Neural
                        </h4>
                        <span className={`text-xl font-black ${getStatusColor(manualFeel)}`}>{manualFeel}%</span>
                    </div>
                    <div className="relative h-8 flex items-center">
                        <input type="range" min="0" max="100" value={manualFeel} onChange={e => setManualFeel(parseInt(e.target.value))} className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-white z-20 relative" />
                        <div className="absolute left-0 right-0 h-2 rounded-lg overflow-hidden z-10 pointer-events-none opacity-50"><div className="w-full h-full bg-gradient-to-r from-red-900 via-yellow-900 to-emerald-900"></div></div>
                    </div>
                    <div className="flex justify-between text-[9px] font-bold text-slate-500 uppercase tracking-tighter px-1"><span>Siento Fatiga</span><span>Siento Energía</span></div>
                    <button onClick={handleApplyFeedback} disabled={isCalibrating || manualFeel === status.recoveryScore} className={`w-full py-3 rounded-xl text-xs font-black uppercase tracking-widest border transition-all flex items-center justify-center gap-2 ${manualFeel !== status.recoveryScore ? 'bg-white text-black border-white hover:scale-[1.02] shadow-xl' : 'bg-slate-800 text-slate-500 border-transparent cursor-not-allowed'}`}>
                        {isCalibrating ? <RefreshCwIcon size={14} className="animate-spin"/> : <SaveIcon size={14}/>} {manualFeel !== status.recoveryScore ? 'Guardar mi Sensación' : 'Alineado con IA'}
                    </button>
                    <p className="text-[8px] text-slate-500 text-center leading-relaxed">El algoritmo aprenderá de tu feedback para ajustar la recuperación futura de este músculo.</p>
                </div>

                <div className="grid grid-cols-2 gap-3">
                    <div className="bg-white/5 p-4 rounded-2xl border border-white/5"><p className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">Volumen Sem.</p><p className="text-xl font-black text-white">{analysis?.programmedVolume || 0} <span className="text-[10px] text-slate-500">SETS</span></p></div>
                    <div className="bg-white/5 p-4 rounded-2xl border border-white/5"><p className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">Mayor Desgaste</p><p className="text-sm font-bold text-white leading-tight truncate">{analysis?.topDrainer.name || '---'}</p></div>
                </div>
            </div>
        </Modal>
    );
};

const BatteryDrainAnalysis: React.FC<{ history: WorkoutLog[], exerciseList: ExerciseMuscleInfo[] }> = ({ history, exerciseList }) => {
    const analysisData = useMemo(() => {
        const exerciseDrainMap: Record<string, { cns: number; muscular: number; count: number }> = {};
        const oneMonthAgo = new Date();
        oneMonthAgo.setDate(oneMonthAgo.getDate() - 30);
        const recentHistory = history.filter(log => new Date(log.date) >= oneMonthAgo);

        recentHistory.forEach(log => {
            log.completedExercises.forEach(ex => {
                const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.exerciseName);
                let cnsLoad = 0;
                let muscularLoad = 0;
                const isCompound = info?.type === 'Básico';
                const totalReps = ex.sets.reduce((acc, s) => acc + (s.completedReps || 0), 0);
                const avgIntensity = ex.sets.reduce((acc, s) => acc + (s.completedRPE || 8), 0) / (ex.sets.length || 1);
                const baseStress = totalReps * (avgIntensity / 10);

                if (isCompound) { cnsLoad = baseStress * 1.5; muscularLoad = baseStress * 0.8; } 
                else { cnsLoad = baseStress * 0.4; muscularLoad = baseStress * 1.2; }

                if (avgIntensity >= 9) cnsLoad *= 1.3;
                if (!exerciseDrainMap[ex.exerciseName]) exerciseDrainMap[ex.exerciseName] = { cns: 0, muscular: 0, count: 0 };
                exerciseDrainMap[ex.exerciseName].cns += cnsLoad;
                exerciseDrainMap[ex.exerciseName].muscular += muscularLoad;
                exerciseDrainMap[ex.exerciseName].count += 1;
            });
        });

        const topExercises = Object.entries(exerciseDrainMap)
            .map(([name, data]) => ({
                name: name.length > 15 ? name.substring(0, 15) + '...' : name,
                SNC: Math.round(data.cns),
                Muscular: Math.round(data.muscular),
                total: data.cns + data.muscular
            }))
            .sort((a, b) => b.total - a.total).slice(0, 5);

        return { topExercises };
    }, [history, exerciseList]);

    return (
        <div className="mt-6 pt-6 border-t border-white/5 animate-fade-in space-y-6">
            <h4 className="text-[10px] font-black text-slate-500 mb-3 flex items-center gap-2 uppercase tracking-[0.2em]">
                <ZapIcon size={12} className="text-yellow-400"/> Historial de Desgaste (30d)
            </h4>
            <div className="h-56 w-full bg-black/40 rounded-3xl p-4 border border-white/5">
                <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={analysisData.topExercises} layout="vertical">
                        <XAxis type="number" hide />
                        <YAxis dataKey="name" type="category" width={80} tick={{fontSize: 10, fill: '#64748b', fontWeight: 800}} />
                        <Tooltip contentStyle={{ backgroundColor: '#020617', borderColor: 'rgba(255,255,255,0.1)', borderRadius: '16px', fontSize: '11px' }} />
                        <Bar dataKey="SNC" stackId="a" fill="#f87171" radius={[0, 0, 0, 0]} />
                        <Bar dataKey="Muscular" stackId="a" fill="#3b82f6" radius={[0, 4, 4, 0]} />
                    </BarChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
};

const MuscleRecoveryWidget: React.FC = () => {
    const { history, exerciseList, isAppLoading, sleepLogs, settings, postSessionFeedback, muscleHierarchy, dailyWellbeingLogs, programs } = useAppState();
    const [selectedMuscle, setSelectedMuscle] = useState<MuscleRecoveryStatus | null>(null);
    const [isExpanded, setIsExpanded] = useState(true); // Default extended
    
    // --- MIGRATED HUD LOGIC ---
    const hudStats = useMemo(() => {
        if (!history) return { tonnage: "0", streak: 0 };
        const currentWeekId = getWeekId(new Date(), settings.startWeekOn);
        const weeklyTonnage = history
            .filter(log => getWeekId(new Date(log.date), settings.startWeekOn) === currentWeekId)
            .reduce((acc, log) => acc + log.completedExercises.reduce((sAcc, ex) => 
                sAcc + ex.sets.reduce((setAcc, s) => setAcc + (s.weight || 0) * (s.completedReps || 0), 0)
            , 0), 0);
        const streak = calculateStreak(history, settings).streak;
        return {
            tonnage: formatLargeNumber(weeklyTonnage),
            streak
        };
    }, [history, settings]);
    // -------------------------

    const aggregatedRecovery = useMemo(() => {
        if (!history || history.length === 0) return { systemic: 100, upper: 100, lower: 100 };
         const upperMuscles = ['Pectorales', 'Dorsales', 'Deltoides', 'Bíceps', 'Tríceps'];
         const lowerMuscles = ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'];
         const allMuscles = [...upperMuscles, ...lowerMuscles, 'Abdomen', 'Core y Espalda Baja'];
         const calcAverage = (muscles: string[]) => {
             let totalScore = 0;
             muscles.forEach(m => {
                 const bat = calculateMuscleBattery(m, history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback);
                 totalScore += bat.recoveryScore;
             });
             return Math.round(totalScore / muscles.length);
         };
         return { systemic: calcAverage(allMuscles), upper: calcAverage(upperMuscles), lower: calcAverage(lowerMuscles) };
    }, [history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback]);

    const cnsData = useMemo(() => {
        if (isAppLoading || !history || !sleepLogs) return null;
        return calculateSystemicFatigue(history, sleepLogs, dailyWellbeingLogs);
    }, [history, sleepLogs, dailyWellbeingLogs, isAppLoading]);

    const pagedMuscleData = useMemo(() => {
        if (isAppLoading || !history || !exerciseList || !postSessionFeedback) return [];
        const allMuscles = Object.values(BODY_PART_GROUPS).flat();
        const statuses = allMuscles.map(muscleName => {
            const status = calculateMuscleBattery(muscleName, history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback);
            return { muscleId: muscleName, muscleName: muscleName, ...status } as any;
        });
        statuses.sort((a: any, b: any) => a.recoveryScore - b.recoveryScore);
        const pages: any[] = [];
        for (let i = 0; i < statuses.length; i += ITEMS_PER_PAGE) { pages.push(statuses.slice(i, i + ITEMS_PER_PAGE)); }
        return pages;
    }, [history, exerciseList, isAppLoading, sleepLogs, settings, postSessionFeedback, muscleHierarchy]);

    const getStatusColor = (score: number) => {
        if (score < 40) return 'text-red-500';
        if (score < 85) return 'text-yellow-400';
        return 'text-emerald-400';
    };
    const getRingColor = (score: number) => {
        if (score < 40) return '#ef4444';
        if (score < 85) return '#facc15';
        return '#10b981';
    };

    if (isAppLoading) return <SkeletonLoader lines={4} />;

    return (
        <div className="relative z-50 overflow-visible pb-24">
            <MuscleDetailModal 
                status={selectedMuscle} 
                onClose={() => setSelectedMuscle(null)} 
                programs={programs}
                exerciseList={exerciseList}
            />
            
            <div className={`cursor-pointer group p-4 rounded-3xl transition-all duration-500 bg-black/60 backdrop-blur-2xl border border-white/10 shadow-2xl`}>
                
                {/* --- HEADER: MIGRATED HUD METRICS --- */}
                <div className="flex items-center justify-between mb-8 pb-4 border-b border-white/10">
                    <div className="text-center w-1/3 border-r border-white/10">
                         <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Tonelaje</p>
                         <p className="text-xl font-black text-white">{hudStats.tonnage}</p>
                    </div>
                    <div className="text-center w-1/3">
                         <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest">SNC</p>
                         <p className={`text-xl font-black ${getStatusColor(cnsData?.total || 100)}`}>{cnsData?.total || 100}%</p>
                    </div>
                    <div className="text-center w-1/3 border-l border-white/10">
                         <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Racha</p>
                         <p className="text-xl font-black text-orange-400">{hudStats.streak}d</p>
                    </div>
                </div>

                <div className="flex justify-between items-end mb-4">
                     <h4 className="text-[10px] font-black text-white/40 uppercase tracking-[0.3em] flex items-center gap-2">
                        <ActivityIcon size={14} className="text-primary-color"/> 
                        <span>Batería Biomecánica</span>
                     </h4>
                     <ChevronDownIcon size={16} onClick={() => setIsExpanded(!isExpanded)} className={`text-slate-500 transition-transform duration-500 ${isExpanded ? 'rotate-180' : ''}`}/>
                </div>
                
                <div className="grid grid-cols-3 gap-4">
                    <div className="flex flex-col items-center">
                        <span className={`text-2xl font-black ${getStatusColor(aggregatedRecovery.systemic)}`}>{aggregatedRecovery.systemic}%</span>
                        <span className="text-[8px] text-slate-500 uppercase font-black tracking-widest mt-1">Cuerpo</span>
                        <div className="w-full h-1 bg-white/5 rounded-full mt-2 overflow-hidden backdrop-blur-sm"><div className="h-full rounded-full transition-all duration-1000" style={{width: `${aggregatedRecovery.systemic}%`, backgroundColor: getRingColor(aggregatedRecovery.systemic)}}/></div>
                    </div>
                    <div className="flex flex-col items-center">
                        <span className={`text-2xl font-black ${getStatusColor(aggregatedRecovery.upper)}`}>{aggregatedRecovery.upper}%</span>
                        <span className="text-[8px] text-slate-500 uppercase font-black tracking-widest mt-1">Torso</span>
                        <div className="w-full h-1 bg-white/5 rounded-full mt-2 overflow-hidden backdrop-blur-sm"><div className="h-full rounded-full transition-all duration-1000" style={{width: `${aggregatedRecovery.upper}%`, backgroundColor: getRingColor(aggregatedRecovery.upper)}}/></div>
                    </div>
                    <div className="flex flex-col items-center">
                        <span className={`text-2xl font-black ${getStatusColor(aggregatedRecovery.lower)}`}>{aggregatedRecovery.lower}%</span>
                        <span className="text-[8px] text-slate-500 uppercase font-black tracking-widest mt-1">Pierna</span>
                        <div className="w-full h-1 bg-white/5 rounded-full mt-2 overflow-hidden backdrop-blur-sm"><div className="h-full rounded-full transition-all duration-1000" style={{width: `${aggregatedRecovery.lower}%`, backgroundColor: getRingColor(aggregatedRecovery.lower)}}/></div>
                    </div>
                </div>

                <div className={`transition-all duration-700 ease-[cubic-bezier(0.23,1,0.32,1)] overflow-hidden ${isExpanded ? 'max-h-[2000px] opacity-100 mt-6' : 'max-h-0 opacity-0'}`}>
                    <div className="border-t border-white/5 pt-6">
                        {cnsData && (
                            <div className="mb-6 pb-6 border-b border-white/5">
                                <div className="flex justify-between items-center mb-3">
                                     <div className="flex items-center gap-2 text-slate-400 font-black text-[9px] uppercase tracking-[0.25em]">
                                        <BrainIcon size={12} className={cnsData.total < 40 ? "text-red-400" : "text-sky-400"} />
                                        <span>Estado Red Neural</span>
                                    </div>
                                    <span className="text-xs font-black text-white">{cnsData.total}%</span>
                                </div>
                                <div className="w-full bg-white/5 rounded-full h-1.5 overflow-hidden"><div className={`h-full transition-all duration-1000 ${cnsData.total < 40 ? 'bg-red-500' : 'bg-sky-500'}`} style={{ width: `${cnsData.total}%` }}/></div>
                            </div>
                        )}

                        <div className="relative">
                             <div className="flex items-center justify-between mb-4">
                                 <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-[0.3em]">Celdas Musculares</h4>
                                 <span className="text-[8px] text-slate-700 uppercase font-black">Tap para calibrar</span>
                             </div>
                            <div className="flex overflow-x-auto snap-x snap-mandatory hide-scrollbar pb-2 -mx-2 px-2">
                                {pagedMuscleData.map((page, pageIndex) => (
                                    <div key={pageIndex} className="min-w-full snap-center pr-2">
                                        <div className="space-y-2">
                                            {page.map((muscle) => (
                                                <div key={muscle.muscleId} onClick={(e) => { e.stopPropagation(); setSelectedMuscle(muscle); }} className="flex items-center justify-between p-3.5 rounded-2xl bg-white/5 hover:bg-white/10 transition-all cursor-pointer border border-white/5 group active:scale-[0.97]">
                                                    <div className="flex items-center gap-3">
                                                        <div className={`w-1.5 h-1.5 rounded-full ${muscle.recoveryScore < 40 ? 'bg-red-500 shadow-[0_0_5px_red]' : muscle.recoveryScore < 85 ? 'bg-yellow-500' : 'bg-emerald-500'}`}></div>
                                                        <p className="text-[11px] font-black text-slate-300 group-hover:text-white uppercase tracking-tighter">{muscle.muscleName}</p>
                                                    </div>
                                                    <div className="flex items-center gap-4">
                                                        <span className={`text-xs font-black w-10 text-right ${getStatusColor(muscle.recoveryScore)}`}>{muscle.recoveryScore}%</span>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                        <BatteryDrainAnalysis history={history} exerciseList={exerciseList} />
                    </div>
                </div>
            </div>
        </div>
    );
};

export default MuscleRecoveryWidget;
