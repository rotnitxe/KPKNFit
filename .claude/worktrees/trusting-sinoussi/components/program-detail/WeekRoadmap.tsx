import React, { useMemo } from 'react';
import { motion } from 'framer-motion';
import { Program, ProgramWeek } from '../../types';

interface WeekRoadmapProps {
    program: Program;
    selectedWeekId: string | null;
    onSelectWeek: (weekId: string) => void;
    currentWeekId?: string;
}

interface RoadmapWeek {
    id: string;
    name: string;
    blockName: string;
    mesoGoal: string;
    weekIndex: number;
    isCurrent: boolean;
    isSelected: boolean;
    hasEvent: boolean;
    eventName?: string;
    isCompleted: boolean;
}

const WeekRoadmap: React.FC<WeekRoadmapProps> = ({
    program,
    selectedWeekId,
    onSelectWeek,
    currentWeekId,
}) => {
    const weeks: RoadmapWeek[] = useMemo(() => {
        const result: RoadmapWeek[] = [];
        let globalWeekIndex = 0;

        program.macrocycles.forEach((macro, macroIdx) => {
            (macro.blocks || []).forEach((block, blockIdx) => {
                const blockName = block.name || `Bloque ${blockIdx + 1}`;
                
                block.mesocycles.forEach((meso, mesoIdx) => {
                    const mesoGoal = meso.goal || 'General';
                    
                    meso.weeks.forEach((week, weekIdx) => {
                        const isCurrent = currentWeekId === week.id;
                        const isSelected = selectedWeekId === week.id;
                        
                        // Check for events
                        const hasEvent = Boolean(week.events?.length);
                        const eventName = hasEvent ? week.events?.[0]?.title : undefined;
                        
                        // Check if completed (simplified - would need history data)
                        const isCompleted = globalWeekIndex < (currentWeekId ? globalWeekIndex : 0);
                        
                        result.push({
                            id: week.id,
                            name: `S${globalWeekIndex + 1}`,
                            blockName,
                            mesoGoal,
                            weekIndex: globalWeekIndex,
                            isCurrent,
                            isSelected,
                            hasEvent,
                            eventName,
                            isCompleted,
                        });
                        
                        globalWeekIndex++;
                    });
                });
            });
        });
        
        return result;
    }, [program, selectedWeekId, currentWeekId]);

    const scrollContainerRef = React.useRef<HTMLDivElement>(null);

    // Auto-scroll to current week on mount
    React.useEffect(() => {
        if (scrollContainerRef.current && currentWeekId) {
            const currentWeekEl = scrollContainerRef.current.querySelector('[data-current="true"]');
            if (currentWeekEl) {
                currentWeekEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
            }
        }
    }, [currentWeekId]);

    return (
        <div className="w-full">
            {/* Header del roadmap */}
            <div className="flex items-center justify-between mb-2 px-1">
                <span className="text-[9px] font-black uppercase tracking-[0.25em] text-black/40">Navegación</span>
                {currentWeekId && (
                    <span className="text-[9px] font-bold text-emerald-600 flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                        Semana actual
                    </span>
                )}
            </div>
            
            {/* Roadmap horizontal scrollable */}
            <div 
                ref={scrollContainerRef}
                className="flex gap-1.5 overflow-x-auto pb-2 custom-scrollbar"
                style={{ scrollSnapType: 'x mandatory' }}
            >
                {weeks.map((week, idx) => (
                    <button
                        key={week.id}
                        data-current={week.isCurrent}
                        onClick={() => onSelectWeek(week.id)}
                        className={`
                            relative flex-shrink-0 h-16 px-3 rounded-xl border transition-all
                            flex flex-col items-center justify-center gap-0.5
                            ${week.isSelected 
                                ? 'bg-black border-black text-white shadow-lg scale-105' 
                                : week.isCurrent
                                    ? 'bg-emerald-50 border-emerald-200 text-emerald-700 shadow-md'
                                    : week.isCompleted
                                        ? 'bg-zinc-50 border-zinc-200 text-zinc-400'
                                        : 'bg-white border-zinc-200 text-zinc-600 hover:border-zinc-300 hover:shadow-sm'
                            }
                        `}
                        style={{ scrollSnapAlign: 'center', minWidth: '56px' }}
                    >
                        {/* Indicador de evento */}
                        {week.hasEvent && (
                            <div className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-amber-400 border-2 border-white flex items-center justify-center">
                                <span className="text-[8px]">📅</span>
                            </div>
                        )}
                        
                        {/* Nombre de la semana */}
                        <span className="text-[10px] font-black leading-none">{week.name}</span>
                        
                        {/* Bloque/Mesociclo indicator */}
                        <span className={`text-[7px] uppercase tracking-[0.2em] truncate max-w-[52px] ${week.isSelected ? 'text-white/70' : 'text-zinc-400'}`}>
                            {week.blockName.split(' ')[0]} {Math.floor(week.weekIndex / 4) + 1}
                        </span>
                        
                        {/* Barra de progreso visual */}
                        <div className={`w-full h-0.5 rounded-full mt-0.5 ${week.isSelected ? 'bg-white/30' : 'bg-zinc-100'}`}>
                            {week.isCompleted && (
                                <div className="h-full rounded-full bg-emerald-400" style={{ width: '100%' }} />
                            )}
                        </div>
                    </button>
                ))}
            </div>
        </div>
    );
};

export default WeekRoadmap;
