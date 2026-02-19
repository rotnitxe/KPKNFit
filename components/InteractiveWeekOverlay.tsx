// components/InteractiveWeekOverlay.tsx
import React, { useState } from 'react';
import { ProgramWeek, Session } from '../types';
import { XIcon, DumbbellIcon, PlusIcon, TrashIcon, CheckCircleIcon, EditIcon } from './icons';

interface InteractiveWeekOverlayProps {
    week: ProgramWeek;
    weekTitle: string;
    onClose: () => void;
    onSave: (updatedWeek: ProgramWeek) => void;
}

// Días de la semana, iniciando en Domingo
const DAYS_OF_WEEK = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

export default function InteractiveWeekOverlay({ week, weekTitle, onClose, onSave }: InteractiveWeekOverlayProps) {
    const [localSessions, setLocalSessions] = useState<Session[]>(JSON.parse(JSON.stringify(week.sessions || [])));
    const [weekName, setWeekName] = useState(week.name);

    const handleAddSession = (dayIndex: number) => {
        const newSession: Session = {
            id: crypto.randomUUID(),
            name: 'Nueva Sesión',
            dayOfWeek: dayIndex,
            exercises: []
        };
        setLocalSessions([...localSessions, newSession]);
    };

    const handleRemoveSession = (sessionId: string) => {
        setLocalSessions(localSessions.filter(s => s.id !== sessionId));
    };

    const handleUpdateSessionName = (sessionId: string, newName: string) => {
        setLocalSessions(localSessions.map(s => s.id === sessionId ? { ...s, name: newName } : s));
    };

    const handleSave = () => {
        onSave({ ...week, name: weekName, sessions: localSessions });
        onClose();
    };

    return (
        <div className="fixed inset-0 z-[100] bg-black/95 backdrop-blur-xl overflow-y-auto font-sans flex flex-col">
            {/* Cabecera del Overlay */}
            <div className="sticky top-0 z-50 bg-black/80 backdrop-blur-md border-b border-white/10 px-6 py-4 flex justify-between items-center">
                <div>
                    <span className="text-yellow-400 text-[10px] font-black uppercase tracking-widest block mb-1">Editor de Split Semanal</span>
                    <input 
                        value={weekName} 
                        onChange={(e) => setWeekName(e.target.value)} 
                        className="bg-transparent text-2xl font-black text-white uppercase tracking-tighter focus:outline-none w-full"
                    />
                </div>
                <div className="flex items-center gap-3">
                    <button onClick={onClose} className="p-3 bg-white/5 hover:bg-white/10 rounded-full transition-colors text-white">
                        <XIcon size={20} />
                    </button>
                    <button onClick={handleSave} className="px-6 py-3 bg-white text-black rounded-full font-black text-xs uppercase tracking-widest hover:scale-105 transition-transform flex items-center gap-2 shadow-[0_0_20px_rgba(255,255,255,0.2)]">
                        <CheckCircleIcon size={16} /> Guardar Split
                    </button>
                </div>
            </div>

            {/* Contenido: Grid de 7 Días */}
            <div className="flex-1 p-6 md:p-10 max-w-7xl mx-auto w-full">
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7 gap-4">
                    {DAYS_OF_WEEK.map((dayName, dayIndex) => {
                        const daySessions = localSessions.filter(s => s.dayOfWeek === dayIndex);
                        const isRestDay = daySessions.length === 0;

                        return (
                            <div key={dayIndex} className={`bg-[#111] border rounded-3xl p-4 flex flex-col h-[400px] transition-all duration-300 ${isRestDay ? 'border-white/5' : 'border-white/20 shadow-[0_0_15px_rgba(255,255,255,0.05)]'}`}>
                                <div className="text-center mb-6 pb-4 border-b border-white/5">
                                    <h3 className={`text-sm font-black uppercase tracking-widest ${isRestDay ? 'text-gray-600' : 'text-white'}`}>{dayName}</h3>
                                </div>

                                <div className="flex-1 overflow-y-auto space-y-3 hide-scrollbar">
                                    {isRestDay ? (
                                        <div className="h-full flex flex-col items-center justify-center opacity-30 select-none">
                                            <span className="text-4xl mb-2">😴</span>
                                            <p className="text-[10px] font-black uppercase tracking-widest text-gray-400">Descanso</p>
                                        </div>
                                    ) : (
                                        daySessions.map(session => (
                                            <div key={session.id} className="bg-black border border-white/10 rounded-2xl p-3 relative group">
                                                <div className="flex justify-between items-start mb-2">
                                                    <DumbbellIcon size={14} className="text-yellow-400 shrink-0 mt-1" />
                                                    <button onClick={() => handleRemoveSession(session.id)} className="text-gray-600 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity">
                                                        <TrashIcon size={14} />
                                                    </button>
                                                </div>
                                                <input 
                                                    value={session.name}
                                                    onChange={(e) => handleUpdateSessionName(session.id, e.target.value)}
                                                    className="bg-transparent text-xs font-black text-white focus:outline-none w-full uppercase mb-2"
                                                    placeholder="Nombre Sesión"
                                                />
                                                <button className="w-full py-2 bg-white/5 hover:bg-white/10 rounded-xl flex items-center justify-center gap-1 text-[9px] font-black text-gray-400 uppercase tracking-widest transition-colors mt-2">
                                                    <EditIcon size={10} /> Editar Ejercicios
                                                </button>
                                            </div>
                                        ))
                                    )}
                                </div>

                                <button onClick={() => handleAddSession(dayIndex)} className="mt-4 w-full py-3 border-2 border-dashed border-white/10 hover:border-white/30 rounded-2xl flex items-center justify-center gap-2 text-gray-500 hover:text-white text-[10px] font-black uppercase tracking-widest transition-all">
                                    <PlusIcon size={14} /> Añadir
                                </button>
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}