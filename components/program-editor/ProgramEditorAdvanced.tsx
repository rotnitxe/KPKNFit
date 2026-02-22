import React, { useState, useRef, useCallback } from 'react';
import { Program, ExerciseMuscleInfo } from '../../types';
import { useAppContext } from '../../contexts/AppContext';
import EditorToolbar from './EditorToolbar';
import EditorSidebar from './EditorSidebar';
import DetailsSection from './DetailsSection';
import StructureSection from './StructureSection';
import GoalsSection from './GoalsSection';
import EventsSection from './EventsSection';
import VolumeSection from './VolumeSection';
import ExportSection from './ExportSection';
import SplitChangerModal from '../SplitChangerModal';

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
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [activeSection, setActiveSection] = useState<string>('details');
    const [isSplitChangerOpen, setIsSplitChangerOpen] = useState(false);

    const sectionRefs = useRef<Record<string, HTMLDivElement | null>>({});

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

    const navigateToSection = useCallback((id: string) => {
        setActiveSection(id);
        sectionRefs.current[id]?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, []);

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

    return (
        <div className="fixed inset-0 z-[100] bg-black text-white flex flex-col">
            {/* Toolbar */}
            <EditorToolbar
                programName={program.name || ''}
                onChangeName={name => updateField('name', name)}
                hasUnsavedChanges={hasUnsavedChanges}
                onSave={handleSave}
                onCancel={handleCancel}
                onDuplicate={handleDuplicate}
                onExport={handleExportJSON}
            />

            <div className="flex-1 flex overflow-hidden">
                {/* Sidebar */}
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
                    collapsed={sidebarCollapsed}
                    onToggleCollapsed={() => setSidebarCollapsed(!sidebarCollapsed)}
                />

                {/* Main content */}
                <div className="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-8">
                    <div ref={el => { sectionRefs.current['details'] = el; }}>
                        <DetailsSection
                            program={program}
                            onUpdateField={updateField}
                            onOpenSplitChanger={() => setIsSplitChangerOpen(true)}
                        />
                    </div>

                    <div ref={el => { sectionRefs.current['structure'] = el; }}>
                        <StructureSection
                            program={program}
                            onUpdateProgram={updateProgram}
                        />
                    </div>

                    <div ref={el => { sectionRefs.current['goals'] = el; }}>
                        <GoalsSection
                            program={program}
                            exerciseList={exerciseList}
                            onUpdateProgram={updateProgram}
                        />
                    </div>

                    <div ref={el => { sectionRefs.current['events'] = el; }}>
                        <EventsSection
                            program={program}
                            onUpdateProgram={updateProgram}
                        />
                    </div>

                    <div ref={el => { sectionRefs.current['volume'] = el; }}>
                        <VolumeSection
                            program={program}
                            exerciseList={exerciseList}
                        />
                    </div>

                    <div ref={el => { sectionRefs.current['export'] = el; }}>
                        <ExportSection
                            program={program}
                            onDuplicate={handleDuplicate}
                            addToast={addToast}
                        />
                    </div>
                </div>
            </div>

            {/* Split Changer */}
            <SplitChangerModal
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
