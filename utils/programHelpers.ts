import { Program, Session, ProgramWeek } from '../types';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

const startOfLocalDay = (date: Date): Date => new Date(date.getFullYear(), date.getMonth(), date.getDate());

const getClosestYearForMonthDay = (month: number, day: number, today: Date): number => {
    const currentYear = today.getFullYear();
    const candidates = [currentYear - 1, currentYear, currentYear + 1];
    return candidates.reduce((best, year) => {
        const bestDiff = Math.abs(new Date(best, month - 1, day).getTime() - today.getTime());
        const diff = Math.abs(new Date(year, month - 1, day).getTime() - today.getTime());
        return diff < bestDiff ? year : best;
    }, currentYear);
};

export interface CalendarizedWeekMatch {
    week: ProgramWeek;
    macroIndex: number;
    blockIndex: number;
    blockId: string;
    mesoIndex: number;
    weekId: string;
    startDate: Date;
    endDate: Date;
}

export const parseCalendarizedWeekStartDate = (weekName: string | undefined, today: Date = new Date()): Date | null => {
    if (!weekName) return null;
    const match = weekName.match(/(?:semana\s*:?)?\s*(\d{1,2})\/(\d{1,2})(?:\/(\d{2,4}))?/i);
    if (!match) return null;

    const month = Number(match[1]);
    const day = Number(match[2]);
    if (!Number.isInteger(month) || !Number.isInteger(day) || month < 1 || month > 12 || day < 1 || day > 31) return null;

    const year = match[3]
        ? Number(match[3].length === 2 ? `20${match[3]}` : match[3])
        : getClosestYearForMonthDay(month, day, today);
    const parsed = new Date(year, month - 1, day);

    if (parsed.getMonth() !== month - 1 || parsed.getDate() !== day) return null;
    return parsed;
};

export const formatCalendarizedNameForSpanish = (name: string | undefined): string => {
    if (!name) return '';
    return name.replace(/(\d{1,2})\/(\d{1,2})(?:\/(\d{2,4}))?(?!\/\d)/g, (_match, month, day, year) => {
        const dd = String(Number(day)).padStart(2, '0');
        const mm = String(Number(month)).padStart(2, '0');
        return year ? `${dd}/${mm}/${year}` : `${dd}/${mm}`;
    });
};

export const getCalendarizedCurrentWeek = (program: Program, today: Date = new Date()): CalendarizedWeekMatch | null => {
    const localToday = startOfLocalDay(today);
    const weekDays = Math.max(1, program.weekDays ?? 7);
    let closest: CalendarizedWeekMatch | null = null;

    for (let macroIndex = 0; macroIndex < program.macrocycles.length; macroIndex++) {
        const macro = program.macrocycles[macroIndex];
        let mesoOffset = 0;

        for (let blockIndex = 0; blockIndex < (macro.blocks || []).length; blockIndex++) {
            const block = (macro.blocks || [])[blockIndex];
            for (let localMesoIndex = 0; localMesoIndex < block.mesocycles.length; localMesoIndex++) {
                const meso = block.mesocycles[localMesoIndex];
                for (const week of meso.weeks) {
                    const startDate = parseCalendarizedWeekStartDate(week.name, localToday);
                    if (!startDate) continue;

                    const endDate = new Date(startDate.getTime() + (weekDays - 1) * MS_PER_DAY);
                    const match: CalendarizedWeekMatch = {
                        week,
                        macroIndex,
                        blockIndex,
                        blockId: block.id,
                        mesoIndex: mesoOffset + localMesoIndex,
                        weekId: week.id,
                        startDate,
                        endDate,
                    };

                    if (localToday >= startDate && localToday <= endDate) return match;

                    if (!closest) {
                        closest = match;
                    } else {
                        const currentDistance = Math.min(
                            Math.abs(localToday.getTime() - startDate.getTime()),
                            Math.abs(localToday.getTime() - endDate.getTime()),
                        );
                        const closestDistance = Math.min(
                            Math.abs(localToday.getTime() - closest.startDate.getTime()),
                            Math.abs(localToday.getTime() - closest.endDate.getTime()),
                        );
                        if (currentDistance < closestDistance) closest = match;
                    }
                }
            }
            mesoOffset += block.mesocycles.length;
        }
    }

    return closest;
};

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

export const isProgramSimple = (program: Program): boolean => {
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
