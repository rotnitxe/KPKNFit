// components/PeriodizationTemplateModal.tsx
import React from 'react';
import { Macrocycle, Mesocycle, Block, ProgramWeek } from '../types';
import { TrophyIcon, ChevronRightIcon, LayersIcon, XIcon } from './icons';

const templates: { name: string; description: string; data: { macros: any[], mode?: 'powerlifting' | 'hypertrophy' } }[] = [
    {
        name: 'Modo Powerlifting (Peak 12W)',
        description: 'Estructura clásica de 12 semanas para fuerza máxima. Bloques de volumen, fuerza y peaking para competición.',
        data: {
            mode: 'powerlifting',
            macros: [
                {
                    id: crypto.randomUUID(), name: 'Macrociclo de Competición',
                    blocks: [{
                        id: crypto.randomUUID(),
                        name: 'Bloque de Volumen',
                        mesocycles: [{ id: crypto.randomUUID(), name: 'Acumulación', goal: 'Acumulación', weeks: Array.from({ length: 4 }, (_, i) => ({ id: crypto.randomUUID(), name: `Semana ${i + 1}`, sessions: [] })) }]
                    }, {
                        id: crypto.randomUUID(),
                        name: 'Bloque de Fuerza',
                        mesocycles: [{ id: crypto.randomUUID(), name: 'Intensificación', goal: 'Intensificación', weeks: Array.from({ length: 4 }, (_, i) => ({ id: crypto.randomUUID(), name: `Semana ${i + 5}`, sessions: [] })) }]
                    }, {
                        id: crypto.randomUUID(),
                        name: 'Bloque de Peaking',
                        mesocycles: [
                             { id: crypto.randomUUID(), name: 'Realización', goal: 'Realización', weeks: Array.from({ length: 3 }, (_, i) => ({ id: crypto.randomUUID(), name: `Semana ${i + 9}`, sessions: [] })) },
                             { id: crypto.randomUUID(), name: 'Tapering / Comp', goal: 'Descarga', weeks: [{ id: crypto.randomUUID(), name: `Semana 12`, sessions: [] }] },
                        ]
                    }]
                }
            ]
        }
    },
    {
        name: 'Hipertrofia Ondulante (12W)',
        description: 'Enfoque en ganancia de masa muscular alternando fases de alto volumen con bloques de resensibilización.',
        data: {
            mode: 'hypertrophy',
            macros: [
                 {
                    id: crypto.randomUUID(), name: 'Macrociclo de Hipertrofia',
                    blocks: [{
                        id: crypto.randomUUID(),
                        name: 'Fase General',
                        mesocycles: [
                            { id: crypto.randomUUID(), name: 'Acumulación 1', goal: 'Acumulación', weeks: Array.from({ length: 4 }, (_, i) => ({ id: crypto.randomUUID(), name: `Semana ${i + 1}`, sessions: [] })) },
                            { id: crypto.randomUUID(), name: 'Intensificación 1', goal: 'Intensificación', weeks: Array.from({ length: 3 }, (_, i) => ({ id: crypto.randomUUID(), name: `Semana ${i + 5}`, sessions: [] })) },
                            { id: crypto.randomUUID(), name: 'Descarga Activa', goal: 'Descarga', weeks: [{ id: crypto.randomUUID(), name: `Semana 8`, sessions: [] }] },
                            { id: crypto.randomUUID(), name: 'Acumulación 2', goal: 'Acumulación', weeks: Array.from({ length: 4 }, (_, i) => ({ id: crypto.randomUUID(), name: `Semana ${i + 9}`, sessions: [] })) },
                        ]
                    }]
                }
            ]
        }
    }
];

const PeriodizationTemplateModal: React.FC<{
    isOpen: boolean;
    onClose: () => void;
    onSelect: (data: { macros: Macrocycle[], mode?: 'powerlifting' | 'hypertrophy' }) => void;
}> = ({ isOpen, onClose, onSelect }) => {
    if (!isOpen) return null;

    const handleSelect = (templateData: any) => {
        const newTemplate = JSON.parse(JSON.stringify(templateData));
        newTemplate.macros.forEach((macro: any) => {
            macro.id = crypto.randomUUID();
            (macro.blocks || []).forEach((block: Block) => {
                block.id = crypto.randomUUID();
                block.mesocycles.forEach((meso: Mesocycle) => {
                    meso.id = crypto.randomUUID();
                    meso.weeks.forEach((week: ProgramWeek) => {
                        week.id = crypto.randomUUID();
                    });
                });
            })
        });
        onSelect(newTemplate);
    };

    return (
        <div className="fixed inset-0 z-[9999] bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-fade-in font-sans">
            <div className="absolute inset-0" onClick={onClose} />
            <div className="bg-zinc-950 border border-white/10 rounded-3xl w-full max-w-4xl flex flex-col shadow-2xl relative overflow-hidden z-10">
                <div className="p-6 border-b border-white/10 flex justify-between items-center bg-black">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-white flex items-center justify-center text-black">
                            <LayersIcon size={20} />
                        </div>
                        <h2 className="text-xl sm:text-2xl font-black text-white uppercase tracking-tighter">Catálogo de Plantillas</h2>
                    </div>
                    <button onClick={onClose} className="p-2 rounded-full hover:bg-white/10 text-gray-400 hover:text-white transition-colors"><XIcon size={24}/></button>
                </div>

                <div className="p-6 sm:p-10 custom-scrollbar bg-[#050505] flex overflow-x-auto gap-6 snap-x snap-mandatory hide-scrollbar">
                    {templates.map((tpl, i) => (
                        <div key={i} className="snap-center shrink-0 w-[280px] sm:w-[320px] bg-white rounded-3xl p-6 flex flex-col justify-between hover:scale-[1.02] transition-transform duration-300 shadow-[0_20px_40px_rgba(255,255,255,0.1)] border-4 border-transparent hover:border-blue-500">
                            <div>
                                <div className="flex justify-between items-start mb-6">
                                    <div className="bg-black text-white text-[9px] font-black uppercase px-3 py-1 rounded-full tracking-widest">
                                        {tpl.data.mode === 'powerlifting' ? 'Powerlifting' : 'Hipertrofia'}
                                    </div>
                                    <TrophyIcon size={24} className={tpl.data.mode === 'powerlifting' ? 'text-yellow-500' : 'text-blue-500'} />
                                </div>
                                <h3 className="text-2xl font-black text-black uppercase tracking-tight leading-none mb-3">{tpl.name}</h3>
                                <p className="text-xs font-bold text-gray-500 leading-relaxed mb-6">{tpl.description}</p>
                            </div>
                            <button onClick={() => handleSelect(tpl.data)} className="w-full py-4 bg-black text-white rounded-xl font-black text-xs uppercase tracking-widest hover:bg-blue-600 transition-colors flex items-center justify-center gap-2">
                                Aplicar <ChevronRightIcon size={16}/>
                            </button>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default PeriodizationTemplateModal;