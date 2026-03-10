// components/KPKNView.tsx
import React, { useState, useMemo, useEffect } from 'react';
import { ExerciseMuscleInfo, ExercisePlaylist } from '../types';
import {
  ChevronRightIcon,
  PlusIcon,
  TrashIcon,
  ClipboardListIcon,
  SearchIcon,
  DumbbellIcon,
  BrainIcon,
  ActivityIcon,
} from './icons';
import { useAppState, useAppDispatch, useUIState } from '../contexts/AppContext';
import { motion } from 'framer-motion';
import { getExercisePrimaryDisplayMuscles } from '../utils/canonicalMuscles';

type WikiTab = 'exercises' | 'anatomy' | 'patterns';

const WIKI_TABS: { id: WikiTab; label: string; accent: string }[] = [
  { id: 'exercises', label: 'Ejercicios', accent: 'sky' },
  { id: 'anatomy', label: 'Anatomía', accent: 'purple' },
  { id: 'patterns', label: 'Patrones', accent: 'emerald' },
];

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
  const { navigateTo } = useAppDispatch();
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
      className="p-4 flex justify-between items-center cursor-pointer list-none rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white transition-all duration-200 group active:scale-[0.99] shadow-sm"
    >
      <div className="min-w-0 flex-1">
        <h3 className="font-bold text-[#1D1B20] text-sm group-hover:text-primary transition-colors truncate">
          {exercise.name}
        </h3>
        <p className="text-[10px] font-medium text-[#49454F] opacity-50 mt-0.5 truncate">
          {exercise.type} • {exercise.equipment}
        </p>
        {(exercise.efc != null || exercise.cnc != null || exercise.ssc != null || exercise.ttc != null) && (
          <div className="flex gap-2 mt-2 text-[9px] font-bold text-[#49454F] opacity-40">
            {exercise.efc != null && <span className="px-2 py-0.5 bg-[#ECE6F0] rounded-md">EFC:{exercise.efc}</span>}
            {exercise.cnc != null && <span className="px-2 py-0.5 bg-[#ECE6F0] rounded-md">CNC:{exercise.cnc}</span>}
            {exercise.ssc != null && <span className="px-2 py-0.5 bg-[#ECE6F0] rounded-md">SSC:{exercise.ssc}</span>}
            {exercise.ttc != null && <span className="px-2 py-0.5 bg-[#ECE6F0] rounded-md">TTC:{exercise.ttc}</span>}
          </div>
        )}
      </div>
      <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all flex-shrink-0 ml-2" size={18} />
    </motion.div>
  );
});

