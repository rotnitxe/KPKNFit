// components/CustomExerciseEditorModal.tsx
import React, { useState, useEffect, useMemo } from 'react';
import ReactDOM from 'react-dom';
import { ExerciseMuscleInfo } from '../types';
import { MUSCLE_GROUPS } from '../data/exerciseList';
import Button from './ui/Button';
import { SaveIcon, PlusIcon, XIcon, ActivityIcon } from './icons';
import { useAppContext } from '../contexts/AppContext';
import ToggleSwitch from './ui/ToggleSwitch';

interface CustomExerciseEditorModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (exercise: ExerciseMuscleInfo) => void;
  isOnline: boolean;
  existingExercise?: ExerciseMuscleInfo;
  preFilledName?: string;
}

const CustomExerciseEditorModal: React.FC<CustomExerciseEditorModalProps> = ({ isOpen, onClose, onSave, existingExercise, preFilledName }) => {
  const { addToast, exerciseList } = useAppContext();
  const [isVisible, setIsVisible] = useState(false);
  const [exercise, setExercise] = useState<ExerciseMuscleInfo>({
    id: crypto.randomUUID(), name: '', description: '', involvedMuscles: [],
    category: 'Hipertrofia', type: 'Accesorio', equipment: 'Otro', force: 'Otro', isCustom: true
  });
  const [error, setError] = useState('');
  
  // --- SISTEMA AUGE: Variables Transitorias de la Matriz ---
  const [isAxialLoaded, setIsAxialLoaded] = useState(false);

  useEffect(() => {
    if (isOpen) {
        setIsVisible(true);
        if(existingExercise) {
            setExercise(JSON.parse(JSON.stringify(existingExercise)));
        } else {
            setExercise({
                id: crypto.randomUUID(), name: preFilledName || '', description: '', involvedMuscles: [],
                category: 'Hipertrofia', type: 'Accesorio', equipment: 'Otro', force: 'Otro', isCustom: true
            });
        }
        setError('');
        setIsAxialLoaded(false);
    } else {
        const t = setTimeout(() => setIsVisible(false), 300);
        return () => clearTimeout(t);
    }
  }, [isOpen, existingExercise, preFilledName]);

  const handleChange = (field: keyof ExerciseMuscleInfo, value: any) => {
    setExercise(prev => ({ ...prev, [field]: value }));
  };

  // --- MOTOR PREDICTIVO AUGE (Se calcula en vivo) ---
  const predictedAuge = useMemo(() => {
    let baseEfc = 2.0; let baseCnc = 2.0;
    
    if (exercise.type === 'Aislamiento') { 
        baseEfc = 1.5; baseCnc = 1.5; 
    } else {
        switch (exercise.force) {
            case 'Sentadilla': baseEfc = 4.0; baseCnc = 4.0; break;
            case 'Bisagra': baseEfc = 4.5; baseCnc = 4.5; break;
            case 'Empuje': baseEfc = 3.2; baseCnc = 3.2; break; 
            case 'Tirón': baseEfc = 3.2; baseCnc = 3.2; break;
            case 'Anti-Extensión': case 'Anti-Flexión': case 'Anti-Rotación': case 'Flexión': baseEfc = 2.0; baseCnc = 2.5; break;
            default: baseEfc = 2.5; baseCnc = 2.5; break;
        }
    }

    let eqEfcMult = 1.0; let eqCncMult = 1.0;
    switch (exercise.equipment) {
        case 'Barra': eqEfcMult = 1.0; eqCncMult = 1.2; break;
        case 'Mancuerna': eqEfcMult = 0.9; eqCncMult = 1.1; break;
        case 'Máquina': case 'Polea': eqEfcMult = 0.8; eqCncMult = 0.6; break;
        case 'Peso Corporal': eqEfcMult = 0.8; eqCncMult = 0.8; break;
        default: eqEfcMult = 1.0; eqCncMult = 1.0; break;
    }

    let efc = baseEfc * eqEfcMult;
    let cnc = baseCnc * eqCncMult;
    
    if (exercise.category === 'Fuerza' || exercise.category === 'Potencia') cnc += 0.5;

    let ssc = 0.2;
    if (isAxialLoaded) {
        if (exercise.equipment === 'Barra') ssc = 1.5;
        else if (exercise.equipment === 'Mancuerna') ssc = 1.0;
        else ssc = 0.8; 
    }

    return { 
        efc: Math.min(5.0, Math.max(0.5, efc)), 
        cnc: Math.min(5.0, Math.max(0.5, cnc)), 
        ssc: Math.min(2.0, Math.max(0.0, ssc)) 
    };
  }, [exercise.type, exercise.force, exercise.equipment, exercise.category, isAxialLoaded]);

  // Interceptamos el cambio de patrón para auto-asignar músculos si está vacío
  const handleForceChange = (newForce: string) => {
    handleChange('force', newForce);
    if (exercise.involvedMuscles.length === 0) {
        let muscles: any[] = [];
        switch (newForce) {
            case 'Sentadilla': muscles = [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 }]; break;
            case 'Bisagra': muscles = [{ muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 }, { muscle: 'Espalda Baja', role: 'stabilizer', activation: 0.3 }]; break;
            case 'Empuje': muscles = [{ muscle: 'Pectorales', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }, { muscle: 'Deltoides', role: 'secondary', activation: 0.5 }]; break;
            case 'Tirón': muscles = [{ muscle: 'Dorsales', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }]; break;
            case 'Anti-Extensión': case 'Flexión': muscles = [{ muscle: 'Abdomen', role: 'primary', activation: 1.0 }]; break;
        }
        if (muscles.length > 0) handleChange('involvedMuscles', muscles);
    }
  };

  const handleInvolvedMuscleChange = (index: number, field: 'muscle' | 'activation' | 'role', value: string) => {
    const updatedInvolved = [...(exercise.involvedMuscles || [])];
    const target = updatedInvolved[index];
    if (field === 'muscle') {
        target.muscle = value;
    } else if (field === 'role') {
        target.role = value as any;
        target.activation = value === 'primary' ? 1.0 : (value === 'secondary' ? 0.5 : 0.0);
    }
    handleChange('involvedMuscles', updatedInvolved);
  }

  const addInvolvedMuscle = () => {
      const newMuscle = { muscle: '', activation: 0.5, role: 'secondary' as const };
      handleChange('involvedMuscles', [...(exercise.involvedMuscles || []), newMuscle]);
  }
  
  const removeInvolvedMuscle = (index: number) => {
      const updated = (exercise.involvedMuscles || []).filter((_, i) => i !== index);
      handleChange('involvedMuscles', updated);
  }
  
  const handleSaveClick = () => {
    if (!exercise.name.trim()) {
        setError("El nombre del ejercicio es obligatorio.");
        addToast("El nombre del ejercicio es obligatorio.", "danger");
        return;
    }
    if (exercise.involvedMuscles.length === 0 || exercise.involvedMuscles.some(m => !m.muscle)) {
        setError("Asigna correctamente los músculos implicados.");
        addToast("Revisa los músculos implicados.", "danger");
        return;
    }
    
    const finalExercise = { ...exercise };
    if (finalExercise.efc === undefined) finalExercise.efc = Number(predictedAuge.efc.toFixed(1));
    if (finalExercise.cnc === undefined) finalExercise.cnc = Number(predictedAuge.cnc.toFixed(1));
    if (finalExercise.ssc === undefined) finalExercise.ssc = Number(predictedAuge.ssc.toFixed(1));

    onSave(finalExercise);
    onClose();
  };

  const muscleOptions = useMemo(() => {
    const allMuscles = new Set(exerciseList.flatMap(ex => ex.involvedMuscles?.map(m => m.muscle) || []));
    MUSCLE_GROUPS.forEach(m => allMuscles.add(m));
    allMuscles.delete('Todos');
    return Array.from(allMuscles).filter(Boolean).sort();
  }, [exerciseList]);

  if (!isOpen && !isVisible) return null;

  return ReactDOM.createPortal(
    <div
      className={`fixed inset-0 z-[200] flex items-end justify-center transition-all duration-300 ease-out ${isOpen && isVisible ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
      role="dialog"
      aria-modal="true"
    >
      {/* Backdrop con blur */}
      <div
        className="absolute inset-0 bg-black/70 backdrop-blur-sm transition-opacity duration-300"
        onClick={onClose}
        aria-hidden="true"
      />
      {/* Drawer: desliza desde abajo, 90% altura, borde superior naranja */}
      <div
        className={`relative z-10 w-full max-w-lg max-h-[90vh] flex flex-col bg-[#0a0a0a] text-white overflow-hidden rounded-t-2xl shadow-2xl border-t-4 border-orange-500/80 transform transition-transform duration-300 ease-out ${isOpen && isVisible ? 'translate-y-0' : 'translate-y-full'}`}
      >
            {/* Header Fijo */}
            <div className="flex justify-between items-center p-5 border-b border-orange-500/20 bg-[#0a0a0a] shrink-0">
                <h2 className="text-xs font-black uppercase tracking-widest text-orange-500/90">{existingExercise ? "Editar Ejercicio" : "Nuevo Ejercicio"}</h2>
                <button onClick={onClose} className="text-orange-500/70 hover:text-orange-400 transition-colors p-1">
                    <XIcon size={20}/>
                </button>
            </div>
            
            {/* Cuerpo Scrollable */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6 custom-scrollbar">
                
                {/* Info Principal: Grid de 2 columnas para optimizar espacio */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="space-y-1 md:col-span-2">
                        <label className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest">Nombre</label>
                        <input 
                            type="text" 
                            value={exercise.name} 
                            onChange={e => handleChange('name', e.target.value)} 
                            placeholder="Ej. Press Militar Libre"
                            className="w-full bg-[#0a0a0a] border border-orange-500/20 rounded-xl p-3 text-sm font-bold text-white focus:border-orange-500/50 outline-none transition-colors placeholder:text-zinc-600"
                        />
                    </div>
                    <div className="space-y-1">
                        <label className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest">Alias</label>
                        <input 
                            type="text" 
                            value={exercise.alias || ''} 
                            onChange={e => handleChange('alias', e.target.value)} 
                            placeholder="Ej. OHP"
                            className="w-full bg-[#0a0a0a] border border-orange-500/20 rounded-xl p-3 text-sm font-bold text-white focus:border-orange-500/50 outline-none transition-colors placeholder:text-zinc-600 text-center"
                        />
                    </div>
                </div>

                {/* Clasificación en Grid 2x2 compacta */}
                <div className="bg-[#0a0a0a] border border-orange-500/20 rounded-xl p-4 grid grid-cols-2 gap-4">
                    <div className="space-y-1">
                        <label className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest">Patrón</label>
                        <select value={exercise.force} onChange={e => handleForceChange(e.target.value)} className="w-full text-xs font-bold bg-[#0a0a0a] border border-orange-500/20 rounded-lg p-2 text-white outline-none focus:border-orange-500/50">
                            <option value="Empuje">Empuje</option>
                            <option value="Tirón">Tirón</option>
                            <option value="Bisagra">Bisagra</option>
                            <option value="Sentadilla">Sentadilla</option>
                            <option value="Rotación">Rotación</option>
                            <option value="Anti-Rotación">Anti-Rotación</option>
                            <option value="Flexión">Flexión</option>
                            <option value="Extensión">Extensión</option>
                            <option value="Anti-Flexión">Anti-Flexión</option>
                            <option value="Anti-Extensión">Anti-Extensión</option>
                            <option value="Otro">Otro</option>
                        </select>
                    </div>
                    <div className="space-y-1">
                        <label className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest">Equipo</label>
                        <select value={exercise.equipment} onChange={e => handleChange('equipment', e.target.value as any)} className="w-full text-xs font-bold bg-[#0a0a0a] border border-orange-500/20 rounded-lg p-2 text-white outline-none focus:border-orange-500/50">
                            <option value="Barra">Barra</option>
                            <option value="Mancuerna">Mancuerna</option>
                            <option value="Máquina">Máquina</option>
                            <option value="Peso Corporal">Peso Corporal</option>
                            <option value="Banda">Banda</option>
                            <option value="Kettlebell">Kettlebell</option>
                            <option value="Polea">Polea</option>
                            <option value="Otro">Otro</option>
                        </select>
                    </div>
                    <div className="space-y-1">
                        <label className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest">Tipo</label>
                        <select value={exercise.type} onChange={e => handleChange('type', e.target.value as any)} className="w-full text-xs font-bold bg-[#0a0a0a] border border-orange-500/20 rounded-lg p-2 text-white outline-none focus:border-orange-500/50">
                            <option value="Básico">Básico</option>
                            <option value="Accesorio">Accesorio</option>
                            <option value="Aislamiento">Aislamiento</option>
                        </select>
                    </div>
                    <div className="space-y-1">
                        <label className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest">Categoría</label>
                        <select value={exercise.category} onChange={e => handleChange('category', e.target.value as any)} className="w-full text-xs font-bold bg-[#0a0a0a] border border-orange-500/20 rounded-lg p-2 text-white outline-none focus:border-orange-500/50">
                            <option value="Hipertrofia">Hipertrofia</option>
                            <option value="Fuerza">Fuerza</option>
                            <option value="Potencia">Potencia</option>
                            <option value="Resistencia">Resistencia</option>
                            <option value="Movilidad">Movilidad</option>
                        </select>
                    </div>
                </div>

                {/* Pregunta Clave AUGE */}
                <div className="bg-[#0a0a0a] p-4 rounded-xl border border-orange-500/20 flex justify-between items-center">
                    <div>
                        <span className="text-xs font-bold text-white block">Carga Axial (Espalda)</span>
                        <span className="text-[9px] text-zinc-500 font-medium leading-tight block mt-1">¿El peso descansa o comprime tu columna?</span>
                    </div>
                    <ToggleSwitch checked={isAxialLoaded} onChange={setIsAxialLoaded} />
                </div>

                {/* Perfil AUGE */}
                <div className="bg-[#0a0a0a] p-4 rounded-xl border border-orange-500/20 space-y-3">
                    <div className="flex justify-between items-center mb-2">
                        <h4 className="text-xs font-black text-white uppercase tracking-widest flex items-center gap-2">
                            <ActivityIcon size={14}/> Motor Drenaje AUGE
                        </h4>
                        <span className="text-[9px] text-zinc-500 uppercase font-bold text-right">Auto-calculado</span>
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                        <div className="space-y-1">
                            <label className="text-[9px] font-bold text-zinc-500 uppercase block text-center">EFC (Local)</label>
                            <input type="number" min="1" max="5" step="0.1" value={exercise.efc || ''} onChange={e => handleChange('efc', parseFloat(e.target.value) || undefined)} className="w-full bg-[#0a0a0a] border border-orange-500/20 rounded-lg p-2.5 text-xs font-bold text-white text-center focus:border-orange-500/50 outline-none placeholder:text-zinc-600" placeholder={`Auto (${predictedAuge.efc.toFixed(1)})`} />
                        </div>
                        <div className="space-y-1">
                            <label className="text-[9px] font-bold text-zinc-500 uppercase block text-center">CNC (SNC)</label>
                            <input type="number" min="1" max="5" step="0.1" value={exercise.cnc || ''} onChange={e => handleChange('cnc', parseFloat(e.target.value) || undefined)} className="w-full bg-[#0a0a0a] border border-orange-500/20 rounded-lg p-2.5 text-xs font-bold text-white text-center focus:border-orange-500/50 outline-none placeholder:text-zinc-600" placeholder={`Auto (${predictedAuge.cnc.toFixed(1)})`} />
                        </div>
                        <div className="space-y-1">
                            <label className="text-[9px] font-bold text-zinc-500 uppercase block text-center">SSC (Espinal)</label>
                            <input type="number" min="0" max="2" step="0.1" value={exercise.ssc || ''} onChange={e => handleChange('ssc', parseFloat(e.target.value) || undefined)} className="w-full bg-[#0a0a0a] border border-orange-500/20 rounded-lg p-2.5 text-xs font-bold text-white text-center focus:border-orange-500/50 outline-none placeholder:text-zinc-600" placeholder={`Auto (${predictedAuge.ssc.toFixed(1)})`} />
                        </div>
                    </div>
                </div>

                {/* Músculos */}
                <div className="space-y-3">
                    <label className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest block">Músculos Implicados</label>
                    <div className="space-y-2">
                        {exercise.involvedMuscles.map((inv, idx) => (
                            <div key={idx} className="flex items-center gap-2 bg-[#0a0a0a] p-2 rounded-xl border border-orange-500/20">
                                <select value={inv.muscle} onChange={e => handleInvolvedMuscleChange(idx, 'muscle', e.target.value)} className="flex-grow text-xs bg-transparent border-none font-bold text-white outline-none">
                                    <option value="" className="text-zinc-500">Músculo...</option>
                                    {muscleOptions.map(m => <option key={m} value={m} className="bg-[#111] text-white">{m}</option>)}
                                </select>
                                <select value={inv.role} onChange={e => handleInvolvedMuscleChange(idx, 'role', e.target.value)} className={`w-28 text-[9px] font-black uppercase bg-[#0a0a0a] border border-orange-500/20 rounded p-1.5 outline-none ${inv.role === 'primary' ? 'text-orange-400' : 'text-zinc-500'}`}>
                                    <option value="primary">Primario</option>
                                    <option value="secondary">Secundario</option>
                                    <option value="stabilizer">Estabilizador</option>
                                </select>
                                <button onClick={() => removeInvolvedMuscle(idx)} className="text-zinc-600 hover:text-white p-2 transition-colors">
                                    <XIcon size={14}/>
                                </button>
                            </div>
                        ))}
                        <button 
                            onClick={addInvolvedMuscle} 
                            className="w-full py-3 rounded-xl border border-dashed border-orange-500/30 text-orange-500/70 hover:text-orange-400 hover:border-orange-500/50 bg-transparent transition-all text-xs font-bold flex items-center justify-center gap-2"
                        >
                            <PlusIcon size={14}/> Agregar Músculo
                        </button>
                    </div>
                </div>

                {error && <p className="text-red-400/90 text-center text-xs font-bold bg-[#0a0a0a] border border-orange-500/20 p-3 rounded-xl">{error}</p>}
                
            </div>

            {/* Footer Fijo (Siempre Visible) */}
            <div className="p-5 border-t border-orange-500/20 bg-[#0a0a0a] flex gap-3 shrink-0">
                <Button onClick={onClose} variant="secondary" className="flex-1 !py-3 uppercase font-bold text-xs bg-[#0a0a0a] border-orange-500/20 text-orange-500/80 hover:text-orange-400 hover:border-orange-500/40">
                    Cancelar
                </Button>
                <Button onClick={handleSaveClick} className="flex-[2] !py-3 uppercase font-black text-xs bg-orange-500 text-black hover:bg-orange-400 border-orange-500">
                    <SaveIcon size={16} className="mr-2"/> Guardar Ejercicio
                </Button>
            </div>
            
        </div>
    </div>,
    document.body
  );
};

export default CustomExerciseEditorModal;