// components/Home.tsx
import React, { useState, useMemo, useCallback } from 'react';
import { useAppState } from '../contexts/AppContext';
import { WorkoutLog, Program, Session } from '../types';
import { TodaySessionItem } from './home/SessionTodayCard';
import { HomeCardsSection } from './home/HomeCardsSection';
import { SessionTodayCard } from './home/SessionTodayCard';
import { AugeTelemetryPanel, RingsViewMode } from './home/AugeTelemetryPanel';
import { IntertwinedRingsIcon, SingleRingIcon, PlusIcon, BellIcon, SettingsIcon } from './icons';
import { CaupolicanIcon } from './CaupolicanIcon';
import Button from './ui/Button';

// ─── Inline ViewModel (since separate file was not found) ───────────────────
function useHomeViewModel(onNavigate: (view: any, data?: any) => void, onNavigateToCard: (view: any) => void) {
    const state = useAppState();
    const [isStartWorkoutModalOpen, setIsStartWorkoutModalOpen] = useState(false);

    const activeProgram = useMemo(() =>
        state.programs.find((p: Program) => p.id === state.activeProgramState?.programId) || null
        , [state.programs, state.activeProgramState]);

    // Sessions calculation
    const sessionsWithOngoing = useMemo(() => {
        if (!activeProgram || !state.activeProgramState) return [] as TodaySessionItem[];

        const { currentMacrocycleIndex, currentBlockIndex, currentMesocycleIndex, currentWeekId } = state.activeProgramState;
        const macro = activeProgram.macrocycles?.[currentMacrocycleIndex ?? 0];
        const block = macro?.blocks?.[currentBlockIndex ?? 0];
        const meso = block?.mesocycles?.[currentMesocycleIndex ?? 0];
        const week = meso?.weeks.find(w => w.id === currentWeekId);

        if (!week) return [] as TodaySessionItem[];

        const today = new Date().getDay(); // 0-6 (Sun-Sat)
        const dayMap = [7, 1, 2, 3, 4, 5, 6]; // Map JS day to 1-7 (Mon-Sun)
        const currentDay = dayMap[today];
        const todayStr = new Date().toISOString().split('T')[0];

        return week.sessions.map(session => {
            const isToday = session.dayOfWeek === currentDay;
            const ongoing = state.ongoingWorkout?.session?.id === session.id;
            const logForToday = state.history.find(log =>
                log.sessionId === session.id &&
                log.date.startsWith(todayStr)
            );

            return {
                session: session,
                program: activeProgram,
                location: {
                    macroIndex: currentMacrocycleIndex ?? 0,
                    mesoIndex: currentMesocycleIndex ?? 0,
                    weekId: currentWeekId ?? ''
                },
                isCompleted: !!logForToday,
                dayOfWeek: session.dayOfWeek || 1,
                log: logForToday,
                isOngoing: ongoing
            };
        }).sort((a, b) => {
            if (a.isOngoing) return -1;
            if (b.isOngoing) return 1;

            const aIsToday = a.session.dayOfWeek === currentDay;
            const bIsToday = b.session.dayOfWeek === currentDay;
            if (aIsToday && !bIsToday) return -1;
            if (!aIsToday && bIsToday) return 1;

            return (a.session.dayOfWeek ?? 0) - (b.session.dayOfWeek ?? 0);
        });
    }, [activeProgram, state.activeProgramState, state.ongoingWorkout, state.history]);

    const handleStartWorkout = useCallback((session: Session, program: Program, _?: any, ctx?: any) => {
        onNavigate('log-workout', { programId: program.id, sessionId: session.id, ...ctx });
    }, [onNavigate]);

    const handleCardNav = useCallback((cardType: string) => {
        onNavigateToCard(cardType);
    }, [onNavigateToCard]);

    return {
        sessionsWithOngoing,
        ongoingWorkout: state.ongoingWorkout,
        programs: state.programs,
        activeProgram,
        handleStartWorkout,
        setIsStartWorkoutModalOpen,
        handleCardNav,
        navigateTo: onNavigate,
        isAppLoading: state.isAppLoading
    };
}

interface HomeProps {
    onNavigate: (view: any, props?: any) => void;
    onResumeWorkout: (workout: any) => void;
    onNavigateToCard: (view: any) => void;
    onEditSleepLog?: (log: any) => void; // Fixed missing prop from App.tsx
}

