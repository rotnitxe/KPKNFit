// components/ChainDetailView.tsx
import React, { useMemo, useEffect, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo } from '../types';
import { ChevronRightIcon, PencilIcon, ActivityIcon, DumbbellIcon } from './icons';
import MuscleListEditorModal from './MuscleListEditorModal';
import { motion } from 'framer-motion';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
  const { navigateTo } = useAppDispatch();
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
      className="p-4 flex justify-between items-center cursor-pointer rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white transition-all group shadow-sm"
    >
      <div>
        <h3 className="font-bold text-[#1D1B20] text-sm group-hover:text-primary transition-colors">
          {exercise.name}
        </h3>
        <p className="text-[10px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mt-0.5">
          {exercise.type} • {exercise.equipment}
        </p>
      </div>
      <div className="w-8 h-8 rounded-full flex items-center justify-center text-[#49454F] opacity-20 group-hover:text-primary group-hover:opacity-40 group-hover:translate-x-1 transition-all">
        <ChevronRightIcon size={18} />
      </div>
    </motion.div>
  );
});

type ValidChainId =
  | 'Cadena Anterior'
  | 'Cadena Posterior'
  | 'Cuerpo Completo'
  | 'Tren Superior'
  | 'Tren Inferior'
  | 'Core'
  | 'Otro';

interface ChainDetailViewProps {
  chainId: ValidChainId;
}

const CHAIN_INFO: Record<ValidChainId, { description: string; importance: string }> = {
  'Cadena Anterior': {
    description:
      'La cadena anterior se refiere al conjunto de músculos en la parte frontal del cuerpo. Son los músculos que "ves en el espejo", como el pectoral, cuádriceps, bíceps y abdominales.',
    importance:
      'Es fundamental para los movimientos de empuje, la flexión de cadera y la protección de los órganos vitales. Un desequilibrio con la cadena posterior puede llevar a malas posturas y lesiones.',
  },
  'Cadena Posterior': {
    description:
      'La cadena posterior es el "motor" del cuerpo, comprendiendo los músculos de la parte trasera: isquiotibiales, glúteos, espalda baja y alta, y dorsales.',
    importance:
      'Es la fuente de la potencia atlética para correr, saltar y levantar objetos pesados del suelo. Una cadena posterior fuerte es vital para la salud de la espalda y la estabilidad de la cadera.',
  },
  'Cuerpo Completo': {
    description:
      'Los ejercicios de cuerpo completo o "full chain" involucran de forma sinérgica tanto la cadena anterior como la posterior, requiriendo una gran estabilidad y coordinación intermuscular.',
    importance:
      'Son extremadamente funcionales y transfieren directamente a las actividades de la vida diaria y la mayoría de los deportes, promoviendo una fuerza integral.',
  },
  'Tren Superior': {
    description:
      'La división de "Tren Superior" se enfoca en todos los músculos de la cintura para arriba: pecho, espalda, hombros y brazos. Es una de las formas más populares y efectivas de organizar el entrenamiento, permitiendo una alta frecuencia por grupo muscular.',
    importance:
      'Fortalecer el tren superior es crucial para la postura, la fuerza funcional en tareas diarias (levantar, empujar, tirar) y para crear un físico equilibrado y estético.',
  },
  'Tren Inferior': {
    description:
      'La división de "Tren Inferior" se concentra en los músculos de las piernas y glúteos: cuádriceps, isquiotibiales, glúteos y gemelos. Estos entrenamientos suelen ser los más demandantes metabólicamente.',
    importance:
      'Un tren inferior fuerte es la base de la potencia atlética (correr, saltar), mejora la estabilidad general, aumenta el gasto calórico y tiene grandes beneficios hormonales.',
  },
  'Core': {
    description:
      'El Core es un complejo de músculos que estabilizan la columna y la pelvis. No solo incluye los abdominales, sino también la espalda baja, glúteos y el transverso abdominal. Su función principal es la estabilidad y la transferencia de fuerzas.',
    importance:
      'Es la base de todo movimiento. Un core fuerte permite transferir eficientemente la fuerza desde el tren inferior al superior, aumentando el rendimiento en todos los levantamientos y deportes. Es la mejor protección contra el dolor de espalda baja.',
  },
  'Otro': {
    description:
      'Esta categoría incluye ejercicios que no se clasifican claramente o son específicos para ciertos objetivos.',
    importance: '...',
  },
};

