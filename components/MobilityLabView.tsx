// components/MobilityLabView.tsx
import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { generateMobilityRoutine } from '../services/aiService';
import { MobilityExercise } from '../types';
import { SearchIcon, SparklesIcon, ClockIcon, ArrowLeftIcon } from './icons';
import Button from './ui/Button';
import Card from './ui/Card';
import SkeletonLoader from './ui/SkeletonLoader';
import { MUSCLE_GROUPS } from '../data/exerciseList';

const MobilityLabView: React.FC = () => {
    const { settings, isOnline, exerciseList } = useAppState();
    const { handleBack } = useAppDispatch();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedTarget, setSelectedTarget] = useState<string | null>(null);
    const [routine, setRoutine] = useState<MobilityExercise[] | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const suggestions = useMemo(() => {
        if (searchQuery.length < 2) return [];
        const query = searchQuery.toLowerCase();
        const muscleSuggestions = MUSCLE_GROUPS.filter(m => m !== 'Todos' && m.toLowerCase().includes(query));
        const exerciseSuggestions = exerciseList.filter(ex => ex.name.toLowerCase().includes(query)).map(ex => ex.name);
        return [...new Set([...muscleSuggestions, ...exerciseSuggestions])].slice(0, 10);
    }, [searchQuery, exerciseList]);

    const handleSelectSuggestion = (suggestion: string) => {
        setSearchQuery(suggestion);
        setSelectedTarget(suggestion);
    };

    const handleGenerate = async () => {
        if (!selectedTarget || !isOnline) return;
        setIsLoading(true);
        setError(null);
        setRoutine(null);
        try {
            const result = await generateMobilityRoutine(selectedTarget, settings);
            if (result.length === 0) {
                setError("La IA no pudo generar una rutina para esta selección. Intenta con algo más general.");
            } else {
                setRoutine(result);
            }
        } catch (err: any) {
            setError(err.message || 'Ocurrió un error al generar la rutina.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="pb-28 animate-fade-in">
            <header className="flex items-center gap-4 mb-6 -mx-4 px-4">
                 <button onClick={handleBack} className="p-2 text-slate-300">
                    <ArrowLeftIcon />
                </button>
                <div>
                    <h1 className="text-3xl font-bold text-white">Laboratorio de Movilidad</h1>
                    <p className="text-slate-400 text-sm">Crea un calentamiento dinámico para tu sesión.</p>
                </div>
            </header>

            <div className="space-y-6">
                <Card>
                    <div className="relative">
                        <label className="block text-sm font-medium text-slate-300 mb-1">
                            Elige un ejercicio principal o grupo muscular
                        </label>
                        <div className="relative">
                            <input
                                type="text"
                                value={searchQuery}
                                onChange={(e) => {
                                    setSearchQuery(e.target.value);
                                    setSelectedTarget(null); // Clear selection when typing
                                }}
                                placeholder="Ej: Sentadilla Trasera, Cadera..."
                                className="w-full bg-slate-900 border border-slate-700 rounded-full pl-10 pr-4 py-2 focus:outline-none focus:ring-2 focus:ring-primary-color"
                            />
                            <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={20} />
                        </div>
                        {suggestions.length > 0 && !selectedTarget && (
                            <ul className="absolute z-10 w-full mt-1 bg-slate-800 border border-slate-700 rounded-lg shadow-lg max-h-60 overflow-y-auto">
                                {suggestions.map(item => (
                                    <li key={item} onClick={() => handleSelectSuggestion(item)} className="px-4 py-2 text-white cursor-pointer hover:bg-primary-color">
                                        {item}
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                    <Button onClick={handleGenerate} disabled={!selectedTarget || !isOnline || isLoading} isLoading={isLoading} className="w-full mt-4">
                        <SparklesIcon /> Generar Rutina
                    </Button>
                </Card>

                {isLoading && (
                    <Card>
                        <SkeletonLoader lines={5} />
                    </Card>
                )}

                {error && <p className="text-red-400 text-center">{error}</p>}

                {routine && (
                    <Card className="animate-fade-in">
                        <h2 className="text-2xl font-bold mb-4">Rutina para: <span className="text-primary-color">{selectedTarget}</span></h2>
                        <div className="space-y-3">
                            {routine.map((item, index) => (
                                <div key={index} className="bg-slate-900/50 p-4 rounded-lg">
                                    <div className="flex justify-between items-center mb-1">
                                        <h3 className="font-semibold text-white">{item.name}</h3>
                                        <div className="flex items-center gap-1 text-sm font-mono text-slate-300 bg-slate-700 px-2 py-0.5 rounded-full">
                                            <ClockIcon size={12} />
                                            <span>{item.duration}s</span>
                                        </div>
                                    </div>
                                    <p className="text-sm text-slate-400">{item.instruction}</p>
                                </div>
                            ))}
                        </div>
                    </Card>
                )}
            </div>
        </div>
    );
};

export default MobilityLabView;