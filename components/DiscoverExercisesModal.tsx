// components/DiscoverExercisesModal.tsx
import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { batchGenerateExercises } from '../services/aiService';
import { ExerciseMuscleInfo } from '../types';
import { TacticalModal } from './ui/TacticalOverlays';
import Button from './ui/Button';
import { SparklesIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';
import { MUSCLE_GROUPS } from '../data/exerciseList';

interface DiscoverExercisesModalProps {
    isOpen: boolean;
    onClose: () => void;
}

const DiscoverExercisesModal: React.FC<DiscoverExercisesModalProps> = ({ isOpen, onClose }) => {
    const { settings, isOnline, exerciseList } = useAppState();
    const { batchAddExercises, addToast } = useAppDispatch();

    const [category, setCategory] = useState('Polea');
    const [muscleGroup, setMuscleGroup] = useState('Espalda');
    const [count, setCount] = useState(5);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [results, setResults] = useState<ExerciseMuscleInfo[]>([]);
    const [selected, setSelected] = useState<Set<string>>(new Set());

    const equipmentOptions = [...new Set(exerciseList.map(e => e.equipment))].sort();

    const handleGenerate = async () => {
        if (!isOnline) return;
        setIsLoading(true);
        setError(null);
        setResults([]);
        setSelected(new Set());
        try {
            const existingNames = exerciseList.map(ex => ex.name);
            const response = await batchGenerateExercises(category, muscleGroup, count, existingNames, settings);
            setResults(response);
            if (response.length === 0) {
                addToast("La IA no devolvió nuevos ejercicios. Intenta con otra combinación.", "suggestion");
            }
        } catch (err: any) {
            setError(err.message || 'Error al generar ejercicios.');
        } finally {
            setIsLoading(false);
        }
    };
    
    const toggleSelection = (exerciseId: string) => {
        setSelected(prev => {
            const newSet = new Set(prev);
            if (newSet.has(exerciseId)) {
                newSet.delete(exerciseId);
            } else {
                newSet.add(exerciseId);
            }
            return newSet;
        });
    };

    const handleSave = () => {
        const selectedExercises = results.filter(ex => selected.has(ex.id));
        batchAddExercises(selectedExercises);
        onClose();
    };

    return (
        <TacticalModal isOpen={isOpen} onClose={onClose} title="Descubrir Nuevos Ejercicios">
            <div className="space-y-4 max-h-[70vh] flex flex-col">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                        <label className="text-sm">Categoría de Equipo</label>
                        <select value={category} onChange={e => setCategory(e.target.value)} className="w-full mt-1">
                            {equipmentOptions.map(opt => <option key={opt} value={opt}>{opt}</option>)}
                        </select>
                    </div>
                     <div>
                        <label className="text-sm">Grupo Muscular</label>
                        <select value={muscleGroup} onChange={e => setMuscleGroup(e.target.value)} className="w-full mt-1">
                            {MUSCLE_GROUPS.filter(m => m !== 'Todos').map(opt => <option key={opt} value={opt}>{opt}</option>)}
                        </select>
                    </div>
                </div>
                <div>
                    <label className="text-sm">Número de Ejercicios (1-10)</label>
                    <input type="number" min="1" max="10" value={count} onChange={e => setCount(parseInt(e.target.value))} className="w-full mt-1"/>
                </div>

                <Button onClick={handleGenerate} isLoading={isLoading} disabled={!isOnline || isLoading}>
                    <SparklesIcon/> Generar con IA
                </Button>

                {isLoading && <SkeletonLoader lines={5} />}
                {error && <p className="text-red-400 text-center">{error}</p>}
                
                {results.length > 0 && (
                    <div className="flex-grow space-y-2 overflow-y-auto pr-2 bg-slate-900/50 p-3 rounded-lg min-h-[100px]">
                        <h3 className="text-lg font-semibold text-white">Ejercicios Encontrados</h3>
                        {results.map(ex => (
                            <div key={ex.id} onClick={() => toggleSelection(ex.id)} className={`flex items-center gap-3 p-2 rounded-lg cursor-pointer ${selected.has(ex.id) ? 'bg-primary-color/20 ring-1 ring-primary-color' : 'bg-slate-800/50'}`}>
                                <input type="checkbox" checked={selected.has(ex.id)} readOnly className="form-checkbox h-5 w-5 rounded text-primary-color bg-slate-700 border-slate-600 focus:ring-primary-color"/>
                                <div>
                                    <p className="font-semibold">{ex.name}</p>
                                    <p className="text-xs text-slate-400">{ex.description}</p>
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                <div className="flex justify-end gap-2 pt-4 border-t border-slate-700">
                    <Button variant="secondary" onClick={onClose}>Cancelar</Button>
                    <Button onClick={handleSave} disabled={selected.size === 0}>
                        Añadir {selected.size} a KPKN
                    </Button>
                </div>
            </div>
        </TacticalModal>
    );
};

export default DiscoverExercisesModal;