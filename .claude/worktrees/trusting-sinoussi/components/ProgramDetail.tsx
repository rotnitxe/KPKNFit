import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program } from '../types';
import { CalendarIcon, ActivityIcon, DumbbellIcon, XIcon } from './icons';
import { useAppContext } from '../contexts/AppContext';
import { calculateUnifiedMuscleVolume } from '../services/volumeCalculator';
import { getCachedAdaptiveData, AugeAdaptiveCache } from '../services/augeAdaptiveService';

import CompactHeroBanner from './program-detail/CompactHeroBanner';
import type { StructureSubTab, AnalyticsSubTab } from './program-detail/IntegratedTabs';
import WeekView from './program-detail/WeekView';
import VolumeView from './program-detail/VolumeView';
import ProgressView from './program-detail/ProgressView';
import HistoryView from './program-detail/HistoryView';
import IntegratedTabs from './program-detail/IntegratedTabs';
import BlockRoadmap from './program-detail/BlockRoadmap';
import DayView from './program-detail/DayView';
import { SplitView } from './program-detail/SplitView';
import { MacrocycleEditor } from './program-detail/MacrocycleEditorIntegrated';
import LoopsView from './program-detail/LoopsView';
import ProtocolsView from './program-detail/ProtocolsView';

interface ProgramDetailProps {
    program: Program;
    onEdit?: () => void;
    isActive?: boolean;
    history?: any;
    settings?: any;
    isOnline?: boolean;
    onLogWorkout?: any;
    onEditProgram?: any;
    onEditSession?: any;
    onDeleteSession?: any;
    onAddSession?: any;
    onDeleteProgram?: any;
    onUpdateProgram?: any;
    onStartWorkout?: any;
}

