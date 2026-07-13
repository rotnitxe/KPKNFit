// components/Home.tsx
import React, { useState, useMemo, useCallback } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { WorkoutLog, Program, Session } from '../types';
import { TodaySessionItem } from './home/SessionTodayCard';
import { HomeCardsSection } from './home/HomeCardsSection';
import { SessionTodayCard } from './home/SessionTodayCard';
import { AugeTelemetryPanel, RingsViewMode } from './home/AugeTelemetryPanel';
import { IntertwinedRingsIcon, SingleRingIcon, PlusIcon, BellIcon, SettingsIcon, SunIcon, MoonIcon } from './icons';
import { CaupolicanIcon } from './CaupolicanIcon';
import Button from './ui/Button';

const isDarkTheme = (theme: string | undefined) => theme === 'dark' || theme === 'deep-black';

// ─── Inline ViewModel (since separate file was not found) ───────────────────
function useHomeViewModel(onNavigate: (view: any, data?: any) => void, onNavigateToCard: (view: any) => void) {
    const state = useAppState();
    const { handleStartWorkout: startWorkout } = useAppDispatch();
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

    const handleStartWorkout = useCallback((session: Session, program: Program, weekVariant?: 'A' | 'B' | 'C' | 'D', ctx?: any) => {
        startWorkout(session, program, weekVariant, ctx);
    }, [startWorkout]);

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
    const { setSettings } = useAppDispatch();
    const vm = useHomeViewModel(onNavigate, onNavigateToCard);

    const isDark = isDarkTheme(settings?.appTheme);

    const toggleTheme = useCallback(() => {
        setSettings({ appTheme: isDark ? 'default' : 'dark' });
    }, [isDark, setSettings]);

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
                        <div className={`w-12 h-12 rounded-full border ${isDark ? 'border-white/10 bg-white/5' : 'border-black/[0.05] bg-white'} flex items-center justify-center shadow-sm overflow-hidden`}>
                            {settings?.profilePicture ? (
                                <img src={settings.profilePicture} className="w-full h-full object-cover" alt="Profile" />
                            ) : (
                                <CaupolicanIcon size={24} className={isDark ? "text-white/20" : "text-black/20"} />
                            )}
                        </div>
                        <div className="flex gap-2">
                            <button onClick={toggleTheme} className="w-12 h-12 rounded-full hover:bg-black/5 dark:hover:bg-white/10 flex items-center justify-center text-[#49454F] dark:text-white/70 transition-colors">
                                {isDark ? <SunIcon size={22} /> : <MoonIcon size={22} />}
                            </button>
                            <button className="w-12 h-12 rounded-full hover:bg-black/5 dark:hover:bg-white/10 flex items-center justify-center text-[#49454F] dark:text-white/70 transition-colors">
                                <BellIcon size={24} />
                            </button>
                            <button onClick={() => onNavigate('settings')} className="w-12 h-12 rounded-full hover:bg-black/5 dark:hover:bg-white/10 flex items-center justify-center text-[#49454F] dark:text-white/70 transition-colors">
                                <SettingsIcon size={24} />
                            </button>
                        </div>
                    </div>
                    <div className="text-[#1C1B1F] dark:text-white text-[32px] font-black font-['Roboto'] leading-tight tracking-tighter">
                        {greeting},<br />{userName}!
                    </div>
                </div>

                {/* ─── CONTENT ─── */}
                <div className="relative z-10 w-full min-h-[300px] flex flex-col overflow-visible">
                    <div className="px-6 flex justify-between items-center h-14 z-10">
                        <div className="text-[#1D1B20] dark:text-white text-xl font-black font-['Roboto'] leading-7 uppercase tracking-[0.05em]">Tus RINGS</div>
                        <div className={`flex p-1 rounded-full items-center gap-1 shadow-sm ${isDark ? 'bg-white/10' : 'bg-[#ECE6F0]'}`}>
                            <button onClick={() => setRingsView('rings')} className={`w-10 h-10 flex items-center justify-center rounded-full transition-all ${ringsView === 'rings' ? `${isDark ? 'bg-white/20' : 'bg-white'} shadow-sm text-primary` : `${isDark ? 'text-white/60' : 'text-[#49454F]'}`}`}>
                                <IntertwinedRingsIcon size={22} strokeWidth={2.5} />
                            </button>
                            <button onClick={() => setRingsView('individual')} className={`w-10 h-10 flex items-center justify-center rounded-full transition-all ${ringsView === 'individual' ? `${isDark ? 'bg-white/20' : 'bg-white'} shadow-sm text-primary` : `${isDark ? 'text-white/60' : 'text-[#49454F]'}`}`}>
                                <SingleRingIcon size={22} strokeWidth={2.5} />
                            </button>
                        </div>
                    </div>
                    <AugeTelemetryPanel variant="hero" shareable viewMode={ringsView} />
                </div>

                {/* ═══ Sesión de hoy ═══ */}
                <div className="w-full pt-2 overflow-visible">
                    <div className="px-6 mb-4 flex justify-between items-center">
                        <div className="text-[#1D1B20] dark:text-white text-[22px] font-black font-['Roboto'] leading-[28px] uppercase tracking-tighter">Sesión de hoy</div>
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
                        <div className="text-[#1D1B20] dark:text-white text-[22px] font-black font-['Roboto'] leading-[28px] uppercase tracking-tighter">Tus Programas</div>
                    </div>
                    <div className="pl-6 overflow-x-auto no-scrollbar flex gap-4 pb-4 pr-6">
                        {vm.programs.map((prog: Program) => (
                            <button key={prog.id} onClick={() => onNavigate('program-detail', { programId: prog.id })} className="flex flex-col gap-3 flex-shrink-0 group">
                                <div className={`w-44 h-28 rounded-[32px] ${isDark ? 'bg-white/5 border border-white/10' : 'bg-white border border-black/[0.03]'} shadow-sm flex items-center justify-center overflow-hidden group-active:scale-[0.96] transition-all relative`}>
                                    {prog.coverImage ? (
                                        <img src={prog.coverImage} className="w-full h-full object-cover opacity-80 group-hover:opacity-100 transition-opacity" alt={prog.name} />
                                    ) : (
                                        <CaupolicanIcon size={40} className={isDark ? "text-white/10" : "text-black/5"} />
                                    )}
                                    <div className={`absolute inset-0 ${isDark ? 'bg-black/30 group-hover:bg-transparent' : 'bg-black/5 group-hover:bg-transparent'} transition-colors`} />
                                </div>
                                <div className={`text-[11px] font-black uppercase tracking-tight text-left truncate w-44 px-2 ${isDark ? 'text-white/80' : 'text-black'}`}>{prog.name}</div>
                            </button>
                        ))}
                    </div>
                </div>

                {/* ═══ Rincones ═══ */}
                <div className="px-6 pb-16 mt-6 flex flex-col gap-5 overflow-visible">
                    <div className="text-[#1D1B20] dark:text-white text-[22px] font-black font-['Roboto'] leading-[28px] uppercase tracking-tighter">Rincones</div>
                    <div className="flex flex-col gap-4">
                        <button onClick={() => onNavigate('powerlifter-corner' as any)} className={`${isDark ? 'bg-white/5 backdrop-blur-xl border border-white/10' : 'bg-white/50 backdrop-blur-xl border border-black/[0.02]'} p-5 rounded-[36px] flex items-center gap-6 active:scale-[0.98] transition-all shadow-sm`}>
                            <div className={`w-16 h-16 rounded-[24px] ${isDark ? 'bg-white/10' : 'bg-[#ECE6F0]'} flex items-center justify-center flex-shrink-0`}>
                                <CaupolicanIcon size={32} className={isDark ? "text-white/30" : "text-[#49454F]/50"} />
                            </div>
                            <div className="flex-1 flex flex-col items-start gap-0.5">
                                <span className={`text-lg font-black leading-tight ${isDark ? 'text-white' : 'text-[#1D1B20]'}`}>Powerlifter Corner</span>
                                <span className={`text-xs font-medium text-left leading-snug ${isDark ? 'text-white/40' : 'text-[#49454F] opacity-60'}`}>Federaciones, historial y competiciones.</span>
                            </div>
                        </button>
                        <button onClick={() => onNavigate('wiki-home' as any)} className={`${isDark ? 'bg-white/5 backdrop-blur-xl border border-white/10' : 'bg-white/50 backdrop-blur-xl border border-black/[0.02]'} p-5 rounded-[36px] flex items-center gap-6 active:scale-[0.98] transition-all shadow-sm`}>
                            <div className={`w-16 h-16 rounded-[24px] ${isDark ? 'bg-white/10' : 'bg-[#ECE6F0]'} flex items-center justify-center flex-shrink-0`}>
                                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className={isDark ? "text-white/30" : "text-[#49454F]/50"}><path d="M12 6.042A8.967 8.967 0 0 0 6 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 0 1 6 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 0 1 6-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0 0 18 18a8.967 8.967 0 0 0-6 2.292m0-14.25v14.25" /></svg>
                            </div>
                            <div className="flex-1 flex flex-col items-start gap-0.5">
                                <span className={`text-lg font-black leading-tight ${isDark ? 'text-white' : 'text-[#1D1B20]'}`}>WikiLab</span>
                                <span className={`text-xs font-medium text-left leading-snug ${isDark ? 'text-white/40' : 'text-[#49454F] opacity-60'}`}>Ciencia del entrenamiento y biomecánica.</span>
                            </div>
                        </button>
                    </div>
                </div>
            </div>
        );
    };

    const renderEmpty = () => (
        <div className="flex-1 flex flex-col bg-transparent overflow-visible pt-10">
            <div className={`px-6 mb-8 text-[32px] font-black font-['Roboto'] leading-tight tracking-tighter ${isDark ? 'text-white' : 'text-[#1C1B1F]'}`}>
                Inicia tu próximo<br /><span className="text-primary">Plan Maestro</span>
            </div>
            {/* Minimal Auge Panel */}
            <div className="w-full overflow-visible">
                <AugeTelemetryPanel variant="hero" shareable />
            </div>

            <HomeCardsSection onNavigateToCard={vm.handleCardNav} />

            <div className="px-6 mt-16 pb-24 flex flex-col items-center text-center gap-8">
                <div className="relative">
                    <CaupolicanIcon size={120} className={isDark ? "text-white/[0.03]" : "text-black/[0.03]"} />
                    <div className="absolute inset-0 flex items-center justify-center">
                        <PlusIcon size={40} className="text-primary/10" />
                    </div>
                </div>
                <div className="max-w-[280px] space-y-3">
                    <p className="text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F] opacity-40">Arsenal Vacío</p>
                    <p className={`text-base font-medium leading-relaxed ${isDark ? 'text-white/50' : 'text-[#49454F] opacity-70'}`}>Configura tu biometría avanzada creando tu primer programa de entrenamiento.</p>
                </div>
                <Button onClick={() => onNavigate('program-editor')} className="w-full !py-5 !rounded-3xl shadow-2xl shadow-primary/20 !bg-primary !text-white !font-black !tracking-widest">
                    CREAR PROGRAMA
                </Button>
            </div>
        </div>
    );

    return (
        <div className={`w-full min-h-screen pb-20 overflow-x-hidden relative ${isDark ? 'bg-[#121212]' : 'bg-transparent'}`}>
            {activeProgram ? renderWithProgram() : renderEmpty()}
        </div>
    );
};

export default Home;
