// components/Home.tsx
// ══════════════════════════════════════════════════════════════════════════════
// Dashboard de Entrenamiento — Pantalla Home
//
// Arquitectura M3 (traducida de Compose a React):
//   Scaffold       → Wrapper <div> con safe-area
//   TopAppBar      → Hero contextual (AugeTelemetryPanel)
//   NavigationBar  → TabBar (gestionada en App.tsx)
//   ElevatedCard   → Componente <ElevatedCard> con sombra dinámica
//   LazyColumn     → Scroll nativo con overflow-y-auto
//
// Reglas:
//   • Todos los colores usan tokens M3 (--md-sys-color-*), nunca hex fijos
//   • Espaciados siguen rejilla 8dp (8px, 16px, 24px, 32px, 40px, 48px)
//   • Cada componente tiene un comentario explicando la decisión UX
// ══════════════════════════════════════════════════════════════════════════════

import React, { useMemo, useState } from 'react';
import type { Program, Session, View, WorkoutLog, SleepLog, ProgramWeek } from '../types';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { AugeTelemetryPanel } from './home/AugeTelemetryPanel';
import { SessionTodayCard } from './home/SessionTodayCard';
import { HomeCardsSection } from './home/HomeCardsSection';
import { CaupolicanIcon } from './CaupolicanIcon';
import { TargetIcon, PlusIcon, LinkIcon } from './icons';
import Button from './ui/Button';
import { ExerciseHistoryNerdView } from './ExerciseHistoryNerdView';
import { getLocalDateString } from '../utils/dateUtils';
import { WorkoutShareCard, buildShareCardDataFromLog } from './FinishWorkoutModal';
import { shareElementAsImage } from '../services/shareService';

// ─── Tipos ────────────────────────────────────────────────────────────────────

interface HomeProps {
    onNavigate: (view: View, program?: Program) => void;
    onResumeWorkout: () => void;
    onEditSleepLog: (log: SleepLog) => void;
    onNavigateToCard?: (cardType: string) => void;
}

// ─── ElevatedCard (equivalente a ElevatedCard de M3) ──────────────────────────
// UX: Las tarjetas elevadas crean jerarquía visual. El usuario distingue
// elementos interactivos (cards) del fondo (surface). La sombra tenue
// refuerza la profundidad sin ser agresiva (M3 elevation level 1).

const ElevatedCard: React.FC<{
    children: React.ReactNode;
    className?: string;
    onClick?: () => void;
}> = ({ children, className = '', onClick }) => (
    <div
        className={`
            bg-[var(--md-sys-color-surface-container)]
            border border-[var(--md-sys-color-outline-variant)]
            rounded-xl
            shadow-[0_1px_3px_rgba(0,0,0,0.3),0_4px_8px_rgba(0,0,0,0.2)]
            transition-all duration-200
            ${onClick ? 'cursor-pointer hover:shadow-[0_2px_6px_rgba(0,0,0,0.4),0_8px_16px_rgba(0,0,0,0.25)] active:scale-[0.98]' : ''}
            ${className}
        `}
        onClick={onClick}
    >
        {/* Glow sutil en el borde superior para heredar luz ambiental */}
        <div className="absolute inset-0 rounded-xl pointer-events-none border-t border-white/10 opacity-40" />
        {children}
    </div>
);

// ─── Componente Principal ─────────────────────────────────────────────────────

// ─── useHomeViewModel (State Hoisting) ────────────────────────────────────────
// UX: Separamos la lógica de negocio de la UI para facilitar el testing
// y seguir patrones de arquitectura modernos (MVVM).

function useHomeViewModel(onNavigate: HomeProps['onNavigate'], onNavigateToCard: HomeProps['onNavigateToCard']) {
    const { programs, history, activeProgramState, settings, ongoingWorkout } = useAppState();
    const { handleStartWorkout, navigateTo, setIsStartWorkoutModalOpen, handleStartProgram } = useAppDispatch();

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
                if (week) { activeWeek = week; break; }
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

    const handleCardNav = (cardType: string) => {
        if (onNavigateToCard) onNavigateToCard(cardType);
        else if (['macros', 'evolution'].includes(cardType)) navigateTo('body-progress');
        else if (cardType === 'calories-history') navigateTo('nutrition');
        else if (activeProgram) navigateTo('program-detail', { programId: activeProgram.id });
        else if (programs[0]) navigateTo('program-detail', { programId: programs[0].id });
        else navigateTo('program-editor');
    };

    return {
        programs,
        history,
        settings,
        activeProgram,
        sessionsWithOngoing,
        ongoingWorkout,
        handleStartWorkout,
        handleStartProgram,
        navigateTo,
        setIsStartWorkoutModalOpen,
        handleCardNav
    };
}