const ProgramDetail: React.FC<ProgramDetailProps> = ({ program, onDeleteSession }) => {
    const {
        history, settings, setSettings, handleEditSession, handleBack, handleAddSession, isOnline,
        exerciseList, handleStartProgram, handlePauseProgram, handleEditProgram,
        handleUpdateProgram, handleChangeSplit, handleReorderSessions, addToast, handleStartWorkout, postSessionFeedback,
    } = useAppContext();
    const { activeProgramState } = useAppContext();

    // ─── State ───
    const [activeTab, setActiveTab] = useState<'training' | 'analytics'>('training');
    const [structureSubTab, setStructureSubTab] = useState<StructureSubTab>('semana');
    const [analyticsSubTab, setAnalyticsSubTab] = useState<AnalyticsSubTab>('volumen');
    const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null);
    const [selectedWeekId, setSelectedWeekId] = useState<string | null>(null);
    const [tourStep, setTourStep] = useState(0);

    // ─── Program type detection ───
    const isSimpleProgram = useMemo(() =>
        program.structure === 'simple' || (!program.structure && program.macrocycles.length === 1 && (program.macrocycles[0]?.blocks || []).length <= 1),
        [program]);

    // ─── Derived data ───
    const roadmapBlocks = useMemo(() =>
        program.macrocycles.flatMap((macro, macroIdx) =>
            (macro.blocks || []).map((block, blockIdx) => ({
                ...block, macroIndex: macroIdx, blockIndex: blockIdx,
                totalWeeks: block.mesocycles.reduce((acc, m) => acc + m.weeks.length, 0),
            }))
        ),
        [program]);

    const activeBlockId = useMemo(() => {
        if (!activeProgramState || activeProgramState.programId !== program.id) return null;
        const current = roadmapBlocks.find(b =>
            b.macroIndex === activeProgramState.currentMacrocycleIndex &&
            b.blockIndex === activeProgramState.currentBlockIndex
        );
        return current ? current.id : null;
    }, [activeProgramState, program.id, roadmapBlocks]);

    const currentWeeks = useMemo(() => {
        if (!selectedBlockId) return [];
        const block = roadmapBlocks.find(b => b.id === selectedBlockId);
        if (!block) return [];
        const macro = program.macrocycles[block.macroIndex];
        let mesoOffset = 0;
        if (macro) {
            for (const b of (macro.blocks || [])) {
                if (b.id === block.id) break;
                mesoOffset += b.mesocycles.length;
            }
        }
        return block.mesocycles.flatMap((meso, localMesoIdx) =>
            meso.weeks.map(w => ({ ...w, mesoGoal: meso.goal, mesoIndex: mesoOffset + localMesoIdx }))
        );
    }, [selectedBlockId, roadmapBlocks, program.macrocycles]);

    const visualizerData = useMemo(() => {
        if (currentWeeks.length === 0) return [];
        const allSessions = currentWeeks.flatMap(w => w.sessions);
        const totalVol = calculateUnifiedMuscleVolume(allSessions, exerciseList);
        return totalVol.map(v => ({ ...v, displayVolume: Math.round((v.displayVolume / currentWeeks.length) * 10) / 10 }));
    }, [currentWeeks, exerciseList]);

    const displayedSessions = useMemo(() => {
        if (!selectedWeekId) return [];
        const week = currentWeeks.find(w => w.id === selectedWeekId);
        return week ? week.sessions : [];
    }, [selectedWeekId, currentWeeks]);

    const programDiscomforts = useMemo(() => {
        const map = new Map<string, number>();
        history.filter((log: any) => log.programId === program.id).forEach((log: any) => {
            (log.discomforts || []).forEach((d: string) => map.set(d, (map.get(d) || 0) + 1));
        });
        return Array.from(map.entries()).map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count);
    }, [history, program.id]);

    const [adaptiveCache] = useState<AugeAdaptiveCache>(() => getCachedAdaptiveData());

    const programLogs = useMemo(() =>
        history.filter((log: any) => log.programId === program.id).sort((a: any, b: any) => new Date(b.date).getTime() - new Date(a.date).getTime()),
        [history, program.id]);

    const totalAdherence = useMemo(() => {
        const completedIds = new Set(programLogs.map((l: any) => l.sessionId));
        const allSessions = program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks.flatMap(w => w.sessions))));
        if (allSessions.length === 0) return 0;
        return Math.round((allSessions.filter(s => completedIds.has(s.id)).length / allSessions.length) * 100);
    }, [programLogs, program]);

    const weeklyAdherence = useMemo(() =>
        currentWeeks.map((week, idx) => {
            const weekSessionIds = new Set(week.sessions.map(s => s.id));
            const logs = programLogs.filter((l: any) => weekSessionIds.has(l.sessionId));
            const completed = new Set(logs.map((l: any) => l.sessionId)).size;
            const planned = week.sessions.length;
            return { weekName: `Semana ${idx + 1}`, pct: planned > 0 ? Math.round((completed / planned) * 100) : 0 };
        }),
        [currentWeeks, programLogs]);

    const totalWeeks = useMemo(() =>
        program.macrocycles.reduce((acc, m) => acc + (m.blocks || []).reduce((ba, b) => ba + b.mesocycles.reduce((ma, me) => ma + me.weeks.length, 0), 0), 0),
        [program]);

    const currentWeekIndex = useMemo(() => {
        if (!activeProgramState || activeProgramState.programId !== program.id) return 0;
        let weekIdx = 0;
        for (const m of program.macrocycles) {
            for (const b of (m.blocks || [])) {
                for (const me of b.mesocycles) {
                    for (const w of me.weeks) {
                        if (w.id === activeProgramState.currentWeekId) return weekIdx;
                        weekIdx++;
                    }
                }
            }
        }
        return 0;
    }, [activeProgramState, program]);

    // ─── Effects ───
    useEffect(() => {
        if (roadmapBlocks.length > 0 && (!selectedBlockId || !roadmapBlocks.find(b => b.id === selectedBlockId))) {
            setSelectedBlockId(roadmapBlocks[0].id);
        }
    }, [roadmapBlocks, selectedBlockId]);

    useEffect(() => {
        if (currentWeeks.length > 0 && (!selectedWeekId || !currentWeeks.find(w => w.id === selectedWeekId))) {
            setSelectedWeekId(currentWeeks[0].id);
        }
    }, [selectedBlockId, currentWeeks, selectedWeekId]);

    useEffect(() => {
        const tourSeen = localStorage.getItem(`kpkn_tour_seen_${program.id}`);
        if (!tourSeen && program.id) {
            const timer = setTimeout(() => setTourStep(1), 500);
            return () => clearTimeout(timer);
        }
    }, [program.id]);

    // ─── Handlers ───
    const handleProgramEdit = useCallback(() => handleEditProgram(program.id), [handleEditProgram, program.id]);

    const onEditSessionClick = useCallback((sessionId: string) => {
        const block = roadmapBlocks.find(b => b.id === selectedBlockId);
        const week = currentWeeks.find(w => w.id === selectedWeekId);
        if (block && week) handleEditSession(program.id, block.macroIndex, week.mesoIndex, week.id, sessionId);
    }, [roadmapBlocks, selectedBlockId, currentWeeks, selectedWeekId, handleEditSession, program.id]);

    const onDeleteSessionHandler = useCallback((sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => {
        if (onDeleteSession) onDeleteSession(sessionId, programId, macroIndex, mesoIndex, weekId);
    }, [onDeleteSession]);

    const isActiveProgram = activeProgramState?.programId === program.id && activeProgramState?.status === 'active';
    const isPausedProgram = activeProgramState?.programId === program.id && activeProgramState?.status === 'paused';

    // ─── Render ───
    return (
        <div className="fixed inset-0 z-[100] flex flex-col min-h-0 bg-[#FEF7FF] text-zinc-900 safe-area-root">
            {/* Un único scroll: Hero + Tabs + Contenido */}
            <div className="flex-1 w-full min-h-0 overflow-y-auto overflow-x-hidden custom-scrollbar bg-[#FEF7FF]">
                <CompactHeroBanner
                    program={program}
                    isActive={!!isActiveProgram}
                    isPaused={!!isPausedProgram}
                    onBack={handleBack}
                    onEdit={handleProgramEdit}
                    onStart={() => handleStartProgram(program.id)}
                    onPause={handlePauseProgram}
                    onUpdateProgram={handleUpdateProgram}
                    currentWeekIndex={currentWeekIndex}
                    totalWeeks={totalWeeks}
                    totalAdherence={totalAdherence}                />

                {/* Tabs Integrados - Estructura | Analíticas */}
                <IntegratedTabs
                    activeMainTab={activeTab}
                    onMainTabChange={setActiveTab}
                    activeSubTab={activeTab === 'training' ? structureSubTab : analyticsSubTab}
                    onSubTabChange={(tab) => {
                        if (activeTab === 'training') {
                            setStructureSubTab(tab as StructureSubTab);
                        } else {
                            setAnalyticsSubTab(tab as AnalyticsSubTab);
                        }
                    }}
                    gradientTheme="purple"
                    isSimpleProgram={isSimpleProgram}
                />

                {/* Contenido: Training y/o Analytics */}
                <div className="flex flex-col min-h-0 min-w-0">
                    {/* Training panel */}
                    <AnimatePresence mode="wait">
                        {activeTab === 'training' && (
                            <motion.div
                                key="training-panel"
                                initial={{ opacity: 0, scale: 0.98 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.98 }}
                                transition={{ duration: 0.2 }}
                                className="flex-1 w-full"
                            >
                                {/* Roadmap de Bloques y Semanas */}
                                <BlockRoadmap
                                    program={program}
                                    selectedBlockId={selectedBlockId}
                                    selectedWeekId={selectedWeekId}
                                    currentWeekId={activeProgramState?.currentWeekId}
                                    onSelectBlock={setSelectedBlockId}
                                    onSelectWeek={setSelectedWeekId}
                                />

                                {/* Vista de Semana */}
                                {structureSubTab === 'semana' && (
                                    <DayView
                                        program={program}
                                        selectedWeekId={selectedWeekId}
                                        currentWeekId={activeProgramState?.currentWeekId}
                                        onEditSession={onEditSessionClick}
                                        onAddSession={(dayOfWeek) => {
                                            const block = roadmapBlocks.find(b => b.id === selectedBlockId);
                                            const week = currentWeeks.find(w => w.id === selectedWeekId);
                                            if (block && week) handleAddSession(program.id, block.macroIndex, week.mesoIndex, selectedWeekId || '', dayOfWeek);
                                        }}
                                        onDeleteSession={onDeleteSessionHandler}
                                        onStartWorkout={(session) => handleStartWorkout(session, program)}
                                        onUpdateProgram={handleUpdateProgram}
                                        addToast={addToast}
                                    />
                                )}

                                {/* Vista de Split - INTEGRADA, NO DRAWER */}
                                {structureSubTab === 'split' && (
                                    <SplitView
                                        program={program}
                                        selectedBlockId={selectedBlockId}
                                        selectedWeekId={selectedWeekId}
                                        onUpdateProgram={handleUpdateProgram}
                                        addToast={addToast}
                                    />
                                )}

                                {/* Vista de Loops - Programas Simples */}
                                {structureSubTab === 'loops' && (
                                    <LoopsView
                                        program={program}
                                        onUpdateProgram={handleUpdateProgram}
                                        addToast={addToast}
                                    />
                                )}

                                {/* Vista de Protocolos - Programas Avanzados */}
                                {structureSubTab === 'protocolos' && (
                                    <ProtocolsView
                                        program={program}
                                        onUpdateProgram={handleUpdateProgram}
                                        addToast={addToast}
                                    />
                                )}

                                {/* Vista de Macrociclo - EDITOR AVANZADO INTEGRADO */}
                                {structureSubTab === 'macrociclo' && (
                                    <MacrocycleEditor
                                        program={program}
                                        onUpdateProgram={handleUpdateProgram}
                                        addToast={addToast}
                                    />
                                )}

                                <div className="h-[max(120px,calc(100px+env(safe-area-inset-bottom)))]" />
                            </motion.div>
                        )}

                        {/* Analytics panel */}
                        {activeTab === 'analytics' && (
                            <motion.div
                                key="analytics-panel"
                                initial={{ opacity: 0, scale: 0.98 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.98 }}
                                transition={{ duration: 0.2 }}
                                className="flex-1 w-full"
                            >
                                {/* Vista de Volumen */}
                                {analyticsSubTab === 'volumen' && (
                                    <VolumeView
                                        program={program}
                                        history={history}
                                        settings={settings}
                                        isOnline={isOnline}
                                        isActive={!!isActiveProgram}
                                        currentWeeks={currentWeeks}
                                        selectedWeekId={selectedWeekId ?? undefined}
                                        onSelectWeek={setSelectedWeekId}
                                        visualizerData={visualizerData}
                                        displayedSessions={displayedSessions}
                                        totalAdherence={totalAdherence}
                                        weeklyAdherence={weeklyAdherence}
                                        programDiscomforts={programDiscomforts}
                                        adaptiveCache={adaptiveCache}
                                        exerciseList={exerciseList}
                                        setSettings={setSettings}
                                        onUpdateProgram={handleUpdateProgram}
                                        addToast={addToast}
                                        postSessionFeedback={postSessionFeedback}
                                    />
                                )}

                                {/* Vista de Progreso */}
                                {analyticsSubTab === 'progreso' && (
                                    <ProgressView
                                        program={program}
                                        history={history}
                                        settings={settings}
                                    />
                                )}

                                {/* Vista de Historiales */}
                                {analyticsSubTab === 'historiales' && (
                                    <HistoryView
                                        program={program}
                                        history={history}
                                    />
                                )}

                                <div className="h-[max(120px,calc(100px+env(safe-area-inset-bottom)))]" />
                            </motion.div>
                        )}
                    </AnimatePresence>
                </div>
            </div>

            {/* ╝╝╝ Modals ╝╝╝ */}

            {/* Tour de bienvenida */}
            {tourStep > 0 && (
                <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40 backdrop-blur-md px-4">
                    <div className="bg-white border border-[#ECE6F0] rounded-[2.5rem] p-8 max-w-sm w-full shadow-2xl relative text-center animate-in fade-in zoom-in duration-300">
                        <div className="absolute top-0 left-0 w-full h-1.5 bg-blue-600 rounded-t-full" />
                        <div className="w-20 h-20 mx-auto bg-blue-50 rounded-[2rem] flex items-center justify-center mb-6">
                            {tourStep === 1 && <DumbbellIcon size={32} className="text-blue-600" />}
                            {tourStep === 2 && <ActivityIcon size={32} className="text-blue-600" />}
                        </div>
                        <h3 className="text-base font-black text-zinc-900 uppercase tracking-widest mb-3">
                            {tourStep === 1 ? 'Entrenamiento' : 'Analytics'}
                        </h3>
                        <p className="text-[11px] text-[#49454F] font-bold leading-relaxed uppercase tracking-tight mb-8">
                            {tourStep === 1 ? 'La grilla muestra tu semana completa. Usa el selector de bloques/semanas para abrir la estructura. Toca una sesión para iniciar o editar.'
                                : 'El panel de Analytics muestra volumen, fuerza, recuperación y más en tiempo real con inteligencia adaptativa.'}
                        </p>
                        <div className="flex justify-between items-center border-t border-gray-50 pt-6">
                            <div className="flex gap-2">
                                {[1, 2].map(step => (
                                    <div key={step} className={`w-3 h-3 rounded-full transition-all ${tourStep === step ? 'bg-blue-600 w-8' : 'bg-gray-200'}`} />
                                ))}
                            </div>
                            <button onClick={() => {
                                if (tourStep < 2) setTourStep(prev => prev + 1);
                                else { setTourStep(0); localStorage.setItem(`kpkn_tour_seen_${program.id}`, 'true'); }
                            }} className="bg-zinc-900 text-white px-8 py-3 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-zinc-800 transition-all shadow-lg active:scale-95">
                                {tourStep < 2 ? 'Siguiente' : '¡Entendido!'}
                            </button>
                        </div>
                        <button onClick={() => { setTourStep(0); localStorage.setItem(`kpkn_tour_seen_${program.id}`, 'true'); }} className="absolute top-8 right-8 text-zinc-300 hover:text-zinc-900 transition-colors">
                            <XIcon size={16} />
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ProgramDetail;









