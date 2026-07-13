// Paso: Datos físicos. Todos omitibles. Estética Tú.
import React from 'react';
import type { Settings } from '../../../types';

const GENDER_OPTIONS: { id: string; label: string }[] = [
  { id: 'male', label: 'Hombre' },
  { id: 'female', label: 'Mujer' },
  { id: 'other', label: 'Otro' },
];

export interface PhysicalDataForm {
  name?: string;
  age?: number;
  sex?: string;
  weight?: number;
  height?: number;
  bodyFat?: number;
  muscleMass?: number;
}

interface PhysicalDataStepProps {
  initial?: Partial<PhysicalDataForm>;
  settings: Settings;
  onNext: (data: PhysicalDataForm) => void;
  onSkip: () => void;
}

export const PhysicalDataStep: React.FC<PhysicalDataStepProps> = ({
  initial,
  settings,
  onNext,
  onSkip,
}) => {
  const vitals = settings.userVitals || {};
  const [name, setName] = React.useState(initial?.name ?? settings.username ?? '');
  const [age, setAge] = React.useState(initial?.age != null ? String(initial.age) : (vitals.age != null ? String(vitals.age) : ''));
  const [sex, setSex] = React.useState(initial?.sex ?? vitals.gender ?? '');
  const [weight, setWeight] = React.useState(initial?.weight != null ? String(initial.weight) : (vitals.weight != null ? String(vitals.weight) : ''));
  const [height, setHeight] = React.useState(initial?.height != null ? String(initial.height) : (vitals.height != null ? String(vitals.height) : ''));
  const [bodyFat, setBodyFat] = React.useState(initial?.bodyFat != null ? String(initial.bodyFat) : (vitals.bodyFatPercentage != null ? String(vitals.bodyFatPercentage) : ''));
  const [muscleMass, setMuscleMass] = React.useState(initial?.muscleMass != null ? String(initial.muscleMass) : (vitals.muscleMassPercentage != null ? String(vitals.muscleMassPercentage) : ''));

  const handleNext = () => {
    onNext({
      name: name.trim() || undefined,
      age: age ? parseInt(age, 10) : undefined,
      sex: sex || undefined,
      weight: weight ? parseFloat(weight) : undefined,
      height: height ? parseFloat(height) : undefined,
      bodyFat: bodyFat ? parseFloat(bodyFat) : undefined,
      muscleMass: muscleMass ? parseFloat(muscleMass) : undefined,
    });
  };

  const inputCls = 'w-full bg-[#252525] border border-[#3f3f3f] px-4 py-3 text-white text-sm placeholder-[#737373] focus:border-[#525252] outline-none';

  return (
    <div className="flex flex-col min-h-0 flex-1">
      <div className="flex-1 overflow-y-auto px-4 py-6 custom-scrollbar">
        <h2 className="text-lg font-medium text-white mb-1">Tus datos físicos</h2>
        <p className="text-sm text-[#a3a3a3] mb-6">Opcional. Sirve para calorías y métricas.</p>
        <div className="space-y-4">
          <div>
            <label className="block text-xs text-[#737373] mb-1.5">Nombre</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ej: Juan"
              className={inputCls}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-[#737373] mb-1.5">Edad</label>
              <input
                type="number"
                min={10}
                max={120}
                value={age}
                onChange={(e) => setAge(e.target.value)}
                placeholder="30"
                className={inputCls}
              />
            </div>
            <div>
              <label className="block text-xs text-[#737373] mb-1.5">Sexo</label>
              <select value={sex} onChange={(e) => setSex(e.target.value)} className={inputCls}>
                <option value="">Seleccionar</option>
                {GENDER_OPTIONS.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-[#737373] mb-1.5">Peso (kg)</label>
              <input
                type="number"
                min={30}
                max={300}
                step={0.1}
                value={weight}
                onChange={(e) => setWeight(e.target.value)}
                placeholder="70"
                className={inputCls}
              />
            </div>
            <div>
              <label className="block text-xs text-[#737373] mb-1.5">Altura (cm)</label>
              <input
                type="number"
                min={100}
                max={250}
                value={height}
                onChange={(e) => setHeight(e.target.value)}
                placeholder="170"
                className={inputCls}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-[#737373] mb-1.5">% Grasa</label>
              <input
                type="number"
                min={5}
                max={60}
                step={0.5}
                value={bodyFat}
                onChange={(e) => setBodyFat(e.target.value)}
                placeholder="Ej: 18"
                className={inputCls}
              />
            </div>
            <div>
              <label className="block text-xs text-[#737373] mb-1.5">% Músculo</label>
              <input
                type="number"
                min={20}
                max={55}
                step={0.5}
                value={muscleMass}
                onChange={(e) => setMuscleMass(e.target.value)}
                placeholder="Ej: 42"
                className={inputCls}
              />
            </div>
          </div>
        </div>
      </div>
      <div className="shrink-0 p-4 flex flex-col gap-3 border-t border-[#2a2a2a]">
        <button onClick={handleNext} className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm">
          Continuar
        </button>
        <button onClick={onSkip} className="text-sm text-[#737373] py-2">
          Omitir — Puedes completarlo después en Nutrición
        </button>
      </div>
    </div>
  );
};
