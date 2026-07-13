// components/BodyProgressSheet.tsx
// Sheet desde abajo (~90% altura) — registro corporal unificado

import React, { useState, useEffect } from 'react';
import ReactDOM from 'react-dom';
import { BodyProgressLog, Settings, NutritionPlan } from '../types';
import { XIcon, SaveIcon } from './icons';
import { useAppDispatch } from '../contexts/AppContext';

const PREDEFINED_MEASUREMENTS = [
  'Pecho', 'Cintura', 'Cadera', 'Cuello',
  'Bíceps (Izq)', 'Bíceps (Der)', 'Antebrazo (Izq)', 'Antebrazo (Der)',
  'Muslo (Izq)', 'Muslo (Der)', 'Pantorrilla (Izq)', 'Pantorrilla (Der)',
];

interface BodyProgressSheetProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (log: BodyProgressLog) => void;
  settings: Settings;
  initialLog?: BodyProgressLog | null;
  activePlan?: NutritionPlan | null;
}

export const BodyProgressSheet: React.FC<BodyProgressSheetProps> = ({
  isOpen,
  onClose,
  onSave,
  settings,
  initialLog,
  activePlan,
}) => {
  const { addToast } = useAppDispatch();
  const [weight, setWeight] = useState('');
  const [bodyFat, setBodyFat] = useState('');
  const [muscleMass, setMuscleMass] = useState('');
  const [measurements, setMeasurements] = useState<Record<string, number>>({});
  const [measurementsExpanded, setMeasurementsExpanded] = useState(false);

  useEffect(() => {
    if (isOpen && initialLog) {
      setWeight(initialLog.weight != null ? String(initialLog.weight) : '');
      setBodyFat(initialLog.bodyFatPercentage != null ? String(initialLog.bodyFatPercentage) : '');
      setMuscleMass(initialLog.muscleMassPercentage != null ? String(initialLog.muscleMassPercentage) : '');
      setMeasurements(initialLog.measurements ?? {});
    } else if (isOpen && !initialLog) {
      setWeight('');
      setBodyFat('');
      setMuscleMass('');
      setMeasurements({});
    }
  }, [isOpen, initialLog]);

  const handleMeasurementChange = (key: string, value: string) => {
    const numValue = parseFloat(value);
    setMeasurements(prev => ({
      ...prev,
      [key]: isNaN(numValue) ? 0 : numValue,
    }));
  };

  const handleSave = () => {
    if (!weight) {
      addToast('El peso es obligatorio.', 'danger');
      return;
    }
    const finalMeasurements = Object.entries(measurements).reduce((acc, [key, value]) => {
      if (typeof value === 'number' && value > 0) acc[key] = value;
      return acc;
    }, {} as Record<string, number>);

    const newLog: BodyProgressLog = {
      id: initialLog?.id ?? crypto.randomUUID(),
      date: initialLog?.date ?? new Date().toISOString(),
      weight: parseFloat(weight),
      bodyFatPercentage: bodyFat ? parseFloat(bodyFat) : undefined,
      muscleMassPercentage: muscleMass ? parseFloat(muscleMass) : undefined,
      measurements: Object.keys(finalMeasurements).length > 0 ? finalMeasurements : undefined,
    };
    onSave(newLog);
    onClose();
  };

  if (!isOpen) return null;

  const content = (
    <>
      <div
        className="fixed inset-0 z-[200] bg-black/30 animate-fade-in"
        onClick={onClose}
        aria-hidden
      />
      <div
        className="fixed left-0 right-0 bottom-0 z-[201] bg-[#e5e5e5] animate-slide-up flex flex-col"
        style={{ height: '90vh', maxHeight: '90dvh' }}
      >
        <div className="flex-shrink-0 flex items-center justify-between px-4 py-4">
          <h2 className="text-base font-black text-[#1a1a1a] uppercase tracking-tight">
            {initialLog ? 'Editar registro' : 'Registrar avance'}
          </h2>
          <button
            onClick={onClose}
            className="p-2 text-[#525252] hover:text-[#1a1a1a] transition-colors"
            aria-label="Cerrar"
          >
            <XIcon size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-[10px] font-black text-[#525252] uppercase tracking-widest mb-1.5">
                Peso ({settings.weightUnit}) *
              </label>
              <input
                type="number"
                step="0.1"
                value={weight}
                onChange={e => setWeight(e.target.value)}
                className="w-full px-3 py-2.5 bg-white border border-[#a3a3a3] text-[#1a1a1a] text-sm focus:border-[#525252] focus:outline-none"
                required
              />
            </div>
            <div>
              <label className="block text-[10px] font-black text-[#525252] uppercase tracking-widest mb-1.5">
                % Grasa
              </label>
              <input
                type="number"
                step="0.1"
                value={bodyFat}
                onChange={e => setBodyFat(e.target.value)}
                className="w-full px-3 py-2.5 bg-white border border-[#a3a3a3] text-[#1a1a1a] text-sm focus:border-[#525252] focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-[10px] font-black text-[#525252] uppercase tracking-widest mb-1.5">
                % Músculo
              </label>
              <input
                type="number"
                step="0.1"
                value={muscleMass}
                onChange={e => setMuscleMass(e.target.value)}
                className="w-full px-3 py-2.5 bg-white border border-[#a3a3a3] text-[#1a1a1a] text-sm focus:border-[#525252] focus:outline-none"
              />
            </div>
          </div>

          <div>
            <button
              type="button"
              onClick={() => setMeasurementsExpanded(!measurementsExpanded)}
              className="text-[10px] font-black text-[#525252] uppercase tracking-widest hover:text-[#1a1a1a] transition-colors"
            >
              Medidas (cm) {measurementsExpanded ? '−' : '+'}
            </button>
            {measurementsExpanded && (
              <div className="grid grid-cols-2 gap-x-4 gap-y-2 mt-3">
                {PREDEFINED_MEASUREMENTS.map(name => (
                  <div key={name}>
                    <label className="block text-[9px] text-[#525252] mb-1">{name}</label>
                    <input
                      type="number"
                      step="0.1"
                      value={measurements[name] ?? ''}
                      onChange={e => handleMeasurementChange(name, e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-[#a3a3a3] text-[#1a1a1a] text-sm focus:border-[#525252] focus:outline-none"
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="flex-shrink-0 p-4 pt-0 pb-[max(1rem, env(safe-area-inset-bottom))]">
          <button
            onClick={handleSave}
            className="w-full py-3 bg-white text-[#1a1a1a] font-black text-sm uppercase tracking-wider border border-[#a3a3a3] hover:bg-[#f5f5f5] transition-colors flex items-center justify-center gap-2"
          >
            <SaveIcon size={16} />
            Guardar
          </button>
        </div>
      </div>
    </>
  );

  return ReactDOM.createPortal(content, document.body);
};
