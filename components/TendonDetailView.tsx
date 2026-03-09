// components/TendonDetailView.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, DumbbellIcon, ActivityIcon, BrainIcon } from './icons';
import { motion } from 'framer-motion';

interface TendonDetailViewProps {
  tendonId: string;
  isOnline: boolean;
}

const TendonDetailView: React.FC<TendonDetailViewProps> = ({ tendonId }) => {
  const { tendonDatabase, muscleGroupData, jointDatabase, muscleHierarchy, exerciseList } = useAppState();
  const { navigateTo } = useAppDispatch();

  const tendon = tendonDatabase?.find((t) => t.id === tendonId);

  const protectiveExercises = React.useMemo(() => {
    if (!tendon?.protectiveExercises?.length || !exerciseList) return [];
    return tendon.protectiveExercises!
      .map((id) => exerciseList.find((e) => e.id === id))
      .filter((e): e is NonNullable<typeof e> => !!e);
  }, [tendon?.protectiveExercises, exerciseList]);

  const rawMuscle = tendon ? muscleGroupData.find((m) => m.id === tendon.muscleId) : null;
  const muscle = React.useMemo(() => {
    if (!rawMuscle) return null;
    const childToParent = new Map<string, string>();
    Object.values(muscleHierarchy?.bodyPartHierarchy || {}).forEach((subgroups) => {
      subgroups.forEach((sg) => {
        if (typeof sg === 'object' && sg !== null) {
          const parent = Object.keys(sg)[0];
          (sg as Record<string, string[]>)[parent]?.forEach((child) => {
            childToParent.set(child, parent);
          });
        }
      });
    });
    const parentName = childToParent.get(rawMuscle.name) || rawMuscle.name;
    const parentEntry = muscleGroupData?.find((mg) => mg.name === parentName);
    return parentEntry || rawMuscle;
  }, [rawMuscle, muscleGroupData, muscleHierarchy]);

  const joint = tendon?.jointId ? jointDatabase?.find((j) => j.id === tendon.jointId) : null;

  if (!tendon) {
    return (
      <div className="pt-[65px] text-center">
        <h2 className="text-2xl font-bold text-red-500">No encontrado</h2>
        <p className="text-[#49454F] opacity-60 mt-2">
          Tendón "{tendonId}" no existe en la base de datos.
        </p>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-[#FDFCFE] overflow-x-hidden relative pb-32">
      {/* Header */}
      <header className="relative pt-12 pb-8 px-6">
        <span className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mb-2 block">
          Tendón
        </span>
        <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">{tendon.name}</h1>
      </header>

      {/* Content */}
      <div className="relative z-10 px-6 space-y-4">
        {/* Description */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
              Descripción
            </h3>
            <p className="text-sm text-[#49454F] opacity-70 leading-relaxed">{tendon.description}</p>
          </div>
        </motion.div>

        {/* Associated Muscle */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 flex items-center gap-2">
              <BrainIcon size={14} className="text-purple-600" /> Músculo Asociado
            </h3>
            {muscle ? (
              <div
                onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: muscle.id })}
                className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all group shadow-sm"
              >
                <span className="font-bold text-[#1D1B20] text-sm">{muscle.name}</span>
                <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:text-purple-600 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
              </div>
            ) : (
              <p className="p-4 rounded-2xl bg-[#F3EDF7]/30 border border-black/[0.03] text-[#49454F] opacity-60 text-sm italic">
                {tendon.muscleId
                  ? `Músculo referenciado no disponible.`
                  : 'Sin músculo asociado registrado.'}
              </p>
            )}
          </div>
        </motion.div>

        {/* Associated Joint */}
        {joint && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 flex items-center gap-2">
                <ActivityIcon size={14} className="text-sky-600" /> Articulación Relacionada
              </h3>
              <div
                onClick={() => navigateTo('joint-detail', { jointId: joint.id })}
                className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all group shadow-sm"
              >
                <span className="font-bold text-[#1D1B20] text-sm">{joint.name}</span>
                <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:text-sky-600 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
              </div>
            </div>
          </motion.div>
        )}

        {/* Protective Exercises */}
        {protectiveExercises.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-2 flex items-center gap-2">
                <DumbbellIcon size={14} className="text-emerald-600" /> Fortalecimiento y Salud
              </h3>
              <p className="text-[10px] text-[#49454F] opacity-60 mb-4 leading-relaxed">
                Isométricos, excéntricos y ejercicios recomendados para la salud de este tendón.
              </p>
              <div className="space-y-2">
                {protectiveExercises.map((ex) => (
                  <div
                    key={ex.id}
                    onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })}
                    className="p-4 flex justify-between items-center rounded-[24px] bg-[#E8F5E9]/50 hover:bg-[#E8F5E9] cursor-pointer transition-all border border-black/[0.03] group shadow-sm"
                  >
                    <span className="font-bold text-[#1D1B20] text-sm">{ex.name}</span>
                    <ChevronRightIcon className="text-emerald-600/50 group-hover:text-emerald-600 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        )}
      </div>
    </div>
  );
};

export default TendonDetailView;
