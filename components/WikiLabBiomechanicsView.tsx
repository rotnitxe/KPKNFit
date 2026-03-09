import React, { useMemo, useState } from 'react';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import { ChevronRightIcon, ActivityIcon, RulerIcon } from './icons';
import BiomechanicalStickman, {
  calculateBiomechanicalPose,
  LiftType,
  LimbLengthsCm,
  PosePreset,
} from './wikilab/BiomechanicalStickman';

interface TestScenario {
  id: 'low-bar' | 'sumo';
  title: string;
  subtitle: string;
  liftType: LiftType;
  loadKg: number;
  preset: PosePreset;
}

const DEFAULT_HEIGHT_CM = 175;

const scenarioConfig: TestScenario[] = [
  {
    id: 'low-bar',
    title: 'Test Low Bar Squat (170kg)',
    subtitle: 'Torso inclinado + mayor brazo de momento en cadera.',
    liftType: 'low-bar-squat',
    loadKg: 170,
    preset: {
      hipBackRatio: 0.13,
      hipHeightRatio: 0.31,
    },
  },
  {
    id: 'sumo',
    title: 'Test Sumo Deadlift (200kg)',
    subtitle: 'Torso más vertical + apertura de caderas en despegue.',
    liftType: 'sumo-deadlift',
    loadKg: 200,
    preset: {
      hipBackRatio: 0.05,
      hipHeightRatio: 0.39,
      barHeightRatio: 0.04,
    },
  },
];

const deriveAverageLengths = (heightCm: number): LimbLengthsCm => ({
  femur: heightCm * 0.245,
  tibia: heightCm * 0.257,
  torso: heightCm * 0.302,
  arms: heightCm * 0.32,
});

const sanitizeLengths = (incoming: Partial<LimbLengthsCm>, fallback: LimbLengthsCm): LimbLengthsCm => ({
  femur: incoming.femur && incoming.femur > 10 ? incoming.femur : fallback.femur,
  tibia: incoming.tibia && incoming.tibia > 10 ? incoming.tibia : fallback.tibia,
  torso: incoming.torso && incoming.torso > 10 ? incoming.torso : fallback.torso,
  arms: incoming.arms && incoming.arms > 10 ? incoming.arms : fallback.arms,
});

