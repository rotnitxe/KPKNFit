// components/JointDetailView.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, DumbbellIcon } from './icons';
import { motion } from 'framer-motion';

const JOINT_TYPE_LABELS: Record<string, string> = {
  'hinge': 'Bisagra',
  'ball-socket': 'Esferoidea',
  'pivot': 'Pivote',
  'gliding': 'Deslizante',
  'saddle': 'Silla de montar',
  'condyloid': 'Condílea',
};

interface JointDetailViewProps {
  jointId: string;
  isOnline: boolean;
}

const JointDetailView: React.FC<JointDetailViewProps> = ({ jointId }) => {
  const { jointDatabase, muscleGroupData, tendonDatabase, muscleHierarchy, exerciseList } = useAppState();
  const { navigateTo } = useAppDispatch();

  const joint = jointDatabase?.find(j => j.id === jointId);

  const protectiveExercises = React.useMemo(() => {
    if (!joint?.protectiveExercises?.length || !exerciseList) return [];
    return joint.protectiveExercises!
      .map(id => exerciseList.find(e => e.id === id))
      .filter((e): e is NonNullable<typeof e> => !!e);
  }, [joint?.protectiveExercises, exerciseList]);

  const relatedMuscles = React.useMemo(() => {
    const childNameToParentName = new Map<string, string>();
    Object.values(muscleHierarchy?.bodyPartHierarchy || {}).forEach(subgroups => {
      subgroups.forEach(sg => {
        if (typeof sg === 'object' && sg !== null) {
          const parent = Object.keys(sg)[0];
          (sg as Record<string, string[]>)[parent]?.forEach(child => {
            childNameToParentName.set(child, parent);
          });
        }
      });
    });
    const seen = new Set<string>();
    const result: { id: string; name: string }[] = [];
    joint?.musclesCrossing?.forEach(mId => {
      const m = muscleGroupData?.find(mg => mg.id === mId);
      if (!m) return;
      const parentName = childNameToParentName.get(m.name) || m.name;
      const parentEntry = muscleGroupData?.find(mg => mg.name === parentName);
      const parentId = parentEntry?.id || mId;
      if (seen.has(parentId)) return;
      seen.add(parentId);
      result.push({ id: parentId, name: parentEntry?.name || m.name });
    });
    return result;
  }, [joint?.musclesCrossing, muscleGroupData, muscleHierarchy]);
  const relatedTendons = joint?.tendonsRelated?.map(tId => tendonDatabase?.find(t => t.id === tId)).filter(Boolean) || [];

  if (!joint) {
    return (
      <div className="pt-[65px] text-center">
        <h2 className="text-2xl font-bold text-red-400">No encontrado</h2>
        <p className="text-slate-300 mt-2">Articulación "{jointId}" no existe en la base de datos.</p>
      </div>
    );
  }

  const typeLabel = JOINT_TYPE_LABELS[joint.type] || joint.type;

  return (
    <div className="min-h-screen flex flex-col bg-transparent overflow-x-hidden relative pb-32">
      <header className="relative pt-12 pb-8 px-6">
        <span className="text-[10px] font-black uppercase tracking-widest text-sky-400 mb-2 block">{typeLabel}</span>
        <h1 className="text-5xl font-black text-white tracking-tighter leading-none">{joint.name}</h1>
      </header>

      <div className="relative z-10 px-6 space-y-6">
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="p-6 rounded-[32px] bg-white/5 backdrop-blur-2xl border border-white/10 shadow-2xl">
          <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Descripción</h3>
          <p className="text-sm text-white/70 leading-relaxed">{joint.description}</p>
        </motion.div>

        {relatedMuscles.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Músculos que la cruzan</h3>
            <div className="space-y-2">
              {relatedMuscles.map(m => (
                <div
                  key={m.id}
                  onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })}
                  className="p-4 flex justify-between items-center rounded-[20px] bg-white/[0.03] hover:bg-white/[0.08] cursor-pointer transition-all group"
                >
                  <span className="font-bold text-white/90 text-sm">{m.name}</span>
                  <ChevronRightIcon className="text-white/20 group-hover:text-sky-400 group-hover:translate-x-1 transition-all" size={18} />
                </div>
              ))}
            </div>
          </motion.div>
        )}

        {protectiveExercises.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-emerald-400 mb-2 flex items-center gap-2">
              <DumbbellIcon size={14} /> Fortalecimiento y Protección
            </h3>
            <p className="text-[11px] text-white/50 mb-4 leading-relaxed">Isométricos, movilidad y ejercicios de rehabilitación recomendados.</p>
            <div className="space-y-2">
              {protectiveExercises.map(ex => (
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

        {relatedTendons.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Tendones Relacionados</h3>
            <div className="space-y-2">
              {relatedTendons.map(t => t && (
                <div
                  key={t.id}
                  onClick={() => navigateTo('tendon-detail', { tendonId: t.id })}
                  className="p-4 flex justify-between items-center rounded-[20px] bg-white/[0.03] hover:bg-white/[0.08] cursor-pointer transition-all group"
                >
                  <span className="font-bold text-white/90 text-sm">{t.name}</span>
                  <ChevronRightIcon className="text-white/20 group-hover:text-sky-400 group-hover:translate-x-1 transition-all" size={18} />
                </div>
              ))}
            </div>
          </motion.div>
        )}

        {joint.commonInjuries && joint.commonInjuries.length > 0 && (
          <motion.details initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }} className="rounded-[32px] bg-white/5 border border-white/10 overflow-hidden group">
            <summary className="p-6 cursor-pointer flex justify-between items-center list-none outline-none">
              <h3 className="text-[10px] font-black uppercase tracking-widest text-red-400/90">Lesiones Comunes</h3>
              <ChevronRightIcon className="text-white/40 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="px-4 pb-4 space-y-3">
              {joint.commonInjuries.map((inj, i) => (
                <div key={i} className="bg-black/20 rounded-[20px] p-5 border border-white/5">
                  <h4 className="font-bold text-white/90 text-sm">{inj.name}</h4>
                  <p className="text-white/60 text-xs mt-2 leading-relaxed">{inj.description}</p>
                  {inj.returnProgressions && inj.returnProgressions.length > 0 && (
                    <div className="mt-4 px-3 py-2 bg-white/5 rounded-xl border border-white/5">
                        <p className="text-white/40 text-[10px] font-black uppercase tracking-widest mb-1">Progresión sugerida:</p>
                        <p className="text-white/70 text-xs font-mono">{inj.returnProgressions.join(' → ')}</p>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </motion.details>
        )}
      </div>
    </div>
  );
};

export default JointDetailView;
