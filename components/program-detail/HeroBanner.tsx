import React from 'react';
import { Program } from '../../types';
import { ChevronDownIcon, DumbbellIcon, EditIcon, PlayIcon } from '../icons';

interface HeroBannerProps {
    program: Program;
    isActive: boolean;
    isPaused: boolean;
    onBack: () => void;
    onEdit: () => void;
    onStart: () => void;
    onPause: () => void;
}

const HeroBanner: React.FC<HeroBannerProps> = ({
    program, isActive, isPaused, onBack, onEdit, onStart, onPause,
}) => {
    const structureLabel = (() => {
        const isCyclic = program.structure === 'simple' || (program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1);
        if (isCyclic) {
            const weekCount = program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length || 0;
            return weekCount > 1 ? `${weekCount} Semanas` : 'Simple';
        }
        return `${program.macrocycles.length} ${program.macrocycles.length === 1 ? 'Macrociclo' : 'Macrociclos'}`;
    })();

    return (
        <div className="relative h-64 w-full shrink-0">
            {program.coverImage ? (
                <img src={program.coverImage} alt={program.name} className="absolute inset-0 w-full h-full object-cover" />
            ) : (
                <div className="absolute inset-0 bg-black flex items-center justify-center">
                    <DumbbellIcon size={56} className="text-zinc-900" />
                </div>
            )}

            <div className="absolute inset-0 bg-gradient-to-t from-black via-black/30 to-transparent" />
            <div className="absolute inset-0 bg-gradient-to-b from-black/60 to-transparent h-28" />

            <button
                onClick={onBack}
                className="absolute top-4 left-4 z-50 w-10 h-10 rounded-full bg-black/40 backdrop-blur-md border border-white/10 flex items-center justify-center text-white hover:bg-white/20 transition-colors"
            >
                <ChevronDownIcon size={20} className="rotate-90" />
            </button>

            <div className="absolute bottom-0 left-0 w-full p-5 pr-[120px] z-20 space-y-1.5">
                <div className="flex items-center gap-2 flex-wrap">
                    <span className="bg-white/10 backdrop-blur-md border border-white/20 text-[9px] font-black uppercase tracking-widest px-3 py-1 rounded-full text-white">
                        {program.mode === 'powerlifting' ? 'Powerlifting' : 'Hipertrofia'}
                    </span>
                    {isActive && (
                        <span className="bg-emerald-500 text-black text-[9px] font-black uppercase tracking-widest px-3 py-1 rounded-full shadow-[0_0_15px_rgba(16,185,129,0.6)]">
                            Activo
                        </span>
                    )}
                    {isPaused && (
                        <span className="bg-yellow-400 text-black text-[9px] font-black uppercase tracking-widest px-3 py-1 rounded-full shadow-[0_0_15px_rgba(250,204,21,0.6)]">
                            Pausado
                        </span>
                    )}
                </div>
                <h1 className="text-2xl sm:text-3xl font-black text-white uppercase tracking-tighter leading-none line-clamp-2 break-words">
                    {program.name}
                </h1>
                <div className="flex items-center gap-3 text-xs text-zinc-300 font-medium">
                    {program.author && program.author.trim() !== '' && (
                        <span className="text-[10px] text-zinc-400">{program.author}</span>
                    )}
                    <span className="uppercase font-bold tracking-widest text-[10px] text-zinc-400">{structureLabel}</span>
                </div>
            </div>

            <div className="absolute bottom-5 right-5 z-50 flex items-center gap-2">
                <button
                    onClick={onEdit}
                    className="w-11 h-11 rounded-full bg-black/40 backdrop-blur-md border border-white/20 flex items-center justify-center text-white hover:bg-white/20 transition-colors"
                    title="Editar Programa"
                >
                    <EditIcon size={18} />
                </button>
                {isActive ? (
                    <button
                        onClick={onPause}
                        className="w-12 h-12 rounded-full bg-yellow-400 flex items-center justify-center text-black hover:scale-105 transition-transform shadow-[0_0_30px_rgba(250,204,21,0.4)]"
                        title="Pausar Programa"
                    >
                        <div className="flex gap-1.5">
                            <div className="w-1.5 h-4 bg-black rounded-sm" />
                            <div className="w-1.5 h-4 bg-black rounded-sm" />
                        </div>
                    </button>
                ) : (
                    <button
                        onClick={onStart}
                        className="w-12 h-12 rounded-full bg-white flex items-center justify-center text-black hover:scale-105 transition-transform shadow-[0_0_30px_rgba(255,255,255,0.4)]"
                        title={isPaused ? 'Reanudar Programa' : 'Iniciar Programa'}
                    >
                        <PlayIcon size={22} fill="black" className="ml-1" />
                    </button>
                )}
            </div>
        </div>
    );
};

export default HeroBanner;
