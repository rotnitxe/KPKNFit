// components/MovementPatternDetailView.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, DumbbellIcon, ActivityIcon } from './icons';
import { motion } from 'framer-motion';

interface MovementPatternDetailViewProps {
  movementPatternId: string;
  isOnline: boolean;
}

const MovementPatternDetailView: React.FC<MovementPatternDetailViewProps> = ({ movementPatternId }) => {
  const { movementPatternDatabase, muscleGroupData, jointDatabase, exerciseList, muscleHierarchy } = useAppState();
  const { navigateTo } = useAppDispatch();

  const pattern = movementPatternDatabase?.find((p) => p.id === movementPatternId);

  const relatedMuscles = React.useMemo(() => {
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
    const seen = new Set<string>();
    const result: { id: string; name: string }[] = [];
    pattern?.primaryMuscles?.forEach((mRef) => {
      const m = muscleGroupData?.find((mg) => mg.name === mRef || mg.id === mRef);
      if (!m) return;
      const parentName = childToParent.get(m.name) || m.name;
      const parentEntry = muscleGroupData?.find((mg) => mg.name === parentName);
      const parentId = parentEntry?.id || m.id;
      if (seen.has(parentId)) return;
      seen.add(parentId);
      result.push({ id: parentId, name: parentEntry?.name || m.name });
    });
    return result;
  }, [pattern?.primaryMuscles, muscleGroupData, muscleHierarchy]);

  const relatedJoints =
    pattern?.primaryJoints?.map((jId) => jointDatabase?.find((j) => j.id === jId)).filter(Boolean) || [];

  const exampleExercises =
    pattern?.exampleExercises?.map((exId) => exerciseList.find((e) => e.id === exId)).filter(Boolean) || [];

  if (!pattern) {
    return (
      <div className="pt-[65px] text-center">
        <h2 className="text-2xl font-bold text-red-500">No encontrado</h2>
        <p className="text-[#49454F] opacity-60 mt-2">
          Patrón "{movementPatternId}" no existe en la base de datos.
        </p>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-[#FDFCFE] overflow-x-hidden relative pb-32">
      {/* Header */}
      <header className="relative pt-12 pb-8 px-6">
        <span className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mb-2 block">
          Patrón Biomecánico
        </span>
        <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">{pattern.name}</h1>
      </header>

      {/* Content */}
      <div className="relative z-10 px-6 space-y-4">
        {/* Description */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
              Descripción
            </h3>
            <p className="text-sm text-[#49454F] opacity-70 leading-relaxed">{pattern.description}</p>
          </div>
        </motion.div>

        {/* Force Types */}
        {(pattern.forceTypes?.length || 0) > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                Vectores de Fuerza
              </h3>
              <div className="flex flex-wrap gap-2">
                {pattern.forceTypes.map((f, i) => (
                  <span
                    key={i}
                    className="px-3 py-1.5 rounded-xl bg-[#E8F5E9] border border-black/[0.03] text-[9px] font-black uppercase tracking-widest text-emerald-700"
                  >
                    {f}
                  </span>
                ))}
              </div>
            </div>
          </motion.div>
        )}

        {/* Related Muscles */}
        {relatedMuscles.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                Motores Principales
              </h3>
              <div className="space-y-2">
                {relatedMuscles.map((m) => (
                  <div
                    key={m.id}
                    onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })}
                    className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all group shadow-sm"
                  >
                    <span className="font-bold text-[#1D1B20] text-sm">{m.name}</span>
                    <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:text-emerald-700 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        )}

        {/* Related Joints */}
        {relatedJoints.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                Ejes Articulares
              </h3>
              <div className="space-y-2">
                {relatedJoints.map((j) => (
                  <div
                    key={j.id}
                    onClick={() => navigateTo('joint-detail', { jointId: j.id })}
                    className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all group shadow-sm"
                  >
                    <span className="font-bold text-[#1D1B20] text-sm">{j.name}</span>
                    <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:text-emerald-700 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        )}

        {/* Example Exercises */}
        {exampleExercises.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 flex items-center gap-2">
                <DumbbellIcon size={14} className="text-primary" /> Ejercicios de Ejemplo
              </h3>
              <div className="space-y-2">
                {exampleExercises.map((ex) => (
                  <div
                    key={ex.id}
                    onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })}
                    className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all group shadow-sm"
                  >
                    <div>
                      <h4 className="font-bold text-[#1D1B20] text-sm">{ex.name}</h4>
                      <p className="text-[10px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mt-0.5">
                        {ex.type} • {ex.equipment}
                      </p>
                    </div>
                    <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:text-primary group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
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

export default MovementPatternDetailView;
