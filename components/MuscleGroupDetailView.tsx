// components/MuscleGroupDetailView.tsx
import React, { useMemo, useEffect, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { enrichWithWikipedia } from '../services/wikipediaEnrichment';
import { ExerciseMuscleInfo, MuscleHierarchy, MuscleSubGroup } from '../types';
import {
  SparklesIcon,
  ChevronRightIcon,
  DumbbellIcon,
  PencilIcon,
  StarIcon,
  BrainIcon,
  ActivityIcon,
} from './icons';
import MuscleGroupEditorModal from './MuscleGroupEditorModal';
import { MuscleTrainingAnalysis } from './MuscleTrainingAnalysis';
import { motion } from 'framer-motion';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo; isFavorite?: boolean }> = React.memo(
  ({ exercise, isFavorite }) => {
    const { navigateTo } = useAppDispatch();
    return (
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
        className="p-4 flex justify-between items-center cursor-pointer rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white transition-all group shadow-sm"
      >
        <div className="flex items-center gap-3 min-w-0 flex-1">
          {isFavorite && (
            <div className="w-8 h-8 rounded-full bg-[#FFF8E1] flex items-center justify-center flex-shrink-0">
              <StarIcon size={14} className="text-amber-600" />
            </div>
          )}
          <div className="min-w-0 flex-1">
            <h3 className="font-bold text-[#1D1B20] text-sm group-hover:text-purple-600 transition-colors truncate">
              {exercise.name}
            </h3>
            <p className="text-[10px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mt-0.5 truncate">
              {exercise.type} • {exercise.equipment}
            </p>
          </div>
        </div>
        <div className="w-8 h-8 rounded-full flex items-center justify-center text-[#49454F] opacity-20 group-hover:text-purple-600 group-hover:opacity-40 group-hover:translate-x-1 transition-all flex-shrink-0">
          <ChevronRightIcon size={18} />
        </div>
      </motion.div>
    );
  }
);

interface MuscleGroupDetailViewProps {
  muscleGroupId: string;
  isOnline: boolean;
}

