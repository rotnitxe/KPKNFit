import React, { useState, useMemo, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program, Loop, LoopType } from '../../types';
import { PlusIcon, TrashIcon, EditIcon, XIcon, ChevronDownIcon } from '../icons';
import {
    projectLoops, detectLoopCollisions, postponeLoop, cancelLoop, reactivateLoop,
    getCycleLength, getCurrentCycle, getLoopTypeEmoji, getLoopTypeLabel, formatLoopCountdown,
    migrateEventsToLoops, LoopProjection,
} from '../../services/loopEngine';
import ProgramTemplatesView from './ProgramTemplatesView';

// ─── Templates para Programas Simples ───
const LOOP_TEMPLATES: { id: string; name: string; emoji: string; desc: string; loops: Omit<Loop, 'id'>[] }[] = [
    {
        id: 'deload-4', name: 'Descarga cada 4', emoji: '🧘',
        desc: 'Semana de descarga automática cada 4 ciclos. Ideal para principiantes e intermedios.',
        loops: [{ title: 'Descarga', type: '1rm_test' as LoopType, repeatEveryXLoops: 4, durationType: 'week' }],
    },
    {
        id: '1rm-8', name: 'Test 1RM cada 8', emoji: '🏋️',
        desc: 'Semana de pruebas de fuerza máxima cada 8 ciclos.',
        loops: [{ title: 'Test 1RM', type: '1rm_test', repeatEveryXLoops: 8, durationType: 'week' }],
    },
    {
        id: 'deload-1rm', name: 'Descarga + Test 1RM', emoji: '⚡',
        desc: 'Descarga cada 4 ciclos y Test 1RM cada 8. Periodización completa.',
        loops: [
            { title: 'Descarga', type: 'deload', repeatEveryXLoops: 4, durationType: 'week' },
            { title: 'Test 1RM', type: '1rm_test', repeatEveryXLoops: 8, durationType: 'week' },
        ],
    },
    {
        id: 'competition-12', name: 'Competición cada 12', emoji: '🏆',
        desc: 'Semana de competición cada 12 ciclos con descarga previa cada 4.',
        loops: [
            { title: 'Descarga', type: 'deload', repeatEveryXLoops: 4, durationType: 'week' },
            { title: 'Competición', type: 'competition', repeatEveryXLoops: 12, durationType: 'week' },
        ],
    },
];

const LOOP_TYPES: { value: LoopType; label: string; emoji: string }[] = [
    { value: '1rm_test', label: 'Test 1RM', emoji: '🏋️' },
    { value: 'deload', label: 'Descarga', emoji: '🧘' },
    { value: 'competition', label: 'Competición', emoji: '🏆' },
    { value: 'custom', label: 'Personalizado', emoji: '⚡' },
];

interface LoopsViewProps {
    program: Program;
    onUpdateProgram: (program: Program) => void;
    addToast: (message: string, type?: 'danger' | 'success' | 'achievement' | 'suggestion', title?: string, duration?: number, why?: string) => void;
}

