// components/JointDetailView.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, DumbbellIcon, ActivityIcon, BrainIcon } from './icons';
import { motion } from 'framer-motion';

const JOINT_TYPE_LABELS: Record<string, string> = {
  hinge: 'Bisagra',
  'ball-socket': 'Esferoidea',
  pivot: 'Pivote',
  gliding: 'Deslizante',
  saddle: 'Silla de montar',
  condyloid: 'Condílea',
};

interface JointDetailViewProps {
  jointId: string;
  isOnline: boolean;
}

const JointDetailView: React.FC<JointDetailViewProps> = ({ jointId }) => {
  const { jointDatabase, muscleGroupData, tendonDatabase, muscleHierarchy, exerciseList } = useAppState();
  const { navigateTo } = useAppDispatch();

  const joint = jointDatabase?.find((j) => j.id === jointId);

  const protectiveExercises = React.useMemo(() => {
    if (!joint?.protectiveExercises?.length || !exerciseList) return [];
    return joint.protectiveExercises!
      .map((id) => exerciseList.find((e) => e.id === id))
      .filter((e): e is NonNullable<typeof e> => !!e);
  }, [joint?.protectiveExercises, exerciseList]);

  const relatedMuscles = React.useMemo(() => {
    const childNameToParentName = new Map<string, string>();
    Object.values(muscleHierarchy?.bodyPartHierarchy || {}).forEach((subgroups) => {
      subgroups.forEach((sg) => {
        if (typeof sg === 'object' && sg !== null) {
          const parent = Object.keys(sg)[0];
          (sg as Record<string, string[]>)[parent]?.forEach((child) => {
            childNameToParentName.set(child, parent);
          });
        }
      });
    });
    const seen = new Set<string>();
    const result: { id: string; name: string }[] = [];
    joint?.musclesCrossing?.forEach((mId) => {
      const m = muscleGroupData?.find((mg) => mg.id === mId);
      if (!m) return;
      const parentName = childNameToParentName.get(m.name) || m.name;
      const parentEntry = muscleGroupData?.find((mg) => mg.name === parentName);
      const parentId = parentEntry?.id || mId;
      if (seen.has(parentId)) return;
      seen.add(parentId);
      result.push({ id: parentId, name: parentEntry?.name || m.name });
    });
    return result;
  }, [joint?.musclesCrossing, muscleGroupData, muscleHierarchy]);

  const relatedTendons = React.useMemo(() => {
    if (!joint?.tendonsRelated?.length || !tendonDatabase) return [];
    return joint.tendonsRelated
      .map((tId) => tendonDatabase.find((t) => t.id === tId))
      .filter((t): t is NonNullable<typeof t> => Boolean(t));
  }, [joint?.tendonsRelated, tendonDatabase]);

  if (!joint) {
    return (
      <div className="pt-[65px] text-center">
        <h2 className="text-2xl font-bold text-red-500">No encontrado</h2>
        <p className="text-[#49454F] opacity-60 mt-2">
          Articulación "{jointId}" no existe en la base de datos.
        </p>
      </div>
    );
  }

  const typeLabel = JOINT_TYPE_LABELS[joint.type] || joint.type;

  return (
    <div className="min-h-screen flex flex-col bg-[#FDFCFE] overflow-x-hidden relative pb-32">
      {/* Header */}
      <header className="relative pt-12 pb-8 px-6">
        <span className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mb-2 block">
          {typeLabel}
        </span>
        <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">{joint.name}</h1>
      </header>

      {/* Content */}
      <div className="relative z-10 px-6 space-y-4">
        {/* Description */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
              Descripción
            </h3>
            <p className="text-sm text-[#49454F] opacity-70 leading-relaxed">{joint.description}</p>
          </div>
        </motion.div>

        {/* Related Muscles */}
        {relatedMuscles.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 flex items-center gap-2">
                <BrainIcon size={14} className="text-purple-600" /> Músculos que la cruzan
              </h3>
              <div className="space-y-2">
                {relatedMuscles.map((m) => (
                  <div
                    key={m.id}
                    onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })}
                    className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all group shadow-sm"
                  >
                    <span className="font-bold text-[#1D1B20] text-sm">{m.name}</span>
                    <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:text-purple-600 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        )}

        {/* Related Tendons */}
        {relatedTendons.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 flex items-center gap-2">
                <ActivityIcon size={14} className="text-amber-600" /> Tendones Relacionados
              </h3>
              <div className="space-y-2">
                {relatedTendons.map((t) => (
                  <div
                    key={t.id}
                    onClick={() => navigateTo('tendon-detail', { tendonId: t.id })}
                    className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all group shadow-sm"
                  >
                    <span className="font-bold text-[#1D1B20] text-sm">{t.name}</span>
                    <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:text-amber-600 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        )}

        {/* Protective Exercises */}
        {protectiveExercises.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 flex items-center gap-2">
                <DumbbellIcon size={14} className="text-primary" /> Ejercicios Protectores
              </h3>
              <div className="space-y-2">
                {protectiveExercises.map((ex) => (
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

export default JointDetailView;
