import React, { useState, useEffect, useRef } from 'react';
import { Session, Exercise, ExerciseMuscleInfo, WarmupSetDefinition } from '../../types';
import { XIcon, SearchIcon, PlusIcon, TrashIcon, CheckIcon, LayersIcon } from '../icons';
import Button from '../ui/Button';

interface DrawerProps {
    isOpen: boolean;
    onClose: () => void;
    position?: 'right' | 'bottom';
    height?: string;
    title: string;
    children: React.ReactNode;
}

const Drawer: React.FC<DrawerProps> = ({ isOpen, onClose, position = 'right', height = '80%', title, children }) => {
    if (!isOpen) return null;

    const isBottom = position === 'bottom';
    const posClasses = isBottom
        ? `bottom-0 left-0 right-0 rounded-t-2xl`
        : `top-0 right-0 bottom-0 w-[340px] max-w-[90vw]`;
    const animClass = isBottom ? 'animate-slide-up' : 'animate-slide-left';

    return (
        <>
            <div className="fixed inset-0 z-[110] bg-black/40" onClick={onClose} />
            <div
                className={`fixed z-[111] bg-[#111] border-white/[0.08] flex flex-col ${posClasses} ${animClass}`}
                style={isBottom ? { height, borderTop: '1px solid rgba(255,255,255,0.08)' } : { borderLeft: '1px solid rgba(255,255,255,0.08)' }}
            >
                <div className="flex items-center justify-between px-4 py-3 border-b border-white/[0.08] shrink-0">
                    <h3 className="text-sm font-semibold text-white">{title}</h3>
                    <button onClick={onClose} className="text-[#555] hover:text-white transition-colors">
                        <XIcon size={16} />
                    </button>
                </div>
                <div className="flex-1 overflow-y-auto custom-scrollbar">
                    {children}
                </div>
            </div>
        </>
    );
};

/* ═══════════ Transfer Drawer ═══════════ */
interface TransferDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    weekSessions: Session[];
    activeSessionId: string;
    session: Session;
    onTransfer: (mode: 'export' | 'import', targetId: string) => void;
}

export const TransferDrawer: React.FC<TransferDrawerProps> = ({ isOpen, onClose, weekSessions, activeSessionId, session, onTransfer }) => {
    const [mode, setMode] = useState<'export' | 'import'>('export');
    const [targetId, setTargetId] = useState('');

    return (
        <Drawer isOpen={isOpen} onClose={onClose} title="Transferencia de Sesión" position="right">
            <div className="p-4 space-y-4">
                <div className="flex gap-1 bg-white/5 p-1 rounded-lg">
                    <button onClick={() => setMode('export')} className={`flex-1 py-2 text-[10px] font-bold rounded-md transition-all ${mode === 'export' ? 'bg-white text-black' : 'text-[#999]'}`}>Exportar</button>
                    <button onClick={() => setMode('import')} className={`flex-1 py-2 text-[10px] font-bold rounded-md transition-all ${mode === 'import' ? 'bg-white text-black' : 'text-[#999]'}`}>Importar</button>
                </div>
                <p className="text-xs text-[#999]">Selecciona la sesión de {mode === 'export' ? 'destino' : 'origen'}:</p>
                <div className="space-y-2">
                    {weekSessions.filter(s => s.id !== activeSessionId).map(s => (
                        <button key={s.id} onClick={() => setTargetId(s.id)} className={`w-full p-3 text-left rounded-lg border text-xs font-medium transition-all ${targetId === s.id ? 'bg-[#FC4C02]/10 border-[#FC4C02]/30 text-white' : 'bg-white/[0.02] border-white/[0.06] text-[#999] hover:border-white/10'}`}>
                            {s.name || `Sesión Día ${s.dayOfWeek}`}
                            <span className="block text-[10px] text-[#555] mt-0.5">{s.parts?.reduce((acc, p) => acc + p.exercises.length, 0) || 0} Ejercicios</span>
                        </button>
                    ))}
                </div>
                {targetId && (
                    <button onClick={() => { onTransfer(mode, targetId); onClose(); }} className="w-full py-2.5 rounded-lg bg-[#FC4C02] text-white text-xs font-bold hover:brightness-110 transition-all">
                        Confirmar Transferencia
                    </button>
                )}
            </div>
        </Drawer>
    );
};

/* ═══════════ Warmup Drawer ═══════════ */
interface WarmupDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    exerciseName: string;
    warmupSets: WarmupSetDefinition[];
    onSave: (sets: WarmupSetDefinition[]) => void;
}