const LoopsView: React.FC<LoopsViewProps> = ({ program, onUpdateProgram, addToast }) => {
    const [showAddModal, setShowAddModal] = useState(false);
    const [editingLoop, setEditingLoop] = useState<Loop | null>(null);
    const [showTemplates, setShowTemplates] = useState(false);
    const [expandedSequencer, setExpandedSequencer] = useState(true);

    const loops = program.loops || [];
    const cycleLength = getCycleLength(program);
    const currentCycle = getCurrentCycle(program);

    // Proyectar próximas 12 activaciones
    const projections = useMemo(() =>
        projectLoops(program, currentCycle, 12),
        [program, currentCycle]);

    const collisions = useMemo(() =>
        detectLoopCollisions(projections),
        [projections]);

    // ─── Handlers ───
    const handleAddLoop = useCallback((loop: Omit<Loop, 'id'>) => {
        const updated = JSON.parse(JSON.stringify(program)) as Program;
        if (!updated.loops) updated.loops = [];
        updated.loops.push({ ...loop, id: crypto.randomUUID() });
        onUpdateProgram(updated);
        addToast(`Loop "${loop.title}" creado`, 'success');
        setShowAddModal(false);
    }, [program, onUpdateProgram, addToast]);

    const handleUpdateLoop = useCallback((loop: Loop) => {
        const updated = JSON.parse(JSON.stringify(program)) as Program;
        if (!updated.loops) return;
        const idx = updated.loops.findIndex(l => l.id === loop.id);
        if (idx >= 0) updated.loops[idx] = loop;
        onUpdateProgram(updated);
        addToast(`Loop "${loop.title}" actualizado`, 'success');
        setEditingLoop(null);
    }, [program, onUpdateProgram, addToast]);

    const handleDeleteLoop = useCallback((loopId: string) => {
        const updated = JSON.parse(JSON.stringify(program)) as Program;
        updated.loops = (updated.loops || []).filter(l => l.id !== loopId);
        onUpdateProgram(updated);
        addToast('Loop eliminado', 'success');
    }, [program, onUpdateProgram, addToast]);

    const handlePostpone = useCallback((loopId: string, cycle: number) => {
        const updated = postponeLoop(program, loopId, cycle);
        onUpdateProgram(updated);
        addToast('Loop pospuesto al siguiente ciclo', 'suggestion');
    }, [program, onUpdateProgram, addToast]);

    const handleCancel = useCallback((loopId: string) => {
        const updated = cancelLoop(program, loopId);
        onUpdateProgram(updated);
        addToast('Loop cancelado', 'danger');
    }, [program, onUpdateProgram, addToast]);

    const handleReactivate = useCallback((loopId: string) => {
        const updated = reactivateLoop(program, loopId);
        onUpdateProgram(updated);
        addToast('Loop reactivado', 'success');
    }, [program, onUpdateProgram, addToast]);

    const handleApplyTemplate = useCallback((template: typeof LOOP_TEMPLATES[0]) => {
        const updated = JSON.parse(JSON.stringify(program)) as Program;
        if (!updated.loops) updated.loops = [];
        for (const loopDef of template.loops) {
            updated.loops.push({ ...loopDef, id: crypto.randomUUID() });
        }
        onUpdateProgram(updated);
        addToast(`Template "${template.name}" aplicado`, 'success');
        setShowTemplates(false);
    }, [program, onUpdateProgram, addToast]);

    const cancelledSet = new Set(program.loopState?.cancelled || []);
    const legacyEvents = (program.events || []).filter(e => e.repeatEveryXCycles);
    const hasLegacyEvents = legacyEvents.length > 0 && loops.length === 0;

    const handleMigrateLegacy = useCallback(() => {
        const updated = migrateEventsToLoops(program);
        onUpdateProgram(updated);
        addToast(`${legacyEvents.length} eventos migrados a Loops`, 'success');
    }, [program, onUpdateProgram, addToast, legacyEvents.length]);

    return (
        <div className="px-4 pb-6 space-y-4">
            {/* ── Legacy Migration Banner ── */}
            {hasLegacyEvents && (
                <motion.div
                    initial={{ opacity: 0, y: -10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="bg-amber-50 border border-amber-200 rounded-2xl p-4"
                >
                    <p className="text-[10px] font-black text-amber-800 uppercase tracking-wider">
                        ⚡ {legacyEvents.length} evento{legacyEvents.length > 1 ? 's' : ''} detectado{legacyEvents.length > 1 ? 's' : ''}
                    </p>
                    <p className="text-[9px] text-amber-700 mt-1">
                        Tu programa tiene eventos configurados en el formato anterior. Migra al nuevo sistema de Loops para acceder a todas las funciones.
                    </p>
                    <button
                        onClick={handleMigrateLegacy}
                        className="mt-3 px-4 py-2 rounded-xl bg-amber-500 text-white text-[9px] font-bold uppercase tracking-wider hover:bg-amber-600 transition-colors"
                    >
                        Migrar a Loops
                    </button>
                </motion.div>
            )}
            {/* ── Header ── */}
            <div className="flex items-center justify-between">
                <div>
                    <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest">
                        🔄 Loops
                    </h3>
                    <p className="text-[9px] text-zinc-500 uppercase tracking-wider mt-0.5">
                        Ciclo {currentCycle} · {cycleLength} {cycleLength === 1 ? 'semana' : 'semanas'} por ciclo
                    </p>
                </div>
                <div className="flex gap-2">
                    <button
                        onClick={() => setShowTemplates(true)}
                        className="px-3 py-2 rounded-xl bg-zinc-100 text-zinc-600 text-[9px] font-bold uppercase tracking-wider hover:bg-zinc-200 transition-colors"
                    >
                        📋 Templates
                    </button>
                    <button
                        onClick={() => setShowAddModal(true)}
                        className="px-3 py-2 rounded-xl bg-zinc-900 text-white text-[9px] font-bold uppercase tracking-wider hover:bg-zinc-800 transition-colors flex items-center gap-1"
                    >
                        <PlusIcon size={10} /> Nuevo
                    </button>
                </div>
            </div>

            {/* ── Loop Sequencer Visual ── */}
            {loops.length > 0 && (
                <div className="bg-white rounded-2xl border border-zinc-200 overflow-hidden">
                    <button
                        onClick={() => setExpandedSequencer(!expandedSequencer)}
                        className="w-full flex items-center justify-between px-4 py-3"
                    >
                        <span className="text-[10px] font-black text-zinc-900 uppercase tracking-widest">
                            Sequencer · Próximos 12 ciclos
                        </span>
                        <ChevronDownIcon size={14} className={`text-zinc-400 transition-transform ${expandedSequencer ? 'rotate-180' : ''}`} />
                    </button>

                    <AnimatePresence>
                        {expandedSequencer && (
                            <motion.div
                                initial={{ height: 0, opacity: 0 }}
                                animate={{ height: 'auto', opacity: 1 }}
                                exit={{ height: 0, opacity: 0 }}
                                transition={{ duration: 0.2 }}
                                className="overflow-hidden"
                            >
                                <div className="px-4 pb-4">
                                    <div className="flex gap-1 overflow-x-auto pb-2" style={{ scrollbarWidth: 'none' }}>
                                        {Array.from({ length: 12 }, (_, i) => currentCycle + i).map((cycle) => {
                                            const cycleProjections = projections.filter(p => p.cycle === cycle);
                                            const hasCollision = collisions.has(cycle);
                                            const isNow = cycle === currentCycle;

                                            return (
                                                <div
                                                    key={cycle}
                                                    className={`flex-shrink-0 w-14 rounded-xl border-2 p-1.5 text-center transition-all ${
                                                        isNow
                                                            ? 'border-purple-400 bg-purple-50'
                                                            : hasCollision
                                                            ? 'border-amber-300 bg-amber-50'
                                                            : cycleProjections.length > 0
                                                            ? 'border-emerald-300 bg-emerald-50'
                                                            : 'border-zinc-200 bg-zinc-50'
                                                    }`}
                                                >
                                                    <div className={`text-[8px] font-black uppercase tracking-wider ${isNow ? 'text-purple-600' : 'text-zinc-400'}`}>
                                                        C{cycle}
                                                    </div>
                                                    <div className="flex flex-col items-center gap-0.5 mt-1 min-h-[20px]">
                                                        {cycleProjections.map((p, idx) => (
                                                            <span key={idx} className="text-[10px] leading-none" title={p.loop.title}>
                                                                {getLoopTypeEmoji(p.loop.type)}
                                                            </span>
                                                        ))}
                                                        {hasCollision && (
                                                            <span className="text-[7px] text-amber-600 font-bold">⚠️</span>
                                                        )}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>

                                    {/* Leyenda */}
                                    <div className="flex gap-3 mt-2">
                                        {loops.map(loop => (
                                            <div key={loop.id} className="flex items-center gap-1">
                                                <span className="text-[10px]">{getLoopTypeEmoji(loop.type)}</span>
                                                <span className="text-[8px] text-zinc-500 font-bold uppercase">{loop.title}</span>
                                                <span className="text-[7px] text-zinc-400">c/{loop.repeatEveryXLoops}</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </motion.div>
                        )}
                    </AnimatePresence>
                </div>
            )}

            {/* ── Lista de Loops ── */}
            {loops.length === 0 ? (
                <div className="bg-white rounded-2xl border border-dashed border-zinc-300 p-8 text-center">
                    <div className="text-3xl mb-3">🔄</div>
                    <h4 className="text-sm font-black text-zinc-900 uppercase tracking-widest mb-2">
                        Sin Loops
                    </h4>
                    <p className="text-[10px] text-zinc-500 leading-relaxed max-w-xs mx-auto mb-4">
                        Los Loops son semanas especiales que se activan automáticamente cada X ciclos.
                        Ideal para descargas, tests de fuerza o competiciones.
                    </p>
                    <button
                        onClick={() => setShowTemplates(true)}
                        className="px-4 py-2.5 rounded-xl bg-purple-600 text-white text-[9px] font-black uppercase tracking-widest hover:bg-purple-700 transition-colors"
                    >
                        Elegir Template
                    </button>
                </div>
            ) : (
                <div className="space-y-2">
                    {loops.map(loop => {
                        const isCancelled = cancelledSet.has(loop.id);
                        const nextActivation = projections.find(p => p.loop.id === loop.id && !p.isCancelled);

                        return (
                            <motion.div
                                key={loop.id}
                                layout
                                className={`bg-white rounded-2xl border p-4 transition-all ${
                                    isCancelled ? 'border-red-200 opacity-50' : 'border-zinc-200'
                                }`}
                            >
                                <div className="flex items-start justify-between">
                                    <div className="flex items-center gap-3">
                                        <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-lg ${
                                            isCancelled ? 'bg-zinc-100' : 'bg-gradient-to-br from-purple-100 to-purple-200'
                                        }`}>
                                            {getLoopTypeEmoji(loop.type)}
                                        </div>
                                        <div>
                                            <h4 className="text-xs font-black text-zinc-900 uppercase tracking-wider">
                                                {loop.title}
                                            </h4>
                                            <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-wider">
                                                Cada {loop.repeatEveryXLoops} {loop.repeatEveryXLoops === 1 ? 'ciclo' : 'ciclos'}
                                                {loop.durationType === 'week' ? ` · ${loop.durationWeeks || 1} sem` : ` · 1 día`}
                                            </p>
                                        </div>
                                    </div>

                                    <div className="flex items-center gap-1">
                                        {!isCancelled && nextActivation && (
                                            <span className="text-[8px] font-bold text-emerald-600 bg-emerald-50 px-2 py-1 rounded-lg">
                                                C{nextActivation.cycle}
                                            </span>
                                        )}
                                        {isCancelled && (
                                            <span className="text-[8px] font-bold text-red-500 bg-red-50 px-2 py-1 rounded-lg">
                                                Cancelado
                                            </span>
                                        )}
                                    </div>
                                </div>

                                {/* Actions */}
                                <div className="flex items-center gap-2 mt-3 pt-3 border-t border-zinc-100">
                                    <button
                                        onClick={() => setEditingLoop(loop)}
                                        className="flex-1 py-2 rounded-lg bg-zinc-50 text-zinc-600 text-[8px] font-bold uppercase tracking-wider hover:bg-zinc-100 transition-colors flex items-center justify-center gap-1"
                                    >
                                        <EditIcon size={10} /> Editar
                                    </button>
                                    {!isCancelled && nextActivation && (
                                        <button
                                            onClick={() => handlePostpone(loop.id, nextActivation.cycle)}
                                            className="flex-1 py-2 rounded-lg bg-amber-50 text-amber-700 text-[8px] font-bold uppercase tracking-wider hover:bg-amber-100 transition-colors"
                                        >
                                            ⏭ Posponer
                                        </button>
                                    )}
                                    {isCancelled ? (
                                        <button
                                            onClick={() => handleReactivate(loop.id)}
                                            className="flex-1 py-2 rounded-lg bg-emerald-50 text-emerald-700 text-[8px] font-bold uppercase tracking-wider hover:bg-emerald-100 transition-colors"
                                        >
                                            ✅ Reactivar
                                        </button>
                                    ) : (
                                        <button
                                            onClick={() => handleCancel(loop.id)}
                                            className="flex-1 py-2 rounded-lg bg-red-50 text-red-600 text-[8px] font-bold uppercase tracking-wider hover:bg-red-100 transition-colors"
                                        >
                                            ✕ Cancelar
                                        </button>
                                    )}
                                    <button
                                        onClick={() => handleDeleteLoop(loop.id)}
                                        className="w-8 h-8 rounded-lg bg-zinc-50 text-zinc-400 flex items-center justify-center hover:bg-red-50 hover:text-red-500 transition-colors"
                                    >
                                        <TrashIcon size={12} />
                                    </button>
                                </div>
                            </motion.div>
                        );
                    })}
                </div>
            )}

            {/* ── Program Templates Section ── */}
            <div className="bg-gradient-to-b from-purple-50 to-white rounded-2xl border border-purple-200 p-4">
                <div className="flex items-center justify-between mb-3">
                    <div>
                        <h3 className="text-[10px] font-black text-purple-900 uppercase tracking-widest">
                            📦 Templates de Programa
                        </h3>
                        <p className="text-[8px] text-purple-600 mt-0.5">
                            Estructura completa: semanas + loops preconfigurados
                        </p>
                    </div>
                </div>
                <ProgramTemplatesView
                    program={program}
                    onUpdateProgram={onUpdateProgram}
                    addToast={addToast}
                />
            </div>

            {/* ── Modal: Templates ── */}
            <AnimatePresence>
                {showTemplates && (
                    <div className="fixed inset-0 z-[300] bg-black/40 backdrop-blur-sm flex items-end justify-center" onClick={() => setShowTemplates(false)}>
                        <motion.div
                            initial={{ y: '100%' }}
                            animate={{ y: 0 }}
                            exit={{ y: '100%' }}
                            transition={{ type: 'spring', damping: 28, stiffness: 300 }}
                            className="bg-white rounded-t-[2rem] w-full max-w-md p-6 pb-10 max-h-[80vh] overflow-y-auto"
                            onClick={e => e.stopPropagation()}
                        >
                            <div className="flex items-center justify-between mb-6">
                                <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest">
                                    📋 Templates de Loops
                                </h3>
                                <button onClick={() => setShowTemplates(false)} className="text-zinc-400 hover:text-zinc-600">
                                    <XIcon size={16} />
                                </button>
                            </div>

                            <div className="space-y-3">
                                {LOOP_TEMPLATES.map(template => (
                                    <button
                                        key={template.id}
                                        onClick={() => handleApplyTemplate(template)}
                                        className="w-full text-left bg-zinc-50 hover:bg-purple-50 rounded-2xl p-4 border border-zinc-200 hover:border-purple-300 transition-all"
                                    >
                                        <div className="flex items-center gap-3 mb-2">
                                            <span className="text-2xl">{template.emoji}</span>
                                            <div>
                                                <h4 className="text-xs font-black text-zinc-900 uppercase tracking-wider">{template.name}</h4>
                                                <p className="text-[9px] text-zinc-500">{template.desc}</p>
                                            </div>
                                        </div>
                                        <div className="flex gap-2 mt-2">
                                            {template.loops.map((l, i) => (
                                                <span key={i} className="text-[8px] bg-white px-2 py-1 rounded-lg border border-zinc-200 font-bold text-zinc-600">
                                                    {getLoopTypeEmoji(l.type as LoopType)} {l.title} c/{l.repeatEveryXLoops}
                                                </span>
                                            ))}
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </motion.div>
                    </div>
                )}
            </AnimatePresence>

            {/* ── Modal: Add/Edit Loop ── */}
            <AnimatePresence>
                {(showAddModal || editingLoop) && (
                    <LoopFormModal
                        loop={editingLoop}
                        onSave={(loop) => {
                            if (editingLoop) {
                                handleUpdateLoop({ ...editingLoop, ...loop, id: editingLoop.id });
                            } else {
                                handleAddLoop(loop);
                            }
                        }}
                        onClose={() => { setShowAddModal(false); setEditingLoop(null); }}
                    />
                )}
            </AnimatePresence>
        </div>
    );
};

// ─── Loop Form Modal ───

interface LoopFormModalProps {
    loop?: Loop | null;
    onSave: (loop: Omit<Loop, 'id'>) => void;
    onClose: () => void;
}

const LoopFormModal: React.FC<LoopFormModalProps> = ({ loop, onSave, onClose }) => {
    const [title, setTitle] = useState(loop?.title || '');
    const [type, setType] = useState<LoopType>(loop?.type || 'custom');
    const [repeatEvery, setRepeatEvery] = useState(loop?.repeatEveryXLoops || 4);
    const [durationType, setDurationType] = useState<'day' | 'week'>(loop?.durationType || 'week');
    const [durationWeeks, setDurationWeeks] = useState(loop?.durationWeeks || 1);
    const [priority, setPriority] = useState(loop?.priority || 0);

    const handleSubmit = () => {
        if (!title.trim()) return;
        onSave({
            title: title.trim(),
            type,
            repeatEveryXLoops: repeatEvery,
            durationType,
            durationWeeks: durationType === 'week' ? durationWeeks : undefined,
            priority,
        });
    };

    return (
        <div className="fixed inset-0 z-[300] bg-black/40 backdrop-blur-sm flex items-center justify-center p-4" onClick={onClose}>
            <motion.div
                initial={{ scale: 0.95, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                exit={{ scale: 0.95, opacity: 0 }}
                className="bg-white rounded-[2rem] w-full max-w-sm p-6 shadow-2xl"
                onClick={e => e.stopPropagation()}
            >
                <div className="flex items-center justify-between mb-6">
                    <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest">
                        {loop ? 'Editar Loop' : 'Nuevo Loop'}
                    </h3>
                    <button onClick={onClose} className="text-zinc-400 hover:text-zinc-600">
                        <XIcon size={16} />
                    </button>
                </div>

                <div className="space-y-4">
                    {/* Nombre */}
                    <div>
                        <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block mb-1">Nombre</label>
                        <input
                            value={title}
                            onChange={e => setTitle(e.target.value)}
                            placeholder="Ej: Descarga, Test 1RM..."
                            className="w-full px-3 py-2.5 rounded-xl border border-zinc-200 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400"
                        />
                    </div>

                    {/* Tipo */}
                    <div>
                        <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block mb-1">Tipo</label>
                        <div className="grid grid-cols-4 gap-1.5">
                            {LOOP_TYPES.map(lt => (
                                <button
                                    key={lt.value}
                                    onClick={() => { setType(lt.value); if (!title) setTitle(lt.label); }}
                                    className={`py-2 rounded-xl text-center transition-all ${
                                        type === lt.value
                                            ? 'bg-purple-100 border-2 border-purple-400 text-purple-700'
                                            : 'bg-zinc-50 border-2 border-transparent text-zinc-600 hover:bg-zinc-100'
                                    }`}
                                >
                                    <div className="text-lg">{lt.emoji}</div>
                                    <div className="text-[7px] font-bold uppercase tracking-wider mt-0.5">{lt.label}</div>
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Frecuencia */}
                    <div>
                        <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block mb-1">
                            Cada cuántos ciclos
                        </label>
                        <div className="flex items-center gap-2">
                            <span className="text-[10px] text-zinc-500 font-bold">Cada</span>
                            <input
                                type="number"
                                min={1}
                                max={99}
                                value={repeatEvery}
                                onChange={e => setRepeatEvery(Math.max(1, parseInt(e.target.value) || 1))}
                                className="w-16 px-3 py-2 rounded-xl border border-zinc-200 text-sm text-center font-bold focus:outline-none focus:ring-2 focus:ring-purple-400"
                            />
                            <span className="text-[10px] text-zinc-500 font-bold">ciclos</span>
                        </div>
                    </div>

                    {/* Duración */}
                    <div>
                        <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block mb-1">Duración</label>
                        <div className="flex gap-2">
                            <button
                                onClick={() => setDurationType('week')}
                                className={`flex-1 py-2.5 rounded-xl text-[9px] font-bold uppercase tracking-wider transition-all ${
                                    durationType === 'week' ? 'bg-zinc-900 text-white' : 'bg-zinc-100 text-zinc-500'
                                }`}
                            >
                                Semana completa
                            </button>
                            <button
                                onClick={() => setDurationType('day')}
                                className={`flex-1 py-2.5 rounded-xl text-[9px] font-bold uppercase tracking-wider transition-all ${
                                    durationType === 'day' ? 'bg-zinc-900 text-white' : 'bg-zinc-100 text-zinc-500'
                                }`}
                            >
                                Solo 1 día
                            </button>
                        </div>
                        {durationType === 'week' && (
                            <div className="flex items-center gap-2 mt-2">
                                <input
                                    type="number"
                                    min={1}
                                    max={4}
                                    value={durationWeeks}
                                    onChange={e => setDurationWeeks(Math.max(1, parseInt(e.target.value) || 1))}
                                    className="w-16 px-3 py-2 rounded-xl border border-zinc-200 text-sm text-center font-bold focus:outline-none focus:ring-2 focus:ring-purple-400"
                                />
                                <span className="text-[9px] text-zinc-500 font-bold uppercase">{durationWeeks === 1 ? 'semana' : 'semanas'}</span>
                            </div>
                        )}
                    </div>

                    {/* Prioridad */}
                    <div>
                        <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block mb-1">
                            Prioridad (en colisiones)
                        </label>
                        <div className="flex gap-1.5">
                            {[0, 1, 2].map(p => (
                                <button
                                    key={p}
                                    onClick={() => setPriority(p)}
                                    className={`flex-1 py-2 rounded-xl text-[9px] font-bold uppercase transition-all ${
                                        priority === p ? 'bg-zinc-900 text-white' : 'bg-zinc-100 text-zinc-500'
                                    }`}
                                >
                                    {p === 0 ? 'Normal' : p === 1 ? 'Alta' : 'Máxima'}
                                </button>
                            ))}
                        </div>
                    </div>
                </div>

                {/* Actions */}
                <div className="flex gap-2 mt-6">
                    <button
                        onClick={onClose}
                        className="flex-1 py-3 rounded-xl bg-zinc-100 text-zinc-600 text-[10px] font-black uppercase tracking-widest hover:bg-zinc-200 transition-colors"
                    >
                        Cancelar
                    </button>
                    <button
                        onClick={handleSubmit}
                        disabled={!title.trim()}
                        className="flex-1 py-3 rounded-xl bg-zinc-900 text-white text-[10px] font-black uppercase tracking-widest hover:bg-zinc-800 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {loop ? 'Guardar' : 'Crear Loop'}
                    </button>
                </div>
            </motion.div>
        </div>
    );
};

export default LoopsView;