const MuscleGroupDetailView: React.FC<MuscleGroupDetailViewProps> = ({ muscleGroupId, isOnline }) => {
  const { muscleGroupData, settings, exerciseList, muscleHierarchy, history, jointDatabase, tendonDatabase } =
    useAppState();
  const { setCurrentBackgroundOverride, navigateTo } = useAppDispatch();
  const [isEditorOpen, setIsEditorOpen] = useState(false);
  const [wikiExtract, setWikiExtract] = useState<string | null>(null);
  const [wikiLoading, setWikiLoading] = useState(false);
  const [wikiError, setWikiError] = useState(false);

  const muscleInfo = useMemo(() => {
    return muscleGroupData.find((m) => m.id === muscleGroupId);
  }, [muscleGroupId, muscleGroupData]);

  useEffect(() => {
    if (muscleInfo?.coverImage) {
      setCurrentBackgroundOverride({
        type: 'image',
        value: muscleInfo.coverImage,
        style: { blur: 24, brightness: 0.3 },
      });
    } else {
      setCurrentBackgroundOverride(undefined);
    }
    return () => setCurrentBackgroundOverride(undefined);
  }, [muscleInfo, setCurrentBackgroundOverride]);

  const recommendedExercises = useMemo(() => {
    if (!muscleInfo || !muscleInfo.recommendedExercises) return [];
    return muscleInfo.recommendedExercises
      .map((id) => exerciseList.find((ex) => ex.id === id))
      .filter((ex): ex is ExerciseMuscleInfo => !!ex);
  }, [muscleInfo, exerciseList]);

  const childMuscleNames = useMemo(() => {
    if (!muscleInfo) return [];
    let children: string[] = [];
    Object.values(muscleHierarchy.bodyPartHierarchy).forEach((subgroups: MuscleSubGroup[]) => {
      subgroups.forEach((subgroup) => {
        if (typeof subgroup === 'object' && subgroup !== null) {
          const parentName = Object.keys(subgroup)[0];
          if (parentName === muscleInfo.name) {
            children = subgroup[parentName];
          }
        }
      });
    });
    return children;
  }, [muscleInfo, muscleHierarchy]);

  const childMuscleInfos = useMemo(() => {
    return childMuscleNames
      .map((name) => muscleGroupData.find((m) => m.name === name))
      .filter((m): m is NonNullable<typeof m> => !!m);
  }, [childMuscleNames, muscleGroupData]);

  const allExercises = useMemo(() => {
    if (!muscleInfo) return [];

    const relevantMuscleNames = [muscleInfo.name, ...childMuscleNames];

    const filteredExercises = exerciseList.filter((ex) => {
      if (relevantMuscleNames.includes(ex.subMuscleGroup || '')) {
        return true;
      }
      return ex.involvedMuscles.some(
        (m) => relevantMuscleNames.includes(m.muscle) && m.role === 'primary'
      );
    });

    if (childMuscleNames.length === 0) {
      filteredExercises.sort((a, b) => {
        const aIsSpecific = a.subMuscleGroup === muscleInfo.name;
        const bIsSpecific = b.subMuscleGroup === muscleInfo.name;
        if (aIsSpecific && !bIsSpecific) return -1;
        if (!aIsSpecific && bIsSpecific) return 1;
        return 0;
      });
    }

    return filteredExercises;
  }, [exerciseList, muscleInfo, muscleHierarchy, childMuscleNames]);

  const exercisesByHead = useMemo(() => {
    if (muscleInfo?.name !== 'Deltoides' || childMuscleNames.length === 0) return null;
    const groups: Record<string, ExerciseMuscleInfo[]> = {};
    childMuscleNames.forEach((head) => {
      groups[head] = [];
    });
    allExercises.forEach((ex) => {
      const head =
        ex.subMuscleGroup ||
        ex.involvedMuscles?.find((m) => childMuscleNames.includes(m.muscle) && m.role === 'primary')?.muscle;
      if (head && groups[head]) groups[head].push(ex);
      else if (groups[childMuscleNames[0]]) groups[childMuscleNames[0]].push(ex);
    });
    return groups;
  }, [muscleInfo, childMuscleNames, allExercises]);

  if (!muscleInfo) {
    return (
      <div className="pt-[65px] text-center">
        <h2 className="text-2xl font-bold text-red-500">Error</h2>
        <p className="text-[#49454F] opacity-60 mt-2">
          No se encontró información para el grupo muscular con ID: "{muscleGroupId}".
        </p>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-[#FDFCFE] overflow-x-hidden relative pb-32">
      {isEditorOpen && (
        <MuscleGroupEditorModal
          isOpen={isEditorOpen}
          onClose={() => setIsEditorOpen(false)}
          muscleGroup={muscleInfo}
        />
      )}

      {/* Header */}
      <header className="relative pt-12 pb-8 px-6 flex justify-between items-end">
        <div className="relative z-10">
          <span className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mb-2 block">
            Músculo
          </span>
          <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">{muscleInfo.name}</h1>
        </div>
        <button
          onClick={() => setIsEditorOpen(true)}
          className="relative z-20 px-4 py-2 rounded-full bg-white/70 backdrop-blur-xl border border-black/[0.03] text-[9px] font-black uppercase tracking-widest text-[#49454F] hover:bg-white transition-colors flex items-center gap-2 shadow-sm"
        >
          <PencilIcon size={12} /> Info
        </button>
      </header>

      {/* Content */}
      <div className="relative z-10 px-6 space-y-4">
        {/* Training Analysis */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <div className="rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm overflow-hidden">
            <MuscleTrainingAnalysis muscleName={muscleInfo.name} history={history} isOnline={isOnline} settings={settings} />
          </div>
        </motion.div>

        {/* General Information */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
              Información General
            </h3>
            <p className="whitespace-pre-wrap text-sm text-[#49454F] opacity-70 leading-relaxed">
              {muscleInfo.description}
            </p>
            {!wikiExtract && (
              <button
                onClick={async () => {
                  setWikiLoading(true);
                  setWikiError(false);
                  try {
                    const r = await enrichWithWikipedia(muscleInfo.name);
                    if (r?.extract) setWikiExtract(r.extract);
                    else setWikiError(true);
                  } catch {
                    setWikiError(true);
                  } finally {
                    setWikiLoading(false);
                  }
                }}
                disabled={wikiLoading}
                className="mt-4 px-4 py-2 rounded-xl bg-[#F3EDF7] border border-black/[0.03] text-[10px] font-black uppercase tracking-widest text-purple-700 hover:bg-purple-100 transition-colors disabled:opacity-50"
              >
                {wikiLoading ? 'Cargando...' : wikiError ? 'Reintentar Wikipedia' : 'Wikipedia'}
              </button>
            )}
            {wikiExtract && (
              <div className="mt-4 p-4 bg-[#F3EDF7]/50 rounded-2xl border border-black/[0.03]">
                <p className="text-xs text-[#49454F] opacity-60 italic leading-relaxed">{wikiExtract}</p>
              </div>
            )}
          </div>
        </motion.div>

        {/* Aesthetic Importance */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
              Importancia Estética
            </h3>
            <p className="text-sm text-[#49454F] opacity-70 leading-relaxed">
              {muscleInfo.aestheticImportance ||
                muscleInfo.aestheticRole ||
                muscleInfo.importance?.health ||
                `El ${muscleInfo.name} contribuye a la forma y proporción del cuerpo. Su desarrollo equilibrado mejora la simetría y la apariencia general.`}
            </p>
          </div>
        </motion.div>

        {/* Child Muscles / Portions */}
        {childMuscleInfos.length > 0 && (
          <motion.details
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] overflow-hidden group shadow-sm"
            open
          >
            <summary className="p-6 cursor-pointer flex justify-between items-center list-none outline-none">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50">
                Porciones / Cabezas
              </h3>
              <ChevronRightIcon className="text-[#49454F] opacity-20 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="px-4 pb-4 space-y-3">
              {childMuscleInfos.map((portion) => (
                <div key={portion.id} className="p-5 rounded-[24px] bg-[#F3EDF7]/30 border border-black/[0.03]">
                  <h4 className="font-bold text-[#1D1B20] mb-2">{portion.name}</h4>
                  <p className="text-sm text-[#49454F] opacity-60 leading-relaxed mb-3">{portion.description}</p>
                  {(portion.origin || portion.insertion) && (
                    <div className="text-xs text-[#49454F] opacity-50 space-y-1 mb-3">
                      {portion.origin && (
                        <p>
                          <strong className="text-[#1D1B20] font-black uppercase tracking-widest text-[8px]">
                            Origen:
                          </strong>{' '}
                          {portion.origin}
                        </p>
                      )}
                      {portion.insertion && (
                        <p>
                          <strong className="text-[#1D1B20] font-black uppercase tracking-widest text-[8px]">
                            Inserción:
                          </strong>{' '}
                          {portion.insertion}
                        </p>
                      )}
                    </div>
                  )}
                  {portion.mechanicalFunctions && portion.mechanicalFunctions.length > 0 && (
                    <div className="flex flex-wrap gap-2">
                      {portion.mechanicalFunctions.map((f, i) => (
                        <span
                          key={i}
                          className="px-2 py-1 bg-white/70 rounded-lg text-[8px] font-black uppercase tracking-widest text-[#49454F] opacity-50"
                        >
                          {f}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </motion.details>
        )}

        {/* Biomechanical & Health Role */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h4 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                Rol Biomecánico
              </h4>
              <p className="text-sm text-[#49454F] opacity-70 leading-relaxed">{muscleInfo.importance.movement}</p>
            </div>
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h4 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                Rol en la Salud
              </h4>
              <p className="text-sm text-[#49454F] opacity-70 leading-relaxed">{muscleInfo.importance.health}</p>
            </div>
          </motion.div>
        </div>

        {/* Related Joints */}
        {(muscleInfo.relatedJoints?.length || 0) > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.35 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                Articulaciones Relacionadas
              </h3>
              <div className="flex flex-wrap gap-2">
                {muscleInfo.relatedJoints?.map((jId) => {
                  const joint = jointDatabase?.find((j) => j.id === jId);
                  const label = joint?.name?.split('(')[0]?.trim() || jId.replace(/-/g, ' ');
                  return (
                    <button
                      key={jId}
                      onClick={() => navigateTo('joint-detail', { jointId: jId })}
                      className="px-4 py-2 rounded-xl bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-black tracking-widest uppercase text-primary hover:bg-primary/10 transition-colors"
                    >
                      {label}
                    </button>
                  );
                })}
              </div>
            </div>
          </motion.div>
        )}

        {/* Related Tendons */}
        {(muscleInfo.relatedTendons?.length || 0) > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                Tendones Relacionados
              </h3>
              <div className="flex flex-wrap gap-2">
                {muscleInfo.relatedTendons?.map((tId) => {
                  const tendon = tendonDatabase?.find((t) => t.id === tId);
                  const label = tendon?.name || tId.replace(/tendon-|-/g, ' ');
                  return (
                    <button
                      key={tId}
                      onClick={() => navigateTo('tendon-detail', { tendonId: tId })}
                      className="px-4 py-2 rounded-xl bg-[#FFF8E1] border border-black/[0.03] text-[10px] font-black tracking-widest uppercase text-amber-700 hover:bg-amber-100 transition-colors"
                    >
                      {label}
                    </button>
                  );
                })}
              </div>
            </div>
          </motion.div>
        )}

        {/* Volume Recommendations */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.45 }}>
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-4">
              Volumen Semanal Recomendado
            </h3>
            <div className="grid grid-cols-3 gap-3">
              <div className="bg-[#F3EDF7]/30 rounded-2xl p-4 text-center border border-black/[0.03]">
                <p className="text-2xl font-black text-[#1D1B20]">{muscleInfo.volumeRecommendations.mev}</p>
                <p className="text-[9px] text-[#49454F] opacity-50 uppercase tracking-widest mt-1">Mínimo</p>
              </div>
              <div className="bg-[#ECE6F0] rounded-2xl p-4 text-center border border-black/[0.03] shadow-sm">
                <p className="text-2xl font-black text-primary">{muscleInfo.volumeRecommendations.mav}</p>
                <p className="text-[9px] text-primary opacity-60 uppercase tracking-widest mt-1">Óptimo</p>
              </div>
              <div className="bg-[#F3EDF7]/30 rounded-2xl p-4 text-center border border-black/[0.03]">
                <p className="text-2xl font-black text-[#1D1B20]">{muscleInfo.volumeRecommendations.mrv}</p>
                <p className="text-[9px] text-[#49454F] opacity-50 uppercase tracking-widest mt-1">Máximo</p>
              </div>
            </div>
          </div>
        </motion.div>

        {/* Recommended Exercises */}
        {recommendedExercises.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.5 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-4 flex items-center gap-2">
                <SparklesIcon size={14} className="text-amber-600" /> Ejercicios Destacados
              </h3>
              <div className="space-y-2">
                {recommendedExercises.map((ex) => (
                  <ExerciseItem key={ex.id} exercise={ex} isFavorite={ex.id === muscleInfo.favoriteExerciseId} />
                ))}
              </div>
            </div>
          </motion.div>
        )}

        {/* All Exercises */}
        {allExercises.length > 0 && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.55 }}>
            <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
              <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-4 flex items-center gap-2">
                <DumbbellIcon size={14} className="text-primary" /> Todos los ejercicios
              </h3>
              <div className="space-y-4">
                {exercisesByHead ? (
                  childMuscleNames.map((head) => {
                    const exs = exercisesByHead[head] || [];
                    if (exs.length === 0) return null;
                    return (
                      <div key={head} className="space-y-2">
                        <h4 className="text-[8px] font-black text-purple-700 uppercase tracking-widest ml-2 mb-2">
                          {head}
                        </h4>
                        <div className="space-y-2">
                          {exs.map((ex) => (
                            <ExerciseItem key={ex.id} exercise={ex} />
                          ))}
                        </div>
                      </div>
                    );
                  })
                ) : (
                  <div className="space-y-2">
                    {allExercises.map((ex) => (
                      <ExerciseItem key={ex.id} exercise={ex} />
                    ))}
                  </div>
                )}
              </div>
            </div>
          </motion.div>
        )}
      </div>
    </div>
  );
};

export default MuscleGroupDetailView;
