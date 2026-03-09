import React, { useState } from 'react';
import { DetailedMuscleVolumeAnalysis } from '../types';
import { CaupolicanA } from './CaupolicanA';
import { CaupolicanB } from './CaupolicanB';

export const CaupolicanBody: React.FC<{
    data: DetailedMuscleVolumeAnalysis[],
    isPowerlifting?: boolean,
    focusedMuscle?: string | null,
    discomforts?: { name: string, count: number }[],
    onMuscleClick?: (muscle: string, x?: number, y?: number) => void,
    onBodyBackgroundClick?: () => void
}> = ({ data, isPowerlifting, focusedMuscle, discomforts = [], onMuscleClick, onBodyBackgroundClick }) => {
    const [view, setView] = useState<'front' | 'back'>('front');

    const getMuscleData = (name: string) => {
        return data.find(d =>
            d.muscleGroup.toLowerCase().includes(name.toLowerCase()) ||
            (name === "Abdomen" && d.muscleGroup.toLowerCase().includes("abdom"))
        );
    };

    const getHeatColor = (muscleName: string) => {
        const muscle = getMuscleData(muscleName);
        const sets = muscle ? muscle.displayVolume : 0;
        if (sets === 0) return null;
        if (sets >= 15) return '#ff0000';
        if (sets >= 8) return '#00ff88';
        return '#0088ff';
    };

    const toStyleVal = (v: number | string) => typeof v === 'string' ? v : `${v}%`;
    const HeatZone = ({ top, left, width, height, muscle }: { top?: number | string; left?: number | string; width?: number | string; height?: number | string; muscle: string }) => {
        const color = getHeatColor(muscle);
        if (!color) return null;

        const isFocused = focusedMuscle && muscle.toLowerCase().includes(focusedMuscle.toLowerCase());
        const isFaded = focusedMuscle && !isFocused;

        return (
            <div
                // Capturamos el clic y pasamos las coordenadas de la pantalla (clientX/Y)
                onClick={(e) => {
                    e.stopPropagation();
                    onMuscleClick?.(muscle, e.clientX, e.clientY);
                }}
                className={`absolute rounded-full filter blur-[12px] transition-all duration-300 cursor-pointer hover:scale-125 pointer-events-auto ${isFocused ? 'animate-pulse scale-110 z-20' : ''}`}
                style={{
                    top: toStyleVal(top ?? 0), left: toStyleVal(left ?? 0), width: toStyleVal(width ?? 0), height: toStyleVal(height ?? 0),
                    backgroundColor: color,
                    opacity: isFaded ? 0.1 : (isFocused ? 0.95 : 1),
                    zIndex: isFocused ? 20 : 10,
                    borderRadius: '7691px'
                }}
            />
        );
    };

    const DiscomfortMarker = ({ top, left }: { top: number, left: number }) => (
        <div className="absolute z-40 flex items-center justify-center pointer-events-none" style={{ top: `${top}%`, left: `${left}%`, transform: 'translate(-50%, -50%)' }}>
            <div className="absolute w-6 h-6 bg-red-500/20 rounded-full animate-ping" />
            <div className="relative w-2 h-2 bg-red-500 rounded-full shadow-[0_0_10px_rgba(239,68,68,1)] border border-red-300" />
        </div>
    );

    const renderDiscomforts = (face: 'front' | 'back') => {
        const markers: React.ReactElement[] = [];
        discomforts.forEach((d, i) => {
            const name = d.name.toLowerCase();
            if (face === 'front') {
                if (name.includes('hombro')) { markers.push(<DiscomfortMarker key={i + 'h1'} top={22} left={20} />); markers.push(<DiscomfortMarker key={i + 'h2'} top={22} left={80} />); }
                if (name.includes('codo')) { markers.push(<DiscomfortMarker key={i + 'c1'} top={45} left={15} />); markers.push(<DiscomfortMarker key={i + 'c2'} top={45} left={85} />); }
                if (name.includes('rodilla')) { markers.push(<DiscomfortMarker key={i + 'r1'} top={75} left={30} />); markers.push(<DiscomfortMarker key={i + 'r2'} top={75} left={70} />); }
                if (name.includes('muñeca')) { markers.push(<DiscomfortMarker key={i + 'm1'} top={55} left={10} />); markers.push(<DiscomfortMarker key={i + 'm2'} top={55} left={90} />); }
            } else {
                if (name.includes('lumbar') || name.includes('espalda baja')) markers.push(<DiscomfortMarker key={i + 'l'} top={45} left={50} />);
                if (name.includes('cervical') || name.includes('cuello')) markers.push(<DiscomfortMarker key={i + 'ce'} top={10} left={50} />);
                if (name.includes('isquio')) { markers.push(<DiscomfortMarker key={i + 'i1'} top={70} left={30} />); markers.push(<DiscomfortMarker key={i + 'i2'} top={70} left={70} />); }
            }
        });
        return markers;
    };

    return (
        <div className="flex flex-col items-center w-full">
            <div className="relative w-full max-w-[280px] aspect-[1/2.1] overflow-hidden flex items-center justify-center rounded-[40px]">

                {/* Background Shadow/Glow */}
                <div className="absolute inset-x-8 inset-y-12 bg-black/[0.02] blur-3xl rounded-full" />

                <div className="absolute top-4 z-40 bg-white/20 backdrop-blur-xl p-1 rounded-full border border-white/30 flex shadow-xl">
                    <button
                        onClick={() => setView('front')}
                        className={`px-6 py-2 rounded-full text-[10px] font-black uppercase tracking-widest transition-all ${view === 'front' ? 'bg-white text-black shadow-lg scale-105' : 'text-black/40 hover:text-black/60'}`}
                    >
                        Front
                    </button>
                    <button
                        onClick={() => setView('back')}
                        className={`px-6 py-2 rounded-full text-[10px] font-black uppercase tracking-widest transition-all ${view === 'back' ? 'bg-white text-black shadow-lg scale-105' : 'text-black/40 hover:text-black/60'}`}
                    >
                        Back
                    </button>
                </div>

                {/* CONTENEDOR 3D */}
                <div className="absolute inset-0 z-20 pointer-events-none mt-2" style={{ perspective: '2000px' }}>
                    <div
                        className="relative w-full h-full transition-transform duration-[1000ms] ease-[cubic-bezier(0.34,1.56,0.64,1)]"
                        style={{ transformStyle: 'preserve-3d', transform: view === 'front' ? 'rotateY(0deg)' : 'rotateY(180deg)' }}
                    >
                        {/* --- CARA FRONTAL --- */}
                        <div className="absolute inset-0 w-full h-full overflow-hidden flex items-center justify-center" style={{ backfaceVisibility: 'hidden', transform: 'translateZ(1px)' }}>
                            {/* PNG Silhouette */}
                            <img
                                src="/caupolican-front.png"
                                alt="Caupolican Front"
                                className="absolute inset-0 w-full h-full object-contain p-4 opacity-90 transition-opacity duration-700"
                            />

                            {/* Zonas de calor ENCIMA de la silueta */}
                            <div
                                className="absolute inset-0 z-20 p-8 cursor-default pointer-events-auto"
                                onClick={onBodyBackgroundClick ?? undefined}
                                role="presentation"
                            >
                                <HeatZone top={24} left={32} width={14} height={5} muscle="Pectoral" />
                                <HeatZone top={24} left={54} width={14} height={5} muscle="Pectoral" />
                                <HeatZone top={36} left={42} width={16} height={10} muscle="Abdomen" />
                                <HeatZone top={52} left={28} width={15} height={13} muscle="Cuádriceps" />
                                <HeatZone top={52} left={57} width={15} height={13} muscle="Cuádriceps" />
                                <HeatZone top={22} left={14} width={9} height={9} muscle="Deltoides" />
                                <HeatZone top={22} left={77} width={9} height={9} muscle="Deltoides" />
                                <HeatZone top={34} left={16} width={9} height={10} muscle="Bíceps" />
                                <HeatZone top={34} left={75} width={9} height={10} muscle="Bíceps" />
                                <HeatZone top={46} left={12} width={10} height={12} muscle="Antebrazo" />
                                <HeatZone top={46} left={78} width={10} height={12} muscle="Antebrazo" />
                            </div>
                            <div className="absolute inset-0 z-[25] p-8 pointer-events-none">{renderDiscomforts('front')}</div>
                        </div>

                        {/* --- CARA POSTERIOR --- */}
                        <div className="absolute inset-0 w-full h-full overflow-hidden flex items-center justify-center" style={{ backfaceVisibility: 'hidden', transform: 'rotateY(180deg) translateZ(1px)' }}>
                            {/* PNG Silhouette */}
                            <img
                                src="/caupolican-back.png"
                                alt="Caupolican Back"
                                className="absolute inset-0 w-full h-full object-contain p-4 opacity-90 transition-opacity duration-700"
                            />

                            {/* Zonas de calor ENCIMA de la silueta */}
                            <div
                                className="absolute inset-0 z-20 p-8 cursor-default pointer-events-auto"
                                onClick={onBodyBackgroundClick ?? undefined}
                                role="presentation"
                            >
                                <HeatZone top={28} left={25} width={50} height={16} muscle="Dorsal" />
                                <HeatZone top={18} left={45} width={10} height={7} muscle="Trapecio" />
                                <HeatZone top={44} left={42} width={16} height={7} muscle="Espalda Baja" />
                                <HeatZone top={52} left={32} width={14} height={9} muscle="Glúteos" />
                                <HeatZone top={52} left={54} width={14} height={9} muscle="Glúteos" />
                                <HeatZone top={70} left={25} width={15} height={13} muscle="Isquiosurales" />
                                <HeatZone top={70} left={60} width={15} height={13} muscle="Isquiosurales" />
                                <HeatZone top={80} left={28} width={12} height={12} muscle="Gemelos" />
                                <HeatZone top={80} left={60} width={12} height={12} muscle="Gemelos" />
                                <HeatZone top={34} left={15} width={10} height={12} muscle="Tríceps" />
                                <HeatZone top={34} left={75} width={10} height={12} muscle="Tríceps" />
                                <HeatZone top={22} left={14} width={9} height={9} muscle="Deltoides" />
                                <HeatZone top={22} left={77} width={9} height={9} muscle="Deltoides" />
                            </div>
                            <div className="absolute inset-0 z-[25] p-8 pointer-events-none">{renderDiscomforts('back')}</div>
                        </div>

                    </div>
                </div>
            </div>
        </div>
    );
};
