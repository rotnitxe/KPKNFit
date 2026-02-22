// components/TendonDetailView.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon } from './icons';

interface TendonDetailViewProps {
  tendonId: string;
  isOnline: boolean;
}

const TendonDetailView: React.FC<TendonDetailViewProps> = ({ tendonId }) => {
  const { tendonDatabase, muscleGroupData, jointDatabase, muscleHierarchy } = useAppState();
  const { navigateTo } = useAppDispatch();

  const tendon = tendonDatabase?.find(t => t.id === tendonId);
  const rawMuscle = tendon ? muscleGroupData.find(m => m.id === tendon.muscleId) : null;
  const muscle = React.useMemo(() => {
    if (!rawMuscle) return null;
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
    const parentName = childToParent.get(rawMuscle.name) || rawMuscle.name;
    const parentEntry = muscleGroupData?.find(mg => mg.name === parentName);
    return parentEntry || rawMuscle;
  }, [rawMuscle, muscleGroupData, muscleHierarchy]);
  const joint = tendon?.jointId ? jointDatabase?.find(j => j.id === tendon.jointId) : null;

  if (!tendon) {
    return (
      <div className="pt-[65px] text-center">
        <h2 className="text-2xl font-bold text-red-400">No encontrado</h2>
        <p className="text-slate-300 mt-2">Tendón "{tendonId}" no existe en la base de datos.</p>
      </div>
    );
  }

  return (
    <div className="pb-[max(120px,calc(90px+env(safe-area-inset-bottom,0px)+24px))] animate-fade-in bg-[#0a0a0a] min-h-screen">
      <header className="relative h-32 -mx-4 bg-gradient-to-b from-orange-900/30 to-[#0a0a0a] border-b border-orange-500/20">
        <div className="absolute inset-0 bg-gradient-to-t from-[#0a0a0a] via-transparent to-transparent" />
        <div className="absolute bottom-4 left-4 right-4">
          <span className="text-orange-500/90 text-[10px] font-mono font-black uppercase tracking-widest">Tendón</span>
          <h1 className="text-3xl font-bold font-mono text-white mt-1">{tendon.name}</h1>
        </div>
      </header>

      <div className="space-y-6 mt-6 px-4">
        <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
          <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-2">Descripción</h3>
          <p className="text-slate-300 text-sm">{tendon.description}</p>
        </div>

        <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
          <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-2">Músculo asociado</h3>
          {muscle ? (
            <div
              onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: muscle.id })}
              className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/50 cursor-pointer transition-all"
            >
              <span className="font-mono font-semibold text-slate-200 text-sm">{muscle.name}</span>
              <ChevronRightIcon className="text-orange-500/50" size={18} />
            </div>
          ) : (
            <p className="p-4 rounded-xl bg-[#0a0a0a] border border-orange-500/20 text-slate-500 text-sm italic">
              {tendon.muscleId
                ? `Músculo referenciado no disponible en la base de datos.`
                : 'Sin músculo asociado registrado.'}
            </p>
          )}
        </div>

        {joint && (
          <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
            <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-2">Articulación</h3>
            <div
              onClick={() => navigateTo('joint-detail', { jointId: joint.id })}
              className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/50 cursor-pointer transition-all"
            >
              <span className="font-mono font-semibold text-slate-200 text-sm">{joint.name}</span>
              <ChevronRightIcon className="text-orange-500/50" size={18} />
            </div>
          </div>
        )}

        {tendon.commonInjuries && tendon.commonInjuries.length > 0 && (
          <details className="rounded-xl border border-orange-500/20 overflow-hidden">
            <summary className="p-4 cursor-pointer flex justify-between items-center list-none bg-[#0d0d0d]">
              <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-red-400/90">Lesiones Comunes</h3>
              <ChevronRightIcon className="details-arrow text-orange-500/50" />
            </summary>
            <div className="p-4 border-t border-white/5 space-y-3 bg-[#080808]">
              {tendon.commonInjuries.map((inj, i) => (
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

export default TendonDetailView;
