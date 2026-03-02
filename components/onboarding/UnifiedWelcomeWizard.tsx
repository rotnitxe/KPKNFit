// components/onboarding/UnifiedWelcomeWizard.tsx
// Wizard único: 2 slides bienvenida + datos físicos + tipo atleta + nombre + split + volumen + entrenamientos + baterías
// Estética Tú: fondo #1a1a1a, grises, blanco, plano

import React, { useState, useCallback } from 'react';
import { useAppContext } from '../../contexts/AppContext';
import { getKpnkVolumeRecommendations } from '../../services/volumeCalculator';
import { SPLIT_TEMPLATES } from '../../data/splitTemplates';
import type { Program, Session, Macrocycle, Block, Mesocycle } from '../../types';
import type { SplitTemplate } from '../../data/splitTemplates';
import type { AthleteProfileScore } from '../../types';
import type { PrecalibrationExerciseInput } from '../../services/auge';
import { PhysicalDataStep, type PhysicalDataForm } from './steps/PhysicalDataStep';
import { AthleteTypeStep } from './steps/AthleteTypeStep';
import { ProgramNameStep } from './steps/ProgramNameStep';
import { SplitStep } from './steps/SplitStep';
import { VolumeStep } from './steps/VolumeStep';
import { RecentWorkoutsStep } from './steps/RecentWorkoutsStep';
import { BatteryRingsStep } from './steps/BatteryRingsStep';

const WELCOME_SLIDES = [
  {
    id: 'logo',
    content: (
      <div className="flex flex-col items-center justify-center py-12">
        <img src="/kpkn-logo-welcome.png" alt="KPKN" className="w-[120px] h-[120px] object-contain" />
      </div>
    ),
  },
  {
    id: 'intro',
    content: (
      <div className="space-y-4 text-center px-4">
        <p className="text-[#a3a3a3] text-sm leading-relaxed">
          Programa tus entrenamientos, registra tus sesiones y haz seguimiento de tu progreso.
        </p>
        <p className="text-[#737373] text-xs">Puedes configurar todo ahora o después.</p>
      </div>
    ),
  },
];

const DEFAULT_ATHLETE_SCORE: AthleteProfileScore = {
  technicalScore: 2,
  consistencyScore: 2,
  strengthScore: 2,
  mobilityScore: 2,
  trainingStyle: 'Powerbuilder',
  totalScore: 8,
  profileLevel: 'Beginner',
};

function createProgramFromSplit(
  name: string,
  split: SplitTemplate,
  volumeRecs: { volumeSystem: string; volumeRecommendations: any[]; athleteProfileScore?: AthleteProfileScore }
): Program {
  const startDay = 0;
  const pattern = split.pattern;

  const generateSessionsForWeek = (): Session[] => {
    const sessions: Session[] = [];
    pattern.forEach((label, dayIndex) => {
      if (label && label.toLowerCase() !== 'descanso' && label.trim() !== '') {
        const assignedDay = (startDay + dayIndex) % 7;
        sessions.push({
          id: crypto.randomUUID(),
          name: label,
          description: '',
          exercises: [],
          dayOfWeek: assignedDay,
        });
      }
    });
    return sessions;
  };

  const newMacro: Macrocycle = { id: crypto.randomUUID(), name: 'Macrociclo Cíclico', blocks: [] };
  const newBlock: Block = { id: crypto.randomUUID(), name: 'BLOQUE CÍCLICO', mesocycles: [] };
  const newMeso: Mesocycle = {
    id: crypto.randomUUID(),
    name: 'Ciclo Base',
    goal: 'Custom',
    weeks: [
      {
        id: crypto.randomUUID(),
        name: 'Semana 1',
        sessions: generateSessionsForWeek(),
        variant: 'A',
      },
    ],
  };
  newBlock.mesocycles.push(newMeso);
  newMacro.blocks = [newBlock];

  return {
    id: crypto.randomUUID(),
    name,
    description: `Split: ${split.name}`,
    structure: 'simple',
    mode: 'hypertrophy',
    startDay,
    selectedSplitId: split.id,
    macrocycles: [newMacro],
    ...(volumeRecs && {
      volumeSystem: volumeRecs.volumeSystem as 'kpnk' | 'israetel' | 'manual',
      volumeRecommendations: volumeRecs.volumeRecommendations,
      volumeAlertsEnabled: true,
      athleteProfileScore: volumeRecs.athleteProfileScore,
    }),
  };
}

