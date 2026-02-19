// components/WorkoutEditorModal.tsx
import React, { useState } from 'react';
import { Session } from '../types';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { XIcon, SaveIcon, PlusIcon } from './icons';
import Button from './ui/Button';
import { SessionEditor } from './SessionEditor';
import { hapticImpact, ImpactStyle } from '../services/hapticsService';


interface WorkoutEditorModalProps {
    isOpen: boolean;
    onClose: () => void;
    sessionInfo: { session: Session; programId: string; macroIndex: number; mesoIndex: number; weekId: string; } | null;
    onSave: (session: Session) => void;
}

const WorkoutEditorModal: React.FC<WorkoutEditorModalProps> = ({ isOpen, onClose, sessionInfo, onSave }) => {
    const { isOnline, settings, exerciseList } = useAppState();
    const { handleUpdateSessionInProgram, addToast } = useAppDispatch();
    
    const [saveTrigger, setSaveTrigger] = useState(0);
    const [addExerciseTrigger, setAddExerciseTrigger] = useState(0);
    const [isSavePromptOpen, setIsSavePromptOpen] = useState(false);
    const [pendingSession, setPendingSession] = useState<Session | null>(null);
    
    if (!isOpen) return null;
    if (!sessionInfo) return null; 

    const handleInitialSave = (session: Session) => {
        setPendingSession(session);
        setIsSavePromptOpen(true);
    };

    const confirmSave = (mode: 'temp' | 'permanent') => {
        if (!pendingSession) return;

        hapticImpact(ImpactStyle.Medium as any);

        // 1. Update current ongoing workout state
        onSave(pendingSession);

        // 2. If permanent, update the program structure too
        if (mode === 'permanent') {
            handleUpdateSessionInProgram(
                pendingSession, 
                sessionInfo.programId, 
                sessionInfo.macroIndex, 
                sessionInfo.mesoIndex, 
                sessionInfo.weekId
            );
            addToast("Sesión actualizada en el programa.", "success");
        } else {
             addToast("Cambios aplicados solo a esta sesión.", "success");
        }

        setIsSavePromptOpen(false);
        setPendingSession(null);
        onClose();
    };
    
    return (
        <div className="fixed inset-0 z-[100] flex items-center justify-center">
            {/* Backdrop */}
            <div className="absolute inset-0 bg-black/90 backdrop-blur-md" onClick={onClose} />
            
            {/* Modal Content */}
            <div className="relative w-full h-full md:h-[90vh] md:w-[90vw] md:max-w-4xl bg-slate-950 flex flex-col md:rounded-2xl shadow-2xl overflow-hidden animate-modal-enter">
                <header className="flex items-center justify-between p-4 bg-slate-900 border-b border-slate-800 shrink-0 z-10">
                    <h2 className="text-xl font-bold text-white">Modificar Sesión Activa</h2>
                    <button onClick={onClose} className="p-2 rounded-full hover:bg-slate-800 text-slate-400 hover:text-white transition-colors">
                        <XIcon size={24}/>
                    </button>
                </header>

                <div className="flex-grow overflow-y-auto px-4 pb-24 pt-4 custom-scrollbar">
                    <SessionEditor
                        onSave={handleInitialSave}
                        onCancel={onClose}
                        existingSessionInfo={{...sessionInfo, sessionId: sessionInfo.session.id}}
                        isOnline={isOnline}
                        settings={settings}
                        saveTrigger={saveTrigger}
                        addExerciseTrigger={addExerciseTrigger}
                        exerciseList={exerciseList}
                    />
                </div>

                <footer className="absolute bottom-0 left-0 right-0 p-4 bg-slate-900/95 backdrop-blur border-t border-slate-800 flex gap-3 z-20">
                    <Button onClick={() => setAddExerciseTrigger(c => c + 1)} variant="secondary" className="flex-1 !py-3 !text-sm">
                        <PlusIcon size={18} className="mr-1"/> Ejercicio
                    </Button>
                    <Button onClick={() => setSaveTrigger(c => c + 1)} className="flex-1 !py-3 !text-sm">
                        <SaveIcon size={18} className="mr-1"/> Guardar
                    </Button>
                </footer>
            </div>

            {/* Save Confirmation Dialog (Nested Modal) */}
            {isSavePromptOpen && (
                <div className="fixed inset-0 z-[110] flex items-center justify-center p-6 bg-black/80 backdrop-blur-sm animate-fade-in">
                    <div className="bg-slate-900 border border-slate-700 rounded-2xl p-6 w-full max-w-sm shadow-2xl transform scale-100 animate-modal-enter">
                        <h3 className="text-xl font-bold text-white mb-2">Guardar Cambios</h3>
                        <p className="text-slate-300 text-sm mb-6 leading-relaxed">
                            ¿Quieres aplicar estos cambios solo para hoy, o actualizar el programa original permanentemente?
                        </p>
                        <div className="space-y-3">
                            <button 
                                onClick={() => confirmSave('temp')}
                                className="w-full py-3.5 px-4 rounded-xl bg-slate-800 text-white font-semibold hover:bg-slate-700 border border-slate-600 transition-colors"
                            >
                                Solo por hoy
                            </button>
                            <button 
                                onClick={() => confirmSave('permanent')}
                                className="w-full py-3.5 px-4 rounded-xl bg-primary-color text-white font-bold hover:brightness-110 shadow-lg shadow-primary-color/20 transition-all"
                            >
                                Actualizar Programa (Permanente)
                            </button>
                             <button 
                                onClick={() => setIsSavePromptOpen(false)}
                                className="w-full py-2 text-slate-500 hover:text-slate-300 text-sm mt-2 font-medium"
                            >
                                Cancelar
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default WorkoutEditorModal;
