import React, { useState } from 'react';
import WizardStepIndicator from './WizardStepIndicator';
import ProgramPreviewPanel from './ProgramPreviewPanel';
import { SplitTemplate } from '../../data/splitTemplates';
import { ChevronDownIcon, XIcon } from '../icons';

interface WizardLayoutProps {
    currentStep: number;
    onStepClick?: (step: number) => void;
    onCancel: () => void;
    programName: string;
    templateName: string;
    templateType: 'simple' | 'complex';
    selectedSplit: SplitTemplate | null;
    startDay: number;
    cycleDuration: number;
    blockNames?: string[];
    blockDurations?: number[];
    events?: { title: string; calculatedWeek?: number; repeatEveryXCycles?: number }[];
    children: React.ReactNode;
}

const WizardLayout: React.FC<WizardLayoutProps> = ({
    currentStep, onStepClick, onCancel,
    programName, templateName, templateType, selectedSplit,
    startDay, cycleDuration, blockNames, blockDurations, events,
    children,
}) => {
    const [previewOpen, setPreviewOpen] = useState(false);

    return (
        <div className="fixed inset-0 z-[100] bg-black text-white flex flex-col">
            {/* Top Bar */}
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/5 bg-black shrink-0">
                <button onClick={onCancel} className="p-1.5 rounded-full border border-white/10 text-zinc-400 hover:text-white transition-colors">
                    <XIcon size={16} />
                </button>
                <WizardStepIndicator currentStep={currentStep} onStepClick={onStepClick} />
                <button
                    onClick={() => setPreviewOpen(!previewOpen)}
                    className="sm:hidden p-1.5 rounded-full border border-white/10 text-zinc-400 hover:text-white transition-colors"
                >
                    <ChevronDownIcon size={16} className={previewOpen ? 'rotate-180' : ''} />
                </button>
                <div className="hidden sm:block w-8" />
            </div>

            {/* Split layout: content + preview */}
            <div className="flex-1 flex overflow-hidden">
                {/* Main content */}
                <div className="flex-1 overflow-y-auto custom-scrollbar">
                    {children}
                </div>

                {/* Desktop preview panel */}
                <div className="hidden sm:block w-[280px] shrink-0">
                    <ProgramPreviewPanel
                        programName={programName}
                        templateName={templateName}
                        templateType={templateType}
                        selectedSplit={selectedSplit}
                        startDay={startDay}
                        cycleDuration={cycleDuration}
                        blockNames={blockNames}
                        blockDurations={blockDurations}
                        events={events}
                    />
                </div>
            </div>

            {/* Mobile bottom sheet preview */}
            {previewOpen && (
                <div className="sm:hidden fixed inset-x-0 bottom-0 z-50 bg-zinc-950 border-t border-white/10 rounded-t-2xl max-h-[50vh] overflow-y-auto custom-scrollbar animate-slide-up shadow-2xl">
                    <div className="flex items-center justify-between px-4 pt-3 pb-2 border-b border-white/5">
                        <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">Preview</span>
                        <button onClick={() => setPreviewOpen(false)} className="p-1 text-zinc-500 hover:text-white">
                            <XIcon size={14} />
                        </button>
                    </div>
                    <ProgramPreviewPanel
                        programName={programName}
                        templateName={templateName}
                        templateType={templateType}
                        selectedSplit={selectedSplit}
                        startDay={startDay}
                        cycleDuration={cycleDuration}
                        blockNames={blockNames}
                        blockDurations={blockDurations}
                        events={events}
                    />
                </div>
            )}
        </div>
    );
};

export default WizardLayout;
