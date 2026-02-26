// components/ProgramsView.tsx
import React, { useEffect, useState, useRef } from 'react';
import { Program } from '../types';
import { PlayIcon, ChevronRightIcon, PlusIcon, DumbbellIcon, ActivityIcon, CalendarIcon, LayersIcon } from './icons';
import { useAppContext } from '../contexts/AppContext';

// --- COMPONENTE SWIPE TO DELETE ---
const SwipeToDeleteCard: React.FC<{ children: React.ReactNode; onDelete: () => void; }> = ({ children, onDelete }) => {
    const [translateX, setTranslateX] = useState(0);
    const [isDragging, setIsDragging] = useState(false);
    const startX = useRef(0);
    const currentX = useRef(0);

    const handleTouchStart = (e: React.TouchEvent) => {
        startX.current = e.touches[0].clientX;
        setIsDragging(true);
    };

    const handleTouchMove = (e: React.TouchEvent) => {
        if (!isDragging) return;
        const diff = e.touches[0].clientX - startX.current;
        if (diff < 0) { // Solo permite deslizar hacia la izquierda
            currentX.current = Math.max(diff, -100);
            setTranslateX(currentX.current);
        }
    };

    const handleTouchEnd = () => {
        setIsDragging(false);
        if (currentX.current <= -80) { // Umbral de eliminación
            onDelete();
            setTranslateX(0);
            currentX.current = 0;
        } else {
            setTranslateX(0);
            currentX.current = 0;
        }
    };

    return (
        <div className="relative overflow-hidden rounded-2xl mb-3">
            {translateX < 0 && (
                <div className="absolute inset-0 bg-red-500/90 flex items-center justify-end px-6 rounded-2xl pointer-events-none">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 6h18"></path><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                </div>
            )}
            <div 
                className="relative w-full h-full transition-transform duration-200"
                style={{ transform: `translateX(${translateX}px)` }}
                onTouchStart={handleTouchStart}
                onTouchMove={handleTouchMove}
                onTouchEnd={handleTouchEnd}
            >
                {children}
            </div>
        </div>
    );
};

interface ProgramsViewProps {
  programs: Program[];
  onSelectProgram: (program: Program) => void;
  onCreateProgram: () => void;
  isOnline: boolean;
}

