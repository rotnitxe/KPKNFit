// components/ExerciseDatabaseView.tsx
import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo } from '../types';
import { ArrowLeftIcon, SearchIcon, ChevronRightIcon, DumbbellIcon } from './icons';
import { MUSCLE_GROUPS, EXERCISE_TYPES, CHAIN_TYPES } from '../data/exerciseList';
import { normalizeMuscleGroup } from '../services/volumeCalculator';
import { motion } from 'framer-motion';

const PATTERN_FORCE_OPTIONS: (ExerciseMuscleInfo['force'] | 'All')[] = [
  'All',
  'Empuje',
  'Tirón',
  'Bisagra',
  'Sentadilla',
  'Rotación',
  'Anti-Rotación',
  'Flexión',
  'Extensión',
  'Anti-Flexión',
  'Anti-Extensión',
  'Salto',
  'Otro',
];

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
  const { navigateTo } = useAppDispatch();
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
      className="p-4 flex justify-between items-center cursor-pointer list-none rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white transition-all group active:scale-[0.99] shadow-sm"
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

const ExerciseDatabaseView: React.FC = () => {
  const { exerciseList } = useAppState();
  const { handleBack, navigateTo } = useAppDispatch();
  const [searchQuery, setSearchQuery] = useState('');
  const [muscleFilter, setMuscleFilter] = useState('All');
  const [categoryFilter, setCategoryFilter] = useState('All');
  const [equipmentFilter, setEquipmentFilter] = useState('All');
  const [typeFilter, setTypeFilter] = useState('All');
  const [chainFilter, setChainFilter] = useState('All');
  const [patternFilter, setPatternFilter] = useState<ExerciseMuscleInfo['force'] | 'All'>('All');
  const [showFilters, setShowFilters] = useState(false);

  const filterOptions = useMemo(
    () => ({
      muscles: MUSCLE_GROUPS,
      categories: ['All', ...[...new Set(exerciseList.map((e) => e.category))].sort()],
      equipment: ['All', ...[...new Set(exerciseList.map((e) => e.equipment))].sort()],
      types: EXERCISE_TYPES,
      chains: CHAIN_TYPES,
    }),
    [exerciseList]
  );

  const filteredExercises = useMemo(() => {
    return exerciseList.filter((ex) => {
      const q = searchQuery.toLowerCase();
      const searchMatch =
        searchQuery.length === 0 ||
        ex.name.toLowerCase().includes(q) ||
        (ex.alias && ex.alias.toLowerCase().includes(q)) ||
        (ex.equipment && ex.equipment.toLowerCase().includes(q));
      const muscleMatch =
        muscleFilter === 'All' ||
        muscleFilter === 'Todos' ||
        ex.involvedMuscles.some((m) => m.role === 'primary' && normalizeMuscleGroup(m.muscle) === muscleFilter);
      const categoryMatch = categoryFilter === 'All' || ex.category === categoryFilter;
      const equipmentMatch = equipmentFilter === 'All' || ex.equipment === equipmentFilter;
      const typeMatch = typeFilter === 'All' || ex.type === typeFilter;
      const chainMatch = chainFilter === 'All' || (ex.chain && ex.chain.toLowerCase() === chainFilter.toLowerCase());
      const patternMatch = patternFilter === 'All' || ex.force === patternFilter;

      return searchMatch && muscleMatch && categoryMatch && equipmentMatch && typeMatch && chainMatch && patternMatch;
    });
  }, [exerciseList, searchQuery, muscleFilter, categoryFilter, equipmentFilter, typeFilter, chainFilter, patternFilter]);

  const availablePatterns = useMemo(() => {
    const forces = new Set(exerciseList.map((e) => e.force).filter(Boolean));
    return PATTERN_FORCE_OPTIONS.filter((p) => p === 'All' || forces.has(p));
  }, [exerciseList]);

  return (
    <div className="pt-12 px-6 tab-bar-safe-area min-h-screen bg-[#FDFCFE] pb-32">
      <header className="flex items-center gap-4 mb-6">
        <button
          onClick={handleBack}
          className="w-10 h-10 rounded-full bg-white/70 backdrop-blur-xl border border-black/[0.03] flex items-center justify-center text-[#49454F] hover:bg-white transition-colors shadow-sm"
        >
          <ArrowLeftIcon size={20} />
        </button>
        <div>
          <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">
            Ejercicios
          </h1>
          <p className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mt-1">
            {exerciseList.length} movimientos disponibles
          </p>
        </div>
      </header>

      {/* Search Bar */}
      <div className="relative mb-4">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Buscar por nombre, equipo..."
          className="w-full bg-white/80 backdrop-blur-xl border border-black/[0.05] rounded-2xl pl-12 pr-4 py-4 text-[#1D1B20] placeholder:text-[#49454F] opacity-50 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/30 text-sm font-medium shadow-sm"
        />
        <SearchIcon className="absolute left-4 top-1/2 -translate-y-1/2 text-[#49454F] opacity-40" size={20} />
      </div>

      {/* Pattern Filter Chips */}
      <div className="mb-4">
        <p className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mb-2">Patrón</p>
        <div className="flex flex-wrap gap-1.5">
          {availablePatterns.map((p) => (
            <button
              key={p}
              onClick={() => setPatternFilter(p)}
              className={`px-3 py-2 text-[9px] font-black uppercase tracking-widest rounded-full border transition-all ${
                patternFilter === p
                  ? 'bg-[#ECE6F0] text-primary border-black/[0.05]'
                  : 'bg-white/70 text-[#49454F] opacity-50 border-black/[0.03] hover:bg-white hover:opacity-70'
              }`}
            >
              {p === 'All' ? 'Todos' : p}
            </button>
          ))}
        </div>
      </div>

      {/* Filters Toggle */}
      <button
        onClick={() => setShowFilters(!showFilters)}
        className="w-full py-3 rounded-2xl bg-white/70 backdrop-blur-xl border border-black/[0.05] text-[10px] font-black uppercase tracking-widest text-[#49454F] opacity-60 hover:bg-white transition-all mb-4 shadow-sm"
      >
        {showFilters ? 'Ocultar Filtros' : 'Filtros Avanzados'}
      </button>

      {/* Advanced Filters */}
      {showFilters && (
        <motion.div
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          className="grid grid-cols-2 gap-2 mb-4"
        >
          <select
            value={muscleFilter}
            onChange={(e) => setMuscleFilter(e.target.value)}
            className="w-full bg-white/80 backdrop-blur-xl border border-black/[0.05] rounded-xl text-[#1D1B20] text-[10px] font-medium py-3 px-3 focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-sm"
          >
            {filterOptions.muscles.map((cat) => (
              <option key={cat} value={cat}>
                {cat === 'All' ? 'Músculo Principal' : cat}
              </option>
            ))}
          </select>
          <select
            value={equipmentFilter}
            onChange={(e) => setEquipmentFilter(e.target.value)}
            className="w-full bg-white/80 backdrop-blur-xl border border-black/[0.05] rounded-xl text-[#1D1B20] text-[10px] font-medium py-3 px-3 focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-sm"
          >
            {filterOptions.equipment.map((eq) => (
              <option key={eq} value={eq}>
                {eq === 'All' ? 'Equipamiento' : eq}
              </option>
            ))}
          </select>
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="w-full bg-white/80 backdrop-blur-xl border border-black/[0.05] rounded-xl text-[#1D1B20] text-[10px] font-medium py-3 px-3 focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-sm"
          >
            {filterOptions.categories.map((cat) => (
              <option key={cat} value={cat}>
                {cat === 'All' ? 'Categoría' : cat}
              </option>
            ))}
          </select>
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="w-full bg-white/80 backdrop-blur-xl border border-black/[0.05] rounded-xl text-[#1D1B20] text-[10px] font-medium py-3 px-3 focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-sm"
          >
            {filterOptions.types.map((t) => (
              <option key={t} value={t}>
                {t === 'All' ? 'Tipo' : t}
              </option>
            ))}
          </select>
          <select
            value={chainFilter}
            onChange={(e) => setChainFilter(e.target.value)}
            className="w-full bg-white/80 backdrop-blur-xl border border-black/[0.05] rounded-xl text-[#1D1B20] text-[10px] font-medium py-3 px-3 focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-sm"
          >
            {filterOptions.chains.map((c) => (
              <option key={c} value={c}>
                {c === 'All' ? 'Cadena Muscular' : c}
              </option>
            ))}
          </select>
        </motion.div>
      )}

      {/* Results Count */}
      <p className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 text-center mb-4">
        {filteredExercises.length} resultados
      </p>

      {/* Exercise List */}
      <div className="space-y-2">
        {filteredExercises.map((ex, idx) => (
          <motion.div
            key={ex.id}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: idx * 0.02 }}
          >
            <ExerciseItem exercise={ex} />
          </motion.div>
        ))}
        {filteredExercises.length === 0 && (
          <div className="text-center py-12 rounded-2xl border border-black/[0.05] bg-white/70 backdrop-blur-xl">
            <DumbbellIcon size={40} className="mx-auto text-[#49454F] opacity-20 mb-4" />
            <p className="text-[#49454F] opacity-50 text-sm font-medium">
              No se encontraron ejercicios con los filtros seleccionados.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default ExerciseDatabaseView;
