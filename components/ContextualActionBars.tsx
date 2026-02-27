
// components/ContextualActionBars.tsx
import React, { useState, useEffect, useMemo } from 'react';
import { TabBarActions, OngoingWorkoutState, Session, Exercise, ExerciseMuscleInfo, OngoingSetData } from '../types';
import {
    CheckCircleIcon, XCircleIcon, PencilIcon, PlusCircleIcon,
    CoachIcon, SaveIcon, PauseIcon, ClockIcon, DumbbellIcon, 
    MoreVerticalIcon, PlayIcon, PlusIcon, ClipboardPlusIcon,
    ActivityIcon, SettingsIcon, Volume2Icon, PaletteIcon
} from './icons';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateSetStress, classifyStressLevel } from '../services/auge';

const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
    const secs = (seconds % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
};

/** Barra mínima cuando el carrusel está activo: timer/stats están en WorkoutHeader */
export const WorkoutCarouselPlaceholderBar: React.FC<{ actions: TabBarActions }> = ({ actions }) => {
    const { ongoingWorkout } = useAppState();
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    if (!ongoingWorkout) return null;
    return (
        <div className="relative h-full px-6 flex items-center justify-between w-full max-w-md mx-auto">
            <div className="flex-1" />
            <div className="flex justify-center items-center h-full">
                <button onClick={(e) => { e.stopPropagation(); actions.onFinishWorkoutPress(); }} className="w-14 h-14 rounded-full bg-gradient-to-br from-green-500 to-emerald-700 text-white flex items-center justify-center shadow-[0_10px_20px_rgba(34,197,94,0.3)] active:scale-95 transition-all border-4 border-slate-950" title="Finalizar">
                    <CheckCircleIcon size={28} strokeWidth={2.5} />
                </button>
            </div>
            <div className="flex-1 flex justify-end">
                <button onClick={() => setIsMenuOpen(!isMenuOpen)} className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all border ${isMenuOpen ? 'bg-slate-700 border-slate-500 text-white shadow-inner' : 'bg-slate-900/90 border-slate-800 text-slate-500 shadow-xl'}`}>
                    <SettingsIcon size={20} />
                </button>
                {isMenuOpen && (
                    <>
                        <div className="fixed inset-0 z-40 bg-black/20" onClick={() => setIsMenuOpen(false)} />
                        <div className="absolute bottom-full right-0 mb-4 w-64 bg-slate-950 border border-slate-800 rounded-3xl shadow-2xl z-50 overflow-hidden animate-modal-enter origin-bottom-right">
                            <div className="p-2 space-y-1">
                                <button onClick={() => { setIsMenuOpen(false); actions.onTimersPress(); }} className="w-full flex items-center gap-3 px-4 py-3 text-left text-xs text-slate-200 hover:bg-slate-800 rounded-2xl transition-colors font-bold"><ClockIcon size={16} className="text-sky-400"/> Cronómetros</button>
                                <button onClick={() => { setIsMenuOpen(false); actions.onPauseWorkoutPress(); }} className="w-full flex items-center gap-3 px-4 py-3 text-left text-xs text-yellow-400 hover:bg-yellow-900/10 rounded-2xl transition-colors font-bold"><PauseIcon size={16}/> Pausar Sesión</button>
                                <button onClick={() => { setIsMenuOpen(false); actions.onCancelWorkoutPress(); }} className="w-full flex items-center gap-3 px-4 py-3 text-left text-xs text-red-400 hover:bg-red-900/10 rounded-2xl transition-colors font-bold"><XCircleIcon size={16}/> Cancelar y Salir</button>
                            </div>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
};

export const WorkoutSessionActionBar: React.FC<{ actions: TabBarActions }> = ({ actions }) => {
    const { ongoingWorkout, restTimer, exerciseList } = useAppState();
    const { handleAdjustRestTimer } = useAppDispatch();
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const [elapsedTime, setElapsedTime] = useState(0);

    useEffect(() => {
        if (!ongoingWorkout) return;
        setElapsedTime(Math.floor((Date.now() - ongoingWorkout.startTime) / 1000));
        const interval = setInterval(() => {
            setElapsedTime(Math.floor((Date.now() - ongoingWorkout.startTime) / 1000));
        }, 1000);
        return () => clearInterval(interval);
    }, [ongoingWorkout?.startTime]);

    const stats = useMemo(() => {
        if (!ongoingWorkout) return { completedSets: 0, totalSets: 0, totalTonnage: 0, totalStress: 0 };
        const totalSets = ongoingWorkout.session.exercises.reduce((acc, ex) => acc + ex.sets.length, 0) + (ongoingWorkout.session.parts?.reduce((acc, p) => acc + p.exercises.reduce((sAcc, ex) => sAcc + ex.sets.length, 0), 0) || 0);
        let totalTonnage = 0;
        let totalStress = 0;
        const allExercises = [...ongoingWorkout.session.exercises, ...(ongoingWorkout.session.parts?.flatMap(p => p.exercises) || [])];
        Object.entries(ongoingWorkout.completedSets).forEach(([setId, data]) => {
             const setPair = data as { left: OngoingSetData | null, right: OngoingSetData | null };
             if (setPair.left) {
                 totalTonnage += (setPair.left.weight || 0) * (setPair.left.reps || 0);
                 const parentEx = allExercises.find(ex => ex.sets.some(s => s.id === setId));
                 const exInfo = parentEx ? exerciseList.find(e => e.id === parentEx.exerciseDbId) : undefined;
                 totalStress += calculateSetStress(setPair.left, exInfo, parentEx?.restTime || 90);
             }
             if (setPair.right) {
                 totalTonnage += (setPair.right.weight || 0) * (setPair.right.reps || 0);
                  const parentEx = allExercises.find(ex => ex.sets.some(s => s.id === setId));
                 const exInfo = parentEx ? exerciseList.find(e => e.id === parentEx.exerciseDbId) : undefined;
                 totalStress += calculateSetStress(setPair.right, exInfo, parentEx?.restTime || 90);
             }
        });
        return { completedSets: Object.keys(ongoingWorkout.completedSets).length, totalSets, totalTonnage, totalStress: Math.round(totalStress) };
    }, [ongoingWorkout, exerciseList]);

    const stressInfo = classifyStressLevel(stats.totalStress);

    if (!ongoingWorkout) return null;

    return (
        <div className="relative h-full px-6 flex items-center justify-between w-full max-w-md mx-auto">
            {/* Timer Left */}
            <div className="flex items-center w-28">
                <div className="flex items-center gap-0.5 bg-slate-900/90 backdrop-blur-md p-1 rounded-xl border border-white/5 shadow-lg w-full justify-between h-10">
                    <button onClick={() => handleAdjustRestTimer(-15)} className="w-8 h-full rounded-lg bg-white/5 hover:bg-white/10 text-slate-400 font-bold text-[10px] flex items-center justify-center transition-all active:scale-90">-15</button>
                    <div className="flex flex-col items-center justify-center flex-grow">
                         {restTimer ? (
                            <span className="text-xs font-mono font-black text-green-400 leading-none">{formatTime(restTimer.remaining)}</span>
                         ) : (
                            <span className="text-[10px] font-mono font-bold text-slate-700 leading-none">--:--</span>
                         )}
                    </div>
                    <button onClick={() => handleAdjustRestTimer(15)} className="w-8 h-full rounded-lg bg-white/5 hover:bg-white/10 text-slate-400 font-bold text-[10px] flex items-center justify-center transition-all active:scale-90">+15</button>
                </div>
            </div>

            {/* Main Action Center */}
            <div className="flex justify-center items-center h-full">
                 <button 
                    onClick={(e) => { e.stopPropagation(); actions.onFinishWorkoutPress(); }} 
                    className="w-14 h-14 rounded-full bg-gradient-to-br from-green-500 to-emerald-700 text-white flex items-center justify-center shadow-[0_10px_20px_rgba(34,197,94,0.3)] active:scale-95 transition-all border-4 border-slate-950"
                    title="Finalizar"
                >
                    <CheckCircleIcon size={28} strokeWidth={2.5} />
                 </button>
            </div>

            {/* Menu/Stats Right */}
            <div className="flex items-center gap-2 justify-end w-32 h-full">
                <div className="flex flex-col items-end">
                    <div className="flex items-center gap-1 text-[9px] font-mono text-slate-400 bg-black/40 px-1.5 py-0.5 rounded-md border border-white/5">
                        <ClockIcon size={10} className="text-sky-400"/>
                        <span>{formatTime(elapsedTime)}</span>
                    </div>
                     <div className="flex items-center gap-1 text-[9px] font-mono text-slate-400 bg-black/40 px-1.5 py-0.5 rounded-md border border-white/5 mt-1">
                        <DumbbellIcon size={10} className="text-primary-color"/>
                        <span>{stats.completedSets}/{stats.totalSets}</span>
                    </div>
                </div>
                
                <button 
                    onClick={() => setIsMenuOpen(!isMenuOpen)} 
                    className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all border ${isMenuOpen ? 'bg-slate-700 border-slate-500 text-white shadow-inner' : 'bg-slate-900/90 border-slate-800 text-slate-500 shadow-xl'}`}
                >
                    <SettingsIcon size={20} />
                </button>

                {isMenuOpen && (
                    <>
                        <div className="fixed inset-0 z-40 bg-black/20" onClick={() => setIsMenuOpen(false)}></div>
                        <div className="absolute bottom-full right-0 mb-4 w-64 bg-slate-950 border border-slate-800 rounded-3xl shadow-2xl z-50 overflow-hidden animate-modal-enter origin-bottom-right">
                            <div className="p-4 bg-slate-900/50 border-b border-slate-800 space-y-3">
                                 <div className="flex justify-between items-center mb-1">
                                    <span className="text-[10px] font-black uppercase text-slate-500 tracking-widest">Estado Fisiológico</span>
                                    <span className={`text-[10px] font-bold ${stressInfo.color}`}>{stressInfo.label}</span>
                                 </div>
                                 <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden mb-2">
                                    <div className={`h-full transition-all duration-500 ${stressInfo.barColor}`} style={{ width: `${Math.min(100, (stats.totalStress / 120) * 100)}%` }}></div>
                                 </div>
                                 <div className="grid grid-cols-2 gap-2 text-center">
                                    <div className="bg-slate-900 p-2 rounded-xl border border-slate-800"><p className="text-[8px] text-slate-500 uppercase font-black mb-0.5">Carga (kg)</p><p className="text-xs font-mono font-bold text-sky-400">{stats.totalTonnage.toLocaleString()}</p></div>
                                    <div className="bg-slate-900 p-2 rounded-xl border border-slate-800"><p className="text-[8px] text-slate-500 uppercase font-black mb-0.5">Fatiga (u)</p><p className="text-xs font-mono font-bold text-cyber-cyan">{stats.totalStress}</p></div>
                                 </div>
                            </div>
                            <div className="p-2 space-y-1">
                                <button onClick={() => { setIsMenuOpen(false); actions.onTimersPress(); }} className="w-full flex items-center gap-3 px-4 py-3 text-left text-xs text-slate-200 hover:bg-slate-800 rounded-2xl transition-colors font-bold"><ClockIcon size={16} className="text-sky-400"/> Cronómetros</button>
                                <button onClick={() => { setIsMenuOpen(false); actions.onPauseWorkoutPress(); }} className="w-full flex items-center gap-3 px-4 py-3 text-left text-xs text-yellow-400 hover:bg-yellow-900/10 rounded-2xl transition-colors font-bold"><PauseIcon size={16}/> Pausar Sesión</button>
                                <button onClick={() => { setIsMenuOpen(false); actions.onCancelWorkoutPress(); }} className="w-full flex items-center gap-3 px-4 py-3 text-left text-xs text-red-400 hover:bg-red-900/10 rounded-2xl transition-colors font-bold"><XCircleIcon size={16}/> Cancelar y Salir</button>
                            </div>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
};

export const EditorActionBar: React.FC<{ context: 'session-editor' | 'log-workout' | 'program-editor', actions: TabBarActions }> = ({ context, actions }) => {
    const slots = Array(5).fill(null);
    slots[0] = { action: actions.onCancelEditPress, icon: XCircleIcon, label: 'Cerrar', className: 'hover:text-red-400' };
    
    let centralAction: (() => void) | undefined;
    let CentralIcon = CheckCircleIcon;
    let centralLabel = 'Guardar';
    let isSuccessLook = true;

    if (context === 'log-workout') { 
        centralAction = actions.onSaveLoggedWorkoutPress; 
        centralLabel = 'Listo'; 
    }
    else if (context === 'session-editor') { 
        centralAction = actions.onSaveSessionPress; 
        CentralIcon = CheckCircleIcon; 
        centralLabel = 'Guardar';
        // Inyectamos botones custom para Sesión Editor usando Eventos Globales
        slots[1] = { action: () => window.dispatchEvent(new Event('openSessionRules')), icon: SettingsIcon, label: 'Reglas', className: 'hover:text-white' };
        slots[3] = { action: () => window.dispatchEvent(new Event('openSessionHistory')), icon: ClockIcon, label: 'Historial', className: 'hover:text-white' };
        slots[4] = { action: actions.onAddExercisePress, icon: PlusCircleIcon, label: 'Añadir', className: 'hover:text-white' };
    }
    else if (context === 'program-editor') { 
        centralAction = actions.onSaveProgramPress; 
        CentralIcon = CheckCircleIcon;
        centralLabel = 'Confirmar';
    }

    const handleAction = (e: React.MouseEvent, action?: () => void) => {
        e.preventDefault();
        e.stopPropagation();
        if (action) action();
    }

    const renderSlot = (slot: any) => {
        if (!slot) return <div className="flex-1"/>;
        const Icon = slot.icon;
        return (
            <button onClick={(e) => handleAction(e, slot.action)} className={`flex flex-col items-center justify-center h-full transition-colors active:scale-90 ${slot.className || ''}`}>
                <Icon size={24}/>
                <span className="text-[8px] mt-1 font-black uppercase tracking-widest">{slot.label}</span>
            </button>
        );
    };

    return (
        <div className="grid grid-cols-5 items-center h-full text-slate-300 px-4 w-full min-w-[320px]">
            {renderSlot(slots[0])}
            {renderSlot(slots[1])}
            
            <div className="flex justify-center items-center h-full relative z-20">
                <button 
                    onClick={(e) => handleAction(e, centralAction)} 
                    className={`w-16 h-16 rounded-full flex flex-col items-center justify-center text-white transition-all active:scale-95 border-4 border-slate-950 shadow-2xl relative
                        ${isSuccessLook 
                            ? 'bg-[#064e3b] border-[#10b981]/30 shadow-[0_0_20px_rgba(16,185,129,0.3)] hover:shadow-[0_0_30px_rgba(16,185,129,0.5)]' 
                            : 'bg-gradient-to-br from-primary-color to-purple-600 shadow-[0_10px_25px_rgba(var(--primary-color-rgb),0.4)]'}
                    `}
                >
                    <CentralIcon size={32} className={isSuccessLook ? 'text-[#10b981]' : ''} strokeWidth={3} />
                </button>
            </div>

            {renderSlot(slots[3])}
            {renderSlot(slots[4])}
        </div>
    );
};
