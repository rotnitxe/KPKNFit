// components/Home.tsx
import React, { useMemo } from 'react';
import { Program, Session, SleepLog, View, ProgramWeek } from '../types';
import Button from './ui/Button';
import { PlusIcon, TargetIcon } from './icons';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { CaupolicanIcon } from './CaupolicanIcon';
import { SessionTodayCard, BatteryCockpitWidget, ReadinessWidget, StreakWidget, QuickLogWidget } from './home/index';

interface HomeProps {
  onNavigate: (view: View, program?: Program) => void;
  onResumeWorkout: () => void;
  onEditSleepLog: (log: SleepLog) => void;
}

// --- MAIN HOME COMPONENT ---
const Home: React.FC<HomeProps> = ({ onNavigate, onResumeWorkout }) => {
    const { programs, history, activeProgramState } = useAppState();
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
                        {/* HUB HEADER - Cockpit style */}
                        <div className="mb-6">
                            <h1 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.25em] font-mono mb-0.5">
                                {activeProgram.name}
                            </h1>
                            <p className="text-[9px] font-mono text-zinc-600 tracking-widest">
                                {new Date().toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'short', year: 'numeric' })}
                            </p>
                        </div>

                        {/* Fila 1: Sesión | Batería (igual prioridad) */}
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
                            <SessionTodayCard
                                programName={activeProgram.name}
                                programId={activeProgram.id}
                                todaySessions={todaySessions}
                                onStartWorkout={handleStartWorkout}
                                onEditSession={(programId, macroIndex, mesoIndex, weekId, sessionId) =>
                                    navigateTo('session-editor', { programId, macroIndex, mesoIndex, weekId, sessionId })
                                }
                                onViewProgram={(programId) => navigateTo('program-detail', { programId })}
                                onOpenStartWorkoutModal={() => setIsStartWorkoutModalOpen(true)}
                            />
                            <div data-testid="recovery-widget" aria-label="Recovery">
                                <BatteryCockpitWidget />
                            </div>
                        </div>

                        {/* Fila 2: Readiness | Streak */}
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
                            <ReadinessWidget />
                            <StreakWidget />
                        </div>

                        {/* Fila 3: Quick Log */}
                        <div className="mb-8">
                            <QuickLogWidget />
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