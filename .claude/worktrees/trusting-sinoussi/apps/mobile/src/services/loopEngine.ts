/**
 * Loop Engine — Motor de Loops para Programas Simples
 * 
 * Un "Loop" reemplaza el concepto de "Evento Cíclico".
 * Un "Ciclo" = trayecto completo de semana 1 a semana N del único bloque.
 * Los Loops se activan cada X ciclos, insertando semanas especiales.
 */
import type { Program, Loop, LoopActivation } from '../types/workout';

export interface LoopState {
    currentCycle: number;
    postponed?: { loopId: string; fromCycle: number; toCycle: number }[];
    cancelled?: string[];
}

export function getCycleLength(program: Program): number {
    const block = program.macrocycles[0]?.blocks?.[0];
    if (!block) return 1;
    return block.mesocycles.reduce((acc, m) => acc + m.weeks.length, 0) || 1;
}

export function getCurrentCycle(program: Program): number {
    return (program as any).loopState?.currentCycle ?? 0;
}

export function getDaysIntoCycle(program: Program, startDate: Date, now?: Date): number {
    const cycleLength = getCycleLength(program);
    const cycleDays = cycleLength * ((program as any).weekDays ?? 7);
    const nowTime = now ? now.getTime() : Date.now();
    const daysSinceStart = Math.floor((nowTime - startDate.getTime()) / (1000 * 60 * 60 * 24));
    return daysSinceStart % cycleDays;
}

export interface LoopProjection {
    loop: Loop;
    cycle: number;
    isPostponed: boolean;
    isCancelled: boolean;
    daysUntil: number;
    weekInCycle: number;
}

export function projectLoops(
    program: Program,
    fromCycle: number,
    lookAheadCycles: number = 12
): LoopProjection[] {
    const loops = (program as any).loops || [];
    if (loops.length === 0) return [];

    const cycleLength = getCycleLength(program);
    const cycleDays = cycleLength * ((program as any).weekDays ?? 7);
    const postponed = (program as any).loopState?.postponed || [];
    const cancelled = new Set((program as any).loopState?.cancelled || []);
    const projections: LoopProjection[] = [];

    for (let cycle = fromCycle; cycle < fromCycle + lookAheadCycles; cycle++) {
        for (const loop of loops) {
            if (cancelled.has(loop.id)) continue;

            const isActive = cycle > 0 && cycle % loop.repeatEveryXLoops === 0;
            const postponement = postponed.find((p: any) => p.loopId === loop.id && p.fromCycle === cycle);

            if (isActive && !postponement) {
                projections.push({
                    loop,
                    cycle,
                    isPostponed: false,
                    isCancelled: false,
                    daysUntil: (cycle - fromCycle) * cycleDays,
                    weekInCycle: cycleLength,
                });
            }

            const deferredHere = postponed.find((p: any) => p.loopId === loop.id && p.toCycle === cycle);
            if (deferredHere) {
                projections.push({
                    loop,
                    cycle,
                    isPostponed: true,
                    isCancelled: false,
                    daysUntil: (cycle - fromCycle) * cycleDays,
                    weekInCycle: cycleLength,
                });
            }
        }
    }

    return projections.sort((a, b) => a.cycle - b.cycle || (b.loop.priority ?? 0) - (a.loop.priority ?? 1));
}

export function detectLoopCollisions(projections: LoopProjection[]): Map<number, LoopProjection[]> {
    const byCycle = new Map<number, LoopProjection[]>();
    for (const p of projections) {
        const arr = byCycle.get(p.cycle) || [];
        arr.push(p);
        byCycle.set(p.cycle, arr);
    }
    const collisions = new Map<number, LoopProjection[]>();
    for (const [cycle, loops] of byCycle) {
        if (loops.length > 1) collisions.set(cycle, loops);
    }
    return collisions;
}

export function postponeLoop(program: Program, loopId: string, fromCycle: number): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    if (!(updated as any).loopState) (updated as any).loopState = { currentCycle: 0 };
    if (!(updated as any).loopState.postponed) (updated as any).loopState.postponed = [];

    const loop = (updated as any).loops?.find((l: any) => l.id === loopId);
    if (!loop) return updated;

    (updated as any).loopState.postponed.push({
        loopId,
        fromCycle,
        toCycle: fromCycle + 1,
    });

    return updated;
}

export function cancelLoop(program: Program, loopId: string): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    if (!(updated as any).loopState) (updated as any).loopState = { currentCycle: 0 };
    if (!(updated as any).loopState.cancelled) (updated as any).loopState.cancelled = [];

    if (!((updated as any).loopState.cancelled as string[]).includes(loopId)) {
        (updated as any).loopState.cancelled.push(loopId);
    }

    return updated;
}

export function reactivateLoop(program: Program, loopId: string): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    if ((updated as any).loopState?.cancelled) {
        (updated as any).loopState.cancelled = ((updated as any).loopState.cancelled as string[]).filter((id: string) => id !== loopId);
    }
    return updated;
}

export function advanceCycle(program: Program): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    if (!(updated as any).loopState) (updated as any).loopState = { currentCycle: 0 };
    (updated as any).loopState.currentCycle += 1;

    if ((updated as any).loopState.postponed) {
        (updated as any).loopState.postponed = ((updated as any).loopState.postponed as any[]).filter(
            (p: any) => p.toCycle > (updated as any).loopState.currentCycle
        );
    }

    return updated;
}

export function migrateEventsToLoops(program: Program): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    const legacyEvents = ((updated as any).events || []).filter((e: any) => e.repeatEveryXCycles);

    if (legacyEvents.length === 0) return updated;

    if (!(updated as any).loops) (updated as any).loops = [];

    for (const event of legacyEvents) {
        const alreadyMigrated = (updated as any).loops.some((l: any) => l.title === event.title);
        if (alreadyMigrated) continue;

        (updated as any).loops.push({
            id: event.id || `loop-${Date.now().toString(36).substr(2, 9)}`,
            title: event.title,
            type: event.type || 'custom',
            repeatEveryXLoops: event.repeatEveryXCycles,
            durationType: 'week',
            sessions: event.sessions,
        });
    }

    (updated as any).events = ((updated as any).events || []).filter((e: any) => !e.repeatEveryXCycles);

    return updated;
}

export function formatLoopCountdown(daysUntil: number): string {
    if (daysUntil <= 0) return 'Ahora';
    if (daysUntil === 1) return '1 dia';
    if (daysUntil < 7) return `${daysUntil} dias`;
    const weeks = Math.floor(daysUntil / 7);
    const days = daysUntil % 7;
    if (days === 0) return `${weeks} sem`;
    return `${weeks}s ${days}d`;
}

export function getLoopTypeEmoji(type: Loop['type']): string {
    switch (type) {
        case '1rm_test': return '🏋️';
        case 'deload': return '🧘';
        case 'competition': return '🏆';
        case 'custom': return '⚡';
        default: return '🔄';
    }
}

export function getLoopTypeLabel(type: Loop['type']): string {
    switch (type) {
        case '1rm_test': return 'Test 1RM';
        case 'deload': return 'Descarga';
        case 'competition': return 'Competición';
        case 'custom': return 'Personalizado';
        default: return 'Loop';
    }
}
