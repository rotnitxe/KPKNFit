// components/JointDetailView.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, DumbbellIcon } from './icons';

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
  const { jointDatabase, muscleGroupData, tendonDatabase, muscleHierarchy } = useAppState();
  const { navigateTo } = useAppDispatch();

  const joint = jointDatabase?.find(j => j.id === jointId);

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
    <div className="pb-[max(120px,calc(90px+env(safe-area-inset-bottom,0px)+24px))] animate-fade-in bg-[#0a0a0a] min-h-screen">
      <header className="relative h-32 -mx-4 bg-gradient-to-b from-orange-900/30 to-[#0a0a0a] border-b border-orange-500/20">
        <div className="absolute inset-0 bg-gradient-to-t from-[#0a0a0a] via-transparent to-transparent" />
        <div className="absolute bottom-4 left-4 right-4">
          <span className="text-orange-500/90 text-[10px] font-mono font-black uppercase tracking-widest">{typeLabel}</span>
          <h1 className="text-3xl font-bold font-mono text-white mt-1">{joint.name}</h1>
        </div>
      </header>

      <div className="space-y-6 mt-6 px-4">
        <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
          <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-2">Descripción</h3>
          <p className="text-slate-300 text-sm">{joint.description}</p>
        </div>

        {relatedMuscles.length > 0 && (
          <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
            <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-3">Músculos que la cruzan</h3>
            <div className="space-y-2">
              {relatedMuscles.map(m => (
                <div
                  key={m.id}
                  onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })}
                  className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/50 cursor-pointer transition-all"
                >
                  <span className="font-mono font-semibold text-slate-200 text-sm">{m.name}</span>
                  <ChevronRightIcon className="text-orange-500/50" size={18} />
                </div>
              ))}
            </div>
          </div>
        )}

        {relatedTendons.length > 0 && (
          <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
            <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-3">Tendones relacionados</h3>
            <div className="space-y-2">
              {relatedTendons.map(t => t && (
                <div
                  key={t.id}
                  onClick={() => navigateTo('tendon-detail', { tendonId: t.id })}
                  className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/50 cursor-pointer transition-all"
                >
                  <span className="font-mono font-semibold text-slate-200 text-sm">{t.name}</span>
                  <ChevronRightIcon className="text-orange-500/50" size={18} />
                </div>
              ))}
            </div>
          </div>
        )}

        {joint.commonInjuries && joint.commonInjuries.length > 0 && (
          <details className="rounded-xl border border-orange-500/20 overflow-hidden">
            <summary className="p-4 cursor-pointer flex justify-between items-center list-none bg-[#0d0d0d]">
              <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-red-400/90">Lesiones Comunes</h3>
              <ChevronRightIcon className="details-arrow text-orange-500/50" />
            </summary>
            <div className="p-4 border-t border-white/5 space-y-3 bg-[#080808]">
              {joint.commonInjuries.map((inj, i) => (
                <div key={i} className="bg-[#0a0a0a] rounded-xl p-3 border border-orange-500/20">
                  <h4 className="font-semibold text-white text-sm">{inj.name}</h4>
                  <p className="text-slate-400 text-xs mt-1">{inj.description}</p>
                  {inj.returnProgressions && inj.returnProgressions.length > 0 && (
                    <p className="text-slate-500 text-xs mt-2 font-mono">Progresión: {inj.returnProgressions.join(' → ')}</p>
                  )}
                </div>
              ))}
            </div>
          </details>
        )}
      </div>
    </div>
  );
};

export default JointDetailView;
