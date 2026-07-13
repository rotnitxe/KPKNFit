import React, { useState } from 'react';
import { ChevronDownIcon, PlusIcon } from '../icons';

interface PartSectionProps {
    partName: string;
    partIndex: number;
    exerciseCount: number;
    color?: string;
    isCollapsed: boolean;
    onToggleCollapse: () => void;
    onRename: (name: string) => void;
    onChangeColor: (color: string) => void;
    onAddExercise: () => void;
    children: React.ReactNode;
}

const PART_COLORS = ['#00F0FF', '#3B82F6', '#00F19F', '#A855F7', '#EAB308', '#F43F5E', '#06B6D4', '#8B5CF6'];

const PartSection: React.FC<PartSectionProps> = ({
    partName, partIndex, exerciseCount, color = '#00F0FF', isCollapsed,
    onToggleCollapse, onRename, onChangeColor, onAddExercise, children,
}) => {
    const [isEditingName, setIsEditingName] = useState(false);
    const [showColorPicker, setShowColorPicker] = useState(false);

    return (
        <div className="mb-6 rounded-2xl overflow-hidden shadow-sm border" style={{ backgroundColor: 'rgba(255, 255, 255, 0.8)', backdropFilter: 'blur(12px)', borderColor: 'rgba(0, 0, 0, 0.08)' }}>
            {/* Part header */}
            <div className="flex items-center gap-2 px-4 py-2">
                {/* Color dot */}
                <div className="relative">
                    <button
                        onClick={() => setShowColorPicker(!showColorPicker)}
                        className="w-2.5 h-2.5 rounded-full shrink-0 hover:scale-125 transition-transform shadow-sm"
                        style={{ backgroundColor: color }}
                    />
                    {showColorPicker && (
                        <>
                            <div className="fixed inset-0 z-40" onClick={() => setShowColorPicker(false)} />
                            <div className="absolute top-6 left-0 z-50 bg-white/95 backdrop-blur-2xl border border-slate-200 rounded-xl p-2 flex gap-1.5 shadow-xl">
                                {PART_COLORS.map(c => (
                                    <button
                                        key={c}
                                        onClick={() => { onChangeColor(c); setShowColorPicker(false); }}
                                        className="w-5 h-5 rounded-full hover:scale-110 transition-transform shadow-sm"
                                        style={{ backgroundColor: c, outline: c === color ? '2px solid white' : 'none', outlineOffset: '2px', border: '1px solid rgba(0,0,0,0.1)' }}
                                    />
                                ))}
                            </div>
                        </>
                    )}
                </div>

                {/* Part name */}
                {isEditingName ? (
                    <input
                        type="text"
                        value={partName}
                        onChange={e => onRename(e.target.value)}
                        onBlur={() => setIsEditingName(false)}
                        onKeyDown={e => { if (e.key === 'Enter') setIsEditingName(false); }}
                        autoFocus
                        className="text-xs font-bold uppercase tracking-wide text-slate-500 bg-transparent border-b border-slate-200 focus:border-cyan-500 outline-none py-0.5"
                    />
                ) : (
                    <button
                        onClick={() => setIsEditingName(true)}
                        className="text-xs font-bold uppercase tracking-wide text-slate-500 hover:text-slate-800 transition-colors"
                    >
                        {partName}
                    </button>
                )}

                <span className="text-[10px] text-slate-400 font-bold">{exerciseCount}</span>

                <div className="flex-1" />

                <button onClick={onToggleCollapse} className="text-slate-400 hover:text-slate-700 transition-colors p-1 rounded-lg hover:bg-slate-100">
                    <ChevronDownIcon size={14} className={`transition-transform ${isCollapsed ? '-rotate-90' : ''}`} />
                </button>
            </div>

            {/* Exercises */}
            {!isCollapsed && (
                <div>
                    {children}
                    <button
                        onClick={onAddExercise}
                        className="flex items-center gap-1.5 px-4 py-2.5 w-full text-left text-slate-500 hover:text-cyan-600 hover:bg-cyan-50/50 transition-all"
                    >
                        <PlusIcon size={14} className="text-cyan-500" />
                        <span className="text-xs font-bold">Agregar ejercicio</span>
                    </button>
                </div>
            )}
        </div>
    );
};

export default PartSection;
