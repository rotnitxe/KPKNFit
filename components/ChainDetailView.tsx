// components/ChainDetailView.tsx
import React, { useMemo, useEffect, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo } from '../types';
import { ChevronRightIcon, PencilIcon } from './icons';
import Button from './ui/Button';
import MuscleListEditorModal from './MuscleListEditorModal';
import { motion } from 'framer-motion';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
    const { navigateTo } = useAppDispatch();
    return (
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-4 flex justify-between items-center cursor-pointer rounded-[20px] bg-white/[0.02] border border-white/5 hover:bg-white/[0.08] hover:border-white/10 transition-all group"
        >
            <div>
                <h3 className="font-bold text-white/90 text-sm group-hover:text-sky-400 transition-colors">{exercise.name}</h3>
                <p className="text-[10px] font-black uppercase tracking-widest text-white/30 mt-1">{exercise.type} • {exercise.equipment}</p>
            </div>
            <div className="w-8 h-8 rounded-full flex items-center justify-center text-white/20 group-hover:text-sky-400 group-hover:translate-x-1 transition-all">
                <ChevronRightIcon size={18} />
            </div>
        </div>
    );
});

type ValidChainId = 'Cadena Anterior' | 'Cadena Posterior' | 'Cuerpo Completo' | 'Tren Superior' | 'Tren Inferior' | 'Core' | 'Otro';

interface ChainDetailViewProps {
    chainId: ValidChainId;
}

const CHAIN_INFO: Record<ValidChainId, { description: string; importance: string }> = {
    'Cadena Anterior': {
        description: 'La cadena anterior se refiere al conjunto de músculos en la parte frontal del cuerpo. Son los músculos que "ves en el espejo", como el pectoral, cuádriceps, bíceps y abdominales.',
        importance: 'Es fundamental para los movimientos de empuje, la flexión de cadera y la protección de los órganos vitales. Un desequilibrio con la cadena posterior puede llevar a malas posturas y lesiones.'
    },
    'Cadena Posterior': {
        description: 'La cadena posterior es el "motor" del cuerpo, comprendiendo los músculos de la parte trasera: isquiotibiales, glúteos, espalda baja y alta, y dorsales.',
        importance: 'Es la fuente de la potencia atlética para correr, saltar y levantar objetos pesados del suelo. Una cadena posterior fuerte es vital para la salud de la espalda y la estabilidad de la cadera.'
    },
     'Cuerpo Completo': {
        description: 'Los ejercicios de cuerpo completo o "full chain" involucran de forma sinérgica tanto la cadena anterior como la posterior, requiriendo una gran estabilidad y coordinación intermuscular.',
        importance: 'Son extremadamente funcionales y transfieren directamente a las actividades de la vida diaria y la mayoría de los deportes, promoviendo una fuerza integral.'
    },
    'Tren Superior': {
        description: 'La división de "Tren Superior" se enfoca en todos los músculos de la cintura para arriba: pecho, espalda, hombros y brazos. Es una de las formas más populares y efectivas de organizar el entrenamiento, permitiendo una alta frecuencia por grupo muscular.',
        importance: 'Fortalecer el tren superior es crucial para la postura, la fuerza funcional en tareas diarias (levantar, empujar, tirar) y para crear un físico equilibrado y estético.'
    },
    'Tren Inferior': {
        description: 'La división de "Tren Inferior" se concentra en los músculos de las piernas y glúteos: cuádriceps, isquiotibiales, glúteos y gemelos. Estos entrenamientos suelen ser los más demandantes metabólicamente.',
        importance: 'Un tren inferior fuerte es la base de la potencia atlética (correr, saltar), mejora la estabilidad general, aumenta el gasto calórico y tiene grandes beneficios hormonales.'
    },
    'Core': {
        description: 'El Core es un complejo de músculos que estabilizan la columna y la pelvis. No solo incluye los abdominales, sino también la espalda baja, glúteos y el transverso abdominal. Su función principal es la estabilidad y la transferencia de fuerzas.',
        importance: 'Es la base de todo movimiento. Un core fuerte permite transferir eficientemente la fuerza desde el tren inferior al superior, aumentando el rendimiento en todos los levantamientos y deportes. Es la mejor protección contra el dolor de espalda baja.'
    },
     'Otro': {
        description: 'Esta categoría incluye ejercicios que no se clasifican claramente o son específicos para ciertos objetivos.',
        importance: '...'
    }
}