const Home: React.FC<HomeProps> = ({ onNavigate, onResumeWorkout, onNavigateToCard }) => {
    const vm = useHomeViewModel(onNavigate, onNavigateToCard);

    // Estados internos de UI (no negocio)
    const [exerciseHistoryModal, setExerciseHistoryModal] = useState<string | null>(null);
    const [shareLog, setShareLog] = useState<WorkoutLog | null>(null);
    const [isSharing, setIsSharing] = useState(false);

    const dateHeaderStr = useMemo(() => new Date().toLocaleDateString('es-ES', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
    }), []);

    const handleShareLog = (log: WorkoutLog) => setShareLog(log);

    // ═══════════════════════════════════════════════════════════════════════════
    // renderWithProgram 
    // ═══════════════════════════════════════════════════════════════════════════
    const renderWithProgram = () => (
        <>
            <div
                className="w-full flex flex-col"
                style={{
                    backgroundColor: 'var(--md-sys-color-surface-container)',
                    paddingTop: 'max(1.25rem, env(safe-area-inset-top, 0px))',
                }}
            >
                <div className="w-full max-w-md mx-auto px-4 sm:px-6 pt-2 pb-5">
                    <p
                        className="text-label-sm mb-3 opacity-70"
                        style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                    >
                        {dateHeaderStr}
                    </p>
                    <AugeTelemetryPanel variant="hero" shareable />
                </div>
            </div>

            <div
                className="relative z-10 flex flex-col w-full max-w-md mx-auto pb-10"
                style={{ backgroundColor: 'var(--md-sys-color-background)' }}
            >
                <div className="flex flex-col px-6 pt-6 space-y-8">

                    <div className="w-full">
                        {(() => {
                            const primaryProgram = (vm.sessionsWithOngoing.length > 0 && vm.ongoingWorkout && vm.sessionsWithOngoing.length === 1)
                                ? vm.programs.find(p => p.id === vm.ongoingWorkout!.programId) || vm.activeProgram!
                                : vm.activeProgram!;
                            return (
                                <SessionTodayCard
                                    key="session"
                                    variant="continuation"
                                    programName={primaryProgram?.name ?? 'Entrenamiento'}
                                    programId={primaryProgram?.id ?? ''}
                                    todaySessions={vm.sessionsWithOngoing}
                                    ongoingWorkout={vm.ongoingWorkout ? { session: vm.ongoingWorkout.session, programId: vm.ongoingWorkout.programId, isPaused: vm.ongoingWorkout.isPaused } : null}
                                    onStartWorkout={vm.handleStartWorkout}
                                    onResumeWorkout={onResumeWorkout}
                                    onEditSession={(programId, macroIndex, mesoIndex, weekId, sessionId) =>
                                        vm.navigateTo('session-editor', { programId, macroIndex, mesoIndex, weekId, sessionId })
                                    }
                                    onViewProgram={(programId) => vm.navigateTo('program-detail', { programId })}
                                    onOpenStartWorkoutModal={() => vm.setIsStartWorkoutModalOpen(true)}
                                    onShareLog={handleShareLog}
                                />
                            );
                        })()}
                    </div>

                    <HomeCardsSection onNavigateToCard={vm.handleCardNav} />
                </div>
            </div>
        </>
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // renderSelectProgram
    // ═══════════════════════════════════════════════════════════════════════════
    const renderSelectProgram = () => (
        <>
            <div
                className="w-full flex flex-col"
                style={{
                    backgroundColor: 'var(--md-sys-color-surface-container)',
                    paddingTop: 'max(1.25rem, env(safe-area-inset-top, 0px))',
                }}
            >
                <div className="w-full max-w-md mx-auto px-4 sm:px-6 pt-2 pb-5">
                    <p className="text-label-sm mb-3 opacity-70" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                        {dateHeaderStr}
                    </p>
                    <AugeTelemetryPanel variant="hero" shareable />
                </div>
            </div>

            <div
                className="relative z-10 flex flex-col px-6 pt-8 w-full max-w-md mx-auto pb-10 space-y-8"
                style={{ backgroundColor: 'var(--md-sys-color-background)' }}
            >
                <div className="text-center mb-2">
                    <h2
                        className="text-headline-sm uppercase tracking-tight font-black"
                        style={{ color: 'var(--md-sys-color-on-background)' }}
                    >
                        Selecciona un Programa
                    </h2>
                    <p
                        className="text-body-sm mt-2 opacity-60"
                        style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                    >
                        Activa uno para comenzar tu evolución.
                    </p>
                </div>

                <HomeCardsSection onNavigateToCard={vm.handleCardNav} />

                <div className="space-y-4">
                    {vm.programs.map(prog => (
                        <ElevatedCard key={prog.id} className="p-5 flex items-center justify-between group overflow-hidden">
                            <div className="relative z-10 flex flex-col gap-1">
                                <h3
                                    className="text-title-md uppercase tracking-tight font-black truncate max-w-[180px]"
                                    style={{ color: 'var(--md-sys-color-on-surface)' }}
                                >
                                    {prog.name}
                                </h3>
                                <p
                                    className="text-label-sm uppercase tracking-widest opacity-50"
                                    style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                                >
                                    {prog.macrocycles?.length || 0} Fases • {prog.mode || 'Estándar'}
                                </p>
                            </div>
                            <Button
                                onClick={() => {
                                    vm.handleStartProgram(prog.id);
                                    vm.navigateTo('program-detail', { programId: prog.id });
                                }}
                                className="!py-3 !px-6 !text-label-sm font-black transition-transform min-h-[48px]"
                                style={{
                                    backgroundColor: 'var(--md-sys-color-primary)',
                                    color: 'var(--md-sys-color-on-primary)',
                                    borderRadius: '12px'
                                }}
                            >
                                ACTIVAR
                            </Button>
                            <div
                                className="absolute -right-6 -bottom-6 opacity-[0.05] group-hover:opacity-[0.1] transition-opacity pointer-events-none"
                                style={{ color: 'var(--md-sys-color-primary)' }}
                            >
                                <TargetIcon size={100} />
                            </div>
                        </ElevatedCard>
                    ))}
                </div>

                <Button
                    onClick={() => vm.navigateTo('program-editor')}
                    variant="secondary"
                    className="w-full !py-5 !text-label-lg font-black !rounded-xl border-dashed opacity-80 hover:opacity-100 transition-all min-h-[48px]"
                    style={{
                        borderColor: 'var(--md-sys-color-outline-variant)',
                        color: 'var(--md-sys-color-on-surface-variant)',
                        backgroundColor: 'transparent',
                    }}
                >
                    <PlusIcon className="mr-2" size={18} /> CREAR OTRO PROGRAMA
                </Button>
            </div>
        </>
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // renderNoPrograms
    // ═══════════════════════════════════════════════════════════════════════════
    const renderNoPrograms = () => (
        <>
            <div
                className="w-full flex flex-col"
                style={{
                    backgroundColor: 'var(--md-sys-color-surface-container)',
                    paddingTop: 'max(1.25rem, env(safe-area-inset-top, 0px))',
                }}
            >
                <div className="w-full max-w-md mx-auto px-4 sm:px-6 pt-2 pb-5">
                    <p className="text-label-sm mb-3 opacity-70" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                        {dateHeaderStr}
                    </p>
                    <AugeTelemetryPanel variant="hero" shareable />
                </div>
            </div>

            <div
                className="relative z-10 flex flex-col px-6 pt-5 w-full max-w-md mx-auto pb-10"
                style={{ backgroundColor: 'var(--md-sys-color-background)' }}
            >
                <HomeCardsSection onNavigateToCard={vm.handleCardNav} />

                <div className="text-center w-full pt-8">
                    <div className="w-full flex justify-center mb-10 animate-fade-in">
                        <div className="relative">
                            <div
                                className="absolute inset-0 rounded-full blur-3xl scale-150 opacity-[0.15]"
                                style={{ backgroundColor: 'var(--md-sys-color-primary)' }}
                            />
                            <CaupolicanIcon size={200} color="currentColor" />
                        </div>
                    </div>

                    <h3
                        className="text-display-sm sm:text-display-md font-black uppercase tracking-tighter mb-6 leading-[0.85]"
                        style={{ color: 'var(--md-sys-color-on-background)' }}
                    >
                        DISEÑA TU<br />
                        <span style={{ color: 'var(--md-sys-color-primary)' }}>PRIMER PLAN</span>
                        <br />
                        MAESTRO
                    </h3>

                    <p
                        className="text-body-sm mb-10 leading-relaxed max-w-xs mx-auto opacity-70"
                        style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                    >
                        Activa la ingeniería de tu cuerpo. Inicia un programa para desbloquear la telemetría avanzada.
                    </p>

                    <Button
                        onClick={() => vm.navigateTo('program-editor')}
                        className="w-full max-w-xs mx-auto !py-5 !text-title-sm font-black !rounded-2xl border-none mb-10 hover:scale-[1.05] shadow-lg shadow-primary/20 transition-all min-h-[56px]"
                        style={{
                            backgroundColor: 'var(--md-sys-color-primary)',
                            color: 'var(--md-sys-color-on-primary)',
                        }}
                    >
                        <PlusIcon className="mr-2" size={20} /> CREAR PROGRAMA
                    </Button>
                </div>
            </div>
        </>
    );

    return (
        <div
            className="relative min-h-full w-full flex flex-col tab-bar-safe-area"
            style={{ backgroundColor: 'var(--md-sys-color-background)' }}
        >
            <div className="fixed inset-0 pointer-events-none overflow-hidden -z-10">
                <div
                    className="absolute -top-40 -right-20 w-80 h-80 rounded-full blur-[100px] opacity-[0.04]"
                    style={{ backgroundColor: 'var(--md-sys-color-primary)' }}
                />
                <div
                    className="absolute top-1/3 -left-20 w-60 h-60 rounded-full blur-[80px] opacity-[0.03]"
                    style={{ backgroundColor: 'var(--md-sys-color-tertiary)' }}
                />
            </div>

            {vm.activeProgram ? renderWithProgram() : vm.programs.length > 0 ? renderSelectProgram() : renderNoPrograms()}

            {exerciseHistoryModal && (
                <ExerciseHistoryNerdView
                    exerciseName={exerciseHistoryModal}
                    history={vm.history}
                    settings={vm.settings}
                    onClose={() => setExerciseHistoryModal(null)}
                />
            )}

            {shareLog && (() => {
                const cardData = buildShareCardDataFromLog(shareLog, vm.settings?.weightUnit ?? 'kg');
                return (
                    <div className="fixed inset-0 z-[100] flex items-center justify-center p-6 bg-black/90 backdrop-blur-sm">
                        <ElevatedCard className="p-6 w-full max-w-[280px] flex flex-col items-center gap-4 animate-scale-up">
                            <h3
                                className="text-title-small uppercase font-black tracking-widest"
                                style={{ color: 'var(--md-sys-color-on-surface)' }}
                            >
                                Compartir Log
                            </h3>
                            <div
                                className="rounded-xl overflow-hidden shadow-2xl"
                                style={{
                                    width: 140,
                                    height: 248,
                                    borderColor: 'var(--md-sys-color-outline-variant)',
                                    borderWidth: 1,
                                }}
                            >
                                <div className="w-[540px] h-[960px] origin-top-left" style={{ transform: 'scale(0.259)' }}>
                                    <WorkoutShareCard {...cardData} preview />
                                </div>
                            </div>
                            <div className="flex flex-col gap-2 w-full">
                                <Button
                                    className="w-full !py-3 bg-primary text-on-primary font-black uppercase text-xs rounded-xl min-h-[48px]"
                                    style={{
                                        backgroundColor: 'var(--md-sys-color-primary)',
                                        color: 'var(--md-sys-color-on-primary)',
                                    }}
                                    onClick={async () => {
                                        setIsSharing(true);
                                        await shareElementAsImage('workout-summary-share-card', '¡Entrenamiento Terminado!', 'Registra tus entrenamientos con KPKN. #KPKN #Fitness');
                                        setIsSharing(false);
                                        setShareLog(null);
                                    }}
                                    disabled={isSharing}
                                >
                                    {isSharing ? 'Generando...' : 'COMPARTIR'}
                                </Button>
                                <Button
                                    variant="secondary"
                                    className="w-full !py-3 font-black uppercase text-xs rounded-xl min-h-[48px]"
                                    onClick={() => setShareLog(null)}
                                >
                                    CANCELAR
                                </Button>
                            </div>
                        </ElevatedCard>
                    </div>
                );
            })()}
        </div>
    );
};


export default Home;