const KPKNView: React.FC = () => {
  const { exerciseList, exercisePlaylists, addOrUpdatePlaylist, deletePlaylist, muscleGroupData, muscleHierarchy, jointDatabase, tendonDatabase, movementPatternDatabase } = useAppState();
  const { navigateTo } = useAppDispatch();
  const { activeSubTabs, searchQuery } = useUIState();

  const currentTab = activeSubTabs['kpkn'] || 'Explorar';
  const [activeWikiTab, setActiveWikiTab] = useState<WikiTab>('exercises');
  const [newPlaylistName, setNewPlaylistName] = useState('');
  const [isCreatingNew, setIsCreatingNew] = useState(false);
  const [localSearchQuery, setLocalSearchQuery] = useState('');

  // Si viene con searchQuery desde WikiHomeView, usarlo
  useEffect(() => {
    if (searchQuery) {
      setLocalSearchQuery(searchQuery);
    }
  }, [searchQuery]);

  const bodyPartCategories = useMemo(
    () => Object.keys(muscleHierarchy?.bodyPartHierarchy || {}).sort((a, b) => a.localeCompare(b)),
    [muscleHierarchy]
  );
  const specialCategories = useMemo(
    () => Object.keys(muscleHierarchy?.specialCategories || {}),
    [muscleHierarchy]
  );

  const handleCreatePlaylist = () => {
    if (!newPlaylistName.trim()) return;
    const newPlaylist: ExercisePlaylist = {
      id: crypto.randomUUID(),
      name: newPlaylistName.trim(),
      exerciseIds: [],
    };
    addOrUpdatePlaylist(newPlaylist);
    setNewPlaylistName('');
    setIsCreatingNew(false);
  };

  const handleDeletePlaylist = (playlistId: string) => {
    if (window.confirm('¿Estás seguro de que quieres eliminar esta lista?')) {
      deletePlaylist(playlistId);
    }
  };

  const unifiedSearchResults = useMemo(() => {
    const query = localSearchQuery || searchQuery || '';
    if (!query || query.length < 2) {
      return { exercises: [], muscles: [], joints: [], tendons: [], patterns: [] };
    }
    const q = query.toLowerCase().trim();
    const exercises = exerciseList
      .filter(
        (ex) =>
          ex.name.toLowerCase().includes(q) ||
          (ex.alias && ex.alias.toLowerCase().includes(q)) ||
          (ex.equipment && ex.equipment.toLowerCase().includes(q)) ||
          ex.involvedMuscles.some((m) => m.muscle.toLowerCase().includes(q))
      )
      .slice(0, 15);

    const childNames = new Set<string>();
    const childToParent = new Map<string, string>();
    Object.values(muscleHierarchy?.bodyPartHierarchy || {}).forEach((subgroups) => {
      subgroups.forEach((sg) => {
        if (typeof sg === 'object' && sg !== null) {
          const parent = Object.keys(sg)[0];
          (sg as Record<string, string[]>)[parent]?.forEach((child) => {
            childNames.add(child);
            childToParent.set(child, parent);
          });
        }
      });
    });
    const parentMuscles = (muscleGroupData || []).filter((m) => !childNames.has(m.name));
    const parentsToIncludeFromChildMatch = new Set<string>();
    const matchingChildrenWithoutParent = new Set<string>();
    childToParent.forEach((parent, child) => {
      if (child.toLowerCase().includes(q)) {
        parentsToIncludeFromChildMatch.add(parent);
        const parentInDb = (muscleGroupData || []).find((m) => m.name === parent);
        if (!parentInDb) matchingChildrenWithoutParent.add(child);
      }
    });
    const muscles = (muscleGroupData || [])
      .filter(
        (m) =>
          m.name.toLowerCase().includes(q) ||
          (m.description || '').toLowerCase().includes(q) ||
          parentsToIncludeFromChildMatch.has(m.name) ||
          matchingChildrenWithoutParent.has(m.name)
      )
      .slice(0, 10);
    const joints = (jointDatabase || [])
      .filter((j) => j.name.toLowerCase().includes(q) || (j.description || '').toLowerCase().includes(q))
      .slice(0, 8);
    const tendons = (tendonDatabase || [])
      .filter((t) => t.name.toLowerCase().includes(q) || (t.description || '').toLowerCase().includes(q))
      .slice(0, 8);
    const patterns = (movementPatternDatabase || [])
      .filter((p) => p.name.toLowerCase().includes(q) || (p.description || '').toLowerCase().includes(q))
      .slice(0, 8);
    return { exercises, muscles, joints, tendons, patterns };
  }, [localSearchQuery, searchQuery, exerciseList, muscleGroupData, muscleHierarchy, jointDatabase, tendonDatabase, movementPatternDatabase]);

  const renderWikiTabContent = () => {
    switch (activeWikiTab) {
      case 'exercises':
        return (
          <div className="space-y-4">
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              onClick={() => navigateTo('exercise-database')}
              className="p-5 rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all flex items-center justify-between shadow-sm"
            >
              <div>
                <h3 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-1">
                  Base de ejercicios
                </h3>
                <p className="text-[#1D1B20] font-bold text-sm">{exerciseList.length} ejercicios</p>
              </div>
              <ChevronRightIcon className="text-[#49454F] opacity-20" size={20} />
            </motion.div>
            <div className="grid grid-cols-2 gap-3">
              {exerciseList.filter((ex) => ex.type === 'Básico').slice(0, 6).map((ex, idx) => (
                <motion.div
                  key={ex.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.05 }}
                  onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })}
                  className="p-4 rounded-[20px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all shadow-sm"
                >
                  <h3 className="font-bold text-[#1D1B20] text-sm truncate">{ex.name}</h3>
                  <p className="text-[9px] text-[#49454F] opacity-50 font-medium mt-0.5 truncate">
                    {getExercisePrimaryDisplayMuscles(ex)[0] || ex.subMuscleGroup || 'Core'}
                  </p>
                  {(exercise.efc != null || exercise.cnc != null || exercise.ssc != null || exercise.ttc != null) && (
                    <div className="flex gap-1 mt-2 text-[8px] font-bold text-[#49454F] opacity-40">
                      {ex.efc != null && <span>EFC:{ex.efc}</span>}
                      {ex.cnc != null && <span>CNC:{ex.cnc}</span>}
                      {ex.ssc != null && <span>SSC:{ex.ssc}</span>}
                      {ex.ttc != null && <span>TTC:{ex.ttc}</span>}
                    </div>
                  )}
                </motion.div>
              ))}
            </div>
          </div>
        );
      case 'anatomy':
        return (
          <div className="space-y-6">
            <div>
              <h2 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                Anatomía por zona
              </h2>
              <div className="space-y-2">
                {bodyPartCategories.map((cat, idx) => (
                  <motion.div
                    key={cat}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: idx * 0.03 }}
                    onClick={() => navigateTo('muscle-category', { categoryName: cat })}
                    className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all shadow-sm group"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-full bg-[#F3EDF7] flex items-center justify-center text-purple-600">
                        <BrainIcon size={16} />
                      </div>
                      <span className="font-bold text-[#1D1B20] text-sm">{cat}</span>
                    </div>
                    <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                  </motion.div>
                ))}
              </div>
            </div>
            <div>
              <h2 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                Articulaciones y Tendones
              </h2>
              <div className="space-y-2">
                <div className="mb-3">
                  <span className="text-[8px] font-black uppercase tracking-widest text-[#49454F] opacity-40">
                    Articulaciones
                  </span>
                  <div className="mt-1 space-y-2">
                    {(jointDatabase || []).slice(0, 8).map((j, idx) => (
                      <motion.div
                        key={j.id}
                        initial={{ opacity: 0, x: -10 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: idx * 0.03 }}
                        onClick={() => navigateTo('joint-detail', { jointId: j.id })}
                        className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all shadow-sm group"
                      >
                        <span className="font-bold text-[#1D1B20] text-sm">
                          {j.name?.split('(')[0]?.trim() || j.name}
                        </span>
                        <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                      </motion.div>
                    ))}
                  </div>
                </div>
                <div>
                  <span className="text-[8px] font-black uppercase tracking-widest text-[#49454F] opacity-40">
                    Tendones
                  </span>
                  <div className="mt-1 space-y-2">
                    {(tendonDatabase || []).slice(0, 6).map((t, idx) => (
                      <motion.div
                        key={t.id}
                        initial={{ opacity: 0, x: -10 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: idx * 0.03 }}
                        onClick={() => navigateTo('tendon-detail', { tendonId: t.id })}
                        className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all shadow-sm group"
                      >
                        <span className="font-bold text-[#1D1B20] text-sm">{t.name}</span>
                        <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                      </motion.div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
            {specialCategories.length > 0 && (
              <div>
                <h2 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
                  Grupos funcionales
                </h2>
                <div className="grid grid-cols-2 gap-3">
                  {specialCategories.map((cat, idx) => (
                    <motion.div
                      key={cat}
                      initial={{ opacity: 0, scale: 0.95 }}
                      animate={{ opacity: 1, scale: 1 }}
                      transition={{ delay: idx * 0.03 }}
                      onClick={() => navigateTo('chain-detail', { chainId: cat })}
                      className="p-4 rounded-[20px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all shadow-sm"
                    >
                      <span className="font-bold text-[#1D1B20] text-sm">{cat}</span>
                    </motion.div>
                  ))}
                </div>
              </div>
            )}
          </div>
        );
      case 'patterns':
        return (
          <div className="space-y-2">
            <h2 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3">
              Patrones de movimiento ({movementPatternDatabase?.length || 0})
            </h2>
            {(movementPatternDatabase || []).map((p, idx) => (
              <motion.div
                key={p.id}
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: idx * 0.03 }}
                onClick={() => navigateTo('movement-pattern-detail', { movementPatternId: p.id })}
                className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all shadow-sm group"
              >
                <span className="font-bold text-[#1D1B20] text-sm">{p.name}</span>
                <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
              </motion.div>
            ))}
          </div>
        );
      default:
        return null;
    }
  };

  const renderExploreTab = () => {
    const hasResults =
      localSearchQuery.length >= 2 &&
      (unifiedSearchResults.exercises.length > 0 ||
        unifiedSearchResults.muscles.length > 0 ||
        unifiedSearchResults.joints.length > 0 ||
        unifiedSearchResults.tendons.length > 0 ||
        unifiedSearchResults.patterns.length > 0);

    return (
      <div className="space-y-6">
        {/* Search Bar */}
        <div className="relative">
          <input
            type="text"
            value={localSearchQuery}
            onChange={(e) => setLocalSearchQuery(e.target.value)}
            placeholder="Buscar ejercicios, músculos, articulaciones..."
            className="w-full bg-white/80 backdrop-blur-xl border border-black/[0.05] rounded-2xl pl-12 pr-4 py-4 text-[#1D1B20] placeholder:text-[#49454F] opacity-50 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/30 text-sm font-medium shadow-sm"
          />
          <SearchIcon className="absolute left-4 top-1/2 -translate-y-1/2 text-[#49454F] opacity-40" size={20} />
        </div>

        {localSearchQuery.length >= 2 ? (
          <div className="space-y-6">
            {hasResults ? (
              <>
                {unifiedSearchResults.exercises.length > 0 && (
                  <div>
                    <h2 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 ml-1">
                      Ejercicios ({unifiedSearchResults.exercises.length})
                    </h2>
                    <div className="space-y-2">
                      {unifiedSearchResults.exercises.map((ex, idx) => (
                        <motion.div
                          key={ex.id}
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: idx * 0.03 }}
                        >
                          <ExerciseItem exercise={ex} />
                        </motion.div>
                      ))}
                    </div>
                  </div>
                )}
                {unifiedSearchResults.muscles.length > 0 && (
                  <div>
                    <h2 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 ml-1">
                      Músculos ({unifiedSearchResults.muscles.length})
                    </h2>
                    <div className="space-y-2">
                      {unifiedSearchResults.muscles.map((m, idx) => (
                        <motion.div
                          key={m.id}
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: idx * 0.03 }}
                          onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })}
                          className="p-4 flex justify-between items-center cursor-pointer rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white transition-all shadow-sm group"
                        >
                          <span className="font-bold text-[#1D1B20]">{m.name}</span>
                          <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                        </motion.div>
                      ))}
                    </div>
                  </div>
                )}
                {unifiedSearchResults.joints.length > 0 && (
                  <div>
                    <h2 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 ml-1">
                      Articulaciones ({unifiedSearchResults.joints.length})
                    </h2>
                    <div className="space-y-2">
                      {unifiedSearchResults.joints.map((j, idx) => (
                        <motion.div
                          key={j.id}
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: idx * 0.03 }}
                          onClick={() => navigateTo('joint-detail', { jointId: j.id })}
                          className="p-4 flex justify-between items-center cursor-pointer rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white transition-all shadow-sm group"
                        >
                          <span className="font-bold text-[#1D1B20]">{j.name}</span>
                          <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                        </motion.div>
                      ))}
                    </div>
                  </div>
                )}
                {unifiedSearchResults.tendons.length > 0 && (
                  <div>
                    <h2 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 ml-1">
                      Tendones ({unifiedSearchResults.tendons.length})
                    </h2>
                    <div className="space-y-2">
                      {unifiedSearchResults.tendons.map((t, idx) => (
                        <motion.div
                          key={t.id}
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: idx * 0.03 }}
                          onClick={() => navigateTo('tendon-detail', { tendonId: t.id })}
                          className="p-4 flex justify-between items-center cursor-pointer rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white transition-all shadow-sm group"
                        >
                          <span className="font-bold text-[#1D1B20]">{t.name}</span>
                          <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                        </motion.div>
                      ))}
                    </div>
                  </div>
                )}
                {unifiedSearchResults.patterns.length > 0 && (
                  <div>
                    <h2 className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-50 mb-3 ml-1">
                      Patrones ({unifiedSearchResults.patterns.length})
                    </h2>
                    <div className="space-y-2">
                      {unifiedSearchResults.patterns.map((p, idx) => (
                        <motion.div
                          key={p.id}
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: idx * 0.03 }}
                          onClick={() => navigateTo('movement-pattern-detail', { movementPatternId: p.id })}
                          className="p-4 flex justify-between items-center cursor-pointer rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white transition-all shadow-sm group"
                        >
                          <span className="font-bold text-[#1D1B20]">{p.name}</span>
                          <ChevronRightIcon className="text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all" size={18} />
                        </motion.div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="text-center py-12 rounded-2xl border border-black/[0.05] bg-white/70 backdrop-blur-xl">
                <DumbbellIcon size={40} className="mx-auto text-[#49454F] opacity-20 mb-4" />
                <p className="text-[#49454F] opacity-50 text-sm font-medium">No se encontraron resultados.</p>
                <p className="text-[#49454F] opacity-40 text-[10px] font-medium mt-1">
                  Prueba con músculos, articulaciones o tendones.
                </p>
              </div>
            )}
          </div>
        ) : (
          <>
            {/* Tabs */}
            <div className="flex overflow-x-auto gap-1 pb-2 -mx-2 no-scrollbar">
              {WIKI_TABS.map((tab) => {
                const isActive = activeWikiTab === tab.id;
                const activeClasses: Record<string, string> = {
                  sky: 'bg-[#ECE6F0] text-primary border-black/[0.05]',
                  purple: 'bg-[#F3EDF7] text-purple-700 border-black/[0.05]',
                  emerald: 'bg-[#E8F5E9] text-emerald-700 border-black/[0.05]',
                };
                return (
                  <button
                    key={tab.id}
                    onClick={() => setActiveWikiTab(tab.id)}
                    className={`flex-shrink-0 px-4 py-2.5 rounded-full text-[11px] font-black uppercase tracking-widest transition-all border ${
                      isActive
                        ? activeClasses[tab.accent]
                        : 'text-[#49454F] opacity-50 hover:opacity-70 hover:bg-white/50'
                    }`}
                  >
                    {tab.label}
                  </button>
                );
              })}
            </div>
            {renderWikiTabContent()}
          </>
        )}
      </div>
    );
  };

  const renderListsTab = () => (
    <div className="space-y-4">
      {!isCreatingNew && (
        <motion.button
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          onClick={() => setIsCreatingNew(true)}
          className="w-full py-4 rounded-2xl border border-black/[0.05] bg-white/70 backdrop-blur-xl hover:bg-white transition-all flex items-center justify-center gap-2 font-black uppercase tracking-widest text-[10px] text-[#49454F] shadow-sm"
        >
          <PlusIcon size={18} /> Crear Nueva Lista
        </motion.button>
      )}
      {isCreatingNew && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="p-4 rounded-2xl border border-black/[0.05] bg-white/70 backdrop-blur-xl"
        >
          <input
            type="text"
            value={newPlaylistName}
            onChange={(e) => setNewPlaylistName(e.target.value)}
            placeholder="Nombre de la nueva lista"
            className="w-full bg-white border border-black/[0.05] rounded-xl p-3 text-[#1D1B20] text-sm font-medium mb-3 placeholder:text-[#49454F] opacity-40 focus:border-primary/30 focus:outline-none"
            autoFocus
          />
          <div className="flex gap-2">
            <button
              onClick={() => setIsCreatingNew(false)}
              className="flex-1 py-2.5 rounded-xl border border-black/[0.05] text-[#49454F] opacity-50 text-[10px] font-black uppercase tracking-widest hover:bg-white/50 transition-colors"
            >
              Cancelar
            </button>
            <button
              onClick={handleCreatePlaylist}
              disabled={!newPlaylistName.trim()}
              className="flex-1 py-2.5 rounded-xl bg-primary text-white text-[10px] font-black uppercase tracking-widest disabled:opacity-40 disabled:cursor-not-allowed transition-opacity"
            >
              Crear
            </button>
          </div>
        </motion.div>
      )}

      <div className="space-y-3">
        {Array.isArray(exercisePlaylists) && exercisePlaylists.length > 0 ? (
          exercisePlaylists.map((playlist, idx) => (
            <motion.details
              key={playlist.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.05 }}
              className="rounded-2xl border border-black/[0.05] bg-white/70 backdrop-blur-xl overflow-hidden group shadow-sm"
            >
              <summary className="p-4 cursor-pointer list-none flex justify-between items-center hover:bg-white/50 transition-colors">
                <div>
                  <h3 className="font-bold text-[#1D1B20] text-sm">{playlist.name}</h3>
                  <p className="text-[9px] font-medium text-[#49454F] opacity-50 mt-0.5">
                    {playlist.exerciseIds.length} ejercicios
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      handleDeletePlaylist(playlist.id);
                    }}
                    className="p-2 text-[#49454F] opacity-40 hover:text-red-500 transition-colors"
                    title="Eliminar lista"
                  >
                    <TrashIcon size={14} />
                  </button>
                  <ChevronRightIcon className="details-arrow transition-transform text-[#49454F] opacity-20" size={16} />
                </div>
              </summary>
              <div className="border-t border-black/[0.05] p-3 space-y-2 bg-white/50">
                {playlist.exerciseIds.length > 0 ? (
                  playlist.exerciseIds.map((exId) => {
                    const exercise = exerciseList.find((e) => e.id === exId);
                    return exercise ? <ExerciseItem key={exId} exercise={exercise} /> : null;
                  })
                ) : (
                  <p className="text-[9px] font-medium text-[#49454F] opacity-40 text-center py-4">
                    Lista vacía. Añade ejercicios desde el botón + en la vista de detalle.
                  </p>
                )}
              </div>
            </motion.details>
          ))
        ) : (
          !isCreatingNew && (
            <div className="text-center py-12 rounded-2xl border border-black/[0.05] bg-white/70 backdrop-blur-xl">
              <ClipboardListIcon size={40} className="mx-auto text-[#49454F] opacity-20 mb-4" />
              <p className="text-[#49454F] opacity-50 text-sm font-medium">
                Aún no has creado ninguna lista.
              </p>
              <p className="text-[#49454F] opacity-40 text-[10px] font-medium mt-1">
                Crea una para organizar tus ejercicios favoritos.
              </p>
            </div>
          )
        )}
      </div>
    </div>
  );

  return (
    <div className="pt-12 px-6 max-w-4xl mx-auto bg-[#FDFCFE] min-h-screen pb-32">
      <div className="mb-6">
        <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">
          {currentTab === 'Explorar' ? 'WikiLab' : 'Mis Listas'}
        </h1>
        <p className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mt-1">
          {currentTab === 'Explorar'
            ? 'base de conocimiento · ejercicios · anatomía · patrones'
            : 'colecciones personalizadas'}
        </p>
      </div>

      {currentTab === 'Explorar' ? renderExploreTab() : renderListsTab()}
    </div>
  );
};

export default KPKNView;
