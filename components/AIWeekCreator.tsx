
import React, { useState } from 'react';
import { Program, ProgramWeek, Session, Exercise, ExerciseSet } from '../types';
import { generateWeekFromPrompt } from '../services/aiService';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { SparklesIcon, PlusIcon } from './icons';
import { useAppState } from '../contexts/AppContext';

interface AIWeekCreatorProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (week: ProgramWeek) => void;
  program: Program;
  isOnline: boolean;
}

const AIWeekCreator: React.FC<AIWeekCreatorProps> = ({ isOpen, onClose, onSave, program, isOnline }) => {
  const { settings } = useAppState();
  const [view, setView] = useState<'options' | 'ai'>('options');
  const [prompt, setPrompt] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generatedWeek, setGeneratedWeek] = useState<Omit<ProgramWeek, 'id'> | null>(null);

  // Fix: Access mesocycles via blocks property
  const allProgramWeeks = program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks)));

  const handleGenerate = async () => {
    if (!prompt || !isOnline) return;
    setIsLoading(true);
    setError(null);
    setGeneratedWeek(null);
    try {
      const result = await generateWeekFromPrompt(program, prompt, settings);
      setGeneratedWeek(result);
    } catch (err: any) {
      setError(err.message || 'Ocurrió un error desconocido.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleClose = () => {
    setPrompt('');
    setGeneratedWeek(null);
    setError(null);
    setView('options');
    onClose();
  }

  const handleCreateBlankWeek = () => {
    const newWeek: ProgramWeek = {
        id: crypto.randomUUID(),
        name: `Semana ${allProgramWeeks.length + 1}`,
        sessions: [],
    };
    onSave(newWeek);
  }

  const handleCopyWeek1 = () => {
    const week1 = allProgramWeeks[0];
    if (!week1) {
        alert("No se encontró la Semana 1 para copiar.");
        return;
    }
    const newWeek: ProgramWeek = {
        ...JSON.parse(JSON.stringify(week1)), // Deep copy
        id: crypto.randomUUID(),
        name: `Semana ${allProgramWeeks.length + 1} (Copia de Semana 1)`,
    };
    // Re-assign all IDs to ensure uniqueness
    newWeek.sessions.forEach((session: Session) => {
        session.id = crypto.randomUUID();
        session.exercises.forEach((ex: Exercise) => {
            ex.id = crypto.randomUUID();
            ex.sets.forEach((set: ExerciseSet) => {
                set.id = crypto.randomUUID();
            });
        });
    });
    onSave(newWeek);
  }

  const handleSave = () => {
    if (generatedWeek) {
        const finalWeek: ProgramWeek = {
            ...generatedWeek,
            id: crypto.randomUUID(),
            sessions: generatedWeek.sessions.map(s => ({
                ...s,
                id: crypto.randomUUID(),
                exercises: s.exercises.map(e => ({
                    ...e,
                    id: crypto.randomUUID(),
                    sets: e.sets.map(set => ({...set, id: crypto.randomUUID()}))
                }))
            }))
        };
        onSave(finalWeek);
    }
  }
  
  const hasPreviousWeek = allProgramWeeks.length > 0;

  const renderOptions = () => (
    <div className="p-2 space-y-4">
        <h3 className="text-lg font-semibold text-white text-center">¿Cómo quieres crear la Semana {allProgramWeeks.length + 1}?</h3>
        <Button onClick={() => setView('ai')} className="w-full !justify-start !text-base !py-4" disabled={!isOnline}><SparklesIcon className="text-[var(--primary-color-400)]" size={20}/> Generar con Asistente IA</Button>
        <Button onClick={handleCopyWeek1} disabled={allProgramWeeks.length === 0} variant="secondary" className="w-full !justify-start !text-base !py-4"><PlusIcon size={20}/> Copiar Semana 1</Button>
        <Button onClick={handleCreateBlankWeek} variant="secondary" className="w-full !justify-start !text-base !py-4"><PlusIcon size={20}/> Crear Semana en Blanco</Button>
    </div>
  );

  const renderAIView = () => (
    <div className="p-2">
         {!generatedWeek && !isLoading && (
          <div className="space-y-4 animate-fade-in">
            <h3 className="text-lg font-semibold text-white">
              {hasPreviousWeek ? `¿Cuál es el objetivo para la Semana ${allProgramWeeks.length + 1}?` : `¿Cuál es el objetivo para la Semana 1?`}
            </h3>
            {hasPreviousWeek && <p className="text-sm text-slate-400">La IA usará la semana anterior como base para aplicar sobrecarga progresiva.</p>}
            <textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Ejemplos:&#10;- Aumentar la intensidad y bajar las repeticiones.&#10;- Enfocarse más en el volumen de piernas.&#10;- Una semana de descarga (deload)."
              rows={4}
              className="w-full bg-slate-800 border border-slate-700 rounded-md p-2 text-white placeholder-slate-500"
              disabled={!isOnline}
            />
            <Button onClick={handleGenerate} disabled={!prompt || !isOnline} className="w-full">
              <SparklesIcon size={16} /> Generar Semana
            </Button>
             {error && <p className="text-red-400 text-sm text-center mt-2">{error}</p>}
          </div>
        )}

        {isLoading && (
           <div className="flex flex-col items-center justify-center p-8">
                <svg className="animate-spin h-8 w-8 text-[var(--primary-color-400)] mb-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <p className="text-slate-300">La IA está diseñando tu próxima semana...</p>
            </div>
        )}

        {generatedWeek && !isLoading && (
            <div className="animate-fade-in">
                <h3 className="text-lg font-semibold text-white mb-2">Vista Previa: {generatedWeek.name}</h3>
                <div className="space-y-3 max-h-80 overflow-y-auto bg-slate-900/50 p-3 rounded-lg">
                    {generatedWeek.sessions.map((session, sIndex) => (
                        <details key={sIndex} className="bg-slate-800 p-2 rounded-md">
                           <summary className="font-semibold text-slate-200 cursor-pointer list-none">{session.name}</summary>
                           <ul className="mt-2 pl-4 text-sm text-slate-400 space-y-1">
                                {session.exercises.map((ex, eIndex) => (
                                    <li key={eIndex} className="text-xs">{ex.sets.length} x {ex.sets[0]?.targetReps} {ex.name} @{ex.sets[0]?.targetRPE}</li>
                                ))}
                           </ul>
                        </details>
                    ))}
                </div>
                 <div className="flex justify-end gap-4 mt-6">
                    <Button variant="secondary" onClick={() => { setGeneratedWeek(null); setPrompt(''); }}>Atrás</Button>
                    <Button variant="primary" onClick={handleSave}>Añadir al Programa</Button>
                </div>
            </div>
        )}
    </div>
  );

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="Añadir Nueva Semana">
      {view === 'options' ? renderOptions() : renderAIView()}
    </Modal>
  );
};

export default AIWeekCreator;
