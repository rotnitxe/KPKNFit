// components/Home.tsx
// Reforma Home: pantalla seria con secciones (Hoy, Estado, Progreso, Programa), hero contextual, acordeón

import React, { useMemo, useState } from 'react';
import { Program, Session, SleepLog, View, ProgramWeek, WorkoutLog } from '../types';
import Button from './ui/Button';
import { PlusIcon, TargetIcon, LinkIcon } from './icons';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { CaupolicanIcon } from './CaupolicanIcon';
import {
    SessionTodayCard,
    AugeTelemetryPanel,
    HomeCardsSection,
} from './home/index';
import { ExerciseHistoryNerdView } from './ExerciseHistoryNerdView';
import { getLocalDateString } from '../utils/dateUtils';
import { WorkoutShareCard, buildShareCardDataFromLog } from './FinishWorkoutModal';
import { shareElementAsImage } from '../services/shareService';

interface HomeProps {
    onNavigate: (view: View, program?: Program) => void;
    onResumeWorkout: () => void;
    onEditSleepLog: (log: SleepLog) => void;
    /** Navegación desde tarjetas del Home */
    onNavigateToCard?: (cardType: string) => void;
}

const Home: React.FC<HomeProps> = ({ onNavigate, onResumeWorkout, onNavigateToCard }) => {
    const { programs, history, activeProgramState, settings, ongoingWorkout } = useAppState();
    const { handleStartWorkout, navigateTo, setIsStartWorkoutModalOpen, handleStartProgram } = useAppDispatch();
    const [exerciseHistoryModal, setExerciseHistoryModal] = useState<string | null>(null);
    const [shareLog, setShareLog] = useState<WorkoutLog | null>(null);
    const [isSharing, setIsSharing] = useState(false);

    const todayStr = getLocalDateString();
    const currentDayOfWeek = new Date().getDay();

    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);

    const todaySessions = useMemo(() => {
        if (!activeProgram || !activeProgramState) return [];
        const mIdx = activeProgramState.currentMacrocycleIndex || 0;
        const macro = activeProgram.macrocycles?.[mIdx];
        if (!macro) return [];

        let activeWeek: ProgramWeek | null = null;
        let mesoIdxTotal = 0;

        for (const block of (macro.blocks || [])) {
            for (const meso of (block.mesocycles || [])) {
                const week = (meso.weeks || []).find(w => w.id === activeProgramState.currentWeekId);
                if (week) {
                    activeWeek = week;
                    break;
                }
                mesoIdxTotal++;
            }
            if (activeWeek) break;
        }

        if (!activeWeek || !activeWeek.sessions) return [];

        return activeWeek.sessions
            .filter(s => s.dayOfWeek === currentDayOfWeek)
            .map(session => {
                const log = history.find(l => l.sessionId === session.id && l.date.startsWith(todayStr));
                return {
                    session,
                    program: activeProgram,
                    location: { macroIndex: mIdx, mesoIndex: mesoIdxTotal, weekId: activeWeek!.id },
                    isCompleted: !!log,
                    log: log ?? undefined,
                };
            });
    }, [activeProgram, activeProgramState, currentDayOfWeek, history, todayStr]);

    const sessionsWithOngoing = useMemo(() => {
        if (!ongoingWorkout) return todaySessions;
        const inList = todaySessions.some(ts => ts.session.id === ongoingWorkout.session.id && ts.program.id === ongoingWorkout.programId);
        if (inList) return todaySessions;
        const ongoingProgram = programs.find(p => p.id === ongoingWorkout.programId) || activeProgram;
        if (!ongoingProgram) return todaySessions;
        return [{
            session: ongoingWorkout.session,
            program: ongoingProgram,
            location: { macroIndex: ongoingWorkout.macroIndex ?? 0, mesoIndex: ongoingWorkout.mesoIndex ?? 0, weekId: ongoingWorkout.weekId || '' },
            isCompleted: false,
        }, ...todaySessions];
    }, [todaySessions, ongoingWorkout, activeProgram, programs]);

    const handleShareLog = (log: WorkoutLog) => setShareLog(log);

    const dateHeaderStr = new Date().toLocaleDateString('es-ES', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
    });

    const renderWithProgram = () => (
        <>
            {/* Anillos arriba — hero #1a1a1a; fondo general #121212 más oscuro */}
            <div
                className="w-full flex flex-col"
                style={{ backgroundColor: '#1a1a1a', paddingTop: 'max(1.25rem, env(safe-area-inset-top, 0px))' }}
            >
                <div className="w-full max-w-md mx-auto px-4 sm:px-6 pt-2 pb-5">
                    <p className="text-xs text-zinc-500 mb-3">{dateHeaderStr}</p>
                    <AugeTelemetryPanel variant="hero" shareable />
                </div>
            </div>

            {/* Sesión de hoy + resto del contenido — debajo de los anillos */}
            <div className="relative z-10 flex flex-col w-full max-w-md mx-auto pb-6">
                <div className="flex flex-col px-6 pt-4 space-y-6">
                <div className="w-full">
                    {(() => {
                        const primaryProgram = (sessionsWithOngoing.length > 0 && ongoingWorkout && todaySessions.length === 0)
                            ? programs.find(p => p.id === ongoingWorkout.programId) || activeProgram!
                            : activeProgram!;
                        return (
                            <SessionTodayCard
                                key="session"
                                variant="continuation"
                                programName={primaryProgram?.name ?? 'Entrenamiento'}
                                programId={primaryProgram?.id ?? ''}
                                todaySessions={sessionsWithOngoing}
                                ongoingWorkout={ongoingWorkout ? { session: ongoingWorkout.session, programId: ongoingWorkout.programId, isPaused: ongoingWorkout.isPaused } : null}
                                onStartWorkout={handleStartWorkout}
                                onResumeWorkout={onResumeWorkout}
                                onEditSession={(programId, macroIndex, mesoIndex, weekId, sessionId) =>
                                    navigateTo('session-editor', { programId, macroIndex, mesoIndex, weekId, sessionId })
                                }
                                onViewProgram={(programId) => navigateTo('program-detail', { programId })}
                                onOpenStartWorkoutModal={() => setIsStartWorkoutModalOpen(true)}
                                onShareLog={handleShareLog}
                            />
                        );
                    })()}
                </div>

                {/* Tarjetas cuadradas por categorías */}
                <HomeCardsSection
                    onNavigateToCard={(cardType) => {
                        if (onNavigateToCard) onNavigateToCard(cardType);
                        else if (['macros', 'evolution'].includes(cardType)) navigateTo('body-progress');
                        else if (cardType === 'calories-history') navigateTo('nutrition');
                        else if (activeProgram) navigateTo('program-detail', { programId: activeProgram.id });
                    }}
                />
                </div>
            </div>
        </>
    );

    const renderSelectProgram = () => (
        <>
            <div className="w-full flex flex-col" style={{ backgroundColor: '#1a1a1a', paddingTop: 'max(1.25rem, env(safe-area-inset-top, 0px))' }}>
                <div className="w-full max-w-md mx-auto px-4 sm:px-6 pt-2 pb-5">
                    <p className="text-xs text-zinc-500 mb-3">{dateHeaderStr}</p>
                    <AugeTelemetryPanel variant="hero" shareable />
                </div>
            </div>
            <div className="relative z-10 flex flex-col px-6 pt-5 w-full max-w-md mx-auto pb-10 space-y-6">
                <div className="text-center mb-4">
                    <h2 className="text-xl font-black text-white uppercase tracking-tight">Selecciona un Programa</h2>
                    <p className="text-zinc-400 text-xs mt-1.5">Activa uno para comenzar.</p>
                </div>
            <HomeCardsSection
                onNavigateToCard={(cardType) => {
                    if (onNavigateToCard) onNavigateToCard(cardType);
                    else if (['macros', 'evolution'].includes(cardType)) navigateTo('body-progress');
                    else if (cardType === 'calories-history') navigateTo('nutrition');
                    else if (programs[0]) navigateTo('program-detail', { programId: programs[0].id });
                }}
            />
            <div className="space-y-4 mt-6">
                {programs.map(prog => (
                    <div
                        key={prog.id}
                        className="bg-gradient-to-b from-white/[0.06] to-transparent border border-white/10 p-5 rounded-2xl flex flex-col gap-4 relative overflow-hidden group hover:border-cyan-500/30 hover:shadow-lg hover:shadow-cyan-500/5 transition-all duration-300"
                    >
                        <div className="relative z-10 flex justify-between items-center">
                            <div>
                                <h3 className="text-lg font-black text-white uppercase tracking-tight truncate max-w-[200px]">{prog.name}</h3>
                                <p className="text-[10px] text-zinc-500 font-bold uppercase tracking-widest mt-1">
                                    {prog.macrocycles?.length || 0} Fases • {prog.mode || 'Estándar'}
                                </p>
                            </div>
                            <Button
                                onClick={() => {
                                    handleStartProgram(prog.id);
                                    navigateTo('program-detail', { programId: prog.id });
                                }}
                                className="!py-2 !px-4 !text-[10px] !bg-white !text-black hover:scale-105 transition-transform shrink-0"
                            >
                                Activar
                            </Button>
                        </div>
                        <div className="absolute -right-4 -bottom-4 opacity-10 group-hover:opacity-20 transition-opacity pointer-events-none text-cyan-400/50">
                            <TargetIcon size={80} />
                        </div>
                    </div>
                ))}
            </div>
            <Button
                onClick={() => navigateTo('program-editor')}
                variant="secondary"
                className="w-full !py-4 !text-xs !font-black !rounded-2xl border-dashed border-white/10 text-zinc-400 hover:text-cyan-400 hover:border-cyan-500/30 hover:bg-cyan-500/5 mt-4 bg-transparent transition-all duration-300"
            >
                <PlusIcon className="mr-2" size={16} /> CREAR OTRO PROGRAMA
            </Button>
            </div>
        </>
    );

    const renderNoPrograms = () => (
        <>
            <div className="w-full flex flex-col" style={{ backgroundColor: '#1a1a1a', paddingTop: 'max(1.25rem, env(safe-area-inset-top, 0px))' }}>
                <div className="w-full max-w-md mx-auto px-4 sm:px-6 pt-2 pb-5">
                    <p className="text-xs text-zinc-500 mb-3">{dateHeaderStr}</p>
                    <AugeTelemetryPanel variant="hero" shareable />
                </div>
            </div>
            <div className="relative z-10 flex flex-col px-6 pt-5 w-full max-w-md mx-auto pb-10">
                <HomeCardsSection
                    onNavigateToCard={(cardType) => {
                        if (onNavigateToCard) onNavigateToCard(cardType);
                        else if (['macros', 'evolution'].includes(cardType)) navigateTo('body-progress');
                        else if (cardType === 'calories-history') navigateTo('nutrition');
                        else navigateTo('program-editor');
                    }}
                />
                <div className="text-center w-full pt-2 mt-6">
                <div className="w-full flex justify-center mb-6 animate-fade-in">
                    <div className="relative">
                        <div className="absolute inset-0 bg-cyan-500/10 rounded-full blur-2xl scale-150" />
                        <CaupolicanIcon size={200} color="white" />
                    </div>
                </div>
                <h3 className="text-4xl sm:text-5xl font-black text-white uppercase tracking-tighter mb-4 drop-shadow-lg leading-[0.9]">
                    CREA TU PRIMER<br />
                    <span className="bg-gradient-to-r from-cyan-400 to-violet-400 bg-clip-text text-transparent">PROGRAMA DE</span>
                    <br />
                    ENTRENAMIENTO
                </h3>
                <p className="text-zinc-400 text-xs mb-8 font-medium leading-relaxed max-w-xs mx-auto">
                    No olvides presionar en <span className="text-cyan-400 font-bold">&quot;iniciar programa&quot;</span> para que aparezcan tus sesiones acá.
                </p>
                <Button
                    onClick={() => navigateTo('program-editor')}
                    className="w-full max-w-xs mx-auto !py-4 !text-base !font-black !rounded-2xl !bg-white !text-black border-none mb-10 hover:scale-[1.02] hover:shadow-xl hover:shadow-cyan-500/20 transition-all duration-300"
                >
                    <PlusIcon className="mr-2" /> CREAR PROGRAMA
                </Button>
                </div>
            </div>
        </>
    );

    return (
        <div className="relative min-h-full w-full flex flex-col bg-[#121212] tab-bar-safe-area">
            {/* Fondos sutiles Minimal NERD - orbs con menor opacidad */}
            <div className="fixed inset-0 pointer-events-none overflow-hidden -z-10">
                <div className="absolute -top-40 -right-20 w-80 h-80 rounded-full bg-cyan-500/[0.03] blur-3xl" />
                <div className="absolute top-1/3 -left-20 w-60 h-60 rounded-full bg-zinc-500/[0.02] blur-3xl" />
            </div>

            {activeProgram ? renderWithProgram() : programs.length > 0 ? renderSelectProgram() : renderNoPrograms()}

            {exerciseHistoryModal && (
                <ExerciseHistoryNerdView
                    exerciseName={exerciseHistoryModal}
                    history={history}
                    settings={settings}
                    onClose={() => setExerciseHistoryModal(null)}
                />
            )}

            {shareLog && (() => {
                const cardData = buildShareCardDataFromLog(shareLog, settings?.weightUnit ?? 'kg');
                return (
                <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/80">
                    <div className="bg-zinc-900 border border-white/10 rounded-xl p-4 w-full max-w-[260px] flex flex-col items-center">
                        <h3 className="text-xs font-black text-white mb-2 uppercase">Compartir</h3>
                        <div className="rounded border border-white/10 mb-2 overflow-hidden" style={{ width: 108, height: 192 }}>
                            <div className="w-[540px] h-[960px] origin-top-left" style={{ transform: 'scale(0.2)' }}>
                                <WorkoutShareCard {...cardData} preview />
                            </div>
                        </div>
                        <WorkoutShareCard {...cardData} />
                        <div className="flex gap-2 w-full mt-2">
                            <Button variant="secondary" className="flex-1 !py-2.5 !text-xs" onClick={() => setShareLog(null)}>
                                Cancelar
                            </Button>
                            <Button
                                className="flex-1 !py-2.5 !text-xs flex items-center justify-center gap-2"
                                onClick={async () => {
                                    setIsSharing(true);
                                    await shareElementAsImage('workout-summary-share-card', '¡Entrenamiento Terminado!', 'Registra tus entrenamientos con KPKN. #KPKN #Fitness');
                                    setIsSharing(false);
                                    setShareLog(null);
                                }}
                                disabled={isSharing}
                            >
                                {isSharing ? <span className="animate-pulse">Compartiendo...</span> : <><LinkIcon size={14} /> Compartir</>}
                            </Button>
                        </div>
                    </div>
                </div>
                );
            })()}
        </div>
    );
};

export default Home;
