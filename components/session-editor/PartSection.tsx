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

const PART_COLORS = ['#FC4C02', '#3B82F6', '#00F19F', '#A855F7', '#EAB308', '#F43F5E', '#06B6D4', '#8B5CF6'];

const PartSection: React.FC<PartSectionProps> = ({
    partName, partIndex, exerciseCount, color = '#FC4C02', isCollapsed,
    onToggleCollapse, onRename, onChangeColor, onAddExercise, children,
}) => {
    const [isEditingName, setIsEditingName] = useState(false);
    const [showColorPicker, setShowColorPicker] = useState(false);

    return (
        <div className="mb-6">
            {/* Part header */}
            <div className="flex items-center gap-2 px-4 py-2">
                {/* Color dot */}
                <div className="relative">
                    <button
                        onClick={() => setShowColorPicker(!showColorPicker)}
                        className="w-2.5 h-2.5 rounded-full shrink-0 hover:scale-125 transition-transform"
                        style={{ backgroundColor: color }}
                    />
                    {showColorPicker && (
                        <>
                            <div className="fixed inset-0 z-40" onClick={() => setShowColorPicker(false)} />
                            <div className="absolute top-6 left-0 z-50 bg-[#111] border border-white/10 rounded-lg p-2 flex gap-1.5">
                                {PART_COLORS.map(c => (
                                    <button
                                        key={c}
                                        onClick={() => { onChangeColor(c); setShowColorPicker(false); }}
                                        className="w-5 h-5 rounded-full hover:scale-110 transition-transform"
                                        style={{ backgroundColor: c, outline: c === color ? '2px solid white' : 'none', outlineOffset: '2px' }}
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
                        className="text-xs font-bold uppercase tracking-wide text-[#999] bg-transparent border-b border-white/10 focus:border-[#FC4C02] outline-none py-0.5"
                    />
                ) : (
                    <button
                        onClick={() => setIsEditingName(true)}
                        className="text-xs font-bold uppercase tracking-wide text-[#999] hover:text-white transition-colors"
                    >
                        {partName}
                    </button>
                )}

                <span className="text-[10px] text-[#555]">{exerciseCount}</span>

                <div className="flex-1" />

                <button onClick={onToggleCollapse} className="text-[#555] hover:text-white transition-colors">
                    <ChevronDownIcon size={14} className={`transition-transform ${isCollapsed ? '-rotate-90' : ''}`} />
                </button>
            </div>

            {/* Exercises */}
            {!isCollapsed && (
                <div>
                    {children}
                    <button
                        onClick={onAddExercise}
                        className="flex items-center gap-1.5 px-4 py-2.5 w-full text-left text-[#555] hover:text-[#FC4C02] transition-colors"
                    >
                        <PlusIcon size={14} />
                        <span className="text-xs font-medium">Agregar ejercicio</span>
                    </button>
                </div>
            )}
        </div>
    );
};

export default PartSection;
