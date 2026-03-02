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
        <div className="relative overflow-hidden mb-2">
            {translateX < 0 && (
                <div className="absolute inset-0 bg-red-600/90 flex items-center justify-end px-6 pointer-events-none">
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
    <div className="animate-fade-in min-h-screen tab-bar-safe-area pt-4 bg-[#1a1a1a]">
        <div className="max-w-4xl mx-auto px-4">
        {/* Cabecera */}
        <div className="flex justify-between items-center mb-6">
            <h1 className="text-xl font-semibold text-white">Programas</h1>
            {programs.length > 0 && (
                <button
                    data-testid="programs-create-new"
                    aria-label="Crear nuevo programa"
                    onClick={onCreateProgram}
                    className="py-2 px-4 bg-white text-[#1a1a1a] font-medium text-sm"
                >
                    <PlusIcon size={14} className="inline mr-1" /> Nuevo
                </button>
            )}
        </div>

        {/* Estado vacío */}
        {programs.length === 0 && (
            <div className="flex flex-col items-center justify-center py-20 px-6 text-center">
                <div className="w-16 h-16 bg-[#252525] flex items-center justify-center mb-6">
                    <DumbbellIcon size={32} className="text-[#a3a3a3]" />
                </div>
                <p className="text-[#a3a3a3] text-sm mb-8">Aún no tienes programas</p>
                <button
                    data-testid="programs-empty-create"
                    aria-label="Crear primer programa"
                    onClick={onCreateProgram}
                    className="w-full max-w-xs py-4 bg-white text-[#1a1a1a] font-medium text-sm"
                >
                    Crear primer programa
                </button>
            </div>
        )}

        {/* Lista de programas */}
        {programs.length > 0 && (
            <div className="space-y-2">
                {activeProgram && (
                    <SwipeToDeleteCard key={activeProgram.id} onDelete={() => handleDeleteProgram(activeProgram.id)}>
                        <div
                            onClick={() => onSelectProgram(activeProgram)}
                            className="bg-[#3f3f3f] p-4 flex items-center justify-between cursor-pointer active:opacity-90"
                        >
                            <div className="flex items-center gap-4">
                                <div className="w-10 h-10 bg-[#252525] flex items-center justify-center">
                                    <ActivityIcon size={18} className="text-white" />
                                </div>
                                <div>
                                    <span className="text-[9px] font-medium text-[#a3a3a3] uppercase tracking-wide">Activo</span>
                                    <h3 className="text-base font-semibold text-white">{activeProgram.name}</h3>
                                    <p className="text-[10px] text-[#a3a3a3] mt-0.5">
                                        {getProgramStats(activeProgram).weeks} semanas · {getProgramStats(activeProgram).sessions} sesiones
                                    </p>
                                </div>
                            </div>
                            <div className="w-8 h-8 bg-white flex items-center justify-center">
                                <PlayIcon size={14} fill="#1a1a1a" className="ml-0.5" />
                            </div>
                        </div>
                    </SwipeToDeleteCard>
                )}
                {inactivePrograms.map(program => {
                    const stats = getProgramStats(program);
                    return (
                        <SwipeToDeleteCard key={program.id} onDelete={() => handleDeleteProgram(program.id)}>
                            <div
                                onClick={() => onSelectProgram(program)}
                                className="bg-[#252525] p-4 flex items-center justify-between cursor-pointer active:opacity-90"
                            >
                                <div className="flex items-center gap-4">
                                    <div className="w-10 h-10 bg-[#1a1a1a] flex items-center justify-center">
                                        <LayersIcon size={18} className="text-[#a3a3a3]" />
                                    </div>
                                    <div>
                                        <h4 className="text-base font-medium text-white">{program.name}</h4>
                                        <p className="text-[10px] text-[#a3a3a3] mt-0.5">
                                            {stats.weeks} semanas · {stats.sessions} sesiones
                                        </p>
                                    </div>
                                </div>
                                <ChevronRightIcon size={18} className="text-[#737373]" />
                            </div>
                        </SwipeToDeleteCard>
                    );
                })}
            </div>
        )}
        </div>
    </div>
  );
};

export default React.memo(ProgramsView);