// components/CurrentBlockCard.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import Card from './ui/Card';
import { BookOpenIcon } from './icons';

const CurrentBlockCard: React.FC = () => {
    const { history, programs } = useAppState();

    const currentBlock = useMemo(() => {
        const lastLog = history[history.length - 1];
        if (!lastLog) return null;

        const program = programs.find(p => p.id === lastLog.programId);
        if (!program || !program.macrocycles) return null;

        for (const macro of program.macrocycles) {
            for (const block of (macro.blocks || [])) {
                for (const meso of block.mesocycles) {
                    for (const week of meso.weeks) {
                        if (week.sessions.some(s => s.id === lastLog.sessionId)) {
                            return {
                                name: meso.name,
                                goal: meso.goal,
                            };
                        }
                    }
                }
            }
        }
        return null;
    }, [history, programs]);

    if (!currentBlock) {
        return null;
    }

    return (
        <Card>
            <div className="flex items-center gap-3">
                <BookOpenIcon size={24} className="text-primary-color flex-shrink-0" />
                <div>
                    <h3 className="text-xl font-bold text-white">Estás en el bloque:</h3>
                    <p className="text-primary-color font-semibold">{currentBlock.name} ({currentBlock.goal})</p>
                </div>
            </div>
        </Card>
    );
};

export default CurrentBlockCard;