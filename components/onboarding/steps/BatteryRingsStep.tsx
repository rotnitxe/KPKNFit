// Paso: Anillos de batería SNC, Columna, músculos. Estética Tú.
import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Battery, Brain, Activity, ChevronRight, Info, Zap } from 'lucide-react';
import { applyPrecalibrationToBattery, applyPrecalibrationReadinessOnly } from '../../../services/auge';
import type { PrecalibrationExerciseInput, PrecalibrationReadinessInput } from '../../../services/auge';
import type { Settings } from '../../../types';
import type { ExerciseMuscleInfo } from '../../../types';

// Subcomponente de los Anillos (Estética "Tú")
const RingProgress = ({ value, color, label, icon: Icon, onChange }: { value: number, color: string, label: string, icon: any, onChange: (val: number) => void }) => {
  const radius = 35;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (value / 100) * circumference;

  return (
    <div className="flex flex-col items-center gap-3 w-full">
      <div className="relative flex items-center justify-center w-24 h-24">
        <svg className="absolute inset-0 w-full h-full transform -rotate-90">
          <circle cx="48" cy="48" r={radius} stroke="currentColor" strokeWidth="6" fill="transparent" className="text-white/10" />
          <motion.circle
            initial={{ strokeDashoffset: circumference }} animate={{ strokeDashoffset }} transition={{ duration: 1, ease: "easeOut" }}
            cx="48" cy="48" r={radius} stroke={color} strokeWidth="6" fill="transparent" strokeDasharray={circumference} strokeLinecap="round" className="drop-shadow-lg"
          />
        </svg>
        <div className="absolute flex flex-col items-center justify-center text-center">
          <Icon size={16} className="mb-1 opacity-80" style={{ color }} />
          <span className="text-lg font-bold text-white">{Math.round(value)}%</span>
        </div>
      </div>
      <div className="w-full px-2 flex flex-col items-center gap-1">
        <span className="text-[10px] font-bold uppercase tracking-wider text-white/60">{label}</span>
        <input 
          type="range" min="0" max="100" value={value} onChange={(e) => onChange(Number(e.target.value))}
          className="w-full h-1.5 bg-white/10 rounded-lg appearance-none cursor-pointer mt-1" style={{ accentColor: color }}
        />
      </div>
    </div>
  );
};

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
  const [snc, setSnc] = useState(100);
  const [spinal, setSpinal] = useState(100);
  const [peripheral, setPeripheral] = useState(100);
  const [muscleBreakdown, setMuscleBreakdown] = useState<Record<string, number>>({});

  // Calcular batería inicial al montar el componente usando tu lógica de AUGE
  useEffect(() => {
    // Usamos un readiness base asumiendo un estado neutral inicial para el cálculo matemático
    const defaultReadiness: PrecalibrationReadinessInput = { sleepQuality: 3, stressLevel: 3, doms: 3, motivation: 3 };
    
    let initialDeltas;
    if (exercises.length > 0) {
      initialDeltas = applyPrecalibrationToBattery(exercises, defaultReadiness, exerciseList, settings);
    } else {
      initialDeltas = applyPrecalibrationReadinessOnly(defaultReadiness);
    }

    // Los deltas de AUGE son fatiga (0 a 100). La batería visible es 100 - fatiga.
    const deltaSNC = initialDeltas.cnsDelta || 0;
    const deltaSpinal = initialDeltas.spinalDelta || 0;
    const muscles: Record<string, number> = (initialDeltas.muscularDelta as any) || {};
    
    setSnc(Math.max(0, Math.min(100, 100 - deltaSNC)));
    setSpinal(Math.max(0, Math.min(100, 100 - deltaSpinal)));
    setMuscleBreakdown(muscles);

    // Forzamos el cálculo del promedio periférico
    const values = Object.values(muscles);
    if (values.length > 0) {
      const avgFatigue = values.reduce((a, b) => a + b, 0) / values.length;
      setPeripheral(Math.max(0, Math.min(100, 100 - avgFatigue)));
    } else {
      setPeripheral(100);
    }
  }, [exercises, exerciseList, settings]);

  const handleFinish = () => {
    // Convertir de vuelta la batería visual (100%) a fatiga/delta (0) para guardar
    const finalCnsDelta = 100 - snc;
    const finalSpinalDelta = 100 - spinal;
    
    // Si el usuario modificó manualmente el anillo periférico general, ajustamos los músculos proporcionalmente
    const finalMuscularDelta = { ...muscleBreakdown };
    const muscleKeys = Object.keys(finalMuscularDelta);
    const finalAverageDelta = 100 - peripheral;
    
    if (muscleKeys.length > 0) {
      muscleKeys.forEach(m => {
        finalMuscularDelta[m] = finalAverageDelta; 
      });
    } else if (finalAverageDelta > 0) {
      finalMuscularDelta['general'] = finalAverageDelta;
    }

    onApplyCalibration({
      cnsDelta: finalCnsDelta,
      muscularDelta: finalMuscularDelta as any,
      spinalDelta: finalSpinalDelta,
      lastCalibrated: new Date().toISOString(),
      precalibrationContext: {},
    } as any);
    
    setTimeout(() => {
      onComplete();
    }, 50);
  };

  return (
    <div className="flex flex-col min-h-0 flex-1 bg-[#0a0a0a]">
      <div className="flex-1 overflow-y-auto px-4 py-6 custom-scrollbar space-y-8">
        
        {/* Explicación de la Batería */}
        <div className="space-y-3">
          <h2 className="text-2xl font-black text-white tracking-tight flex items-center gap-2">
            BATERÍA <span className="text-[#facc15]">AUGE</span> <Zap size={24} className="text-[#facc15]" />
          </h2>
          <div className="bg-white/5 border border-white/10 rounded-2xl p-4 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-32 h-32 bg-[#facc15]/5 rounded-full blur-3xl -mr-10 -mt-10" />
            <p className="text-sm text-white/70 leading-relaxed relative z-10">
              Hemos estimado tu estado actual basado en tus últimos entrenamientos. 
              El <strong>SNC</strong> (Sistema Nervioso Central) regula tu fuerza máxima, el <strong>ESPINAL</strong> mide la carga en tu columna, y el <strong>PERIFÉRICO</strong> tu fatiga muscular. 
              <br/><br/>
              <span className="text-[#facc15] font-medium">Si no te sientes como indica el cálculo, ajusta los anillos deslizando la barra.</span>
            </p>
          </div>
        </div>

        {/* Anillos de Calibración */}
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-[#1a1a1a] border border-[#2a2a2a] rounded-3xl p-4 flex justify-center">
              <RingProgress value={snc} color="#3b82f6" label="SNC" icon={Brain} onChange={setSnc} />
            </div>
            <div className="bg-[#1a1a1a] border border-[#2a2a2a] rounded-3xl p-4 flex justify-center">
              <RingProgress value={spinal} color="#eab308" label="ESPINAL" icon={Activity} onChange={setSpinal} />
            </div>
          </div>

          <div className="bg-[#1a1a1a] border border-[#2a2a2a] rounded-3xl p-5 flex flex-col items-center">
            <div className="flex items-center gap-2 mb-4">
              <Info size={16} className="text-white/40" />
              <span className="text-xs font-medium text-white/40 uppercase tracking-wider">Fatiga Muscular Global</span>
            </div>
            <RingProgress value={peripheral} color="#ef4444" label="PERIFÉRICO" icon={Battery} onChange={setPeripheral} />
            
            {/* Desglose de músculos involucrados */}
            {Object.keys(muscleBreakdown).length > 0 ? (
              <div className="w-full mt-6 pt-4 border-t border-white/5">
                <p className="text-[11px] font-bold text-white/40 uppercase tracking-wider mb-3 text-center">
                  Músculos afectados por tus ejercicios
                </p>
                <div className="flex flex-wrap gap-2 justify-center">
                  {Object.entries(muscleBreakdown).map(([muscle, delta]) => (
                    <div key={muscle} className="bg-black/40 border border-white/5 px-3 py-1.5 rounded-lg flex items-center gap-2">
                      <span className="text-xs text-white/80 capitalize">{muscle}</span>
                      {/* Convertimos el delta de vuelta a nivel de batería para mostrarlo en verde/rojo */}
                      <span className={`text-xs font-bold ${delta > 50 ? 'text-[#ef4444]' : 'text-green-500'}`}>
                        {Math.round(100 - delta)}%
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <p className="mt-4 text-[11px] text-white/40 text-center">
                No registraste ejercicios. Tu musculatura está fresca.
              </p>
            )}
          </div>
        </div>

      </div>
      
      <div className="shrink-0 p-4 border-t border-[#2a2a2a] bg-[#0a0a0a]">
        <motion.button
          whileTap={{ scale: 0.98 }}
          onClick={handleFinish}
          className="w-full py-4 bg-[#facc15] text-black font-black text-sm rounded-2xl flex items-center justify-center gap-2 uppercase tracking-wide shadow-[0_0_20px_rgba(250,204,21,0.2)]"
        >
          <span>Confirmar Calibración</span>
          <ChevronRight size={20} strokeWidth={3} />
        </motion.button>
      </div>
    </div>
  );
};
