// components/ExerciseDatabaseView.tsx
import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo } from '../types';
import { ArrowLeftIcon, SearchIcon, ChevronRightIcon } from './icons';
import { MUSCLE_GROUPS, EXERCISE_TYPES, CHAIN_TYPES } from '../data/exerciseList';
import { normalizeMuscleGroup } from '../services/volumeCalculator';

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
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-4 flex justify-between items-center cursor-pointer list-none bg-[#0a0a0a] hover:bg-slate-900/80 rounded-xl border border-cyber-cyan/20 hover:border-cyber-cyan/50 transition-all group"
        >
            <div className="min-w-0 flex-1">
                <h3 className="font-bold text-white text-md group-hover:text-cyber-cyan/90 transition-colors">{exercise.name}</h3>
                <p className="text-xs text-slate-400 mt-0.5">{exercise.type} • {exercise.equipment}</p>
                {(exercise.efc != null || exercise.cnc != null || exercise.ssc != null) && (
                    <div className="flex gap-2 mt-2 text-[10px] font-mono text-slate-500">
                        {exercise.efc != null && <span>EFC:{exercise.efc}</span>}
                        {exercise.cnc != null && <span>CNC:{exercise.cnc}</span>}
                        {exercise.ssc != null && <span>SSC:{exercise.ssc}</span>}
                    </div>
                )}
            </div>
            <ChevronRightIcon className="text-cyber-cyan/50 group-hover:text-cyber-cyan flex-shrink-0 ml-2" size={18} />
        </div>
    );
});

