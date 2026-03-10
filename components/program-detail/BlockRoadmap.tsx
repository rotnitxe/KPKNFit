import React, { useMemo, useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program, ProgramWeek } from '../../types';
import { ChevronLeftIcon, ChevronRightIcon } from '../icons';

interface BlockRoadmapProps {
    program: Program;
    selectedBlockId: string | null;
    selectedWeekId: string | null;
    currentWeekId?: string;
    onSelectBlock: (blockId: string) => void;
    onSelectWeek: (weekId: string) => void;
}

interface RoadmapBlock {
    id: string;
    name: string;
    macroIndex: number;
    blockIndex: number;
    totalWeeks: number;
    weeks: RoadmapWeek[];
}

interface RoadmapWeek {
    id: string;
    name: string;
    weekNumber: number;
    mesoGoal: string;
    isCurrent: boolean;
    isSelected: boolean;
    hasEvent: boolean;
    eventName?: string;
}

const BlockRoadmap: React.FC<BlockRoadmapProps> = ({
    program,
    selectedBlockId,
    selectedWeekId,
    currentWeekId,
    onSelectBlock,
    onSelectWeek,
}) => {
    const [collapsedBlocks, setCollapsedBlocks] = useState<Set<string>>(new Set());
    const scrollContainerRef = useRef<HTMLDivElement>(null);

    // Build roadmap data
    const roadmapBlocks: RoadmapBlock[] = useMemo(() => {
        return program.macrocycles.flatMap((macro, macroIdx) =>
            (macro.blocks || []).map((block, blockIdx) => {
                const blockName = block.name || `Bloque ${blockIdx + 1}`;
                let mesoOffset = 0;
                
                // Calculate meso offset for proper week numbering
                for (let i = 0; i < blockIdx; i++) {
                    mesoOffset += (macro.blocks || [])[i]?.mesocycles?.length || 0;
                }

                const weeks: RoadmapWeek[] = block.mesocycles.flatMap((meso, localMesoIdx) =>
                    meso.weeks.map((week, weekIdx) => {
                        const isCurrent = currentWeekId === week.id;
                        const isSelected = selectedWeekId === week.id;
                        const hasEvent = week.events && week.events.length > 0;
                        
                        return {
                            id: week.id,
                            name: `S${mesoOffset + localMesoIdx + 1}`,
                            weekNumber: mesoOffset + localMesoIdx,
                            mesoGoal: meso.goal || 'General',
                            isCurrent,
                            isSelected,
                            hasEvent,
                            eventName: hasEvent ? week.events[0]?.title : undefined,
                        };
                    })
                );

                return {
                    id: block.id || `${macroIdx}-${blockIdx}`,
                    name: blockName,
                    macroIndex: macroIdx,
                    blockIndex: blockIdx,
                    totalWeeks: weeks.length,
                    weeks,
                };
            })
        );
    }, [program, selectedWeekId, currentWeekId]);

    // Toggle block collapse
    const toggleBlock = (blockId: string) => {
        const newCollapsed = new Set(collapsedBlocks);
        if (newCollapsed.has(blockId)) {
            newCollapsed.delete(blockId);
        } else {
            newCollapsed.add(blockId);
        }
        setCollapsedBlocks(newCollapsed);
    };

    // Auto-scroll to current week on mount
    useEffect(() => {
        if (scrollContainerRef.current && currentWeekId) {
            const currentWeekEl = scrollContainerRef.current.querySelector('[data-current="true"]');
            if (currentWeekEl) {
                currentWeekEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
            }
        }
    }, [currentWeekId]);

    const scrollLeft = () => {
        if (scrollContainerRef.current) {
            scrollContainerRef.current.scrollBy({ left: -200, behavior: 'smooth' });
        }
    };

    const scrollRight = () => {
        if (scrollContainerRef.current) {
            scrollContainerRef.current.scrollBy({ left: 200, behavior: 'smooth' });
        }
    };

    return (
        <div className="w-full px-4 pb-2">
            {/* Header */}
            <div className="flex items-center justify-between mb-2">
                <span className="text-[8px] font-black uppercase tracking-[0.25em] text-zinc-400">Roadmap</span>
                {currentWeekId && (
                    <span className="text-[7px] font-bold text-emerald-600 flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                        Actual
                    </span>
                )}
            </div>

            {/* Roadmap Container with Navigation */}
            <div className="relative">
                {/* Left Arrow - posicionado más hacia adentro */}
                <button
                    onClick={scrollLeft}
                    className="absolute left-1 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-white shadow-lg border border-zinc-200 flex items-center justify-center z-20 hover:bg-zinc-50 transition-colors"
                    style={{ marginLeft: '-4px' }}
                >
                    <ChevronLeftIcon size={14} className="text-zinc-600" />
                </button>

                {/* Right Arrow - posicionado más hacia adentro */}
                <button
                    onClick={scrollRight}
                    className="absolute right-1 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-white shadow-lg border border-zinc-200 flex items-center justify-center z-20 hover:bg-zinc-50 transition-colors"
                    style={{ marginRight: '-4px' }}
                >
                    <ChevronRightIcon size={14} className="text-zinc-600" />
                </button>

                {/* Scrollable Roadmap - con padding lateral para safe zone */}
                <div
                    ref={scrollContainerRef}
                    className="flex items-center gap-3 overflow-x-auto px-8 py-4 custom-scrollbar scroll-smooth"
                    style={{ scrollSnapType: 'x mandatory', scrollbarWidth: 'none', msOverflowStyle: 'none' }}
                >
                    {roadmapBlocks.map((block, blockIdx) => {
                        const isBlockSelected = selectedBlockId === block.id;
                        const isCollapsed = collapsedBlocks.has(block.id);
                        const isLastBlock = blockIdx === roadmapBlocks.length - 1;

                        return (
                            <div key={block.id} className="flex items-center gap-2 flex-shrink-0" style={{ scrollSnapAlign: 'center' }}>
                                {/* Block Node - Circle */}
                                <div className="flex flex-col items-center flex-shrink-0">
                                    <button
                                        onClick={() => {
                                            onSelectBlock(block.id);
                                            if (isCollapsed) toggleBlock(block.id);
                                        }}
                                        className={`
                                            relative w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0
                                            transition-all duration-300
                                            ${isBlockSelected
                                                ? 'bg-gradient-to-br from-purple-500 to-purple-600 text-white shadow-[0_4px_12px_rgba(168,85,247,0.4)] scale-105'
                                                : 'bg-white text-zinc-700 shadow-[0_2px_8px_rgba(0,0,0,0.08)] hover:shadow-[0_4px_12px_rgba(0,0,0,0.12)] hover:scale-[1.08] border border-zinc-200'
                                            }
                                        `}
                                    >
                                        <span className="text-[10px] font-black leading-none">
                                            B{blockIdx + 1}
                                        </span>

                                        {/* Event Indicator */}
                                        {block.weeks.some(w => w.hasEvent) && (
                                            <div className="absolute -top-0.5 -right-0.5 w-3.5 h-3.5 rounded-full bg-amber-400 border-2 border-white flex items-center justify-center shadow-sm">
                                                <span className="text-[6px]">📅</span>
                                            </div>
                                        )}
                                    </button>
                                </div>

                                {/* Weeks - Small Circles (when expanded) */}
                                <AnimatePresence>
                                    {!isCollapsed && block.weeks.length > 0 && (
                                        <motion.div
                                            initial={{ width: 0, opacity: 0 }}
                                            animate={{ width: 'auto', opacity: 1 }}
                                            exit={{ width: 0, opacity: 0 }}
                                            transition={{ duration: 0.2, ease: 'easeInOut' }}
                                            className="flex items-center gap-1.5 overflow-visible"
                                        >
                                            {block.weeks.map((week, weekIdx) => {
                                                const isLastWeek = weekIdx === block.weeks.length - 1;

                                                return (
                                                    <div key={week.id} className="flex items-center flex-shrink-0">
                                                        {/* Week Node - Small Circle */}
                                                        <button
                                                            data-current={week.isCurrent}
                                                            onClick={() => {
                                                                onSelectWeek(week.id);
                                                                if (!isBlockSelected) onSelectBlock(block.id);
                                                            }}
                                                            className={`
                                                                relative w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0
                                                                transition-all duration-200
                                                                ${week.isSelected
                                                                    ? 'bg-zinc-900 text-white shadow-[0_2px_8px_rgba(0,0,0,0.3)] scale-105'
                                                                    : week.isCurrent
                                                                        ? 'bg-emerald-500 text-white shadow-[0_2px_8px_rgba(16,185,129,0.3)]'
                                                                        : 'bg-zinc-100 text-zinc-500 shadow-[0_2px_6px_rgba(0,0,0,0.08)] hover:bg-zinc-200'
                                                                }
                                                            `}
                                                            style={{ zIndex: 1 }}
                                                        >
                                                            <span className="text-[7px] font-black leading-none">
                                                                {week.name.replace('S', '')}
                                                            </span>

                                                            {/* Event Dot */}
                                                            {week.hasEvent && (
                                                                <div className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 rounded-full bg-amber-400 border-2 border-white shadow-sm" />
                                                            )}
                                                        </button>

                                                        {/* Week Connecting Line */}
                                                        {!isLastWeek && (
                                                            <div className="w-1.5 h-0.5 bg-zinc-300" />
                                                        )}
                                                    </div>
                                                );
                                            })}
                                        </motion.div>
                                    )}
                                </AnimatePresence>

                                {/* Block Connecting Line (to next block) */}
                                {!isLastBlock && (
                                    <div className="w-8 h-0.5 bg-gradient-to-r from-zinc-300 to-zinc-200 flex-shrink-0" />
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};

export default BlockRoadmap;
