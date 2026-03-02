// services/tendonAlertsService.ts
// Monitor de desfases músculo-tendón y protocolo de compensación

import type { ArticularBatteryId } from '../data/articularBatteryConfig';
import { MUSCLE_TO_ARTICULAR_BATTERIES } from './tendonRecoveryService';

const ARTICULAR_LABELS: Record<ArticularBatteryId, string> = {
  shoulder: 'Hombro',
  elbow: 'Codo',
  knee: 'Rodilla',
  hip: 'Cadera',
  ankle: 'Tobillo',
};

export interface TendonImbalanceAlert {
  type: 'warning' | 'danger';
  muscleLabel: string;
  articularLabel: string;
  muscleBattery: number;
  articularBattery: number;
  gap: number;
  message: string;
}

export interface TendonCompensationSuggestion {
  type: 'biomechanical' | 'nutrition';
  title: string;
  message: string;
}

const IMBALANCE_THRESHOLD = 30;

/**
 * Detecta desfases músculo-tendón: Batería_Muscular - Batería_Tendinosa > 30%
 */
export function getTendonImbalanceAlerts(
  perMuscle: Record<string, number>,
  articularBatteries: Record<ArticularBatteryId, { recoveryScore: number }> | undefined,
  sessionMuscleIds: string[] = []
): TendonImbalanceAlert[] {
  if (!articularBatteries) return [];

  const alerts: TendonImbalanceAlert[] = [];

  for (const [muscleId, articularIds] of Object.entries(MUSCLE_TO_ARTICULAR_BATTERIES)) {
    if (sessionMuscleIds.length > 0 && !sessionMuscleIds.includes(muscleId)) continue;

    const muscleBattery = perMuscle[muscleId] ?? 100;
    const muscleLabel = muscleId.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());

    for (const aid of articularIds) {
      const ab = articularBatteries[aid];
      if (!ab) continue;

      const articularBattery = ab.recoveryScore;
      const gap = muscleBattery - articularBattery;

      if (gap > IMBALANCE_THRESHOLD) {
        const articularLabel = ARTICULAR_LABELS[aid];
        alerts.push({
          type: articularBattery < 40 ? 'danger' : 'warning',
          muscleLabel,
          articularLabel,
          muscleBattery,
          articularBattery,
          gap,
          message: `Tus músculos (${muscleLabel}) están recuperados (${muscleBattery}%), pero tus tendones de ${articularLabel} aún tienen fatiga (${articularBattery}%). Evita cargas superiores al 80% 1RM o movimientos explosivos hoy para prevenir tendinopatías.`,
        });
      }
    }
  }

  return alerts;
}

/**
 * Sugerencias de compensación cuando la batería tendinosa está baja
 */
export function getTendonCompensationSuggestions(
  articularBatteries: Record<ArticularBatteryId, { recoveryScore: number }> | undefined,
  sessionArticularIds: ArticularBatteryId[] = []
): TendonCompensationSuggestion[] {
  if (!articularBatteries) return [];

  const suggestions: TendonCompensationSuggestion[] = [];
  const lowBatteries = (Object.entries(articularBatteries) as [ArticularBatteryId, { recoveryScore: number }][])
    .filter(([id, ab]) => (sessionArticularIds.length === 0 || sessionArticularIds.includes(id)) && ab.recoveryScore < 50);

  if (lowBatteries.length > 0) {
    suggestions.push({
      type: 'nutrition',
      title: 'Soporte nutricional',
      message: 'Colágeno hidrolizado + Vitamina C 30-60 min antes del entrenamiento puede mejorar la síntesis de colágeno en tendones y acelerar la recuperación (~10%).',
    });
  }

  const veryLowBatteries = lowBatteries.filter(([, ab]) => ab.recoveryScore < 30);
  if (veryLowBatteries.length > 0) {
    suggestions.push({
      type: 'biomechanical',
      title: 'Ajuste biomecánico',
      message: 'Considera sustituir ejercicios pesados o pliométricos por alternativas isométricas o de bajo TTC (ej. Sentadilla isométrica en pared en lugar de sentadillas pesadas) para no agravar el daño tendinoso.',
    });
  }

  return suggestions;
}
