import React, { useState } from 'react';
import { ProgramWeek, Session } from '../types';
import { DragDropContext, Droppable, Draggable, DropResult } from '@hello-pangea/dnd';
import { DragHandleIcon, XIcon, CheckIcon } from './icons';
import { getOrderedDaysOfWeek } from '../utils/calculations';
import { useAppContext } from '../contexts/AppContext';

interface Props {
    week: ProgramWeek;
    weekTitle: string;
    onClose: () => void;
    onSave: (updatedWeek: ProgramWeek) => void;
}

const InteractiveWeekOverlay: React.FC<Props> = ({ week, weekTitle, onClose, onSave }) => {
    const { settings } = useAppContext();
    const startOn = settings?.startWeekOn ?? 1; // 1 = Lunes
    const days = getOrderedDaysOfWeek(startOn);

    const [sessionsState, setSessionsState] = useState<Session[]>(week.sessions || []);

    const onDragEnd = (result: DropResult) => {
        const { source, destination, draggableId } = result;
        if (!destination) return;
        if (source.droppableId === destination.droppableId && source.index === destination.index) return;

        const sessionToMove = sessionsState.find(s => s.id === draggableId);
        if (!sessionToMove) return;

        const newDayValue = parseInt(destination.droppableId.replace('day-', ''));

        const updatedSessions = sessionsState.map(s => {
            if (s.id === draggableId) return { ...s, dayOfWeek: newDayValue };
            return s;
        });

        setSessionsState(updatedSessions);
    };

    return (
        <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-xl flex flex-col items-center p-4 sm:p-6 overflow-hidden animate-in fade-in duration-300">
            
            {/* BOTÓN CERRAR (Pequeño, esquina superior derecha) */}
            <button 
                onClick={onClose} 
                className="absolute top-6 right-6 z-50 p-2.5 bg-zinc-900 border border-white/10 rounded-full text-zinc-500 hover:text-white hover:bg-zinc-800 transition-all shadow-lg"
                title="Cerrar sin guardar"
            >
                <XIcon size={14}/>
            </button>

            {/* CONTENEDOR PRINCIPAL */}
            <div className="w-full max-w-4xl flex flex-col h-full relative">
                
                {/* TÍTULO CON SU PROPIO ESPACIO */}
                <div className="w-full flex flex-col items-center justify-center pt-8 pb-6 shrink-0 border-b border-white/5">
                    <span className="text-[10px] text-zinc-500 font-black uppercase tracking-widest mb-1">Editor de Split</span>
                    <h2 className="text-2xl font-black text-white uppercase tracking-tight text-center px-4 w-full break-words leading-tight">
                        Distribución: {week.name || weekTitle}
                    </h2>
                    <p className="text-[10px] text-zinc-400 mt-2 font-bold max-w-xs text-center">Arrastra las sesiones para reorganizar tu semana.</p>
                </div>

                {/* GRID DE DÍAS (Tarjetas compactas) */}
                <div className="flex-1 overflow-y-auto custom-scrollbar w-full py-6 pr-2">
                    <DragDropContext onDragEnd={onDragEnd}>
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 pb-24">
                            {days.map(day => {
                                const daySessions = sessionsState.filter(s => s.dayOfWeek === day.value);
                                
                                return (
                                    <Droppable key={`day-${day.value}`} droppableId={`day-${day.value}`}>
                                        {(provided: any, snapshot: any) => (
                                            <div
                                                ref={provided.innerRef}
                                                {...provided.droppableProps}
                                                className={`bg-[#0a0a0a] border rounded-2xl p-3 transition-colors min-h-[120px] flex flex-col
                                                    ${snapshot.isDraggingOver ? 'border-white/40 bg-[#111]' : 'border-[#222]'}`}
                                            >
                                                <div className="flex items-center gap-2 mb-3 shrink-0">
                                                    <div className={`w-8 h-8 rounded-lg flex items-center justify-center text-[10px] font-black ${daySessions.length > 0 ? 'bg-white text-black' : 'bg-zinc-900 text-zinc-600'}`}>
                                                        {day.label.substring(0,3).toUpperCase()}
                                                    </div>
                                                    <span className="text-[11px] font-bold text-zinc-400 uppercase">{day.label}</span>
                                                </div>

                                                <div className="flex-1 flex flex-col gap-2">
                                                    {daySessions.map((session, index) => (
                                                        <Draggable key={session.id} draggableId={session.id} index={index}>
                                                        {(provided: any, snapshot: any) => (
                                                            <div
                                                                ref={provided.innerRef}
                                                                    {...provided.draggableProps}
                                                                    {...provided.dragHandleProps}
                                                                    className={`bg-zinc-900 border border-white/5 rounded-xl p-3 flex items-center justify-between group shadow-sm transition-all
                                                                        ${snapshot.isDragging ? 'shadow-[0_10px_30px_rgba(0,0,0,0.8)] scale-105 border-white/20 z-50' : 'hover:border-white/20'}`}
                                                                >
                                                                    <div className="flex-1 pr-2 truncate">
                                                                        <h4 className="text-[11px] font-black text-white uppercase truncate">{session.name}</h4>
                                                                        <span className="text-[9px] text-zinc-500 font-bold">{(session.exercises || []).length} ejs.</span>
                                                                    </div>
                                                                    <DragHandleIcon size={14} className="text-zinc-600 cursor-grab shrink-0"/>
                                                                </div>
                                                            )}
                                                        </Draggable>
                                                    ))}
                                                    {provided.placeholder}
                                                </div>
                                            </div>
                                        )}
                                    </Droppable>
                                );
                            })}
                        </div>
                    </DragDropContext>
                </div>
                
                {/* BOTÓN FLOTANTE "GUARDAR" (FAB BLANCO) */}
                <div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-50">
                    <button 
                        onClick={() => onSave({ ...week, sessions: sessionsState })}
                        className="w-16 h-16 bg-white text-black rounded-full flex items-center justify-center shadow-[0_10px_40px_rgba(255,255,255,0.3)] hover:scale-110 active:scale-95 transition-all"
                        title="Guardar Split Semanal"
                    >
                        <CheckIcon size={32} strokeWidth={3} />
                    </button>
                </div>

            </div>
        </div>
    );
};

export default InteractiveWeekOverlay;