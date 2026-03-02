// components/onboarding/UnifiedWelcomeWizard.tsx
// Wizard único: 2 slides bienvenida + datos físicos + tipo atleta + nombre + split + volumen + entrenamientos + baterías
// Estética Tú: fondo ilustración, tarjeta gris con bordes redondeados, swipe entre slides

import React, { useState, useCallback, useRef, useEffect } from 'react';
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
import { motion, AnimatePresence } from 'framer-motion';
import { Zap, Activity, Target, Utensils, BarChart3, ChevronRight } from 'lucide-react';


const WELCOME_SLIDES = [
  {
    id: 'logo',
    content: (
      <motion.div 
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.5 }}
        className="flex flex-col items-center justify-center py-10"
      >
        <div className="relative">
          <motion.div 
            animate={{ rotate: 360 }}
            transition={{ duration: 20, repeat: Infinity, ease: "linear" }}
            className="absolute -inset-4 border border-dashed border-white/20 rounded-full"
          />
          <img src="/kpkn-logo-welcome.png" alt="KPKN" className="w-[140px] h-[140px] object-contain relative z-10" />
        </div>
        <motion.h1 
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.3 }}
          className="mt-6 text-white text-2xl font-bold tracking-tighter"
        >
          BIENVENIDO A <span className="text-[#facc15]">KPKN</span>
        </motion.h1>
      </motion.div>
    ),
  },
  {
    id: 'features',
    content: (
      <div className="space-y-6 px-4">
        <div className="grid grid-cols-2 gap-3">
          {[
            { icon: <Activity size={18} />, txt: "Programas", col: "bg-blue-500/10" },
            { icon: <Zap size={18} />, txt: "Batería AUGE", col: "bg-yellow-500/10" },
            { icon: <Target size={18} />, txt: "1RM & RPE", col: "bg-red-500/10" },
            { icon: <Utensils size={18} />, txt: "Nutrición", col: "bg-green-500/10" },
          ].map((f, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.1 }}
              className={`p-3 rounded-2xl ${f.col} border border-white/5 flex flex-col items-center gap-2 text-center`}
            >
              <div className="text-white/80">{f.icon}</div>
              <span className="text-[10px] font-bold uppercase tracking-wider text-white/90">{f.txt}</span>
            </motion.div>
          ))}
        </div>
        <motion.div 
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.5 }}
          className="bg-black/20 p-4 rounded-2xl border border-white/5"
        >
          <p className="text-white/70 text-xs leading-relaxed text-center italic">
            "La fuerza no viene de la capacidad física, sino de una voluntad indomable."
          </p>
        </motion.div>
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

  // --- MOVER HOOKS A NIVEL SUPERIOR ---
  const scrollRef = useRef<HTMLDivElement>(null);
  const programmaticScrollRef = useRef(false);

  useEffect(() => {
    if (phase !== 'welcome') return; // Solo ejecutar en fase welcome
    const el = scrollRef.current;
    if (!el) return;
    programmaticScrollRef.current = true;
    el.scrollTo({ left: welcomeStep * el.clientWidth, behavior: 'smooth' });
    const t = setTimeout(() => { programmaticScrollRef.current = false; }, 400);
    return () => clearTimeout(t);
  }, [welcomeStep, phase]);

  const handleScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el || programmaticScrollRef.current) return;
    const idx = Math.round(el.scrollLeft / el.clientWidth);
    if (idx >= 0 && idx < WELCOME_SLIDES.length && idx !== welcomeStep) {
      setWelcomeStep(idx);
    }
  }, [welcomeStep]);
  // ------------------------------------

  if (phase === 'welcome') {
    const isLastWelcome = welcomeStep === WELCOME_SLIDES.length - 1;
      const el = scrollRef.current;
      if (!el || programmaticScrollRef.current) return;
      const idx = Math.round(el.scrollLeft / el.clientWidth);
      if (idx >= 0 && idx < WELCOME_SLIDES.length && idx !== welcomeStep) {
        setWelcomeStep(idx);
      }
    }, [welcomeStep]);

    return (
      <div className="fixed inset-0 z-[9999] flex flex-col overflow-hidden safe-area-root">
        {/* Fondo: ilustración que se asoma por los bordes — detrás de todo */}
        <div className="absolute inset-0 z-0">
          <img
            src="/fondo-welcome-ilustracion.png"
            alt=""
            className="w-full h-full object-cover object-center"
            aria-hidden
          />
        </div>
        {/* Omitir: flotante sobre el fondo */}
        <button
          onClick={() => {
            setSettings({ hasSeenWelcome: true, hasSeenGeneralWizard: true, precalibrationDismissed: true });
            onComplete();
          }}
          className="absolute top-[max(0.5rem,env(safe-area-inset-top))] right-4 z-10 text-white/90 text-sm font-medium py-2 px-3 rounded-lg bg-black/30 hover:bg-black/50 transition-colors"
        >
          Omitir
        </button>
        {/* Tarjeta Evolucionada: Cristalina con Gradiente */}
        <motion.div 
          initial={{ y: 100, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          className="relative z-20 flex-1 flex flex-col min-h-0 m-5 mt-16 rounded-[2.5rem] overflow-hidden shadow-2xl border border-white/10 backdrop-blur-md" 
          style={{ 
            background: 'linear-gradient(180deg, rgba(40,40,40,0.95) 0%, rgba(20,20,20,0.98) 100%)',
            minHeight: 320 
          }}
        >
          {/* Swipe entre slides */}
          <div
            ref={scrollRef}
            onScroll={handleScroll}
            className="flex-1 flex overflow-x-auto overflow-y-hidden snap-x snap-mandatory scroll-smooth hide-scrollbar"
            style={{ WebkitOverflowScrolling: 'touch' }}
          >
            {WELCOME_SLIDES.map((slide) => (
              <div
                key={slide.id}
                className="flex-shrink-0 w-full snap-center flex flex-col items-center justify-center min-h-[200px] py-8 px-4"
              >
                {slide.content}
              </div>
            ))}
          </div>
          {/* Footer: indicadores + botón, con safe-area para Android (evita que se tape con la barra de navegación) */}
          <div className="shrink-0 p-4 flex flex-col gap-4 wizard-safe-footer">
            <div className="flex gap-1.5 justify-center">
              {WELCOME_SLIDES.map((_, i) => (
                <button
                  key={i}
                  onClick={() => setWelcomeStep(i)}
                  className={`w-2 h-2 rounded-full transition-colors ${i === welcomeStep ? 'bg-[#1a1a1a]' : 'bg-[#1a1a1a]/40'}`}
                  aria-label={`Slide ${i + 1}`}
                />
              ))}
            </div>
            <button
              onClick={() => (isLastWelcome ? setPhase('physical') : setWelcomeStep((s) => s + 1))}
              className="w-full py-4 bg-white text-black font-bold text-sm rounded-2xl flex items-center justify-center gap-2 active:scale-95 transition-transform shadow-lg shadow-white/5"
            >
              <span>{isLastWelcome ? 'COMENZAR' : 'SIGUIENTE'}</span>
              <ChevronRight size={18} />
            </button>
          </div>
        </div>
      </div>
    );
  }

  // --- CONTENEDOR UNIFICADO PARA PASOS DE CONFIGURACIÓN ---
  if (phase !== 'welcome') {
    const renderStep = () => {
      switch (phase) {
        case 'physical':
          return (
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
          );
        case 'athlete':
          return (
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
          );
        case 'program-name':
          return (
            <ProgramNameStep
              value={programName}
              onChange={setProgramName}
              onNext={() => setPhase('split')}
              onSkip={() => setPhase('volume')}
            />
          );
        case 'split':
          return (
            <SplitStep
              selectedSplitId={selectedSplit?.id ?? null}
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
          );
        case 'volume':
          return <VolumeStep volumeRecommendations={volumeRecs} onNext={() => setPhase('recent-workouts')} />;
        case 'recent-workouts':
          return (
            <RecentWorkoutsStep
              exerciseList={exerciseList}
              onNext={(exs) => {
                setRecentExercises(exs);
                setPhase('battery');
              }}
              onSkip={() => setPhase('battery')}
            />
          );
        case 'battery':
          return (
            <BatteryRingsStep
              exercises={recentExercises}
              settings={settings}
              exerciseList={exerciseList}
              onApplyCalibration={handleBatteryApplyCalibration}
              onComplete={handleBatteryComplete}
            />
          );
        default:
          return null;
      }
    };

    return (
      <motion.div 
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        className="fixed inset-0 z-[9999] flex flex-col bg-[#0a0a0a] overflow-hidden safe-area-root"
      >
        {/* Barra de progreso sutil en la parte superior */}
        <div className="absolute top-0 left-0 right-0 h-1 flex gap-1 px-4 mt-[env(safe-area-inset-top)]">
          {['physical', 'athlete', 'program-name', 'split', 'volume', 'recent-workouts', 'battery'].map((p, i) => {
            const phases = ['physical', 'athlete', 'program-name', 'split', 'volume', 'recent-workouts', 'battery'];
            const currentIndex = phases.indexOf(phase);
            return (
              <div 
                key={p} 
                className={`h-full flex-1 rounded-full transition-all duration-500 ${i <= currentIndex ? 'bg-[#facc15]' : 'bg-white/10'}`} 
              />
            );
          })}
        </div>
        <AnimatePresence mode="wait">
          <motion.div
            key={phase}
            initial={{ x: 20, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: -20, opacity: 0 }}
            transition={{ duration: 0.3 }}
            className="flex-1 flex flex-col"
          >
            <div className="flex-1 flex flex-col overflow-y-auto hide-scrollbar pb-10">
              {renderStep()}
            </div>
          </motion.div>
        </AnimatePresence>
        
        {/* Decoración Táctica Final: Un gradiente sutil en la base para suavizar el recorte */}
        <div className="absolute bottom-0 left-0 right-0 h-20 bg-gradient-to-t from-[#0a0a0a] to-transparent pointer-events-none z-50" />
      </motion.div>
    );
  }

// Si no estamos en ninguna fase conocida, no renderizamos nada
  return null;
};
