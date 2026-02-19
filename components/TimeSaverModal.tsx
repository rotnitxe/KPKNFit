import React, { useState, useEffect } from 'react';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { Exercise, Settings } from '../types';
import { generateTimeSaverSuggestions } from '../services/aiService';
import { ZapIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';
import { useAppState } from '../contexts/AppContext';

interface TimeSaverModalProps {
  isOpen: boolean;
  onClose: () => void;
  remainingExercises: Exercise[];
  isOnline: boolean;
  onApply: (changes: any[]) => void;
}

const TimeSaverModal: React.FC<TimeSaverModalProps> = ({ isOpen, onClose, remainingExercises, isOnline, onApply }) => {
  const { settings } = useAppState();
  const [suggestions, setSuggestions] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [timeAvailable, setTimeAvailable] = useState('');
  const [hasSearched, setHasSearched] = useState(false);

  useEffect(() => {
    // Reset state when modal is closed or opened
    setSuggestions([]);
    setIsLoading(false);
    setError(null);
    setTimeAvailable('');
    setHasSearched(false);
  }, [isOpen]);

  const handleFetchSuggestions = async () => {
    const minutes = parseInt(timeAvailable, 10);
    if (!minutes || minutes <= 0 || !isOnline || remainingExercises.length < 2) {
        setError("Introduce un tiempo válido. Se necesitan al menos 2 ejercicios restantes.");
        return;
    }
    
    setIsLoading(true);
    setHasSearched(true);
    setError(null);
    setSuggestions([]);
    try {
      const result = await generateTimeSaverSuggestions(remainingExercises, minutes, settings);
      setSuggestions(result.suggestions);
    } catch (err: any) {
      setError(err.message || 'Error al obtener sugerencias.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleApplySuggestion = (suggestion: any) => {
    onApply(suggestion.changes);
    onClose();
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Time Saver IA">
      <div className="space-y-4 p-2">
        {!hasSearched && (
            <div className="text-center space-y-4 animate-fade-in">
                <label htmlFor="time-input" className="block text-lg font-medium text-slate-200">¿Cuántos minutos te quedan?</label>
                <input
                    id="time-input"
                    type="number"
                    value={timeAvailable}
                    onChange={(e) => setTimeAvailable(e.target.value)}
                    placeholder="Ej: 20"
                    className="w-48 mx-auto text-4xl font-bold text-center bg-slate-800 border-slate-700 rounded-lg p-2"
                />
                <Button onClick={handleFetchSuggestions} disabled={!isOnline || !timeAvailable} className="w-full !py-3">
                    Obtener Sugerencias
                </Button>
            </div>
        )}

        {isLoading && (
            <div className="text-center p-4">
                <p className="text-slate-300 mb-4">La IA está buscando formas de acortar tu sesión...</p>
                <SkeletonLoader lines={5} />
            </div>
        )}
        
        {hasSearched && !isLoading && error && <p className="text-red-400 text-center">{error}</p>}
        
        {hasSearched && !isLoading && !error && suggestions.length > 0 && (
          <div className="space-y-3">
            {suggestions.map((s, index) => (
              <div key={index} className="glass-card-nested p-4">
                <h4 className="font-bold text-primary-color flex items-center gap-2"><ZapIcon size={16}/> {s.title}</h4>
                <p className="text-sm text-slate-300 mt-1 mb-3">{s.description}</p>
                <Button onClick={() => handleApplySuggestion(s)} className="w-full">Aplicar Sugerencia</Button>
              </div>
            ))}
          </div>
        )}
        
        {hasSearched && !isLoading && !error && suggestions.length === 0 && (
          <p className="text-center text-slate-400 p-8">No se encontraron sugerencias para el tiempo especificado.</p>
        )}
      </div>
    </Modal>
  );
};

export default TimeSaverModal;