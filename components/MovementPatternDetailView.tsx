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
    <div className="pb-20 animate-fade-in">
      <header className="relative h-32 -mx-4 bg-gradient-to-b from-emerald-900/40 to-black">
        <div className="absolute inset-0 bg-gradient-to-t from-black via-transparent to-transparent" />
        <div className="absolute bottom-4 left-4 right-4">
          <span className="text-emerald-400 text-xs font-bold uppercase tracking-wider">Patrón de Movimiento</span>
          <h1 className="text-3xl font-bold text-white mt-1">{pattern.name}</h1>
        </div>
      </header>

      <div className="space-y-6 mt-6">
        <div className="glass-card-nested p-4">
          <h3 className="font-bold text-white mb-2">Descripción</h3>
          <p className="text-slate-300 text-sm">{pattern.description}</p>
        </div>

        {(pattern.forceTypes?.length || 0) > 0 && (
          <div className="glass-card-nested p-4">
            <h3 className="font-bold text-white mb-2">Tipos de Fuerza</h3>
            <div className="flex flex-wrap gap-2">
              {pattern.forceTypes.map((f, i) => (
                <span key={i} className="px-2 py-1 bg-slate-800 rounded-lg text-sm text-slate-300">{f}</span>
              ))}
            </div>
          </div>
        )}

        {relatedMuscles.length > 0 && (
          <div className="glass-card-nested p-4">
            <h3 className="font-bold text-white mb-3">Músculos principales</h3>
            <div className="space-y-2">
              {relatedMuscles.map(m => (
                <div
                  key={m.id}
                  onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })}
                  className="p-3 flex justify-between items-center rounded-lg bg-slate-900/50 hover:bg-slate-800 cursor-pointer transition-colors"
                >
                  <span className="font-semibold text-slate-200">{m.name}</span>
                  <ChevronRightIcon className="text-slate-500" size={16} />
                </div>
              ))}
            </div>
          </div>
        )}

        {relatedJoints.length > 0 && (
          <div className="glass-card-nested p-4">
            <h3 className="font-bold text-white mb-3">Articulaciones principales</h3>
            <div className="space-y-2">
              {relatedJoints.map(j => j && (
                <div
                  key={j.id}
                  onClick={() => navigateTo('joint-detail', { jointId: j.id })}
                  className="p-3 flex justify-between items-center rounded-lg bg-slate-900/50 hover:bg-slate-800 cursor-pointer transition-colors"
                >
                  <span className="font-semibold text-slate-200">{j.name}</span>
                  <ChevronRightIcon className="text-slate-500" size={16} />
                </div>
              ))}
            </div>
          </div>
        )}

        {exampleExercises.length > 0 && (
          <div className="glass-card-nested p-4">
            <h3 className="font-bold text-white mb-3 flex items-center gap-2">
              <DumbbellIcon size={18} /> Ejercicios de ejemplo
            </h3>
            <div className="space-y-2">
              {exampleExercises.map(ex => ex && (
                <div
                  key={ex.id}
                  onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })}
                  className="p-3 flex justify-between items-center rounded-lg bg-slate-900/50 hover:bg-slate-800 cursor-pointer transition-colors"
                >
                  <span className="font-semibold text-slate-200">{ex.name}</span>
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