const Home: React.FC<HomeProps> = ({ onNavigate, onResumeWorkout, onNavigateToCard }) => {
    const { settings, activeProgramState, programs: allPrograms } = useAppState();
    const vm = useHomeViewModel(onNavigate, onNavigateToCard);

    const [ringsView, setRingsView] = useState<RingsViewMode>('rings');
    const [shareLog, setShareLog] = useState<WorkoutLog | null>(null);

    const activeProgram = useMemo(() =>
        allPrograms.find((p: Program) => p.id === activeProgramState?.programId) || null
        , [allPrograms, activeProgramState]);

    const greeting = (() => {
        const h = new Date().getHours();
        if (h < 12) return '¡Buenos días';
        if (h < 19) return '¡Buenas tardes';
        return '¡Buenas noches';
    })();
    const userName = settings?.username || 'Usuario';

    // ─── Renderers ──────────────────────────────────────────────────────────

    const renderWithProgram = () => {
        const primaryProgram = (vm.sessionsWithOngoing.length > 0 && vm.ongoingWorkout && vm.sessionsWithOngoing.length === 1)
            ? vm.programs.find((p: Program) => p.id === vm.ongoingWorkout!.programId) || activeProgram!
            : activeProgram!;

        return (
            <div className="flex flex-col w-full bg-transparent overflow-x-hidden relative">
                {/* ═══ Header ═══ */}
                <div className="w-full py-4 px-6 flex flex-col gap-6">
                    <div className="flex justify-between items-center w-full">
                        <div className="w-12 h-12 rounded-full border border-black/[0.05] bg-white flex items-center justify-center shadow-sm overflow-hidden">
                            {settings?.profilePicture ? (
                                <img src={settings.profilePicture} className="w-full h-full object-cover" alt="Profile" />
                            ) : (
                                <CaupolicanIcon size={24} className="text-black/20" />
                            )}
                        </div>
                        <div className="flex gap-2">
                            <button className="w-12 h-12 rounded-full hover:bg-black/5 flex items-center justify-center text-[#49454F] transition-colors">
                                <BellIcon size={24} />
                            </button>
                            <button onClick={() => onNavigate('settings')} className="w-12 h-12 rounded-full hover:bg-black/5 flex items-center justify-center text-[#49454F] transition-colors">
                                <SettingsIcon size={24} />
                            </button>
                        </div>
                    </div>
                    <div className="text-[#1C1B1F] text-[32px] font-black font-['Roboto'] leading-tight tracking-tighter">
                        {greeting},<br />{userName}!
                    </div>
                </div>

                {/* ─── CONTENT ─── */}
                <div className="relative z-10 w-full min-h-[300px] flex flex-col overflow-visible">
                    <div className="px-6 flex justify-between items-center h-14 z-10">
                        <div className="text-[#1D1B20] text-xl font-black font-['Roboto'] leading-7 uppercase tracking-[0.05em]">Tus RINGS</div>
                        <div className="flex bg-[#ECE6F0] p-1 rounded-full items-center gap-1 shadow-sm">
                            <button onClick={() => setRingsView('rings')} className={`w-10 h-10 flex items-center justify-center rounded-full transition-all ${ringsView === 'rings' ? 'bg-white shadow-sm text-primary' : 'text-[#49454F]'}`}>
                                <IntertwinedRingsIcon size={22} strokeWidth={2.5} />
                            </button>
                            <button onClick={() => setRingsView('individual')} className={`w-10 h-10 flex items-center justify-center rounded-full transition-all ${ringsView === 'individual' ? 'bg-white shadow-sm text-primary' : 'text-[#49454F]'}`}>
                                <SingleRingIcon size={22} strokeWidth={2.5} />
                            </button>
                        </div>
                    </div>
                    <AugeTelemetryPanel variant="hero" shareable viewMode={ringsView} />
                </div>

                {/* ═══ Sesión de hoy ═══ */}
                <div className="w-full pt-2 overflow-visible">
                    <div className="px-6 mb-4 flex justify-between items-center">
                        <div className="text-[#1D1B20] text-[22px] font-black font-['Roboto'] leading-[28px] uppercase tracking-tighter">Sesión de hoy</div>
                    </div>
                    <div className="w-full overflow-visible">
                        <SessionTodayCard
                            programName={activeProgram?.name ?? 'Entrenamiento'}
                            programId={activeProgram?.id ?? ''}
                            sessions={vm.sessionsWithOngoing}
                            ongoingWorkout={vm.ongoingWorkout ? { session: vm.ongoingWorkout.session, programId: vm.ongoingWorkout.programId, isPaused: vm.ongoingWorkout.isPaused } : null}
                            onStartWorkout={vm.handleStartWorkout as any}
                            onResumeWorkout={() => onResumeWorkout(vm.ongoingWorkout)}
                            onOpenStartWorkoutModal={() => onNavigate('program-detail', { programId: activeProgram?.id })}
                            currentDayOfWeek={[7, 1, 2, 3, 4, 5, 6][new Date().getDay()]}
                        />
                    </div>
                </div>

                {/* ═══ Home Cards Carousel ═══ */}
                <div className="w-full mt-6">
                    <HomeCardsSection onNavigateToCard={vm.handleCardNav} />
                </div>

                {/* ═══ Tus Programas ═══ */}
                <div className="w-full overflow-visible mt-6">
                    <div className="px-6 h-12 flex justify-between items-center mb-4">
                        <div className="text-[#1D1B20] text-[22px] font-black font-['Roboto'] leading-[28px] uppercase tracking-tighter">Tus Programas</div>
                    </div>
                    <div className="pl-6 overflow-x-auto no-scrollbar flex gap-4 pb-4 pr-6">
                        {vm.programs.map((prog: Program) => (
                            <button key={prog.id} onClick={() => onNavigate('program-detail', { programId: prog.id })} className="flex flex-col gap-3 flex-shrink-0 group">
                                <div className="w-44 h-28 rounded-[32px] bg-white border border-black/[0.03] shadow-sm flex items-center justify-center overflow-hidden group-active:scale-[0.96] transition-all relative">
                                    {prog.coverImage ? (
                                        <img src={prog.coverImage} className="w-full h-full object-cover opacity-80 group-hover:opacity-100 transition-opacity" alt={prog.name} />
                                    ) : (
                                        <CaupolicanIcon size={40} className="text-black/5" />
                                    )}
                                    <div className="absolute inset-0 bg-black/5 group-hover:bg-transparent transition-colors" />
                                </div>
                                <div className="text-[11px] font-black text-black uppercase tracking-tight text-left truncate w-44 px-2">{prog.name}</div>
                            </button>
                        ))}
                    </div>
                </div>

                {/* ═══ Rincones ═══ */}
                <div className="px-6 pb-16 mt-6 flex flex-col gap-5 overflow-visible">
                    <div className="text-[#1D1B20] text-[22px] font-black font-['Roboto'] leading-[28px] uppercase tracking-tighter">Rincones</div>
                    <div className="flex flex-col gap-4">
                        <button onClick={() => onNavigate('powerlifter-corner' as any)} className="bg-white/50 backdrop-blur-xl p-5 rounded-[36px] border border-black/[0.02] flex items-center gap-6 active:scale-[0.98] transition-all shadow-sm">
                            <div className="w-16 h-16 rounded-[24px] bg-[#ECE6F0] flex items-center justify-center flex-shrink-0">
                                <CaupolicanIcon size={32} className="text-[#49454F]/50" />
                            </div>
                            <div className="flex-1 flex flex-col items-start gap-0.5">
                                <span className="text-lg font-black text-[#1D1B20] leading-tight">Powerlifter Corner</span>
                                <span className="text-xs font-medium text-[#49454F] opacity-60 text-left leading-snug">Federaciones, historial y competiciones.</span>
                            </div>
                        </button>
                        <button onClick={() => onNavigate('wiki-home' as any)} className="bg-white/50 backdrop-blur-xl p-5 rounded-[36px] border border-black/[0.02] flex items-center gap-6 active:scale-[0.98] transition-all shadow-sm">
                            <div className="w-16 h-16 rounded-[24px] bg-[#ECE6F0] flex items-center justify-center flex-shrink-0">
                                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="text-[#49454F]/50"><path d="M12 6.042A8.967 8.967 0 0 0 6 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 0 1 6 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 0 1 6-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0 0 18 18a8.967 8.967 0 0 0-6 2.292m0-14.25v14.25" /></svg>
                            </div>
                            <div className="flex-1 flex flex-col items-start gap-0.5">
                                <span className="text-lg font-black text-[#1D1B20] leading-tight">WikiLab</span>
                                <span className="text-xs font-medium text-[#49454F] opacity-60 text-left leading-snug">Ciencia del entrenamiento y biomecánica.</span>
                            </div>
                        </button>
                    </div>
                </div>
            </div>
        );
    };

    const renderEmpty = () => (
        <div className="flex-1 flex flex-col bg-transparent overflow-visible pt-10">
            <div className="px-6 mb-8 text-[#1C1B1F] text-[32px] font-black font-['Roboto'] leading-tight tracking-tighter">
                Inicia tu próximo<br /><span className="text-primary">Plan Maestro</span>
            </div>
            {/* Minimal Auge Panel */}
            <div className="w-full overflow-visible">
                <AugeTelemetryPanel variant="hero" shareable />
            </div>

            <HomeCardsSection onNavigateToCard={vm.handleCardNav} />

            <div className="px-6 mt-16 pb-24 flex flex-col items-center text-center gap-8">
                <div className="relative">
                    <CaupolicanIcon size={120} className="text-black/[0.03]" />
                    <div className="absolute inset-0 flex items-center justify-center">
                        <PlusIcon size={40} className="text-primary/10" />
                    </div>
                </div>
                <div className="max-w-[280px] space-y-3">
                    <p className="text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F] opacity-40">Arsenal Vacío</p>
                    <p className="text-base font-medium text-[#49454F] opacity-70 leading-relaxed">Configura tu biometría avanzada creando tu primer programa de entrenamiento.</p>
                </div>
                <Button onClick={() => onNavigate('program-editor')} className="w-full !py-5 !rounded-3xl shadow-2xl shadow-primary/20 !bg-primary !text-white !font-black !tracking-widest">
                    CREAR PROGRAMA
                </Button>
            </div>
        </div>
    );

    return (
        <div className="w-full min-h-screen pb-20 overflow-x-hidden relative">
            {activeProgram ? renderWithProgram() : renderEmpty()}
        </div>
    );
};

export default Home;
