
// components/PeriodizationEditor.tsx
import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Program, Macrocycle, Mesocycle, ProgramWeek, Session, SessionBackground, Block } from '../types';
import Button from './ui/Button';
import { generateImage } from '../services/aiService';
import { SparklesIcon, UploadIcon, ImageIcon, PlusIcon, TrashIcon, ChevronRightIcon, Wand2Icon, PencilIcon, TargetIcon, CheckCircleIcon, RefreshCwIcon, GridIcon, LayersIcon, ClockIcon, TrophyIcon, ArrowUpIcon, ArrowDownIcon, XIcon, ChevronDownIcon, BellIcon } from './icons';
import { storageService } from '../services/storageService';
import BackgroundEditorModal from './SessionBackgroundModal';
import { useAppContext } from '../contexts/AppContext';
import PeriodizationTemplateModal from './PeriodizationTemplateModal';
import Card from './ui/Card';
import ToggleSwitch from './ui/ToggleSwitch';

// --- SHARED UTILS ---

const isProgramComplex = (p: Program | null): boolean => {
    if (!p) return false;
    if (p.structure === 'complex') return true;
    if (p.structure === 'simple') return false;
    if (p.macrocycles.length > 1) return true;
    const macro = p.macrocycles[0];
    if (!macro) return false;
    if ((macro.blocks || []).length > 1) return true;
    const block = (macro.blocks || [])[0];
    if (!block) return false;
    if (block.mesocycles.length > 1) return true;
    return false;
};

// --- DATA ---
const goalOptions: (Mesocycle['goal'])[] = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'];

// --- MAIN COMPONENT ---

interface PeriodizationEditorProps {
  onSave: (program: Program) => void;
  onCancel: () => void;
  existingProgram: Program | null;
  isOnline: boolean;
  saveTrigger: number;
  onStartProgram?: (programId: string) => void;
}

const PROGRAM_DRAFT_KEY = 'program-editor-draft';

