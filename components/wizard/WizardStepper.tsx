// components/wizard/WizardStepper.tsx
// Stepper horizontal con labels para wizards. Estética Cyber/NERD.

import React from 'react';

export interface WizardStepperProps {
    currentStep: number;
    totalSteps: number;
    /** Labels opcionales por paso (1-indexed). Por defecto "1", "2", "3"... */
    labels?: string[];
    className?: string;
}

export const WizardStepper: React.FC<WizardStepperProps> = ({
    currentStep,
    totalSteps,
    labels,
    className = '',
}) => {
    const steps = Array.from({ length: totalSteps }, (_, i) => i);
    return (
        <div className={`flex items-center justify-center gap-2 ${className}`}>
            {steps.map((step, idx) => {
                const isActive = currentStep === step;
                const isCompleted = currentStep > step;
                const label = labels?.[idx] ?? String(step + 1);
                return (
                    <React.Fragment key={step}>
                        <div
                            className={`flex items-center justify-center w-8 h-8 rounded-full text-[10px] font-black uppercase transition-all ${
                                isActive
                                    ? 'bg-cyber-cyan/20 border border-cyber-cyan/50 text-cyber-cyan'
                                    : isCompleted
                                        ? 'bg-white/10 border border-white/20 text-white'
                                        : 'bg-transparent border border-white/10 text-zinc-500'
                            }`}
                            aria-current={isActive ? 'step' : undefined}
                        >
                            {label}
                        </div>
                        {idx < totalSteps - 1 && (
                            <div
                                className={`w-6 h-px ${isCompleted ? 'bg-cyber-cyan/50' : 'bg-white/10'}`}
                                aria-hidden
                            />
                        )}
                    </React.Fragment>
                );
            })}
        </div>
    );
};