const WikiLabBiomechanicsView: React.FC = () => {
  const { handleBack } = useAppDispatch();
  const { biomechanicalData } = useAppState();

  const [activeScenario, setActiveScenario] = useState<TestScenario>(scenarioConfig[0]);

  const athleteHeight = biomechanicalData?.height && biomechanicalData.height > 100 ? biomechanicalData.height : DEFAULT_HEIGHT_CM;

  const fallbackLengths = useMemo(() => deriveAverageLengths(athleteHeight), [athleteHeight]);

  const lengthsCm = useMemo(
    () =>
      sanitizeLengths(
        {
          femur: biomechanicalData?.femurLength,
          tibia: biomechanicalData?.tibiaLength,
          torso: biomechanicalData?.torsoLength,
          arms:
            (biomechanicalData?.humerusLength || 0) + (biomechanicalData?.forearmLength || 0) ||
            undefined,
        },
        fallbackLengths
      ),
    [biomechanicalData, fallbackLengths]
  );

  const solve = useMemo(
    () => calculateBiomechanicalPose(athleteHeight, lengthsCm, activeScenario.liftType, activeScenario.preset),
    [athleteHeight, lengthsCm, activeScenario]
  );

  const movementBias = solve.momentArms.hipCm > solve.momentArms.kneeCm ? 'Dominante de cadera' : 'Dominante de rodilla';

  return (
    <div className="min-h-screen bg-[#FDFCFE] px-5 pb-32">
      <header className="pt-6 pb-5 flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <button
            onClick={handleBack}
            className="w-10 h-10 rounded-full bg-white/80 border border-black/[0.06] flex items-center justify-center text-[#49454F] shadow-sm"
            aria-label="Volver"
          >
            <ChevronRightIcon size={20} className="rotate-180" />
          </button>
          <div className="px-3 py-1.5 rounded-full bg-[#ECE6F0] border border-black/[0.04] text-[9px] font-black uppercase tracking-[0.16em] text-[#49454F]">
            WikiLab Engineering
          </div>
          <div className="w-10" />
        </div>

        <div>
          <h1 className="text-2xl font-black text-[#1D1B20] tracking-tight">Palitos Biomecánicos</h1>
          <p className="text-sm text-[#49454F] opacity-70 mt-1">
            Motor SVG con cinemática inversa: barra sobre mid-foot, nodos conectados y brazos de momento visibles.
          </p>
        </div>
      </header>

      <section className="grid grid-cols-1 gap-2 mb-4">
        {scenarioConfig.map((scenario) => {
          const isActive = scenario.id === activeScenario.id;
          return (
            <button
              key={scenario.id}
              onClick={() => setActiveScenario(scenario)}
              className={`w-full text-left rounded-2xl px-4 py-3 border transition-all ${
                isActive
                  ? 'bg-[#1D1B20] text-white border-[#1D1B20] shadow-lg'
                  : 'bg-white/80 text-[#1D1B20] border-black/[0.08] hover:bg-white'
              }`}
            >
              <p className="text-[12px] font-black uppercase tracking-wide">{scenario.title}</p>
              <p className={`text-[11px] mt-1 ${isActive ? 'text-white/75' : 'text-[#49454F] opacity-70'}`}>{scenario.subtitle}</p>
            </button>
          );
        })}
      </section>

      <BiomechanicalStickman
        heightCm={athleteHeight}
        lengthsCm={lengthsCm}
        liftType={activeScenario.liftType}
        loadKg={activeScenario.loadKg}
        preset={activeScenario.preset}
      />

      <section className="mt-4 grid grid-cols-2 gap-2">
        <div className="rounded-2xl bg-white/85 border border-black/[0.06] p-3">
          <p className="text-[10px] font-black uppercase tracking-wider text-[#49454F] opacity-70">Ángulo Rodilla</p>
          <p className="text-xl font-black text-[#1D1B20]">{solve.angles.kneeDeg.toFixed(1)}°</p>
        </div>
        <div className="rounded-2xl bg-white/85 border border-black/[0.06] p-3">
          <p className="text-[10px] font-black uppercase tracking-wider text-[#49454F] opacity-70">Ángulo Cadera</p>
          <p className="text-xl font-black text-[#1D1B20]">{solve.angles.hipDeg.toFixed(1)}°</p>
        </div>
      </section>

      <section className="mt-2 rounded-2xl bg-white/85 border border-black/[0.06] p-4 space-y-2">
        <div className="flex items-center gap-2 text-[#1D1B20]">
          <ActivityIcon size={16} />
          <p className="text-[11px] font-black uppercase tracking-wider">Diagnóstico instantáneo</p>
        </div>
        <p className="text-sm font-bold text-[#1D1B20]">{movementBias}</p>
        <p className="text-[12px] text-[#49454F] opacity-80 leading-relaxed">
          Mid-foot alineado con la barra. Momento en cadera: {solve.momentArms.hipCm.toFixed(1)} cm · Momento en rodilla:{' '}
          {solve.momentArms.kneeCm.toFixed(1)} cm.
        </p>
      </section>

      <section className="mt-2 rounded-2xl bg-white/85 border border-black/[0.06] p-4">
        <div className="flex items-center gap-2 text-[#1D1B20] mb-1">
          <RulerIcon size={16} />
          <p className="text-[11px] font-black uppercase tracking-wider">Antropometría activa</p>
        </div>
        <p className="text-[12px] text-[#49454F] opacity-85">
          Altura {athleteHeight.toFixed(0)} cm · Fémur {lengthsCm.femur.toFixed(1)} cm · Tibia {lengthsCm.tibia.toFixed(1)} cm · Torso {lengthsCm.torso.toFixed(1)} cm ·
          Brazos {lengthsCm.arms.toFixed(1)} cm.
        </p>
      </section>
    </div>
  );
};

export default WikiLabBiomechanicsView;
