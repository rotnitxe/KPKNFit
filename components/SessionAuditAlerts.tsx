import React, { useMemo } from 'react';
import { AlertTriangleIcon, InfoIcon } from './icons';
import { Exercise, ExerciseMuscleInfo } from '../types';
import { validateSessionVolume, normalizeMuscleGroup } from '../services/volumeCalculator';

interface SessionAuditAlertsProps {
    sessionExercises: { name: string; sets: any[]; exerciseDbId?: string }[]; 
    allExercisesDB: Exercise[]; // Necesitamos la DB para saber qué músculos ataca cada ejercicio
}

const SessionAuditAlerts: React.FC<SessionAuditAlertsProps> = ({ sessionExercises, allExercisesDB }) => {
    
// 1. Calcular Volumen de Hipertrofia y Fatiga Marginal (Doble Motor AUGE)
    const { sessionVolume, marginalFatigue } = useMemo(() => {
        const hyperMap: Record<string, number> = {};
        const fatigueMap: Record<string, number> = {};
        
        if (!sessionExercises) return { sessionVolume: hyperMap, marginalFatigue: fatigueMap };

        sessionExercises.forEach(ex => {
             const dbInfo = allExercisesDB.find(e => e.id === ex.exerciseDbId || e.name === ex.name);
             
             if (dbInfo && dbInfo.involvedMuscles) {
                 const setParam = ex.sets; 
                 const setCount = Array.isArray(setParam) ? setParam.length : (typeof setParam === 'number' ? setParam : 0);

                 const exerciseHyperImpacts: Record<string, number> = {};
                 const exerciseFatigueImpacts: Record<string, number> = {};

                 dbInfo.involvedMuscles.forEach((m) => {
                     const parentMuscle = normalizeMuscleGroup(m.muscle);
                     
                     // Reglas Biológicas AUGE: Separación de Hipertrofia y Fatiga
                     let hyperFactor = 0;
                     let fatigueFactor = 0;
                     
                     if (m.role === 'primary') { hyperFactor = 1.0; fatigueFactor = 1.0; }
                     else if (m.role === 'secondary') { hyperFactor = 0.5; fatigueFactor = 0.6; }
                     else if (m.role === 'stabilizer') { hyperFactor = 0.0; fatigueFactor = 0.3; } // Esfuerzo isométrico
                     else if (m.role === 'neutralizer') { hyperFactor = 0.0; fatigueFactor = 0.15; }

                     const hyperImpact = setCount * hyperFactor;
                     const fatigueImpact = setCount * fatigueFactor;

                     if (!exerciseHyperImpacts[parentMuscle] || hyperImpact > exerciseHyperImpacts[parentMuscle]) {
                         exerciseHyperImpacts[parentMuscle] = hyperImpact;
                     }
                     if (!exerciseFatigueImpacts[parentMuscle] || fatigueImpact > exerciseFatigueImpacts[parentMuscle]) {
                         exerciseFatigueImpacts[parentMuscle] = fatigueImpact;
                     }
                 });

                 // Volcar impactos únicos
                 Object.entries(exerciseHyperImpacts).forEach(([parent, vol]) => {
                     if (!hyperMap[parent]) hyperMap[parent] = 0;
                     hyperMap[parent] += vol;
                 });
                 Object.entries(exerciseFatigueImpacts).forEach(([parent, vol]) => {
                     if (!fatigueMap[parent]) fatigueMap[parent] = 0;
                     fatigueMap[parent] += vol;
                 });
             }
        });
        return { sessionVolume: hyperMap, marginalFatigue: fatigueMap };
    }, [sessionExercises, allExercisesDB]);

    const alerts: { type: 'warning' | 'error' | 'info'; msg: string }[] = [];
    
    // 2. Validar Techo de Sesión (Límite de Hipertrofia)
    (Object.entries(sessionVolume) as [string, number][]).forEach(([muscle, vol]) => {
        const check = validateSessionVolume(vol, muscle);
        if (!check.isValid && check.message) {
             alerts.push({ type: 'error', msg: check.message });
        } else if (check.isValid && check.message) {
             alerts.push({ type: 'warning', msg: check.message });
        }
    });

    // 3. Monitor de Integridad AUGE (Volumen Marginal Invisible)
    (Object.entries(marginalFatigue) as [string, number][]).forEach(([muscle, fatigueVol]) => {
        const hyperVol = sessionVolume[muscle] || 0;
        // Si hay alta fatiga (equiv. a ~8+ series de estabilizador) pero nulo/bajo estímulo hipertrófico
        if (fatigueVol >= 2.5 && hyperVol <= 0.5) {
            alerts.push({ 
                type: 'info', 
                msg: `🔋 Fatiga Marginal: Has acumulado mucho estrés isométrico en ${muscle} actuando como estabilizador. Su batería se ha drenado al trabajar en la sombra sin recibir estímulo de hipertrofia.` 
            });
        }
    });

    if (alerts.length === 0) return null;

    return (
        <div className="mb-4 space-y-2 animate-in fade-in slide-in-from-top-2">
            {alerts.map((alert, i) => (
                <div 
                    key={i} 
                    className={`flex items-start gap-3 p-3 rounded-xl border ${
                        alert.type === 'error' 
                        ? 'bg-red-500/10 border-red-500/20 text-red-200' 
                        : alert.type === 'warning'
                        ? 'bg-yellow-500/10 border-yellow-500/20 text-yellow-200'
                        : 'bg-orange-500/10 border-orange-500/20 text-orange-200'
                    }`}
                >
                    {alert.type === 'error' ? (
                        <AlertTriangleIcon size={16} className="text-red-500 shrink-0 mt-0.5" />
                    ) : alert.type === 'warning' ? (
                        <InfoIcon size={16} className="text-yellow-500 shrink-0 mt-0.5" />
                    ) : (
                        <InfoIcon size={16} className="text-orange-500 shrink-0 mt-0.5" />
                    )}
                    <p className="text-[11px] font-medium leading-relaxed">{alert.msg}</p>
                </div>
            ))}
        </div>
    );
};

export default SessionAuditAlerts;