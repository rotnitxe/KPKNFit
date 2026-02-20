// components/Home.tsx
import React, { useMemo, useState, useEffect } from 'react';
import { Program, Session, WorkoutLog, Settings, DailyWellbeingLog, SleepLog, View, OngoingWorkoutState, ProgramWeek, ExerciseMuscleInfo } from '../types';
import Card from './ui/Card';
import Button from './ui/Button';
import { PlayIcon, FlameIcon, TrophyIcon, ClockIcon, PlusIcon, ChevronRightIcon, ActivityIcon, TargetIcon, CalendarIcon, AlertTriangleIcon, CheckCircleIcon, InfoIcon, PencilIcon, RefreshCwIcon, StarIcon, SettingsIcon, TrendingUpIcon, XIcon } from './icons';
import { calculateBrzycki1RM } from '../utils/calculations';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateSystemicFatigue, calculateDailyReadiness } from '../services/recoveryService';
import Modal from './ui/Modal';
import { CaupolicanIcon } from './CaupolicanIcon';
import MuscleRecoveryWidget from './MuscleRecoveryWidget';

interface HomeProps {
  onNavigate: (view: View, program?: Program) => void;
  onResumeWorkout: () => void;
  onEditSleepLog: (log: SleepLog) => void;
}

// --- WIDGET 1RM PROGRESO (Miniatura -> Overlay) ---
const Progress1RMWidget: React.FC<{ history: WorkoutLog[] }> = ({ history }) => {
    const [isExpanded, setIsExpanded] = useState(false);
    const [selectedExercise, setSelectedExercise] = useState<string>('');
    const [timePeriod, setTimePeriod] = useState<'1M'|'3M'|'6M'|'ALL'>('ALL');

    const exerciseData = useMemo(() => {
        const data: Record<string, {date: string, rm: number, rpe: number}[]> = {};
        history.forEach(log => {
            (log.completedExercises || []).forEach((ex: any) => {
                const validSets = (ex.sets || []).filter((s: any) => s.weight > 0 && s.completedReps > 0);
                if (validSets.length > 0) {
                    const maxRM = Math.max(...validSets.map((s: any) => calculateBrzycki1RM(s.weight, s.completedReps)));
                    const avgRPE = validSets.reduce((acc: number, s: any) => acc + (s.completedRPE || s.rpe || 8), 0) / validSets.length;
                    if (!data[ex.exerciseName]) data[ex.exerciseName] = [];
                    data[ex.exerciseName].push({ date: log.date, rm: maxRM, rpe: avgRPE });
                }
            });
        });
        Object.values(data).forEach(arr => arr.sort((a,b) => new Date(a.date).getTime() - new Date(b.date).getTime()));
        return data;
    }, [history]);

    const exercises = Object.keys(exerciseData).sort();
    useEffect(() => { if (exercises.length > 0 && !selectedExercise) setSelectedExercise(exercises[0]); }, [exercises, selectedExercise]);

    const filteredData = useMemo(() => {
        if (!selectedExercise || !exerciseData[selectedExercise]) return [];
        const data = exerciseData[selectedExercise];
        if (timePeriod === 'ALL') return data;
        const limit = new Date().getTime() - ((timePeriod === '1M' ? 1 : timePeriod === '3M' ? 3 : 6) * 30 * 24 * 60 * 60 * 1000);
        return data.filter(d => new Date(d.date).getTime() >= limit);
    }, [selectedExercise, timePeriod, exerciseData]);

    const stats = useMemo(() => {
        if (filteredData.length < 2) return { trend: 0, kgPerWeek: 0, avgIntensity: 0 };
        const first = filteredData[0];
        const last = filteredData[filteredData.length - 1];
        const weeks = (new Date(last.date).getTime() - new Date(first.date).getTime()) / (1000 * 60 * 60 * 24 * 7);
        const trend = last.rm - first.rm;
        const kgPerWeek = weeks > 0 ? trend / weeks : 0;
        const avgIntensity = filteredData.reduce((acc, d) => acc + d.rpe, 0) / filteredData.length;
        return { trend, kgPerWeek, avgIntensity };
    }, [filteredData]);

    if (exercises.length === 0) return null;

    // Mini Widget View
    return (
        <>
            <div onClick={() => setIsExpanded(true)} className="bg-[#0a0a0a] border border-[#222] rounded-2xl p-4 cursor-pointer hover:border-[#444] transition-all group flex flex-col justify-between h-32 relative overflow-hidden">
                <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-20 transition-opacity"><TrendingUpIcon size={48}/></div>
                <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest relative z-10 flex items-center gap-2"><TrendingUpIcon size={12}/> Analítica 1RM</h3>
                <div className="relative z-10">
                    <p className="text-2xl font-black text-white leading-none">{stats.kgPerWeek > 0 ? '+' : ''}{stats.kgPerWeek.toFixed(1)} <span className="text-xs text-zinc-500">kg/sem</span></p>
                    <p className="text-[9px] text-zinc-400 font-bold uppercase mt-1 truncate">Progreso Promedio</p>
                </div>
            </div>

            {/* Overlay Full View */}
            <Modal isOpen={isExpanded} onClose={() => setIsExpanded(false)} title="Análisis de Sobrecarga" useCustomContent={true}>
                <div className="bg-[#0a0a0a] w-full max-w-lg mx-auto h-[85vh] sm:h-[650px] flex flex-col text-white">
                    <div className="flex justify-between items-center p-5 border-b border-[#222] shrink-0">
                        <h2 className="text-lg font-black uppercase tracking-tight flex items-center gap-2"><TrendingUpIcon className="text-blue-500"/> Laboratorio 1RM</h2>
                        <button onClick={() => setIsExpanded(false)} className="text-zinc-500 hover:text-white transition-colors p-1"><XIcon size={20}/></button>
                    </div>
                    
                    <div className="flex-1 overflow-y-auto p-6 space-y-6 custom-scrollbar">
                        <select value={selectedExercise} onChange={(e) => setSelectedExercise(e.target.value)} className="w-full bg-[#111] border border-[#222] text-white text-sm font-black uppercase tracking-widest py-4 px-4 rounded-xl outline-none focus:border-zinc-500 transition-colors">
                            {exercises.map(ex => <option key={ex} value={ex}>{ex}</option>)}
                        </select>

                        <div className="flex bg-[#111] p-1 rounded-xl border border-[#222]">
                            {['1M', '3M', '6M', 'ALL'].map(period => (
                                <button key={period} onClick={() => setTimePeriod(period as any)} className={`flex-1 py-2 rounded-lg text-[10px] font-black tracking-widest transition-all ${timePeriod === period ? 'bg-white text-black shadow-md' : 'text-zinc-500 hover:text-zinc-300'}`}>
                                    {period}
                                </button>
                            ))}
                        </div>

                        {/* Chart Area */}
                        {filteredData.length > 0 ? (
                            <div className="relative w-full h-[200px] bg-[#111] border border-[#222] rounded-2xl p-4">
                                <svg viewBox={`0 0 300 100`} className="w-full h-full overflow-visible">
                                    <polyline points={filteredData.map((d, i) => `${filteredData.length === 1 ? 150 : (i / (filteredData.length - 1)) * 300},${100 - ((d.rm - (Math.min(...filteredData.map(d => d.rm)) * 0.9)) / ((Math.max(...filteredData.map(d => d.rm), 1)) - (Math.min(...filteredData.map(d => d.rm)) * 0.9) || 1)) * 100}`).join(' ')} fill="none" stroke="#3b82f6" strokeWidth="3" strokeLinejoin="round" />
                                    {filteredData.map((d, i) => {
                                        const x = filteredData.length === 1 ? 150 : (i / (filteredData.length - 1)) * 300;
                                        const y = 100 - ((d.rm - (Math.min(...filteredData.map(d => d.rm)) * 0.9)) / ((Math.max(...filteredData.map(d => d.rm), 1)) - (Math.min(...filteredData.map(d => d.rm)) * 0.9) || 1)) * 100;
                                        return (
                                            <g key={i}>
                                                <circle cx={x} cy={y} r="4" fill="#0a0a0a" stroke="#3b82f6" strokeWidth="2" />
                                                {i === filteredData.length - 1 && <text x={x} y={y - 12} fill="white" fontSize="12" fontWeight="bold" textAnchor="end">{Math.round(d.rm)}kg</text>}
                                            </g>
                                        );
                                    })}
                                </svg>
                            </div>
                        ) : (
                            <div className="h-[200px] border border-dashed border-[#222] rounded-2xl flex items-center justify-center text-zinc-600 text-xs font-bold uppercase tracking-widest">Sin datos</div>
                        )}

                        {/* Insight Cards */}
                        <div className="grid grid-cols-2 gap-3">
                            <div className="bg-[#111] border border-[#222] p-4 rounded-xl">
                                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block mb-1">Tendencia Total</span>
                                <span className={`text-xl font-black ${stats.trend >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>{stats.trend > 0 ? '+' : ''}{stats.trend.toFixed(1)} kg</span>
                            </div>
                            <div className="bg-[#111] border border-[#222] p-4 rounded-xl">
                                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block mb-1">Intensidad Media</span>
                                <span className="text-xl font-black text-white">RPE {stats.avgIntensity.toFixed(1)}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </Modal>
        </>
    );
};


// --- WIDGET AUGE (Panel Biológico Compacto + Acordeón) ---
const AugeStatusWidget: React.FC = () => {
    const { history, settings, sleepLogs, dailyWellbeingLogs, exerciseList } = useAppState();
    const [isExpanded, setIsExpanded] = useState(false);

    const cnsBattery = useMemo(() => {
        try {
            // Ajuste de firma según el error: (history, sleepLogs, settings, date, exerciseList)
            // Usamos cast a any para que la llamada sea flexible ante cambios en recoveryService
            const fatigueRes = (calculateSystemicFatigue as any)(history, sleepLogs || [], settings, new Date(), exerciseList) || 0;
            
            const fatigueVal = typeof fatigueRes === 'number' 
                ? fatigueRes 
                : (fatigueRes?.totalSystemicFatigue || fatigueRes?.cnsFatigue || fatigueRes?.cnsDrain || 0);
                
            return Math.max(0, Math.min(100, 100 - fatigueVal));
        } catch (e) {
            console.error("Error calculando SNC:", e);
            return 100; // Fallback seguro para evitar pantalla negra
        }
    }, [history, settings, exerciseList, sleepLogs]);
    
    const readiness = useMemo(() => calculateDailyReadiness(sleepLogs, dailyWellbeingLogs, settings, cnsBattery), [sleepLogs, dailyWellbeingLogs, settings, cnsBattery]);

    return (
        <div className="bg-[#0a0a0a] border border-[#222] rounded-2xl overflow-hidden mb-4 transition-all">
            {/* Header Compacto (Siempre Visible) */}
            <div onClick={() => setIsExpanded(!isExpanded)} className="p-4 cursor-pointer hover:bg-[#111] transition-colors flex flex-col gap-3 relative">
                <div className="flex justify-between items-center z-10">
                    <div className="flex items-center gap-2">
                        <ActivityIcon size={14} className={readiness.status === 'red' ? 'text-red-500' : readiness.status === 'yellow' ? 'text-yellow-500' : 'text-emerald-500'}/>
                        <span className="text-[10px] font-black text-white uppercase tracking-widest">Ecosistema AUGE</span>
                    </div>
                    <ChevronRightIcon size={14} className={`text-zinc-500 transition-transform ${isExpanded ? 'rotate-90' : ''}`} />
                </div>
                
                <div className="grid grid-cols-2 gap-4 z-10">
                    <div>
                        <div className="flex justify-between items-end mb-1">
                            <span className="text-[9px] font-bold text-zinc-500 uppercase">SNC (Sistema Central)</span>
                            <span className="text-[10px] font-mono font-bold text-white">{Math.round(readiness.cnsBattery)}%</span>
                        </div>
                        <div className="h-1.5 w-full bg-[#222] rounded-full overflow-hidden">
                            <div className={`h-full transition-all ${readiness.status === 'red' ? 'bg-red-500' : readiness.status === 'yellow' ? 'bg-yellow-500' : 'bg-emerald-500'}`} style={{width: `${readiness.cnsBattery}%`}}></div>
                        </div>
                    </div>
                    <div>
                        <div className="flex justify-between items-end mb-1">
                            <span className="text-[9px] font-bold text-zinc-500 uppercase">Estado Biológico</span>
                            <span className={`text-[9px] font-black uppercase ${readiness.status === 'red' ? 'text-red-400' : readiness.status === 'yellow' ? 'text-yellow-400' : 'text-emerald-400'}`}>
                                {readiness.status === 'red' ? 'Peligro' : readiness.status === 'yellow' ? 'Precaución' : 'Óptimo'}
                            </span>
                        </div>
                        <p className="text-[8px] text-zinc-400 leading-tight truncate">{readiness.recommendation}</p>
                    </div>
                </div>
                {/* Glow de fondo sutil */}
                <div className={`absolute -right-10 -top-10 w-32 h-32 blur-[50px] rounded-full pointer-events-none z-0 opacity-20 ${readiness.status === 'red' ? 'bg-red-500' : readiness.status === 'yellow' ? 'bg-yellow-500' : 'bg-emerald-500'}`} />
            </div>

            {/* Acordeón de Baterías Musculares */}
            <div className={`transition-all duration-500 ease-[cubic-bezier(0.25,1,0.5,1)] ${isExpanded ? 'max-h-[1000px] opacity-100 border-t border-[#222]' : 'max-h-0 opacity-0'}`}>
                <div className="p-4 bg-black">
                    <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-4">Fatiga Muscular Local</p>
                    <MuscleRecoveryWidget />
                </div>
            </div>
        </div>
    );
};

// --- MAIN HOME COMPONENT ---
const Home: React.FC<HomeProps> = ({ onNavigate, onResumeWorkout }) => {
    const { programs, history, activeProgramState, dailyWellbeingLogs, settings } = useAppState();
    // Añadimos handleStartProgram para poder activar programas desde la Home
    const { handleStartWorkout, navigateTo, setIsStartWorkoutModalOpen, handleStartProgram } = useAppDispatch();

    const todayStr = new Date().toISOString().split('T')[0];
    const currentDayOfWeek = new Date().getDay();
    
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);
    
    const todaySessions = useMemo(() => {
        if (!activeProgram || !activeProgramState) return [];
        const mIdx = activeProgramState.currentMacrocycleIndex || 0;
        const macro = activeProgram.macrocycles?.[mIdx];
        if (!macro) return [];

        let activeWeek: ProgramWeek | null = null;
        let mesoIdxTotal = 0;
        
        // Protecciones agresivas contra arrays nulos o indefinidos (Crash Prevention)
        for (const block of (macro.blocks || [])) {
            for (const meso of (block.mesocycles || [])) {
                const week = (meso.weeks || []).find(w => w.id === activeProgramState.currentWeekId);
                if (week) { activeWeek = week; break; }
                mesoIdxTotal++;
            }
            if (activeWeek) break;
        }

        if (!activeWeek || !activeWeek.sessions) return [];

        return activeWeek.sessions
            .filter(s => s.dayOfWeek === currentDayOfWeek)
            .map(session => ({
                session,
                program: activeProgram,
                location: { macroIndex: mIdx, mesoIndex: mesoIdxTotal, weekId: activeWeek!.id },
                isCompleted: history.some(log => log.sessionId === session.id && log.date.startsWith(todayStr))
            }));
    }, [activeProgram, activeProgramState, currentDayOfWeek, history, todayStr]);

    return (
        <div className="relative h-full min-h-screen w-full flex flex-col bg-black pb-32 overflow-y-auto overflow-x-hidden custom-scrollbar">
            
            <div className="relative z-10 flex flex-col px-6 pt-16 w-full max-w-md mx-auto">
                {activeProgram ? (
                    <>
                        {/* HERO SECTION (Sesión de Hoy) */}
                        <div className="mb-6">
                            <h1 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest mb-1">{activeProgram.name}</h1>
                            <h2 className="text-4xl font-black text-white uppercase tracking-tighter leading-none mb-6">
                                {todaySessions.length > 0 ? 'DÍA DE ACCIÓN' : 'DÍA DE DESCANSO'}
                            </h2>

                            {todaySessions.length > 0 ? (
                                <div className="space-y-4">
                                    {todaySessions.map((ts, idx) => (
                                        <div key={idx} className={`relative p-6 rounded-[2rem] border transition-all overflow-hidden ${ts.isCompleted ? 'bg-[#0a0a0a] border-emerald-900/50' : 'bg-white border-white'}`}>
                                            <div className="relative z-10">
                                                {ts.isCompleted && <div className="inline-flex items-center gap-2 px-3 py-1 bg-emerald-500/20 text-emerald-400 text-[9px] font-black uppercase tracking-widest rounded-full mb-4 border border-emerald-500/30"><CheckCircleIcon size={12}/> Completada</div>}
                                                <h3 className={`font-black uppercase tracking-tighter leading-[0.9] text-3xl mb-6 ${ts.isCompleted ? 'text-zinc-600' : 'text-black'}`}>{ts.session.name}</h3>
                                                
                                                {!ts.isCompleted && (
                                                    <button onClick={() => handleStartWorkout(ts.session, ts.program, undefined, ts.location)} className="w-full py-4 bg-black text-white font-black uppercase text-xs tracking-widest rounded-xl hover:scale-[1.02] active:scale-95 transition-all shadow-xl flex justify-center items-center gap-2">
                                                        <PlayIcon size={16} fill="currentColor" /> INICIAR SESIÓN
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <div className="p-8 rounded-[2rem] bg-[#0a0a0a] border border-[#222] text-center">
                                    <CaupolicanIcon size={64} color="#333" />
                                    <p className="text-sm font-bold text-zinc-300 mt-4">Recuperación Activa</p>
                                    <p className="text-[10px] text-zinc-600 font-bold uppercase tracking-widest mt-1">El músculo crece cuando descansas.</p>
                                </div>
                            )}
                        </div>

                        {/* ACCIONES RÁPIDAS */}
                        <div className="flex gap-2 mb-8 overflow-x-auto no-scrollbar pb-2">
                            {todaySessions.length > 0 && (
                                <button onClick={() => navigateTo('session-editor', { programId: activeProgram.id, macroIndex: todaySessions[0].location.macroIndex, mesoIndex: todaySessions[0].location.mesoIndex, weekId: todaySessions[0].location.weekId, sessionId: todaySessions[0].session.id })} className="shrink-0 px-5 py-3 rounded-xl border border-[#222] bg-[#0a0a0a] text-[9px] font-black text-zinc-400 uppercase tracking-widest hover:bg-white hover:text-black hover:border-white transition-colors flex items-center gap-2">
                                    <PencilIcon size={14} /> Modificar
                                </button>
                            )}
                            <button onClick={() => navigateTo('program-detail', { programId: activeProgram.id })} className="shrink-0 px-5 py-3 rounded-xl border border-[#222] bg-[#0a0a0a] text-[9px] font-black text-zinc-400 uppercase tracking-widest hover:bg-white hover:text-black hover:border-white transition-colors flex items-center gap-2">
                                <SettingsIcon size={14} /> Programa
                            </button>
                            <button onClick={() => setIsStartWorkoutModalOpen(true)} className="shrink-0 px-5 py-3 rounded-xl border border-[#222] bg-[#0a0a0a] text-[9px] font-black text-zinc-400 uppercase tracking-widest hover:bg-white hover:text-black hover:border-white transition-colors flex items-center gap-2">
                                <RefreshCwIcon size={14} /> Cambiar
                            </button>
                        </div>

                        {/* WIDGETS DE ANÁLISIS (AUGE + 1RM) */}
                        <div className="space-y-4">
                            <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest ml-1">Análisis Biométrico</h3>
                            <AugeStatusWidget />
                            
                            <div className="grid grid-cols-2 gap-4">
                                <Progress1RMWidget history={history} />
                                {/* Placeholder for another square widget if needed in the future, like Volume or Nutrition sum */}
                                <div className="bg-[#0a0a0a] border border-[#222] rounded-2xl p-4 flex flex-col justify-between h-32 opacity-50">
                                    <h3 className="text-[10px] font-black text-zinc-600 uppercase tracking-widest">Próximamente</h3>
                                    <div className="w-8 h-1 bg-[#222] rounded-full"></div>
                                </div>
                            </div>
                        </div>
                    </>
                ) : programs.length > 0 ? (
                    // NUEVO ESTADO: HAY PROGRAMAS PERO NINGUNO ACTIVO
                    <div className="w-full pt-4 pb-10 animate-fade-in space-y-6">
                        <div className="text-center mb-6">
                            <h2 className="text-2xl sm:text-3xl font-black text-white uppercase tracking-tighter drop-shadow-lg">Selecciona un Programa</h2>
                            <p className="text-zinc-400 text-xs mt-2 font-medium">Tienes programas creados. Activa uno para comenzar.</p>
                        </div>
                        
                        <div className="space-y-4">
                            {programs.map(prog => (
                                <div key={prog.id} className="bg-[#111] border border-[#222] p-5 rounded-2xl flex flex-col gap-4 relative overflow-hidden group hover:border-white/20 transition-colors">
                                    <div className="relative z-10 flex justify-between items-center">
                                        <div>
                                            <h3 className="text-lg font-black text-white uppercase tracking-tight truncate max-w-[200px]">{prog.name}</h3>
                                            <p className="text-[10px] text-zinc-500 font-bold uppercase tracking-widest mt-1">
                                                {prog.macrocycles?.length || 0} Fases • {prog.mode || 'Estándar'}
                                            </p>
                                        </div>
                                        <Button onClick={() => { handleStartProgram(prog.id); navigateTo('program-detail', { programId: prog.id }); }} className="!py-2 !px-4 !text-[10px] !bg-white !text-black hover:scale-105 transition-transform shrink-0">
                                            Activar
                                        </Button>
                                    </div>
                                    <div className="absolute -right-4 -bottom-4 opacity-5 group-hover:opacity-10 transition-opacity pointer-events-none">
                                        <TargetIcon size={80} />
                                    </div>
                                </div>
                            ))}
                        </div>
                        
                        <Button onClick={() => navigateTo('program-editor')} variant="secondary" className="w-full !py-4 !text-xs !font-black !rounded-2xl border-dashed border-[#333] text-zinc-400 hover:text-white mt-4 bg-transparent">
                            <PlusIcon className="mr-2" size={16} /> CREAR OTRO PROGRAMA
                        </Button>
                    </div>
                ) : (
                    // ESTADO VACÍO ORIGINAL (CERO PROGRAMAS)
                    <div className="text-center w-full pt-2 pb-10">
                        <div className="w-full flex justify-center mb-6 animate-fade-in"><CaupolicanIcon size={200} color="white" /></div>
                        <h3 className="text-4xl sm:text-5xl font-black text-white uppercase tracking-tighter mb-4 drop-shadow-lg leading-[0.9]">CREA TU PRIMER<br/><span className="text-[var(--text-color)]">PROGRAMA DE</span><br/>ENTRENAMIENTO</h3>
                        <p className="text-zinc-400 text-xs mb-8 font-medium leading-relaxed max-w-xs mx-auto">No olvides presionar en <span className="text-white font-bold">"iniciar programa"</span> para que aparezcan tus sesiones acá.</p>
                        <Button onClick={() => navigateTo('program-editor')} className="w-full max-w-xs mx-auto !py-4 !text-base !font-black !rounded-2xl !bg-white !text-black border-none mb-10 hover:scale-[1.02] transition-transform">
                            <PlusIcon className="mr-2" /> CREAR PROGRAMA
                        </Button>
                    </div>
                )}
            </div>
        </div>
    );
};

export default Home;