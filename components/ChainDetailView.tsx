// components/ChainDetailView.tsx
import React, { useMemo, useEffect, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo } from '../types';
import { ChevronRightIcon, PencilIcon } from './icons';
import Button from './ui/Button';
import MuscleListEditorModal from './MuscleListEditorModal';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
    const { navigateTo } = useAppDispatch();
    return (
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-3 flex justify-between items-center cursor-pointer list-none hover:bg-slate-800/50 rounded-lg transition-colors"
        >
            <div>
                <h3 className="font-semibold text-white text-md">{exercise.name}</h3>
                <p className="text-xs text-slate-400">{exercise.type} • {exercise.equipment}</p>
            </div>
            <ChevronRightIcon className="text-slate-500" />
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
            <div className="pb-20 animate-fade-in">
                <header className="mb-6 flex justify-between items-center">
                    <h1 className="text-4xl font-bold text-white">{chainId}</h1>
                     <Button onClick={() => openMuscleListEditor(chainId, 'special')} variant="secondary" className="!text-xs !py-1"><PencilIcon size={14}/> Editar</Button>
                </header>
                 <div className="space-y-4">
                    <div className="glass-card-nested p-4 mb-6">
                        <p className="text-sm text-slate-300 mb-2">{info.description}</p>
                        <p className="text-sm text-slate-400 italic">{info.importance}</p>
                    </div>
                    {coreStructure.map(section => (
                        <details key={section.title} className="glass-card !p-0" open>
                            <summary className="p-4 cursor-pointer list-none flex justify-between items-center">
                                <h2 className="text-xl font-bold text-primary-color">{section.title}</h2>
                                <ChevronRightIcon className="details-arrow transition-transform" />
                            </summary>
                            <div className="border-t border-slate-700/50 p-2 space-y-1">
                                {section.muscles.map(muscleName => {
                                    const muscleId = muscleName.toLowerCase().replace(/\s+/g, '-').replace(/[()]/g, '');
                                    return (
                                        <div key={muscleId} onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: muscleId })} className="p-3 flex justify-between items-center cursor-pointer list-none hover:bg-slate-800/50 rounded-lg transition-colors">
                                            <h3 className="font-semibold text-white text-md">{muscleName}</h3>
                                            <ChevronRightIcon className="text-slate-500" />
                                        </div>
                                    )
                                })}
                            </div>
                        </details>
                    ))}
                 </div>
            </div>
        );
    }


    return (
        <div className="pb-20 animate-fade-in">
            <header className="mb-6 flex justify-between items-center">
                <h1 className="text-4xl font-bold text-white">{chainId}</h1>
                 <Button onClick={() => openMuscleListEditor(chainId, 'special')} variant="secondary" className="!text-xs !py-1"><PencilIcon size={14}/> Editar</Button>
            </header>
            
            <div className="space-y-4">
                 <div className="glass-card-nested p-4 mb-6">
                    <p className="text-sm text-slate-300 mb-2">{info.description}</p>
                    <p className="text-sm text-slate-400 italic">{info.importance}</p>
                </div>
            
                {sortedMuscleGroups.map(muscle => (
                    <details key={muscle} className="glass-card !p-0" open>
                        <summary className="p-4 cursor-pointer list-none flex justify-between items-center">
                            <h2 className="text-xl font-bold text-primary-color">{muscle}</h2>
                            <ChevronRightIcon className="details-arrow transition-transform" />
                        </summary>
                        <div className="border-t border-slate-700/50 p-2 space-y-1">
                            {exercisesByMuscle[muscle].map(ex => (
                                <ExerciseItem key={ex.id} exercise={ex} />
                            ))}
                        </div>
                    </details>
                ))}
            </div>
        </div>
    );
};

export default ChainDetailView;