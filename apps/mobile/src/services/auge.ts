// apps/mobile/src/services/auge.ts
// AUGE v3.0 — SINGLE SOURCE OF TRUTH — Ported from PWA

export {
    getDynamicAugeMetrics,
    getEffectiveRPE,
    calculatePersonalizedBatteryTanks,
    calculateSetBatteryDrain,
    calculatePredictedSessionDrain,
    isSetEffective,
    calculateCompletedSessionStress,
    calculateCompletedSessionDrainBreakdown,
    WEEKLY_CNS_FATIGUE_REFERENCE,
} from './fatigueService';

export {
    calculateMuscleBattery,
    calculateGlobalBatteries,
} from './recoveryService';

export {
    calculateTTC,
    calculateSetTendonDrain,
    getTendonDrainInput,
} from './ttcService';

/**
 * Clasificación semántica del estrés de sesión.
 */
export const classifyStressLevel = (score: number): { label: string; color: string; barColor: string } => {
    if (score < 40) return { label: 'Bajo', color: '#38BDF8', barColor: '#0EA5E9' };
    if (score < 80) return { label: 'Óptimo', color: '#00FF9D', barColor: '#00FF9D' };
    if (score < 120) return { label: 'Alto', color: '#FFD600', barColor: '#FFD600' };
    return { label: 'Excesivo', color: '#FF2E43', barColor: '#FF2E43' };
};
