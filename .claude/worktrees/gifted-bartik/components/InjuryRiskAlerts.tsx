
import React, { useMemo } from 'react';
import { WorkoutLog } from '../types';
import Card from './ui/Card';
import { AlertTriangleIcon } from './icons';

interface InjuryRiskAlertsProps {
  history: WorkoutLog[];
}

const InjuryRiskAlerts: React.FC<InjuryRiskAlertsProps> = ({ history }) => {
  const alerts = useMemo(() => {
    if (history.length < 3) return [];

    const discomfortMap = new Map<string, { exercises: string[], count: number }>();

    history.forEach(log => {
      if (log.discomforts && log.discomforts.length > 0) {
        const exerciseNames = (log.completedExercises || []).map(ex => ex.exerciseName);
        log.discomforts.forEach(discomfort => {
          const entry = discomfortMap.get(discomfort) || { exercises: [], count: 0 };
          entry.count += 1;
          exerciseNames.forEach(exName => {
            if (!entry.exercises.includes(exName)) {
              entry.exercises.push(exName);
            }
          });
          discomfortMap.set(discomfort, entry);
        });
      }
    });
    
    const significantAlerts: { discomfort: string; exercise: string }[] = [];
    discomfortMap.forEach((data, discomfort) => {
        if (data.count > 1) { // Alert if a discomfort is reported more than once
            // This is a simple correlation, a more advanced version could find the most common exercise
            const mostLikelyExercise = data.exercises[0]; // For now, just point to the first related exercise
            if (mostLikelyExercise) {
                significantAlerts.push({ discomfort, exercise: mostLikelyExercise });
            }
        }
    });

    return significantAlerts;

  }, [history]);

  return (
    <Card>
      <h3 className="text-xl font-bold text-white mb-3 flex items-center gap-2">
        <AlertTriangleIcon className="text-yellow-400"/> Alertas de Riesgo
      </h3>
      {alerts.length > 0 ? (
        <div className="space-y-3">
          {alerts.map((alert, index) => (
            <div key={index} className="bg-yellow-950/50 border-l-4 border-yellow-500 p-3 rounded-r-md">
              <p className="font-semibold text-yellow-300">{alert.discomfort}</p>
              <p className="text-sm text-slate-300">
                Has reportado esta molestia repetidamente. Considera revisar tu técnica o reducir la carga en ejercicios como <span className="font-bold">{alert.exercise}</span>.
              </p>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-sm text-slate-400">
          No se han detectado patrones de riesgo significativos en tu historial reciente. ¡Sigue así y escucha a tu cuerpo!
        </p>
      )}
    </Card>
  );
};

export default InjuryRiskAlerts;
