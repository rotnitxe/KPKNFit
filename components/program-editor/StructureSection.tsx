import React from 'react';
import { Program } from '../../types';
import { ActivityIcon, PlusIcon, TrashIcon } from '../icons';

const GOAL_OPTIONS = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'];

interface StructureSectionProps {
    program: Program;
    onUpdateProgram: (program: Program) => void;
}

const StructureSection: React.FC<StructureSectionProps> = ({ program, onUpdateProgram }) => {
    const update = (fn: (p: Program) => void) => {
        const clone = JSON.parse(JSON.stringify(program));
        fn(clone);
        onUpdateProgram(clone);
    };

    const totalWeeks = program.macrocycles.reduce((acc, m) =>
        acc + (m.blocks || []).reduce((ba, b) =>
            ba + b.mesocycles.reduce((ma, me) => ma + me.weeks.length, 0), 0), 0);

    const blockColors = ['bg-blue-500', 'bg-purple-500', 'bg-yellow-500', 'bg-emerald-500', 'bg-rose-500'];

    return (
        <div className="space-y-4">
            <h3 className="text-xs font-black text-white uppercase tracking-widest flex items-center gap-2">
                <ActivityIcon size={14} className="text-zinc-400" /> Estructura del Programa
            </h3>

            {/* Timeline bar */}
            {totalWeeks > 0 && (
                <div className="bg-zinc-950 border border-white/5 rounded-xl p-3">
                    <div className="flex items-center justify-between mb-2">
                        <span className="text-[8px] font-black text-zinc-500 uppercase tracking-widest">Timeline</span>
                        <span className="text-[9px] text-zinc-500 font-bold">{totalWeeks} semanas</span>
                    </div>
                    <div className="flex gap-0.5 h-4 rounded-full overflow-hidden">
                        {program.macrocycles.flatMap((m, mi) =>
                            (m.blocks || []).map((b, bi) => {
                                const weeks = b.mesocycles.reduce((a, me) => a + me.weeks.length, 0);
                                return (
                                    <div
                                        key={`${mi}-${bi}`}
                                        className={`${blockColors[(mi * 10 + bi) % blockColors.length]} rounded-sm relative group cursor-pointer`}
                                        style={{ flex: weeks }}
                                        title={`${b.name}: ${weeks}sem`}
                                    >
                                        <span className="absolute inset-0 flex items-center justify-center text-[7px] font-black text-black/60 opacity-0 group-hover:opacity-100 transition-opacity truncate px-1">
                                            {b.name}
                                        </span>
                                    </div>
                                );
                            })
                        )}
                    </div>
                </div>
            )}

            {/* Macrocycles */}
            {program.macrocycles.map((macro, macroIdx) => (
                <div key={macro.id} className="bg-zinc-950 border border-white/5 rounded-xl p-4 space-y-3">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 flex-1">
                            <span className="bg-white/10 text-white text-[7px] font-black px-1.5 py-0.5 rounded uppercase">M{macroIdx + 1}</span>
                            <input
                                className="bg-transparent text-xs font-black text-white uppercase focus:ring-0 border-none p-0 flex-1"
                                value={macro.name}
                                onChange={e => update(p => { p.macrocycles[macroIdx].name = e.target.value; })}
                            />
                        </div>
                    </div>

                    {/* Blocks */}
                    <div className="space-y-3 pl-3 border-l-2 border-white/10">
                        {(macro.blocks || []).map((block, blockIdx) => (
                            <div key={block.id} className="bg-black/40 border border-white/5 rounded-xl p-3 space-y-2">
                                <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-2 flex-1">
                                        <span className="text-[7px] font-black text-zinc-500 uppercase">B{blockIdx + 1}</span>
                                        <input
                                            className="bg-transparent text-[11px] font-black text-white uppercase focus:ring-0 border-none p-0 flex-1"
                                            value={block.name}
                                            onChange={e => update(p => { p.macrocycles[macroIdx].blocks[blockIdx].name = e.target.value; })}
                                        />
                                    </div>
                                    <button
                                        onClick={() => { if (window.confirm('¿Eliminar bloque?')) update(p => { p.macrocycles[macroIdx].blocks.splice(blockIdx, 1); }); }}
                                        className="p-1 text-zinc-700 hover:text-red-500 transition-colors"
                                    >
                                        <TrashIcon size={10} />
                                    </button>
                                </div>

                                {/* Mesocycles */}
                                {block.mesocycles.map((meso, mesoIdx) => (
                                    <div key={meso.id} className="flex items-center justify-between bg-zinc-950 rounded-lg p-2">
                                        <div className="flex items-center gap-2 flex-1">
                                            <div className="w-1.5 h-1.5 rounded-full bg-blue-500" />
                                            <input
                                                className="bg-transparent text-[9px] font-bold text-zinc-300 focus:ring-0 border-none p-0 flex-1"
                                                value={meso.name}
                                                onChange={e => update(p => { p.macrocycles[macroIdx].blocks[blockIdx].mesocycles[mesoIdx].name = e.target.value; })}
                                            />
                                        </div>
                                        <div className="flex items-center gap-1.5">
                                            <select
                                                className="bg-black text-[7px] text-zinc-400 border border-white/10 rounded px-1 py-0.5 font-bold"
                                                value={meso.goal}
                                                onChange={e => update(p => { p.macrocycles[macroIdx].blocks[blockIdx].mesocycles[mesoIdx].goal = e.target.value; })}
                                            >
                                                {GOAL_OPTIONS.map(g => <option key={g} value={g}>{g}</option>)}
                                            </select>
                                            <span className="text-[8px] text-zinc-500 font-bold">{meso.weeks.length}sem</span>
                                            <button
                                                onClick={() => update(p => {
                                                    const m = p.macrocycles[macroIdx].blocks[blockIdx].mesocycles[mesoIdx];
                                                    m.weeks.push({ id: crypto.randomUUID(), name: `Semana ${m.weeks.length + 1}`, sessions: [] });
                                                })}
                                                className="p-0.5 text-zinc-600 hover:text-white transition-colors"
                                            >
                                                <PlusIcon size={10} />
                                            </button>
                                        </div>
                                    </div>
                                ))}

                                <button
                                    onClick={() => update(p => {
                                        p.macrocycles[macroIdx].blocks[blockIdx].mesocycles.push({
                                            id: crypto.randomUUID(), name: 'Nuevo Meso', goal: 'Acumulación', weeks: [],
                                        });
                                    })}
                                    className="w-full py-1.5 text-[8px] font-bold text-zinc-600 hover:text-white border border-dashed border-white/10 rounded-lg flex items-center justify-center gap-1 transition-colors"
                                >
                                    <PlusIcon size={9} /> Mesociclo
                                </button>
                            </div>
                        ))}

                        <button
                            onClick={() => update(p => {
                                p.macrocycles[macroIdx].blocks.push({
                                    id: crypto.randomUUID(), name: 'Nuevo Bloque',
                                    mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [] }],
                                });
                            })}
                            className="w-full py-2 text-[8px] font-bold text-zinc-600 hover:text-white border border-dashed border-white/10 rounded-lg flex items-center justify-center gap-1 transition-colors"
                        >
                            <PlusIcon size={10} /> Bloque
                        </button>
                    </div>
                </div>
            ))}
        </div>
    );
};

export default StructureSection;