type WizardPhase =
  | 'welcome'
  | 'physical'
  | 'athlete'
  | 'program-name'
  | 'split'
  | 'volume'
  | 'recent-workouts'
  | 'battery';

interface UnifiedWelcomeWizardProps {
  onComplete: () => void;
}

export const UnifiedWelcomeWizard: React.FC<UnifiedWelcomeWizardProps> = ({ onComplete }) => {
  const { setSettings, setPrograms, addToast, settings, exerciseList } = useAppContext();
  const [phase, setPhase] = useState<WizardPhase>('welcome');
  const [welcomeStep, setWelcomeStep] = useState(0);
  const [physicalData, setPhysicalData] = useState<PhysicalDataForm>({});
  const [athleteScore, setAthleteScore] = useState<AthleteProfileScore | null>(null);
  const [programName, setProgramName] = useState('');
  const [selectedSplit, setSelectedSplit] = useState<SplitTemplate | null>(null);
  const [showAdvancedSplits, setShowAdvancedSplits] = useState(false);
  const [recentExercises, setRecentExercises] = useState<PrecalibrationExerciseInput[]>([]);

  const score = athleteScore ?? DEFAULT_ATHLETE_SCORE;
  const volumeRecs = getKpnkVolumeRecommendations(score, settings, 'Acumulación');
  const volumeRecsConfig = {
    volumeSystem: 'kpnk',
    volumeRecommendations: volumeRecs,
    athleteProfileScore: score,
  };

  const applyPhysicalData = useCallback(
    (data: PhysicalDataForm) => {
      setSettings({
        ...(data.name && { username: data.name }),
        userVitals: {
          ...settings.userVitals,
          ...(data.age != null && { age: data.age }),
          ...(data.sex && { gender: data.sex as any }),
          ...(data.weight != null && { weight: data.weight }),
          ...(data.height != null && { height: data.height }),
          ...(data.bodyFat != null && { bodyFatPercentage: data.bodyFat }),
          ...(data.muscleMass != null && { muscleMassPercentage: data.muscleMass }),
        },
      });
    },
    [setSettings, settings.userVitals]
  );

  const handleBatteryComplete = useCallback(() => {
    setSettings({ hasSeenGeneralWizard: true, hasPrecalibratedBattery: true });
    onComplete();
  }, [setSettings, onComplete]);

  const handleBatteryApplyCalibration = useCallback(
    (calibration: Parameters<typeof setSettings>[0]['batteryCalibration']) => {
      setSettings({ batteryCalibration: calibration });
    },
    [setSettings]
  );

  if (phase === 'welcome') {
    const isLastWelcome = welcomeStep === WELCOME_SLIDES.length - 1;
    const current = WELCOME_SLIDES[welcomeStep];
    return (
      <div className="fixed inset-0 z-[9999] flex flex-col bg-[#1a1a1a] overflow-hidden safe-area-root">
        <div className="flex-1 flex flex-col min-h-0 px-4">
          <div className="flex justify-end pt-4 shrink-0">
            <button
              onClick={() => {
                setSettings({ hasSeenWelcome: true, hasSeenGeneralWizard: true, precalibrationDismissed: true });
                onComplete();
              }}
              className="text-[#737373] text-sm font-medium py-2"
            >
              Omitir
            </button>
          </div>
          <div className="flex-1 flex flex-col items-center justify-center min-h-0">{current.content}</div>
          <div className="shrink-0 pb-8 flex flex-col gap-4">
            <div className="flex gap-1.5 justify-center">
              {WELCOME_SLIDES.map((_, i) => (
                <span key={i} className={`w-2 h-2 ${i === welcomeStep ? 'bg-white' : 'bg-[#525252]'}`} />
              ))}
            </div>
            <button
              onClick={() => (isLastWelcome ? setPhase('physical') : setWelcomeStep((s) => s + 1))}
              className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm"
            >
              {isLastWelcome ? 'Continuar' : 'Siguiente'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (phase === 'physical') {
    return (
      <div className="fixed inset-0 z-[9999] flex flex-col bg-[#1a1a1a] overflow-hidden safe-area-root">
        <PhysicalDataStep
          initial={physicalData}
          settings={settings}
          onNext={(data) => {
            setPhysicalData(data);
            applyPhysicalData(data);
            setPhase('athlete');
          }}
          onSkip={() => setPhase('athlete')}
        />
      </div>
    );
  }

  if (phase === 'athlete') {
    return (
      <div className="fixed inset-0 z-[9999] flex flex-col bg-[#1a1a1a] overflow-hidden safe-area-root">
        <AthleteTypeStep
          onComplete={(s) => {
            setAthleteScore(s);
            setPhase('program-name');
          }}
          onSkip={() => {
            setAthleteScore(DEFAULT_ATHLETE_SCORE);
            setPhase('program-name');
          }}
        />
      </div>
    );
  }

  if (phase === 'program-name') {
    return (
      <div className="fixed inset-0 z-[9999] flex flex-col bg-[#1a1a1a] overflow-hidden safe-area-root">
        <ProgramNameStep
          value={programName}
          onChange={setProgramName}
          onNext={() => setPhase('split')}
          onSkip={() => setPhase('volume')}
        />
      </div>
    );
  }

  if (phase === 'split') {
    const split = SPLIT_TEMPLATES.find((s) => s.id === selectedSplit?.id) ?? null;
    return (
      <div className="fixed inset-0 z-[9999] flex flex-col bg-[#1a1a1a] overflow-hidden safe-area-root">
        <SplitStep
          selectedSplitId={split?.id ?? null}
          onSelect={(s) => setSelectedSplit(s)}
          onNext={() => {
            if (programName.trim() && selectedSplit) {
              const prog = createProgramFromSplit(programName.trim(), selectedSplit, volumeRecsConfig);
              setPrograms((prev) => [...prev, prog]);
              addToast('Programa creado con éxito.', 'success');
            }
            setPhase('volume');
          }}
          onBack={() => setPhase('program-name')}
          showAdvanced={showAdvancedSplits}
          onToggleAdvanced={() => setShowAdvancedSplits((v) => !v)}
        />
      </div>
    );
  }

  if (phase === 'volume') {
    return (
      <div className="fixed inset-0 z-[9999] flex flex-col bg-[#1a1a1a] overflow-hidden safe-area-root">
        <VolumeStep volumeRecommendations={volumeRecs} onNext={() => setPhase('recent-workouts')} />
      </div>
    );
  }

  if (phase === 'recent-workouts') {
    return (
      <div className="fixed inset-0 z-[9999] flex flex-col bg-[#1a1a1a] overflow-hidden safe-area-root">
        <RecentWorkoutsStep
          exerciseList={exerciseList}
          onNext={(exs) => {
            setRecentExercises(exs);
            setPhase('battery');
          }}
          onSkip={() => setPhase('battery')}
        />
      </div>
    );
  }

  if (phase === 'battery') {
    return (
      <div className="fixed inset-0 z-[9999] flex flex-col bg-[#1a1a1a] overflow-hidden safe-area-root">
        <BatteryRingsStep
          exercises={recentExercises}
          settings={settings}
          exerciseList={exerciseList}
          onApplyCalibration={handleBatteryApplyCalibration}
          onComplete={handleBatteryComplete}
        />
      </div>
    );
  }

  return null;
};
