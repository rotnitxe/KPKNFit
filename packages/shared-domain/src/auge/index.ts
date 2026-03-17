
import { 
    AugeExerciseMetrics,
    MuscleRecoveryStatus,
    AugeAdaptiveCache,
    DailyWellnessLog,
    AugeReadinessVerdict
} from '@kpkn/shared-types';
import { calculateMuscleRecovery } from './recovery';
import { 
    getDynamicAugeMetrics, 
    calculateSetBatteryDrain, 
    calculatePersonalizedBatteryTanks,
    isSetEffective
} from './fatigue';

export * from './fatigue';
export * from './recovery';
export * from './ttc';
export * from './tendonRecovery';
export * from './structuralReadiness';
export * from './tendonAlerts';
export * from './nutritionRecovery';

/**
 * --- AUGE ENGINE FAÇADE ---
 * Punto de entrada unificado en shared-domain.
 */

export interface AugeEngineConfig {
    settings: any;
    adaptiveCache: AugeAdaptiveCache;
    wellbeing?: DailyWellnessLog;
    history: any[];
    cnsBattery: number;
}

export const computeAugeReadiness = (config: AugeEngineConfig): AugeReadinessVerdict => {
    const { settings, wellbeing, cnsBattery } = config;
    let recoveryTimeMultiplier = 1.0;
    const diagnostics: string[] = [];

    // 1. Evaluador de Estilo de Vida
    const sleepHours = wellbeing?.sleepHours || 7.5;
    if (sleepHours < 6) {
        recoveryTimeMultiplier *= 1.5;
        diagnostics.push("Falta de sueño detectada (<6h). Tu recarga está severamente frenada hoy.");
    }

    if (wellbeing && wellbeing.stressLevel >= 4) {
        recoveryTimeMultiplier *= 1.4;
        diagnostics.push("Tus niveles altos de estrés están liberando cortisol, bloqueando la recuperación del sistema nervioso.");
    }

    // 2. Nutrición
    if (settings.calorieGoalObjective === 'deficit') {
        recoveryTimeMultiplier *= 1.3;
        diagnostics.push("Al estar en déficit calórico, tienes recursos limitados para reparar tejido dañado.");
    }

    // 3. Recomendación y Estatus
    let status: 'green' | 'yellow' | 'red' = 'green';
    let recommendation = "Estás en condiciones óptimas. Tienes luz verde para buscar récords personales o tirar pesado.";
    const isDeficit = settings.calorieGoalObjective === 'deficit';

    if (isDeficit) {
        recommendation = "En régimen de déficit: prioriza mantener masa muscular. Evita volumen excesivo o ir al fallo en cada serie.";
    }

    if (cnsBattery < 40 || recoveryTimeMultiplier >= 1.8) {
        status = 'red';
        recommendation = isDeficit
            ? "Déficit + fatiga alta. Riesgo de pérdida muscular. Descanso o sesión muy ligera. Prioriza proteína y sueño."
            : "Tu sistema nervioso no está listo. Tu falta de sueño/estrés está frenando tu recarga. Considera descanso total o una sesión muy ligera de movilidad.";
    } else if (cnsBattery < 70 || recoveryTimeMultiplier >= 1.3) {
        status = 'yellow';
        recommendation = isDeficit
            ? "Déficit activo. Reduce volumen o RPE hoy para poder recuperarte y proteger tu masa muscular."
            : "Tienes fatiga residual o factores externos en contra. Cambia el trabajo pesado por técnica, o reduce tu volumen planificado al 50%.";
    }

    if (diagnostics.length === 0) {
        diagnostics.push("Tus hábitos de las últimas 24 hrs fueron excelentes. La síntesis de recuperación está a tope.");
    }

    return {
        status,
        stressMultiplier: parseFloat(recoveryTimeMultiplier.toFixed(2)),
        cnsBattery,
        diagnostics,
        recommendation
    };
};