const ChainDetailView: React.FC<ChainDetailViewProps> = ({ chainId }) => {
  const { exerciseList, muscleHierarchy } = useAppState();
  const { navigateTo, setCurrentBackgroundOverride, openMuscleListEditor } = useAppDispatch();

  useEffect(() => {
    setCurrentBackgroundOverride(undefined);
    return () => setCurrentBackgroundOverride(undefined);
  }, [setCurrentBackgroundOverride]);

  const exercisesByMuscle = useMemo(() => {
    const chainMap = {
      'Cadena Anterior': 'anterior',
      'Cadena Posterior': 'posterior',
      'Cuerpo Completo': 'full',
    };
    const targetChain = chainMap[chainId as keyof typeof chainMap];

    let filtered: ExerciseMuscleInfo[];

    if (targetChain) {
      filtered = exerciseList.filter((ex) => ex.chain === targetChain);
    } else {
      const specialMuscles = muscleHierarchy.specialCategories[chainId];
      if (specialMuscles) {
        filtered = exerciseList.filter(
          (ex) =>
            ex.involvedMuscles.some(
              (m) => specialMuscles.includes(m.muscle) && m.role === 'primary'
            )
        );
      } else {
        filtered = [];
      }
    }

    return filtered.reduce((acc, ex) => {
      const primaryMuscle = ex.involvedMuscles.find((m) => m.role === 'primary')?.muscle || 'Otros';
      if (!acc[primaryMuscle]) {
        acc[primaryMuscle] = [];
      }
      acc[primaryMuscle].push(ex);
      return acc;
    }, {} as Record<string, ExerciseMuscleInfo[]>);
  }, [chainId, exerciseList, muscleHierarchy]);

  const sortedMuscleGroups = Object.keys(exercisesByMuscle).sort((a, b) => a.localeCompare(b));
  const info = CHAIN_INFO[chainId];

  return (
    <div className="min-h-screen flex flex-col bg-[#FDFCFE] overflow-x-hidden relative pb-32">
      {/* Header */}
      <header className="relative pt-12 pb-8 px-6">
        <div className="absolute top-4 right-6 z-20">
          <button
            onClick={() => openMuscleListEditor(chainId, 'special')}
            className="px-4 py-2 rounded-full bg-white/70 backdrop-blur-xl border border-black/[0.03] text-[9px] font-black uppercase tracking-widest text-[#49454F] hover:bg-white transition-colors flex items-center gap-2 shadow-sm"
          >
            <PencilIcon size={12} /> Editar
          </button>
        </div>
        <span className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mb-2 block">
          Cadena Cinética
        </span>
        <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">{chainId}</h1>
      </header>

      {/* Content */}
      <div className="relative z-10 px-6 space-y-4">
        {/* Info Card */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
              Descripción
            </h3>
            <p className="text-sm text-[#49454F] opacity-70 leading-relaxed mb-4">{info.description}</p>
            <div className="px-4 py-3 rounded-2xl bg-[#ECE6F0] border border-black/[0.03]">
              <p className="text-[10px] font-medium text-[#1D1B20] leading-snug">
                <span className="font-black text-primary uppercase tracking-widest block mb-1">Importancia</span>
                {info.importance}
              </p>
            </div>
          </div>
        </motion.div>

        {/* Exercises by Muscle */}
        {sortedMuscleGroups.map((muscle, idx) => (
          <motion.details
            key={muscle}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: idx * 0.05 }}
            className="rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] overflow-hidden group shadow-sm"
            open
          >
            <summary className="p-6 cursor-pointer flex justify-between items-center list-none outline-none">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-[#ECE6F0] flex items-center justify-center text-primary">
                  <DumbbellIcon size={18} />
                </div>
                <h2 className="text-xl font-bold text-[#1D1B20]">{muscle}</h2>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-[9px] font-black text-[#49454F] opacity-40 uppercase tracking-widest">
                  {exercisesByMuscle[muscle].length} ejercicios
                </span>
                <ChevronRightIcon className="details-arrow transition-transform text-[#49454F] opacity-20" size={20} />
              </div>
            </summary>
            <div className="border-t border-black/[0.03] p-4 space-y-2 bg-white/50">
              {exercisesByMuscle[muscle].map((ex) => (
                <ExerciseItem key={ex.id} exercise={ex} />
              ))}
            </div>
          </motion.details>
        ))}

        {sortedMuscleGroups.length === 0 && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-center py-12 rounded-2xl border border-black/[0.05] bg-white/70 backdrop-blur-xl"
          >
            <ActivityIcon size={40} className="mx-auto text-[#49454F] opacity-20 mb-4" />
            <p className="text-[#49454F] opacity-50 text-sm font-medium">
              No hay ejercicios registrados para esta cadena.
            </p>
          </motion.div>
        )}
      </div>
    </div>
  );
};

export default ChainDetailView;
