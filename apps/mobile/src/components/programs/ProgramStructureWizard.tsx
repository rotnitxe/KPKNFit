import React, { useState, useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput } from 'react-native';
import { useColors } from '../../theme';
import { ProgramTemplateOption, SplitTemplate } from '../../types/workout';
import { PROGRAM_TEMPLATES } from '../../data/programTemplates';
import SplitGallery from './SplitGallery';
import { DumbbellIcon, ChevronDownIcon, CalendarIcon } from '../icons';

const DAYS_OF_WEEK = [
    { label: 'Domingo', value: 0 }, { label: 'Lunes', value: 1 }, { label: 'Martes', value: 2 },
    { label: 'Miércoles', value: 3 }, { label: 'Jueves', value: 4 }, { label: 'Viernes', value: 5 },
    { label: 'Sábado', value: 6 },
];

interface ProgramStructureWizardProps {
    onComplete: (data: any) => void;
    onCancel: () => void;
}

const ProgramStructureWizard: React.FC<ProgramStructureWizardProps> = ({ onComplete, onCancel }) => {
    const colors = useColors();
    const [step, setStep] = useState(1); // 1: Basic Info, 2: Template, 3: Split
    
    // State
    const [programName, setProgramName] = useState('');
    const [selectedTemplateId, setSelectedTemplateId] = useState(PROGRAM_TEMPLATES[0].id);
    const [startDay, setStartDay] = useState(1); // Lunes default
    const [cycleDuration, setCycleDuration] = useState(7);
    const [selectedSplit, setSelectedSplit] = useState<SplitTemplate | null>(null);

    const activeTemplate = useMemo(() => 
        PROGRAM_TEMPLATES.find(t => t.id === selectedTemplateId), 
    [selectedTemplateId]);

    const handleNext = () => {
        if (step < 3) setStep(step + 1);
        else {
            onComplete({
                name: programName,
                templateId: selectedTemplateId,
                startDay,
                cycleDuration,
                split: selectedSplit,
            });
        }
    };

    const handleBack = () => {
        if (step > 1) setStep(step - 1);
        else onCancel();
    };

    const canContinue = () => {
        if (step === 1) return programName.trim().length > 0;
        if (step === 2) return !!selectedTemplateId;
        if (step === 3) return !!selectedSplit;
        return false;
    };

    const renderStep1 = () => (
        <View style={styles.stepContainer}>
            <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>NOMBRE DEL PROGRAMA</Text>
            <TextInput
                value={programName}
                onChangeText={setProgramName}
                placeholder="Ej: Empuje Tirón de Élite"
                placeholderTextColor={colors.onSurfaceVariant + '60'}
                style={[styles.input, { color: colors.onSurface, backgroundColor: colors.surfaceContainerHigh, borderColor: colors.outlineVariant }]}
            />

            <View style={styles.grid}>
                <View style={styles.gridItem}>
                    <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>INICIO SEMANA</Text>
                    <View style={[styles.pickerContainer, { backgroundColor: colors.surfaceContainerHigh, borderColor: colors.outlineVariant }]}>
                        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.dayScroll}>
                            {DAYS_OF_WEEK.map(d => (
                                <TouchableOpacity
                                    key={d.value}
                                    onPress={() => setStartDay(d.value)}
                                    style={[
                                        styles.dayBadge,
                                        startDay === d.value && { backgroundColor: colors.primary }
                                    ]}
                                >
                                    <Text style={[styles.dayText, { color: startDay === d.value ? colors.onPrimary : colors.onSurfaceVariant }]}>
                                        {d.label.slice(0, 3)}
                                    </Text>
                                </TouchableOpacity>
                            ))}
                        </ScrollView>
                    </View>
                </View>

                <View style={styles.gridItem}>
                    <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>DÍAS POR CICLO</Text>
                    <View style={[styles.inputContainer, { backgroundColor: colors.surfaceContainerHigh, borderColor: colors.outlineVariant }]}>
                        <TextInput
                            value={cycleDuration.toString()}
                            onChangeText={v => setCycleDuration(parseInt(v) || 7)}
                            keyboardType="numeric"
                            style={[styles.numericInput, { color: colors.onSurface }]}
                        />
                    </View>
                </View>
            </View>
        </View>
    );

    const renderStep2 = () => (
        <ScrollView style={styles.stepContainer} showsVerticalScrollIndicator={false}>
            <Text style={[styles.stepTitle, { color: colors.onSurface }]}>Estructura Temporal</Text>
            <View style={styles.templateList}>
                {PROGRAM_TEMPLATES.map(t => {
                    const isSelected = selectedTemplateId === t.id;
                    return (
                        <TouchableOpacity
                            key={t.id}
                            onPress={() => setSelectedTemplateId(t.id)}
                            style={[
                                styles.templateCard,
                                { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant },
                                isSelected && { borderColor: colors.primary, borderWidth: 2 }
                            ]}
                        >
                            <View style={[styles.iconCircle, { backgroundColor: isSelected ? colors.primary : colors.surfaceVariant }]}>
                                <DumbbellIcon size={20} color={isSelected ? colors.onPrimary : colors.onSurfaceVariant} />
                            </View>
                            <View style={styles.templateInfo}>
                                <Text style={[styles.templateName, { color: colors.onSurface }]}>{t.name}</Text>
                                <Text style={[styles.templateDesc, { color: colors.onSurfaceVariant }]}>{t.description}</Text>
                            </View>
                        </TouchableOpacity>
                    );
                })}
            </View>
        </ScrollView>
    );

    const renderStep3 = () => (
        <View style={styles.stepContainerFull}>
            <View style={styles.splitPreview}>
                <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>PREVISUALIZACIÓN DE SPLIT</Text>
                {selectedSplit ? (
                    <View style={[styles.previewCard, { backgroundColor: colors.surfaceContainerHigh, borderColor: colors.outlineVariant }]}>
                        <View style={styles.previewHeader}>
                            <Text style={[styles.previewName, { color: colors.onSurface }]}>{selectedSplit.name}</Text>
                            <Text style={[styles.previewDays, { color: colors.primary }]}>
                                {selectedSplit.pattern.filter(d => d.toLowerCase() !== 'descanso').length} días de entreno
                            </Text>
                        </View>
                        <View style={styles.patternBar}>
                            {selectedSplit.pattern.map((day, i) => {
                                const dayLabel = DAYS_OF_WEEK[(startDay + i) % 7]?.label.slice(0, 3) || '';
                                const isRest = day.toLowerCase() === 'descanso';
                                return (
                                    <View key={i} style={styles.patternItem}>
                                        <Text style={[styles.patternLabel, { color: colors.onSurfaceVariant }]}>{dayLabel}</Text>
                                        <View style={[styles.patternDot, { backgroundColor: isRest ? colors.surfaceVariant : colors.primary }]} />
                                    </View>
                                );
                            })}
                        </View>
                    </View>
                ) : (
                    <View style={[styles.emptyPreview, { borderColor: colors.outlineVariant, borderStyle: 'dashed' }]}>
                        <Text style={[styles.emptyPreviewText, { color: colors.onSurfaceVariant }]}>Selecciona un split abajo</Text>
                    </View>
                )}
            </View>
            <View style={styles.galleryWrapper}>
                <SplitGallery 
                    onSelect={setSelectedSplit}
                    currentSplitId={selectedSplit?.id}
                />
            </View>
        </View>
    );

    return (
        <View style={styles.container}>
            <View style={styles.content}>
                {step === 1 && renderStep1()}
                {step === 2 && renderStep2()}
                {step === 3 && renderStep3()}
            </View>

            <View style={[styles.footer, { borderTopColor: colors.outlineVariant }]}>
                <TouchableOpacity onPress={handleBack} style={styles.backButton}>
                    <Text style={[styles.backButtonText, { color: colors.onSurfaceVariant }]}>
                        {step === 1 ? 'CANCELAR' : 'ATRÁS'}
                    </Text>
                </TouchableOpacity>
                
                <View style={styles.dots}>
                    {[1, 2, 3].map(i => (
                        <View 
                            key={i} 
                            style={[
                                styles.dot, 
                                { backgroundColor: step === i ? colors.primary : colors.outlineVariant }
                            ]} 
                        />
                    ))}
                </View>

                <TouchableOpacity 
                    onPress={handleNext} 
                    disabled={!canContinue()}
                    style={[
                        styles.nextButton, 
                        { backgroundColor: canContinue() ? colors.primary : colors.surfaceVariant }
                    ]}
                >
                    <Text style={[styles.nextButtonText, { color: canContinue() ? colors.onPrimary : colors.onSurfaceVariant }]}>
                        {step === 3 ? 'FINALIZAR' : 'SIGUIENTE'}
                    </Text>
                </TouchableOpacity>
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1 },
    content: { flex: 1 },
    stepContainer: { padding: 20 },
    stepContainerFull: { flex: 1 },
    stepTitle: { fontSize: 22, fontWeight: '900', marginBottom: 20, textAlign: 'center' },
    label: { fontSize: 10, fontWeight: '900', letterSpacing: 1, marginBottom: 8 },
    input: {
        height: 56,
        borderRadius: 16,
        borderWidth: 1,
        paddingHorizontal: 20,
        fontSize: 16,
        fontWeight: '700',
        marginBottom: 24,
    },
    grid: { flexDirection: 'row', gap: 12 },
    gridItem: { flex: 1 },
    pickerContainer: {
        height: 56,
        borderRadius: 16,
        borderWidth: 1,
        overflow: 'hidden',
    },
    dayScroll: { paddingHorizontal: 8, alignItems: 'center', gap: 6 },
    dayBadge: {
        width: 36,
        height: 36,
        borderRadius: 18,
        alignItems: 'center',
        justifyContent: 'center',
    },
    dayText: { fontSize: 12, fontWeight: '900' },
    inputContainer: {
        height: 56,
        borderRadius: 16,
        borderWidth: 1,
        alignItems: 'center',
        justifyContent: 'center',
    },
    numericInput: { fontSize: 18, fontWeight: '900', textAlign: 'center', width: '100%' },
    templateList: { gap: 12, paddingBottom: 40 },
    templateCard: {
        flexDirection: 'row',
        padding: 16,
        borderRadius: 20,
        borderWidth: 1,
        alignItems: 'center',
        gap: 16,
    },
    iconCircle: {
        width: 44,
        height: 44,
        borderRadius: 22,
        alignItems: 'center',
        justifyContent: 'center',
    },
    templateInfo: { flex: 1 },
    templateName: { fontSize: 16, fontWeight: '900', marginBottom: 2 },
    templateDesc: { fontSize: 12, lineHeight: 16, opacity: 0.7 },
    splitPreview: { padding: 20, paddingBottom: 0 },
    previewCard: {
        padding: 16,
        borderRadius: 20,
        borderWidth: 1,
        marginBottom: 12,
    },
    previewHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 },
    previewName: { fontSize: 14, fontWeight: '900' },
    previewDays: { fontSize: 11, fontWeight: '700' },
    patternBar: { flexDirection: 'row', gap: 4 },
    patternItem: { flex: 1, alignItems: 'center' },
    patternLabel: { fontSize: 8, fontWeight: '700', marginBottom: 4 },
    patternDot: { width: '100%', height: 6, borderRadius: 3 },
    emptyPreview: {
        height: 80,
        borderRadius: 20,
        borderWidth: 1,
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: 12,
    },
    emptyPreviewText: { fontSize: 12, fontWeight: '700', opacity: 0.5 },
    galleryWrapper: { flex: 1 },
    footer: {
        flexDirection: 'row',
        alignItems: 'center',
        padding: 20,
        paddingBottom: 30,
        borderTopWidth: 1,
        gap: 16,
    },
    backButton: { paddingVertical: 12 },
    backButtonText: { fontSize: 12, fontWeight: '900', letterSpacing: 0.5 },
    dots: { flex: 1, flexDirection: 'row', justifyContent: 'center', gap: 6 },
    dot: { width: 6, height: 6, borderRadius: 3 },
    nextButton: {
        paddingHorizontal: 24,
        paddingVertical: 12,
        borderRadius: 12,
    },
    nextButtonText: { fontSize: 12, fontWeight: '900', letterSpacing: 0.5 },
});

export default ProgramStructureWizard;
