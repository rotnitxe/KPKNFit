import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { useColors } from '../../theme';
import { 
    ArrowLeftIcon, 
    ActivityIcon, 
    ZapIcon, 
    MoveIcon, 
    DumbbellIcon, 
    TargetIcon,
    CheckCircleIcon
} from '../icons';
import { AthleteProfileScore } from '../../types/workout';

interface AthleteProfilingWizardProps {
    onComplete: (score: AthleteProfileScore) => void;
    onCancel: () => void;
}

type QuestionStep = 'preference' | 'technique' | 'consistency' | 'strength' | 'mobility';

const AthleteProfilingWizard: React.FC<AthleteProfilingWizardProps> = ({ onComplete, onCancel }) => {
    const colors = useColors();
    const [step, setStep] = useState<number>(0);
    const [scores, setScores] = useState({
        preference: 'Bodybuilder' as 'Bodybuilder' | 'Powerbuilder' | 'Powerlifter',
        technique: 0 as 1 | 2 | 3,
        consistency: 0 as 1 | 2 | 3,
        strength: 0 as 1 | 2 | 3,
        mobility: 0 as 1 | 2 | 3,
    });

    const steps: { id: QuestionStep; title: string; description: string; icon: React.ReactNode }[] = [
        { id: 'preference', title: '¿Cuál es tu norte?', description: 'Cuéntanos qué te motiva hoy: ¿buscas verte mejor, sentirte más fuerte o un equilibrio de ambos?', icon: <TargetIcon size={24} color={colors.primary} /> },
        { id: 'technique', title: 'Tu conexión con el hierro', description: '¿Cómo te sientes al ejecutar tus movimientos? Queremos entender tu nivel de confianza con la técnica.', icon: <DumbbellIcon size={24} color={colors.primary} /> },
        { id: 'consistency', title: 'Tu ritmo de vida', description: 'El progreso nace de la constancia. ¿Cómo ha sido tu frecuencia de entrenamiento últimamente?', icon: <ActivityIcon size={24} color={colors.primary} /> },
        { id: 'strength', title: 'Tu nivel de fuerza actual', description: 'No importa dónde estés, sino hacia dónde vas. Danos una idea de tu fuerza respecto a tu peso.', icon: <ZapIcon size={24} color={colors.primary} /> },
        { id: 'mobility', title: '¿Cómo se siente tu cuerpo?', description: 'La libertad de movimiento es clave para evitar molestias y rendir al máximo.', icon: <MoveIcon size={24} color={colors.primary} /> },
    ];

    const options: Record<string, { value: any; label: string; detail: string }[]> = {
        preference: [
            { value: 'Bodybuilder', label: 'Estética y Vitalidad', detail: 'Mi prioridad es mejorar mi composición corporal y ganar masa muscular.' },
            { value: 'Powerbuilder', label: 'Fuerza con Propósito', detail: 'Busco el equilibrio: ser cada día más fuerte sin descuidar mi apariencia.' },
            { value: 'Powerlifter', label: 'Rendimiento Máximo', detail: 'Mi enfoque está en los números: quiero dominar los levantamientos principales.' },
        ],
        technique: [
            { value: 1, label: 'Estoy Construyendo mi Base', detail: 'Estoy aprendiendo los patrones básicos y puliendo mis movimientos.' },
            { value: 2, label: 'Me Siento Seguro', detail: 'Controlo la mayoría de los ejercicios con buena forma y conciencia.' },
            { value: 3, label: 'Dominio y Fluidez', detail: 'Mi técnica es sólida incluso cuando el esfuerzo es máximo.' },
        ],
        consistency: [
            { value: 1, label: 'Retomando el Hábito', detail: 'Estoy volviendo a empezar o mis semanas son algo irregulares.' },
            { value: 2, label: 'Ritmo Estable', detail: 'Entreno de forma constante unas 2 o 3 veces por semana.' },
            { value: 3, label: 'Compromiso Total', detail: 'El entrenamiento es parte innegociable de mi día a día (+4 veces).' },
        ],
        strength: [
            { value: 1, label: 'Descubriendo mi Fuerza', detail: 'Aún estoy conociendo mis límites y construyendo fuerza inicial.' },
            { value: 2, label: 'Fuerza Intermedia', detail: 'Manejo mi propio peso corporal con facilidad en ejercicios clave.' },
            { value: 3, label: 'Nivel Avanzado', detail: 'Muevo cargas pesadas con frecuencia (más de 1.5x mi peso).' },
        ],
        mobility: [
            { value: 1, label: 'Siento Rigidez', detail: 'Me cuesta llegar a rangos profundos o siento tensiones acumuladas.' },
            { value: 2, label: 'Movimiento Fluido', detail: 'Me muevo con libertad en la mayoría de mis entrenamientos.' },
            { value: 3, label: 'Gran Flexibilidad', detail: 'Tengo un rango de movimiento excelente y articulaciones ágiles.' },
        ],
    };

    const handleOptionSelect = (value: any) => {
        const currentStepId = steps[step].id;
        const newScores = { ...scores, [currentStepId]: value };
        setScores(newScores);

        if (step < steps.length - 1) {
            setStep(step + 1);
        } else {
            const total = newScores.technique + newScores.consistency + newScores.strength + newScores.mobility;
            onComplete({
                trainingStyle: newScores.preference,
                technicalScore: newScores.technique,
                consistencyScore: newScores.consistency,
                strengthScore: newScores.strength,
                mobilityScore: newScores.mobility,
                totalScore: total,
                profileLevel: total >= 8 ? 'Advanced' : 'Beginner'
            });
        }
    };

    const currentStepData = steps[step];
    const currentOptions = options[currentStepData?.id] || [];

    return (
        <View style={styles.container}>
            <View style={styles.header}>
                {step > 0 ? (
                    <TouchableOpacity onPress={() => setStep(step - 1)} style={styles.backBtn}>
                        <ArrowLeftIcon size={20} color={colors.primary} />
                        <Text style={[styles.backText, { color: colors.primary }]}>ATRÁS</Text>
                    </TouchableOpacity>
                ) : (
                    <TouchableOpacity onPress={onCancel} style={styles.backBtn}>
                        <Text style={[styles.backText, { color: colors.onSurfaceVariant, opacity: 0.5 }]}>CANCELAR</Text>
                    </TouchableOpacity>
                )}
                
                <View style={styles.progressRow}>
                    {steps.map((_, i) => (
                        <View 
                            key={i} 
                            style={[
                                styles.progressDot, 
                                { backgroundColor: i <= step ? colors.primary : colors.surfaceVariant },
                                i <= step && { width: 24 }
                            ]} 
                        />
                    ))}
                </View>
            </View>

            <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
                <View style={styles.iconContainer}>
                    {currentStepData.icon}
                </View>
                
                <Text style={[styles.title, { color: colors.onSurface }]}>{currentStepData.title}</Text>
                <Text style={[styles.description, { color: colors.onSurfaceVariant }]}>{currentStepData.description}</Text>

                <View style={styles.optionsList}>
                    {currentOptions.map((opt) => (
                        <TouchableOpacity
                            key={opt.value}
                            activeOpacity={0.7}
                            onPress={() => handleOptionSelect(opt.value)}
                            style={[
                                styles.optionCard,
                                { backgroundColor: colors.surfaceContainerHigh, borderColor: colors.outlineVariant }
                            ]}
                        >
                            <View style={styles.optionHeader}>
                                <Text style={[styles.optionLabel, { color: colors.onSurface }]}>{opt.label}</Text>
                                {typeof opt.value === 'number' && (
                                    <View style={styles.levelDots}>
                                        {[1, 2, 3].map(i => (
                                            <View 
                                                key={i} 
                                                style={[
                                                    styles.levelDot, 
                                                    { backgroundColor: i <= opt.value ? colors.primary : colors.surfaceVariant }
                                                ]} 
                                            />
                                        ))}
                                    </View>
                                )}
                            </View>
                            <Text style={[styles.optionDetail, { color: colors.onSurfaceVariant }]}>{opt.detail}</Text>
                        </TouchableOpacity>
                    ))}
                </View>
            </ScrollView>
        </View>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1 },
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: 20,
        paddingTop: 10,
        paddingBottom: 20,
    },
    backBtn: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
    },
    backText: {
        fontSize: 10,
        fontWeight: '900',
        letterSpacing: 1,
    },
    progressRow: {
        flexDirection: 'row',
        gap: 6,
    },
    progressDot: {
        height: 4,
        width: 8,
        borderRadius: 2,
    },
    scrollContent: {
        paddingHorizontal: 20,
        paddingBottom: 40,
    },
    iconContainer: {
        width: 48,
        height: 48,
        borderRadius: 24,
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: 16,
    },
    title: {
        fontSize: 24,
        fontWeight: '900',
        textTransform: 'uppercase',
        letterSpacing: -0.5,
        marginBottom: 8,
    },
    description: {
        fontSize: 15,
        lineHeight: 22,
        opacity: 0.7,
        marginBottom: 32,
    },
    optionsList: {
        gap: 12,
    },
    optionCard: {
        padding: 20,
        borderRadius: 24,
        borderWidth: 1,
    },
    optionHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 8,
    },
    optionLabel: {
        fontSize: 14,
        fontWeight: '900',
        textTransform: 'uppercase',
        letterSpacing: 0.5,
    },
    levelDots: {
        flexDirection: 'row',
        gap: 4,
    },
    levelDot: {
        width: 6,
        height: 6,
        borderRadius: 3,
    },
    optionDetail: {
        fontSize: 13,
        lineHeight: 18,
        opacity: 0.6,
    },
});

export default AthleteProfilingWizard;