const ExerciseDatabaseView: React.FC = () => {
    const { exerciseList } = useAppState();
    const { handleBack } = useAppDispatch();
    const [searchQuery, setSearchQuery] = useState('');
    const [muscleFilter, setMuscleFilter] = useState('All');
    const [categoryFilter, setCategoryFilter] = useState('All');
    const [equipmentFilter, setEquipmentFilter] = useState('All');
    const [typeFilter, setTypeFilter] = useState('All');
    const [chainFilter, setChainFilter] = useState('All');
    const [patternFilter, setPatternFilter] = useState<ExerciseMuscleInfo['force'] | 'All'>('All');
    
    const filterOptions = useMemo(() => ({
        muscles: MUSCLE_GROUPS,
        categories: ['All', ...[...new Set(exerciseList.map(e => e.category))].sort()],
        equipment: ['All', ...[...new Set(exerciseList.map(e => e.equipment))].sort()],
        types: EXERCISE_TYPES,
        chains: CHAIN_TYPES,
    }), [exerciseList]);

    const filteredExercises = useMemo(() => {
        return exerciseList.filter(ex => {
            const q = searchQuery.toLowerCase();
            const searchMatch = searchQuery.length === 0 || 
                                ex.name.toLowerCase().includes(q) ||
                                (ex.alias && ex.alias.toLowerCase().includes(q)) ||
                                (ex.equipment && ex.equipment.toLowerCase().includes(q));
            const muscleMatch = muscleFilter === 'All' || muscleFilter === 'Todos' || ex.involvedMuscles.some(m => m.role === 'primary' && normalizeMuscleGroup(m.muscle) === muscleFilter);
            const categoryMatch = categoryFilter === 'All' || ex.category === categoryFilter;
            const equipmentMatch = equipmentFilter === 'All' || ex.equipment === equipmentFilter;
            const typeMatch = typeFilter === 'All' || ex.type === typeFilter;
            const chainMatch = chainFilter === 'All' || (ex.chain && ex.chain.toLowerCase() === chainFilter.toLowerCase());
            const patternMatch = patternFilter === 'All' || ex.force === patternFilter;
            
            return searchMatch && muscleMatch && categoryMatch && equipmentMatch && typeMatch && chainMatch && patternMatch;
        });
    }, [exerciseList, searchQuery, muscleFilter, categoryFilter, equipmentFilter, typeFilter, chainFilter, patternFilter]);

    const availablePatterns = useMemo(() => {
        const forces = new Set(exerciseList.map(e => e.force).filter(Boolean));
        return PATTERN_FORCE_OPTIONS.filter(p => p === 'All' || forces.has(p));
    }, [exerciseList]);

    return (
        <div className="pt-[65px] tab-bar-safe-area animate-fade-in bg-[#0a0a0a] min-h-screen">
            <header className="flex items-center gap-4 mb-6 -mx-4 px-4">
                <button onClick={handleBack} className="p-2 text-slate-300 hover:text-cyber-cyan/80 transition-colors">
                    <ArrowLeftIcon />
                </button>
                <div>
                    <h1 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90">Base de Datos</h1>
                    <p className="text-white font-mono text-xl mt-0.5">{exerciseList.length} ejercicios</p>
                </div>
            </header>

            <div className="bg-[#0a0a0a] py-4 -mx-4 px-4 border-b border-cyber-cyan/20 space-y-3">
                <div className="relative">
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Buscar por nombre..."
                        className="w-full bg-[#0a0a0a] border border-cyber-cyan/20 rounded-lg pl-10 pr-4 py-2.5 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-cyber-cyan/50 focus:border-cyber-cyan/40"
                    />
                    <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-cyber-cyan/50" size={20} />
                </div>

                {/* Filtro por patrón - chips */}
                <div>
                    <p className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-2">Patrón</p>
                    <div className="flex flex-wrap gap-1.5">
                        {availablePatterns.map(p => (
                            <button
                                key={p}
                                onClick={() => setPatternFilter(p)}
                                className={`px-2.5 py-1 text-[10px] font-mono font-bold uppercase tracking-wider rounded-lg border transition-all ${
                                    patternFilter === p
                                        ? 'bg-cyber-cyan/20 text-cyber-cyan border-cyber-cyan/50'
                                        : 'bg-[#0a0a0a] text-slate-500 border-cyber-cyan/20 hover:border-cyber-cyan/40 hover:text-slate-300'
                                }`}
                            >
                                {p === 'All' ? 'Todos' : p}
                            </button>
                        ))}
                    </div>
                </div>
                
                <details className="border border-cyber-cyan/20 rounded-lg overflow-hidden">
                    <summary className="text-[10px] font-mono font-black uppercase tracking-widest text-slate-400 cursor-pointer py-2 px-3 hover:text-cyber-cyan/80">
                        Filtros Avanzados
                    </summary>
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-2 p-3 border-t border-white/5 bg-[#0d0d0d]">
                        <select value={muscleFilter} onChange={e => setMuscleFilter(e.target.value)} className="w-full bg-[#0a0a0a] border border-cyber-cyan/20 rounded text-white text-sm py-1.5">
                            {filterOptions.muscles.map(cat => <option key={cat} value={cat}>{cat === 'All' ? 'Músculo Principal' : cat}</option>)}
                        </select>
                        <select value={equipmentFilter} onChange={e => setEquipmentFilter(e.target.value)} className="w-full bg-[#0a0a0a] border border-cyber-cyan/20 rounded text-white text-sm py-1.5">
                            {filterOptions.equipment.map(eq => <option key={eq} value={eq}>{eq === 'All' ? 'Equipamiento' : eq}</option>)}
                        </select>
                        <select value={categoryFilter} onChange={e => setCategoryFilter(e.target.value)} className="w-full bg-[#0a0a0a] border border-cyber-cyan/20 rounded text-white text-sm py-1.5">
                            {filterOptions.categories.map(cat => <option key={cat} value={cat}>{cat === 'All' ? 'Categoría' : cat}</option>)}
                        </select>
                        <select value={typeFilter} onChange={e => setTypeFilter(e.target.value)} className="w-full bg-[#0a0a0a] border border-cyber-cyan/20 rounded text-white text-sm py-1.5">
                            {filterOptions.types.map(t => <option key={t} value={t}>{t === 'All' ? 'Tipo' : t}</option>)}
                        </select>
                        <select value={chainFilter} onChange={e => setChainFilter(e.target.value)} className="w-full bg-[#0a0a0a] border border-cyber-cyan/20 rounded text-white text-sm py-1.5">
                            {filterOptions.chains.map(c => <option key={c} value={c}>{c === 'All' ? 'Cadena Muscular' : c}</option>)}
                        </select>
                    </div>
                </details>
            </div>

            <p className="text-[10px] font-mono text-cyber-cyan/90 text-center my-4 px-4">{filteredExercises.length} resultados</p>

            <div className="mt-4 space-y-2 px-4">
                {filteredExercises.map(ex => (
                    <ExerciseItem key={ex.id} exercise={ex} />
                ))}
                {filteredExercises.length === 0 && (
                    <p className="text-center text-slate-500 text-sm pt-8 font-mono">No se encontraron ejercicios con los filtros seleccionados.</p>
                )}
            </div>
        </div>
    );
};

export default ExerciseDatabaseView;
