import React, { useState, useMemo } from 'react';
import { Program, ProgramWeek, Block, Mesocycle } from '../../types';
import {
    XIcon, PlusIcon, ChevronDownIcon, CalendarIcon,
    AlertTriangleIcon, EditIcon, TrashIcon, GridIcon
} from '../icons';
import { getAbsoluteWeekIndex, checkWeekHasEvent } from '../../utils/programHelpers';
import StructureGalleryDrawer from './StructureGalleryDrawer';

interface StructureDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    program: Program;
    isCyclic: boolean;
    selectedBlockId: string | null;
    selectedWeekId: string | null;
    onSelectBlock: (id: string) => void;
    onSelectWeek: (id: string) => void;
    onUpdateProgram?: (program: Program) => void;
    onEditWeek: (info: {
        macroIndex: number; blockIndex: number; mesoIndex: number;
        weekIndex: number; week: ProgramWeek; isSimple: boolean;
    }) => void;
    onShowAdvancedTransition: () => void;
    onShowSimpleTransition: () => void;
    onOpenEventModal: (data?: any) => void;
    inline?: boolean;
}

const GOAL_OPTIONS = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'];
const BLOCK_COLORS = ['#3B82F6', '#A855F7', '#EAB308', '#10B981', '#F43F5E', '#06B6D4', '#EC4899'];

