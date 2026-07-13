// components/AddBodyLogModal.tsx
import React, { useState, useEffect } from 'react';
import { BodyProgressLog, Settings } from '../types';
import { TacticalModal } from './ui/TacticalOverlays';
import Button from './ui/Button';
import { SaveIcon } from './icons';
import { useAppDispatch } from '../contexts/AppContext';

interface AddBodyLogModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (log: BodyProgressLog) => void;
  settings: Settings;
  isOnline: boolean;
  /** Si se proporciona, el modal actúa en modo edición */
  initialLog?: BodyProgressLog | null;
}

const PREDEFINED_MEASUREMENTS = [
  "Pecho", "Cintura", "Cadera", "Cuello",
  "Bíceps (Izq)", "Bíceps (Der)", "Antebrazo (Izq)", "Antebrazo (Der)",
  "Muslo (Izq)", "Muslo (Der)", "Pantorrilla (Izq)", "Pantorrilla (Der)",
];

const AddBodyLogModal: React.FC<AddBodyLogModalProps> = ({ isOpen, onClose, onSave, settings, initialLog }) => {
  const { addToast } = useAppDispatch();
  const [weight, setWeight] = useState('');
  const [bodyFat, setBodyFat] = useState('');
  const [muscleMass, setMuscleMass] = useState('');
  const [measurements, setMeasurements] = useState<{ [key: string]: number }>({});

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
      addToast("El peso es un campo obligatorio.", "danger");
      return;
    }
    const finalMeasurements = Object.entries(measurements).reduce((acc, [key, value]) => {
        if (typeof value === 'number' && value > 0) {
            acc[key] = value;
        }
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

  return (
    <TacticalModal isOpen={isOpen} onClose={onClose} title={initialLog ? "Editar Registro Corporal" : "Añadir Registro Corporal"}>
      <div className="space-y-4 max-h-[70vh] overflow-y-auto p-1">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Peso ({settings.weightUnit}) *</label>
            <input
              type="number"
              step="0.1"
              value={weight}
              onChange={e => setWeight(e.target.value)}
              className="w-full px-3 py-2 bg-slate-800/50 border border-slate-600 rounded-lg text-white"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">% Grasa Corporal</label>
            <input
              type="number"
              step="0.1"
              value={bodyFat}
              onChange={e => setBodyFat(e.target.value)}
              className="w-full px-3 py-2 bg-slate-800/50 border border-slate-600 rounded-lg text-white"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">% Músculo</label>
            <input
              type="number"
              step="0.1"
              value={muscleMass}
              onChange={e => setMuscleMass(e.target.value)}
              className="w-full px-3 py-2 bg-slate-800/50 border border-slate-600 rounded-lg text-white"
            />
          </div>
        </div>

        <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">Medidas (cm)</label>
            <div className="grid grid-cols-2 gap-x-4 gap-y-2">
                {PREDEFINED_MEASUREMENTS.map(name => (
                    <div key={name}>
                         <label className="block text-xs font-medium text-slate-400 mb-1">{name}</label>
                         <input
                            type="number"
                            step="0.1"
                            value={measurements[name] || ''}
                            onChange={e => handleMeasurementChange(name, e.target.value)}
                            className="w-full px-3 py-2 bg-slate-800/50 border border-slate-600 rounded-lg text-white"
                         />
                    </div>
                ))}
            </div>
        </div>

        <div className="flex justify-end gap-4 pt-4 border-t border-slate-700">
          <Button onClick={onClose} variant="secondary">Cancelar</Button>
          <Button onClick={handleSave}><SaveIcon size={16}/> Guardar Registro</Button>
        </div>
      </div>
    </TacticalModal>
  );
};

export default AddBodyLogModal;
