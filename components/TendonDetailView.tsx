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
    <div className="pb-20 animate-fade-in">
      <header className="relative h-32 -mx-4 bg-gradient-to-b from-amber-900/40 to-black">
        <div className="absolute inset-0 bg-gradient-to-t from-black via-transparent to-transparent" />
        <div className="absolute bottom-4 left-4 right-4">
          <span className="text-amber-400 text-xs font-bold uppercase tracking-wider">Tendón</span>
          <h1 className="text-3xl font-bold text-white mt-1">{tendon.name}</h1>
        </div>
      </header>

      <div className="space-y-6 mt-6">
        <div className="glass-card-nested p-4">
          <h3 className="font-bold text-white mb-2">Descripción</h3>
          <p className="text-slate-300 text-sm">{tendon.description}</p>
        </div>

        <div className="glass-card-nested p-4">
          <h3 className="font-bold text-white mb-2">Músculo asociado</h3>
          {muscle ? (
            <div
              onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: muscle.id })}
              className="p-3 flex justify-between items-center rounded-lg bg-slate-900/50 hover:bg-slate-800 cursor-pointer transition-colors"
            >
              <span className="font-semibold text-slate-200">{muscle.name}</span>
              <ChevronRightIcon className="text-slate-500" size={16} />
            </div>
          ) : (
            <p className="p-3 rounded-lg bg-slate-900/50 text-slate-500 text-sm italic">
              {tendon.muscleId
                ? `Músculo referenciado no disponible en la base de datos.`
                : 'Sin músculo asociado registrado.'}
            </p>
          )}
        </div>

        {joint && (
          <div className="glass-card-nested p-4">
            <h3 className="font-bold text-white mb-2">Articulación</h3>
            <div
              onClick={() => navigateTo('joint-detail', { jointId: joint.id })}
              className="p-3 flex justify-between items-center rounded-lg bg-slate-900/50 hover:bg-slate-800 cursor-pointer transition-colors"
            >
              <span className="font-semibold text-slate-200">{joint.name}</span>
              <ChevronRightIcon className="text-slate-500" size={16} />
            </div>
          </div>
        )}

        {tendon.commonInjuries && tendon.commonInjuries.length > 0 && (
          <details className="glass-card-nested !p-0">
            <summary className="p-4 cursor-pointer flex justify-between items-center list-none">
              <h3 className="font-bold text-red-400/90">Lesiones Comunes</h3>
              <ChevronRightIcon className="details-arrow" />
            </summary>
            <div className="p-4 border-t border-slate-700/50 space-y-3">
              {tendon.commonInjuries.map((inj, i) => (
                <div key={i} className="bg-slate-900/50 rounded-lg p-3">
                  <h4 className="font-semibold text-white text-sm">{inj.name}</h4>
                  <p className="text-slate-400 text-xs mt-1">{inj.description}</p>
                  {inj.returnProgressions && inj.returnProgressions.length > 0 && (
                    <p className="text-slate-500 text-xs mt-2">Progresión: {inj.returnProgressions.join(' → ')}</p>
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
