// components/ExerciseEditorView.tsx
import React, { useState, useEffect, useRef } from 'react';
import { ExerciseMuscleInfo } from '../types';
import { MUSCLE_GROUPS } from '../data/exerciseList';
import { generateExerciseDescription, analyzeExerciseMuscles, estimateSFR, generateExerciseAlias } from '../services/aiService';
import Button from './ui/Button';
import { SparklesIcon, SaveIcon, BrainIcon, PlusIcon, TrashIcon, ArrowLeftIcon, XIcon } from './icons';
import { useAppState, useAppDispatch } from '../contexts/AppContext';

const ExerciseEditorView: React.FC = () => {
  const { settings, isOnline, editingCustomExerciseData } = useAppState();
  const { addOrUpdateCustomExercise, handleBack, addToast } = useAppDispatch();
  
  const [exercise, setExercise] = useState<ExerciseMuscleInfo>({
    id: crypto.randomUUID(), name: '', description: '', involvedMuscles: [],
    category: 'Hipertrofia', type: 'Accesorio', equipment: 'Otro', force: 'Otro', isCustom: true
  });
  
  const [isLoading, setIsLoading] = useState(false);
  const [loadingAction, setLoadingAction] = useState<'desc' | 'muscle' | 'sfr' | 'alias' | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if(editingCustomExerciseData?.exercise) {
        setExercise(JSON.parse(JSON.stringify(editingCustomExerciseData.exercise)));
    } else {
        setExercise({
            id: crypto.randomUUID(), name: editingCustomExerciseData?.preFilledName || '', description: '', involvedMuscles: [],
            category: 'Hipertrofia', type: 'Accesorio', equipment: 'Otro', force: 'Otro', isCustom: true
        });
    }
    setError('');
    setIsLoading(false);
    setLoadingAction(null);
  }, [editingCustomExerciseData]);

  const handleChange = (field: keyof ExerciseMuscleInfo, value: any) => {
    setExercise(prev => ({ ...prev, [field]: value }));
  };
  
  const handleGenerateDescription = async () => {
      if (!isOnline || !exercise.name) return;
      setIsLoading(true);
      setLoadingAction('desc');
      setError('');
      try {
        const desc = await generateExerciseDescription(exercise.name, settings);
        handleChange('description', desc);
      } catch (err: any) {
        setError(err.message || 'Error al generar descripción');
      } finally {
        setIsLoading(false);
        setLoadingAction(null);
      }
  };

  const handleAnalyzeMuscles = async () => {
    if (!isOnline || !exercise.name) return;
    setIsLoading(true);
    setLoadingAction('muscle');
    setError('');
    try {
        const { involvedMuscles } = await analyzeExerciseMuscles(exercise.name, settings);
        handleChange('involvedMuscles', involvedMuscles);
    } catch (err: any) {
        setError(err.message || 'Error al analizar músculos');
    } finally {
        setIsLoading(false);
        setLoadingAction(null);
    }
  };

   const handleEstimateSfr = async () => {
    if (!isOnline || !exercise.name) return;
    setIsLoading(true);
    setLoadingAction('sfr');
    setError('');
    try {
        const sfrData = await estimateSFR(exercise.name, settings);
        handleChange('sfr', sfrData);
    } catch (err: any) {
        setError(err.message || 'Error al estimar SFR');
    } finally {
        setIsLoading(false);
        setLoadingAction(null);
    }
  };

  const handleGenerateAlias = async () => {
    if (!isOnline || !exercise.name) return;
    setIsLoading(true);
    setLoadingAction('alias');
    setError('');
    try {
        const { alias } = await generateExerciseAlias(exercise.name, settings);
        handleChange('alias', alias);
    } catch (err: any) {
        setError(err.message || 'Error al generar alias');
    } finally {
        setIsLoading(false);
        setLoadingAction(null);
    }
  };

  const handleInvolvedMuscleChange = (index: number, field: 'muscle' | 'activation' | 'role', value: string) => {
    const updatedInvolved = [...(exercise.involvedMuscles || [])];
    const target = updatedInvolved[index];
    if (field === 'muscle' || field === 'role') {
        (target as any)[field] = value;
    } else { // activation
        target.activation = (parseInt(value, 10) || 0) / 100;
    }
    handleChange('involvedMuscles', updatedInvolved);
  }

  const addInvolvedMuscle = () => {
      const newMuscle = { muscle: 'Hombro', activation: 0.3, role: 'secondary' as const };
      handleChange('involvedMuscles', [...(exercise.involvedMuscles || []), newMuscle]);
  }
  
  const removeInvolvedMuscle = (index: number) => {
      const updated = (exercise.involvedMuscles || []).filter((_, i) => i !== index);
      handleChange('involvedMuscles', updated);
  }
  
  const handleSaveClick = () => {
    if (!exercise.name.trim()) {
        setError("El nombre del ejercicio no puede estar vacío.");
        addToast("El nombre del ejercicio no puede estar vacío.", "danger");
        return;
    }
    addOrUpdateCustomExercise(exercise);
    addToast(`Ejercicio "${exercise.name}" guardado.`, "success");
    handleBack();
  };

  const muscleOptions = MUSCLE_GROUPS.filter(g => g !== 'Todos');

  return (
    <div className="space-y-4">
        <div className="flex justify-between items-center">
            <h1 className="text-3xl font-bold text-white">{editingCustomExerciseData?.exercise ? "Editar Ejercicio" : "Crear Ejercicio"}</h1>
        </div>

        <input type="text" value={exercise.name} onChange={e => handleChange('name', e.target.value)} className="w-full bg-slate-800 border-slate-700 rounded-md p-2 font-bold text-lg"/>
        
        {/* ... Rest of the form fields ... */}
        {/* Alias */}
        <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Alias / Sobrenombre</label>
            <div className="flex gap-2">
                <input type="text" value={exercise.alias || ''} onChange={e => handleChange('alias', e.target.value)} className="w-full" placeholder="Ej: RDL, OHP..."/>
                <Button onClick={handleGenerateAlias} variant="secondary" className="!px-3" isLoading={loadingAction === 'alias'} disabled={!isOnline || isLoading || !exercise.name} title="Sugerir alias con IA"><SparklesIcon size={16}/></Button>
            </div>
        </div>

        {/* Description */}
        <div className="flex justify-between items-center mb-1">
            <label className="block text-sm font-medium text-slate-300">Descripción</label>
            <Button onClick={handleGenerateDescription} variant="secondary" className="!text-xs !py-1" isLoading={loadingAction === 'desc'} disabled={!isOnline || isLoading}><SparklesIcon size={14}/> Generar con IA</Button>
        </div>
        <textarea value={exercise.description} onChange={e => handleChange('description', e.target.value)} rows={4} className="w-full" />
        
        {/* Classification */}
        <div className="grid grid-cols-2 gap-4">
            <select value={exercise.type} onChange={e => handleChange('type', e.target.value as ExerciseMuscleInfo['type'])} className="w-full"><option value="Básico">Básico</option><option value="Accesorio">Accesorio</option><option value="Aislamiento">Aislamiento</option></select>
            <select value={exercise.category} onChange={e => handleChange('category', e.target.value as ExerciseMuscleInfo['category'])} className="w-full"><option value="Hipertrofia">Hipertrofia</option><option value="Fuerza">Fuerza</option><option value="Potencia">Potencia</option><option value="Resistencia">Resistencia</option><option value="Movilidad">Movilidad</option></select>
            <select value={exercise.equipment} onChange={e => handleChange('equipment', e.target.value as ExerciseMuscleInfo['equipment'])} className="w-full"><option value="Barra">Barra</option><option value="Mancuerna">Mancuerna</option><option value="Máquina">Máquina</option><option value="Peso Corporal">Peso Corporal</option><option value="Banda">Banda</option><option value="Kettlebell">Kettlebell</option><option value="Polea">Polea</option><option value="Otro">Otro</option></select>
            <select value={exercise.force} onChange={e => handleChange('force', e.target.value as ExerciseMuscleInfo['force'])} className="w-full"><option value="Empuje">Empuje</option><option value="Tirón">Tirón</option><option value="Bisagra">Bisagra</option><option value="Sentadilla">Sentadilla</option><option value="Rotación">Rotación</option><option value="Anti-Rotación">Anti-Rotación</option><option value="Otro">Otro</option></select>
        </div>

        {/* SFR Section */}
        <div className="bg-slate-900/50 p-3 rounded-lg space-y-2">
            <div className="flex justify-between items-center">
                <h4 className="font-semibold text-white">Ratio Estímulo-Fatiga (SFR)</h4>
                <Button onClick={handleEstimateSfr} variant="secondary" className="!text-xs !py-1" isLoading={loadingAction === 'sfr'} disabled={!isOnline || isLoading}><SparklesIcon size={14}/> Estimar con IA</Button>
            </div>
            <div className="flex items-center gap-2"><label className="text-sm">Puntuación (1-10):</label><input type="number" min="1" max="10" value={exercise.sfr?.score || ''} onChange={e => setExercise(p => ({ ...p, sfr: { ...p.sfr!, score: parseInt(e.target.value) || 0 } }))} className="w-20 text-center text-sm" /></div>
            <textarea value={exercise.sfr?.justification || ''} onChange={e => setExercise(p => ({ ...p, sfr: { ...p.sfr!, justification: e.target.value } }))} placeholder="Justificación..." rows={2} className="w-full text-sm"/>
        </div>

        {/* Muscles Section */}
        <div className="bg-slate-900/50 p-3 rounded-lg space-y-3">
            <div className="flex justify-between items-center">
                <h4 className="font-semibold text-white">Músculos Involucrados</h4>
                <Button onClick={handleAnalyzeMuscles} variant="secondary" className="!text-xs !py-1" isLoading={loadingAction === 'muscle'} disabled={!isOnline || isLoading}><BrainIcon size={14}/> Analizar con IA</Button>
            </div>
            <div className="space-y-2">
                {(exercise.involvedMuscles || []).map((inv, index) => (
                    <div key={index} className="grid grid-cols-12 gap-2 items-center">
                        <select value={inv.muscle} onChange={e => handleInvolvedMuscleChange(index, 'muscle', e.target.value)} className="col-span-5 text-sm"><option value="">Seleccionar...</option>{muscleOptions.map((group: string) => <option key={group} value={group}>{group}</option>)}</select>
                        <select value={inv.role} onChange={e => handleInvolvedMuscleChange(index, 'role', e.target.value)} className="col-span-3 text-sm"><option value="primary">Primario</option><option value="secondary">Secundario</option><option value="stabilizer">Estabilizador</option></select>
                        <div className="col-span-3 flex items-center gap-1"><input type="range" min="0" max="100" step="5" value={Math.round(inv.activation * 100)} onChange={e => handleInvolvedMuscleChange(index, 'activation', e.target.value)} className="w-full" /><span className="text-xs w-8 text-right">{Math.round(inv.activation * 100)}%</span></div>
                        <button onClick={() => removeInvolvedMuscle(index)} className="col-span-1"><TrashIcon size={16} className="text-slate-500 hover:text-red-400"/></button>
                    </div>
                ))}
                <Button onClick={addInvolvedMuscle} variant="secondary" className="!text-xs !py-1"><PlusIcon size={14}/> Añadir Músculo</Button>
            </div>
        </div>
        {error && <p className="text-red-400 text-center text-sm">{error}</p>}
        
        <div className="flex justify-end gap-4 pt-4 border-t border-slate-700 mt-4">
            <Button onClick={handleBack} variant="secondary">Cancelar</Button>
            <Button onClick={handleSaveClick}><SaveIcon size={16}/> {editingCustomExerciseData?.exercise ? 'Guardar Cambios' : 'Guardar Ejercicio'}</Button>
        </div>
    </div>
  );
};

export default ExerciseEditorView;