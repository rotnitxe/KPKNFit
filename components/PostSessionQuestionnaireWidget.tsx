
// components/PostSessionQuestionnaireWidget.tsx
import React, { useState, useMemo } from 'react';
import { PendingQuestionnaire, PostSessionFeedback, ExerciseMuscleInfo } from '../types';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { CheckCircleIcon, SparklesIcon, DumbbellIcon, BrainIcon, PlusIcon, SearchIcon } from './icons';
import { MUSCLE_GROUPS } from '../data/exerciseList';

interface PostSessionQuestionnaireWidgetProps {
    isOpen: boolean;
    onClose: () => void;
    questionnaire: PendingQuestionnaire;
}

const DOMS_LABELS = ["Ninguno", "Leve", "Moderado", "Fuerte", "Extremo"];

export const PostSessionQuestionnaireWidget: React.FC<PostSessionQuestionnaireWidgetProps> = ({ isOpen, onClose, questionnaire }) => {
    const { handleSavePostSessionFeedback } = useAppDispatch();
    const { history, exerciseList } = useAppState();
    const [isSubmitted, setIsSubmitted] = useState(false);
    const [showMuscleSearch, setShowMuscleSearch] = useState(false);
    const [cnsLevel, setCnsLevel] = useState(8);

    const initialMuscles = useMemo(() => {
        const log = history.find(h => h.id === questionnaire.logId);
        if (!log) return ["General"];
        const found = new Set<string>();
        log.completedExercises.forEach(ex => {
            const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.exerciseName);
            info?.involvedMuscles.forEach(m => {
                if (m.role === 'primary') found.add(m.muscle);
            });
        });
        return Array.from(found);
    }, [history, questionnaire.logId, exerciseList]);

    const [extraMuscles, setExtraMuscles] = useState<string[]>([]);
    const allEvalMuscles = [...initialMuscles, ...extraMuscles];

    const [feedback, setFeedback] = useState<Record<string, any>>(() => {
        const state: any = {};
        initialMuscles.forEach(m => state[m] = { doms: 1, jointPain: false, strengthCapacity: 8, notes: '' });
        return state;
    });

    const handleAddExtraMuscle = (m: string) => {
        if (!allEvalMuscles.includes(m)) {
            setExtraMuscles(prev => [...prev, m]);
            setFeedback(prev => ({ ...prev, [m]: { doms: 1, jointPain: false, strengthCapacity: 8, notes: '' } }));
        }
        setShowMuscleSearch(false);
    };

    const handleSubmit = () => {
        handleSavePostSessionFeedback({
            logId: questionnaire.logId,
            date: new Date().toISOString(),
            cnsRecovery: cnsLevel,
            feedback
        });
        setIsSubmitted(true);
        setTimeout(onClose, 1500);
    };

    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Estado Post-Entrenamiento">
            <div className="space-y-6 p-2 max-h-[80vh] overflow-y-auto custom-scrollbar">
                {/* SNC EVALUATION */}
                <div className="bg-gradient-to-br from-purple-900/40 to-slate-900 p-4 rounded-2xl border border-purple-500/30">
                    <h3 className="text-sm font-black text-purple-300 uppercase tracking-widest mb-4 flex items-center gap-2">
                        <BrainIcon size={18}/> Estado del SNC (Ganas/Energía)
                    </h3>
                    <input type="range" min="1" max="10" value={cnsLevel} onChange={e => setCnsLevel(parseInt(e.target.value))} className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-purple-500" />
                    <div className="flex justify-between text-[10px] font-bold text-slate-500 mt-2 uppercase">
                        <span>Agotado</span>
                        <span className="text-white text-lg font-black">{cnsLevel}</span>
                        <span>Listo para todo</span>
                    </div>
                </div>

                {/* MUSCLE EVALUATION */}
                {allEvalMuscles.map(m => (
                    <div key={m} className="bg-slate-900/50 p-4 rounded-2xl border border-white/5 space-y-4">
                        <div className="flex justify-between items-center">
                            <h4 className="font-bold text-white flex items-center gap-2"><DumbbellIcon size={14} className="text-primary-color"/> {m}</h4>
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="text-[10px] font-black text-slate-500 uppercase block mb-1">Agujetas</label>
                                <select value={feedback[m]?.doms} onChange={e => setFeedback({...feedback, [m]: {...feedback[m], doms: parseInt(e.target.value)}})} className="w-full bg-slate-800 border-none rounded-lg text-xs font-bold text-white">
                                    {DOMS_LABELS.map((l, i) => <option key={i} value={i+1}>{l}</option>)}
                                </select>
                            </div>
                            <div>
                                <label className="text-[10px] font-black text-slate-500 uppercase block mb-1">Fuerza Percibida</label>
                                <input type="number" value={feedback[m]?.strengthCapacity} onChange={e => setFeedback({...feedback, [m]: {...feedback[m], strengthCapacity: parseInt(e.target.value)}})} className="w-full bg-slate-800 border-none rounded-lg text-xs font-bold text-white text-center" />
                            </div>
                        </div>
                    </div>
                ))}

                {/* ADD EXTRA MUSCLE */}
                {showMuscleSearch ? (
                    <div className="animate-fade-in bg-slate-800 p-3 rounded-xl border border-white/10">
                        <div className="flex items-center gap-2 mb-3 bg-black/20 p-2 rounded-lg">
                            <SearchIcon size={14} className="text-slate-500"/>
                            <input type="text" placeholder="Buscar músculo..." className="bg-transparent border-none text-xs w-full focus:ring-0" autoFocus />
                        </div>
                        <div className="flex flex-wrap gap-1 max-h-32 overflow-y-auto">
                            {MUSCLE_GROUPS.filter(m => m !== 'Todos').map(m => (
                                <button key={m} onClick={() => handleAddExtraMuscle(m)} className="px-2 py-1 bg-white/5 hover:bg-primary-color text-[10px] font-bold rounded transition-colors">{m}</button>
                            ))}
                        </div>
                    </div>
                ) : (
                    <button onClick={() => setShowMuscleSearch(true)} className="w-full py-3 border-2 border-dashed border-white/5 rounded-2xl text-slate-500 text-xs font-bold hover:text-white hover:border-white/10 transition-all flex items-center justify-center gap-2">
                        <PlusIcon size={14}/> Evaluar otro músculo específico
                    </button>
                )}

                <Button onClick={handleSubmit} className="w-full !py-4 shadow-xl">Guardar Evaluación</Button>
            </div>
        </Modal>
    );
};
