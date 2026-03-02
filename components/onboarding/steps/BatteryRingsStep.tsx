// Paso: Anillos de batería SNC, Columna, músculos. Estética Tú.
import React, { useState } from 'react';
import { applyPrecalibrationToBattery, applyPrecalibrationReadinessOnly } from '../../../services/auge';
import type { PrecalibrationExerciseInput, PrecalibrationReadinessInput } from '../../../services/auge';
import type { Settings } from '../../../types';
import type { ExerciseMuscleInfo } from '../../../types';

interface BatteryRingsStepProps {
  exercises: PrecalibrationExerciseInput[];
  settings: Settings;
  exerciseList: ExerciseMuscleInfo[];
  onApplyCalibration: (calibration: Settings['batteryCalibration']) => void;
  onComplete: () => void;
}

export const BatteryRingsStep: React.FC<BatteryRingsStepProps> = ({
  exercises,
  settings,
  exerciseList,
  onApplyCalibration,
  onComplete,
}) => {
  const [represents, setRepresents] = useState<boolean | null>(null);
  const [sleepQuality, setSleepQuality] = useState(3);
  const [stressLevel, setStressLevel] = useState(3);
  const [doms, setDoms] = useState(3);
  const [motivation, setMotivation] = useState(3);

  const handleFinish = (doesRepresent: boolean) => {
    const readiness: PrecalibrationReadinessInput = { sleepQuality, stressLevel, doms, motivation };
    if (doesRepresent) {
      if (exercises.length > 0) {
        const deltas = applyPrecalibrationToBattery(exercises, readiness, exerciseList, settings);
        onApplyCalibration({
          cnsDelta: deltas.cnsDelta,
          muscularDelta: deltas.muscularDelta,
          spinalDelta: deltas.spinalDelta,
          lastCalibrated: new Date().toISOString(),
          precalibrationContext: {},
        });
      } else {
        const deltas = applyPrecalibrationReadinessOnly(readiness);
        onApplyCalibration({
          ...deltas,
          lastCalibrated: new Date().toISOString(),
          precalibrationContext: {},
        });
      }
    }
    onComplete();
  };

  if (represents === null) {
    return (
      <div className="flex flex-col min-h-0 flex-1">
        <div className="flex-1 overflow-y-auto px-4 py-6 custom-scrollbar">
          <h2 className="text-lg font-medium text-white mb-1">Anillos de batería</h2>
          <p className="text-sm text-[#a3a3a3] mb-6">
            {exercises.length > 0
              ? 'Según los ejercicios indicados, la batería se calibrará automáticamente.'
              : 'Sin entrenamientos recientes, todo estará al 100%.'}
          </p>
          <p className="text-white font-medium mb-4">¿Te representa este estado inicial?</p>
          <div className="flex gap-3">
            <button
              onClick={() => handleFinish(true)}
              className="flex-1 py-4 bg-white text-[#1a1a1a] font-medium text-sm"
            >
              Sí
            </button>
            <button
              onClick={() => setRepresents(false)}
              className="flex-1 py-4 bg-[#252525] text-white font-medium text-sm border border-[#3f3f3f]"
            >
              No, ajustar
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-0 flex-1">
      <div className="flex-1 overflow-y-auto px-4 py-6 custom-scrollbar">
        <h2 className="text-lg font-medium text-white mb-1">Ajustar batería inicial</h2>
        <p className="text-sm text-[#a3a3a3] mb-6">Readiness base para calibrar SNC, Columna y músculos.</p>
        <div className="space-y-4">
          {[
            { label: 'Calidad del sueño', value: sleepQuality, set: setSleepQuality },
            { label: 'Estrés del SNC', value: stressLevel, set: setStressLevel },
            { label: 'Daño muscular', value: doms, set: setDoms },
            { label: 'Motivación', value: motivation, set: setMotivation },
          ].map(({ label, value, set }) => (
            <div key={label} className="bg-[#252525] p-4 border border-[#3f3f3f]">
              <div className="flex justify-between items-center mb-2">
                <span className="text-sm text-[#a3a3a3]">{label}</span>
                <span className="text-white font-medium">{value}/5</span>
              </div>
              <input
                type="range"
                min={1}
                max={5}
                value={value}
                onChange={(e) => set(parseInt(e.target.value))}
                className="w-full h-2 bg-[#1a1a1a] appearance-none cursor-pointer accent-white"
              />
            </div>
          ))}
        </div>
      </div>
      <div className="shrink-0 p-4 border-t border-[#2a2a2a]">
        <button
          onClick={() => handleFinish(true)}
          className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm"
        >
          Aplicar y terminar
        </button>
      </div>
    </div>
  );
};
