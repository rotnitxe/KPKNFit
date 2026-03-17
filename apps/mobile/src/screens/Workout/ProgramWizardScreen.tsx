import React, { useState, useCallback } from 'react';
import { View, StyleSheet, Alert, Modal } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { WorkoutStackParamList } from '../../navigation/types';
import { useColors } from '../../theme';
import { useProgramStore } from '../../stores/programStore';
import AthleteProfilingWizard from '../../components/programs/AthleteProfilingWizard';
import ProgramStructureWizard from '../../components/programs/ProgramStructureWizard';
import { generateId } from '../../utils/generateId';
import { Program, SplitTemplate, AthleteProfileScore } from '../../types/workout';
import { PROGRAM_TEMPLATES } from '../../data/programTemplates';

export const ProgramWizardScreen: React.FC = () => {
    const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
    const route = useRoute<RouteProp<WorkoutStackParamList, 'ProgramWizard'>>();
    const colors = useColors();
    const { addProgram, programs } = useProgramStore();
    
    // Wizard Phase: 1 (Profiling), 2 (Structure), 3 (Complete/Saving)
    const [phase, setPhase] = useState(1);
    const [athleteProfile, setAthleteProfile] = useState<AthleteProfileScore | null>(null);

    const buildProgramObject = (structureData: any, profile: AthleteProfileScore): Program => {
        const { name, templateId, startDay, cycleDuration, split } = structureData;
        const template = PROGRAM_TEMPLATES.find(t => t.id === templateId)!;
        
        const programId = generateId();
        const macrocycleId = generateId();
        
        // Basic structure based on template rules
        const blocks = template.type === 'simple' 
            ? [buildSimpleBlock(template, split, startDay)]
            : buildComplexBlocks(template, split, startDay);

        return {
            id: programId,
            name: name || 'Mi Programa',
            description: `Perfil: ${profile.profileLevel}. Enfoque: ${profile.trainingStyle}.`,
            mode: profile.trainingStyle.toLowerCase() as any,
            structure: template.type,
            author: 'Yo',
            isDraft: false,
            startDay,
            selectedSplitId: split.id,
            athleteProfile: profile,
            macrocycles: [
                {
                    id: macrocycleId,
                    name: 'Macrociclo 1',
                    blocks
                }
            ]
        };
    };

    const buildSimpleBlock = (template: any, split: SplitTemplate, startDay: number) => {
        const blockId = generateId();
        const mesocycleId = generateId();
        
        // Simple template usually has 4 weeks per mesocycle
        const weeks = Array.from({ length: 4 }, (_, i) => buildWeek(split, startDay, i));
        
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

    const buildComplexBlocks = (template: any, split: SplitTemplate, startDay: number) => {
        // Complex templates have multiple blocks (e.g. Accumulation, Transmutation, Realization)
        const blockNames = ['Acumulación', 'Transmutación', 'Realización'];
        return blockNames.map((name, i) => {
            const blockId = generateId();
            const mesocycleId = generateId();
            const weeks = Array.from({ length: 4 }, (_, j) => buildWeek(split, startDay, j));
            
            return {
                id: blockId,
                name,
                mesocycles: [
                    {
                        id: mesocycleId,
                        name: `Mesociclo ${i + 1}`,
                        goal: (name === 'Transmutación' ? 'Intensificación' : (name === 'Realización' ? 'Realización' : 'Acumulación')) as any,
                        weeks
                    }
                ]
            };
        });
    };

    const buildWeek = (split: SplitTemplate, startDay: number, index: number) => {
        const weekId = generateId();
        const sessions = split.pattern
            .map((label, dayIndex) => {
                if (label && label.toLowerCase() !== 'descanso' && label.trim() !== '') {
                    return {
                        id: generateId(),
                        name: label,
                        description: '',
                        exercises: [],
                        warmup: [],
                        dayOfWeek: (startDay + dayIndex) % 7
                    };
                }
                return null;
            })
            .filter(Boolean) as any[];

        return {
            id: weekId,
            name: `Semana ${index + 1}`,
            sessions
        };
    };

    const handleProfileComplete = (score: AthleteProfileScore) => {
        setAthleteProfile(score);
        setPhase(2);
    };

    const handleStructureComplete = async (structureData: any) => {
        if (!athleteProfile) return;
        
        try {
            const newProgram = buildProgramObject(structureData, athleteProfile);
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

    return (
        <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
            <View style={styles.content}>
                {phase === 1 && (
                    <AthleteProfilingWizard 
                        onComplete={handleProfileComplete}
                        onCancel={() => navigation.goBack()}
                    />
                )}
                {phase === 2 && (
                    <ProgramStructureWizard
                        onComplete={handleStructureComplete}
                        onCancel={() => setPhase(1)}
                    />
                )}
            </View>
        </SafeAreaView>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1 },
    content: { flex: 1 },
});
