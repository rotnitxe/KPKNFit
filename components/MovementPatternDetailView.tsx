// components/MovementPatternDetailView.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, DumbbellIcon } from './icons';
import { motion } from 'framer-motion';

interface MovementPatternDetailViewProps {
  movementPatternId: string;
  isOnline: boolean;
}

const MovementPatternDetailView: React.FC<MovementPatternDetailViewProps> = ({ movementPatternId }) => {
  const { movementPatternDatabase, muscleGroupData, jointDatabase, exerciseList, muscleHierarchy } = useAppState();
  const { navigateTo } = useAppDispatch();

  const pattern = movementPatternDatabase?.find(p => p.id === movementPatternId);
  const relatedMuscles = React.useMemo(() => {
    const childToParent = new Map<string, string>();
    Object.values(muscleHierarchy?.bodyPartHierarchy || {}).forEach(subgroups => {
      subgroups.forEach(sg => {
        if (typeof sg === 'object' && sg !== null) {
          const parent = Object.keys(sg)[0];
          (sg as Record<string, string[]>)[parent]?.forEach(child => {
            childToParent.set(child, parent);
          });
        }
      });
    });
    const seen = new Set<string>();
    const result: { id: string; name: string }[] = [];
    pattern?.primaryMuscles?.forEach(mRef => {
      const m = muscleGroupData?.find(mg => mg.name === mRef || mg.id === mRef);
      if (!m) return;
      const parentName = childToParent.get(m.name) || m.name;
      const parentEntry = muscleGroupData?.find(mg => mg.name === parentName);
      const parentId = parentEntry?.id || m.id;
      if (seen.has(parentId)) return;
      seen.add(parentId);
      result.push({ id: parentId, name: parentEntry?.name || m.name });
    });
    return result;
  }, [pattern?.primaryMuscles, muscleGroupData, muscleHierarchy]);
  const relatedJoints = pattern?.primaryJoints?.map(jId => jointDatabase?.find(j => j.id === jId)).filter(Boolean) || [];
  const exampleExercises = pattern?.exampleExercises?.map(exId => exerciseList.find(e => e.id === exId)).filter(Boolean) || [];

  if (!pattern) {
    return (
      <div className="pt-[65px] text-center">
        <h2 className="text-2xl font-bold text-red-400">No encontrado</h2>
        <p className="text-slate-300 mt-2">Patrón "{movementPatternId}" no existe en la base de datos.</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-transparent overflow-x-hidden relative pb-32">
      <header className="relative pt-12 pb-8 px-6">
        <span className="text-[10px] font-black uppercase tracking-widest text-emerald-400 mb-2 block">Patrón Biomecánico</span>
        <h1 className="text-5xl font-black text-white tracking-tighter leading-none">{pattern.name}</h1>
      </header>

      <div className="relative z-10 px-6 space-y-6">
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="p-6 rounded-[32px] bg-white/5 backdrop-blur-2xl border border-white/10 shadow-2xl">
          <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Descripción</h3>
          <p className="text-sm text-white/70 leading-relaxed">{pattern.description}</p>
        </motion.div>

        {(pattern.forceTypes?.length || 0) > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Vectores de Fuerza</h3>
            <div className="flex flex-wrap gap-2">
              {pattern.forceTypes.map((f, i) => (
                <span key={i} className="px-3 py-1.5 bg-emerald-500/10 border border-emerald-500/20 rounded-xl text-[10px] font-black uppercase tracking-widest text-emerald-400">{f}</span>
              ))}
            </div>
          </motion.div>
        )}

        {relatedMuscles.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Motores Principales</h3>
            <div className="space-y-2">
              {relatedMuscles.map(m => (
                <div
                  key={m.id}
                  onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })}
                  className="p-4 flex justify-between items-center rounded-[20px] bg-white/[0.03] hover:bg-white/[0.08] cursor-pointer transition-all group"
                >
                  <span className="font-bold text-white/90 text-sm">{m.name}</span>
                  <ChevronRightIcon className="text-white/20 group-hover:text-emerald-400 group-hover:translate-x-1 transition-all" size={18} />
                </div>
              ))}
            </div>
          </motion.div>
        )}

        {relatedJoints.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Ejes Articulares</h3>
            <div className="space-y-2">
              {relatedJoints.map(j => j && (
                <div
                  key={j.id}
                  onClick={() => navigateTo('joint-detail', { jointId: j.id })}
                  className="p-4 flex justify-between items-center rounded-[20px] bg-white/[0.03] hover:bg-white/[0.08] cursor-pointer transition-all group"
                >
                  <span className="font-bold text-white/90 text-sm">{j.name}</span>
                  <ChevronRightIcon className="text-white/20 group-hover:text-emerald-400 group-hover:translate-x-1 transition-all" size={18} />
                </div>
              ))}
            </div>
          </motion.div>
        )}

        {exampleExercises.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-emerald-400 mb-3 flex items-center gap-2">
              <DumbbellIcon size={14} /> Ejercicios Core
            </h3>
            <div className="space-y-2">
              {exampleExercises.map(ex => ex && (
                <div
                  key={ex.id}
                  onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })}
                  className="p-4 flex justify-between items-center rounded-[20px] bg-emerald-500/5 hover:bg-emerald-500/10 cursor-pointer transition-all border border-emerald-500/10 group"
                >
                  <span className="font-bold text-white/90 text-sm">{ex.name}</span>
                  <ChevronRightIcon className="text-emerald-400/50 group-hover:text-emerald-400 group-hover:translate-x-1 transition-all" size={18} />
                </div>
              ))}
            </div>
          </motion.div>
        )}
      </div>
    </div>
  );
};

export default MovementPatternDetailView;
