import React, { useState, useCallback } from 'react';
import { Program } from '../../types';
import { useAppContext } from '../../contexts/AppContext';
import EditorToolbar from './EditorToolbar';
import EditorSidebar from './EditorSidebar';
import DetailsSection from './DetailsSection';
import StructureDrawer from '../program-detail/StructureDrawer';
import GoalsSection from './GoalsSection';
import EventsSection from './EventsSection';
import VolumeSection from './VolumeSection';
import ExportSection from './ExportSection';
import SplitChangerDrawer from '../program-detail/SplitChangerDrawer';

interface ProgramEditorAdvancedProps {
    program: Program;
    onSave: (program: Program) => void;
    onCancel: () => void;
}

const ProgramEditorAdvanced: React.FC<ProgramEditorAdvancedProps> = ({
    program: initialProgram, onSave, onCancel,
}) => {
    const { exerciseList, handleChangeSplit, addToast } = useAppContext();
    const [program, setProgram] = useState<Program>(() => JSON.parse(JSON.stringify(initialProgram)));
    const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
    const [isSidebarOpen, setIsSidebarOpen] = useState(false);
    const [activeSection, setActiveSection] = useState<string>('details');
    const [isSplitChangerOpen, setIsSplitChangerOpen] = useState(false);
    const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null);
    const [selectedWeekId, setSelectedWeekId] = useState<string | null>(null);

    const updateProgram = useCallback((updated: Program) => {
        setProgram(updated);
        setHasUnsavedChanges(true);
    }, []);

    const updateField = useCallback((field: string, value: any) => {
        setProgram(prev => {
            const updated = JSON.parse(JSON.stringify(prev));
            (updated as any)[field] = value;
            return updated;
        });
        setHasUnsavedChanges(true);
    }, []);

    const handleSave = useCallback(() => {
        onSave(program);
        setHasUnsavedChanges(false);
        addToast('Programa guardado', 'success');
    }, [program, onSave, addToast]);

    const handleCancel = useCallback(() => {
        if (hasUnsavedChanges && !window.confirm('¿Descartar cambios sin guardar?')) return;
        onCancel();
    }, [hasUnsavedChanges, onCancel]);

    const handleDuplicate = useCallback(() => {
        const dup = JSON.parse(JSON.stringify(program));
        dup.id = crypto.randomUUID();
        dup.name = `${program.name} (Copia)`;
        onSave(dup);
        addToast('Programa duplicado', 'success');
    }, [program, onSave, addToast]);

    const handleExportJSON = useCallback(() => {
        const blob = new Blob([JSON.stringify(program, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${program.name?.replace(/[^a-zA-Z0-9]/g, '_') || 'program'}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        addToast('Exportado', 'success');
    }, [program, addToast]);

    const navigateToSection = (id: string) => {
        setActiveSection(id);
        setIsSidebarOpen(false);
    };

    return (
        <div className="fixed inset-0 z-[100] bg-[var(--md-sys-color-surface)] text-black flex flex-col font-sans overflow-hidden">
            {/* Toolbar */}
            <EditorToolbar
                programName={program.name || ''}
                onChangeName={name => updateField('name', name)}
                hasUnsavedChanges={hasUnsavedChanges}
                onSave={handleSave}
                onCancel={handleCancel}
                onDuplicate={handleDuplicate}
                onExport={handleExportJSON}
                onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
            />

            <div className="flex-1 flex overflow-hidden relative">
                {/* Backdrop for mobile sidebar */}
                {isSidebarOpen && (
                    <div
                        className="fixed inset-0 bg-black/40 backdrop-blur-sm z-40 transition-opacity duration-300"
                        onClick={() => setIsSidebarOpen(false)}
                    />
                )}

                {/* Sidebar */}
                <div
                    className={`fixed inset-y-0 left-0 w-72 bg-white border-r border-[var(--md-sys-color-outline-variant)] z-50 transform transition-transform duration-300 ease-spring ${isSidebarOpen ? 'translate-x-0 shadow-2xl' : '-translate-x-full'}`}
                >
                    <EditorSidebar
                        program={program}
                        activeSectionId={activeSection}
                        onNavigateToSection={navigateToSection}
                        onAddBlock={(macroIdx) => {
                            const updated = JSON.parse(JSON.stringify(program));
                            updated.macrocycles[macroIdx].blocks.push({
                                id: crypto.randomUUID(), name: 'Nuevo Bloque',
                                mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [] }],
                            });
                            updateProgram(updated);
                        }}
                        onAddWeek={(macroIdx, blockIdx, mesoIdx) => {
                            const updated = JSON.parse(JSON.stringify(program));
                            const meso = updated.macrocycles[macroIdx].blocks[blockIdx].mesocycles[mesoIdx];
                            meso.weeks.push({ id: crypto.randomUUID(), name: `Semana ${meso.weeks.length + 1}`, sessions: [] });
                            updateProgram(updated);
                        }}
                    />
                </div>

                {/* Main content */}
                <main className="flex-1 overflow-y-auto custom-scrollbar p-4 bg-[var(--md-sys-color-surface)] pb-32">
                    <div className="space-y-8 h-full">
                        {activeSection === 'details' && (
                            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                <DetailsSection
                                    program={program}
                                    onUpdateField={updateField}
                                    onOpenSplitChanger={() => setIsSplitChangerOpen(true)}
                                />
                            </div>
                        )}

                        {activeSection === 'structure' && (
                            <div className="animate-in fade-in zoom-in-95 duration-500 h-full">
                                <StructureDrawer
                                    isOpen={true}
                                    onClose={() => { }}
                                    program={program}
                                    isCyclic={program.structure === 'simple'}
                                    selectedBlockId={selectedBlockId}
                                    selectedWeekId={selectedWeekId}
                                    onSelectBlock={setSelectedBlockId}
                                    onSelectWeek={setSelectedWeekId}
                                    onUpdateProgram={updateProgram}
                                    onEditWeek={() => { }} // Handle navigation to session editor?
                                    onShowAdvancedTransition={() => updateField('structure', 'complex')}
                                    onShowSimpleTransition={() => updateField('structure', 'simple')}
                                    onOpenEventModal={() => setActiveSection('events')}
                                    inline={true}
                                />
                            </div>
                        )}

                        {activeSection === 'goals' && (
                            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                <GoalsSection
                                    program={program}
                                    exerciseList={exerciseList}
                                    onUpdateProgram={updateProgram}
                                />
                            </div>
                        )}

                        {activeSection === 'events' && (
                            <div className="animate-in fade-in slide-in-from-left-4 duration-500">
                                <EventsSection
                                    program={program}
                                    onUpdateProgram={updateProgram}
                                />
                            </div>
                        )}

                        {activeSection === 'volume' && (
                            <div className="animate-in fade-in slide-in-from-right-4 duration-500">
                                <VolumeSection
                                    program={program}
                                    exerciseList={exerciseList}
                                />
                            </div>
                        )}

                        {activeSection === 'export' && (
                            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                <ExportSection
                                    program={program}
                                    onDuplicate={handleDuplicate}
                                    addToast={addToast as any}
                                />
                            </div>
                        )}
                    </div>
                </main>
            </div>

            {/* Split Changer Drawer */}
            <SplitChangerDrawer
                isOpen={isSplitChangerOpen}
                onClose={() => setIsSplitChangerOpen(false)}
                currentSplitId={program.selectedSplitId}
                currentStartDay={program.startDay ?? 1}
                isSimpleProgram={program.structure === 'simple'}
                onApply={(split, scope, preserveExercises, startDay) => {
                    handleChangeSplit(program.id, split.pattern, split.id, scope, preserveExercises, startDay);
                    setIsSplitChangerOpen(false);
                }}
            />
        </div>
    );
};

export default ProgramEditorAdvanced;
