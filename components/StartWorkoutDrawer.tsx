// components/StartWorkoutDrawer.tsx - Drawer de inicio de entrenamiento (migración de StartWorkoutModal)

import React, { useEffect, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { Program, Session, ProgramWeek } from '../types';
import { getSessionExerciseCount } from '../utils/calculations';
import WorkoutDrawer from './workout/WorkoutDrawer';
import { ChevronRightIcon, PlayIcon, DumbbellIcon } from './icons';
import Button from './ui/Button';

interface StartWorkoutDrawerProps {
  isOpen: boolean;
  onClose: () => void;
}

const StartWorkoutDrawer: React.FC<StartWorkoutDrawerProps> = ({ isOpen, onClose }) => {
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
      <div className="space-y-3 animate-fade-in p-5">
        <h3 className="text-base font-mono font-black text-center mb-4 uppercase tracking-widest text-orange-500/90">
          Variante para "{sessionToStart.session.name}"
        </h3>
        {variants.map(v => (
          <Button key={v} onClick={() => handleVariantClick(v as any)} className="w-full !py-4 !text-[10px] font-mono font-black uppercase tracking-widest border border-orange-500/30 bg-orange-500/10 text-orange-400 hover:bg-orange-500/20">
            Realizar Sesión {v}
          </Button>
        ))}
      </div>
    );
  };

  const renderProgramList = () => (
    <div className="space-y-4 max-h-[65vh] overflow-y-auto p-5 custom-scrollbar">
      {programs.length === 0 && (
        <div className="text-center py-12">
          <DumbbellIcon size={48} className="mx-auto text-slate-600 mb-4" />
          <p className="text-[10px] font-mono font-black text-slate-500 uppercase tracking-widest">No hay programas activos</p>
          <Button onClick={() => { navigateTo('program-editor'); onClose(); }} className="mt-6 border border-orange-500/30">Crear Nuevo Programa</Button>
        </div>
      )}
      {programs.map(program => (
        <details key={program.id} className="border border-orange-500/20 rounded-xl overflow-hidden bg-[#0d0d0d]" open={programs.length === 1}>
          <summary className="p-4 cursor-pointer list-none flex justify-between items-center">
            <h3 className="font-mono font-black text-white text-sm uppercase tracking-widest">{program.name}</h3>
            <ChevronRightIcon className="details-arrow transition-transform text-slate-500" />
          </summary>
          <div className="border-t border-orange-500/10 p-3 space-y-2">
            {program.macrocycles.flatMap(macro =>
              (macro.blocks || []).flatMap(block =>
                block.mesocycles.flatMap(meso =>
                  meso.weeks.map(week => (
                    <div key={week.id} className="mb-4 last:mb-0">
                      <h4 className="text-[9px] font-mono font-black text-slate-500 uppercase tracking-widest px-2 mb-2">{block.name} • {meso.name} • {week.name}</h4>
                      <div className="space-y-1">
                        {week.sessions.map(session => (
                          <div key={session.id} onClick={() => handleSessionClick(session, program, week.variant)} className="flex justify-between items-center p-3 hover:bg-orange-500/10 bg-[#0a0a0a] rounded-xl cursor-pointer transition-colors border border-orange-500/10 hover:border-orange-500/30 group">
                            <div className="min-w-0">
                              <p className="text-slate-200 font-mono font-bold text-sm truncate group-hover:text-white">{session.name}</p>
                              <p className="text-[9px] font-mono text-slate-500 uppercase tracking-widest">{getSessionExerciseCount(session)} ejercicios</p>
                            </div>
                            <PlayIcon size={20} className="text-orange-500/70 group-hover:text-orange-500" />
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
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Entrenar" height="90vh">
      {sessionToStart ? renderVariantSelector() : renderProgramList()}
    </WorkoutDrawer>
  );
};

export default StartWorkoutDrawer;
