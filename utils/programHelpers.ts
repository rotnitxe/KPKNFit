import { Program, Session, ProgramWeek } from '../types';

export const getAbsoluteWeekIndex = (program: Program, targetBlockId: string, targetWeekId: string): number => {
    let abs = 0;
    for (const macro of program.macrocycles) {
        for (const block of (macro.blocks || [])) {
            for (const meso of block.mesocycles) {
                for (const week of meso.weeks) {
                    if (block.id === targetBlockId && week.id === targetWeekId) return abs;
                    abs++;
                }
            }
        }
    }
    return abs;
};

export const checkWeekHasEvent = (program: Program, absIndex: number): boolean => {
    return (program.events || []).some(e => {
        if (e.repeatEveryXCycles) {
            const cycleLength = program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length || 1;
            return ((absIndex + 1) % (e.repeatEveryXCycles * cycleLength)) === 0;
        }
        return e.calculatedWeek === absIndex;
    });
};

export const getDayName = (dayIndex: number, startWeekOn: number): string => {
    const days = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
    const realIndex = (startWeekOn + (dayIndex - 1)) % 7;
    return days[realIndex];
};

export const DAYS_LABELS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

export const generateSessionsForWeek = (
    startDay: number,
    pattern: string[],
    details?: Record<number, Session>,
): Session[] => {
    const sessions: Session[] = [];
    pattern.forEach((label, dayIndex) => {
        if (label && label.toLowerCase() !== 'descanso' && label.trim() !== '') {
            const assignedDay = (startDay + dayIndex) % 7;
            const existingDetail = details?.[dayIndex];
            if (existingDetail) {
                sessions.push({ ...existingDetail, id: crypto.randomUUID(), dayOfWeek: assignedDay });
            } else {
                sessions.push({
                    id: crypto.randomUUID(),
                    name: label,
                    description: '',
                    exercises: [],
                    dayOfWeek: assignedDay,
                });
            }
        }
    });
    return sessions;
};

export const isProgramCyclic = (program: Program): boolean => {
    if (program.structure === 'simple') return true;
    if (program.structure === 'complex') return false;
    if (program.macrocycles.length > 1) return false;
    const macro = program.macrocycles[0];
    if (!macro) return false;
    if ((macro.blocks || []).length > 1) return false;
    const block = (macro.blocks || [])[0];
    if (!block) return false;
    return block.mesocycles.length <= 1;
};

export const isProgramComplex = (p: Program | null): boolean => {
    if (!p) return false;
    if (p.structure === 'complex') return true;
    if (p.structure === 'simple') return false;
    if (p.macrocycles.length > 1) return true;
    const macro = p.macrocycles[0];
    if (!macro) return false;
    if ((macro.blocks || []).length > 1) return true;
    const block = (macro.blocks || [])[0];
    if (!block) return false;
    return block.mesocycles.length > 1;
};

export interface RoadmapBlock {
    id: string;
    name: string;
    macroIndex: number;
    mesocycles: any[];
}

export const getRoadmapBlocks = (program: Program): RoadmapBlock[] => {
    const blocks: RoadmapBlock[] = [];
    program.macrocycles.forEach((macro, macroIdx) => {
        (macro.blocks || []).forEach(block => {
            blocks.push({
                id: block.id,
                name: block.name,
                macroIndex: macroIdx,
                mesocycles: block.mesocycles,
            });
        });
    });
    return blocks;
};

export const countTrainingDays = (pattern: string[]): number => {
    return pattern.filter(d => d.toLowerCase() !== 'descanso' && d.trim() !== '').length;
};

export const getTotalWeeks = (program: Program): number => {
    let total = 0;
    for (const macro of program.macrocycles) {
        for (const block of (macro.blocks || [])) {
            for (const meso of block.mesocycles) {
                total += meso.weeks.length;
            }
        }
    }
    return total;
};
