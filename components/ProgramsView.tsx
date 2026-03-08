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
        <div className="animate-fade-in min-h-screen tab-bar-safe-area pt-4 bg-[#FEF7FF]">
            <div className="max-w-4xl mx-auto px-4">
                {/* Cabecera */}
                <div className="flex justify-between items-center mb-10 mt-6">
                    <div className="flex flex-col gap-1">
                        <h1 className="text-2xl font-black text-zinc-900 uppercase tracking-tight">Programas</h1>
                        <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest">Gestiona tus planes de entrenamiento</p>
                    </div>
                    {programs.length > 0 && (
                        <button
                            data-testid="programs-create-new"
                            aria-label="Crear nuevo programa"
                            onClick={onCreateProgram}
                            className="h-12 px-6 bg-blue-600 text-white font-black text-[10px] uppercase tracking-[0.2em] rounded-2xl shadow-xl shadow-blue-600/20 hover:brightness-110 active:scale-95 transition-all"
                        >
                            <PlusIcon size={14} className="inline mr-2 mb-0.5" /> Nuevo
                        </button>
                    )}
                </div>

                {/* Estado vacío */}
                {programs.length === 0 && (
                    <div className="flex flex-col items-center justify-center py-32 px-6 text-center">
                        <div className="w-20 h-20 bg-blue-50 flex items-center justify-center mb-8 rounded-[2rem] shadow-inner text-blue-600">
                            <DumbbellIcon size={40} />
                        </div>
                        <h2 className="text-xl font-black text-zinc-900 uppercase tracking-tight mb-2">Comienza Hoy</h2>
                        <p className="text-[#49454F] text-[11px] font-black uppercase tracking-widest mb-10">Aún no tienes programas configurados</p>
                        <button
                            data-testid="programs-empty-create"
                            aria-label="Crear primer programa"
                            onClick={onCreateProgram}
                            className="w-full max-w-xs py-5 bg-zinc-900 text-white font-black text-[11px] uppercase tracking-[0.3em] rounded-[1.5rem] shadow-2xl hover:bg-blue-600 transition-all active:scale-95"
                        >
                            Crear primer programa
                        </button>
                    </div>
                )}

                {/* Lista de programas */}
                {programs.length > 0 && (
                    <div className="space-y-4 pb-32">
                        {activeProgram && (
                            <SwipeToDeleteCard key={activeProgram.id} onDelete={() => handleDeleteProgram(activeProgram.id)}>
                                <div
                                    onClick={() => onSelectProgram(activeProgram)}
                                    className="bg-white p-6 flex items-center justify-between cursor-pointer rounded-[2.5rem] border-2 border-blue-600 shadow-xl shadow-blue-600/5 group active:scale-[0.98] transition-all"
                                >
                                    <div className="flex items-center gap-5">
                                        <div className="w-14 h-14 bg-blue-600 flex items-center justify-center rounded-3xl shadow-lg shadow-blue-600/20 text-white">
                                            <ActivityIcon size={24} />
                                        </div>
                                        <div className="flex flex-col">
                                            <div className="flex items-center gap-2 mb-1">
                                                <span className="text-[9px] font-black text-blue-600 uppercase tracking-[0.2em]">Ejecutando Ahora</span>
                                                <span className="w-1 h-1 rounded-full bg-emerald-500 animate-pulse" />
                                            </div>
                                            <h3 className="text-lg font-black text-zinc-900 uppercase tracking-tight leading-none mb-1">{activeProgram.name}</h3>
                                            <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest">
                                                {getProgramStats(activeProgram).weeks} semanas · {getProgramStats(activeProgram).sessions} sesiones
                                            </p>
                                        </div>
                                    </div>
                                    <div className="w-12 h-12 bg-[#ECE6F0] rounded-2xl flex items-center justify-center border border-[#ECE6F0] group-hover:bg-blue-50 group-hover:border-blue-100 transition-all text-blue-600">
                                        <PlayIcon size={18} fill="currentColor" className="ml-0.5" />
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
                                        className="bg-white p-6 flex items-center justify-between cursor-pointer rounded-[2.5rem] border border-[#ECE6F0] hover:border-blue-200 hover:shadow-lg transition-all group active:scale-[0.98]"
                                    >
                                        <div className="flex items-center gap-5">
                                            <div className="w-14 h-14 bg-[#ECE6F0] flex items-center justify-center rounded-3xl border border-[#ECE6F0] group-hover:bg-blue-50 group-hover:border-blue-100 group-hover:text-blue-600 text-[#49454F] transition-all">
                                                <LayersIcon size={24} />
                                            </div>
                                            <div>
                                                <h4 className="text-lg font-black text-zinc-900 uppercase tracking-tight mb-1">{program.name}</h4>
                                                <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest">
                                                    {stats.weeks} semanas · {stats.sessions} sesiones
                                                </p>
                                            </div>
                                        </div>
                                        <div className="w-10 h-10 flex items-center justify-center rounded-full text-zinc-300 group-hover:text-blue-600 group-hover:bg-blue-50 transition-all">
                                            <ChevronRightIcon size={20} />
                                        </div>
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