// apps/mobile/src/utils/sessionMusclesForBattery.ts
// Mapea músculos principales de la sesión - Ported from PWA
import { Session } from '../types/workout';

export interface SessionMuscleForBattery {
    id: string;
    label: string;
    battery: number;
}

export function getSessionMusclesWithBatteries(
    session: Session,
    exerciseList: any[],
    perMuscleBatteries: Record<string, number>
): SessionMuscleForBattery[] {
    if (!session) return [];
    // Logic simplified for mobile port initial phase
    return [];
}