const StructureDrawer: React.FC<StructureDrawerProps> = ({
    isOpen, onClose, program, isCyclic, selectedBlockId, selectedWeekId,
    onSelectBlock, onSelectWeek, onUpdateProgram, onEditWeek,
    onShowAdvancedTransition, onShowSimpleTransition, onOpenEventModal,
    inline = false,
}) => {
    const [isGalleryOpen, setIsGalleryOpen] = useState(false);
    const [showAddBlockConfirm, setShowAddBlockConfirm] = useState(false);
    const [pendingAddBlockMacroIdx, setPendingAddBlockMacroIdx] = useState<number | null>(null);

    const blockLabel = program.blockLabel || 'bloque';
    const blockCount = program.macrocycles.reduce((acc, m) => acc + (m.blocks || []).length, 0);

    const update = (fn: (p: Program) => void) => {
        if (!onUpdateProgram) return;
        const clone = JSON.parse(JSON.stringify(program));
        fn(clone);
        onUpdateProgram(clone);
    };

    const handleApplyTemplate = (template: any) => {
        update(p => {
            p.macrocycles = template.macrocycles.map((m: any) => ({
                id: crypto.randomUUID(),
                name: m.name,
                blocks: template.blocks.map((b: any) => ({
                    id: crypto.randomUUID(),
                    name: b.name,
                    mesocycles: b.mesocycles.map((meso: any) => ({
                        id: crypto.randomUUID(),
                        name: meso.name,
                        goal: meso.goal,
                        weeks: Array.from({ length: meso.weeksCount }).map((_, i) => ({
                            id: crypto.randomUUID(),
                            name: `Semana ${i + 1}`,
                            sessions: []
                        }))
                    }))
                }))
            }));
        });
    };

    const totalWeeks = useMemo(() => program.macrocycles.reduce((acc, m) =>
        acc + (m.blocks || []).reduce((ba, b) =>
            ba + b.mesocycles.reduce((ma, me) => ma + me.weeks.length, 0), 0), 0), [program]);

    if (!isOpen) return null;

    const containerStyle = inline
        ? "flex-1 flex flex-col p-4 pt-2"
        : "fixed inset-0 z-[110] bg-black/95 overflow-y-auto p-6";

    return (
        <div className={containerStyle}>
            {/* ── Toolbar Superior ── */}
            <div className="flex items-center justify-between mb-8 gap-4 px-2">
                <div className="flex flex-col gap-1">
                    <h2 className="text-sm font-black uppercase tracking-widest text-[#555] opacity-50">Estructura</h2>
                    <div className="flex items-center p-1 bg-white/5 border border-white/10 rounded-xl">
                        <button
                            onClick={() => update(p => { p.blockLabel = 'bloque'; })}
                            className={`px-3 py-1.5 text-[9px] font-black uppercase tracking-widest rounded-lg transition-all ${blockLabel === 'bloque' ? 'bg-white text-black shadow-lg' : 'text-zinc-500'}`}
                        >
                            Bloques
                        </button>
                        <button
                            onClick={() => update(p => { p.blockLabel = 'mesociclo'; })}
                            className={`px-3 py-1.5 text-[9px] font-black uppercase tracking-widest rounded-lg transition-all ${blockLabel === 'mesociclo' ? 'bg-white text-black shadow-lg' : 'text-zinc-500'}`}
                        >
                            Mesociclos
                        </button>
                    </div>
                </div>

                <div className="flex items-center gap-2">
                    <button
                        onClick={() => setIsGalleryOpen(true)}
                        className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] text-[10px] font-black uppercase tracking-widest hover:brightness-110 active:scale-95 transition-all shadow-lg"
                    >
                        <GridIcon size={14} /> Plantillas
                    </button>
                    {!inline && (
                        <button onClick={onClose} className="w-10 h-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center text-white">
                            <XIcon size={20} />
                        </button>
                    )}
                </div>
            </div>

            {/* ── Listado de Macrociclos (usualmente 1) ── */}
            <div className="flex flex-col gap-10 pb-40">
                {program.macrocycles.map((macro, macroIdx) => (
                    <div key={macro.id} className="flex flex-col gap-4">
                        {/* Macrocycle Header */}
                        <div className="flex items-center gap-3 px-4 py-1">
                            <h3 className="text-title-sm font-black uppercase tracking-[0.2em] text-cyan-400">{macro.name}</h3>
                            <div className="h-px flex-1 bg-gradient-to-r from-cyan-400/30 to-transparent" />
                            <span className="text-[10px] font-black text-zinc-600 uppercase tracking-widest">{totalWeeks} Semanas totales</span>
                        </div>

                        {/* Blocks as Cards */}
                        <div className="flex flex-col gap-6">
                            {(macro.blocks || []).map((block, blockIdx) => {
                                const color = BLOCK_COLORS[(macroIdx * 10 + blockIdx) % BLOCK_COLORS.length];
                                const isSelected = block.id === selectedBlockId;
                                const blockWeeks = block.mesocycles.reduce((a, m) => a + m.weeks.length, 0);

                                return (
                                    <div
                                        key={block.id}
                                        className={`group relative flex flex-col rounded-3xl border transition-all duration-300 shadow-xl overflow-hidden ${isSelected ? 'border-[var(--md-sys-color-primary)]/50 bg-[var(--md-sys-color-surface-container-high)] ring-4 ring-[var(--md-sys-color-primary)]/10' : 'border-white/10 bg-[var(--md-sys-color-surface-container)] hover:border-white/20'}`}
                                    >
                                        {/* Left color bar */}
                                        <div className="absolute left-0 top-0 bottom-0 w-1.5" style={{ backgroundColor: color }} />

                                        {/* Block Card Header */}
                                        <div className="p-5 pb-3 flex items-start justify-between gap-4">
                                            <div className="flex-1 min-w-0">
                                                <input
                                                    type="text"
                                                    value={block.name}
                                                    onChange={(e) => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].name = e.target.value; })}
                                                    className="bg-transparent text-xl font-black uppercase tracking-tight text-white w-full border-none p-0 focus:ring-0 outline-none placeholder:opacity-20"
                                                    placeholder="Sin nombre"
                                                />
                                                <div className="flex items-center gap-3 mt-1.5">
                                                    <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">{blockWeeks} SEMANAS</span>
                                                    <span className="text-[10px] font-black uppercase tracking-widest text-[#555] select-none">ID: {block.id.slice(0, 4)}</span>
                                                </div>
                                            </div>
                                            <div className="flex items-center gap-1 opacity-40 group-hover:opacity-100 transition-opacity">
                                                <button
                                                    onClick={() => update(p => {
                                                        const target = p.macrocycles[macroIdx].blocks;
                                                        if (target) {
                                                            const idx = target.findIndex(b => b.id === block.id);
                                                            target.splice(idx, 1);
                                                        }
                                                    })}
                                                    className="w-9 h-9 rounded-xl flex items-center justify-center text-error hover:bg-error/10 transition-colors"
                                                >
                                                    <TrashIcon size={16} />
                                                </button>
                                            </div>
                                        </div>

                                        {/* Mesocycles / Phases List */}
                                        <div className="px-5 pb-6 flex flex-col gap-4 mt-2">
                                            {block.mesocycles.map((meso, mesoIdx) => {
                                                const filledWeeks = meso.weeks.filter(w => w.sessions.length > 0).length;
                                                const pctFilled = (filledWeeks / meso.weeks.length) * 100;

                                                return (
                                                    <div key={meso.id} className="relative pl-6 border-l-2 border-white/5 py-1">
                                                        <div className="flex items-center justify-between gap-4 mb-2">
                                                            <div className="flex-1 min-w-0 flex items-center gap-3">
                                                                <input
                                                                    type="text"
                                                                    value={meso.name}
                                                                    onChange={(e) => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx].name = e.target.value; })}
                                                                    className="bg-transparent text-xs font-black uppercase tracking-wider text-zinc-300 w-full border-none p-0 focus:ring-0 outline-none"
                                                                />
                                                                <select
                                                                    value={meso.goal}
                                                                    onChange={(e) => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx].goal = e.target.value as any; })}
                                                                    className="bg-white/5 border-none text-[9px] font-black uppercase tracking-widest text-cyan-400 rounded-lg px-2 py-1 focus:ring-0 outline-none ring-1 ring-white/10"
                                                                >
                                                                    {GOAL_OPTIONS.map(g => <option key={g} value={g} className="bg-zinc-900">{g}</option>)}
                                                                </select>
                                                            </div>
                                                            <button
                                                                onClick={() => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles.splice(mesoIdx, 1); })}
                                                                className="text-zinc-700 hover:text-error transition-colors p-1"
                                                            >
                                                                <TrashIcon size={12} />
                                                            </button>
                                                        </div>

                                                        {/* Week Management & Markers */}
                                                        <div className="flex flex-wrap items-center gap-2">
                                                            {meso.weeks.map((week, weekIdx) => {
                                                                const absIdx = getAbsoluteWeekIndex(program, block.id, week.id);
                                                                const hasEvent = checkWeekHasEvent(program, absIdx);
                                                                const isSelectedWeek = week.id === selectedWeekId;

                                                                return (
                                                                    <div key={week.id} className="relative group/week">
                                                                        <button
                                                                            onClick={() => { onSelectBlock(block.id); onSelectWeek(week.id); }}
                                                                            className={`px-3 py-1.5 rounded-lg border text-[10px] font-black uppercase tracking-widest transition-all ${isSelectedWeek ? 'bg-white text-black border-transparent shadow-lg' : 'bg-white/5 border-white/10 text-zinc-500 hover:text-white hover:bg-white/10'}`}
                                                                        >
                                                                            W{weekIdx + 1}
                                                                            {hasEvent && <CalendarIcon size={10} className="inline ml-1 text-cyan-400" />}
                                                                        </button>
                                                                        {/* Remove week button on hover */}
                                                                        {meso.weeks.length > 1 && (
                                                                            <button
                                                                                onClick={(e) => { e.stopPropagation(); update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx].weeks.splice(weekIdx, 1); }); }}
                                                                                className="absolute -top-1.5 -right-1.5 w-4 h-4 rounded-full bg-error text-white opacity-0 group-hover/week:opacity-100 flex items-center justify-center transition-all scale-75 hover:scale-100"
                                                                            >
                                                                                <XIcon size={8} />
                                                                            </button>
                                                                        )}
                                                                    </div>
                                                                );
                                                            })}
                                                            <button
                                                                onClick={() => update(p => {
                                                                    const m = p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx];
                                                                    m.weeks.push({ id: crypto.randomUUID(), name: `Semana ${m.weeks.length + 1}`, sessions: [] });
                                                                })}
                                                                className="w-10 h-8 rounded-lg border border-dashed border-white/20 flex items-center justify-center text-zinc-600 hover:text-white hover:border-white/50 transition-all"
                                                            >
                                                                <PlusIcon size={12} />
                                                            </button>
                                                        </div>

                                                        {/* Phase Progress Bar */}
                                                        <div className="mt-4 h-1 w-full bg-white/5 rounded-full overflow-hidden">
                                                            <div
                                                                className="h-full bg-cyan-400 transition-all duration-500 rounded-full shadow-[0_0_8px_rgba(34,211,238,0.3)]"
                                                                style={{ width: `${pctFilled}%` }}
                                                            />
                                                        </div>
                                                    </div>
                                                );
                                            })}

                                            <button
                                                onClick={() => update(p => {
                                                    p.macrocycles[macroIdx].blocks![blockIdx].mesocycles.push({
                                                        id: crypto.randomUUID(),
                                                        name: 'Nueva Fase',
                                                        goal: 'Custom',
                                                        weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }]
                                                    });
                                                })}
                                                className="w-full py-3 rounded-2xl border-2 border-dashed border-white/10 text-[9px] font-black uppercase tracking-widest text-zinc-600 hover:bg-white/5 hover:text-zinc-400 transition-all flex items-center justify-center gap-2"
                                            >
                                                <PlusIcon size={12} /> Añadir Fase de {blockLabel}
                                            </button>
                                        </div>
                                    </div>
                                );
                            })}

                            <button
                                onClick={() => {
                                    if (isCyclic && blockCount === 1) {
                                        setPendingAddBlockMacroIdx(macroIdx);
                                        setShowAddBlockConfirm(true);
                                    } else {
                                        update(p => {
                                            p.macrocycles[macroIdx].blocks!.push({
                                                id: crypto.randomUUID(),
                                                name: 'Nuevo Bloque',
                                                mesocycles: [{
                                                    id: crypto.randomUUID(),
                                                    name: 'Base',
                                                    goal: 'Acumulación',
                                                    weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }]
                                                }]
                                            });
                                        });
                                    }
                                }}
                                className="w-full py-6 rounded-[2rem] border-4 border-dashed border-white/5 group hover:bg-white/5 hover:border-white/10 transition-all flex flex-col items-center justify-center gap-2"
                            >
                                <div className="w-10 h-10 rounded-full bg-white/5 flex items-center justify-center text-zinc-600 group-hover:text-white transition-all">
                                    <PlusIcon size={20} />
                                </div>
                                <span className="text-[10px] font-black uppercase tracking-[0.3em] text-zinc-600 group-hover:text-zinc-400">Crear {blockLabel}</span>
                            </button>
                        </div>
                    </div>
                ))}
            </div>

            {/* ── Secciones Secundarias ── */}
            <div className="flex flex-col gap-12 mt-12 px-2 border-t border-white/10 pt-12">
                {/* Events Section */}
                <div>
                    <div className="flex items-center justify-between mb-4">
                        <div className="flex flex-col gap-1">
                            <h3 className="text-sm font-black uppercase tracking-widest text-[#555]">Eventos & Hitos</h3>
                            <p className="text-[10px] text-zinc-600">Competiciones, evaluaciones y fechas clave</p>
                        </div>
                        <button onClick={() => onOpenEventModal()} className="px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-[10px] font-black uppercase tracking-widest text-white hover:bg-white/10 transition-all">
                            + Añadir Evento
                        </button>
                    </div>
                    {program.events?.length ? (
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            {program.events.map(ev => (
                                <div
                                    key={ev.id}
                                    onClick={() => onOpenEventModal(ev)}
                                    className="p-4 rounded-2xl bg-white/5 border border-white/10 hover:border-cyan-400/50 transition-all flex items-center justify-between cursor-pointer group"
                                >
                                    <div className="flex items-center gap-3">
                                        <div className="w-8 h-8 rounded-full bg-cyan-400/20 flex items-center justify-center text-cyan-400">
                                            <CalendarIcon size={14} />
                                        </div>
                                        <div>
                                            <p className="text-[11px] font-black uppercase text-white">{ev.title}</p>
                                            <p className="text-[9px] text-zinc-600 uppercase tracking-widest">{ev.type} · Semana {(ev.calculatedWeek || 0) + 1}</p>
                                        </div>
                                    </div>
                                    <ChevronDownIcon size={12} className="-rotate-90 text-zinc-600 group-hover:text-white transition-colors" />
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="py-12 text-center rounded-3xl bg-white/5 border border-dashed border-white/10">
                            <CalendarIcon size={24} className="mx-auto text-zinc-700 mb-2" />
                            <p className="text-[10px] font-black uppercase tracking-widest text-zinc-700">No hay eventos programados</p>
                        </div>
                    )}
                </div>

                {/* Conversion Section */}
                <div className="flex items-center justify-between p-6 rounded-[2rem] bg-gradient-to-br from-amber-400/5 to-transparent border border-amber-400/10 mb-20">
                    <div className="flex-1">
                        <h3 className="text-xs font-black uppercase tracking-widest text-amber-200">¿Cambiar modo de programa?</h3>
                        <p className="text-[10px] text-amber-200/50 mt-1 max-w-sm">Los programas simples son cíclicos; los avanzados permiten estructuras asimétricas y periodización compleja.</p>
                    </div>
                    {isCyclic ? (
                        <button onClick={onShowAdvancedTransition} className="px-5 py-3 rounded-xl bg-amber-400 text-black text-[10px] font-black uppercase tracking-widest shadow-lg active:scale-95 transition-all">
                            Pasar a Avanzado
                        </button>
                    ) : (
                        <button onClick={onShowSimpleTransition} className="px-5 py-3 rounded-xl bg-white/5 border border-white/10 text-[10px] font-black uppercase tracking-widest text-zinc-500 hover:text-white transition-all">
                            Simplificar
                        </button>
                    )}
                </div>
            </div>

            {/* Gallery Overlay */}
            <StructureGalleryDrawer
                isOpen={isGalleryOpen}
                onClose={() => setIsGalleryOpen(false)}
                program={program}
                onApply={handleApplyTemplate}
            />

            {/* Add block confirmation modal */}
            {showAddBlockConfirm && (
                <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fade-in" onClick={() => setShowAddBlockConfirm(false)}>
                    <div className="bg-[#1a1a1a] border border-white/10 rounded-[2.5rem] p-8 max-w-sm w-full shadow-2xl" onClick={e => e.stopPropagation()}>
                        <div className="w-16 h-16 rounded-full bg-amber-400/10 flex items-center justify-center mb-6 mx-auto">
                            <AlertTriangleIcon size={32} className="text-amber-400" />
                        </div>
                        <h3 className="text-lg font-black text-center text-white uppercase tracking-tight">Evolución de Programa</h3>
                        <p className="text-xs text-zinc-500 text-center mt-3 leading-relaxed">
                            Añadir un bloque adicional convertirá este programa a <span className="text-amber-400 font-bold">Modo Avanzado</span>. Se perderá la repetición cíclica automática de eventos. ¿Deseas evolucionar?
                        </p>
                        <div className="flex flex-col gap-3 mt-8">
                            <button
                                onClick={() => {
                                    if (pendingAddBlockMacroIdx !== null) {
                                        update(p => {
                                            p.structure = 'complex';
                                            p.macrocycles[pendingAddBlockMacroIdx].blocks!.push({
                                                id: crypto.randomUUID(), name: 'Bloque II',
                                                mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }]
                                            });
                                        });
                                        setShowAddBlockConfirm(false);
                                    }
                                }}
                                className="w-full py-4 rounded-2xl bg-white text-black text-xs font-black uppercase tracking-widest shadow-xl active:scale-95 transition-all"
                            >
                                Sí, evolucionar
                            </button>
                            <button
                                onClick={() => setShowAddBlockConfirm(false)}
                                className="w-full py-4 rounded-2xl bg-white/5 text-zinc-500 text-xs font-black uppercase tracking-widest hover:text-white transition-all"
                            >
                                Mantener simple
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default StructureDrawer;