export const WarmupDrawer: React.FC<WarmupDrawerProps> = ({ isOpen, onClose, exerciseName, warmupSets, onSave }) => {
    const [sets, setSets] = useState<WarmupSetDefinition[]>([]);

    useEffect(() => {
        if (isOpen) setSets(warmupSets.length > 0 ? [...warmupSets] : [{ id: crypto.randomUUID(), percentageOfWorkingWeight: 50, targetReps: 10 }]);
    }, [isOpen, warmupSets]);

    const addSet = () => {
        const last = sets[sets.length - 1];
        setSets([...sets, {
            id: crypto.randomUUID(),
            percentageOfWorkingWeight: last ? Math.min(90, last.percentageOfWorkingWeight + 10) : 50,
            targetReps: last ? Math.max(1, Math.floor(last.targetReps / 2)) : 10,
        }]);
    };

    return (
        <Drawer isOpen={isOpen} onClose={onClose} title={`Aproximación: ${exerciseName}`} position="bottom" height="50%">
            <div className="p-4 space-y-3">
                {sets.map((set, i) => (
                    <div key={set.id} className="flex items-center gap-2">
                        <span className="w-6 text-xs font-mono text-[#999] text-center">{i + 1}</span>
                        <div className="flex-1">
                            <span className="text-[10px] text-[#555] block">Carga %</span>
                            <input type="number" value={set.percentageOfWorkingWeight} onChange={e => { const u = [...sets]; u[i].percentageOfWorkingWeight = parseFloat(e.target.value); setSets(u); }} className="w-full bg-transparent border-b border-white/10 focus:border-[#FC4C02] text-sm font-mono text-white py-0.5 outline-none" />
                        </div>
                        <div className="w-16">
                            <span className="text-[10px] text-[#555] block">Reps</span>
                            <input type="number" value={set.targetReps} onChange={e => { const u = [...sets]; u[i].targetReps = parseFloat(e.target.value); setSets(u); }} className="w-full bg-transparent border-b border-white/10 focus:border-[#FC4C02] text-sm font-mono text-white py-0.5 text-center outline-none" />
                        </div>
                        <button onClick={() => setSets(sets.filter((_, idx) => idx !== i))} className="text-[#555] hover:text-red-400 transition-colors mt-3">
                            <XIcon size={12} />
                        </button>
                    </div>
                ))}
                <button onClick={addSet} className="flex items-center gap-1.5 text-[#555] hover:text-[#FC4C02] text-xs font-medium transition-colors">
                    <PlusIcon size={12} /> Agregar serie
                </button>
                <div className="flex gap-2 pt-3 border-t border-white/[0.08]">
                    <button onClick={onClose} className="flex-1 py-2.5 rounded-lg text-xs font-medium text-[#999] hover:bg-white/5 transition-all">Cancelar</button>
                    <button onClick={() => { onSave(sets); onClose(); }} className="flex-1 py-2.5 rounded-lg bg-[#FC4C02] text-white text-xs font-bold hover:brightness-110 transition-all">Guardar</button>
                </div>
            </div>
        </Drawer>
    );
};

/* ═══════════ History Drawer ═══════════ */
interface HistoryDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    sessionHistory: Session[];
    onRestore: (session: Session) => void;
}

export const HistoryDrawer: React.FC<HistoryDrawerProps> = ({ isOpen, onClose, sessionHistory, onRestore }) => {
    return (
        <Drawer isOpen={isOpen} onClose={onClose} title="Historial de Cambios" position="right">
            <div className="p-4 space-y-2">
                {sessionHistory.length === 0 ? (
                    <p className="text-xs text-[#555] text-center py-8">No hay cambios recientes.</p>
                ) : [...sessionHistory].reverse().map((hist, idx) => (
                    <button
                        key={idx}
                        onClick={() => { onRestore(hist); onClose(); }}
                        className="w-full text-left p-3 border border-white/[0.06] hover:border-white/10 rounded-lg text-xs transition-all group"
                    >
                        <span className="text-white font-medium group-hover:text-[#FC4C02]">
                            Estado #{sessionHistory.length - idx}
                        </span>
                        <span className="text-[10px] text-[#555] ml-2">Restaurar</span>
                    </button>
                ))}
            </div>
        </Drawer>
    );
};

/* ═══════════ Rules Drawer ═══════════ */
interface RulesDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    onApply: (sets: number, reps: number, rpe: number) => void;
}

export const RulesDrawer: React.FC<RulesDrawerProps> = ({ isOpen, onClose, onApply }) => {
    const [sets, setSets] = useState(3);
    const [reps, setReps] = useState(10);
    const [rpe, setRpe] = useState(8);

    return (
        <Drawer isOpen={isOpen} onClose={onClose} title="Reglas Macro" position="bottom" height="45%">
            <div className="p-4 space-y-4">
                <p className="text-xs text-[#999] leading-relaxed">Aplica reglas masivas a todos los ejercicios de esta sesión.</p>
                <div className="grid grid-cols-3 gap-3">
                    <div>
                        <span className="text-[10px] font-bold text-[#555] uppercase block mb-1">Series</span>
                        <input type="number" value={sets} onChange={e => setSets(parseInt(e.target.value) || 3)} className="w-full bg-[#0d0d0d] border-b border-white/10 focus:border-[#FC4C02] text-sm font-mono text-white py-1.5 text-center outline-none" />
                    </div>
                    <div>
                        <span className="text-[10px] font-bold text-[#555] uppercase block mb-1">Reps</span>
                        <input type="number" value={reps} onChange={e => setReps(parseInt(e.target.value) || 10)} className="w-full bg-[#0d0d0d] border-b border-white/10 focus:border-[#FC4C02] text-sm font-mono text-white py-1.5 text-center outline-none" />
                    </div>
                    <div>
                        <span className="text-[10px] font-bold text-[#555] uppercase block mb-1">RPE</span>
                        <input type="number" value={rpe} step="0.5" onChange={e => setRpe(parseFloat(e.target.value) || 8)} className="w-full bg-[#0d0d0d] border-b border-white/10 focus:border-[#FC4C02] text-sm font-mono text-white py-1.5 text-center outline-none" />
                    </div>
                </div>
                <button onClick={() => { onApply(sets, reps, rpe); onClose(); }} className="w-full py-2.5 rounded-lg bg-[#FC4C02] text-white text-xs font-bold hover:brightness-110 transition-all">
                    Aplicar a toda la Sesión
                </button>
            </div>
        </Drawer>
    );
};

