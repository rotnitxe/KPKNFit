/**
 * Loop Engine — Motor de Loops para Programas Simples
 * 
 * Un "Loop" reemplaza el concepto de "Evento Cíclico".
 * Un "Ciclo" = trayecto completo de semana 1 a semana N del único bloque.
 * Los Loops se activan cada X ciclos, insertando semanas especiales.
 */
import { Program, Loop, LoopActivation } from '../types';

// ─── Cálculos de ciclo ───

export function getCycleLength(program: Program): number {
    const block = program.macrocycles[0]?.blocks?.[0];
    if (!block) return 1;
    return block.mesocycles.reduce((acc, m) => acc + m.weeks.length, 0) || 1;
}

export function getCurrentCycle(program: Program): number {
    return program.loopState?.currentCycle ?? 0;
}

export function getDaysIntoCycle(program: Program, startDate: Date, now: Date = new Date()): number {
    const cycleLength = getCycleLength(program);
    const cycleDays = cycleLength * (program.weekDays ?? 7);
    const daysSinceStart = Math.floor((now.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24));
    return daysSinceStart % cycleDays;
}

// ─── Proyección de Loops ───

export interface LoopProjection {
    loop: Loop;
    cycle: number;
    isPostponed: boolean;
    isCancelled: boolean;
    daysUntil: number;
    weekInCycle: number;
}

/**
 * Proyecta las próximas activaciones de loops para los siguientes N ciclos.
 * Incluye lógica de stacking (cuando dos loops coinciden) y postponements.
 */
export function projectLoops(
    program: Program,
    fromCycle: number,
    lookAheadCycles: number = 12
): LoopProjection[] {
    const loops = program.loops || [];
    if (loops.length === 0) return [];

    const cycleLength = getCycleLength(program);
    const cycleDays = cycleLength * (program.weekDays ?? 7);
    const postponed = program.loopState?.postponed || [];
    const cancelled = new Set(program.loopState?.cancelled || []);
    const projections: LoopProjection[] = [];

    for (let cycle = fromCycle; cycle < fromCycle + lookAheadCycles; cycle++) {
        for (const loop of loops) {
            if (cancelled.has(loop.id)) continue;

            const isActive = cycle > 0 && cycle % loop.repeatEveryXLoops === 0;
            const postponement = postponed.find(p => p.loopId === loop.id && p.fromCycle === cycle);

            if (isActive && !postponement) {
                projections.push({
                    loop,
                    cycle,
                    isPostponed: false,
                    isCancelled: false,
                    daysUntil: (cycle - fromCycle) * cycleDays,
                    weekInCycle: cycleLength, // Loop fires after last week of cycle
                });
            }

            // Check if a postponed loop lands on this cycle
            const deferredHere = postponed.find(p => p.loopId === loop.id && p.toCycle === cycle);
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

    return projections.sort((a, b) => a.cycle - b.cycle || (b.loop.priority ?? 0) - (a.loop.priority ?? 0));
}

/**
 * Detecta colisiones de loops en el mismo ciclo.
 */
export function detectLoopCollisions(projections: LoopProjection[]): Map<number, LoopProjection[]> {
    const byCycle = new Map<number, LoopProjection[]>();
    for (const p of projections) {
        const arr = byCycle.get(p.cycle) || [];
        arr.push(p);
        byCycle.set(p.cycle, arr);
    }
    // Only return cycles with >1 loop (actual collisions)
    const collisions = new Map<number, LoopProjection[]>();
    for (const [cycle, loops] of byCycle) {
        if (loops.length > 1) collisions.set(cycle, loops);
    }
    return collisions;
}

// ─── Acciones sobre Loops ───

export function postponeLoop(program: Program, loopId: string, fromCycle: number): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    if (!updated.loopState) updated.loopState = { currentCycle: 0 };
    if (!updated.loopState.postponed) updated.loopState.postponed = [];

    const loop = updated.loops?.find(l => l.id === loopId);
    if (!loop) return updated;

    updated.loopState.postponed.push({
        loopId,
        fromCycle,
        toCycle: fromCycle + 1,
    });

    return updated;
}

export function cancelLoop(program: Program, loopId: string): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    if (!updated.loopState) updated.loopState = { currentCycle: 0 };
    if (!updated.loopState.cancelled) updated.loopState.cancelled = [];

    if (!updated.loopState.cancelled.includes(loopId)) {
        updated.loopState.cancelled.push(loopId);
    }

    return updated;
}

export function reactivateLoop(program: Program, loopId: string): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    if (updated.loopState?.cancelled) {
        updated.loopState.cancelled = updated.loopState.cancelled.filter(id => id !== loopId);
    }
    return updated;
}

export function advanceCycle(program: Program): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    if (!updated.loopState) updated.loopState = { currentCycle: 0 };
    updated.loopState.currentCycle += 1;

    // Clean expired postponements
    if (updated.loopState.postponed) {
        updated.loopState.postponed = updated.loopState.postponed.filter(
            p => p.toCycle > updated.loopState!.currentCycle
        );
    }

    return updated;
}

// ─── Migración: Convertir events legacy a loops ───

export function migrateEventsToLoops(program: Program): Program {
    const updated = JSON.parse(JSON.stringify(program)) as Program;
    const legacyEvents = (updated.events || []).filter(e => e.repeatEveryXCycles);

    if (legacyEvents.length === 0) return updated;

    if (!updated.loops) updated.loops = [];

    for (const event of legacyEvents) {
        const alreadyMigrated = updated.loops.some(l => l.title === event.title);
        if (alreadyMigrated) continue;

        updated.loops.push({
            id: event.id || crypto.randomUUID(),
            title: event.title,
            type: (event.type as Loop['type']) || 'custom',
            repeatEveryXLoops: event.repeatEveryXCycles!,
            durationType: 'week',
            sessions: event.sessions,
        });
    }

    // Remove migrated events from legacy array
    updated.events = (updated.events || []).filter(e => !e.repeatEveryXCycles);

    return updated;
}

// ─── Formateo ───

export function formatLoopCountdown(daysUntil: number): string {
    if (daysUntil <= 0) return 'Ahora';
    if (daysUntil === 1) return '1 día';
    if (daysUntil < 7) return `${daysUntil} días`;
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
