import React, { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { Program, Session, ProgramWeek } from '../../types';
import { useAppContext } from '../../contexts/AppContext';
import { PlayIcon, EditIcon, TrashIcon, PlusIcon, StarIcon, CalendarIcon, GripVerticalIcon, ChevronDownIcon } from '../icons';

interface DayViewProps {
    program: Program;
    selectedWeekId: string | null;
    currentWeekId?: string;
    onEditSession?: (sessionId: string) => void;
    onAddSession?: (dayOfWeek: number) => void;
    onDeleteSession?: (sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    onStartWorkout?: (session: Session) => void;
    onUpdateProgram?: (program: Program) => void;
    addToast?: (message: string, type?: 'danger' | 'success' | 'achievement' | 'suggestion', title?: string, duration?: number, why?: string) => void;
}

const DAYS_OF_WEEK = [
    { id: 1, name: 'Lunes', short: 'Lun' },
    { id: 2, name: 'Martes', short: 'Mar' },
    { id: 3, name: 'Miércoles', short: 'Mié' },
    { id: 4, name: 'Jueves', short: 'Jue' },
    { id: 5, name: 'Viernes', short: 'Vie' },
    { id: 6, name: 'Sábado', short: 'Sáb' },
    { id: 0, name: 'Domingo', short: 'Dom' }
];

const DayView: React.FC<DayViewProps> = ({
    program,
    selectedWeekId,
    currentWeekId,
    onEditSession,
    onAddSession,
    onDeleteSession,
    onStartWorkout,
    onUpdateProgram,
    addToast,
}) => {
    const { history, activeProgramState } = useAppContext();
    const [draggedSessionId, setDraggedSessionId] = useState<string | null>(null);
    const [expandedDays, setExpandedDays] = useState<Set<number>>(new Set([1]));
    const [showWeekSettings, setShowWeekSettings] = useState(false);

    // Detectar si es programa simple
    const isSimple = program.structure === 'simple' || (!program.structure && program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1);

    // Día de inicio de la semana y duración (AHORA DESDE EL PROGRAMA)
    const startDay = program.startDay ?? 1;
    const weekDays = program.weekDays ?? 7; // ✅ Ahora lee desde program

    // Generar días de la semana dinámicamente (7-10 días)
    const getDynamicDays = useMemo(() => {
        const baseDays = [
            { id: 1, name: 'Lunes', short: 'Lun' },
            { id: 2, name: 'Martes', short: 'Mar' },
            { id: 3, name: 'Miércoles', short: 'Mié' },
            { id: 4, name: 'Jueves', short: 'Jue' },
            { id: 5, name: 'Viernes', short: 'Vie' },
            { id: 6, name: 'Sábado', short: 'Sáb' },
            { id: 0, name: 'Domingo', short: 'Dom' }
        ];

        // Rotar según startDay (ej: si startDay=3, comienza el Miércoles)
        const rotated = [...baseDays.slice(startDay), ...baseDays.slice(0, startDay)];
        
        // Tomar los primeros 7 días rotados
        const week = rotated.slice(0, 7);
        
        // Agregar días adicionales si weekDays > 7 (ej: "Lunes 2", "Martes 2")
        const extraDays = [];
        for (let i = 7; i < weekDays; i++) {
            const baseDay = baseDays[i % 7];
            const cycleNum = Math.floor(i / 7) + 1;
            extraDays.push({
                id: i,
                name: `${baseDay.name} ${cycleNum}`,
                short: `${baseDay.short}${cycleNum}`
            });
        }
        
        return [...week, ...extraDays];
    }, [startDay, weekDays]);

    // Encontrar la semana seleccionada
    const selectedWeek: ProgramWeek | null = useMemo(() => {
        if (!selectedWeekId) return null;
        for (const macro of program.macrocycles) {
            for (const block of macro.blocks || []) {
                for (const meso of block.mesocycles) {
                    const week = meso.weeks.find(w => w.id === selectedWeekId);
                    if (week) return week;
                }
            }
        }
        return null;
    }, [program, selectedWeekId]);

    // Índices de macrociclo y mesociclo de la semana seleccionada
    const selectedWeekContext = useMemo(() => {
        if (!selectedWeekId) return null;
        for (let macroIdx = 0; macroIdx < program.macrocycles.length; macroIdx++) {
            const macro = program.macrocycles[macroIdx];
            let mesoOffset = 0;
            for (const block of (macro.blocks || [])) {
                for (let localMesoIdx = 0; localMesoIdx < block.mesocycles.length; localMesoIdx++) {
                    const meso = block.mesocycles[localMesoIdx];
                    if (meso.weeks.find(w => w.id === selectedWeekId)) {
                        return { macroIndex: macroIdx, mesoIndex: mesoOffset + localMesoIdx };
                    }
                }
                mesoOffset += block.mesocycles.length;
            }
        }
        return null;
    }, [program, selectedWeekId]);

    // Obtener logs de historial
    const weekLogs = useMemo(() => {
        if (!selectedWeek) return [];
        const weekSessionIds = new Set(selectedWeek.sessions.map(s => s.id));
        return history.filter((log: any) => weekSessionIds.has(log.sessionId));
    }, [selectedWeek, history]);

    // Agrupar sesiones por día
    const sessionsByDay = useMemo(() => {
        const grouped: Record<number, Session[]> = {};
        getDynamicDays.forEach(day => {
            // Para días 0-6, usar dayOfWeek directo
            // Para días 7+, mapear al día base (ej: día 7 = Lunes 2 → dayOfWeek 1)
            const baseDayId = day.id < 7 ? day.id : (day.id % 7);
            grouped[day.id] = selectedWeek?.sessions.filter(s => s.dayOfWeek === baseDayId) || [];
        });
        return grouped;
    }, [selectedWeek, getDynamicDays]);

    // Calcular sesión principal por día (la primera o la marcada como principal)
    const getMainSessionId = (dayId: number): string | undefined => {
        const daySessions = sessionsByDay[dayId];
        if (daySessions.length === 0) return undefined;
        
        // Buscar sesión marcada como principal
        const mainSession = daySessions.find(s => s.isMainSession);
        if (mainSession) return mainSession.id;
        
        // Si no, retornar la primera
        return daySessions[0]?.id;
    };

    // Toggle expandir/colapsar día
    const toggleDay = (dayId: number) => {
        const newExpanded = new Set(expandedDays);
        if (newExpanded.has(dayId)) {
            newExpanded.delete(dayId);
        } else {
            newExpanded.add(dayId);
        }
        setExpandedDays(newExpanded);
    };

    // Drag handlers
    const handleDragStart = (e: React.DragEvent, sessionId: string) => {
        setDraggedSessionId(sessionId);
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', sessionId);
    };

    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
    };

    const handleDropOnDay = (e: React.DragEvent, targetDayId: number) => {
        e.preventDefault();
        if (!draggedSessionId || !selectedWeekId) return;

        // Mover sesión al nuevo día
        if (onUpdateProgram && addToast) {
            const updated = JSON.parse(JSON.stringify(program));
            let found = false;
            
            updated.macrocycles.forEach((macro: any) => {
                macro.blocks.forEach((block: any) => {
                    block.mesocycles.forEach((meso: any) => {
                        meso.weeks.forEach((week: any) => {
                            if (week.id === selectedWeekId) {
                                const session = week.sessions.find((s: any) => s.id === draggedSessionId);
                                if (session) {
                                    session.dayOfWeek = targetDayId;
                                    found = true;
                                }
                            }
                        });
                    });
                });
            });

            if (found) {
                onUpdateProgram(updated);
                addToast('Sesión movida', 'success');
            }
        }
        setDraggedSessionId(null);
    };

    // Reordenar sesiones dentro del mismo día
    const handleReorderSessions = (dayId: number, fromIndex: number, toIndex: number) => {
        if (!selectedWeekId || !onUpdateProgram || !addToast) return;
        
        const updated = JSON.parse(JSON.stringify(program));
        let found = false;

        updated.macrocycles.forEach((macro: any) => {
            macro.blocks.forEach((block: any) => {
                block.mesocycles.forEach((meso: any) => {
                    meso.weeks.forEach((week: any) => {
                        if (week.id === selectedWeekId) {
                            const daySessions = week.sessions.filter((s: any) => s.dayOfWeek === dayId);
                            if (daySessions.length > 1 && fromIndex >= 0 && toIndex >= 0 && fromIndex !== toIndex) {
                                // Reordenar
                                const [removed] = daySessions.splice(fromIndex, 1);
                                daySessions.splice(toIndex, 0, removed);
                                
                                // Actualizar dayOfWeek para mantener el orden
                                daySessions.forEach((s: any, idx: number) => {
                                    s.dayOfWeek = dayId;
                                });
                                
                                found = true;
                            }
                        }
                    });
                });
            });
        });

        if (found) {
            onUpdateProgram(updated);
            addToast('Orden actualizado', 'success');
        }
    };

    // Establecer sesión como principal
    const setMainSession = (dayId: number, sessionId: string) => {
        if (!selectedWeekId || !onUpdateProgram || !addToast) return;

        const updated = JSON.parse(JSON.stringify(program));
        let found = false;

        updated.macrocycles.forEach((macro: any) => {
            macro.blocks.forEach((block: any) => {
                block.mesocycles.forEach((meso: any) => {
                    meso.weeks.forEach((week: any) => {
                        if (week.id === selectedWeekId) {
                            week.sessions.forEach((s: any) => {
                                if (s.dayOfWeek === dayId) {
                                    s.isMainSession = s.id === sessionId;
                                    found = true;
                                }
                            });
                        }
                    });
                });
            });
        });

        if (found) {
            onUpdateProgram(updated);
            addToast('Sesión principal actualizada', 'success');
        }
    };

    // Micro-programación: rotar sesión principal cada X ciclos
    const [showMicroProgramModal, setShowMicroProgramModal] = useState<{ dayId: number; sessionId: string } | null>(null);

    const handleMicroProgram = (dayId: number, sessionId: string, cycles: number) => {
        if (!selectedWeekId || !onUpdateProgram || !addToast) return;

        // Para programas simples, configurar rotación cíclica
        const updated = JSON.parse(JSON.stringify(program));
        
        // Agregar metadata de micro-programación a la sesión
        updated.macrocycles.forEach((macro: any) => {
            macro.blocks.forEach((block: any) => {
                block.mesocycles.forEach((meso: any) => {
                    meso.weeks.forEach((week: any) => {
                        if (week.id === selectedWeekId) {
                            week.sessions.forEach((s: any) => {
                                if (s.dayOfWeek === dayId) {
                                    if (s.id === sessionId) {
                                        s.microProgram = {
                                            enabled: true,
                                            everyXCycles: cycles,
                                            isMainInCycle: true
                                        };
                                    } else {
                                        s.microProgram = {
                                            enabled: true,
                                            everyXCycles: cycles,
                                            isMainInCycle: false
                                        };
                                    }
                                }
                            });
                        }
                    });
                });
            });
        });

        onUpdateProgram(updated);
        addToast('Micro-programación configurada', 'success');
        setShowMicroProgramModal(null);
    };

    if (!selectedWeek) {
        return (
            <div className="px-4 py-8 text-center">
                <p className="text-sm text-zinc-500">Selecciona una semana para ver los detalles</p>
            </div>
        );
    }

    return (
        <div className="pb-6">
            {/* Header con configuración de semana */}
            <div className="mt-0 px-4 mb-3">
                <div className="flex items-center justify-between">
                    <div>
                        <h3 className="text-lg font-black text-zinc-900">
                            {selectedWeek.name || `Semana ${selectedWeek.id.slice(-2)}`}
                        </h3>
                        <p className="text-[11px] text-zinc-500 mt-0.5">
                            {selectedWeek.sessions.length} sesiones programadas
                        </p>
                    </div>
                    <div className="flex items-center gap-2">
                        {/* Botón para configuración de semana */}
                        <button
                            onClick={() => setShowWeekSettings(true)}
                            className="flex items-center gap-2 px-3 py-2 rounded-xl bg-zinc-100 hover:bg-zinc-200 transition-colors group"
                            title="Configurar semana (días y duración)"
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-zinc-600">
                                <circle cx="12" cy="12" r="3"></circle>
                                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
                            </svg>
                            <span className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-600 group-hover:text-zinc-900">
                                Configurar Semana
                            </span>
                        </button>
                    </div>
                </div>
            </div>

            {/* Modal de configuración de semana */}
            {showWeekSettings && (
                <WeekSettingsModal
                    isOpen={showWeekSettings}
                    startDay={startDay}
                    weekDays={weekDays}
                    onStartDayChange={(day) => {
                        const updated = { ...program, startDay: day };
                        onUpdateProgram?.(updated);
                        addToast?.('Día de inicio actualizado', 'success');
                    }}
                    onWeekDaysChange={(days) => {
                        const updated = { ...program, weekDays: days }; // ✅ Ahora guarda en el programa
                        onUpdateProgram?.(updated);
                        addToast?.(`Semana de ${days} días configurada`, 'success');
                    }}
                    onClose={() => setShowWeekSettings(false)}
                />
            )}

            {/* Tarjetas por día */}
            <div className="px-4 space-y-3">
                {getDynamicDays.map((day) => {
                    const daySessions = sessionsByDay[day.id];
                    const isExpanded = expandedDays.has(day.id);
                    const mainSessionId = getMainSessionId(day.id);
                    const hasSessions = daySessions.length > 0;

                    return (
                        <motion.div
                            key={day.id}
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            className="bg-white rounded-3xl border border-zinc-200/60 overflow-hidden shadow-sm"
                        >
                            {/* Header del día (clickable) */}
                            <div
                                onClick={() => toggleDay(day.id)}
                                onDragOver={handleDragOver}
                                onDrop={(e) => handleDropOnDay(e, day.id)}
                                className={`
                                    px-4 py-3 flex items-center justify-between cursor-pointer transition-colors
                                    ${isExpanded ? 'bg-zinc-50' : 'bg-white hover:bg-zinc-50'}
                                `}
                            >
                                <div className="flex items-center gap-3">
                                    <div className={`
                                        w-10 h-10 rounded-xl flex items-center justify-center
                                        ${hasSessions ? 'bg-gradient-to-br from-purple-500 to-purple-600 text-white' : 'bg-zinc-100 text-zinc-400'}
                                    `}>
                                        <span className="text-[11px] font-black">{day.short}</span>
                                    </div>
                                    <div>
                                        <h4 className="text-sm font-bold text-zinc-900">{day.name}</h4>
                                        <p className="text-[9px] text-zinc-500">
                                            {daySessions.length} {daySessions.length === 1 ? 'sesión' : 'sesiones'}
                                            {mainSessionId && daySessions.length > 1 && (
                                                <span className="ml-1.5 px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-700 text-[7px] font-black uppercase tracking-wider">
                                                    Principal
                                                </span>
                                            )}
                                        </p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2">
                                    {hasSessions && (
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onAddSession?.(day.id);
                                            }}
                                            className="w-8 h-8 rounded-full bg-purple-100 text-purple-600 flex items-center justify-center hover:bg-purple-200 transition-colors"
                                        >
                                            <PlusIcon size={16} />
                                        </button>
                                    )}
                                    {isExpanded ? (
                                        <ChevronDownIcon size={18} className="text-zinc-400" />
                                    ) : (
                                        <ChevronDownIcon size={18} className="text-zinc-400 rotate-[-90deg]" />
                                    )}
                                </div>
                            </div>

                            {/* Sesiones del día (expandible) */}
                            {isExpanded && (
                                <motion.div
                                    initial={{ height: 0, opacity: 0 }}
                                    animate={{ height: 'auto', opacity: 1 }}
                                    exit={{ height: 0, opacity: 0 }}
                                    className="px-4 pb-4 pt-2 border-t border-zinc-100"
                                >
                                    {daySessions.length === 0 ? (
                                        <div
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onAddSession?.(day.id);
                                            }}
                                            className="py-5 px-4 text-center border-2 border-dashed border-zinc-200 rounded-2xl cursor-pointer hover:border-purple-300 hover:bg-purple-50/30 transition-colors"
                                        >
                                            <PlusIcon size={20} className="mx-auto text-zinc-400 mb-1" />
                                            <p className="text-[9px] font-bold text-zinc-500 uppercase tracking-[0.3em]">
                                                Añadir sesión
                                            </p>
                                        </div>
                                    ) : (
                                        <div className="space-y-2">
                                            {daySessions.map((session, idx) => {
                                                const isCompleted = weekLogs.some((l: any) => l.sessionId === session.id);
                                                const isMain = session.id === mainSessionId;
                                                const isDragging = draggedSessionId === session.id;

                                                return (
                                                    <div
                                                        key={session.id}
                                                        draggable
                                                        onDragStart={(e) => handleDragStart(e, session.id)}
                                                        className={`
                                                            relative bg-[#FEF7FF] rounded-2xl border p-3 shadow-sm transition-all
                                                            ${isDragging ? 'opacity-30 scale-95 border-purple-400' : 'border-zinc-200'}
                                                            ${isMain ? 'border-l-4 border-l-purple-500' : ''}
                                                        `}
                                                    >
                                                        {/* Indicador de sesión principal */}
                                                        {isMain && (
                                                            <div className="absolute -top-2 -right-2 w-6 h-6 rounded-full bg-purple-500 text-white flex items-center justify-center shadow-md">
                                                                <StarIcon size={12} filled />
                                                            </div>
                                                        )}

                                                        <div className="flex items-start gap-3">
                                                            {/* Grip para arrastrar */}
                                                            <div className="mt-1 text-zinc-300 cursor-grab active:cursor-grabbing">
                                                                <GripVerticalIcon size={16} />
                                                            </div>

                                                            {/* Info de la sesión */}
                                                            <div className="flex-1 min-w-0">
                                                                <h5 className="text-sm font-bold text-zinc-900 truncate">
                                                                    {session.name || `Sesión ${idx + 1}`}
                                                                </h5>
                                                                <p className="text-[10px] text-zinc-500 mt-0.5">
                                                                    {session.exercises?.length || 0} ejercicios
                                                                </p>

                                                                {/* Tags */}
                                                                <div className="flex items-center gap-1.5 mt-2 flex-wrap">
                                                                    {session.focus && (
                                                                        <span className="px-2 py-0.5 rounded-full bg-purple-100 text-purple-700 text-[7px] font-black uppercase tracking-[0.2em]">
                                                                            {session.focus}
                                                                        </span>
                                                                    )}
                                                                    {isCompleted && (
                                                                        <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 text-[7px] font-black uppercase tracking-[0.2em]">
                                                                            Completada
                                                                        </span>
                                                                    )}
                                                                    {daySessions.length > 1 && (
                                                                        <button
                                                                            onClick={() => setMainSession(day.id, session.id)}
                                                                            className={`
                                                                                px-2 py-0.5 rounded-full text-[7px] font-black uppercase tracking-[0.2em] transition-colors
                                                                                ${isMain 
                                                                                    ? 'bg-purple-500 text-white' 
                                                                                    : 'bg-zinc-100 text-zinc-500 hover:bg-zinc-200'
                                                                                }
                                                                            `}
                                                                        >
                                                                            {isMain ? 'Principal' : 'Hacer principal'}
                                                                        </button>
                                                                    )}
                                                                    {isSimple && daySessions.length > 1 && (
                                                                        <button
                                                                            onClick={() => setShowMicroProgramModal({ dayId: day.id, sessionId: session.id })}
                                                                            className="px-2 py-0.5 rounded-full bg-cyan-100 text-cyan-700 text-[7px] font-black uppercase tracking-[0.2em] hover:bg-cyan-200 transition-colors"
                                                                        >
                                                                            Micro-programar
                                                                        </button>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        </div>

                                                        {/* Acciones */}
                                                        <div className="flex items-center gap-2 mt-3 pt-3 border-t border-zinc-100">
                                                            {!isCompleted && onStartWorkout && (
                                                                <button
                                                                    onClick={() => onStartWorkout(session)}
                                                                    className="flex-1 h-8 rounded-xl bg-black text-white text-[8px] font-black uppercase tracking-[0.15em] flex items-center justify-center gap-1.5 hover:bg-zinc-800 transition-colors"
                                                                >
                                                                    <PlayIcon size={12} />
                                                                    Iniciar
                                                                </button>
                                                            )}
                                                            {onEditSession && (
                                                                <button
                                                                    onClick={() => onEditSession(session.id)}
                                                                    className="w-8 h-8 rounded-xl bg-zinc-100 text-zinc-600 flex items-center justify-center hover:bg-zinc-200 transition-colors"
                                                                >
                                                                    <EditIcon size={14} />
                                                                </button>
                                                            )}
                                                            {onDeleteSession && (
                                                                <button
                                                                    onClick={() => onDeleteSession(session.id, program.id, selectedWeekContext?.macroIndex ?? 0, selectedWeekContext?.mesoIndex ?? 0, selectedWeek.id)}
                                                                    className="w-8 h-8 rounded-xl bg-red-50 text-red-500 flex items-center justify-center hover:bg-red-100 transition-colors"
                                                                >
                                                                    <TrashIcon size={14} />
                                                                </button>
                                                            )}
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    )}
                                </motion.div>
                            )}
                        </motion.div>
                    );
                })}
            </div>

            {/* Modal de Micro-programación */}
            {showMicroProgramModal && (
                <div className="fixed inset-0 z-[300] bg-black/40 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowMicroProgramModal(null)}>
                    <motion.div
                        initial={{ scale: 0.9, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        className="bg-white w-full max-w-sm rounded-[2rem] p-6 shadow-2xl"
                        onClick={e => e.stopPropagation()}
                    >
                        <div className="w-12 h-12 rounded-full bg-cyan-100 flex items-center justify-center mb-4 mx-auto">
                            <CalendarIcon className="text-cyan-600" size={24} />
                        </div>
                        <h3 className="text-base font-black text-zinc-900 uppercase tracking-tight text-center mb-2">
                            Micro-programación
                        </h3>
                        <p className="text-[10px] text-zinc-500 uppercase tracking-widest text-center mb-6">
                            Rotar sesión principal cada X ciclos
                        </p>

                        <div className="space-y-4">
                            <div>
                                <label className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-400 block mb-2">
                                    Cada cuántos ciclos
                                </label>
                                <div className="flex items-center gap-3">
                                    <input
                                        type="number"
                                        min="1"
                                        max="12"
                                        defaultValue={1}
                                        id="micro-cycles"
                                        className="w-24 h-12 rounded-xl bg-[#F5F3F8] border border-[#ECE6F0] text-center text-sm font-bold outline-none focus:border-cyan-400"
                                    />
                                    <span className="text-[9px] font-bold text-zinc-500">ciclos</span>
                                </div>
                            </div>

                            <div className="pt-4 border-t border-zinc-100">
                                <p className="text-[8px] text-zinc-400 leading-relaxed">
                                    📅 Esta sesión será la principal cada <strong className="text-zinc-700">X</strong> ciclos, alternando con las otras sesiones del día.
                                </p>
                            </div>

                            <div className="flex items-center gap-3 pt-4">
                                <button
                                    onClick={() => setShowMicroProgramModal(null)}
                                    className="flex-1 py-3 rounded-full border-2 border-zinc-200 text-zinc-600 text-[9px] font-black uppercase tracking-[0.15em] hover:bg-zinc-50 transition-colors"
                                >
                                    Cancelar
                                </button>
                                <button
                                    onClick={() => {
                                        const input = document.getElementById('micro-cycles') as HTMLInputElement;
                                        const cycles = parseInt(input.value) || 1;
                                        handleMicroProgram(showMicroProgramModal.dayId, showMicroProgramModal.sessionId, cycles);
                                    }}
                                    className="flex-1 py-3 rounded-full bg-gradient-to-r from-cyan-500 to-cyan-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg"
                                >
                                    Configurar
                                </button>
                            </div>
                        </div>
                    </motion.div>
                </div>
            )}
        </div>
    );
};

// Modal de configuración de semana
const WeekSettingsModal: React.FC<{
    isOpen: boolean;
    startDay: number;
    weekDays: number;
    onStartDayChange: (day: number) => void;
    onWeekDaysChange: (days: number) => void;
    onClose: () => void;
}> = ({ isOpen, startDay, weekDays, onStartDayChange, onWeekDaysChange, onClose }) => {
    if (!isOpen) return null;

    const DAYS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

    return (
        <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={onClose}>
            <div className="bg-white rounded-[2rem] w-full max-w-sm p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                <button onClick={onClose} className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-600">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
                </button>

                <div className="text-center mb-6">
                    <div className="w-16 h-16 mx-auto bg-gradient-to-br from-cyan-100 to-blue-100 rounded-full flex items-center justify-center mb-4">
                        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-cyan-600">
                            <circle cx="12" cy="12" r="10"></circle>
                            <polyline points="12 6 12 12 16 14"></polyline>
                        </svg>
                    </div>
                    <h3 className="text-lg font-black text-zinc-900 uppercase tracking-tight">
                        Configuración de Semana
                    </h3>
                    <p className="text-[10px] text-zinc-500 uppercase tracking-widest mt-1">
                        Personaliza tu semana
                    </p>
                </div>

                {/* Día de inicio */}
                <div className="mb-6">
                    <label className="text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 block mb-3">
                        Día de inicio de semana
                    </label>
                    <div className="grid grid-cols-7 gap-1">
                        {DAYS.map((day, index) => (
                            <button
                                key={day}
                                onClick={() => onStartDayChange(index)}
                                className={`py-2 rounded-xl text-[8px] font-black transition-all ${
                                    startDay === index
                                        ? 'bg-gradient-to-br from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/30'
                                        : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
                                }`}
                            >
                                {day.slice(0, 3)}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Días de la semana */}
                <div className="mb-6">
                    <label className="text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 block mb-3">
                        Duración de semana
                    </label>
                    <div className="grid grid-cols-4 gap-2">
                        {[7, 8, 9, 10].map(days => (
                            <button
                                key={days}
                                onClick={() => onWeekDaysChange(days)}
                                className={`py-3 rounded-xl font-black transition-all ${
                                    weekDays === days
                                        ? 'bg-gradient-to-br from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/30'
                                        : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
                                }`}
                            >
                                {days} días
                            </button>
                        ))}
                    </div>
                    <p className="text-[8px] text-zinc-400 mt-2 text-center">
                        {weekDays > 7 ? `Los días adicionales se llamarán "Día X ${Math.floor(weekDays / 7) + 1}"` : 'Semana estándar de 7 días'}
                    </p>
                </div>

                <button
                    onClick={onClose}
                    className="w-full py-3 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg"
                >
                    Listo
                </button>
            </div>
        </div>
    );
};

export default DayView;
