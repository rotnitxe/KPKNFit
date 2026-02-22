// components/UpdateNoveltiesModal.tsx
// Semi-wizard de novedades con páginas para features destacadas

import React, { useState } from 'react';
import { ChevronRightIcon, ChevronLeftIcon } from './icons';
import Button from './ui/Button';

interface UpdateNoveltiesModalProps {
    isOpen: boolean;
    version: string;
    onClose: () => void;
}

const TOTAL_STEPS = 5;

export const UpdateNoveltiesModal: React.FC<UpdateNoveltiesModalProps> = ({ isOpen, version, onClose }) => {
    const [step, setStep] = useState(0);

    if (!isOpen) return null;

    const handleNext = () => {
        if (step < TOTAL_STEPS - 1) setStep(s => s + 1);
        else onClose();
    };

    const handlePrev = () => {
        if (step > 0) setStep(s => s - 1);
    };

    return (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4 bg-black/95 backdrop-blur-md animate-fade-in">
            <div className="bg-[#0a0a0a] border border-white/10 rounded-3xl w-full max-w-md shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-white/10 flex-shrink-0">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-cyan-500/20 border border-cyan-500/30 flex items-center justify-center">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                                <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                                <line x1="12" y1="22.08" x2="12" y2="12"/>
                            </svg>
                        </div>
                        <div>
                            <h2 className="text-sm font-black text-white uppercase tracking-tight">Novedades v{version}</h2>
                            <p className="text-[10px] text-slate-500 font-bold">{step + 1} / {TOTAL_STEPS}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-1">
                        {Array.from({ length: TOTAL_STEPS }).map((_, i) => (
                            <div
                                key={i}
                                className={`w-1.5 h-1.5 rounded-full transition-colors ${i === step ? 'bg-cyan-500' : i < step ? 'bg-cyan-500/50' : 'bg-white/20'}`}
                            />
                        ))}
                    </div>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto px-5 py-5 custom-scrollbar min-h-0">
                    {step === 0 && (
                        <div className="space-y-6 text-center">
                            <div className="py-4">
                                <h3 className="text-2xl font-black text-white uppercase tracking-tight mb-2">¡KPKN Actualizado!</h3>
                                <p className="text-slate-400 text-sm">Versión {version} con mejoras importantes en nutrición y WikiLab.</p>
                            </div>
                            <div className="bg-white/5 rounded-2xl p-5 border border-white/5 text-left">
                                <p className="text-xs text-slate-400 font-bold uppercase tracking-widest mb-3">En esta actualización</p>
                                <ul className="space-y-2 text-sm text-slate-300">
                                    <li className="flex items-center gap-2"><span className="text-cyan-500">●</span> Plan de alimentación rediseñado</li>
                                    <li className="flex items-center gap-2"><span className="text-cyan-500">●</span> Editor de plan completo (nivel nerd)</li>
                                    <li className="flex items-center gap-2"><span className="text-cyan-500">●</span> WikiLab con pestañas y datos corregidos</li>
                                    <li className="flex items-center gap-2"><span className="text-cyan-500">●</span> Registro de comida mejorado</li>
                                    <li className="flex items-center gap-2"><span className="text-cyan-500">●</span> Nutrición + Batería AUGE</li>
                                </ul>
                            </div>
                        </div>
                    )}

                    {step === 1 && (
                        <div className="space-y-5">
                            <div>
                                <span className="text-[10px] font-black text-cyan-500 uppercase tracking-widest">Feature destacada</span>
                                <h3 className="text-xl font-black text-white uppercase tracking-tight mt-1">Plan de alimentación</h3>
                            </div>
                            <div className="space-y-4 text-sm text-slate-300">
                                <div className="bg-white/5 rounded-xl p-4 border border-cyan-500/20">
                                    <h4 className="font-bold text-white mb-2">Landing antes del wizard</h4>
                                    <p>Ya no te lanzamos el wizard a secas. Primero verás una pantalla con "Cómo funciona" (3 pasos), beneficios y un CTA para crear tu plan.</p>
                                </div>
                                <div className="bg-white/5 rounded-xl p-4 border border-white/5">
                                    <h4 className="font-bold text-white mb-2">Wizard en 3 pasos</h4>
                                    <p>Cada paso agrupa varias cosas importantes: Objetivo + AUGE + Datos corporales → Actividad + Metabólicas + Dieta → Desglose + Macros + Tendencia. Con explicaciones de por qué importa cada cosa.</p>
                                </div>
                                <div className="bg-white/5 rounded-xl p-4 border border-white/5">
                                    <h4 className="font-bold text-white mb-2">Editor de plan completo</h4>
                                    <p>Una vez creado el plan, puedes editarlo sin volver al wizard. Editor nivel nerd: objetivo, AUGE, datos corporales, bioimpedancia, actividad, metabólicas, dieta, fórmula TMB, macros... todo editable por separado. Acceso desde Nutrición o Ajustes.</p>
                                </div>
                            </div>
                        </div>
                    )}

                    {step === 2 && (
                        <div className="space-y-5">
                            <div>
                                <span className="text-[10px] font-black text-cyan-500 uppercase tracking-widest">Feature destacada</span>
                                <h3 className="text-xl font-black text-white uppercase tracking-tight mt-1">WikiLab rediseñado</h3>
                            </div>
                            <div className="space-y-4 text-sm text-slate-300">
                                <div className="bg-white/5 rounded-xl p-4 border border-cyan-500/20">
                                    <h4 className="font-bold text-white mb-2">Pestañas por sección</h4>
                                    <p>Ejercicios | Anatomía | Articulaciones | Tendones | Patrones. Cada pestaña muestra solo su contenido, sin mezclar.</p>
                                </div>
                                <div className="bg-white/5 rounded-xl p-4 border border-white/5">
                                    <h4 className="font-bold text-white mb-2">Búsqueda agrupada</h4>
                                    <p>Al buscar, los resultados se muestran con headers claros: TENDONES (N), MÚSCULOS (N), ARTICULACIONES (N)... Separación visual entre grupos.</p>
                                </div>
                                <div className="bg-white/5 rounded-xl p-4 border border-white/5">
                                    <h4 className="font-bold text-white mb-2">Datos corregidos</h4>
                                    <p>El tendón del Supraespinoso ya no mostraba "Deltoides Lateral". Añadimos Supraespinoso e Infraespinoso a la base de datos y corregimos las referencias. Fallback cuando un músculo no existe.</p>
                                </div>
                            </div>
                        </div>
                    )}

                    {step === 3 && (
                        <div className="space-y-5">
                            <div>
                                <span className="text-[10px] font-black text-cyan-500 uppercase tracking-widest">Nutrición</span>
                                <h3 className="text-xl font-black text-white uppercase tracking-tight mt-1">Registro y batería</h3>
                            </div>
                            <ul className="space-y-3 text-sm text-slate-300">
                                <li className="flex items-start gap-2 p-3 rounded-xl bg-white/5">
                                    <span className="text-amber-500 mt-0.5">•</span>
                                    <span><strong className="text-white">Registro de comida:</strong> detecta gramos (200g arroz), cantidades (2 huevos) y cocción (frito, plancha, horno). Sin duplicados ni errores como "200x pechuga".</span>
                                </li>
                                <li className="flex items-start gap-2 p-3 rounded-xl bg-white/5">
                                    <span className="text-amber-500 mt-0.5">•</span>
                                    <span><strong className="text-white">Tags automáticos:</strong> al escribir, los alimentos se convierten en etiquetas si hay coincidencia. Pulsa una etiqueta para editar gramos, porción o método de cocción.</span>
                                </li>
                                <li className="flex items-start gap-2 p-3 rounded-xl bg-white/5">
                                    <span className="text-cyan-500 mt-0.5">•</span>
                                    <span><strong className="text-white">Nutrición + Batería AUGE:</strong> conecta calorías y macros con la recuperación muscular. Lógica no lineal: superávit no siempre acelera la recuperación.</span>
                                </li>
                                <li className="flex items-start gap-2 p-3 rounded-xl bg-white/5">
                                    <span className="text-cyan-500 mt-0.5">•</span>
                                    <span><strong className="text-white">Reporte de micronutrientes:</strong> al final del día verás carencias específicas (hierro, calcio, vitaminas) según lo que comiste.</span>
                                </li>
                                <li className="flex items-start gap-2 p-3 rounded-xl bg-white/5">
                                    <span className="text-amber-500 mt-0.5">•</span>
                                    <span><strong className="text-white">Régimen de déficit:</strong> si reportas déficit, la app avisa en el editor de sesiones y reduce los límites de volumen sugeridos.</span>
                                </li>
                                <li className="flex items-start gap-2 p-3 rounded-xl bg-white/5">
                                    <span className="text-cyan-500 mt-0.5">•</span>
                                    <span><strong className="text-white">Bases offline:</strong> USDA y Open Food Facts sin conexión. Productos con caloría 0 enriquecidos con valores genéricos.</span>
                                </li>
                                <li className="flex items-start gap-2 p-3 rounded-xl bg-white/5">
                                    <span className="text-cyan-500 mt-0.5">•</span>
                                    <span><strong className="text-white">Búsqueda unificada:</strong> evita duplicados entre USDA y OFF; prioriza USDA y rellena macros faltantes.</span>
                                </li>
                                <li className="flex items-start gap-2 p-3 rounded-xl bg-white/5">
                                    <span className="text-slate-500 mt-0.5">•</span>
                                    <span>Guía de ayuda en el registro de comida con ejemplos y consejos.</span>
                                </li>
                            </ul>
                        </div>
                    )}

                    {step === 4 && (
                        <div className="space-y-6 text-center py-4">
                            <div>
                                <h3 className="text-xl font-black text-white uppercase tracking-tight mb-2">¡Listo para entrenar!</h3>
                                <p className="text-slate-400 text-sm">Todas las novedades están disponibles. El plan de alimentación y el editor están en Nutrición; WikiLab en KPKN.</p>
                            </div>
                            <div className="bg-cyan-500/10 border border-cyan-500/30 rounded-2xl p-5">
                                <p className="text-xs text-cyan-400 font-bold uppercase tracking-widest mb-2">Tip</p>
                                <p className="text-sm text-slate-300">Pulsa "Editar plan" en la tarjeta de objetivo de Nutrición para acceder al editor completo sin pasar por el wizard.</p>
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="flex gap-3 px-5 py-4 border-t border-white/10 flex-shrink-0">
                    {step > 0 ? (
                        <Button onClick={handlePrev} variant="secondary" className="flex-1">
                            <ChevronLeftIcon size={18} className="mr-1" /> Atrás
                        </Button>
                    ) : (
                        <div className="flex-1" />
                    )}
                    <Button onClick={handleNext} className="flex-1">
                        {step < TOTAL_STEPS - 1 ? (
                            <>Siguiente <ChevronRightIcon size={18} className="ml-1" /></>
                        ) : (
                            '¡Entendido, a entrenar!'
                        )}
                    </Button>
                </div>
            </div>
        </div>
    );
};
