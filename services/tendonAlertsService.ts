import type { ArticularBatteryId } from '../data/articularBatteryConfig';
import { getStructuralReadinessForMuscles } from './structuralReadinessService';

const ARTICULAR_LABELS: Record<ArticularBatteryId, string> = {
  shoulder: 'Hombro',
  elbow: 'Codo',
  knee: 'Rodilla',
  hip: 'Cadera',
  ankle: 'Tobillo',
  cervical: 'Cuello',
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

export function getTendonImbalanceAlerts(
  perMuscle: Record<string, number>,
  articularBatteries: Record<ArticularBatteryId, { recoveryScore: number }> | undefined,
  sessionMuscleIds: string[] = []
): TendonImbalanceAlert[] {
  if (!articularBatteries) return [];

  const alerts: TendonImbalanceAlert[] = [];
  const readiness = getStructuralReadinessForMuscles(perMuscle, articularBatteries, sessionMuscleIds);

  for (const item of readiness) {
    for (const articularId of item.relatedArticularIds) {
      const articularState = articularBatteries[articularId];
      if (!articularState) continue;

      const articularBattery = articularState.recoveryScore;
      const gap = item.muscleBattery - articularBattery;
      if (gap <= IMBALANCE_THRESHOLD) continue;

      const articularLabel = ARTICULAR_LABELS[articularId];
      alerts.push({
        type: articularBattery < 40 ? 'danger' : 'warning',
        muscleLabel: item.muscleLabel,
        articularLabel,
        muscleBattery: item.muscleBattery,
        articularBattery,
        gap,
        message: `Tu lectura muscular de ${item.muscleLabel} va mejor (${item.muscleBattery}%), pero el tejido de ${articularLabel} sigue atrasado (${articularBattery}%). Usa la media combinada antes de subir cargas o meter explosividad hoy.`,
      });
    }
  }

  return alerts;
}

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
      message: 'Colageno hidrolizado + Vitamina C 30-60 min antes del entrenamiento puede mejorar la sintesis de colageno en tendones y acelerar la recuperacion (~10%).',
    });
  }

  const veryLowBatteries = lowBatteries.filter(([, ab]) => ab.recoveryScore < 30);
  if (veryLowBatteries.length > 0) {
    suggestions.push({
      type: 'biomechanical',
      title: 'Ajuste biomecanico',
      message: 'Considera sustituir ejercicios pesados o pliometricos por alternativas isometricas o de bajo TTC para no agravar el dano tendinoso.',
    });
  }

  return suggestions;
}