const ChainDetailView: React.FC<ChainDetailViewProps> = ({ chainId }) => {
    const { exerciseList, muscleHierarchy } = useAppState();
    const { navigateTo, setCurrentBackgroundOverride, openMuscleListEditor } = useAppDispatch();

    useEffect(() => {
        setCurrentBackgroundOverride(undefined);
        return () => setCurrentBackgroundOverride(undefined);
    }, [setCurrentBackgroundOverride]);

    const exercisesByMuscle = useMemo(() => {
        const chainMap = {
            'Cadena Anterior': 'anterior',
            'Cadena Posterior': 'posterior',
            'Cuerpo Completo': 'full',
        };
        const targetChain = chainMap[chainId as keyof typeof chainMap];

        let filtered: ExerciseMuscleInfo[];

        if (targetChain) {
            filtered = exerciseList.filter(ex => ex.chain === targetChain);
        } else {
            const specialMuscles = muscleHierarchy.specialCategories[chainId];
            if (specialMuscles) {
                filtered = exerciseList.filter(ex => 
                    ex.involvedMuscles.some(m => specialMuscles.includes(m.muscle) && m.role === 'primary')
                );
            } else {
                filtered = [];
            }
        }
        
        return filtered.reduce((acc, ex) => {
            const primaryMuscle = ex.involvedMuscles.find(m => m.role === 'primary')?.muscle || 'Otros';
            if (!acc[primaryMuscle]) {
                acc[primaryMuscle] = [];
            }
            acc[primaryMuscle].push(ex);
            return acc;
        }, {} as Record<string, ExerciseMuscleInfo[]>);
    }, [chainId, exerciseList, muscleHierarchy]);

    const sortedMuscleGroups = Object.keys(exercisesByMuscle).sort((a, b) => a.localeCompare(b));
    const info = CHAIN_INFO[chainId];
    
    const coreStructure = [
        { title: "Anti-Extensión (Estabilidad Anterior)", muscles: ["Recto Abdominal", "Transverso Abdominal"] },
        { title: "Anti-Flexión (Estabilidad Posterior)", muscles: ["Erectores Espinales", "Glúteo Mayor", "Multífidos"] },
        { title: "Anti-Rotación (Estabilidad Rotacional)", muscles: ["Oblicuos"] },
        { title: "Estabilidad Interna y Presión", muscles: ["Suelo Pélvico", "Diafragma"] }
    ];

    if (chainId === 'Core') {
        return (
            <div className="min-h-screen flex flex-col bg-transparent overflow-x-hidden relative pb-32">
                <header className="relative pt-12 pb-8 px-6 flex justify-between items-end">
                    <div>
                        <span className="text-[10px] font-black uppercase tracking-widest text-sky-400 mb-2 block">Cadena Cinética</span>
                        <h1 className="text-5xl font-black text-white tracking-tighter leading-none">{chainId}</h1>
                    </div>
                     <button onClick={() => openMuscleListEditor(chainId, 'special')} className="px-4 py-2 rounded-full bg-white/10 backdrop-blur-md border border-white/10 text-[10px] font-black uppercase tracking-widest text-white hover:bg-white/20 transition-colors flex items-center gap-2">
                         <PencilIcon size={12}/> Editar
                     </button>
                </header>
                 <div className="relative z-10 px-6 space-y-6">
                    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="p-6 rounded-[32px] bg-white/5 backdrop-blur-2xl border border-white/10 shadow-2xl">
                        <p className="text-sm text-white/70 leading-relaxed mb-4">{info.description}</p>
                        <div className="px-4 py-3 rounded-2xl bg-sky-500/10 border border-sky-500/20">
                            <p className="text-[11px] font-medium text-sky-200 leading-snug"><span className="font-black text-sky-400 uppercase tracking-widest block mb-1">Importancia</span>{info.importance}</p>
                        </div>
                    </motion.div>

                    {coreStructure.map((section, idx) => (
                        <motion.details key={section.title} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: idx * 0.1 }} className="rounded-[32px] bg-white/5 border border-white/10 overflow-hidden group" open>
                            <summary className="p-5 cursor-pointer list-none flex justify-between items-center outline-none">
                                <h2 className="text-lg font-black text-white/90 tracking-tight">{section.title}</h2>
                                <ChevronRightIcon className="text-white/40 group-open:rotate-90 transition-transform" />
                            </summary>
                            <div className="px-3 pb-3 space-y-2">
                                {section.muscles.map(muscleName => {
                                    const muscleId = muscleName.toLowerCase().replace(/\s+/g, '-').replace(/[()]/g, '');
                                    return (
                                        <div key={muscleId} onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: muscleId })} className="p-4 flex justify-between items-center cursor-pointer rounded-[20px] bg-white/[0.03] hover:bg-white/[0.08] transition-colors">
                                            <h3 className="font-bold text-white/80 text-sm">{muscleName}</h3>
                                            <ChevronRightIcon className="text-sky-400/50" size={16} />
                                        </div>
                                    )
                                })}
                            </div>
                        </motion.details>
                    ))}
                 </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex flex-col bg-transparent overflow-x-hidden relative pb-32">
            <header className="relative pt-12 pb-8 px-6 flex justify-between items-end">
                <div>
                    <span className="text-[10px] font-black uppercase tracking-widest text-sky-400 mb-2 block">Cadena Cinética</span>
                    <h1 className="text-5xl font-black text-white tracking-tighter leading-none">{chainId}</h1>
                </div>
                 <button onClick={() => openMuscleListEditor(chainId, 'special')} className="px-4 py-2 rounded-full bg-white/10 backdrop-blur-md border border-white/10 text-[10px] font-black uppercase tracking-widest text-white hover:bg-white/20 transition-colors flex items-center gap-2">
                     <PencilIcon size={12}/> Editar
                 </button>
            </header>

            <div className="relative z-10 px-6 space-y-6">
                 <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="p-6 rounded-[32px] bg-white/5 backdrop-blur-2xl border border-white/10 shadow-2xl">
                    <p className="text-sm text-white/70 leading-relaxed mb-4">{info.description}</p>
                    <div className="px-4 py-3 rounded-2xl bg-sky-500/10 border border-sky-500/20">
                        <p className="text-[11px] font-medium text-sky-200 leading-snug"><span className="font-black text-sky-400 uppercase tracking-widest block mb-1">Importancia</span>{info.importance}</p>
                    </div>
                </motion.div>

                {sortedMuscleGroups.map((muscle, idx) => (
                    <motion.details key={muscle} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: idx * 0.1 }} className="rounded-[32px] bg-white/5 border border-white/10 overflow-hidden group" open>
                        <summary className="p-5 cursor-pointer list-none flex justify-between items-center outline-none">
                            <h2 className="text-lg font-black text-white/90 tracking-tight">{muscle}</h2>
                            <ChevronRightIcon className="text-white/40 group-open:rotate-90 transition-transform" />
                        </summary>
                        <div className="px-3 pb-3 space-y-2">
                            {exercisesByMuscle[muscle].map(ex => (
                                <ExerciseItem key={ex.id} exercise={ex} />
                            ))}
                        </div>
                    </motion.details>
                ))}
            </div>
        </div>
    );
};

export default ChainDetailView;
