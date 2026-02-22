import React, { useState, useMemo, useEffect, useRef } from 'react';
import { Program, Session, ProgramWeek, ExerciseMuscleInfo } from '../types';
import {
    CalendarIcon, ActivityIcon, DumbbellIcon, PlusIcon, XIcon, TrashIcon,
} from './icons';
import { useAppContext } from '../contexts/AppContext';
import { calculateUnifiedMuscleVolume } from '../services/volumeCalculator';
import { getOrderedDaysOfWeek } from '../utils/calculations';
import InteractiveWeekOverlay from './InteractiveWeekOverlay';
import SplitChangerModal, { SplitChangeScope } from './SplitChangerModal';
import { getCachedAdaptiveData, AugeAdaptiveCache } from '../services/augeAdaptiveService';

import {
    HeroBanner, QuickStatsBar, TrainingCard, StructureCard,
    MetricsCard, AugeIntelCard, EventsTimelineCard, ProgramConfigCard,
    StickyMiniNav,
} from './program-detail';

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

const ProgramDetail: React.FC<ProgramDetailProps> = ({ program, onStartWorkout, onEdit, isActive, onDeleteSession }) => {
    const {
        history, settings, handleEditSession, handleBack, handleAddSession, isOnline,
        exerciseList, handleStartProgram, handlePauseProgram, handleEditProgram,
        handleUpdateProgram, handleChangeSplit, addToast, handleStartWorkout,
    } = useAppContext();

    const { activeProgramState } = useAppContext();

    // ─── State ───
    const [isSplitChangerOpen, setIsSplitChangerOpen] = useState(false);
    const [editingWeekInfo, setEditingWeekInfo] = useState<{
        macroIndex: number; blockIndex: number; mesoIndex: number;
        weekIndex: number; week: ProgramWeek; isSimple: boolean;
    } | null>(null);
    const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null);
    const [selectedWeekId, setSelectedWeekId] = useState<string | null>(null);
    const [tourStep, setTourStep] = useState(0);
    const [showAdvancedTransition, setShowAdvancedTransition] = useState(false);
    const [showSimpleTransition, setShowSimpleTransition] = useState(false);
    const [isEventModalOpen, setIsEventModalOpen] = useState(false);
    const [newEventData, setNewEventData] = useState({ id: '', title: '', repeatEveryXCycles: 1, calculatedWeek: 0, type: '1rm_test' });

    // ─── Collapsed cards state ───
    const [collapsedCards, setCollapsedCards] = useState<Record<string, boolean>>({
        structure: true, metrics: true, auge: true, events: true, config: true,
    });
    const toggleCard = (id: string) => setCollapsedCards(prev => ({ ...prev, [id]: !prev[id] }));

    // ─── Section refs ───
    const sectionRefs = useRef<Record<string, HTMLDivElement | null>>({});
    const scrollTo = (id: string) => sectionRefs.current[id]?.scrollIntoView({ behavior: 'smooth', block: 'start' });

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
        return activeProgramState.currentWeekIndex || 0;
    }, [activeProgramState, program.id]);

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
    const handleProgramEdit = (e?: React.MouseEvent) => { e?.stopPropagation(); handleEditProgram(program.id); };

    const onEditSessionClick = (session: Session) => {
        const block = roadmapBlocks.find(b => b.id === selectedBlockId);
        const week = currentWeeks.find(w => w.id === selectedWeekId);
        if (block && week) handleEditSession(program.id, block.macroIndex, week.mesoIndex, week.id, session.id);
    };

    const onDeleteSessionHandler = (sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => {
        if (onDeleteSession) onDeleteSession(sessionId, programId, macroIndex, mesoIndex, weekId);
    };

    const isActiveProgram = activeProgramState?.programId === program.id && activeProgramState?.status === 'active';
    const isPausedProgram = activeProgramState?.programId === program.id && activeProgramState?.status === 'paused';

    // ─── Render ───
    return (
        <div className="fixed inset-0 z-[100] bg-black text-white overflow-y-auto custom-scrollbar">
            {/* Hero */}
            <HeroBanner
                program={program}
                isActive={!!isActiveProgram}
                isPaused={!!isPausedProgram}
                onBack={handleBack}
                onEdit={handleProgramEdit}
                onStart={() => handleStartProgram(program.id)}
                onPause={handlePauseProgram}
            />

            {/* Navigation */}
            <StickyMiniNav onScrollTo={scrollTo} />

            {/* Quick Stats */}
            <QuickStatsBar
                program={program}
                history={history}
                currentWeekIndex={currentWeekIndex}
                totalWeeks={totalWeeks}
            />

            {/* Dashboard Grid */}
            <div className="px-3 pb-32 space-y-3">
                {/* Training Card (always expanded) */}
                <div ref={el => { sectionRefs.current['training'] = el; }}>
                    <TrainingCard
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
                        onStartWorkout={handleStartWorkout}
                        onEditSession={onEditSessionClick}
                        onDeleteSession={onDeleteSessionHandler}
                        onAddSession={handleAddSession}
                        onUpdateProgram={handleUpdateProgram}
                        addToast={addToast}
                    />
                </div>

                {/* Structure Card */}
                <div ref={el => { sectionRefs.current['structure'] = el; }}>
                    <StructureCard
                        program={program}
                        isCyclic={isCyclic}
                        onUpdateProgram={handleUpdateProgram}
                        onEditWeek={setEditingWeekInfo}
                        onShowAdvancedTransition={() => setShowAdvancedTransition(true)}
                        onShowSimpleTransition={() => setShowSimpleTransition(true)}
                        collapsed={collapsedCards.structure}
                        onToggleCollapse={() => toggleCard('structure')}
                    />
                </div>

                {/* Metrics Card */}
                <div ref={el => { sectionRefs.current['metrics'] = el; }}>
                    <MetricsCard
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
                        collapsed={collapsedCards.metrics}
                        onToggleCollapse={() => toggleCard('metrics')}
                    />
                </div>

                {/* AUGE Intelligence */}
                <div ref={el => { sectionRefs.current['auge'] = el; }}>
                    <AugeIntelCard
                        adaptiveCache={adaptiveCache}
                        collapsed={collapsedCards.auge}
                        onToggleCollapse={() => toggleCard('auge')}
                    />
                </div>

                {/* Events */}
                <div ref={el => { sectionRefs.current['events'] = el; }}>
                    <EventsTimelineCard
                        program={program}
                        isCyclic={isCyclic}
                        onUpdateProgram={handleUpdateProgram}
                        onOpenEventModal={(data) => {
                            if (data) setNewEventData(data);
                            else setNewEventData({ id: '', title: '', repeatEveryXCycles: isCyclic ? 4 : 1, calculatedWeek: 0, type: '1rm_test' });
                            setIsEventModalOpen(true);
                        }}
                        collapsed={collapsedCards.events}
                        onToggleCollapse={() => toggleCard('events')}
                    />
                </div>

                {/* Config */}
                <div ref={el => { sectionRefs.current['config'] = el; }}>
                    <ProgramConfigCard
                        program={program}
                        onUpdateProgram={handleUpdateProgram}
                        onOpenSplitChanger={() => setIsSplitChangerOpen(true)}
                        collapsed={collapsedCards.config}
                        onToggleCollapse={() => toggleCard('config')}
                    />
                </div>
            </div>

            {/* ═══ Modals ═══ */}

            {/* Week Overlay */}
            {editingWeekInfo && (
                <InteractiveWeekOverlay
                    week={editingWeekInfo.week}
                    weekTitle={`Semana ${editingWeekInfo.weekIndex + 1}`}
                    onClose={() => setEditingWeekInfo(null)}
                    onSave={(updatedWeek) => {
                        const updated = JSON.parse(JSON.stringify(program));
                        if (editingWeekInfo.isSimple) {
                            if (updated.macrocycles[0]?.blocks[0]?.mesocycles[0]) {
                                updated.macrocycles[0].blocks[0].mesocycles[0].weeks[editingWeekInfo.weekIndex] = updatedWeek;
                            }
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
                            handleEditSession(
                                program.id,
                                editingWeekInfo.isSimple ? 0 : editingWeekInfo.macroIndex,
                                editingWeekInfo.isSimple ? 0 : editingWeekInfo.mesoIndex,
                                editingWeekInfo.week.id,
                                sessionId,
                            );
                        }, 100);
                    }}
                />
            )}

            {/* Event Modal */}
            {isEventModalOpen && (
                <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-sm flex items-center justify-center p-4 animate-fade-in" onClick={() => setIsEventModalOpen(false)}>
                    <div className="bg-[#0a0a0a] border border-white/10 w-full max-w-sm rounded-2xl p-5 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setIsEventModalOpen(false)} className="absolute top-3 right-3 text-zinc-500 hover:text-white bg-zinc-900 rounded-full p-1.5"><XIcon size={14} /></button>
                        <h2 className="text-base font-black text-white uppercase mb-4 tracking-tight flex items-center gap-2"><CalendarIcon size={16} /> Evento</h2>
                        <div className="space-y-4">
                            <div>
                                <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-1 block">Nombre</label>
                                <input type="text" value={newEventData.title} onChange={e => setNewEventData({ ...newEventData, title: e.target.value })} placeholder="Ej: Prueba 1RM" className="w-full bg-zinc-950 border border-white/10 rounded-xl p-3 text-white text-xs font-bold focus:border-white focus:ring-0" />
                            </div>
                            {isCyclic ? (
                                <div>
                                    <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-1 block">Cada cuántos ciclos</label>
                                    <div className="flex items-center gap-3">
                                        <input type="text" inputMode="numeric" pattern="[0-9]*" value={newEventData.repeatEveryXCycles} onChange={e => setNewEventData({ ...newEventData, repeatEveryXCycles: e.target.value === '' ? '' as any : parseInt(e.target.value) })} className="w-24 bg-zinc-950 border border-white/10 rounded-xl p-3 text-white text-center text-xs font-bold focus:border-white focus:ring-0" />
                                        <span className="text-[10px] text-zinc-400 font-bold uppercase">Ciclos</span>
                                    </div>
                                </div>
                            ) : (
                                <div>
                                    <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-1 block">Semana</label>
                                    <div className="flex items-center gap-3">
                                        <span className="text-[10px] text-zinc-400 font-bold uppercase">Semana</span>
                                        <input type="text" inputMode="numeric" pattern="[0-9]*" value={newEventData.calculatedWeek === -1 ? '' : newEventData.calculatedWeek + 1} onChange={e => setNewEventData({ ...newEventData, calculatedWeek: e.target.value === '' ? -1 : (parseInt(e.target.value) || 1) - 1 })} className="w-24 bg-zinc-950 border border-white/10 rounded-xl p-3 text-white text-center text-xs font-bold focus:border-white focus:ring-0" />
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
                                    }} className="w-12 bg-red-500/10 border border-red-500/30 text-red-500 rounded-xl flex items-center justify-center shrink-0">
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
                                }} className="flex-1 bg-white text-black font-black uppercase tracking-widest text-[10px] py-3 rounded-xl hover:bg-zinc-200 transition-colors">
                                    Guardar
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Advanced Transition Modal */}
            {showAdvancedTransition && (
                <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-sm flex items-center justify-center p-4 animate-fade-in" onClick={() => setShowAdvancedTransition(false)}>
                    <div className="bg-[#0a0a0a] border border-white/10 w-full max-w-md rounded-2xl p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setShowAdvancedTransition(false)} className="absolute top-4 right-4 text-zinc-500 hover:text-white bg-zinc-900 rounded-full p-2"><XIcon size={14} /></button>
                        <h2 className="text-lg font-black text-white uppercase mb-2 flex items-center gap-2"><ActivityIcon className="text-white" /> Transición a Avanzado</h2>
                        <p className="text-[10px] text-zinc-400 mb-6 font-bold leading-relaxed pr-4">
                            Convierte tu bucle en <span className="text-white">Periodización por Bloques</span>. Eventos cíclicos se borrarán.
                        </p>
                        <div className="space-y-3">
                            <button onClick={() => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.structure = 'complex'; updated.events = [];
                                updated.macrocycles[0].name = 'Macrociclo Principal';
                                updated.macrocycles[0].blocks[0].name = 'Bloque de Inicio';
                                updated.macrocycles[0].blocks.push({ id: crypto.randomUUID(), name: 'Nuevo Bloque', mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }] });
                                if (handleUpdateProgram) handleUpdateProgram(updated);
                                setShowAdvancedTransition(false); addToast('Programa convertido.', 'success');
                            }} className="w-full text-left p-4 rounded-xl bg-zinc-900/50 border border-white/10 hover:border-blue-500 hover:bg-blue-900/10 transition-all group">
                                <div className="flex items-center gap-3">
                                    <div className="w-8 h-8 rounded-full bg-blue-500/20 text-blue-400 flex items-center justify-center shrink-0"><PlusIcon size={14} /></div>
                                    <div>
                                        <h4 className="text-xs font-black text-white uppercase group-hover:text-blue-400 transition-colors">Bloque en Blanco</h4>
                                        <p className="text-[9px] text-zinc-500 mt-0.5">Crear bloque vacío.</p>
                                    </div>
                                </div>
                            </button>
                            <button onClick={() => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.structure = 'complex'; updated.events = [];
                                updated.macrocycles[0].name = 'Macrociclo de Fuerza';
                                updated.macrocycles[0].blocks[0].name = 'Bloque de Acumulación';
                                updated.macrocycles[0].blocks.push({ id: crypto.randomUUID(), name: 'Bloque de Intensificación', mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Peaking', goal: 'Intensificación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }, { id: crypto.randomUUID(), name: 'Semana 2', sessions: [] }] }] });
                                if (handleUpdateProgram) handleUpdateProgram(updated);
                                setShowAdvancedTransition(false); addToast('Plantilla de fuerza aplicada.', 'success');
                            }} className="w-full text-left p-4 rounded-xl bg-zinc-900/50 border border-white/10 hover:border-yellow-500 hover:bg-yellow-900/10 transition-all group">
                                <div className="flex items-center gap-3">
                                    <div className="w-8 h-8 rounded-full bg-yellow-500/20 text-yellow-400 flex items-center justify-center shrink-0"><DumbbellIcon size={14} /></div>
                                    <div>
                                        <h4 className="text-xs font-black text-white uppercase group-hover:text-yellow-400 transition-colors">Plantilla de Fuerza</h4>
                                        <p className="text-[9px] text-zinc-500 mt-0.5">Acumulación + Intensificación.</p>
                                    </div>
                                </div>
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Simple Transition Modal */}
            {showSimpleTransition && (
                <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-sm flex items-center justify-center p-4 animate-fade-in" onClick={() => setShowSimpleTransition(false)}>
                    <div className="bg-[#0a0a0a] border border-white/10 w-full max-w-md max-h-[85vh] flex flex-col rounded-2xl p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setShowSimpleTransition(false)} className="absolute top-4 right-4 text-zinc-500 hover:text-white bg-zinc-900 rounded-full p-2"><XIcon size={14} /></button>
                        <h2 className="text-lg font-black text-white uppercase mb-2 flex items-center gap-2"><ActivityIcon className="text-white" /> Volver a Simple</h2>
                        <p className="text-[10px] text-zinc-400 mb-4 font-bold leading-relaxed pr-4">
                            ¿Qué semana conservar como ciclo base? Las demás se eliminarán.
                        </p>
                        <div className="overflow-y-auto custom-scrollbar space-y-2 pr-1">
                            <button onClick={() => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.structure = 'simple'; updated.events = [];
                                updated.macrocycles = [{ id: crypto.randomUUID(), name: 'Macrociclo', blocks: [{ id: crypto.randomUUID(), name: 'BLOQUE CÍCLICO', mesocycles: [{ id: crypto.randomUUID(), name: 'Ciclo Base', goal: 'Custom', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }] }] }];
                                if (handleUpdateProgram) handleUpdateProgram(updated);
                                setShowSimpleTransition(false); addToast('Programa simplificado.', 'success');
                            }} className="w-full text-left p-3 rounded-xl bg-zinc-900/50 border border-dashed border-white/20 hover:border-white transition-all group">
                                <div className="flex items-center gap-3">
                                    <div className="w-8 h-8 rounded-full bg-zinc-800 text-zinc-400 flex items-center justify-center shrink-0"><PlusIcon size={14} /></div>
                                    <div>
                                        <h4 className="text-xs font-black text-white uppercase">Semana en Blanco</h4>
                                        <p className="text-[9px] text-zinc-500">Empezar desde cero.</p>
                                    </div>
                                </div>
                            </button>
                            {program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => (b.mesocycles || []).flatMap(me => (me.weeks || []).map(w => ({ ...w, label: `${b.name} - ${w.name}` }))))).map((week, idx) => (
                                <button key={idx} onClick={() => {
                                    const updated = JSON.parse(JSON.stringify(program));
                                    updated.structure = 'simple'; updated.events = [];
                                    updated.macrocycles = [{ id: crypto.randomUUID(), name: 'Macrociclo', blocks: [{ id: crypto.randomUUID(), name: 'BLOQUE CÍCLICO', mesocycles: [{ id: crypto.randomUUID(), name: 'Ciclo Base', goal: 'Custom', weeks: [{ ...week, id: crypto.randomUUID(), name: 'Semana 1' }] }] }] }];
                                    if (handleUpdateProgram) handleUpdateProgram(updated);
                                    setShowSimpleTransition(false); addToast(`Usando: ${week.label}`, 'success');
                                }} className="w-full text-left p-3 rounded-xl bg-zinc-950 border border-white/5 hover:border-blue-500 transition-all group">
                                    <div className="flex items-center gap-3">
                                        <div className="w-8 h-8 rounded-full bg-blue-500/20 text-blue-400 flex items-center justify-center shrink-0"><DumbbellIcon size={14} /></div>
                                        <div className="flex-1 min-w-0">
                                            <h4 className="text-xs font-black text-white uppercase truncate">{week.label}</h4>
                                            <p className="text-[9px] text-zinc-500">{(week.sessions || []).length} sesiones</p>
                                        </div>
                                    </div>
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* Tour */}
            {tourStep > 0 && (
                <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-md animate-fade-in px-4">
                    <div className="bg-zinc-950 border border-white/10 rounded-2xl p-6 max-w-sm w-full shadow-2xl relative text-center overflow-hidden">
                        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-blue-500 to-cyan-400" />
                        <div className="w-16 h-16 mx-auto bg-blue-900/20 rounded-full flex items-center justify-center mb-4 border border-blue-500/30">
                            {tourStep === 1 && <DumbbellIcon size={28} className="text-blue-400" />}
                            {tourStep === 2 && <CalendarIcon size={28} className="text-blue-400" />}
                            {tourStep === 3 && <ActivityIcon size={28} className="text-blue-400" />}
                        </div>
                        <h3 className="text-lg font-black text-white uppercase mb-2">
                            {tourStep === 1 ? 'Tu Programa' : tourStep === 2 ? 'Roadmap' : 'Métricas'}
                        </h3>
                        <p className="text-[11px] text-zinc-400 leading-relaxed mb-6 font-medium">
                            {tourStep === 1 ? 'Centro de mando de tu entrenamiento. Ve, inicia y edita todas tus sesiones.'
                                : tourStep === 2 ? 'Usa la card "Estructura" para editar bloques, mesociclos y semanas.'
                                    : 'Revisa fatiga corporal, volumen acumulado y récords en "Métricas".'}
                        </p>
                        <div className="flex justify-between items-center border-t border-white/5 pt-4">
                            <div className="flex gap-2">
                                {[1, 2, 3].map(step => (
                                    <div key={step} className={`w-2 h-2 rounded-full ${tourStep === step ? 'bg-blue-500' : 'bg-white/10'}`} />
                                ))}
                            </div>
                            <button onClick={() => {
                                if (tourStep < 3) setTourStep(prev => prev + 1);
                                else { setTourStep(0); localStorage.setItem(`kpkn_tour_seen_${program.id}`, 'true'); }
                            }} className="bg-white text-black px-5 py-2 rounded-full text-[10px] font-black uppercase tracking-widest hover:scale-105 transition-transform">
                                {tourStep < 3 ? 'Siguiente' : '¡Listo!'}
                            </button>
                        </div>
                        <button onClick={() => { setTourStep(0); localStorage.setItem(`kpkn_tour_seen_${program.id}`, 'true'); }} className="absolute top-3 right-3 p-1.5 text-zinc-600 hover:text-white bg-zinc-900 rounded-full">
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