const PeriodizationEditor: React.FC<PeriodizationEditorProps> = ({ onSave, onCancel, existingProgram, isOnline, saveTrigger, onStartProgram }) => {
  const { settings, setIsDirty, isDirty: isAppContextDirty, addToast, navigateTo } = useAppContext();
  
  // -- State for Editor --
  const [program, setProgram] = useState<Program | null>(null);
  const [isComplex, setIsComplex] = useState(false);
  const [activeTab, setActiveTab] = useState<'details' | 'structure' | 'goals'>('details');
  const [isBgModalOpen, setIsBgModalOpen] = useState(false);
  const [isTemplateModalOpen, setIsTemplateModalOpen] = useState(false);
  
  // -- New State for Goals Tab --
  const [newGoalExercise, setNewGoalExercise] = useState('');
  const [newGoalWeight, setNewGoalWeight] = useState('');
  
  const prevSaveTriggerRef = useRef(saveTrigger);

  // Initialize
  useEffect(() => {
    const initializeEditor = async () => {
        if (existingProgram) {
            let initialData = JSON.parse(JSON.stringify(existingProgram));
            let complexMode = isProgramComplex(existingProgram);
            
            setIsDirty(false);
            const draft = await storageService.get<{ programData: Program; associatedId: string | null }>(PROGRAM_DRAFT_KEY);
            if (draft) {
                const draftAssociatedId = draft.associatedId;
                const currentId = existingProgram?.id || null;
                if (draftAssociatedId === currentId) {
                    if (window.confirm('Se encontró un borrador no guardado. ¿Restaurar?')) {
                        initialData = draft.programData;
                        complexMode = isProgramComplex(initialData);
                        setIsDirty(true);
                    } else {
                        await storageService.remove(PROGRAM_DRAFT_KEY);
                    }
                }
            }
            setProgram(initialData);
            setIsComplex(complexMode);
        }
    };
    initializeEditor();
  }, [existingProgram, setIsDirty]);

  // Draft saving
  useEffect(() => {
    if (isAppContextDirty && program && existingProgram) {
        storageService.set(PROGRAM_DRAFT_KEY, {
            programData: program,
            associatedId: existingProgram?.id || null,
        });
    }
  }, [program, isAppContextDirty, existingProgram]);

  // Save Trigger
  const handleSave = useCallback(async () => {
      if (program && program.name && program.name.trim()) {
          onSave(program);
          await storageService.remove(PROGRAM_DRAFT_KEY);
          setIsDirty(false);
      }
  }, [onSave, program, setIsDirty]);

  useEffect(() => {
      if (saveTrigger > prevSaveTriggerRef.current) {
          handleSave();
      }
      prevSaveTriggerRef.current = saveTrigger;
  }, [saveTrigger, handleSave]);

  // --- Handlers ---
  const handleProgramInfoChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    if (!program) return;
    setProgram({ ...program, [e.target.name]: e.target.value });
    setIsDirty(true);
  };
  
  const handleProgramFieldChange = <K extends keyof Program>(field: K, value: Program[K]) => {
    if (!program) return;
    setProgram(prev => prev ? ({ ...prev, [field]: value }) : null);
    setIsDirty(true);
  };
  
  const handleSaveBackground = (bg?: SessionBackground) => {
    if (!program) return;
    setProgram(p => p ? ({ ...p, background: bg, coverImage: (bg?.type === 'image' ? bg.value : '') }) : null);
    setIsDirty(true);
  };

  const updateProgramStructure = (updatedMacrocycles: Macrocycle[]) => {
    if (!program) return;
    setProgram(prev => prev ? ({ ...prev, macrocycles: updatedMacrocycles }) : null);
    setIsDirty(true);
  };

  const handleSelectTemplate = (data: { macros: Macrocycle[], mode?: 'powerlifting' }) => {
      setProgram(prev => prev ? ({ ...prev, macrocycles: data.macros, mode: data.mode || prev.mode, structure: 'complex' }) : null);
      setIsComplex(true);
      setIsTemplateModalOpen(false);
      setIsDirty(true);
  };

  // --- Structure Logic ---
  const handleConvertToComplex = () => {
      if (!program) return;
      if (window.confirm('¿Estás seguro? Convertir a estructura por bloques reorganizará tu programa. Esta acción es avanzada.')) {
           // Ensure basic structure exists if missing
           let newMacros = [...program.macrocycles];
           if (newMacros.length === 0) {
                newMacros.push({ id: crypto.randomUUID(), name: 'Macrociclo 1', blocks: [] });
           }
           
           // Ensure first macro has blocks
           if (!newMacros[0].blocks || newMacros[0].blocks.length === 0) {
               newMacros[0].blocks = [{ id: crypto.randomUUID(), name: 'Bloque 1', mesocycles: [] }];
           }
           
           // If simple mode used flat structure, this assumes it is already somewhat nested or will be moved correctly by deeper logic if implemented.
           // For now, we simply flag it as complex and ensure containers exist.
           
           setProgram({ ...program, structure: 'complex', macrocycles: newMacros });
           setIsComplex(true);
           setIsDirty(true);
      }
  }
  
    // Structure Manipulation Handlers
  const handleAddMacro = () => { if (!program) return; const newMacro: Macrocycle = { id: crypto.randomUUID(), name: `Macrociclo ${program.macrocycles.length + 1}`, blocks: [] }; updateProgramStructure([...program.macrocycles, newMacro]); };
  const handleRemoveMacro = (macroIndex: number) => { if (window.confirm('¿Eliminar macrociclo?')) { updateProgramStructure(program!.macrocycles.filter((_, i) => i !== macroIndex)); } };
  const handleMacroChange = (macroIndex: number, newName: string) => { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); updated[macroIndex].name = newName; updateProgramStructure(updated); };
  const handleAddBlock = (macroIndex: number) => { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); const newBlock: Block = { id: crypto.randomUUID(), name: `Bloque ${updated[macroIndex].blocks.length + 1}`, mesocycles: [] }; updated[macroIndex].blocks.push(newBlock); updateProgramStructure(updated); };
  const handleRemoveBlock = (macroIndex: number, blockIndex: number) => { if (window.confirm('¿Eliminar bloque?')) { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); updated[macroIndex].blocks.splice(blockIndex, 1); updateProgramStructure(updated); } };
  const handleBlockChange = (macroIndex: number, blockIndex: number, newName: string) => { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); updated[macroIndex].blocks[blockIndex].name = newName; updateProgramStructure(updated); };
  const handleAddMeso = (macroIndex: number, blockIndex: number) => { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); const newMeso: Mesocycle = { id: crypto.randomUUID(), name: `Meso ${updated[macroIndex].blocks[blockIndex].mesocycles.length + 1}`, goal: 'Acumulación', weeks: [] }; updated[macroIndex].blocks[blockIndex].mesocycles.push(newMeso); updateProgramStructure(updated); };
  const handleRemoveMeso = (macroIndex: number, blockIndex: number, mesoIndex: number) => { if (window.confirm('¿Eliminar mesociclo?')) { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); updated[macroIndex].blocks[blockIndex].mesocycles.splice(mesoIndex, 1); updateProgramStructure(updated); } };
  const handleMesoChange = (macroIndex: number, blockIndex: number, mesoIndex: number, field: keyof Omit<Mesocycle, 'id'|'weeks'>, value: any) => { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); (updated[macroIndex].blocks[blockIndex].mesocycles[mesoIndex] as any)[field] = value; if (field === 'goal' && value !== 'Custom') { updated[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].customGoal = ''; } updateProgramStructure(updated); };
  const handleAddWeek = (macroIndex: number, blockIndex: number, mesoIndex: number) => { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); const newWeek: ProgramWeek = { id: crypto.randomUUID(), name: `Semana ${updated[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks.length + 1}`, sessions: [] }; updated[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks.push(newWeek); updateProgramStructure(updated); };
  const handleRemoveWeek = (macroIndex: number, blockIndex: number, mesoIndex: number, weekIndex: number) => { if (window.confirm('¿Eliminar semana?')) { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); updated[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks.splice(weekIndex, 1); updateProgramStructure(updated); } };
  const handleWeekChange = (macroIndex: number, blockIndex: number, mesoIndex: number, weekIndex: number, newName: string) => { const updated = JSON.parse(JSON.stringify(program!.macrocycles)); updated[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks[weekIndex].name = newName; updateProgramStructure(updated); };


  // --- Goals Logic ---
  const availableExercises = useMemo(() => {
      if (!program) return [];
      const exercises = new Set<string>();
      program.macrocycles.forEach(m => 
        (m.blocks || []).forEach(b => 
            b.mesocycles.forEach(meso => 
                meso.weeks.forEach(w => 
                    w.sessions.forEach(s => 
                        s.exercises.forEach(ex => exercises.add(ex.name))
                    )
                )
            )
        )
      );
      return Array.from(exercises).sort();
  }, [program]);

  const handleAddGoal = () => {
      if (!program || !newGoalExercise || !newGoalWeight) return;
      const weight = parseFloat(newGoalWeight);
      if (isNaN(weight) || weight <= 0) {
          addToast("Peso inválido", "danger");
          return;
      }
      
      const updatedGoals = { ...(program.exerciseGoals || {}), [newGoalExercise]: weight };
      setProgram({ ...program, exerciseGoals: updatedGoals });
      setNewGoalExercise('');
      setNewGoalWeight('');
      setIsDirty(true);
  };

  const handleRemoveGoal = (exerciseName: string) => {
      if (!program || !program.exerciseGoals) return;
      const updatedGoals = { ...program.exerciseGoals };
      delete updatedGoals[exerciseName];
      setProgram({ ...program, exerciseGoals: updatedGoals });
      setIsDirty(true);
  };

  // --- Render Helpers ---
  const simpleWeeks = program?.macrocycles?.[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks || [];

  if (!program) return <div className="p-8 text-center text-white">Cargando...</div>;

  return (
    <div className="fixed inset-0 bg-black z-50 flex flex-col font-sans text-white overflow-hidden animate-fade-in">
       <PeriodizationTemplateModal isOpen={isTemplateModalOpen} onClose={() => setIsTemplateModalOpen(false)} onSelect={handleSelectTemplate} />
       {isBgModalOpen && <BackgroundEditorModal isOpen={isBgModalOpen} onClose={() => setIsBgModalOpen(false)} onSave={handleSaveBackground} initialBackground={program.background} previewTitle={program.name || "Nuevo Programa"} isOnline={isOnline} />}
        
        {/* HEADER */}
        <div className="px-6 pt-12 pb-4 bg-black border-b border-white/10 shrink-0 z-20">
             <div className="flex justify-between items-start mb-6">
                <button onClick={onCancel} className="text-[10px] font-black uppercase tracking-widest text-gray-500 hover:text-white transition-colors">Cancelar</button>
                <button onClick={handleSave} className="bg-white text-black px-6 py-2 rounded-full text-[10px] font-black uppercase tracking-widest hover:scale-105 transition-transform">Guardar Cambios</button>
            </div>
            
            <input 
                type="text" 
                name="name" 
                value={program.name} 
                onChange={handleProgramInfoChange} 
                className="w-full bg-transparent border-none p-0 text-3xl font-black text-white uppercase tracking-tighter placeholder-gray-800 focus:ring-0" 
                placeholder="NOMBRE DEL PROGRAMA" 
            />
            
            <div className="flex gap-6 mt-6 border-b border-white/10">
                <button onClick={() => setActiveTab('details')} className={`text-[10px] font-black uppercase tracking-widest pb-3 border-b-2 transition-all ${activeTab === 'details' ? 'border-white text-white' : 'border-transparent text-gray-600 hover:text-gray-400'}`}>Detalles</button>
                <button onClick={() => setActiveTab('structure')} className={`text-[10px] font-black uppercase tracking-widest pb-3 border-b-2 transition-all ${activeTab === 'structure' ? 'border-white text-white' : 'border-transparent text-gray-600 hover:text-gray-400'}`}>Estructura</button>
                <button onClick={() => setActiveTab('goals')} className={`text-[10px] font-black uppercase tracking-widest pb-3 border-b-2 transition-all ${activeTab === 'goals' ? 'border-white text-white' : 'border-transparent text-gray-600 hover:text-gray-400'}`}>Metas y Progreso</button>
            </div>
        </div>

        {/* CONTENT SCROLL AREA */}
        <div className="flex-1 overflow-y-auto custom-scrollbar p-6">
            <div className="max-w-2xl mx-auto space-y-8 pb-20">

                {activeTab === 'details' && (
                    <div className="space-y-6 animate-fade-in">
                        <div className="space-y-2">
                             <label className="text-[9px] font-black text-gray-500 uppercase tracking-widest">Descripción</label>
                             <textarea 
                                name="description" 
                                value={program.description} 
                                onChange={handleProgramInfoChange} 
                                rows={3} 
                                className="w-full bg-[#111] border border-white/10 rounded-xl p-4 text-sm text-gray-300 focus:border-white/30 focus:ring-0 outline-none transition-all placeholder:text-gray-800" 
                                placeholder="Describe el objetivo del programa..." 
                            />
                        </div>
                        
                         <div className="grid grid-cols-2 gap-4">
                             <div className="bg-[#111] p-4 rounded-2xl border border-white/10">
                                 <label className="block text-[9px] font-black text-gray-500 uppercase tracking-widest mb-2">Modo</label>
                                 <div className="flex gap-1 bg-black p-1 rounded-lg border border-white/5">
                                     <button onClick={() => handleProgramFieldChange('mode', 'hypertrophy')} className={`flex-1 py-2 rounded-md text-[9px] font-black uppercase transition-all ${program.mode === 'hypertrophy' ? 'bg-white text-black' : 'text-gray-500'}`}>H</button>
                                     <button onClick={() => handleProgramFieldChange('mode', 'powerlifting')} className={`flex-1 py-2 rounded-md text-[9px] font-black uppercase transition-all ${program.mode === 'powerlifting' ? 'bg-white text-black' : 'text-gray-500'}`}>P</button>
                                 </div>
                             </div>
                             
                             <div className="bg-[#111] p-4 rounded-2xl border border-white/10 flex flex-col justify-between">
                                 <label className="block text-[9px] font-black text-gray-500 uppercase tracking-widest">Coach IA</label>
                                 <div className="flex items-center justify-between mt-2">
                                     <span className="text-xs font-bold text-white">Carpe Diem</span>
                                     <ToggleSwitch checked={program.carpeDiemEnabled || false} onChange={(c) => handleProgramFieldChange('carpeDiemEnabled', c)} size='sm' />
                                 </div>
                             </div>
                        </div>

                        {/* COVER IMAGE */}
                        <div 
                            onClick={() => setIsBgModalOpen(true)}
                            className="aspect-video w-full bg-[#111] rounded-2xl border border-white/10 flex items-center justify-center cursor-pointer group relative overflow-hidden"
                        >
                            {program.coverImage ? (
                                <>
                                    <img src={program.coverImage} alt="Cover" className="w-full h-full object-cover opacity-60 group-hover:opacity-80 transition-opacity" />
                                    <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity bg-black/40">
                                        <p className="text-[10px] font-black uppercase tracking-widest text-white border border-white px-3 py-1 rounded-full">Cambiar</p>
                                    </div>
                                </>
                            ) : (
                                <div className="flex flex-col items-center gap-2 text-gray-600 group-hover:text-white transition-colors">
                                    <ImageIcon size={32}/>
                                    <p className="text-[9px] font-black uppercase tracking-widest">Añadir Portada</p>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {activeTab === 'structure' && (
                    <div className="space-y-6 animate-fade-in">
                        {isComplex ? (
                            <div className="space-y-6">
                                <Button type="button" onClick={() => setIsTemplateModalOpen(true)} variant="secondary" className="!bg-[#111] border-white/10 text-gray-400 hover:text-white hover:border-white/30 w-full !py-4 !text-[10px] uppercase font-black tracking-widest">
                                    <LayersIcon size={14} className="mr-2"/> Cargar Plantilla
                                </Button>
                                
                                <div className="space-y-8">
                                    {(program.macrocycles || []).map((macro, macroIndex) => (
                                        <div key={macro.id} className="relative pl-4 border-l border-white/10">
                                            <div className="flex justify-between items-center mb-4">
                                                <input value={macro.name} onChange={e => handleMacroChange(macroIndex, e.target.value)} className="bg-transparent text-xl font-black text-white uppercase tracking-tight focus:outline-none w-full" />
                                                <button onClick={() => handleRemoveMacro(macroIndex)} className="text-gray-600 hover:text-red-500"><TrashIcon size={16}/></button>
                                            </div>
                                            
                                            <div className="space-y-6">
                                                {(macro.blocks || []).map((block, blockIndex) => (
                                                    <div key={block.id} className="bg-[#111] rounded-2xl border border-white/5 overflow-hidden">
                                                        <div className="px-4 py-3 border-b border-white/5 flex justify-between items-center bg-white/[0.02]">
                                                            <input value={block.name} onChange={e => handleBlockChange(macroIndex, blockIndex, e.target.value)} className="bg-transparent text-sm font-bold text-gray-200 uppercase tracking-wide focus:outline-none w-full" />
                                                            <button onClick={() => handleRemoveBlock(macroIndex, blockIndex)} className="text-gray-600 hover:text-red-500"><TrashIcon size={14}/></button>
                                                        </div>
                                                        
                                                        <div className="p-4 space-y-4">
                                                            {(block.mesocycles || []).map((meso, mesoIndex) => (
                                                                <div key={meso.id} className="space-y-2">
                                                                    <div className="flex items-center gap-2">
                                                                        <div className="w-1.5 h-1.5 rounded-full bg-white/20"></div>
                                                                        <input value={meso.name} onChange={e => handleMesoChange(macroIndex, blockIndex, mesoIndex, 'name', e.target.value)} className="bg-transparent text-xs font-bold text-white focus:outline-none flex-grow" />
                                                                        <select value={meso.goal} onChange={e => handleMesoChange(macroIndex, blockIndex, mesoIndex, 'goal', e.target.value as any)} className="bg-black text-[9px] text-gray-400 border border-white/10 rounded px-2 py-1 uppercase font-bold outline-none">
                                                                            {goalOptions.map(opt => <option key={opt}>{opt}</option>)}
                                                                        </select>
                                                                        <button onClick={() => handleRemoveMeso(macroIndex, blockIndex, mesoIndex)} className="text-gray-700 hover:text-red-500"><TrashIcon size={12}/></button>
                                                                    </div>
                                                                    
                                                                    <div className="pl-4 space-y-1">
                                                                        {(meso.weeks || []).map((week, weekIndex) => (
                                                                            <div key={week.id} className="flex items-center justify-between p-2 rounded-lg hover:bg-white/5 group transition-colors cursor-default">
                                                                                <input value={week.name} onChange={e => handleWeekChange(macroIndex, blockIndex, mesoIndex, weekIndex, e.target.value)} className="bg-transparent text-xs text-gray-400 font-medium focus:text-white focus:outline-none flex-grow" />
                                                                                <span className="text-[9px] font-mono text-gray-700 mr-3">{week.sessions.length} Sesiones</span>
                                                                                <button onClick={() => handleRemoveWeek(macroIndex, blockIndex, mesoIndex, weekIndex)} className="text-gray-700 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity"><TrashIcon size={10}/></button>
                                                                            </div>
                                                                        ))}
                                                                        <button onClick={() => handleAddWeek(macroIndex, blockIndex, mesoIndex)} className="w-full py-2 text-[9px] font-black text-gray-600 hover:text-white uppercase tracking-widest border border-dashed border-white/5 rounded-lg hover:border-white/20 transition-all mt-2">+ Añadir Semana</button>
                                                                    </div>
                                                                </div>
                                                            ))}
                                                            <button onClick={() => handleAddMeso(macroIndex, blockIndex)} className="w-full py-3 bg-black border border-white/10 rounded-xl text-[10px] font-black text-gray-400 hover:text-white hover:border-white/30 uppercase tracking-widest transition-all">+ Nuevo Mesociclo</button>
                                                        </div>
                                                    </div>
                                                ))}
                                                <button onClick={() => handleAddBlock(macroIndex)} className="w-full py-3 border-2 border-white text-white rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-white hover:text-black transition-all">+ Añadir Bloque</button>
                                            </div>
                                        </div>
                                    ))}
                                    
                                    <button onClick={handleAddMacro} className="w-full py-6 border-2 border-dashed border-white/20 rounded-3xl text-gray-500 font-black uppercase tracking-[0.2em] hover:text-white hover:border-white/50 transition-all">
                                        Crear Nuevo Macrociclo
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <div className="space-y-6">
                                <div className="bg-[#111] p-6 rounded-3xl border border-white/10 text-center">
                                    <h3 className="text-xl font-bold text-white mb-2">Estructura Simple</h3>
                                    <p className="text-xs text-gray-400 mb-6 max-w-xs mx-auto">Tus semanas se ejecutan en bucle. Ideal para rutinas lineales.</p>
                                    
                                    <div className="space-y-2 text-left mb-6">
                                        {/* Simple week list */}
                                         {simpleWeeks.map((week, weekIndex) => (
                                             <div key={week.id} className="p-3 border border-white/10 rounded-lg flex justify-between items-center">
                                                 <input value={week.name} onChange={e => handleWeekChange(0, 0, 0, weekIndex, e.target.value)} className="bg-transparent text-sm font-bold text-white focus:outline-none flex-grow" />
                                                 <span className="text-xs text-gray-500">{week.sessions.length} sesiones</span>
                                                 <button onClick={() => handleRemoveWeek(0, 0, 0, weekIndex)} className="text-gray-600 hover:text-red-500"><TrashIcon size={14}/></button>
                                             </div>
                                         ))}
                                    </div>
                                    <Button onClick={() => handleAddWeek(0, 0, 0)} className="w-full !py-3 !text-xs font-black uppercase !bg-white !text-black border-none">Añadir Semana</Button>
                                </div>
                                
                                <div className="border-t border-white/10 pt-6">
                                    <div className="flex items-center justify-between p-4 bg-[#111] border border-white/10 rounded-2xl">
                                        <div>
                                            <p className="text-xs font-bold text-white uppercase">Periodización por Bloques</p>
                                            <p className="text-[10px] text-gray-500 mt-1">Habilitar Macrociclos y Mesociclos</p>
                                        </div>
                                        <button 
                                            onClick={handleConvertToComplex}
                                            className="px-4 py-2 bg-white text-black text-[10px] font-black uppercase rounded-full hover:bg-gray-200 transition-colors"
                                        >
                                            Activar
                                        </button>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                )}
                
                {activeTab === 'goals' && (
                    <div className="space-y-8 animate-fade-in">
                        {/* 1. Alerts Configuration */}
                        <div className="flex items-center justify-between p-5 bg-[#111] rounded-2xl border border-white/10">
                            <div className="flex items-center gap-3">
                                <BellIcon size={20} className="text-white"/>
                                <div>
                                    <p className="text-sm font-bold text-white uppercase">Alertas de Progreso</p>
                                    <p className="text-[10px] text-gray-500 mt-0.5">Notificar al superar 1RM estimado</p>
                                </div>
                            </div>
                            <ToggleSwitch 
                                checked={program.progressAlertsEnabled || false} 
                                onChange={(c) => handleProgramFieldChange('progressAlertsEnabled', c)} 
                                size="sm" 
                            />
                        </div>

                        {/* 2. 1RM Goals List */}
                        <div className="space-y-4">
                            <div className="flex justify-between items-center px-1">
                                <label className="text-[10px] font-black text-gray-500 uppercase tracking-widest">Metas de Carga (1RM)</label>
                            </div>

                            {/* Add New Goal */}
                            <div className="bg-[#111] p-4 rounded-2xl border border-white/10 space-y-3">
                                <div className="grid grid-cols-2 gap-3">
                                    <select 
                                        value={newGoalExercise} 
                                        onChange={(e) => setNewGoalExercise(e.target.value)} 
                                        className="w-full bg-black border border-white/10 rounded-xl px-3 py-2 text-xs font-bold text-white focus:border-white transition-colors outline-none"
                                    >
                                        <option value="">Seleccionar Ejercicio...</option>
                                        {availableExercises.map(ex => (
                                            <option key={ex} value={ex}>{ex}</option>
                                        ))}
                                    </select>
                                    <div className="flex items-center gap-2">
                                        <input 
                                            type="number" 
                                            value={newGoalWeight} 
                                            onChange={(e) => setNewGoalWeight(e.target.value)} 
                                            placeholder="Peso" 
                                            className="w-full bg-black border border-white/10 rounded-xl px-3 py-2 text-xs font-bold text-white focus:border-white transition-colors outline-none text-center"
                                        />
                                        <span className="text-[10px] font-bold text-gray-500">{settings.weightUnit}</span>
                                    </div>
                                </div>
                                <button 
                                    onClick={handleAddGoal} 
                                    disabled={!newGoalExercise || !newGoalWeight}
                                    className="w-full py-2 bg-white text-black text-[10px] font-black uppercase rounded-lg hover:bg-gray-200 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    + Añadir Meta
                                </button>
                            </div>

                            {/* Goals List */}
                            <div className="space-y-2">
                                {program.exerciseGoals && Object.entries(program.exerciseGoals).length > 0 ? (
                                    Object.entries(program.exerciseGoals).map(([exercise, weight]) => (
                                        <div key={exercise} className="flex items-center justify-between p-3 bg-transparent border-b border-white/10 group hover:bg-white/5 transition-colors">
                                            <span className="text-xs font-bold text-white">{exercise}</span>
                                            <div className="flex items-center gap-4">
                                                <span className="text-sm font-mono font-black text-white">{weight}<span className="text-[10px] text-gray-500 ml-1">{settings.weightUnit}</span></span>
                                                <button onClick={() => handleRemoveGoal(exercise)} className="text-gray-600 hover:text-white transition-colors">
                                                    <XIcon size={14}/>
                                                </button>
                                            </div>
                                        </div>
                                    ))
                                ) : (
                                    <div className="text-center py-8 border-2 border-dashed border-white/5 rounded-2xl">
                                        <TargetIcon size={24} className="mx-auto text-gray-700 mb-2"/>
                                        <p className="text-[10px] text-gray-600 font-bold uppercase tracking-widest">Sin metas definidas</p>
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                )}

            </div>
        </div>
    </div>
  );
};

export default PeriodizationEditor;
