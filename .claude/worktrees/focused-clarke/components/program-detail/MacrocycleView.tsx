import React, { useMemo, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program, ProgramWeek } from '../../types';
import { ChevronDownIcon, ChevronUpIcon, EditIcon, PlusIcon, CalendarIcon } from '../icons';

interface MacrocycleViewProps {
    program: Program;
    onEditWeek?: (week: ProgramWeek, macroIndex: number, mesoIndex: number, weekIndex: number) => void;
    onUpdateProgram?: (program: Program) => void;
}

const MacrocycleView: React.FC<MacrocycleViewProps> = ({
    program,
    onEditWeek,
    onUpdateProgram,
}) => {
    const [expandedBlocks, setExpandedBlocks] = useState<Set<string>>(new Set(['0-0']));

    const toggleBlock = (blockKey: string) => {
        const newExpanded = new Set(expandedBlocks);
        if (newExpanded.has(blockKey)) {
            newExpanded.delete(blockKey);
        } else {
            newExpanded.add(blockKey);
        }
        setExpandedBlocks(newExpanded);
    };

    const totalStats = useMemo(() => {
        let totalWeeks = 0;
        let totalSessions = 0;
        let totalMesocycles = 0;

        program.macrocycles.forEach(macro => {
            (macro.blocks || []).forEach(block => {
                block.mesocycles.forEach(meso => {
                    totalMesocycles++;
                    totalWeeks += meso.weeks.length;
                    totalSessions += meso.weeks.reduce((acc, w) => acc + w.sessions.length, 0);
                });
            });
        });

        return { totalWeeks, totalSessions, totalMesocycles };
    }, [program]);

    // Si no hay bloques, mostrar mensaje
    const totalBlocks = program.macrocycles.reduce((acc, m) => acc + (m.blocks || []).length, 0);
    
    if (totalBlocks === 0) {
        return (
            <div className="px-4 py-8 text-center">
                <p className="text-sm text-zinc-500">No hay bloques configurados</p>
                <p className="text-[11px] text-zinc-400 mt-1">Edita la estructura del programa</p>
            </div>
        );
    }

    return (
        <div className="pb-6">
            {/* Stats resumen */}
            <div className="px-4 mb-4">
                <div className="grid grid-cols-3 gap-2">
                    <div className="bg-white rounded-2xl border border-zinc-200 p-3 text-center shadow-sm">
                        <div className="text-xl font-black text-zinc-900">{totalStats.totalWeeks}</div>
                        <div className="text-[7px] uppercase tracking-[0.2em] text-zinc-400 mt-0.5">Semanas</div>
                    </div>
                    <div className="bg-white rounded-2xl border border-zinc-200 p-3 text-center shadow-sm">
                        <div className="text-xl font-black text-zinc-900">{totalStats.totalMesocycles}</div>
                        <div className="text-[7px] uppercase tracking-[0.2em] text-zinc-400 mt-0.5">Mesociclos</div>
                    </div>
                    <div className="bg-white rounded-2xl border border-zinc-200 p-3 text-center shadow-sm">
                        <div className="text-xl font-black text-zinc-900">{totalStats.totalSessions}</div>
                        <div className="text-[7px] uppercase tracking-[0.2em] text-zinc-400 mt-0.5">Sesiones</div>
                    </div>
                </div>
            </div>

            {/* Lista de macrociclos/bloques */}
            <div className="px-4 space-y-4">
                {program.macrocycles.map((macro, macroIdx) => (
                    <div key={macro.id || macroIdx} className="space-y-2">
                        {/* Header del macrociclo */}
                        <div className="flex items-center gap-2">
                            <div className="w-1 h-4 rounded-full bg-purple-500" />
                            <h3 className="text-[11px] font-black uppercase tracking-[0.2em] text-zinc-600">
                                {macro.name || `Macrociclo ${macroIdx + 1}`}
                            </h3>
                        </div>

                        {/* Bloques */}
                        {(macro.blocks || []).map((block, blockIdx) => {
                            const blockKey = `${macroIdx}-${blockIdx}`;
                            const isExpanded = expandedBlocks.has(blockKey);
                            const blockWeeks = block.mesocycles.reduce((acc, m) => acc + m.weeks.length, 0);

                            return (
                                <div key={block.id || blockIdx} className="bg-white rounded-2xl border border-zinc-200 overflow-hidden shadow-sm">
                                    {/* Header del bloque (clickable) */}
                                    <button
                                        onClick={() => toggleBlock(blockKey)}
                                        className="w-full px-4 py-3 flex items-center justify-between hover:bg-zinc-50 transition-colors"
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className={`
                                                w-8 h-8 rounded-xl flex items-center justify-center flex-shrink-0
                                                ${isExpanded ? 'bg-purple-500 text-white' : 'bg-zinc-100 text-zinc-500'}
                                            `}>
                                                <CalendarIcon size={16} />
                                            </div>
                                            <div className="text-left min-w-0">
                                                <div className="text-sm font-bold text-zinc-900 truncate">
                                                    {block.name || `Bloque ${blockIdx + 1}`}
                                                </div>
                                                <div className="text-[10px] text-zinc-500">
                                                    {blockWeeks} semanas • {block.mesocycles.length} mesociclos
                                                </div>
                                            </div>
                                        </div>
                                        {isExpanded ? (
                                            <ChevronUpIcon size={18} className="text-zinc-400 flex-shrink-0" />
                                        ) : (
                                            <ChevronDownIcon size={18} className="text-zinc-400 flex-shrink-0" />
                                        )}
                                    </button>

                                    {/* Contenido expandido */}
                                    <AnimatePresence>
                                        {isExpanded && (
                                            <motion.div
                                                initial={{ height: 0, opacity: 0 }}
                                                animate={{ height: 'auto', opacity: 1 }}
                                                exit={{ height: 0, opacity: 0 }}
                                                transition={{ duration: 0.2 }}
                                                className="overflow-hidden"
                                            >
                                                <div className="px-4 pb-4 pt-2 border-t border-zinc-100">
                                                    {/* Mesociclos */}
                                                    {block.mesocycles.map((meso, mesoIdx) => (
                                                        <div key={meso.id || mesoIdx} className="mb-4 last:mb-0">
                                                            <div className="flex items-center gap-2 mb-2">
                                                                <div className="w-0.5 h-3 rounded-full bg-purple-300" />
                                                                <span className="text-[9px] font-black uppercase tracking-[0.2em] text-purple-600">
                                                                    {meso.goal || `Mesociclo ${mesoIdx + 1}`}
                                                                </span>
                                                            </div>

                                                            {/* Semanas del mesociclo */}
                                                            <div className="grid grid-cols-4 gap-1.5">
                                                                {meso.weeks.map((week, weekIdx) => {
                                                                    const globalWeekIdx = block.mesocycles
                                                                        .slice(0, mesoIdx)
                                                                        .reduce((acc, m) => acc + m.weeks.length, 0) + weekIdx;

                                                                    return (
                                                                        <button
                                                                            key={week.id}
                                                                            onClick={() => onEditWeek?.(week, macroIdx, mesoIdx, globalWeekIdx)}
                                                                            className="aspect-square rounded-xl bg-zinc-50 border border-zinc-200 hover:border-purple-300 hover:bg-purple-50 transition-all flex flex-col items-center justify-center gap-0.5 group"
                                                                        >
                                                                            <span className="text-[10px] font-black text-zinc-700 group-hover:text-purple-700">
                                                                                S{globalWeekIdx + 1}
                                                                            </span>
                                                                            <span className="text-[7px] text-zinc-400 group-hover:text-purple-500">
                                                                                {week.sessions.length}
                                                                            </span>
                                                                        </button>
                                                                    );
                                                                })}
                                                            </div>
                                                        </div>
                                                    ))}
                                                </div>
                                            </motion.div>
                                        )}
                                    </AnimatePresence>
                                </div>
                            );
                        })}
                    </div>
                ))}
            </div>
        </div>
    );
};

export default MacrocycleView;
