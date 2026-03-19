import React, { useState, useCallback } from 'react';
import { View, StyleSheet, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { WorkoutStackParamList } from '../../navigation/types';
import { useColors } from '../../theme';
import { useProgramStore } from '../../stores/programStore';
import { generateId } from '../../utils/generateId';
import { Program, SplitTemplate, Session, ProgramTemplateOption } from '../../types/workout';
import { PROGRAM_TEMPLATES } from '../../data/programTemplates';
import WizardLayout from '../../components/programs/WizardLayout';
import TypeStep from '../../components/programs/wizard/TypeStep';
import CalendarStep from '../../components/programs/wizard/CalendarStep';
import StructureStep from '../../components/programs/wizard/StructureStep';
import SessionsStep from '../../components/programs/wizard/SessionsStep';
import PreviewStep from '../../components/programs/wizard/PreviewStep';

export const ProgramWizardScreen: React.FC = () => {
    const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
    const route = useRoute<RouteProp<WorkoutStackParamList, 'ProgramWizard'>>();
    const colors = useColors();
    const { addProgram } = useProgramStore();
    
    // Wizard state
    const [currentStep, setCurrentStep] = useState(0);
    const steps = ['Tipo', 'Calendario', 'Estructura', 'Sesiones', 'Preview'];
    
    // Form data
    const [programName, setProgramName] = useState('');
    const [programDescription, setProgramDescription] = useState('');
    const [selectedTemplate, setSelectedTemplate] = useState<ProgramTemplateOption | null>(null);
    const [trainingDays, setTrainingDays] = useState<number[]>([1, 2, 4, 5]); // Default: Mon, Tue, Thu, Fri
    const [selectedSplit, setSelectedSplit] = useState<SplitTemplate | null>(null);
    const [sessions, setSessions] = useState<Session[]>([]);

    const handleNext = () => {
        if (currentStep < steps.length - 1) {
            setCurrentStep(currentStep + 1);
        }
    };

    const handleBack = () => {
        if (currentStep > 0) {
            setCurrentStep(currentStep - 1);
        } else {
            navigation.goBack();
        }
    };

    const canContinue = () => {
        switch (currentStep) {
            case 0: return selectedTemplate !== null;
            case 1: return programName.trim().length > 0 && trainingDays.length > 0;
            case 2: return selectedSplit !== null;
            case 3: return sessions.length > 0 && sessions.some(s => s.exercises.length > 0);
            case 4: return selectedTemplate !== null && selectedSplit !== null;
            default: return false;
        }
    };

    const handleCreateProgram = async () => {
        if (!selectedTemplate || !selectedSplit) return;

        try {
            const newProgram = buildProgramObject();
            await addProgram(newProgram);
            
            Alert.alert(
                "¡Éxito!",
                "Programa creado correctamente. ¿Quieres ir a verlo?",
                [
                    { text: "Ahora no", onPress: () => navigation.goBack() },
                    { text: "Ver programa", onPress: () => navigation.navigate('ProgramDetail', { programId: newProgram.id }) }
                ]
            );
        } catch (error) {
            console.error(error);
            Alert.alert("Error", "No se pudo crear el programa.");
        }
    };

    const buildProgramObject = (): Program => {
        if (!selectedTemplate || !selectedSplit) {
            throw new Error('Template o Split no seleccionado');
        }
        
        const programId = generateId();
        const macrocycleId = generateId();
        
        const blocks = selectedTemplate.type === 'simple' 
            ? [buildSimpleBlock()]
            : buildComplexBlocks();

        return {
            id: programId,
            name: programName,
            description: programDescription,
            mode: selectedTemplate.name.toLowerCase() as any,
            structure: selectedTemplate.type,
            author: 'Yo',
            isDraft: false,
            startDay: trainingDays[0] || 0,
            selectedSplitId: selectedSplit.id,
            macrocycles: [
                {
                    id: macrocycleId,
                    name: 'Macrociclo 1',
                    blocks
                }
            ]
        };
    };

    const buildSimpleBlock = () => {
        if (!selectedTemplate) throw new Error('Template no seleccionado');
        
        const blockId = generateId();
        const mesocycleId = generateId();
        
        const weeks = Array.from({ length: selectedTemplate.weeks }, (_, i) => 
            buildWeek(i)
        );
        
        return {
            id: blockId,
            name: 'Bloque Único',
            mesocycles: [
                {
                    id: mesocycleId,
                    name: 'Mesociclo 1',
                    goal: 'Acumulación' as const,
                    weeks
                }
            ]
        };
    };

    const buildComplexBlocks = () => {
        const blockNames = ['Acumulación', 'Intensificación', 'Realización'];
        return blockNames.map((name, i) => {
            const blockId = generateId();
            const mesocycleId = generateId();
            const weeks = Array.from({ length: 4 }, (_, j) => buildWeek(j));
            
            return {
                id: blockId,
                name,
                mesocycles: [
                    {
                        id: mesocycleId,
                        name: `Mesociclo ${i + 1}`,
                        goal: (name === 'Intensificación' ? 'Intensificación' : (name === 'Realización' ? 'Realización' : 'Acumulación')) as any,
                        weeks
                    }
                ]
            };
        });
    };

    const buildWeek = (index: number) => {
        const weekId = generateId();
        // Map training days to sessions
        const weekSessions = sessions
            .filter(s => trainingDays.includes(s.dayOfWeek || 0))
            .map(s => ({
                ...s,
                id: generateId(),
            }));

        return {
            id: weekId,
            name: `Semana ${index + 1}`,
            sessions: weekSessions
        };
    };

    const renderStep = () => {
        switch (currentStep) {
            case 0:
                return (
                    <TypeStep
                        selectedTemplateId={selectedTemplate?.id || null}
                        onSelectTemplate={setSelectedTemplate}
                    />
                );
            case 1:
                return (
                    <CalendarStep
                        programName={programName}
                        onProgramNameChange={setProgramName}
                        programDescription={programDescription}
                        onProgramDescriptionChange={setProgramDescription}
                        trainingDays={trainingDays}
                        onTrainingDaysChange={setTrainingDays}
                    />
                );
            case 2:
                return (
                    <StructureStep
                        selectedSplit={selectedSplit}
                        onSelectSplit={setSelectedSplit}
                    />
                );
            case 3:
                return (
                    <SessionsStep
                        trainingDays={trainingDays}
                        sessions={sessions}
                        onSessionsChange={setSessions}
                    />
                );
            case 4:
                return (
                    <PreviewStep
                        programName={programName}
                        programDescription={programDescription}
                        selectedTemplate={selectedTemplate}
                        selectedSplit={selectedSplit}
                        trainingDays={trainingDays}
                        sessions={sessions}
                        onCreateProgram={handleCreateProgram}
                    />
                );
            default:
                return null;
        }
    };

    return (
        <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
            <WizardLayout
                title="Crear Programa"
                steps={steps}
                currentStep={currentStep}
                onBack={handleBack}
                onNext={currentStep === steps.length - 1 ? handleCreateProgram : handleNext}
                canNext={canContinue()}
                isLastStep={currentStep === steps.length - 1}
            >
                {renderStep()}
            </WizardLayout>
        </SafeAreaView>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1 },
});