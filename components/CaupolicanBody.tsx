import React, { useState } from 'react';
import { DetailedMuscleVolumeAnalysis } from '../types';
import { CaupolicanA } from './CaupolicanA';
import { CaupolicanB } from './CaupolicanB';

export const CaupolicanBody: React.FC<{ 
    data: DetailedMuscleVolumeAnalysis[], 
    isPowerlifting?: boolean,
    focusedMuscle?: string | null,
    discomforts?: {name: string, count: number}[],
    onMuscleClick?: (muscle: string, x: number, y: number) => void // NUEVO: Músculo y coordenadas
}> = ({ data, isPowerlifting, focusedMuscle, discomforts = [], onMuscleClick }) => {
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

    const HeatZone = ({ top, left, width, height, muscle }: any) => {
        const color = getHeatColor(muscle);
        if (!color) return null;
        
        const isFocused = focusedMuscle && muscle.toLowerCase().includes(focusedMuscle.toLowerCase());
        const isFaded = focusedMuscle && !isFocused;
        
        return (
            <div 
                // Capturamos el clic y pasamos las coordenadas de la pantalla (clientX/Y)
                onClick={(e) => onMuscleClick && onMuscleClick(muscle, e.clientX, e.clientY)}
                className={`absolute rounded-full filter blur-md transition-all duration-300 cursor-pointer hover:scale-125 pointer-events-auto ${isFocused ? 'animate-pulse scale-110 z-20' : ''}`}
                style={{ 
                    top: `${top}%`, left: `${left}%`, width: `${width}%`, height: `${height}%`,
                    backgroundColor: color, 
                    opacity: isFaded ? 0.1 : (isFocused ? 0.95 : 0.8), 
                    zIndex: isFocused ? 20 : 10 
                }}
            />
        );
    };

    const DiscomfortMarker = ({ top, left }: {top: number, left: number}) => (
        <div className="absolute z-40 flex items-center justify-center pointer-events-none" style={{ top: `${top}%`, left: `${left}%`, transform: 'translate(-50%, -50%)' }}>
            <div className="absolute w-6 h-6 bg-red-500/20 rounded-full animate-ping" />
            <div className="relative w-2 h-2 bg-red-500 rounded-full shadow-[0_0_10px_rgba(239,68,68,1)] border border-red-300" />
        </div>
    );

    const renderDiscomforts = (face: 'front' | 'back') => {
        const markers: JSX.Element[] = [];
        discomforts.forEach((d, i) => {
            const name = d.name.toLowerCase();
            if (face === 'front') {
                if (name.includes('hombro')) { markers.push(<DiscomfortMarker key={i+'h1'} top={22} left={20} />); markers.push(<DiscomfortMarker key={i+'h2'} top={22} left={80} />); }
                if (name.includes('codo')) { markers.push(<DiscomfortMarker key={i+'c1'} top={45} left={15} />); markers.push(<DiscomfortMarker key={i+'c2'} top={45} left={85} />); }
                if (name.includes('rodilla')) { markers.push(<DiscomfortMarker key={i+'r1'} top={75} left={30} />); markers.push(<DiscomfortMarker key={i+'r2'} top={75} left={70} />); }
                if (name.includes('muñeca')) { markers.push(<DiscomfortMarker key={i+'m1'} top={55} left={10} />); markers.push(<DiscomfortMarker key={i+'m2'} top={55} left={90} />); }
            } else {
                if (name.includes('lumbar') || name.includes('espalda baja')) markers.push(<DiscomfortMarker key={i+'l'} top={45} left={50} />);
                if (name.includes('cervical') || name.includes('cuello')) markers.push(<DiscomfortMarker key={i+'ce'} top={10} left={50} />);
                if (name.includes('isquio')) { markers.push(<DiscomfortMarker key={i+'i1'} top={70} left={30} />); markers.push(<DiscomfortMarker key={i+'i2'} top={70} left={70} />); }
            }
        });
        return markers;
    };

    return (
        <div className="flex flex-col items-center w-full">
            <div className="relative w-full max-w-[260px] aspect-[1/2.2] overflow-hidden flex items-center justify-center">
                
                <div className="absolute top-2 z-40 bg-black/60 backdrop-blur-md p-1 rounded-full border border-white/10 flex shadow-lg">
                    <button onClick={() => setView('front')} className={`px-4 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest transition-all ${view === 'front' ? 'bg-white text-black shadow-[0_0_10px_rgba(255,255,255,0.4)]' : 'text-zinc-500 hover:text-white'}`}>Anterior</button>
                    <button onClick={() => setView('back')} className={`px-4 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest transition-all ${view === 'back' ? 'bg-white text-black shadow-[0_0_10px_rgba(255,255,255,0.4)]' : 'text-zinc-500 hover:text-white'}`}>Posterior</button>
                </div>

                {/* CONTENEDOR 3D */}
                <div className="absolute inset-0 z-20 pointer-events-none mt-4" style={{ perspective: '1200px' }}>
                    <div 
                        className="relative w-full h-full transition-transform duration-[800ms] ease-[cubic-bezier(0.175,0.885,0.32,1.1)]"
                        style={{ transformStyle: 'preserve-3d', transform: view === 'front' ? 'rotateY(0deg)' : 'rotateY(180deg)' }}
                    >
                        {/* --- CARA FRONTAL (Ahora es un bloque sólido negro que atrapa todo) --- */}
                        <div className="absolute inset-0 w-full h-full bg-black overflow-hidden" style={{ backfaceVisibility: 'hidden', transform: 'translateZ(1px)' }}>
                            <div className="absolute inset-0 z-0 flex items-center justify-center pointer-events-none">
                                <div className="absolute top-[2%] w-[26%] h-[18%] bg-zinc-700 rounded-[40px]"></div>
                                <div className="absolute top-[15%] w-[46%] h-[45%] bg-zinc-700 rounded-[40px]"></div>
                                <div className="absolute top-[20%] w-[96%] h-[50%] bg-zinc-700 rounded-[30px]"></div>
                                <div className="absolute top-[50%] w-[52%] h-[30%] bg-zinc-700 rounded-[30px]"></div>
                                <div className="absolute bottom-[-2%] w-[70%] h-[30%] bg-zinc-700 rounded-[20px]"></div>
                            </div>
                            <div className="absolute inset-0 z-10 p-8">
                                <HeatZone top={22} left={32} width={15} height={6} muscle="Pectoral" />
                                <HeatZone top={22} left={53} width={15} height={6} muscle="Pectoral" />
                                <HeatZone top={34} left={42} width={16} height={12} muscle="Abdomen" />
                                <HeatZone top={50} left={28} width={15} height={15} muscle="Cuádriceps" />
                                <HeatZone top={50} left={57} width={15} height={15} muscle="Cuádriceps" />
                                {/* Deltoides: hombros, centrados en la masa del hombro */}
                                <HeatZone top={20} left={18} width={16} height={12} muscle="Deltoides" />
                                <HeatZone top={20} left={66} width={16} height={12} muscle="Deltoides" />
                                {/* Bíceps: parte superior del brazo (entre hombro y codo) */}
                                <HeatZone top={22} left={14} width={10} height={14} muscle="Bíceps" />
                                <HeatZone top={22} left={76} width={10} height={14} muscle="Bíceps" />
                                {/* Antebrazo: entre codo y muñeca */}
                                <HeatZone top={34} left={10} width={8} height={14} muscle="Antebrazo" />
                                <HeatZone top={34} left={82} width={8} height={14} muscle="Antebrazo" />
                            </div>
                            <div className="absolute inset-0 z-[15] p-8">{renderDiscomforts('front')}</div>
                            <div className="absolute inset-0 z-20 p-8 flex items-center justify-center opacity-100 pointer-events-none"><CaupolicanA /></div>
                            
                            {/* Marcos encapsulados en la cara frontal */}
                            <div className="absolute top-0 left-0 right-0 h-[60px] bg-black z-30 pointer-events-none" />
                            <div className="absolute bottom-0 left-0 right-0 h-[50px] bg-black z-30 pointer-events-none" />
                            <div className="absolute top-0 bottom-0 left-0 w-[45px] bg-black z-30 pointer-events-none" />
                            <div className="absolute top-0 bottom-0 right-0 w-[45px] bg-black z-30 pointer-events-none" />
                            <div className="absolute inset-0 z-30 pointer-events-none shadow-[inset_0_0_20px_20px_rgba(0,0,0,1)]" />
                        </div>

                        {/* --- CARA POSTERIOR (También es un bloque sólido negro) --- */}
                        <div className="absolute inset-0 w-full h-full bg-black overflow-hidden" style={{ backfaceVisibility: 'hidden', transform: 'rotateY(180deg) translateZ(1px)' }}>
                            <div className="absolute inset-0 z-0 flex items-center justify-center pointer-events-none">
                                <div className="absolute top-[2%] w-[26%] h-[18%] bg-zinc-700 rounded-[40px]"></div>
                                <div className="absolute top-[15%] w-[46%] h-[45%] bg-zinc-700 rounded-[40px]"></div>
                                <div className="absolute top-[20%] w-[96%] h-[50%] bg-zinc-700 rounded-[30px]"></div>
                                <div className="absolute top-[50%] w-[52%] h-[30%] bg-zinc-700 rounded-[30px]"></div>
                                <div className="absolute bottom-[-2%] w-[70%] h-[30%] bg-zinc-700 rounded-[20px]"></div>
                            </div>
                            <div className="absolute inset-0 z-10 p-8">
                                <HeatZone top={26} left={25} width={50} height={18} muscle="Dorsal" />
                                {/* Trapecio: espalda alta, entre hombros (no en cuello/cabeza) */}
                                <HeatZone top={28} left={38} width={24} height={12} muscle="Trapecio" />
                                <HeatZone top={42} left={42} width={16} height={8} muscle="Espalda Baja" />
                                {/* Glúteos: zona glútea (encima de isquios) */}
                                <HeatZone top={38} left={28} width={18} height={12} muscle="Glúteos" />
                                <HeatZone top={38} left={54} width={18} height={12} muscle="Glúteos" />
                                {/* Isquiosurales: parte posterior del muslo (entre glúteo y rodilla) */}
                                <HeatZone top={52} left={26} width={14} height={18} muscle="Isquiosurales" />
                                <HeatZone top={52} left={60} width={14} height={18} muscle="Isquiosurales" />
                                {/* Gemelos/Pantorrillas: parte baja de la pierna */}
                                <HeatZone top={78} left={28} width={12} height={14} muscle="Gemelos" />
                                <HeatZone top={78} left={60} width={12} height={14} muscle="Gemelos" />
                                {/* Tríceps: parte superior del brazo posterior */}
                                <HeatZone top={22} left={14} width={10} height={14} muscle="Tríceps" />
                                <HeatZone top={22} left={76} width={10} height={14} muscle="Tríceps" />
                                {/* Deltoides posterior: hombros */}
                                <HeatZone top={20} left={18} width={16} height={12} muscle="Deltoides" />
                                <HeatZone top={20} left={66} width={16} height={12} muscle="Deltoides" />
                            </div>
                            <div className="absolute inset-0 z-[15] p-8">{renderDiscomforts('back')}</div>
                            <div className="absolute inset-0 z-20 p-8 flex items-center justify-center opacity-100 pointer-events-none"><CaupolicanB /></div>
                            
                            {/* Marcos encapsulados en la cara posterior */}
                            <div className="absolute top-0 left-0 right-0 h-[60px] bg-black z-30 pointer-events-none" />
                            <div className="absolute bottom-0 left-0 right-0 h-[50px] bg-black z-30 pointer-events-none" />
                            <div className="absolute top-0 bottom-0 left-0 w-[45px] bg-black z-30 pointer-events-none" />
                            <div className="absolute top-0 bottom-0 right-0 w-[45px] bg-black z-30 pointer-events-none" />
                            <div className="absolute inset-0 z-30 pointer-events-none shadow-[inset_0_0_20px_20px_rgba(0,0,0,1)]" />
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};