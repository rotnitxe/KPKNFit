// components/SubstituteExerciseSheet.tsx
import React, { useState, useEffect } from 'react';
import { Exercise, Settings } from '../types';
import { suggestExerciseAlternatives } from '../services/aiService';
import { useAppState } from '../contexts/AppContext';
import { XIcon } from './icons';
import Button from './ui/Button';
import SkeletonLoader from './ui/SkeletonLoader';

interface SubstituteExerciseSheetProps {
  isOpen: boolean;
  onClose: () => void;
  exercise: Exercise | null;
  onSelectAlternative: (newExerciseName: string) => void;
}

const SubstituteExerciseSheet: React.FC<SubstituteExerciseSheetProps> = ({ isOpen, onClose, exercise, onSelectAlternative }) => {
    const { settings, exerciseList, isOnline } = useAppState();
    const [reason, setReason] = useState<'busy' | 'pain' | 'variety' | null>(null);
    const [alternatives, setAlternatives] = useState<{ name: string; justification: string }[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    
    useEffect(() => {
        if (!isOpen) {
            setReason(null);
            setAlternatives([]);
            setIsLoading(false);
            setError(null);
        }
    }, [isOpen]);

    const handleReasonSelect = async (selectedReason: 'busy' | 'pain' | 'variety') => {
        if (!exercise || !isOnline) return;
        setReason(selectedReason);
        setIsLoading(true);
        setError(null);
        setAlternatives([]);
        
        const reasonText = { busy: 'equipo ocupado', pain: 'molestia leve', variety: 'buscar variedad' }[selectedReason];
        const primaryMuscle = exerciseList.find(ex => ex.id === exercise.exerciseDbId)?.involvedMuscles.find(m => m.role === 'primary')?.muscle || 'músculo principal';
        
        try {
            const result = await suggestExerciseAlternatives(exercise, reasonText, primaryMuscle, settings);
            setAlternatives(result.alternatives);
        } catch (err: any) {
            setError(err.message || "No se pudieron obtener alternativas.");
        } finally {
            setIsLoading(false);
        }
    };
    
    const renderContent = () => {
        if (isLoading) {
            return <div className="p-4"><SkeletonLoader lines={5} /></div>;
        }
        if (error) {
            return <p className="p-4 text-center text-red-400">{error}</p>;
        }
        if (reason && alternatives.length > 0) {
            return (
                 <div className="space-y-3">
                    {alternatives.map((alt, index) => (
                        <button key={index} onClick={() => onSelectAlternative(alt.name)} className="w-full text-left p-3 bg-slate-800/50 hover:bg-slate-700/50 rounded-lg">
                            <p className="font-semibold text-primary-color">{alt.name}</p>
                            <p className="text-xs text-slate-400">{alt.justification}</p>
                        </button>
                    ))}
                </div>
            );
        }
        return (
             <div className="space-y-3">
                <Button onClick={() => handleReasonSelect('busy')} className="w-full !justify-start !py-3">Equipo Ocupado</Button>
                <Button onClick={() => handleReasonSelect('pain')} className="w-full !justify-start !py-3">Molestia Leve</Button>
                <Button onClick={() => handleReasonSelect('variety')} className="w-full !justify-start !py-3">Buscar Variedad</Button>
            </div>
        );
    }
    
    if (!isOpen) return null;
    
    return (
        <>
            <div className="bottom-sheet-backdrop animate-fade-in" onClick={onClose} />
            <div className="bottom-sheet-content open animate-slide-in-up">
                <div className="bottom-sheet-grabber" />
                 <header className="flex items-center justify-between p-4 flex-shrink-0 border-b border-border-color">
                    <h2 className="text-xl font-bold text-white">Sustituir: {exercise?.name}</h2>
                    <button onClick={onClose} className="p-2 text-slate-300"><XIcon /></button>
                </header>
                <div className="flex-grow overflow-y-auto p-4">
                    {!reason && <p className="text-sm text-slate-400 mb-4">¿Por qué quieres sustituir este ejercicio?</p>}
                    {renderContent()}
                </div>
            </div>
        </>
    );
};

export default SubstituteExerciseSheet;
