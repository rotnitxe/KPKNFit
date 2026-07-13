import React from 'react';
import { DumbbellIcon, CalendarIcon, ActivityIcon } from '../icons';

interface Step {
    id: number;
    label: string;
    icon: React.ReactNode;
}

const STEPS: Step[] = [
    { id: 0, label: 'Estructura', icon: <DumbbellIcon size={14} /> },
    { id: 1, label: 'Calendario', icon: <CalendarIcon size={14} /> },
    { id: 2, label: 'Sesiones', icon: <ActivityIcon size={14} /> },
];

interface WizardStepIndicatorProps {
    currentStep: number;
    onStepClick?: (step: number) => void;
}

const WizardStepIndicator: React.FC<WizardStepIndicatorProps> = ({ currentStep, onStepClick }) => {
    return (
        <div className="flex items-center justify-center gap-1 py-3 px-4">
            {STEPS.map((step, idx) => {
                const isActive = currentStep === step.id;
                const isCompleted = currentStep > step.id;
                return (
                    <React.Fragment key={step.id}>
                        <button
                            onClick={() => onStepClick?.(step.id)}
                            disabled={step.id > currentStep}
                            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest transition-all ${
                                isActive
                                    ? 'bg-white text-black shadow-[0_0_12px_rgba(255,255,255,0.15)]'
                                    : isCompleted
                                        ? 'bg-zinc-800 text-white'
                                        : 'bg-transparent text-zinc-600 cursor-not-allowed'
                            }`}
                        >
                            {step.icon}
                            <span className="hidden sm:inline">{step.label}</span>
                        </button>
                        {idx < STEPS.length - 1 && (
                            <div className={`w-8 h-px ${isCompleted ? 'bg-white' : 'bg-zinc-800'}`} />
                        )}
                    </React.Fragment>
                );
            })}
        </div>
    );
};

export default WizardStepIndicator;
