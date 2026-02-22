import React from 'react';
import { Session, ExerciseMuscleInfo } from '../../types';
import { EditIcon, DumbbellIcon, ChevronUpIcon, ChevronDownIcon } from '../icons';

interface SessionsStepProps {
    splitPattern: string[];
    detailedSessions: Record<number, Session>;
    startDay: number;
    cycleDuration: number;
    onRenameSession: (index: number, name: string) => void;
    onUpdateSession: (index: number, session: Session) => void;
    onMoveSession: (index: number, direction: 'up' | 'down') => void;
    exerciseList: ExerciseMuscleInfo[];
    blockNames?: string[];
    activeBlockEdit?: number;
    onSwitchBlockEdit?: (idx: number) => void;
    isComplex?: boolean;
    splitMode?: 'global' | 'per_block';
    applyToAllBlocks?: boolean;
    onToggleApplyToAll?: (v: boolean) => void;
    renderInlineSessionCreator?: (props: {
        dayLabel: string;
        sessionName: string;
        isRest: boolean;
        sessionData: Session | undefined;
        onRename: (name: string) => void;
        onUpdateSession: (s: Session) => void;
        onMoveUp: () => void;
        onMoveDown: () => void;
        isFirst: boolean;
        isLast: boolean;
        exerciseList: ExerciseMuscleInfo[];
    }) => React.ReactNode;
}

const DAY_NAMES = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

const SessionsStep: React.FC<SessionsStepProps> = ({
    splitPattern, detailedSessions, startDay, cycleDuration,
    onRenameSession, onUpdateSession, onMoveSession, exerciseList,
    blockNames, activeBlockEdit, onSwitchBlockEdit,
    isComplex, splitMode, applyToAllBlocks, onToggleApplyToAll,
    renderInlineSessionCreator,
}) => {
    const getDayLabel = (index: number) => {
        const dayIndex = (startDay + index) % 7;
        return DAY_NAMES[dayIndex];
    };

    return (
        <div className="max-w-4xl mx-auto py-6 px-4 space-y-4">
            <div className="flex flex-col gap-1 mb-4">
                <div className="flex items-center gap-2">
                    <EditIcon size={18} className="text-white" />
                    <h3 className="text-lg font-black text-white uppercase tracking-tight">Prepara tus Sesiones</h3>
                </div>
                <p className="text-[10px] text-zinc-500">Podrás editarlas con más detalle después de crear el programa.</p>
            </div>

            {/* Block selector (complex) */}
            {isComplex && blockNames && onSwitchBlockEdit && (
                <div className="flex gap-2 overflow-x-auto no-scrollbar pb-2">
                    {blockNames.map((name, idx) => {
                        const isActive = activeBlockEdit === idx;
                        return (
                            <button
                                key={name}
                                onClick={() => onSwitchBlockEdit(idx)}
                                className={`shrink-0 px-4 py-2 rounded-full text-[9px] font-black uppercase tracking-wider transition-all border ${
                                    isActive
                                        ? 'bg-white text-black border-white'
                                        : 'bg-zinc-950 text-zinc-500 border-white/10 hover:border-white/30'
                                }`}
                            >
                                {name}
                            </button>
                        );
                    })}
                </div>
            )}

            {/* Apply to all toggle */}
            {isComplex && splitMode === 'per_block' && onToggleApplyToAll && (
                <div className="flex items-center justify-end gap-3 bg-zinc-950 px-3 py-2 rounded-lg border border-white/10">
                    <span className="text-[9px] font-bold text-zinc-400 uppercase tracking-wider">
                        Aplicar a bloques con mismo split
                    </span>
                    <button
                        onClick={() => onToggleApplyToAll(!applyToAllBlocks)}
                        className={`w-8 h-4 rounded-full transition-colors relative ${applyToAllBlocks ? 'bg-white' : 'bg-zinc-700'}`}
                    >
                        <div className={`w-3 h-3 rounded-full absolute top-0.5 transition-transform ${applyToAllBlocks ? 'translate-x-4 bg-black' : 'translate-x-0.5 bg-zinc-400'}`} />
                    </button>
                </div>
            )}

            {/* Session list */}
            <div className="space-y-3">
                {splitPattern.map((label, index) => {
                    const dayLabel = getDayLabel(index);
                    const isRest = label.toLowerCase() === 'descanso';

                    if (renderInlineSessionCreator) {
                        return (
                            <React.Fragment key={index}>
                                {renderInlineSessionCreator({
                                    dayLabel,
                                    sessionName: label,
                                    isRest,
                                    sessionData: detailedSessions[index],
                                    onRename: (name: string) => onRenameSession(index, name),
                                    onUpdateSession: (s: Session) => onUpdateSession(index, s),
                                    onMoveUp: () => onMoveSession(index, 'up'),
                                    onMoveDown: () => onMoveSession(index, 'down'),
                                    isFirst: index === 0,
                                    isLast: index === cycleDuration - 1,
                                    exerciseList,
                                })}
                            </React.Fragment>
                        );
                    }

                    return (
                        <div key={index} className={`bg-zinc-950 border rounded-xl p-3 transition-all ${
                            isRest ? 'border-white/5 opacity-40' : 'border-white/10'
                        }`}>
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                    <div className={`w-8 h-8 rounded-lg flex items-center justify-center text-[9px] font-black ${
                                        isRest ? 'bg-zinc-800 text-zinc-600' : 'bg-blue-900/20 text-blue-400 border border-blue-500/30'
                                    }`}>
                                        {dayLabel.slice(0, 3)}
                                    </div>
                                    <div>
                                        {isRest ? (
                                            <span className="text-xs font-bold text-zinc-500 italic">Descanso</span>
                                        ) : (
                                            <input
                                                type="text"
                                                value={label}
                                                onChange={e => onRenameSession(index, e.target.value)}
                                                className="bg-transparent text-xs font-black text-white uppercase tracking-tight focus:ring-0 border-none p-0"
                                            />
                                        )}
                                        <div className="text-[8px] text-zinc-500 font-bold">{dayLabel}</div>
                                    </div>
                                </div>
                                {!isRest && (
                                    <div className="flex items-center gap-1">
                                        <span className="text-[8px] text-zinc-500 font-bold mr-2">
                                            {detailedSessions[index]?.exercises?.length || 0} ej.
                                        </span>
                                        <button
                                            onClick={() => onMoveSession(index, 'up')}
                                            disabled={index === 0}
                                            className="p-1 text-zinc-600 hover:text-white disabled:opacity-20"
                                        >
                                            <ChevronUpIcon size={12} />
                                        </button>
                                        <button
                                            onClick={() => onMoveSession(index, 'down')}
                                            disabled={index === cycleDuration - 1}
                                            className="p-1 text-zinc-600 hover:text-white disabled:opacity-20"
                                        >
                                            <ChevronDownIcon size={12} />
                                        </button>
                                    </div>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default SessionsStep;