const ProgramsView: React.FC<ProgramsViewProps> = ({ programs, onSelectProgram, onCreateProgram, isOnline }) => {
  const { activeProgramState, handleDeleteProgram } = useAppContext();
  const activeProgramId = activeProgramState?.status === 'active' ? activeProgramState.programId : null;
  

  // Clasificación de programas
  const activeProgram = programs.find(p => p.id === activeProgramId);
  const inactivePrograms = programs.filter(p => p.id !== activeProgramId);

  // Utilidad para extraer semanas y sesiones
  const getProgramStats = (program: Program) => {
    const allWeeks = program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks)));
    const totalSessions = allWeeks.reduce((acc, week) => acc + week.sessions.length, 0);
    return { weeks: allWeeks.length, sessions: totalSessions };
  };

  return (
    <div className="animate-fade-in min-h-screen tab-bar-safe-area pt-4">
        {/* Cabecera Principal */}
        <div className="flex justify-between items-center mb-8 px-2">
            <h1 className="text-3xl font-black uppercase tracking-tighter text-white">Programas</h1>
            {programs.length > 0 && (
                <button
                    onClick={onCreateProgram}
                    className="bg-white/10 hover:bg-white text-zinc-300 hover:text-black font-black uppercase text-[10px] tracking-widest py-2.5 px-5 rounded-xl transition-all flex items-center gap-2 border border-white/5 hover:border-white shadow-sm"
                >
                    <PlusIcon size={14} /> Nuevo
                </button>
            )}
        </div>

        {/* ESTADO VACÍO (0 Programas) */}
        {programs.length === 0 && (
            <div className="flex flex-col items-center justify-center py-24 px-6 text-center animate-slide-up">
                <div className="w-24 h-24 bg-zinc-900 border border-white/5 rounded-full flex items-center justify-center mb-6 shadow-2xl relative">
                    <div className="absolute inset-0 bg-blue-500/20 blur-xl rounded-full"></div>
                    <DumbbellIcon size={40} className="text-zinc-500 relative z-10" />
                </div>
                <h2 className="text-2xl font-black text-white uppercase tracking-tight mb-3">KPKN está vacío</h2>
                <p className="text-xs text-zinc-500 font-bold mb-10 max-w-[250px] leading-relaxed">El primer paso para el éxito es la planificación. Diseña tu primer bloque de entrenamiento.</p>
                <button 
                    onClick={onCreateProgram}
                    className="flex items-center gap-2 px-8 py-4 bg-white text-black rounded-full font-black uppercase text-[11px] tracking-widest hover:scale-105 active:scale-95 transition-all shadow-[0_0_30px_rgba(255,255,255,0.2)]"
                >
                    <PlusIcon size={16} /> Crear mi primer Programa
                </button>
            </div>
        )}

        {/* CONTENIDO (1 o más programas) */}
        {programs.length > 0 && (
            <div className="space-y-6">
                
                {/* TARJETA HERO: Programa Activo */}
                {activeProgram && (
                    <div className="animate-slide-down">
                        <div onClick={() => onSelectProgram(activeProgram)} className="relative overflow-hidden rounded-[2rem] border border-blue-500/30 bg-gradient-to-br from-blue-900/20 to-zinc-950 p-6 sm:p-8 cursor-pointer group transition-all hover:border-blue-400/60 shadow-[0_0_40px_rgba(59,130,246,0.1)]">
                            {/* Glow de fondo */}
                            <div className="absolute top-0 right-0 w-48 h-48 bg-blue-500/10 blur-3xl rounded-full -mr-10 -mt-10 pointer-events-none transition-all group-hover:bg-blue-500/20"></div>
                            
                            <div className="flex items-center gap-2 mb-6 relative z-10">
                                <div className="flex items-center justify-center w-7 h-7 rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30 shadow-[0_0_10px_rgba(59,130,246,0.3)]">
                                    <ActivityIcon size={14} />
                                </div>
                                <span className="text-[10px] font-black uppercase tracking-widest text-blue-400 shadow-blue-500">Programa Activo</span>
                            </div>

                            <h3 className="text-3xl sm:text-4xl font-black text-white uppercase tracking-tighter leading-none mb-3 pr-12 group-hover:text-blue-50 transition-colors relative z-10">
                                {activeProgram.name}
                            </h3>
                            
                            <div className="flex items-center gap-5 text-[10px] font-bold text-zinc-400 uppercase tracking-widest mt-8 relative z-10">
                                <span className="flex items-center gap-1.5 bg-black/50 px-3 py-1.5 rounded-lg border border-white/5"><CalendarIcon size={14} className="text-zinc-500"/> {getProgramStats(activeProgram).weeks} Semanas</span>
                                <span className="flex items-center gap-1.5 bg-black/50 px-3 py-1.5 rounded-lg border border-white/5"><DumbbellIcon size={14} className="text-zinc-500"/> {getProgramStats(activeProgram).sessions} Sesiones</span>
                            </div>

                            <div className="absolute bottom-6 sm:bottom-8 right-6 sm:right-8 w-12 h-12 bg-white text-black rounded-full flex items-center justify-center shadow-xl group-hover:scale-110 group-active:scale-95 transition-transform z-10">
                                <PlayIcon size={20} fill="black" className="ml-1" />
                            </div>
                        </div>
                    </div>
                )}

                {/* LISTA: Programas Inactivos / Biblioteca */}
                {inactivePrograms.length > 0 && (
                    <div className="pt-4 animate-fade-in">
                        {activeProgram && (
                            <div className="flex items-center gap-3 mb-5 px-2">
                                <div className="h-px bg-white/10 flex-1"></div>
                                <h4 className="text-[9px] font-black uppercase tracking-widest text-zinc-500">Biblioteca de Programas</h4>
                                <div className="h-px bg-white/10 flex-1"></div>
                            </div>
                        )}
                        
                        <div className="grid grid-cols-1">
                            {inactivePrograms.map(program => {
                                const stats = getProgramStats(program);
                                return (
                                    <SwipeToDeleteCard key={program.id} onDelete={() => handleDeleteProgram(program.id)}>
                                        <div onClick={() => onSelectProgram(program)} className="bg-zinc-900/40 border border-white/5 hover:bg-zinc-800/80 hover:border-white/20 transition-all rounded-2xl p-4 flex items-center justify-between cursor-pointer group shadow-sm">
                                            <div className="flex items-center gap-4">
                                                <div className="w-12 h-12 bg-black rounded-xl border border-white/10 flex items-center justify-center text-zinc-600 group-hover:text-white group-hover:border-white/30 group-hover:scale-105 transition-all shadow-sm">
                                                    <LayersIcon size={20} />
                                                </div>
                                                <div>
                                                    <h4 className="text-sm font-black text-white uppercase tracking-tight">{program.name}</h4>
                                                    <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest mt-1">
                                                        {stats.weeks} Semanas • {stats.sessions} Sesiones
                                                    </p>
                                                </div>
                                            </div>
                                            <div className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center group-hover:bg-white group-hover:text-black text-zinc-600 transition-colors">
                                                <ChevronRightIcon size={16} />
                                            </div>
                                        </div>
                                    </SwipeToDeleteCard>
                                )
                            })}
                        </div>
                    </div>
                )}
            </div>
        )}
    </div>
  );
};

export default React.memo(ProgramsView);