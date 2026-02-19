
import React from 'react';

interface ToggleSwitchProps {
    checked: boolean;
    onChange: (checked: boolean) => void;
    label?: string;
    size?: 'sm' | 'md';
    isBlackAndWhite?: boolean;
}

const ToggleSwitch: React.FC<ToggleSwitchProps> = ({ checked, onChange, label, size = 'md', isBlackAndWhite = false }) => {
    const h = size === 'sm' ? 'h-5' : 'h-6';
    const w = size === 'sm' ? 'w-9' : 'w-11';
    const translate = size === 'sm' ? 'translate-x-4' : 'translate-x-5';
    const knobSize = size === 'sm' ? 'h-4 w-4' : 'h-5 w-5';
    
    // Default primary color or white/black if requested
    const bgClass = checked 
        ? (isBlackAndWhite ? 'bg-white' : 'bg-primary-color') 
        : 'bg-slate-600';
    
    const knobClass = isBlackAndWhite ? 'bg-black' : 'bg-white';

    return (
        <div className="flex items-center gap-2 cursor-pointer" onClick={() => onChange(!checked)}>
            {label && <span className={`font-medium text-slate-300 ${size === 'sm' ? 'text-xs' : 'text-sm'}`}>{label}</span>}
            <div className={`relative inline-flex flex-shrink-0 ${h} ${w} border-2 border-transparent rounded-full cursor-pointer transition-colors ease-in-out duration-200 focus:outline-none ${bgClass}`}>
                <span className={`inline-block ${knobSize} rounded-full ${knobClass} shadow transform ring-0 transition ease-in-out duration-200 ${checked ? translate : 'translate-x-0'}`} />
            </div>
        </div>
    );
};

export default ToggleSwitch;
