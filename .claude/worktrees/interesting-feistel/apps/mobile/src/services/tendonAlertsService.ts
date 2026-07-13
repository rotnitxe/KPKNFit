// apps/mobile/src/services/tendonAlertsService.ts
// Alertas de desbalance tendinoso — Ported from PWA

import { getStructuralReadinessForMuscles } from './structuralReadinessService';
import { ARTICULAR_BATTERIES } from '../data/articularBatteryConfig';
import {
  getTendonImbalanceAlerts as getSharedAlerts,
  getTendonCompensationSuggestions
} from '@kpkn/shared-domain';
import type { ArticularBatteryId, TendonImbalanceAlert, TendonCompensationSuggestion } from '@kpkn/shared-types';

export type { TendonImbalanceAlert, TendonCompensationSuggestion };

export function getTendonImbalanceAlerts(
  perMuscle: Record<string, number>,
  articularBatteries: Record<ArticularBatteryId, { recoveryScore: number }> | undefined,
  sessionMuscleIds: string[] = []
): TendonImbalanceAlert[] {
  return getSharedAlerts(
    perMuscle,
    articularBatteries,
    {
      getStructuralReadinessForMuscles,
      getArticularLabel: (id: ArticularBatteryId) => ARTICULAR_BATTERIES.find((b) => b.id === id)?.shortLabel ?? id
    },
    sessionMuscleIds
  );
}

export { getTendonCompensationSuggestions };