/* ═══════════ Save Drawer ═══════════ */
interface SaveDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    sessionName: string;
    modifiedSessions: Session[];
    showBlockOption: boolean;
    onSaveSingle: (applyToBlock: boolean) => void;
    onSaveMultiple: (sessions: Session[], blockSelections: Record<string, boolean>) => void;
}

export const SaveDrawer: React.FC<SaveDrawerProps> = ({ isOpen, onClose, sessionName, modifiedSessions, showBlockOption, onSaveSingle, onSaveMultiple }) => {
    const [applyToBlock, setApplyToBlock] = useState(false);
    const [blockSelections, setBlockSelections] = useState<Record<string, boolean>>({});
    const isMultiple = modifiedSessions.length > 1;

    return (
        <Drawer isOpen={isOpen} onClose={onClose} title="Confirmar Cambios" position="bottom" height={isMultiple ? '65%' : '40%'}>
            <div className="p-4 space-y-4">
                {isMultiple ? (
                    <>
                        <p className="text-xs text-[#999]">Has modificado {modifiedSessions.length} sesiones:</p>
                        <div className="space-y-2 max-h-40 overflow-y-auto custom-scrollbar">
                            {modifiedSessions.map(s => (
                                <div key={s.id} className="flex items-center justify-between p-3 border border-white/[0.06] rounded-lg">
                                    <div className="flex items-center gap-2">
                                        <CheckIcon size={14} className="text-[#00F19F]" />
                                        <span className="text-xs font-medium text-white">{s.name || 'Sin nombre'}</span>
                                    </div>
                                    {showBlockOption && (
                                        <label className="flex items-center gap-1.5 cursor-pointer">
                                            <input type="checkbox" checked={blockSelections[s.id] || false} onChange={e => setBlockSelections(prev => ({ ...prev, [s.id]: e.target.checked }))} className="rounded border-white/10 bg-black w-3 h-3 text-[#FC4C02] focus:ring-0" />
                                            <span className="text-[10px] text-[#555]">Bloque</span>
                                        </label>
                                    )}
                                </div>
                            ))}
                        </div>
                        <div className="flex gap-2 pt-2">
                            <button onClick={() => onSaveSingle(false)} className="flex-1 py-2.5 rounded-lg border border-white/[0.08] text-xs font-medium text-[#999] hover:text-white hover:bg-white/5 transition-all">Solo actual</button>
                            <button onClick={() => onSaveMultiple(modifiedSessions, blockSelections)} className="flex-1 py-2.5 rounded-lg bg-[#FC4C02] text-white text-xs font-bold hover:brightness-110 transition-all">Guardar todas</button>
                        </div>
                    </>
                ) : (
                    <>
                        <p className="text-sm text-[#999]">Guardar cambios en <strong className="text-white">{sessionName}</strong></p>
                        {showBlockOption && (
                            <label className="flex items-center gap-3 p-3 border border-white/[0.08] rounded-lg cursor-pointer hover:bg-white/[0.02] transition-colors">
                                <input type="checkbox" checked={applyToBlock} onChange={e => setApplyToBlock(e.target.checked)} className="rounded border-white/10 bg-black w-4 h-4 text-[#FC4C02] focus:ring-0" />
                                <div>
                                    <span className="text-xs font-bold text-white block">Aplicar a todo el bloque</span>
                                    <span className="text-[10px] text-[#555]">Cambia este día en todas las semanas restantes.</span>
                                </div>
                            </label>
                        )}
                        <div className="flex gap-2 pt-2">
                            <button onClick={onClose} className="flex-1 py-2.5 rounded-lg border border-white/[0.08] text-xs font-medium text-[#999] hover:text-white hover:bg-white/5 transition-all">Cancelar</button>
                            <button onClick={() => onSaveSingle(applyToBlock)} className="flex-1 py-2.5 rounded-lg bg-[#FC4C02] text-white text-xs font-bold hover:brightness-110 transition-all">Guardar</button>
                        </div>
                    </>
                )}
            </div>
        </Drawer>
    );
};

export { Drawer };
