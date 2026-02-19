// components/ExerciseDatabaseView.tsx
import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo } from '../types';
import { ArrowLeftIcon, SearchIcon, ChevronRightIcon, SparklesIcon } from './icons';
import Card from './ui/Card';
import { MUSCLE_GROUPS, EXERCISE_TYPES, CHAIN_TYPES } from '../data/exerciseList';
import DiscoverExercisesModal from './DiscoverExercisesModal';
import Button from './ui/Button';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
    const { navigateTo } = useAppDispatch();
    return (
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-3 flex justify-between items-center cursor-pointer list-none hover:bg-slate-800/50 rounded-lg transition-colors"
        >
            <div>
                <h3 className="font-semibold text-white text-md">{exercise.name}</h3>
                <p className="text-xs text-slate-400">{exercise.type} • {exercise.equipment}</p>
            </div>
            <ChevronRightIcon className="text-slate-500" />
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
    const [isDiscoverModalOpen, setIsDiscoverModalOpen] = useState(false);
    
    const filterOptions = useMemo(() => ({
        muscles: MUSCLE_GROUPS,
        categories: ['All', ...[...new Set(exerciseList.map(e => e.category))].sort()],
        equipment: ['All', ...[...new Set(exerciseList.map(e => e.equipment))].sort()],
        types: EXERCISE_TYPES,
        chains: CHAIN_TYPES,
    }), [exerciseList]);

    const filteredExercises = useMemo(() => {
        return exerciseList.filter(ex => {
            const searchMatch = searchQuery.length === 0 || 
                                ex.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                                (ex.alias && ex.alias.toLowerCase().includes(searchQuery.toLowerCase()));
            const muscleMatch = muscleFilter === 'All' || ex.involvedMuscles.some(m => m.role === 'primary' && m.muscle === muscleFilter);
            const categoryMatch = categoryFilter === 'All' || ex.category === categoryFilter;
            const equipmentMatch = equipmentFilter === 'All' || ex.equipment === equipmentFilter;
            const typeMatch = typeFilter === 'All' || ex.type === typeFilter;
            const chainMatch = chainFilter === 'All' || (ex.chain && ex.chain.toLowerCase() === chainFilter.toLowerCase());
            
            return searchMatch && muscleMatch && categoryMatch && equipmentMatch && typeMatch && chainMatch;
        });
    }, [exerciseList, searchQuery, muscleFilter, categoryFilter, equipmentFilter, typeFilter, chainFilter]);

    return (
        <div className="pt-[65px] pb-20 animate-fade-in">
            <DiscoverExercisesModal isOpen={isDiscoverModalOpen} onClose={() => setIsDiscoverModalOpen(false)} />
            <header className="flex items-center gap-4 mb-6 -mx-4 px-4">
                <button onClick={handleBack} className="p-2 text-slate-300">
                    <ArrowLeftIcon />
                </button>
                <div>
                    <h1 className="text-3xl font-bold text-white">Base de Datos</h1>
                    <p className="text-slate-400 text-sm">{exerciseList.length} ejercicios</p>
                </div>
            </header>

            <div className="sticky top-[65px] z-10 bg-bg-color py-4 -mx-4 px-4 border-b border-border-color space-y-3">
                <Button onClick={() => setIsDiscoverModalOpen(true)} className="w-full">
                    <SparklesIcon /> Descubrir Nuevos Ejercicios (IA)
                </Button>
                <div className="relative">
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Buscar por nombre..."
                        className="w-full bg-slate-900 border border-slate-700 rounded-full pl-10 pr-4 py-2 focus:outline-none focus:ring-2 focus:ring-primary-color"
                    />
                    <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={20} />
                </div>
                
                <details className="glass-card !p-0 !bg-transparent !border-none">
                    <summary className="text-sm font-semibold text-slate-400 cursor-pointer py-1">Filtros Avanzados</summary>
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-2 pt-2">
                        <select value={muscleFilter} onChange={e => setMuscleFilter(e.target.value)} className="w-full">
                            {filterOptions.muscles.map(cat => <option key={cat} value={cat}>{cat === 'All' ? 'Músculo Principal' : cat}</option>)}
                        </select>
                        <select value={equipmentFilter} onChange={e => setEquipmentFilter(e.target.value)} className="w-full">
                            {filterOptions.equipment.map(eq => <option key={eq} value={eq}>{eq === 'All' ? 'Equipamiento' : eq}</option>)}
                        </select>
                         <select value={categoryFilter} onChange={e => setCategoryFilter(e.target.value)} className="w-full">
                            {filterOptions.categories.map(cat => <option key={cat} value={cat}>{cat === 'All' ? 'Categoría' : cat}</option>)}
                        </select>
                        <select value={typeFilter} onChange={e => setTypeFilter(e.target.value)} className="w-full">
                            {filterOptions.types.map(t => <option key={t} value={t}>{t === 'All' ? 'Tipo' : t}</option>)}
                        </select>
                        <select value={chainFilter} onChange={e => setChainFilter(e.target.value)} className="w-full">
                            {filterOptions.chains.map(c => <option key={c} value={c}>{c === 'All' ? 'Cadena Muscular' : c}</option>)}
                        </select>
                    </div>
                </details>
            </div>

            <p className="text-sm text-slate-500 text-center my-4">{filteredExercises.length} resultados</p>

            <div className="mt-4 space-y-2">
                {filteredExercises.map(ex => (
                    <ExerciseItem key={ex.id} exercise={ex} />
                ))}
                 {filteredExercises.length === 0 && (
                    <p className="text-center text-slate-400 pt-8">No se encontraron ejercicios con los filtros seleccionados.</p>
                )}
            </div>
        </div>
    );
};

export default ExerciseDatabaseView;