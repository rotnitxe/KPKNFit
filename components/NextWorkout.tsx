

import React from 'react';
import { Program, Session } from '../types';
import Card from './ui/Card';
import Button from './ui/Button';
import { PlayIcon } from './icons';

interface NextWorkoutProps {
  programs: Program[];
  onStartWorkout: (session: Session, program: Program) => void;
}

const NextWorkout: React.FC<NextWorkoutProps> = ({ programs, onStartWorkout }) => {
    const today = new Date().getDay(); // Sunday - 0, Monday - 1, ...
    
    const todaysSessions: {program: Program, session: Session}[] = [];
    programs.forEach(program => {
        (program.macrocycles || []).forEach(macro => {
            (macro.blocks || []).forEach(block => {
                (block.mesocycles || []).forEach(meso => {
                    (meso.weeks || []).forEach(week => {
                        (week.sessions || []).forEach(session => {
                            if (session.dayOfWeek === today) {
                                todaysSessions.push({ program, session });
                            }
                        });
                    });
                });
            });
        });
    });

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-4">Entrenamiento de Hoy</h3>
            {todaysSessions.length > 0 ? (
                <div className="space-y-3">
                    {todaysSessions.map(({ program, session }) => (
                        <div key={session.id} className="bg-slate-900 p-3 rounded-md flex justify-between items-center">
                            <div>
                                <p className="font-semibold text-slate-200">{session.name}</p>
                                <p className="text-xs text-slate-400">{program.name}</p>
                            </div>
                            <Button onClick={() => onStartWorkout(session, program)} className="!py-1 !px-3 text-sm">
                                <PlayIcon size={14}/> Empezar
                            </Button>
                        </div>
                    ))}
                </div>
            ) : (
                <div className="text-center py-4 text-slate-400">
                    <p>¡Día de descanso!</p>
                    <p className="text-sm">No tienes ninguna sesión programada para hoy.</p>
                </div>
            )}
        </Card>
    );
};

export default NextWorkout;