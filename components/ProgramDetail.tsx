import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { Program, Session, ProgramWeek } from '../types';
import { CalendarIcon, ActivityIcon, DumbbellIcon, XIcon, TrashIcon } from './icons';
import { useAppContext } from '../contexts/AppContext';
import { calculateUnifiedMuscleVolume } from '../services/volumeCalculator';
import InteractiveWeekOverlay from './InteractiveWeekOverlay';
import SplitChangerDrawer from './program-detail/SplitChangerDrawer';
import StructureDrawer from './program-detail/StructureDrawer';
import { getCachedAdaptiveData, AugeAdaptiveCache } from '../services/augeAdaptiveService';

import CompactHeroBanner from './program-detail/CompactHeroBanner';
import ProgramStructureTab from './program-detail/ProgramStructureTab';
import TrainingCalendarGrid from './program-detail/TrainingCalendarGrid';
import AnalyticsDashboard from './program-detail/AnalyticsDashboard';

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
    const [isSplitChangerOpen, setIsSplitChangerOpen] = useState(false);
    const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null);
    const [selectedWeekId, setSelectedWeekId] = useState<string | null>(null);
    const [editingWeekInfo, setEditingWeekInfo] = useState<{
        macroIndex: number; blockIndex: number; mesoIndex: number;
        weekIndex: number; week: ProgramWeek; isSimple: boolean;
    } | null>(null);
    const [tourStep, setTourStep] = useState(0);
    const [showAdvancedTransition, setShowAdvancedTransition] = useState(false);
    const [showSimpleTransition, setShowSimpleTransition] = useState(false);
    const [isEventModalOpen, setIsEventModalOpen] = useState(false);
    const [newEventData, setNewEventData] = useState({ id: '', title: '', repeatEveryXCycles: 1, calculatedWeek: 0, type: '1rm_test' });

    // ─── Derived data ───
    const isCyclic = useMemo(() =>
        program.structure === 'simple' || (!program.structure && program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1),
        [program]);

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

    const trainingDaysCount = useMemo(() => {
        if (!displayedSessions.length) return 0;
        const days = new Set(displayedSessions.map(s => s.dayOfWeek));
        return days.size;
    }, [displayedSessions]);

    // ─── Effects ───
    useEffect(() => {
        if (roadmapBlocks.length > 0 && (!selectedBlockId || !roadmapBlocks.find(b => b.id === selectedBlockId))) {
            setSelectedBlockId(roadmapBlocks[0].id);
        }
    }, [roadmapBlocks]);

    useEffect(() => {
        if (currentWeeks.length > 0 && (!selectedWeekId || !currentWeeks.find(w => w.id === selectedWeekId))) {
            setSelectedWeekId(currentWeeks[0].id);
        }
    }, [selectedBlockId, currentWeeks]);

    useEffect(() => {
        const tourSeen = localStorage.getItem(`kpkn_tour_seen_${program.id}`);
        if (!tourSeen && program.id) {
            const timer = setTimeout(() => setTourStep(1), 500);
            return () => clearTimeout(timer);
        }
    }, [program.id]);

    // ─── Handlers ───
    const handleProgramEdit = useCallback(() => handleEditProgram(program.id), [handleEditProgram, program.id]);

    const onEditSessionClick = useCallback((session: Session) => {
        const block = roadmapBlocks.find(b => b.id === selectedBlockId);
        const week = currentWeeks.find(w => w.id === selectedWeekId);
        if (block && week) handleEditSession(program.id, block.macroIndex, week.mesoIndex, week.id, session.id);
    }, [roadmapBlocks, selectedBlockId, currentWeeks, selectedWeekId, handleEditSession, program.id]);

    const onDeleteSessionHandler = useCallback((sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => {
        if (onDeleteSession) onDeleteSession(sessionId, programId, macroIndex, mesoIndex, weekId);
    }, [onDeleteSession]);

    const isActiveProgram = activeProgramState?.programId === program.id && activeProgramState?.status === 'active';
    const isPausedProgram = activeProgramState?.programId === program.id && activeProgramState?.status === 'paused';

    const [isStructureDrawerOpen, setIsStructureDrawerOpen] = useState(false);

    // ─── Render ───
    return (
        <div className="fixed inset-0 z-[100] flex flex-col min-h-0 bg-[#FEF7FF] text-zinc-900 safe-area-root items-center">
            {/* Un único scroll: Hero + Tabs + Contenido (max-w-md para Mobile First) */}
            <div className="flex-1 w-full max-w-md min-h-0 overflow-y-auto custom-scrollbar bg-[#FEF7FF]">
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
                    totalAdherence={totalAdherence}
                    trainingDaysCount={trainingDaysCount}
                />

                {/* Tabs: Estructura | Analíticas (M3 Segmented Button) */}
                <div className="flex justify-center py-6 border-b border-[#ECE6F0] bg-[#FEF7FF]">
                    <div className="flex bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-1.5 w-[calc(100%-48px)] shadow-sm">
                        <button
                            onClick={() => setActiveTab('training')}
                            className={`flex-1 py-3 rounded-xl text-[10px] font-black uppercase tracking-[0.2em] transition-all ${activeTab === 'training'
                                ? 'bg-white text-[var(--md-sys-color-primary)] shadow-md'
                                : 'text-black/60 hover:text-black'
                                }`}
                        >
                            <span className="flex items-center justify-center gap-2">
                                {activeTab === 'training' && <div className="w-1 h-1 rounded-full bg-[var(--md-sys-color-primary)]" />}
                                Estructura
                            </span>
                        </button>
                        <button
                            onClick={() => setActiveTab('analytics')}
                            className={`flex-1 py-3 rounded-xl text-[10px] font-black uppercase tracking-[0.2em] transition-all ${activeTab === 'analytics'
                                ? 'bg-white text-[var(--md-sys-color-primary)] shadow-md'
                                : 'text-black/60 hover:text-black'
                                }`}
                        >
                            <span className="flex items-center justify-center gap-2">
                                {activeTab === 'analytics' && <div className="w-1 h-1 rounded-full bg-[var(--md-sys-color-primary)]" />}
                                Analíticas
                            </span>
                        </button>
                    </div>
                </div>

                {/* Contenido: Training y/o Analytics */}
                <div className="flex flex-col min-h-0 bg-[#FEF7FF]">
                    {/* Training panel */}
                    <div className={`flex-1 w-full ${activeTab !== 'training' ? 'hidden' : ''}`} style={{ minWidth: 0 }}>
                        <div className="p-4 flex flex-col gap-2">
                            <button
                                onClick={() => setIsStructureDrawerOpen(true)}
                                className="w-full py-4 rounded-2xl border border-[var(--md-sys-color-outline-variant)] bg-white text-[11px] font-black uppercase tracking-widest shadow-lg hover:shadow-xl transition-all flex justify-center items-center gap-2 text-black active:scale-[0.98]"
                            >
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><rect width="18" height="18" x="3" y="3" rx="2" /><path d="M3 9h18" /><path d="M9 21V9" /></svg>
                                Editar Macrociclos / Fases
                            </button>
                            <button
                                onClick={() => setIsSplitChangerOpen(true)}
                                className="w-full py-4 rounded-2xl border border-[var(--md-sys-color-primary)]/20 bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] text-[11px] font-black uppercase tracking-widest shadow-lg hover:shadow-xl transition-all flex justify-center items-center gap-2 active:scale-[0.98]"
                            >
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2" /></svg>
                                Cambiar Split de Entrenamiento
                            </button>
                        </div>
                        <ProgramStructureTab
                            program={program}
                            history={history}
                            currentWeekId={selectedWeekId || undefined}
                            onEditSession={(sessionId) => {
                                const dummySession = { id: sessionId } as any;
                                onEditSessionClick(dummySession);
                            }}
                            onAddSession={() => handleAddSession(program.id, 0, 0, selectedWeekId || '')}
                            onDeleteSession={onDeleteSessionHandler}
                            onUpdateProgram={handleUpdateProgram}
                        />
                        <div className="h-[max(120px,calc(100px+env(safe-area-inset-bottom)))]" />
                    </div>

                    {/* Analytics panel */}
                    <div className={`flex-1 w-full bg-[#FEF7FF] ${activeTab !== 'analytics' ? 'hidden' : ''}`}>
                        <AnalyticsDashboard
                            program={program}
                            history={history}
                            settings={settings}
                            isOnline={isOnline}
                            isActive={!!isActiveProgram}
                            currentWeeks={currentWeeks}
                            selectedWeekId={selectedWeekId}
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
                    </div>
                </div>
            </div>

            {/* ═══ Modals ═══ */}

            <StructureDrawer
                isOpen={isStructureDrawerOpen}
                onClose={() => setIsStructureDrawerOpen(false)}
                program={program}
                isCyclic={isCyclic}
                selectedBlockId={selectedBlockId}
                selectedWeekId={selectedWeekId}
                onSelectBlock={setSelectedBlockId}
                onSelectWeek={(id) => { setSelectedWeekId(id); setIsStructureDrawerOpen(false); }}
                onUpdateProgram={handleUpdateProgram}
                onEditWeek={setEditingWeekInfo}
                onShowAdvancedTransition={() => { setIsStructureDrawerOpen(false); setShowAdvancedTransition(true); }}
                onShowSimpleTransition={() => { setIsStructureDrawerOpen(false); setShowSimpleTransition(true); }}
                onOpenEventModal={(data) => {
                    setIsStructureDrawerOpen(false);
                    if (data) setNewEventData(data);
                    else setNewEventData({ id: '', title: '', repeatEveryXCycles: isCyclic ? 4 : 1, calculatedWeek: 0, type: '1rm_test' });
                    setIsEventModalOpen(true);
                }}
            />

            {/* Week Overlay */}
            {editingWeekInfo && (
                <InteractiveWeekOverlay
                    week={editingWeekInfo.week}
                    weekTitle={`Semana ${editingWeekInfo.weekIndex + 1}`}
                    onClose={() => setEditingWeekInfo(null)}
                    onSave={(updatedWeek) => {
                        const updated = JSON.parse(JSON.stringify(program));
                        if (editingWeekInfo.isSimple) {
                            if (updated.macrocycles[0]?.blocks[0]?.mesocycles[0]) updated.macrocycles[0].blocks[0].mesocycles[0].weeks[editingWeekInfo.weekIndex] = updatedWeek;
                        } else {
                            updated.macrocycles[editingWeekInfo.macroIndex].blocks[editingWeekInfo.blockIndex].mesocycles[editingWeekInfo.mesoIndex].weeks[editingWeekInfo.weekIndex] = updatedWeek;
                        }
                        if (handleUpdateProgram) { handleUpdateProgram(updated); addToast('Split semanal guardado.', 'success'); }
                        setEditingWeekInfo(null);
                    }}
                    onEditSession={(sessionId, intermediateWeek) => {
                        const updated = JSON.parse(JSON.stringify(program));
                        if (editingWeekInfo.isSimple) {
                            if (updated.macrocycles[0]?.blocks[0]?.mesocycles[0]) updated.macrocycles[0].blocks[0].mesocycles[0].weeks[editingWeekInfo.weekIndex] = intermediateWeek;
                        } else {
                            updated.macrocycles[editingWeekInfo.macroIndex].blocks[editingWeekInfo.blockIndex].mesocycles[editingWeekInfo.mesoIndex].weeks[editingWeekInfo.weekIndex] = intermediateWeek;
                        }
                        if (handleUpdateProgram) handleUpdateProgram(updated);
                        setEditingWeekInfo(null);
                        setTimeout(() => {
                            handleEditSession(program.id, editingWeekInfo.isSimple ? 0 : editingWeekInfo.macroIndex, editingWeekInfo.isSimple ? 0 : editingWeekInfo.mesoIndex, editingWeekInfo.week.id, sessionId);
                        }, 100);
                    }}
                />
            )}

            {/* Event Modal */}
            {isEventModalOpen && (
                <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setIsEventModalOpen(false)}>
                    <div className="bg-white border border-[#ECE6F0] w-full max-w-sm rounded-[2rem] p-8 shadow-2xl relative animate-in fade-in zoom-in duration-300" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setIsEventModalOpen(false)} className="absolute top-6 right-6 text-zinc-300 hover:text-zinc-900 transition-colors"><XIcon size={20} /></button>
                        <h2 className="text-sm font-black text-zinc-900 uppercase tracking-widest mb-8 flex items-center gap-3"><CalendarIcon size={18} className="text-blue-500" /> Evento</h2>
                        <div className="space-y-6">
                            <div>
                                <label className="text-[10px] text-[#49454F] font-black uppercase tracking-widest block mb-2">Nombre del Evento</label>
                                <input type="text" value={newEventData.title} onChange={e => setNewEventData({ ...newEventData, title: e.target.value })} placeholder="Ej: Prueba 1RM" className="w-full bg-[#ECE6F0] border border-[#ECE6F0] rounded-xl p-4 text-zinc-900 text-xs font-bold focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition-all" />
                            </div>
                            {isCyclic ? (
                                <div>
                                    <label className="text-[10px] text-[#49454F] font-black uppercase tracking-widest block mb-1">Cada cuántos ciclos</label>
                                    <div className="flex items-center gap-3">
                                        <input type="text" inputMode="numeric" pattern="[0-9]*" value={newEventData.repeatEveryXCycles} onChange={e => setNewEventData({ ...newEventData, repeatEveryXCycles: e.target.value === '' ? '' as any : parseInt(e.target.value) })} className="w-24 bg-[#ECE6F0] border border-[#ECE6F0] rounded-xl p-4 text-zinc-900 text-center text-xs font-bold" />
                                        <span className="text-[10px] text-[#49454F] font-black uppercase tracking-widest">Ciclos</span>
                                    </div>
                                </div>
                            ) : (
                                <div>
                                    <label className="text-[10px] text-[#49454F] font-black uppercase tracking-widest block mb-2">Semana de realización</label>
                                    <div className="flex items-center gap-3">
                                        <span className="text-[10px] text-[#49454F] font-black uppercase tracking-widest min-w-[60px]">Semana</span>
                                        <input type="text" inputMode="numeric" pattern="[0-9]*" value={newEventData.calculatedWeek === -1 ? '' : newEventData.calculatedWeek + 1} onChange={e => setNewEventData({ ...newEventData, calculatedWeek: e.target.value === '' ? -1 : (parseInt(e.target.value) || 1) - 1 })} className="w-full bg-[#ECE6F0] border border-[#ECE6F0] rounded-xl p-4 text-zinc-900 text-center text-xs font-bold" />
                                    </div>
                                </div>
                            )}
                            <div className="flex gap-3 mt-4 pt-4 border-t border-gray-50">
                                {newEventData.id && (
                                    <button onClick={() => {
                                        if (window.confirm('¿Eliminar evento?')) {
                                            const updated = JSON.parse(JSON.stringify(program));
                                            updated.events = updated.events.filter((e: any) => e.id !== newEventData.id);
                                            if (handleUpdateProgram) handleUpdateProgram(updated);
                                            setIsEventModalOpen(false);
                                        }
                                    }} className="w-14 h-14 bg-red-50 border border-red-100 text-red-500 rounded-xl flex items-center justify-center shrink-0 hover:bg-red-100 transition-colors">
                                        <TrashIcon size={18} />
                                    </button>
                                )}
                                <button onClick={() => {
                                    if (!newEventData.title.trim()) { addToast('Nombre requerido', 'danger'); return; }
                                    const updated = JSON.parse(JSON.stringify(program));
                                    if (!updated.events) updated.events = [];
                                    const payload = {
                                        id: newEventData.id || crypto.randomUUID(),
                                        title: newEventData.title, type: newEventData.type,
                                        date: new Date().toISOString(),
                                        calculatedWeek: isCyclic ? 0 : (newEventData.calculatedWeek === -1 ? 0 : newEventData.calculatedWeek),
                                        repeatEveryXCycles: isCyclic ? (parseInt(newEventData.repeatEveryXCycles as any) || 1) : undefined,
                                    };
                                    if (newEventData.id) {
                                        const idx = updated.events.findIndex((e: any) => e.id === newEventData.id);
                                        if (idx !== -1) updated.events[idx] = payload;
                                    } else {
                                        updated.events.push(payload);
                                    }
                                    if (handleUpdateProgram) handleUpdateProgram(updated);
                                    setIsEventModalOpen(false);
                                    addToast(newEventData.id ? 'Evento actualizado' : 'Evento creado', 'success');
                                }} className="flex-1 bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] font-black text-[10px] uppercase tracking-widest py-4 rounded-xl hover:opacity-90 transition-all active:scale-[0.98] shadow-lg">
                                    Guardar Evento
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Advanced Transition Modal */}
            {showAdvancedTransition && (
                <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowAdvancedTransition(false)}>
                    <div className="bg-white border border-[#ECE6F0] w-full max-w-md rounded-[2.5rem] p-8 shadow-2xl relative animate-in fade-in zoom-in duration-300" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setShowAdvancedTransition(false)} className="absolute top-8 right-8 text-zinc-300 hover:text-zinc-900 transition-colors"><XIcon size={20} /></button>
                        <h2 className="text-lg font-black text-zinc-900 uppercase tracking-tight mb-2 flex items-center gap-3"><ActivityIcon className="text-blue-500" /> Transición a Avanzado</h2>
                        <p className="text-xs text-[#49454F] mb-8 font-medium leading-relaxed uppercase tracking-widest">Convierte tu bucle en <span className="text-zinc-900 font-black">Periodización por Bloques</span>.</p>
                        <div className="space-y-4">
                            <button onClick={() => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.structure = 'complex'; updated.events = [];
                                updated.macrocycles[0].name = 'Macrociclo Principal';
                                updated.macrocycles[0].blocks[0].name = 'Bloque de Inicio';
                                updated.macrocycles[0].blocks.push({ id: crypto.randomUUID(), name: 'Nuevo Bloque', mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }] });
                                if (handleUpdateProgram) handleUpdateProgram(updated);
                                setShowAdvancedTransition(false); addToast('Programa convertido.', 'success');
                            }} className="w-full text-left p-6 rounded-[2rem] bg-[#ECE6F0] hover:bg-white hover:border-blue-200 border border-[#ECE6F0] transition-all shadow-sm group">
                                <h4 className="text-xs font-black text-zinc-900 uppercase tracking-widest mb-1 group-hover:text-blue-600 transition-colors">Bloque en Blanco</h4>
                                <p className="text-[11px] text-[#49454F] font-bold uppercase tracking-tight">Crear bloque vacío para empezar tu periodización.</p>
                            </button>
                            <button onClick={() => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.structure = 'complex'; updated.events = [];
                                updated.macrocycles[0].name = 'Macrociclo de Fuerza';
                                updated.macrocycles[0].blocks[0].name = 'Bloque de Acumulación';
                                updated.macrocycles[0].blocks.push({ id: crypto.randomUUID(), name: 'Bloque de Intensificación', mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Peaking', goal: 'Intensificación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }, { id: crypto.randomUUID(), name: 'Semana 2', sessions: [] }] }] });
                                if (handleUpdateProgram) handleUpdateProgram(updated);
                                setShowAdvancedTransition(false); addToast('Plantilla de fuerza aplicada.', 'success');
                            }} className="w-full text-left p-6 rounded-[2rem] bg-blue-50 hover:bg-white hover:border-blue-200 border border-blue-100/50 transition-all shadow-sm group">
                                <h4 className="text-xs font-black text-blue-600 uppercase tracking-widest mb-1">Plantilla de Fuerza</h4>
                                <p className="text-[11px] text-[#49454F] font-bold uppercase tracking-tight">Acumulación + Intensificación pre-configurados para peaking.</p>
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Simple Transition Modal */}
            {showSimpleTransition && (
                <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowSimpleTransition(false)}>
                    <div className="bg-white border border-[#ECE6F0] w-full max-w-md max-h-[85vh] flex flex-col rounded-[2.5rem] p-8 shadow-2xl relative animate-in fade-in zoom-in duration-300" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setShowSimpleTransition(false)} className="absolute top-8 right-8 text-zinc-300 hover:text-zinc-900 transition-colors"><XIcon size={20} /></button>
                        <h2 className="text-lg font-black text-zinc-900 uppercase tracking-tight mb-2">Volver a Simple</h2>
                        <p className="text-xs text-[#49454F] font-bold uppercase tracking-widest mb-6">¿Qué semana conservar como ciclo base?</p>
                        <div className="overflow-y-auto custom-scrollbar space-y-3 pr-1">
                            <button onClick={() => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.structure = 'simple'; updated.events = [];
                                updated.macrocycles = [{ id: crypto.randomUUID(), name: 'Macrociclo', blocks: [{ id: crypto.randomUUID(), name: 'BLOQUE CÍCLICO', mesocycles: [{ id: crypto.randomUUID(), name: 'Ciclo Base', goal: 'Custom', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }] }] }];
                                if (handleUpdateProgram) handleUpdateProgram(updated);
                                setShowSimpleTransition(false); addToast('Programa simplificado.', 'success');
                            }} className="w-full text-left p-5 rounded-2xl border-2 border-dashed border-[#E6E0E9] hover:border-blue-300 hover:bg-blue-50/50 transition-all">
                                <h4 className="text-xs font-black text-zinc-900 uppercase tracking-widest">Semana en Blanco</h4>
                                <p className="text-[11px] text-[#49454F] font-bold uppercase tracking-tight">Empezar desde cero con un bucle simple.</p>
                            </button>
                            {program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => (b.mesocycles || []).flatMap(me => (me.weeks || []).map(w => ({ ...w, label: `${b.name} · ${w.name}` }))))).map((week, idx) => (
                                <button key={idx} onClick={() => {
                                    const updated = JSON.parse(JSON.stringify(program));
                                    updated.structure = 'simple'; updated.events = [];
                                    updated.macrocycles = [{ id: crypto.randomUUID(), name: 'Macrociclo', blocks: [{ id: crypto.randomUUID(), name: 'BLOQUE CÍCLICO', mesocycles: [{ id: crypto.randomUUID(), name: 'Ciclo Base', goal: 'Custom', weeks: [{ ...week, id: crypto.randomUUID(), name: 'Semana 1' }] }] }] }];
                                    if (handleUpdateProgram) handleUpdateProgram(updated);
                                    setShowSimpleTransition(false); addToast(`Usando: ${week.label}`, 'success');
                                }} className="w-full text-left p-5 rounded-2xl bg-[#ECE6F0] border border-[#ECE6F0] hover:border-blue-200 hover:bg-white transition-all group">
                                    <h4 className="text-xs font-black text-zinc-900 uppercase tracking-widest truncate group-hover:text-blue-600 transition-colors">{week.label}</h4>
                                    <p className="text-[11px] text-[#49454F] font-bold tracking-tight">{(week.sessions || []).length} SESIONES DEFINIDAS</p>
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* Tour */}
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

            {/* Split Changer - drawer integrado con scopes */}
            <SplitChangerDrawer
                isOpen={isSplitChangerOpen}
                onClose={() => setIsSplitChangerOpen(false)}
                currentSplitId={program.selectedSplitId}
                currentStartDay={program.startDay ?? settings?.startWeekOn ?? 1}
                isSimpleProgram={isCyclic}
                onApply={(split, scope, preserveExercises, startDay) => {
                    const block = program.macrocycles[0]?.blocks?.find(b => b.id === selectedBlockId) || program.macrocycles[0]?.blocks?.[0];
                    const week = block?.mesocycles.flatMap(m => m.weeks).find(w => w.id === selectedWeekId) || block?.mesocycles[0]?.weeks[0];
                    handleChangeSplit(program.id, split.pattern, split.id, scope, preserveExercises, startDay, block?.id, week?.id);
                }}
            />
        </div>
    );
};

export default ProgramDetail;
