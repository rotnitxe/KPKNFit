import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { Program, Session, ProgramWeek } from '../types';
import { CalendarIcon, ActivityIcon, DumbbellIcon, XIcon, TrashIcon } from './icons';
import { useAppContext } from '../contexts/AppContext';
import { calculateUnifiedMuscleVolume } from '../services/volumeCalculator';
import InteractiveWeekOverlay from './InteractiveWeekOverlay';
import SplitChangerModal from './SplitChangerModal';
import { getCachedAdaptiveData, AugeAdaptiveCache } from '../services/augeAdaptiveService';

import CompactHeroBanner from './program-detail/CompactHeroBanner';
import TrainingCalendarGrid from './program-detail/TrainingCalendarGrid';
import AnalyticsDashboard from './program-detail/AnalyticsDashboard';
import StructureDrawer from './program-detail/StructureDrawer';

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
        history, settings, handleEditSession, handleBack, handleAddSession, isOnline,
        exerciseList, handleStartProgram, handlePauseProgram, handleEditProgram,
        handleUpdateProgram, handleChangeSplit, addToast, handleStartWorkout,
    } = useAppContext();
    const { activeProgramState } = useAppContext();

    // ─── State ───
    const [activeTab, setActiveTab] = useState<'training' | 'analytics'>('training');
    const [isStructureDrawerOpen, setIsStructureDrawerOpen] = useState(false);
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

    // ─── Render ───
    return (
        <div className="fixed inset-0 z-[100] bg-black text-white flex flex-col">
            {/* Hero Banner */}
            <CompactHeroBanner
                program={program}
                isActive={!!isActiveProgram}
                isPaused={!!isPausedProgram}
                onBack={handleBack}
                onEdit={handleProgramEdit}
                onStart={() => handleStartProgram(program.id)}
                onPause={handlePauseProgram}
                onOpenSplitChanger={() => setIsSplitChangerOpen(true)}
                onUpdateProgram={handleUpdateProgram}
                currentWeekIndex={currentWeekIndex}
                totalWeeks={totalWeeks}
                totalAdherence={totalAdherence}
                trainingDaysCount={trainingDaysCount}
            />

            {/* Tabs: Entrenamiento | Analytics */}
            <div className="flex border-b border-white/5 shrink-0">
                <button
                    onClick={() => setActiveTab('training')}
                    className={`flex-1 py-2.5 text-xs font-bold uppercase tracking-wide text-center transition-colors ${activeTab === 'training' ? 'text-[#FC4C02] border-b-2 border-[#FC4C02]' : 'text-[#48484A]'}`}
                >
                    Entrenamiento
                </button>
                <button
                    onClick={() => setActiveTab('analytics')}
                    className={`flex-1 py-2.5 text-xs font-bold uppercase tracking-wide text-center transition-colors ${activeTab === 'analytics' ? 'text-[#FC4C02] border-b-2 border-[#FC4C02]' : 'text-[#48484A]'}`}
                >
                    Analytics
                </button>
            </div>

            {/* Split view (desktop) / Tab content (mobile) */}
            <div className="flex-1 flex overflow-hidden min-h-0">
                {/* Training panel */}
                <div className={`flex-1 flex flex-col min-h-0 overflow-hidden bg-black ${activeTab !== 'training' ? 'hidden sm:flex' : ''}`} style={{ minWidth: 0 }}>
                    <TrainingCalendarGrid
                        program={program}
                        isCyclic={isCyclic}
                        roadmapBlocks={roadmapBlocks}
                        currentWeeks={currentWeeks}
                        selectedBlockId={selectedBlockId}
                        selectedWeekId={selectedWeekId}
                        activeBlockId={activeBlockId}
                        settings={settings}
                        exerciseList={exerciseList}
                        history={history}
                        onSelectBlock={setSelectedBlockId}
                        onSelectWeek={setSelectedWeekId}
                        onOpenSplitChanger={() => setIsSplitChangerOpen(true)}
                        onOpenStructureDrawer={() => setIsStructureDrawerOpen(true)}
                        onStartWorkout={handleStartWorkout}
                        onEditSession={onEditSessionClick}
                        onDeleteSession={onDeleteSessionHandler}
                        onAddSession={handleAddSession}
                        addToast={addToast}
                    />
                </div>

                {/* Analytics panel */}
                <div className={`sm:w-[40%] sm:max-w-[480px] sm:min-w-[320px] sm:border-l sm:border-white/5 bg-[#111] min-h-0 overflow-hidden flex flex-col ${activeTab !== 'analytics' ? 'hidden sm:flex' : ''}`}>
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
                    />
                </div>
            </div>

            {/* ═══ Drawers & Modals ═══ */}

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
                <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setIsEventModalOpen(false)}>
                    <div className="bg-[#1a1a1a] border border-white/10 w-full max-w-sm rounded-xl p-5 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setIsEventModalOpen(false)} className="absolute top-3 right-3 text-[#48484A] hover:text-white"><XIcon size={14} /></button>
                        <h2 className="text-sm font-bold text-white uppercase tracking-wide mb-4 flex items-center gap-2"><CalendarIcon size={16} className="text-[#FC4C02]" /> Evento</h2>
                        <div className="space-y-4">
                            <div>
                                <label className="text-[10px] text-[#8E8E93] font-bold block mb-1">Nombre</label>
                                <input type="text" value={newEventData.title} onChange={e => setNewEventData({ ...newEventData, title: e.target.value })} placeholder="Ej: Prueba 1RM" className="w-full bg-black border border-white/10 rounded-lg p-3 text-white text-xs font-bold focus:border-[#FC4C02] focus:ring-0 transition-colors" />
                            </div>
                            {isCyclic ? (
                                <div>
                                    <label className="text-[10px] text-[#8E8E93] font-bold block mb-1">Cada cuántos ciclos</label>
                                    <div className="flex items-center gap-3">
                                        <input type="text" inputMode="numeric" pattern="[0-9]*" value={newEventData.repeatEveryXCycles} onChange={e => setNewEventData({ ...newEventData, repeatEveryXCycles: e.target.value === '' ? '' as any : parseInt(e.target.value) })} className="w-24 bg-black border border-white/10 rounded-lg p-3 text-white text-center text-xs font-bold focus:border-[#FC4C02] focus:ring-0" />
                                        <span className="text-[10px] text-[#8E8E93] font-bold">Ciclos</span>
                                    </div>
                                </div>
                            ) : (
                                <div>
                                    <label className="text-[10px] text-[#8E8E93] font-bold block mb-1">Semana</label>
                                    <div className="flex items-center gap-3">
                                        <span className="text-[10px] text-[#8E8E93] font-bold">Semana</span>
                                        <input type="text" inputMode="numeric" pattern="[0-9]*" value={newEventData.calculatedWeek === -1 ? '' : newEventData.calculatedWeek + 1} onChange={e => setNewEventData({ ...newEventData, calculatedWeek: e.target.value === '' ? -1 : (parseInt(e.target.value) || 1) - 1 })} className="w-24 bg-black border border-white/10 rounded-lg p-3 text-white text-center text-xs font-bold focus:border-[#FC4C02] focus:ring-0" />
                                    </div>
                                </div>
                            )}
                            <div className="flex gap-2 mt-2">
                                {newEventData.id && (
                                    <button onClick={() => {
                                        if (window.confirm('¿Eliminar evento?')) {
                                            const updated = JSON.parse(JSON.stringify(program));
                                            updated.events = updated.events.filter((e: any) => e.id !== newEventData.id);
                                            if (handleUpdateProgram) handleUpdateProgram(updated);
                                            setIsEventModalOpen(false);
                                        }
                                    }} className="w-12 bg-[#FF3B30]/10 border border-[#FF3B30]/30 text-[#FF3B30] rounded-lg flex items-center justify-center shrink-0">
                                        <TrashIcon size={14} />
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
                                }} className="flex-1 bg-[#FC4C02] text-white font-bold text-xs py-3 rounded-lg hover:brightness-110 transition-all">
                                    Guardar
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Advanced Transition Modal */}
            {showAdvancedTransition && (
                <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowAdvancedTransition(false)}>
                    <div className="bg-[#1a1a1a] border border-white/10 w-full max-w-md rounded-xl p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setShowAdvancedTransition(false)} className="absolute top-4 right-4 text-[#48484A] hover:text-white"><XIcon size={14} /></button>
                        <h2 className="text-base font-bold text-white uppercase tracking-wide mb-2 flex items-center gap-2"><ActivityIcon className="text-[#FC4C02]" /> Transición a Avanzado</h2>
                        <p className="text-xs text-[#8E8E93] mb-6 leading-relaxed">Convierte tu bucle en <span className="text-white font-bold">Periodización por Bloques</span>.</p>
                        <div className="space-y-3">
                            <button onClick={() => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.structure = 'complex'; updated.events = [];
                                updated.macrocycles[0].name = 'Macrociclo Principal';
                                updated.macrocycles[0].blocks[0].name = 'Bloque de Inicio';
                                updated.macrocycles[0].blocks.push({ id: crypto.randomUUID(), name: 'Nuevo Bloque', mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }] });
                                if (handleUpdateProgram) handleUpdateProgram(updated);
                                setShowAdvancedTransition(false); addToast('Programa convertido.', 'success');
                            }} className="w-full text-left p-4 rounded-xl bg-white/5 hover:bg-[#FC4C02]/10 hover:border-[#FC4C02]/30 border border-white/5 transition-all">
                                <h4 className="text-xs font-bold text-white">Bloque en Blanco</h4>
                                <p className="text-[10px] text-[#8E8E93] mt-0.5">Crear bloque vacío para empezar.</p>
                            </button>
                            <button onClick={() => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.structure = 'complex'; updated.events = [];
                                updated.macrocycles[0].name = 'Macrociclo de Fuerza';
                                updated.macrocycles[0].blocks[0].name = 'Bloque de Acumulación';
                                updated.macrocycles[0].blocks.push({ id: crypto.randomUUID(), name: 'Bloque de Intensificación', mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Peaking', goal: 'Intensificación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }, { id: crypto.randomUUID(), name: 'Semana 2', sessions: [] }] }] });
                                if (handleUpdateProgram) handleUpdateProgram(updated);
                                setShowAdvancedTransition(false); addToast('Plantilla de fuerza aplicada.', 'success');
                            }} className="w-full text-left p-4 rounded-xl bg-white/5 hover:bg-[#FC4C02]/10 hover:border-[#FC4C02]/30 border border-white/5 transition-all">
                                <h4 className="text-xs font-bold text-white">Plantilla de Fuerza</h4>
                                <p className="text-[10px] text-[#8E8E93] mt-0.5">Acumulación + Intensificación pre-configurados.</p>
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Simple Transition Modal */}
            {showSimpleTransition && (
                <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowSimpleTransition(false)}>
                    <div className="bg-[#1a1a1a] border border-white/10 w-full max-w-md max-h-[85vh] flex flex-col rounded-xl p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setShowSimpleTransition(false)} className="absolute top-4 right-4 text-[#48484A] hover:text-white"><XIcon size={14} /></button>
                        <h2 className="text-base font-bold text-white uppercase tracking-wide mb-2">Volver a Simple</h2>
                        <p className="text-xs text-[#8E8E93] mb-4">¿Qué semana conservar como ciclo base?</p>
                        <div className="overflow-y-auto custom-scrollbar space-y-2 pr-1">
                            <button onClick={() => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.structure = 'simple'; updated.events = [];
                                updated.macrocycles = [{ id: crypto.randomUUID(), name: 'Macrociclo', blocks: [{ id: crypto.randomUUID(), name: 'BLOQUE CÍCLICO', mesocycles: [{ id: crypto.randomUUID(), name: 'Ciclo Base', goal: 'Custom', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }] }] }];
                                if (handleUpdateProgram) handleUpdateProgram(updated);
                                setShowSimpleTransition(false); addToast('Programa simplificado.', 'success');
                            }} className="w-full text-left p-3 rounded-xl border border-dashed border-white/10 hover:border-[#FC4C02]/30 transition-all">
                                <h4 className="text-xs font-bold text-white">Semana en Blanco</h4>
                                <p className="text-[10px] text-[#8E8E93]">Empezar desde cero.</p>
                            </button>
                            {program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => (b.mesocycles || []).flatMap(me => (me.weeks || []).map(w => ({ ...w, label: `${b.name} - ${w.name}` }))))).map((week, idx) => (
                                <button key={idx} onClick={() => {
                                    const updated = JSON.parse(JSON.stringify(program));
                                    updated.structure = 'simple'; updated.events = [];
                                    updated.macrocycles = [{ id: crypto.randomUUID(), name: 'Macrociclo', blocks: [{ id: crypto.randomUUID(), name: 'BLOQUE CÍCLICO', mesocycles: [{ id: crypto.randomUUID(), name: 'Ciclo Base', goal: 'Custom', weeks: [{ ...week, id: crypto.randomUUID(), name: 'Semana 1' }] }] }] }];
                                    if (handleUpdateProgram) handleUpdateProgram(updated);
                                    setShowSimpleTransition(false); addToast(`Usando: ${week.label}`, 'success');
                                }} className="w-full text-left p-3 rounded-xl bg-white/5 border border-white/5 hover:border-[#FC4C02]/30 transition-all">
                                    <h4 className="text-xs font-bold text-white truncate">{week.label}</h4>
                                    <p className="text-[10px] text-[#8E8E93]">{(week.sessions || []).length} sesiones</p>
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* Tour */}
            {tourStep > 0 && (
                <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-md px-4">
                    <div className="bg-[#1a1a1a] border border-white/10 rounded-xl p-6 max-w-sm w-full shadow-2xl relative text-center">
                        <div className="absolute top-0 left-0 w-full h-1 bg-[#FC4C02] rounded-t-xl" />
                        <div className="w-14 h-14 mx-auto bg-[#FC4C02]/10 rounded-xl flex items-center justify-center mb-4">
                            {tourStep === 1 && <DumbbellIcon size={24} className="text-[#FC4C02]" />}
                            {tourStep === 2 && <ActivityIcon size={24} className="text-[#FC4C02]" />}
                        </div>
                        <h3 className="text-base font-bold text-white uppercase mb-2">
                            {tourStep === 1 ? 'Entrenamiento' : 'Analytics'}
                        </h3>
                        <p className="text-xs text-[#8E8E93] leading-relaxed mb-6">
                            {tourStep === 1 ? 'La grilla muestra tu semana completa. Usa el selector de bloques/semanas para abrir la estructura. Toca una sesión para iniciar o editar.'
                                : 'El panel de Analytics muestra volumen, fuerza, recuperación y más en tiempo real.'}
                        </p>
                        <div className="flex justify-between items-center border-t border-white/5 pt-4">
                            <div className="flex gap-1.5">
                                {[1, 2].map(step => (
                                    <div key={step} className={`w-2 h-2 rounded-full ${tourStep === step ? 'bg-[#FC4C02]' : 'bg-white/10'}`} />
                                ))}
                            </div>
                            <button onClick={() => {
                                if (tourStep < 2) setTourStep(prev => prev + 1);
                                else { setTourStep(0); localStorage.setItem(`kpkn_tour_seen_${program.id}`, 'true'); }
                            }} className="bg-[#FC4C02] text-white px-5 py-2 rounded-lg text-[10px] font-bold uppercase tracking-wide hover:brightness-110 transition-all">
                                {tourStep < 2 ? 'Siguiente' : '¡Listo!'}
                            </button>
                        </div>
                        <button onClick={() => { setTourStep(0); localStorage.setItem(`kpkn_tour_seen_${program.id}`, 'true'); }} className="absolute top-3 right-3 text-[#48484A] hover:text-white">
                            <XIcon size={12} />
                        </button>
                    </div>
                </div>
            )}

            {/* Split Changer */}
            <SplitChangerModal
                isOpen={isSplitChangerOpen}
                onClose={() => setIsSplitChangerOpen(false)}
                currentSplitId={program.selectedSplitId}
                currentStartDay={program.startDay ?? settings?.startWeekOn ?? 1}
                isSimpleProgram={isCyclic}
                onApply={(split, scope, preserveExercises, startDay) => {
                    const block = roadmapBlocks.find(b => b.id === selectedBlockId);
                    const week = currentWeeks.find(w => w.id === selectedWeekId);
                    handleChangeSplit(program.id, split.pattern, split.id, scope, preserveExercises, startDay, block?.id, week?.id);
                }}
            />
        </div>
    );
};

export default ProgramDetail;
