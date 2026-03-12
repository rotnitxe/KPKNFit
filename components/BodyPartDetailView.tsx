// components/BodyPartDetailView.tsx
import React, { useMemo, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo } from '../types';
import { ChevronRightIcon, DumbbellIcon } from './icons';
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
        <h3 className="font-bold text-[#1D1B20] text-sm group-hover:text-primary transition-colors">{exercise.name}</h3>
        <p className="text-[10px] font-medium text-[#49454F] opacity-50 mt-0.5">
          {exercise.type} • {exercise.equipment}
        </p>
      </div>
      <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all" />
    </motion.div>
  );
});

interface BodyPartDetailViewProps {
  bodyPartId: 'Tren Superior' | 'Tren Inferior' | 'Cuerpo Completo' | 'Otro';
}

const TRAIN_INFO = {
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
  'Cuerpo Completo': {
    description:
      'El entrenamiento de "Cuerpo Completo" (Full Body) trabaja todos los principales grupos musculares en una sola sesión. Es ideal para principiantes o para quienes tienen una disponibilidad limitada para entrenar.',
    importance:
      'Maximiza la frecuencia de estímulo para cada músculo, es muy eficiente en tiempo y promueve una gran respuesta hormonal y metabólica. Excelente para la recomposición corporal y la fuerza general.',
  },
  'Otro': {
    description:
      'Esta categoría incluye ejercicios que no se clasifican claramente o son específicos para ciertos objetivos.',
    importance: '...',
  },
};

const BodyPartDetailView: React.FC<BodyPartDetailViewProps> = ({ bodyPartId }) => {
  const { exerciseList } = useAppState();
  const { setCurrentBackgroundOverride } = useAppDispatch();

  useEffect(() => {
    setCurrentBackgroundOverride(undefined);
    return () => setCurrentBackgroundOverride(undefined);
  }, [setCurrentBackgroundOverride]);

  const exercisesByMuscle = useMemo(() => {
    const partMap = {
      'Tren Superior': 'upper',
      'Tren Inferior': 'lower',
      'Cuerpo Completo': 'full',
      Otro: undefined,
    };
    const targetPart = partMap[bodyPartId];

    const filtered = exerciseList.filter((ex) => ex.bodyPart === targetPart);

    return filtered.reduce((acc, ex) => {
      const primaryMuscle = ex.involvedMuscles.find((m) => m.role === 'primary')?.muscle || 'Otros';
      if (!acc[primaryMuscle]) {
        acc[primaryMuscle] = [];
      }
      acc[primaryMuscle].push(ex);
      return acc;
    }, {} as Record<string, ExerciseMuscleInfo[]>);
  }, [bodyPartId, exerciseList]);

  const sortedMuscleGroups = Object.keys(exercisesByMuscle).sort((a, b) => a.localeCompare(b));
  const info = TRAIN_INFO[bodyPartId];

  return (
    <div className="min-h-screen bg-[#FDFCFE] pb-32">
      {/* Header */}
      <header className="pt-12 px-6 mb-6">
        <span className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mb-2 block">
          División de Entrenamiento
        </span>
        <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">{bodyPartId}</h1>
      </header>

      {/* Content */}
      <div className="px-6 space-y-4">
        {/* Info Card */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <p className="text-sm text-[#49454F] opacity-70 leading-relaxed mb-4">{info.description}</p>
            <div className="px-4 py-3 rounded-2xl bg-[#ECE6F0] border border-black/[0.03]">
              <p className="text-[10px] font-medium text-[#1D1B20] leading-snug">
                <span className="font-black text-primary uppercase tracking-widest block mb-1">Importancia</span>
                {info.importance}
              </p>
            </div>
          </div>
        </motion.div>

        {/* Exercises by Muscle Group */}
        {sortedMuscleGroups.map((muscle, idx) => (
          <motion.details
            key={muscle}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: idx * 0.05 }}
            className="rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] overflow-hidden group shadow-sm"
            open
          >
            <summary className="p-6 cursor-pointer list-none flex justify-between items-center">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-[#ECE6F0] flex items-center justify-center text-primary">
                  <DumbbellIcon size={18} />
                </div>
                <h2 className="text-xl font-bold text-[#1D1B20]">{muscle}</h2>
              </div>
              <ChevronRightIcon className="details-arrow transition-transform text-[#49454F] opacity-20" size={20} />
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
            <DumbbellIcon size={40} className="mx-auto text-[#49454F] opacity-20 mb-4" />
            <p className="text-[#49454F] opacity-50 text-sm font-medium">
              No hay ejercicios registrados para esta categoría.
            </p>
          </motion.div>
        )}
      </div>
    </div>
  );
};

export default BodyPartDetailView;
