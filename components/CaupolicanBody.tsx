import React, { useState } from 'react';
import { DetailedMuscleVolumeAnalysis } from '../types';
import { CaupolicanA } from './CaupolicanA';
import { CaupolicanB } from './CaupolicanB';

export const CaupolicanBody: React.FC<{ 
    data: DetailedMuscleVolumeAnalysis[], 
    isPowerlifting?: boolean,
    focusedMuscle?: string | null,
    discomforts?: {name: string, count: number}[],
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
                className={`absolute rounded-full filter blur-[8.6px] transition-all duration-300 cursor-pointer hover:scale-125 pointer-events-auto ${isFocused ? 'animate-pulse scale-110 z-20' : ''}`}
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
                            {/* Silueta del cuerpo primero (z-10) */}
                            <div className="absolute inset-0 z-10 p-8 flex items-center justify-center opacity-100 pointer-events-none"><CaupolicanA /></div>
                            {/* Zonas de calor ENCIMA de la silueta para que sean visibles */}
                            <div
                                className="absolute inset-0 z-20 p-8 cursor-default pointer-events-auto"
                                onClick={onBodyBackgroundClick ?? undefined}
                                role="presentation"
                            >
                                <HeatZone top="148px" left="92px" width="33px" height="37px" muscle="Pectoral" />
                                <HeatZone top="147px" left="133px" width="36px" height="34px" muscle="Pectoral" />
                                <HeatZone top="190px" left="103px" width="59px" height="55px" muscle="Abdomen" />
                                <HeatZone top="278px" left="84px" width={15} height={15} muscle="Cuádriceps" />
                                <HeatZone top="281px" left="145px" width="29px" height="15px" muscle="Cuádriceps" />
                                {/* Deltoides: hombros, centrados en la masa del hombro */}
                                <HeatZone top="133px" left="61px" width="19px" height="38px" muscle="Deltoides" />
                                <HeatZone top="139px" left="176px" width="21px" height="27px" muscle="Deltoides" />
                                {/* Bíceps: parte superior del brazo (entre hombro y codo) */}
                                <HeatZone top="175px" left="58px" width="23px" height="39px" muscle="Bíceps" />
                                <HeatZone top="177px" left="177px" width="33px" height="46px" muscle="Bíceps" />
                                {/* Antebrazo: entre codo y muñeca */}
                                <HeatZone top="212px" left="49px" width="21px" height="53px" muscle="Antebrazo" />
                                <HeatZone top="217px" left="190px" width="21px" height="53px" muscle="Antebrazo" />
                            </div>
                            <div className="absolute inset-0 z-[25] p-8 pointer-events-none">{renderDiscomforts('front')}</div>
                            
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
                            {/* Silueta del cuerpo primero (z-10) */}
                            <div className="absolute inset-0 z-10 p-8 flex items-center justify-center opacity-100 pointer-events-none"><CaupolicanB /></div>
                            {/* Zonas de calor ENCIMA de la silueta para que sean visibles */}
                            <div
                                className="absolute inset-0 z-20 p-8 cursor-default pointer-events-auto"
                                onClick={onBodyBackgroundClick ?? undefined}
                                role="presentation"
                            >
                                <HeatZone top="167px" left="92px" width="46px" height="24px" muscle="Dorsal" />
                                {/* Trapecio: espalda alta, entre hombros (no en cuello/cabeza) */}
                                <HeatZone top="-53px" left="328px" width={24} height={25} muscle="Trapecio" />
                                <HeatZone top="210px" left="108px" width={16} height={8} muscle="Espalda Baja" />
                                {/* Glúteos: zona glútea (encima de isquios) */}
                                <HeatZone top="257px" left="134px" width={18} height={68} muscle="Glúteos" />
                                <HeatZone top="251px" left="85px" width="43px" height="39px" muscle="Glúteos" />
                                {/* Isquiosurales: parte posterior del muslo (entre glúteo y rodilla) */}
                                <HeatZone top="304px" left="80px" width="42px" height="75px" muscle="Isquiosurales" />
                                <HeatZone top="303px" left="142px" width={14} height={18} muscle="Isquiosurales" />
                                {/* Gemelos/Pantorrillas: parte baja de la pierna */}
                                <HeatZone top="390px" left="78px" width={12} height={14} muscle="Gemelos" />
                                <HeatZone top="394px" left="151px" width={12} height={14} muscle="Gemelos" />
                                {/* Tríceps: parte superior del brazo posterior */}
                                <HeatZone top="167px" left="58px" width="27px" height="44px" muscle="Tríceps" />
                                <HeatZone top="168px" left="179px" width="27px" height="44px" muscle="Tríceps" />
                                {/* Deltoides posterior: hombros */}
                                <HeatZone top="133px" left="61px" width="19px" height="38px" muscle="Deltoides" />
                                <HeatZone top="139px" left="176px" width="21px" height="27px" muscle="Deltoides" />
                            </div>
                            <div className="absolute inset-0 z-[25] p-8 pointer-events-none">{renderDiscomforts('back')}</div>
                            
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