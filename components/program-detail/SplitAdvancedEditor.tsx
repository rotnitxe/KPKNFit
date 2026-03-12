import React, { useState, useMemo, useCallback, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { SPLIT_TEMPLATES, SplitTemplate } from '../../data/splitTemplates';
import { useAppContext } from '../../contexts/AppContext';
import { ProgramWeek } from '../../types';
import {
    SearchIcon, PlusIcon, XIcon, CheckIcon, TrashIcon,
    EditIcon, CopyIcon, MergeIcon, SwapIcon, GripVerticalIcon,
    AlertTriangleIcon, ChevronRightIcon, ChevronDownIcon
} from '../icons';

interface SplitAdvancedEditorProps {
    program: any;
    selectedBlockId: string | null;
    selectedWeekId: string | null;
    onUpdateProgram: (program: any) => void;
    addToast: (msg: string, type?: 'success' | 'danger' | 'achievement' | 'suggestion', title?: string, duration?: number, why?: string) => void;
}

// Modal de confirmación de migración
const MigrationConfirmModal: React.FC<{
    isOpen: boolean;
    splitName: string;
    hasSessions: boolean;
    onMigrate: () => void;
    onStartFromScratch: () => void;
    onCancel: () => void;
}> = ({ isOpen, splitName, hasSessions, onMigrate, onStartFromScratch, onCancel }) => {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={onCancel}>
            <div className="bg-white rounded-[2rem] w-full max-w-sm p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                <button onClick={onCancel} className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-600">
                    <XIcon size={20} />
                </button>
                
                <div className="text-center mb-6">
                    <div className="w-16 h-16 mx-auto bg-gradient-to-br from-purple-100 to-pink-100 rounded-full flex items-center justify-center mb-4">
                        <SwapIcon size={32} className="text-purple-600" />
                    </div>
                    <h3 className="text-lg font-black text-zinc-900 uppercase tracking-tight mb-2">
                        Aplicar Split
                    </h3>
                    <p className="text-[10px] text-zinc-500 uppercase tracking-widest">
                        {splitName}
                    </p>
                </div>

                {hasSessions ? (
                    <>
                        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-6">
                            <div className="flex items-start gap-3">
                                <AlertTriangleIcon size={18} className="text-amber-600 shrink-0 mt-0.5" />
                                <div>
                                    <p className="text-[10px] font-black text-amber-900 uppercase tracking-widest mb-1">
                                        Hay sesiones existentes
                                    </p>
                                    <p className="text-[9px] text-amber-700">
                                        ¿Qué deseas hacer con tus ejercicios actuales?
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div className="space-y-3 mb-6">
                            <button
                                onClick={onMigrate}
                                className="w-full p-4 rounded-2xl border-2 border-purple-300 bg-gradient-to-br from-purple-50 to-white text-left hover:shadow-lg transition-all"
                            >
                                <div className="flex items-center gap-3">
                                    <div className="w-10 h-10 rounded-full bg-purple-500 text-white flex items-center justify-center shrink-0">
                                        <SwapIcon size={18} />
                                    </div>
                                    <div>
                                        <p className="text-sm font-black text-purple-900">Migrar ejercicios</p>
                                        <p className="text-[9px] text-purple-700">Reasignar al nuevo split</p>
                                    </div>
                                </div>
                            </button>

                            <button
                                onClick={onStartFromScratch}
                                className="w-full p-4 rounded-2xl border-2 border-red-300 bg-gradient-to-br from-red-50 to-white text-left hover:shadow-lg transition-all"
                            >
                                <div className="flex items-center gap-3">
                                    <div className="w-10 h-10 rounded-full bg-red-500 text-white flex items-center justify-center shrink-0">
                                        <TrashIcon size={18} />
                                    </div>
                                    <div>
                                        <p className="text-sm font-black text-red-900">Empezar desde cero</p>
                                        <p className="text-[9px] text-red-700">Borrar ejercicios actuales</p>
                                    </div>
                                </div>
                            </button>
                        </div>
                    </>
                ) : (
                    <>
                        <div className="bg-green-50 border border-green-200 rounded-xl p-4 mb-6">
                            <div className="flex items-start gap-3">
                                <CheckIcon size={18} className="text-green-600 shrink-0 mt-0.5" />
                                <div>
                                    <p className="text-[10px] font-black text-green-900 uppercase tracking-widest mb-1">
                                        Semana vacía
                                    </p>
                                    <p className="text-[9px] text-green-700">
                                        Se aplicará el split sin conflictos
                                    </p>
                                </div>
                            </div>
                        </div>

                        <button
                            onClick={onStartFromScratch}
                            className="w-full py-4 rounded-2xl bg-gradient-to-r from-purple-500 to-purple-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg shadow-purple-500/30"
                        >
                            Aplicar Split
                        </button>
                    </>
                )}

                <button
                    onClick={onCancel}
                    className="w-full py-3 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 hover:text-zinc-700 transition-colors"
                >
                    Cancelar
                </button>
            </div>
        </div>
    );
};

