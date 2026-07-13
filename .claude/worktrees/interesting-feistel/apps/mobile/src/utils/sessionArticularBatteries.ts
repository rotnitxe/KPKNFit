// apps/mobile/src/utils/sessionArticularBatteries.ts
// Baterías articulares afectadas por una sesión - Ported from PWA
import type { Session } from '../types/workout';

export interface SessionArticularBattery {
  id: string;
  label: string;
  shortLabel: string;
  battery: number;
}

export function getSessionArticularBatteries(
  session: Session,
  exerciseList: any[],
  articularBatteries: Record<string, { recoveryScore: number }>
): SessionArticularBattery[] {
  if (!session) return [];
  // Logic simplified for mobile port initial phase
  return [];
}
