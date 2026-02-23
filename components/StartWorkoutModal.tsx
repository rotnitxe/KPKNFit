
// components/StartWorkoutModal.tsx
import React, { useEffect, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { Program, Session, ProgramWeek } from '../types';
import { TacticalModal } from './ui/TacticalOverlays';
import { ChevronRightIcon, PlayIcon, DumbbellIcon } from './icons';
import Button from './ui/Button';

interface StartWorkoutModalProps {
    isOpen: boolean;
    onClose: () => void;
}

const getExerciseCount = (session: Session) => {
    if (session.parts && session.parts.length > 0) {
        return session.parts.reduce((acc, p) => acc + (p.exercises?.length || 0), 0);
    }
    return session.exercises?.length || 0;
};

const StartWorkoutModal: React.FC<StartWorkoutModalProps> = ({ isOpen, onClose }) => {
    const { programs } = useAppState();
    const { handleStartWorkout, navigateTo } = useAppDispatch();
    const [sessionToStart, setSessionToStart] = useState<{ session: Session; program: Program; weekVariant?: ProgramWeek['variant']; location?: { macroIndex: number, mesoIndex: number, weekId: string } } | null>(null);

    useEffect(() => {
        if (isOpen && (window as any)._sessionToStart) {
            setSessionToStart((window as any)._sessionToStart);
            delete (window as any)._sessionToStart;
        } else if (!isOpen) {
            setSessionToStart(null);
        }
    }, [isOpen]);

    const handleSessionClick = (session: Session, program: Program, weekVariant?: ProgramWeek['variant']) => {
        const hasVariants = session.sessionB || session.sessionC || session.sessionD;
        let location;
        for (let macroIndex = 0; macroIndex < program.macrocycles.length; macroIndex++) {
            const macro = program.macrocycles[macroIndex];
            let mesoCount = 0;
            for (const block of (macro.blocks || [])) {
                for (let mesoIndex = 0; mesoIndex < block.mesocycles.length; mesoIndex++) {
                    const meso = block.mesocycles[mesoIndex];
                    for (const week of meso.weeks) {
                        if (week.sessions.some(s => s.id === session.id)) {
                            location = { macroIndex, mesoIndex: mesoCount + mesoIndex, weekId: week.id };
                            break;
                        }
                    }
                    if (location) break;
                }
                if (location) break;
                mesoCount += block.mesocycles.length;
            }
            if (location) break;
        }

        if (hasVariants) {
            setSessionToStart({ session, program, weekVariant, location });
        } else {
            handleStartWorkout(session, program, weekVariant, location);
            onClose();
        }
    };

    const handleVariantClick = (variant: 'A' | 'B' | 'C' | 'D') => {
        if (sessionToStart) {
            handleStartWorkout(sessionToStart.session, sessionToStart.program, variant, sessionToStart.location);
            onClose();
        }
    };
    
    const renderVariantSelector = () => {
        if (!sessionToStart) return null;
        const variants = ['A'];
        if (sessionToStart.session.sessionB) variants.push('B');
        if (sessionToStart.session.sessionC) variants.push('C');
        if (sessionToStart.session.sessionD) variants.push('D');

        return (
            <div className="space-y-3 animate-fade-in p-2">
                <h3 className="text-lg font-bold text-center mb-4">Selecciona variante para<br/> <span className="text-primary-color font-black">"{sessionToStart.session.name}"</span></h3>
                {variants.map(v => (
                    <Button key={v} onClick={() => handleVariantClick(v as any)} className="w-full !py-4 !text-base font-black uppercase tracking-widest">
                        Realizar Sesión {v}
                    </Button>
                ))}
            </div>
        )
    };
    
    const renderProgramList = () => (
         <div className="space-y-4 max-h-[70vh] overflow-y-auto p-1 custom-scrollbar">
            {programs.length === 0 && (
                <div className="text-center py-12">
                    <DumbbellIcon size={48} className="mx-auto text-slate-700 mb-4" />
                    <p className="text-slate-400 font-bold uppercase tracking-widest text-sm">No hay programas activos</p>
                    <Button onClick={() => { navigateTo('program-editor'); onClose(); }} className="mt-6">Crear Nuevo Programa</Button>
                </div>
            )}
            {programs.map(program => (
                <details key={program.id} className="glass-card !p-0 overflow-hidden" open={programs.length === 1}>
                    <summary className="p-4 cursor-pointer list-none flex justify-between items-center bg-white/[0.03]">
                        <h3 className="font-black text-white text-md uppercase tracking-tight">{program.name}</h3>
                        <ChevronRightIcon className="details-arrow transition-transform text-slate-500" />
                    </summary>
                    <div className="border-t border-white/5 p-2 space-y-2">
                        {program.macrocycles.flatMap(macro => 
                            (macro.blocks || []).flatMap(block =>
                                block.mesocycles.flatMap(meso => 
                                    meso.weeks.map(week => (
                                        <div key={week.id} className="mb-4 last:mb-0">
                                            <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest px-2 mb-2">{block.name} • {meso.name} • {week.name}</h4>
                                            <div className="space-y-1">
                                                {week.sessions.map(session => (
                                                    <div key={session.id} onClick={() => handleSessionClick(session, program, week.variant)} className="flex justify-between items-center p-3 hover:bg-white/5 bg-black/20 rounded-xl cursor-pointer transition-colors border border-transparent hover:border-white/5 group">
                                                        <div className="min-w-0">
                                                            <p className="text-slate-200 font-bold text-sm truncate group-hover:text-white">{session.name}</p>
                                                            <p className="text-[10px] text-slate-500 font-bold uppercase tracking-tighter">{getExerciseCount(session)} ejercicios</p>
                                                        </div>
                                                        <div className="p-2 text-primary-color opacity-50 group-hover:opacity-100 transition-opacity">
                                                            <PlayIcon size={20}/>
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    ))
                                )
                            )
                        )}
                    </div>
                </details>
            ))}
        </div>
    );
    
    return (
        <TacticalModal isOpen={isOpen} onClose={onClose} title="Entrenar Igual">
            {sessionToStart ? renderVariantSelector() : renderProgramList()}
        </TacticalModal>
    );
};

export default StartWorkoutModal;