// Modal de comparación
const CompareModal: React.FC<{
    isOpen: boolean;
    selectedSplits: SplitTemplate[];
    onRemove: (splitId: string) => void;
    onClose: () => void;
    onApply: (split: SplitTemplate) => void;
}> = ({ isOpen, selectedSplits, onRemove, onClose, onApply }) => {
    if (!isOpen) return null;

    const COMPARE_COLORS = ['from-blue-500 to-cyan-500', 'from-purple-500 to-pink-500', 'from-orange-500 to-red-500'];

    return (
        <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={onClose}>
            <div className="bg-white rounded-[2rem] w-full max-w-2xl max-h-[80vh] overflow-y-auto p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                <button onClick={onClose} className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-600">
                    <XIcon size={20} />
                </button>

                <h3 className="text-lg font-black text-zinc-900 uppercase tracking-tight mb-6">
                    Comparar Splits ({selectedSplits.length})
                </h3>

                {/* Grid comparativo */}
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr>
                                <th className="p-3 text-left text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 bg-zinc-50 rounded-tl-xl" />
                                {selectedSplits.map((split, i) => (
                                    <th key={split.id} className={`p-4 text-left rounded-t-xl ${i === selectedSplits.length - 1 ? 'rounded-tr-xl' : ''}`}>
                                        <div className={`bg-gradient-to-br ${COMPARE_COLORS[i]} text-white rounded-xl p-3 relative`}>
                                            <button
                                                onClick={() => onRemove(split.id)}
                                                className="absolute top-1 right-1 w-5 h-5 rounded-full bg-white/20 hover:bg-white/30 flex items-center justify-center"
                                            >
                                                <XIcon size={10} />
                                            </button>
                                            <p className="text-sm font-black">{split.name}</p>
                                            <p className="text-[9px] text-white/80">{split.pattern.filter(d => d.toLowerCase() !== 'descanso').length} días</p>
                                        </div>
                                    </th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {/* Descripción */}
                            <tr>
                                <td className="p-3 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 bg-zinc-50">Descripción</td>
                                {selectedSplits.map(split => (
                                    <td key={split.id} className="p-3 border-t border-zinc-100">
                                        <p className="text-[10px] text-zinc-600">{split.description}</p>
                                    </td>
                                ))}
                            </tr>

                            {/* Frecuencia */}
                            <tr>
                                <td className="p-3 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 bg-zinc-50">Frecuencia</td>
                                {selectedSplits.map(split => (
                                    <td key={split.id} className="p-3 border-t border-zinc-100 text-center">
                                        <div className="text-xl font-black text-zinc-900">{split.pattern.filter(d => d.toLowerCase() !== 'descanso').length}x</div>
                                        <div className="text-[8px] text-zinc-400 uppercase">por semana</div>
                                    </td>
                                ))}
                            </tr>

                            {/* Patrón */}
                            <tr>
                                <td className="p-3 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 bg-zinc-50">Patrón</td>
                                {selectedSplits.map(split => (
                                    <td key={split.id} className="p-3 border-t border-zinc-100">
                                        <div className="flex gap-0.5">
                                            {split.pattern.map((day, i) => (
                                                <div
                                                    key={i}
                                                    className={`flex-1 h-6 rounded text-[7px] font-black flex items-center justify-center ${
                                                        day.toLowerCase() === 'descanso'
                                                            ? 'bg-zinc-100 text-zinc-400'
                                                            : 'bg-purple-100 text-purple-700'
                                                    }`}
                                                >
                                                    {day.split(' ')[0].slice(0, 2)}
                                                </div>
                                            ))}
                                        </div>
                                    </td>
                                ))}
                            </tr>

                            {/* Pros */}
                            <tr>
                                <td className="p-3 text-[9px] font-black uppercase tracking-[0.15em] text-green-600 bg-zinc-50">✓ Pros</td>
                                {selectedSplits.map(split => (
                                    <td key={split.id} className="p-3 border-t border-zinc-100">
                                        <ul className="space-y-1">
                                            {split.pros?.slice(0, 3).map((pro, i) => (
                                                <li key={i} className="text-[9px] text-green-600">{pro}</li>
                                            ))}
                                        </ul>
                                    </td>
                                ))}
                            </tr>

                            {/* Contras */}
                            <tr>
                                <td className="p-3 text-[9px] font-black uppercase tracking-[0.15em] text-red-600 bg-zinc-50">✗ Contras</td>
                                {selectedSplits.map(split => (
                                    <td key={split.id} className="p-3 border-t border-zinc-100">
                                        <ul className="space-y-1">
                                            {split.cons?.slice(0, 3).map((con, i) => (
                                                <li key={i} className="text-[9px] text-red-600">{con}</li>
                                            ))}
                                        </ul>
                                    </td>
                                ))}
                            </tr>
                        </tbody>
                    </table>
                </div>

                {/* Acciones */}
                <div className="flex gap-3 mt-6 pt-6 border-t border-zinc-100">
                    {selectedSplits.map((split, i) => (
                        <button
                            key={split.id}
                            onClick={() => {
                                onApply(split);
                                onClose();
                            }}
                            className={`flex-1 py-3 rounded-xl bg-gradient-to-r ${COMPARE_COLORS[i]} text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg`}
                        >
                            Aplicar {split.name}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
};

// Modal de hibridización mejorado
const HybridModal: React.FC<{
    isOpen: boolean;
    selectedSplits: SplitTemplate[];
    onClose: () => void;
    onApply: (pattern: string[], name: string, startFromScratch: boolean) => void;
    onSaveAsCustom: (pattern: string[], name: string) => void;
}> = ({ isOpen, selectedSplits, onClose, onApply, onSaveAsCustom }) => {
    const [hybridPattern, setHybridPattern] = useState<string[]>([]);
    const [hybridName, setHybridName] = useState('Split Híbrido');
    const [showMigrationModal, setShowMigrationModal] = useState(false);
    const [pendingAction, setPendingAction] = useState<'apply' | 'save'>('apply');

    const handleCreateHybrid = useCallback(() => {
        if (selectedSplits.length < 2) return;

        const split1 = selectedSplits[0];
        const split2 = selectedSplits[1];
        const hybrid: string[] = [];
        let useSplit1 = true;

        for (let i = 0; i < 7; i++) {
            const day1 = split1.pattern[i];
            const day2 = split2.pattern[i];

            if (day1.toLowerCase() === 'descanso' && day2.toLowerCase() === 'descanso') {
                hybrid.push('Descanso');
            } else if (day1.toLowerCase() === 'descanso') {
                hybrid.push(day2);
            } else if (day2.toLowerCase() === 'descanso') {
                hybrid.push(day1);
            } else {
                // Ambos son días de entrenamiento - alternar inteligentemente
                hybrid.push(useSplit1 ? day1 : day2);
                useSplit1 = !useSplit1;
            }
        }

        // ✅ VALIDACIÓN DE BALANCE
        const trainingDays = hybrid.filter(d => d.toLowerCase() !== 'descanso');
        const pushDays = trainingDays.filter(d => ['Empuje', 'Pecho', 'Hombros', 'Tríceps'].some(k => d.includes(k))).length;
        const pullDays = trainingDays.filter(d => ['Tracción', 'Espalda', 'Bíceps'].some(k => d.includes(k))).length;
        const legDays = trainingDays.filter(d => ['Pierna', 'Cuádriceps', 'Isquios', 'Glúteo'].some(k => d.includes(k))).length;

        const warnings: string[] = [];
        
        if (trainingDays.length === 0) {
            warnings.push('⚠️ No hay días de entrenamiento');
        }
        if (trainingDays.length >= 6) {
            warnings.push('⚠️ Muy pocos días de recuperación');
        }
        if (legDays === 0 && trainingDays.length > 3) {
            warnings.push('⚠️ Sin días de pierna - riesgo de desbalance');
        }
        if (pushDays > 0 && pullDays === 0) {
            warnings.push('⚠️ Solo empuje - riesgo postural');
        }
        if (pullDays > 0 && pushDays === 0) {
            warnings.push('⚠️ Solo tracción - desbalance funcional');
        }

        setHybridPattern(hybrid);
        
        if (warnings.length > 0) {
            onApply(hybrid, 'Split Híbrido (con advertencias)', true);
            warnings.forEach(w => console.warn(w));
        }
    }, [selectedSplits, onApply]);

    const handleEditDay = (index: number, newValue: string) => {
        const newPattern = [...hybridPattern];
        newPattern[index] = newValue;
        setHybridPattern(newPattern);
    };

    const handleApplyClick = () => {
        setPendingAction('apply');
        setShowMigrationModal(true);
    };

    const handleSaveClick = () => {
        setPendingAction('save');
        setShowMigrationModal(true);
    };

    const handleConfirmMigration = (startFromScratch: boolean) => {
        if (pendingAction === 'apply') {
            onApply(hybridPattern, hybridName, startFromScratch);
        } else {
            onSaveAsCustom(hybridPattern, hybridName);
        }
        setShowMigrationModal(false);
        onClose();
    };

    if (!isOpen) return null;

    const DAYS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
    const TRAINING_OPTIONS = ['Descanso', 'Empuje', 'Tracción', 'Pierna', 'Torso', 'Full Body', 'Pecho', 'Espalda', 'Hombros', 'Brazos', 'Cuádriceps', 'Isquios', 'Glúteo'];

    return (
        <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={onClose}>
            <div className="bg-white rounded-[2rem] w-full max-w-lg max-h-[85vh] overflow-y-auto p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                <button onClick={onClose} className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-600">
                    <XIcon size={20} />
                </button>

                <h3 className="text-lg font-black text-zinc-900 uppercase tracking-tight mb-2 flex items-center gap-2">
                    <MergeIcon size={20} className="text-purple-600" />
                    Hibridizar Splits
                </h3>
                <p className="text-[10px] text-zinc-500 uppercase tracking-widest mb-4">
                    Combina {selectedSplits[0]?.name} + {selectedSplits[1]?.name}
                </p>

                {/* Splits originales */}
                <div className="flex items-center gap-2 mb-4">
                    {selectedSplits.map((split, i) => (
                        <div key={split.id} className={`flex-1 bg-gradient-to-br ${i === 0 ? 'from-blue-500 to-cyan-500' : 'from-purple-500 to-pink-500'} text-white rounded-xl p-3 text-center`}>
                            <p className="text-xs font-black">{split.name}</p>
                            <p className="text-[8px] text-white/80">{split.pattern.filter(d => d.toLowerCase() !== 'descanso').length} días</p>
                        </div>
                    ))}
                    <div className="text-zinc-400">
                        <MergeIcon size={20} />
                    </div>
                </div>

                {!hybridPattern.length ? (
                    <button
                        onClick={handleCreateHybrid}
                        className="w-full py-4 rounded-2xl bg-gradient-to-r from-purple-500 to-purple-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg"
                    >
                        Crear Split Híbrido
                    </button>
                ) : (
                    <>
                        {/* Nombre del split */}
                        <div className="mb-4">
                            <label className="text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 block mb-2">Nombre del Split</label>
                            <input
                                type="text"
                                value={hybridName}
                                onChange={e => setHybridName(e.target.value)}
                                className="w-full bg-zinc-50 border border-zinc-200 rounded-xl px-4 py-3 text-sm font-bold focus:ring-2 focus:ring-purple-500/30 outline-none"
                            />
                        </div>

                        {/* Resultado editable */}
                        <div className="bg-zinc-50 rounded-xl p-4 mb-4">
                            <p className="text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 mb-3">Resultado (edita cada día):</p>
                            <div className="space-y-2">
                                {hybridPattern.map((day, i) => (
                                    <div key={i} className="flex items-center gap-2">
                                        <span className="text-[8px] font-bold text-zinc-400 w-16">{DAYS[i]?.slice(0, 3)}</span>
                                        <select
                                            value={day}
                                            onChange={e => handleEditDay(i, e.target.value)}
                                            className={`flex-1 rounded-lg px-3 py-2 text-[9px] font-black ${
                                                day.toLowerCase() === 'descanso'
                                                    ? 'bg-zinc-100 text-zinc-400'
                                                    : 'bg-gradient-to-br from-blue-100 to-purple-100 text-zinc-700'
                                            }`}
                                        >
                                            {TRAINING_OPTIONS.map(opt => (
                                                <option key={opt} value={opt}>{opt}</option>
                                            ))}
                                        </select>
                                    </div>
                                ))}
                            </div>
                        </div>

                        {/* Vista previa visual */}
                        <div className="mb-4">
                            <p className="text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 mb-2">Vista previa:</p>
                            <div className="flex gap-1">
                                {hybridPattern.map((day, i) => (
                                    <div
                                        key={i}
                                        className={`flex-1 h-10 rounded-lg flex flex-col items-center justify-center ${
                                            day.toLowerCase() === 'descanso'
                                                ? 'bg-zinc-100'
                                                : 'bg-gradient-to-br from-blue-100 to-purple-100'
                                        }`}
                                    >
                                        <span className="text-[6px] font-bold text-zinc-400">D{i + 1}</span>
                                        <span className="text-[7px] font-black text-zinc-700">{day.split(' ')[0].slice(0, 3)}</span>
                                    </div>
                                ))}
                            </div>
                        </div>

                        {/* Botones de acción */}
                        <div className="flex gap-2 mb-3">
                            <button
                                onClick={handleSaveClick}
                                className="flex-1 py-3 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg"
                            >
                                Guardar como Personalizado
                            </button>
                            <button
                                onClick={handleApplyClick}
                                className="flex-1 py-3 rounded-xl bg-gradient-to-r from-purple-500 to-purple-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg"
                            >
                                Aplicar Split
                            </button>
                        </div>
                    </>
                )}
            </div>

            {/* Modal de migración */}
            {showMigrationModal && (
                <div className="fixed inset-0 z-[250] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowMigrationModal(false)}>
                    <div className="bg-white rounded-[2rem] w-full max-w-sm p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setShowMigrationModal(false)} className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-600">
                            <XIcon size={20} />
                        </button>
                        
                        <div className="text-center mb-6">
                            <div className="w-16 h-16 mx-auto bg-gradient-to-br from-purple-100 to-pink-100 rounded-full flex items-center justify-center mb-4">
                                <SwapIcon size={32} className="text-purple-600" />
                            </div>
                            <h3 className="text-lg font-black text-zinc-900 uppercase tracking-tight mb-2">
                                {pendingAction === 'apply' ? 'Aplicar Split Híbrido' : 'Guardar Split Híbrido'}
                            </h3>
                            <p className="text-[10px] text-zinc-500 uppercase tracking-widest">
                                {hybridName}
                            </p>
                        </div>

                        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-6">
                            <div className="flex items-start gap-3">
                                <AlertTriangleIcon size={18} className="text-amber-600 shrink-0 mt-0.5" />
                                <div>
                                    <p className="text-[10px] font-black text-amber-900 uppercase tracking-widest mb-1">
                                        ¿Hay sesiones existentes?
                                    </p>
                                    <p className="text-[9px] text-amber-700">
                                        ¿Qué deseas hacer con tus ejercicios actuales?
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div className="space-y-3 mb-6">
                            <button
                                onClick={() => handleConfirmMigration(false)}
                                className="w-full p-4 rounded-2xl border-2 border-purple-300 bg-gradient-to-br from-purple-50 to-white text-left hover:shadow-lg transition-all"
                            >
                                <div className="flex items-center gap-3">
                                    <div className="w-10 h-10 rounded-full bg-purple-500 text-white flex items-center justify-center shrink-0">
                                        <SwapIcon size={18} />
                                    </div>
                                    <div>
                                        <p className="text-sm font-black text-purple-900">Migrar ejercicios</p>
                                        <p className="text-[9px] text-purple-700">Reasignar al nuevo split</p>
                                    </div>
                                </div>
                            </button>

                            <button
                                onClick={() => handleConfirmMigration(true)}
                                className="w-full p-4 rounded-2xl border-2 border-red-300 bg-gradient-to-br from-red-50 to-white text-left hover:shadow-lg transition-all"
                            >
                                <div className="flex items-center gap-3">
                                    <div className="w-10 h-10 rounded-full bg-red-500 text-white flex items-center justify-center shrink-0">
                                        <TrashIcon size={18} />
                                    </div>
                                    <div>
                                        <p className="text-sm font-black text-red-900">Empezar desde cero</p>
                                        <p className="text-[9px] text-red-700">Borrar ejercicios actuales</p>
                                    </div>
                                </div>
                            </button>
                        </div>

                        <button
                            onClick={() => setShowMigrationModal(false)}
                            className="w-full py-3 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 hover:text-zinc-700 transition-colors"
                        >
                            Cancelar
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export const SplitAdvancedEditor: React.FC<SplitAdvancedEditorProps> = ({
    program,
    selectedBlockId,
    selectedWeekId,
    onUpdateProgram,
    addToast,
}) => {
    const { handleChangeSplit } = useAppContext();
    
    // Estados
    const [search, setSearch] = useState('');
    const [selectedForCompare, setSelectedForCompare] = useState<string[]>([]);
    const [showCompareModal, setShowCompareModal] = useState(false);
    const [showHybridModal, setShowHybridModal] = useState(false);
    const [pendingSplit, setPendingSplit] = useState<SplitTemplate | null>(null);
    const [showMigrationModal, setShowMigrationModal] = useState(false);
    const [hasSessions, setHasSessions] = useState(false);
    const [showIdealSplitWizard, setShowIdealSplitWizard] = useState(false);
    const [wizardStep, setWizardStep] = useState(0);
    const [wizardAnswers, setWizardAnswers] = useState({
        daysPerWeek: 4,
        timePerSession: 60,
        preference: 'mix' as 'machines' | 'free_weights' | 'mix',
        experience: 'intermedio' as 'principiante' | 'intermedio' | 'avanzado',
        weekStartDay: 1
    });
    const [wizardRecommendations, setWizardRecommendations] = useState<SplitTemplate[]>([]);
    const [showExportImport, setShowExportImport] = useState(false);
    const [splitHistory, setSplitHistory] = useState<{splitId: string, splitName: string, appliedDate: string, splitPattern?: string[]}[]>([]);

    // Cargar historial de splits
    useEffect(() => {
        try {
            const stored = localStorage.getItem('kpkn_split_history');
            if (stored) {
                setSplitHistory(JSON.parse(stored));
            }
        } catch (e) {
            console.error('Error cargando historial:', e);
        }
    }, []);

    // Guardar split aplicado en historial (CON PATRÓN para poder restaurar)
    const addToSplitHistory = useCallback((splitId: string, splitName: string, pattern?: string[]) => {
        const newEntry = { splitId, splitName, appliedDate: new Date().toISOString(), splitPattern: pattern };
        setSplitHistory(prev => {
            const updated = [newEntry, ...prev.slice(0, 9)]; // Últimos 10 splits
            localStorage.setItem('kpkn_split_history', JSON.stringify(updated));
            return updated;
        });
    }, []);

    const isSimple = program.structure === 'simple' || (!program.structure && program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1);

    // Cargar splits personalizados desde localStorage
    const [customSplits, setCustomSplits] = useState<SplitTemplate[]>([]);

    useEffect(() => {
        try {
            const stored = localStorage.getItem('kpkn_custom_splits');
            if (stored) {
                const parsed = JSON.parse(stored);
                setCustomSplits(parsed);
            }
        } catch (e) {
            console.error('Error cargando splits personalizados:', e);
        }
    }, []);

    // Verificar si la semana actual tiene sesiones
    const checkHasSessions = useCallback(() => {
        if (!selectedWeekId) return false;
        const block = program.macrocycles[0]?.blocks?.find(b => b.id === selectedBlockId);
        if (!block) return false;
        const week = block.mesocycles.flatMap(m => m.weeks).find(w => w.id === selectedWeekId);
        if (!week) return false;
        
        // Verificar si alguna sesión tiene ejercicios
        return week.sessions.some(s => s.exercises && s.exercises.length > 0);
    }, [selectedWeekId, selectedBlockId, program.macrocycles]);

    // Filtros - INCLUYE splits personalizados
    const filteredSplits = useMemo(() => {
        const q = search.toLowerCase();
        
        // Splits personalizados
        const customFiltered = customSplits.filter(s => 
            !q || s.name.toLowerCase().includes(q) || s.description?.toLowerCase().includes(q)
        );
        
        // Splits del sistema
        const systemFiltered = SPLIT_TEMPLATES.filter(s => {
            if (s.id === 'custom') return false;
            return !q || s.name.toLowerCase().includes(q) || s.description.toLowerCase().includes(q);
        });
        
        // Combinar: personalizados primero, luego del sistema
        return [...customFiltered, ...systemFiltered];
    }, [search, customSplits]);

    const currentSplit = useMemo(() => 
        program.selectedSplitId ? SPLIT_TEMPLATES.find(s => s.id === program.selectedSplitId) : null,
        [program.selectedSplitId]
    );

    const selectedSplits = useMemo(() => 
        selectedForCompare.map(id => SPLIT_TEMPLATES.find(s => s.id === id)).filter(Boolean) as SplitTemplate[],
        [selectedForCompare]
    );

    // Handlers
    const handleSelectSplit = (splitId: string) => {
        setSelectedForCompare(prev => {
            if (prev.includes(splitId)) {
                return prev.filter(id => id !== splitId);
            }
            if (prev.length >= 3) {
                addToast('Máximo 3 splits', 'danger');
                return prev;
            }
            return [...prev, splitId];
        });
    };

    const handleApplyClick = (split: SplitTemplate) => {
        const hasSess = checkHasSessions();
        setHasSessions(hasSess);
        setPendingSplit(split);
        setShowMigrationModal(true);
    };

    const handleApplySplit = (split: SplitTemplate, startFromScratch: boolean) => {
        const block = program.macrocycles[0]?.blocks?.find(b => b.id === selectedBlockId) || program.macrocycles[0]?.blocks?.[0];
        const week = block?.mesocycles.flatMap(m => m.weeks).find(w => w.id === selectedWeekId) || block?.mesocycles[0]?.weeks[0];

        const scope = isSimple ? 'program' : 'block';

        if (startFromScratch) {
            // Empezar desde cero - limpiar ejercicios
            handleChangeSplit(program.id, split.pattern, split.id, scope, false, program.startDay ?? 1, block?.id, week?.id);
        } else {
            // Migrar - preservar ejercicios
            handleChangeSplit(program.id, split.pattern, split.id, scope, true, program.startDay ?? 1, block?.id, week?.id);
        }

        // ✅ Guardar en historial CON EL PATRÓN
        addToSplitHistory(split.id, split.name, split.pattern);

        // ✅ Resetear splitTrialSeen para mostrar aviso de prueba
        const updated = { ...program, splitTrialSeen: false };
        handleUpdateProgram(updated);

        addToast(`Split "${split.name}" aplicado`, 'success');
        setShowMigrationModal(false);
        setPendingSplit(null);
        setSelectedForCompare([]);
    };

    // ✅ VOLVER A SPLIT ANTERIOR DEL HISTORIAL
    const handleRestoreSplitFromHistory = (historyItem: {splitId: string, splitName: string, splitPattern?: string[]}) => {
        if (!historyItem.splitPattern) {
            addToast('Este split no tiene patrón guardado. Selecciona uno de la galería.', 'danger');
            return;
        }
        
        const block = program.macrocycles[0]?.blocks?.find(b => b.id === selectedBlockId) || program.macrocycles[0]?.blocks?.[0];
        const week = block?.mesocycles.flatMap(m => m.weeks).find(w => w.id === selectedWeekId) || block?.mesocycles[0]?.weeks[0];
        const scope = isSimple ? 'program' : 'block';
        
        // Aplicar el patrón guardado
        handleChangeSplit(program.id, historyItem.splitPattern, historyItem.splitId, scope, true, program.startDay ?? 1, block?.id, week?.id);
        
        addToast(`Split "${historyItem.splitName}" restaurado`, 'success');
        addToSplitHistory(historyItem.splitId, historyItem.splitName, historyItem.splitPattern);
    };

    const handleCompare = () => {
        if (selectedSplits.length < 2) {
            addToast('Selecciona al menos 2 splits', 'danger');
            return;
        }
        setShowCompareModal(true);
    };

    const handleHybrid = () => {
        if (selectedSplits.length < 2) {
            addToast('Selecciona 2 splits', 'danger');
            return;
        }
        setShowHybridModal(true);
    };

    const handleApplyHybrid = (pattern: string[], name: string, startFromScratch: boolean) => {
        const block = program.macrocycles[0]?.blocks?.find(b => b.id === selectedBlockId) || program.macrocycles[0]?.blocks?.[0];
        const week = block?.mesocycles.flatMap(m => m.weeks).find(w => w.id === selectedWeekId) || block?.mesocycles[0]?.weeks[0];
        const scope = isSimple ? 'program' : 'block';
        
        handleChangeSplit(program.id, pattern, 'hybrid', scope, !startFromScratch, program.startDay ?? 1, block?.id, week?.id);
        addToast(`Split híbrido "${name}" aplicado`, 'success');
        setSelectedForCompare([]);
    };

    const handleSaveHybridAsCustom = (pattern: string[], name: string) => {
        // Guardar como split personalizado en localStorage
        const customSplits = JSON.parse(localStorage.getItem('kpkn_custom_splits') || '[]');
        customSplits.push({
            id: `custom_${Date.now()}`,
            name,
            description: 'Split híbrido personalizado',
            tags: ['Personalizado'],
            pattern,
            difficulty: 'Intermedio',
            pros: ['Personalizado'],
            cons: ['Requiere ajuste']
        });
        localStorage.setItem('kpkn_custom_splits', JSON.stringify(customSplits));
        setCustomSplits(customSplits); // ✅ Actualizar estado local
        addToast(`Split "${name}" guardado como personalizado`, 'success');
        setSelectedForCompare([]);
    };

    const handleDeleteCustomSplit = (splitId: string) => {
        const customSplits = JSON.parse(localStorage.getItem('kpkn_custom_splits') || '[]');
        const filtered = customSplits.filter((s: any) => s.id !== splitId);
        localStorage.setItem('kpkn_custom_splits', JSON.stringify(filtered));
        setCustomSplits(filtered); // ✅ Actualizar estado local
        addToast('Split personalizado eliminado', 'success');
    };

    // ✅ EXPORTAR/IMPORTAR SPLITS
    const handleExportSplits = () => {
        const customSplits = JSON.parse(localStorage.getItem('kpkn_custom_splits') || '[]');
        const data = {
            version: '1.0',
            exportDate: new Date().toISOString(),
            splits: customSplits
        };
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `kpkn-splits-${new Date().toISOString().split('T')[0]}.json`;
        a.click();
        URL.revokeObjectURL(url);
        addToast('Splits exportados', 'success');
    };

    const handleImportSplits = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;
        
        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const data = JSON.parse(e.target?.result as string);
                if (data.splits && Array.isArray(data.splits)) {
                    const currentSplits = JSON.parse(localStorage.getItem('kpkn_custom_splits') || '[]');
                    const merged = [...currentSplits, ...data.splits];
                    localStorage.setItem('kpkn_custom_splits', JSON.stringify(merged));
                    setCustomSplits(merged);
                    addToast(`${data.splits.length} splits importados`, 'success');
                }
            } catch (err) {
                addToast('Error al importar. Archivo inválido.', 'danger');
            }
        };
        reader.readAsText(file);
        event.target.value = ''; // Reset input
    };

    // Handlers del Wizard "Mi Split Ideal"
    const handleOpenIdealSplitWizard = () => {
        // Intentar obtener experiencia del usuario de localStorage (de volumen de entrenamiento)
        const volumeCalibration = localStorage.getItem('kpkn_volume_calibration');
        let experience: 'principiante' | 'intermedio' | 'avanzado' = 'intermedio';
        
        if (volumeCalibration) {
            try {
                const data = JSON.parse(volumeCalibration);
                if (data.level === 'alto' || data.level === 'muy_alto') experience = 'avanzado';
                else if (data.level === 'bajo') experience = 'principiante';
                else experience = 'intermedio';
            } catch {}
        }
        
        setWizardAnswers(prev => ({ ...prev, experience }));
        setWizardStep(0);
        setShowIdealSplitWizard(true);
    };

    const handleWizardNext = () => {
        if (wizardStep < 5) setWizardStep(wizardStep + 1);  // ✅ Ahora llega hasta paso 5 (Loading)
        else handleWizardComplete();
    };

    const handleWizardBack = () => {
        if (wizardStep > 0) setWizardStep(wizardStep - 1);
        else setShowIdealSplitWizard(false);
    };

    const handleWizardComplete = () => {
        // Encontrar splits que coincidan con las respuestas
        const recommendedSplits = SPLIT_TEMPLATES.filter(split => {
            const days = split.pattern.filter(d => d.toLowerCase() !== 'descanso').length;
            const matchesDays = Math.abs(days - wizardAnswers.daysPerWeek) <= 1;
            const matchesExperience = split.difficulty.toLowerCase() === wizardAnswers.experience || 
                (wizardAnswers.experience === 'intermedio' && split.difficulty !== 'avanzado');
            
            // Tags de máquinas/pesos libres (simplificado)
            const isMachineHeavy = split.name.includes('Máquina') || split.name.includes('Cable');
            const isFreeWeightHeavy = split.name.includes('Barra') || split.name.includes('Peso Libre') || 
                ['Powerlifting', '5x5', 'Texas', 'Sheiko', 'Bulgarian'].some(kw => split.name.includes(kw));
            
            let matchesPreference = true;
            if (wizardAnswers.preference === 'machines') matchesPreference = !isFreeWeightHeavy;
            else if (wizardAnswers.preference === 'free_weights') matchesPreference = !isMachineHeavy;
            
            return matchesDays && matchesExperience && matchesPreference;
        }).slice(0, 5); // ✅ Mostrar hasta 5 recomendaciones

        if (recommendedSplits.length > 0) {
            setWizardRecommendations(recommendedSplits);
            setWizardStep(5); // Paso de ver recomendaciones
        } else {
            // Fallback a Full Body x3 o Upper/Lower x4
            const fallback = SPLIT_TEMPLATES.find(s => wizardAnswers.daysPerWeek <= 3 ? s.id === 'fullbody_x3' : s.id === 'ul_x4');
            if (fallback) {
                setWizardRecommendations([fallback]);
                setWizardStep(5);
            }
        }
    };

    return (
        <div className="pb-6">
            {/* Header con selector múltiple y Mi Split Ideal */}
            <div className="px-4 mb-4 space-y-3">
                {/* Botón Mi Split Ideal */}
                <button
                    onClick={handleOpenIdealSplitWizard}
                    className="w-full py-4 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/30 flex items-center justify-center gap-3 hover:opacity-90 transition-all"
                >
                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-white">
                        <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                        <path d="M2 17l10 5 10-5"/>
                        <path d="M2 12l10 5 10-5"/>
                    </svg>
                    <div className="text-left">
                        <p className="text-sm font-black uppercase tracking-[0.15em]">Mi Split Ideal</p>
                        <p className="text-[9px] text-white/80">Wizard personalizado</p>
                    </div>
                </button>

                {/* Botones de utilidad */}
                <div className="flex gap-2">
                    <button
                        onClick={() => setShowExportImport(true)}
                        className="flex-1 py-3 rounded-2xl bg-white border-2 border-zinc-200 text-zinc-600 hover:border-cyan-500 hover:text-cyan-600 transition-all flex items-center justify-center gap-2"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                        <span className="text-[9px] font-black uppercase tracking-[0.15em]">Exportar</span>
                    </button>
                    <label className="flex-1 py-3 rounded-2xl bg-white border-2 border-zinc-200 text-zinc-600 hover:border-cyan-500 hover:text-cyan-600 transition-all flex items-center justify-center gap-2 cursor-pointer">
                        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                        <span className="text-[9px] font-black uppercase tracking-[0.15em]">Importar</span>
                        <input type="file" accept=".json" onChange={handleImportSplits} className="hidden" />
                    </label>
                </div>

                {/* Historial de splits */}
                {splitHistory.length > 0 && (
                    <div className="bg-zinc-50 rounded-2xl p-3">
                        <p className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-400 mb-2">Usados recientemente</p>
                        <div className="flex gap-2 overflow-x-auto custom-scrollbar">
                            {splitHistory.slice(0, 5).map((item, i) => (
                                <div key={i} className="flex-shrink-0 bg-white rounded-xl px-3 py-2 border border-zinc-200">
                                    <p className="text-[9px] font-black text-zinc-700 truncate max-w-[120px]">{item.splitName}</p>
                                    <p className="text-[7px] text-zinc-400">{new Date(item.appliedDate).toLocaleDateString()}</p>
                                    {item.splitPattern && (
                                        <button
                                            onClick={() => handleRestoreSplitFromHistory(item)}
                                            className="text-[7px] font-black uppercase tracking-widest text-cyan-600 hover:text-cyan-700 mt-1"
                                        >
                                            ↻ Restaurar
                                        </button>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* Barra de búsqueda */}
                <div className="relative">
                    <SearchIcon size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-zinc-400" />
                    <input
                        type="text"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Buscar split..."
                        className="w-full bg-white rounded-[24px] pl-12 pr-4 py-3.5 text-sm text-zinc-900 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500/30 border border-zinc-200 transition-all"
                    />
                </div>

                {/* Acciones de selección múltiple */}
                {selectedForCompare.length > 0 && (
                    <motion.div
                        initial={{ opacity: 0, y: -10 }}
                        animate={{ opacity: 1, y: 0 }}
                        className="flex items-center justify-between bg-gradient-to-r from-purple-500 to-purple-600 text-white rounded-2xl p-4"
                    >
                        <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center font-black text-sm">
                                {selectedForCompare.length}
                            </div>
                            <span className="text-[10px] font-black uppercase tracking-widest">
                                {selectedForCompare.length === 1 ? 'Split seleccionado' : 'Splits seleccionados'}
                            </span>
                        </div>
                        <div className="flex gap-2">
                            {selectedForCompare.length >= 2 && (
                                <>
                                    <button
                                        onClick={handleCompare}
                                        className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/20 hover:bg-white/30 transition-colors text-[9px] font-black uppercase tracking-[0.15em]"
                                    >
                                        <CopyIcon size={14} />
                                        Comparar
                                    </button>
                                    <button
                                        onClick={handleHybrid}
                                        className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/20 hover:bg-white/30 transition-colors text-[9px] font-black uppercase tracking-[0.15em]"
                                    >
                                        <MergeIcon size={14} />
                                        Hibridizar
                                    </button>
                                </>
                            )}
                            <button
                                onClick={() => setSelectedForCompare([])}
                                className="w-8 h-8 rounded-full bg-white/20 hover:bg-white/30 transition-colors flex items-center justify-center"
                            >
                                <XIcon size={14} />
                            </button>
                        </div>
                    </motion.div>
                )}
            </div>

            {/* Galería de splits */}
            <div className="px-4 space-y-3">
                {filteredSplits.map(split => {
                    const isCustom = split.id.startsWith('custom_');
                    const isCurrent = split.id === program.selectedSplitId;
                    const isSelected = selectedForCompare.includes(split.id);

                    return (
                        <motion.div
                            key={split.id}
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            className={`bg-white rounded-2xl border overflow-hidden transition-all ${
                                isCurrent ? 'border-purple-300 shadow-lg shadow-purple-500/10' : 'border-zinc-200/60'
                            }`}
                        >
                            <div className="p-4">
                                {/* Header */}
                                <div className="flex items-start gap-3 mb-3">
                                    {/* Checkbox de selección */}
                                    <button
                                        onClick={() => handleSelectSplit(split.id)}
                                        className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center shrink-0 transition-all ${
                                            isSelected
                                                ? 'bg-purple-500 border-purple-500 text-white'
                                                : 'border-zinc-300 hover:border-purple-400'
                                        }`}
                                    >
                                        {isSelected && <CheckIcon size={14} />}
                                    </button>

                                    <div className="flex-1">
                                        <div className="flex items-center gap-2 flex-wrap mb-1">
                                            <span className={`text-sm font-black ${isCurrent ? 'text-purple-900' : 'text-zinc-900'}`}>
                                                {split.name}
                                            </span>
                                            {isCurrent && (
                                                <span className="text-[8px] font-black uppercase px-2 py-0.5 rounded-full bg-gradient-to-r from-purple-500 to-purple-600 text-white">
                                                    Actual
                                                </span>
                                            )}
                                            {isCustom && (
                                                <span className="text-[8px] font-black uppercase px-2 py-0.5 rounded-full bg-gradient-to-r from-cyan-500 to-blue-600 text-white">
                                                    Personalizado
                                                </span>
                                            )}
                                        </div>
                                        <p className={`text-[10px] ${isCurrent ? 'text-purple-700/70' : 'text-zinc-500'}`}>
                                            {split.description}
                                        </p>
                                    </div>

                                    <div className="flex items-center gap-2">
                                        <div className={`text-lg font-black ${isCurrent ? 'text-purple-600' : 'text-zinc-400'}`}>
                                            {split.pattern.filter(d => d.toLowerCase() !== 'descanso').length}d
                                        </div>
                                        {isCustom && (
                                            <button
                                                onClick={() => handleDeleteCustomSplit(split.id)}
                                                className="w-8 h-8 rounded-xl bg-red-50 hover:bg-red-100 transition-colors flex items-center justify-center"
                                            >
                                                <TrashIcon size={16} className="text-red-500" />
                                            </button>
                                        )}
                                    </div>
                                </div>

                                {/* Días */}
                                <div className="flex gap-1.5 mb-4 ml-9">
                                    {split.pattern.map((day, i) => (
                                        <div
                                            key={i}
                                            className={`flex-1 h-2 rounded-full ${
                                                day.toLowerCase() === 'descanso'
                                                    ? 'bg-zinc-100'
                                                    : isCurrent ? 'bg-gradient-to-r from-purple-500 to-purple-600' : 'bg-purple-400/60'
                                            }`}
                                            title={day}
                                        />
                                    ))}
                                </div>

                                {/* Acciones */}
                                {!isCurrent ? (
                                    <div className="flex gap-2 ml-9">
                                        <button
                                            onClick={() => handleApplyClick(split)}
                                            className="flex-1 py-2.5 rounded-xl bg-gradient-to-r from-purple-500 to-purple-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg shadow-purple-500/30"
                                        >
                                            Aplicar
                                        </button>
                                    </div>
                                ) : (
                                    <div className="flex items-center gap-2 text-purple-600 ml-9">
                                        <CheckIcon size={16} />
                                        <span className="text-[9px] font-black uppercase tracking-[0.15em]">Split activo</span>
                                    </div>
                                )}
                            </div>

                            {/* Expansible con pros/contras */}
                            <ExpandableDetails split={split} />
                        </motion.div>
                    );
                })}
            </div>

            {/* Modales */}
            <MigrationConfirmModal
                isOpen={showMigrationModal}
                splitName={pendingSplit?.name || ''}
                hasSessions={hasSessions}
                onMigrate={() => pendingSplit && handleApplySplit(pendingSplit, false)}
                onStartFromScratch={() => pendingSplit && handleApplySplit(pendingSplit, true)}
                onCancel={() => {
                    setShowMigrationModal(false);
                    setPendingSplit(null);
                }}
            />

            <CompareModal
                isOpen={showCompareModal}
                selectedSplits={selectedSplits}
                onRemove={(splitId) => setSelectedForCompare(prev => prev.filter(id => id !== splitId))}
                onClose={() => setShowCompareModal(false)}
                onApply={(split) => handleApplyClick(split)}
            />

            <HybridModal
                isOpen={showHybridModal}
                selectedSplits={selectedSplits}
                onClose={() => setShowHybridModal(false)}
                onApply={handleApplyHybrid}
                onSaveAsCustom={handleSaveHybridAsCustom}
            />

            {/* Wizard Modal - Mi Split Ideal */}
            {showIdealSplitWizard && (
                <IdealSplitWizardModal
                    isOpen={showIdealSplitWizard}
                    step={wizardStep}
                    answers={wizardAnswers}
                    onAnswerChange={setWizardAnswers}
                    onNext={handleWizardNext}
                    onBack={handleWizardBack}
                    onClose={() => setShowIdealSplitWizard(false)}
                />
            )}

            {/* Modal Exportar/Importar */}
            {showExportImport && (
                <ExportImportModal
                    isOpen={showExportImport}
                    onExport={handleExportSplits}
                    onImport={handleImportSplits}
                    onClose={() => setShowExportImport(false)}
                />
            )}
        </div>
    );
};

// Componente ExpandableDetails
const ExpandableDetails: React.FC<{ split: SplitTemplate }> = ({ split }) => {
    const [expanded, setExpanded] = useState(false);

    return (
        <>
            <button
                onClick={() => setExpanded(!expanded)}
                className="w-full py-2 px-4 bg-zinc-50 border-t border-zinc-100 flex items-center justify-between text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 hover:bg-zinc-100 transition-colors"
            >
                {expanded ? 'Ocultar detalles' : 'Ver pros y contras'}
                {expanded ? <ChevronDownIcon size={14} /> : <ChevronRightIcon size={14} />}
            </button>

            <AnimatePresence>
                {expanded && (
                    <motion.div
                        initial={{ height: 0 }}
                        animate={{ height: 'auto' }}
                        exit={{ height: 0 }}
                        className="overflow-hidden"
                    >
                        <div className="p-4 grid grid-cols-2 gap-3">
                            <div>
                                <h4 className="text-[9px] font-black uppercase tracking-[0.15em] text-green-600 mb-2 flex items-center gap-1">
                                    <CheckIcon size={12} /> Pros
                                </h4>
                                <ul className="space-y-1">
                                    {split.pros?.map((pro, i) => (
                                        <li key={i} className="text-[10px] text-zinc-600">• {pro}</li>
                                    ))}
                                </ul>
                            </div>
                            <div>
                                <h4 className="text-[9px] font-black uppercase tracking-[0.15em] text-red-600 mb-2 flex items-center gap-1">
                                    <XIcon size={12} /> Contras
                                </h4>
                                <ul className="space-y-1">
                                    {split.cons?.map((con, i) => (
                                        <li key={i} className="text-[10px] text-zinc-600">• {con}</li>
                                    ))}
                                </ul>
                            </div>
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>
        </>
    );
};

// Wizard Modal - Mi Split Ideal
const IdealSplitWizardModal: React.FC<{
    isOpen: boolean;
    step: number;
    answers: {
        daysPerWeek: number;
        timePerSession: number;
        preference: 'machines' | 'free_weights' | 'mix';
        experience: 'principiante' | 'intermedio' | 'avanzado';
        weekStartDay: number;
    };
    onAnswerChange: React.Dispatch<React.SetStateAction<any>>;
    onNext: () => void;
    onBack: () => void;
    onClose: () => void;
}> = ({ isOpen, step, answers, onAnswerChange, onNext, onBack, onClose }) => {
    if (!isOpen) return null;

    const DAYS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
    const STEPS = [
        { title: 'Disponibilidad Semanal', subtitle: '¿Cuántos días puedes entrenar?' },
        { title: 'Tiempo Disponible', subtitle: '¿Cuánto tiempo por sesión?' },
        { title: 'Preferencia de Equipo', subtitle: '¿Qué prefieres usar?' },
        { title: 'Nivel de Experiencia', subtitle: '¿Cuál es tu nivel?' },
        { title: 'Inicio de Semana', subtitle: '¿Qué día comienza tu semana?' },
        { title: '¡Listo!', subtitle: 'Encontrando tu split ideal...' }
    ];

    const progress = ((step + 1) / STEPS.length) * 100;

    return (
        <div className="fixed inset-0 z-[300] bg-black/20 backdrop-blur-md flex items-center justify-center p-4" onClick={onClose}>
            <div className="bg-white/90 backdrop-blur-xl rounded-[2.5rem] w-full max-w-md p-6 shadow-2xl relative border border-white/50" onClick={e => e.stopPropagation()}>
                {/* Progress bar */}
                <div className="absolute top-0 left-0 right-0 h-1.5 bg-zinc-100 rounded-t-[2.5rem] overflow-hidden">
                    <div 
                        className="h-full bg-gradient-to-r from-cyan-500 to-blue-600 transition-all duration-500"
                        style={{ width: `${progress}%` }}
                    />
                </div>

                <button onClick={onClose} className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-600">
                    <XIcon size={20} />
                </button>

                {/* Header */}
                <div className="mb-6 mt-2">
                    <div className="w-16 h-16 mx-auto bg-gradient-to-br from-cyan-100 to-blue-100 rounded-full flex items-center justify-center mb-4">
                        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-cyan-600">
                            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                            <path d="M2 17l10 5 10-5"/>
                            <path d="M2 12l10 5 10-5"/>
                        </svg>
                    </div>
                    <h3 className="text-lg font-black text-zinc-900 uppercase tracking-tight text-center">
                        {STEPS[step].title}
                    </h3>
                    <p className="text-[10px] text-zinc-500 uppercase tracking-widest text-center mt-1">
                        {STEPS[step].subtitle}
                    </p>
                </div>

                {/* Step content */}
                <div className="mb-6">
                    {/* Paso 0: Días por semana */}
                    {step === 0 && (
                        <div className="grid grid-cols-4 gap-2">
                            {[2, 3, 4, 5, 6].map(days => (
                                <button
                                    key={days}
                                    onClick={() => onAnswerChange((prev: any) => ({ ...prev, daysPerWeek: days }))}
                                    className={`py-4 rounded-2xl font-black transition-all ${
                                        answers.daysPerWeek === days
                                            ? 'bg-gradient-to-br from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/30'
                                            : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
                                    }`}
                                >
                                    {days} días
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Paso 1: Tiempo por sesión */}
                    {step === 1 && (
                        <div className="space-y-3">
                            {[
                                { value: 30, label: '30 min', desc: 'Rápido' },
                                { value: 45, label: '45 min', desc: 'Moderado' },
                                { value: 60, label: '60 min', desc: 'Estándar' },
                                { value: 90, label: '90 min', desc: 'Completo' },
                                { value: 120, label: '120 min', desc: 'Extremo' }
                            ].map(option => (
                                <button
                                    key={option.value}
                                    onClick={() => onAnswerChange((prev: any) => ({ ...prev, timePerSession: option.value }))}
                                    className={`w-full p-4 rounded-2xl border-2 text-left transition-all ${
                                        answers.timePerSession === option.value
                                            ? 'border-cyan-500 bg-gradient-to-br from-cyan-50 to-blue-50'
                                            : 'border-zinc-200 bg-white hover:border-cyan-300'
                                    }`}
                                >
                                    <p className="text-sm font-black text-zinc-900">{option.label}</p>
                                    <p className="text-[9px] text-zinc-500 uppercase tracking-widest">{option.desc}</p>
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Paso 2: Preferencia de equipo */}
                    {step === 2 && (
                        <div className="space-y-3">
                            {[
                                { value: 'free_weights', label: 'Pesos Libres', desc: 'Barras, mancuernas, básico', icon: '🏋️' },
                                { value: 'mix', label: 'Mixto', desc: 'Combinación de ambos', icon: '⚖️' },
                                { value: 'machines', label: 'Máquinas', desc: 'Poleas, máquinas guiadas', icon: '🤖' }
                            ].map(option => (
                                <button
                                    key={option.value}
                                    onClick={() => onAnswerChange((prev: any) => ({ ...prev, preference: option.value }))}
                                    className={`w-full p-4 rounded-2xl border-2 text-left transition-all flex items-center gap-4 ${
                                        answers.preference === option.value
                                            ? 'border-cyan-500 bg-gradient-to-br from-cyan-50 to-blue-50'
                                            : 'border-zinc-200 bg-white hover:border-cyan-300'
                                    }`}
                                >
                                    <span className="text-3xl">{option.icon}</span>
                                    <div>
                                        <p className="text-sm font-black text-zinc-900">{option.label}</p>
                                        <p className="text-[9px] text-zinc-500 uppercase tracking-widest">{option.desc}</p>
                                    </div>
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Paso 3: Experiencia */}
                    {step === 3 && (
                        <div className="space-y-3">
                            {[
                                { value: 'principiante', label: 'Principiante', desc: '< 6 meses entrenando', color: 'from-green-500 to-emerald-600' },
                                { value: 'intermedio', label: 'Intermedio', desc: '6 meses - 2 años', color: 'from-yellow-500 to-orange-600' },
                                { value: 'avanzado', label: 'Avanzado', desc: '> 2 años entrenando', color: 'from-red-500 to-rose-600' }
                            ].map(option => (
                                <button
                                    key={option.value}
                                    onClick={() => onAnswerChange((prev: any) => ({ ...prev, experience: option.value }))}
                                    className={`w-full p-4 rounded-2xl border-2 text-left transition-all ${
                                        answers.experience === option.value
                                            ? `border-transparent bg-gradient-to-br ${option.color} text-white shadow-lg`
                                            : 'border-zinc-200 bg-white hover:border-zinc-300'
                                    }`}
                                >
                                    <p className={`text-sm font-black ${answers.experience === option.value ? 'text-white' : 'text-zinc-900'}`}>{option.label}</p>
                                    <p className={`text-[9px] uppercase tracking-widest ${answers.experience === option.value ? 'text-white/80' : 'text-zinc-500'}`}>{option.desc}</p>
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Paso 4: Inicio de semana */}
                    {step === 4 && (
                        <div className="space-y-2">
                            {DAYS.map((day, index) => (
                                <button
                                    key={day}
                                    onClick={() => onAnswerChange((prev: any) => ({ ...prev, weekStartDay: index }))}
                                    className={`w-full p-4 rounded-2xl text-left transition-all ${
                                        answers.weekStartDay === index
                                            ? 'bg-gradient-to-br from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/30'
                                            : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
                                    }`}
                                >
                                    <span className="text-sm font-black">{day}</span>
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Paso 5: Recomendaciones */}
                    {step === 5 && (
                        <div className="space-y-3 max-h-[400px] overflow-y-auto custom-scrollbar pr-2">
                            <div className="text-center mb-4">
                                <div className="w-16 h-16 mx-auto bg-gradient-to-br from-green-100 to-emerald-100 rounded-full flex items-center justify-center mb-2">
                                    <CheckIcon size={32} className="text-green-600" />
                                </div>
                                <p className="text-sm text-zinc-600">Encontramos {wizardRecommendations.length} splits ideales para ti:</p>
                            </div>
                            
                            {/* ✅ Mensaje de prueba */}
                            <div className="bg-cyan-50 border border-cyan-200 rounded-xl p-3 mb-2">
                                <p className="text-[9px] text-cyan-800 leading-relaxed">
                                    💡 <strong>Consejo:</strong> Prueba este split por una semana para ver si se ajusta a tus necesidades. Siempre podrás volver acá si necesitas otro.
                                </p>
                            </div>
                            
                            {wizardRecommendations.map((split, index) => (
                                <button
                                    key={split.id}
                                    onClick={() => {
                                        setPendingSplit(split);
                                        setHasSessions(checkHasSessions());
                                        setShowIdealSplitWizard(false);
                                        setShowMigrationModal(true);
                                    }}
                                    className="w-full p-4 rounded-2xl border-2 border-zinc-200 hover:border-cyan-500 bg-white hover:bg-gradient-to-br hover:from-cyan-50 hover:to-blue-50 transition-all text-left group"
                                >
                                    <div className="flex items-start justify-between gap-3">
                                        <div className="flex-1">
                                            <div className="flex items-center gap-2 mb-1">
                                                <span className="text-[10px] font-black text-cyan-600 bg-cyan-50 px-2 py-0.5 rounded-full">
                                                    #{index + 1} Recomendado
                                                </span>
                                                <span className="text-[8px] font-black uppercase text-zinc-400 bg-zinc-100 px-2 py-0.5 rounded-full">
                                                    {split.difficulty}
                                                </span>
                                            </div>
                                            <h4 className="text-sm font-black text-zinc-900 group-hover:text-cyan-700 transition-colors">
                                                {split.name}
                                            </h4>
                                            <p className="text-[9px] text-zinc-500 mt-1 line-clamp-2">
                                                {split.description}
                                            </p>
                                        </div>
                                        <div className="text-right">
                                            <div className="text-2xl font-black text-cyan-600">
                                                {split.pattern.filter(d => d.toLowerCase() !== 'descanso').length}d
                                            </div>
                                            <div className="text-[7px] text-zinc-400 uppercase">días</div>
                                        </div>
                                    </div>
                                </button>
                            ))}
                        </div>
                    )}
                </div>

                {/* Navigation */}
                {step < 5 && (  // ✅ Muestra navegación hasta paso 4, paso 5 es recomendaciones
                    <div className="flex gap-3">
                        {step > 0 && (
                            <button
                                onClick={onBack}
                                className="flex-1 py-4 rounded-2xl border-2 border-zinc-200 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-600 hover:bg-zinc-50 transition-all"
                            >
                                Atrás
                            </button>
                        )}
                        <button
                            onClick={onNext}
                            className="flex-1 py-4 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg shadow-cyan-500/30"
                        >
                            {step === 4 ? 'Encontrar Split' : 'Siguiente'}
                        </button>
                    </div>
                )}
                
                {step === 5 && (
                    <button
                        onClick={onClose}
                        className="w-full py-4 rounded-2xl border-2 border-zinc-200 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-600 hover:bg-zinc-50 transition-all"
                    >
                        Cerrar
                    </button>
                )}
            </div>
        </div>
    );
};

// Modal Exportar/Importar Splits
const ExportImportModal: React.FC<{
    isOpen: boolean;
    onExport: () => void;
    onImport: (event: React.ChangeEvent<HTMLInputElement>) => void;
    onClose: () => void;
}> = ({ isOpen, onExport, onImport, onClose }) => {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={onClose}>
            <div className="bg-white rounded-[2rem] w-full max-w-sm p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                <button onClick={onClose} className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-600">
                    <XIcon size={20} />
                </button>

                <div className="text-center mb-6">
                    <div className="w-16 h-16 mx-auto bg-gradient-to-br from-cyan-100 to-blue-100 rounded-full flex items-center justify-center mb-4">
                        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-cyan-600">
                            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                            <polyline points="7 10 12 15 17 10"></polyline>
                            <line x1="12" y1="15" x2="12" y2="3"></line>
                        </svg>
                    </div>
                    <h3 className="text-lg font-black text-zinc-900 uppercase tracking-tight">
                        Gestionar Splits
                    </h3>
                    <p className="text-[10px] text-zinc-500 uppercase tracking-widest mt-1">
                        Exportar o importar splits personalizados
                    </p>
                </div>

                <div className="space-y-3 mb-6">
                    <button
                        onClick={onExport}
                        className="w-full p-4 rounded-2xl border-2 border-cyan-300 bg-gradient-to-br from-cyan-50 to-white text-left hover:shadow-lg transition-all flex items-center gap-3"
                    >
                        <div className="w-10 h-10 rounded-full bg-cyan-500 text-white flex items-center justify-center shrink-0">
                            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                        </div>
                        <div>
                            <p className="text-sm font-black text-cyan-900">Exportar Splits</p>
                            <p className="text-[9px] text-cyan-700">Descargar archivo JSON</p>
                        </div>
                    </button>

                    <label className="w-full p-4 rounded-2xl border-2 border-blue-300 bg-gradient-to-br from-blue-50 to-white text-left hover:shadow-lg transition-all flex items-center gap-3 cursor-pointer">
                        <div className="w-10 h-10 rounded-full bg-blue-500 text-white flex items-center justify-center shrink-0">
                            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                        </div>
                        <div>
                            <p className="text-sm font-black text-blue-900">Importar Splits</p>
                            <p className="text-[9px] text-blue-700">Seleccionar archivo JSON</p>
                        </div>
                        <input type="file" accept=".json" onChange={onImport} className="hidden" />
                    </label>
                </div>

                <button
                    onClick={onClose}
                    className="w-full py-3 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg"
                >
                    Cerrar
                </button>
            </div>
        </div>
    );
};

export default SplitAdvancedEditor;
