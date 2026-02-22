// components/MovementPatternDetailView.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, DumbbellIcon } from './icons';

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
    <div className="pb-[max(120px,calc(90px+env(safe-area-inset-bottom,0px)+24px))] animate-fade-in bg-[#0a0a0a] min-h-screen">
      <header className="relative h-32 -mx-4 bg-gradient-to-b from-orange-900/30 to-[#0a0a0a] border-b border-orange-500/20">
        <div className="absolute inset-0 bg-gradient-to-t from-[#0a0a0a] via-transparent to-transparent" />
        <div className="absolute bottom-4 left-4 right-4">
          <span className="text-orange-500/90 text-[10px] font-mono font-black uppercase tracking-widest">Patrón de Movimiento</span>
          <h1 className="text-3xl font-bold font-mono text-white mt-1">{pattern.name}</h1>
        </div>
      </header>

      <div className="space-y-6 mt-6 px-4">
        <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
          <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-2">Descripción</h3>
          <p className="text-slate-300 text-sm">{pattern.description}</p>
        </div>

        {(pattern.forceTypes?.length || 0) > 0 && (
          <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
            <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-2">Tipos de Fuerza</h3>
            <div className="flex flex-wrap gap-2">
              {pattern.forceTypes.map((f, i) => (
                <span key={i} className="px-2 py-1 bg-[#0d0d0d] border border-orange-500/20 rounded-lg text-[10px] font-mono text-slate-300">{f}</span>
              ))}
            </div>
          </div>
        )}

        {relatedMuscles.length > 0 && (
          <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
            <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-3">Músculos principales</h3>
            <div className="space-y-2">
              {relatedMuscles.map(m => (
                <div
                  key={m.id}
                  onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })}
                  className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/40 cursor-pointer transition-all"
                >
                  <span className="font-mono font-semibold text-slate-200 text-sm">{m.name}</span>
                  <ChevronRightIcon className="text-slate-500" size={16} />
                </div>
              ))}
            </div>
          </div>
        )}

        {relatedJoints.length > 0 && (
          <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
            <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-3">Articulaciones principales</h3>
            <div className="space-y-2">
              {relatedJoints.map(j => j && (
                <div
                  key={j.id}
                  onClick={() => navigateTo('joint-detail', { jointId: j.id })}
                  className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/40 cursor-pointer transition-all"
                >
                  <span className="font-mono font-semibold text-slate-200 text-sm">{j.name}</span>
                  <ChevronRightIcon className="text-slate-500" size={16} />
                </div>
              ))}
            </div>
          </div>
        )}

        {exampleExercises.length > 0 && (
          <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a]">
            <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90 mb-3 flex items-center gap-2">
              <DumbbellIcon size={14} /> Ejercicios de ejemplo
            </h3>
            <div className="space-y-2">
              {exampleExercises.map(ex => ex && (
                <div
                  key={ex.id}
                  onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })}
                  className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/40 cursor-pointer transition-all"
                >
                  <span className="font-mono font-semibold text-slate-200 text-sm">{ex.name}</span>
                  <ChevronRightIcon className="text-slate-500" size={16} />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default MovementPatternDetailView;
