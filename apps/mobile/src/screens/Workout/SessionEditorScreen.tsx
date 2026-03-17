import React, { useState, useMemo } from 'react';
import {
    View,
    Text,
    StyleSheet,
    ScrollView,
    TouchableOpacity,
    TextInput,
    Dimensions,
    Platform,
    KeyboardAvoidingView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { WorkoutStackParamList } from '../../navigation/types';
import { useProgramStore } from '../../stores/programStore';
import { PlusCircleIcon, TrashIcon, ArrowLeftIcon, SearchIcon, ActivityIcon, InfoIcon } from '../../components/icons';
import { AdvancedExercisePicker } from '../../components/exercise/AdvancedExercisePicker';
import { LiquidGlassCard } from '../../components/ui/LiquidGlassCard';
import { Button } from '../../components/ui/Button';
import { useColors } from '../../theme';
import type { Session, Exercise, ExerciseSet } from '../../types/workout';
import { generateId } from '../../utils/generateId';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

export const SessionEditorScreen: React.FC = () => {
    const route = useRoute<RouteProp<WorkoutStackParamList, 'SessionEditor'>>();
    const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
    const { programId, weekId, sessionId } = route.params;
    const colors = useColors();

    const programs = useProgramStore(s => s.programs);
    const updateSession = useProgramStore(s => s.updateSession);

    const program = useMemo(() => programs.find(p => p.id === programId), [programs, programId]);
    
    const sessionOriginal = useMemo(() => {
        if (!program) return null;
        for (const macro of program.macrocycles) {
            for (const block of macro.blocks ?? []) {
                for (const meso of block.mesocycles) {
                    for (const week of meso.weeks) {
                        if (week.id === weekId) {
                            return week.sessions.find(s => s.id === sessionId);
                        }
                    }
                }
            }
        }
        return null;
    }, [program, weekId, sessionId]);

    const [session, setSession] = useState<Session | null>(sessionOriginal ?? null);
    const [pickerVisible, setPickerVisible] = useState(false);

    if (!session) return null;

    const handleSave = async () => {
        if (!session) return;
        ReactNativeHapticFeedback.trigger('notificationSuccess');
        await updateSession(programId, weekId, session);
        navigation.goBack();
    };

    const addExercise = (exInfo: any) => {
        const newExercise: Exercise = {
            id: generateId(),
            name: exInfo.name,
            exerciseDbId: exInfo.id,
            sets: [
                { id: generateId(), targetReps: 10, weight: 0, targetRIR: 2 }
            ],
            restTime: 120,
            trainingMode: 'reps'
        };
        setSession({
            ...session,
            exercises: [...session.exercises, newExercise]
        });
        setPickerVisible(false);
        ReactNativeHapticFeedback.trigger('impactLight');
    };

    const removeExercise = (exerciseId: string) => {
        ReactNativeHapticFeedback.trigger('impactMedium');
        setSession({
            ...session,
            exercises: session.exercises.filter(ex => ex.id !== exerciseId)
        });
    };

    const addSet = (exerciseId: string) => {
        ReactNativeHapticFeedback.trigger('selection');
        setSession({
            ...session,
            exercises: session.exercises.map(ex => {
                if (ex.id !== exerciseId) return ex;
                const lastSet = ex.sets[ex.sets.length - 1];
                const newSet: ExerciseSet = {
                    id: generateId(),
                    targetReps: lastSet?.targetReps ?? 10,
                    weight: lastSet?.weight ?? 0,
                    targetRIR: lastSet?.targetRIR ?? 2
                };
                return { ...ex, sets: [...ex.sets, newSet] };
            })
        });
    };

    const removeSet = (exerciseId: string, setId: string) => {
        ReactNativeHapticFeedback.trigger('impactLight');
        setSession({
            ...session,
            exercises: session.exercises.map(ex => {
                if (ex.id !== exerciseId) return ex;
                return { ...ex, sets: ex.sets.filter(s => s.id !== setId) };
            })
        });
    };

    const updateSet = (exerciseId: string, setId: string, patch: Partial<ExerciseSet>) => {
        setSession({
            ...session,
            exercises: session.exercises.map(ex => {
                if (ex.id !== exerciseId) return ex;
                return {
                    ...ex,
                    sets: ex.sets.map(s => s.id === setId ? { ...s, ...patch } : s)
                };
            })
        });
    };

    return (
        <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
            <KeyboardAvoidingView 
                behavior={Platform.OS === 'ios' ? 'padding' : undefined}
                style={{ flex: 1 }}
            >
                <View style={styles.header}>
                    <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerIcon}>
                        <ArrowLeftIcon size={24} color={colors.onSurface} />
                    </TouchableOpacity>
                    <Text style={[styles.title, { color: colors.onSurface }]}>Editor de Sesión</Text>
                    <TouchableOpacity onPress={handleSave} style={[styles.headerIcon, { backgroundColor: colors.primary }]}>
                        <Text style={{ color: colors.onPrimary, fontWeight: '900' }}>✓</Text>
                    </TouchableOpacity>
                </View>

                <ScrollView 
                    contentContainerStyle={styles.scrollContent}
                    showsVerticalScrollIndicator={false}
                >
                    <LiquidGlassCard style={styles.sessionCard}>
                        <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Configuración General</Text>
                        <TextInput
                            style={[styles.input, { color: colors.onSurface, borderColor: colors.outlineVariant }]}
                            value={session.name}
                            onChangeText={(t) => setSession({ ...session, name: t })}
                            placeholder="Nombre de la sesión"
                            placeholderTextColor={colors.onSurfaceVariant}
                        />
                        <TextInput
                            style={[styles.input, styles.textArea, { color: colors.onSurface, borderColor: colors.outlineVariant }]}
                            value={session.focus}
                            onChangeText={(t) => setSession({ ...session, focus: t })}
                            placeholder="Foco o descripción corta..."
                            placeholderTextColor={colors.onSurfaceVariant}
                            multiline
                        />
                    </LiquidGlassCard>

                    <View style={styles.sectionHeader}>
                        <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Ejercicios</Text>
                        <TouchableOpacity 
                            onPress={() => setPickerVisible(true)} 
                            style={[styles.addButton, { backgroundColor: colors.primaryContainer }]}
                        >
                            <PlusCircleIcon size={18} color={colors.primary} />
                            <Text style={[styles.addButtonText, { color: colors.primary }]}>Añadir</Text>
                        </TouchableOpacity>
                    </View>

                    {session.exercises.length === 0 && (
                        <View style={styles.emptyState}>
                            <InfoIcon size={32} color={colors.onSurfaceVariant} />
                            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
                                No hay ejercicios en esta sesión.
                            </Text>
                        </View>
                    )}

                    {session.exercises.map((ex, exIndex) => (
                        <LiquidGlassCard key={ex.id} style={styles.exerciseCard}>
                            <View style={styles.exerciseHeader}>
                                <View style={styles.exerciseTitleRow}>
                                    <View style={[styles.exerciseIndex, { backgroundColor: colors.secondaryContainer }]}>
                                        <Text style={[styles.exerciseIndexText, { color: colors.secondary }]}>
                                            {exIndex + 1}
                                        </Text>
                                    </View>
                                    <View>
                                        <Text style={[styles.exerciseName, { color: colors.onSurface }]}>{ex.name}</Text>
                                        <Text style={[styles.exerciseMeta, { color: colors.onSurfaceVariant }]}>
                                            {ex.trainingMode === 'reps' ? 'Repeticiones' : 'Tiempo'}
                                        </Text>
                                    </View>
                                </View>
                                <TouchableOpacity onPress={() => removeExercise(ex.id)} style={styles.trashBtn}>
                                    <TrashIcon size={20} color={colors.error} />
                                </TouchableOpacity>
                            </View>

                            <View style={styles.setsList}>
                                <View style={styles.setsHeaderRow}>
                                    <Text style={[styles.setLabel, { flex: 0.6 }]}>#</Text>
                                    <Text style={[styles.setLabel, { flex: 1 }]}>Pesos</Text>
                                    <Text style={[styles.setLabel, { flex: 1 }]}>Reps</Text>
                                    <Text style={[styles.setLabel, { flex: 1 }]}>RIR</Text>
                                    <View style={{ width: 32 }} />
                                </View>

                                {ex.sets.map((set, sIndex) => (
                                    <View key={set.id} style={styles.setRow}>
                                        <Text style={[styles.setIndexText, { flex: 0.6, color: colors.onSurfaceVariant }]}>
                                            {sIndex + 1}
                                        </Text>
                                        <TextInput
                                            style={[styles.setInp, { flex: 1, color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                            keyboardType="numeric"
                                            value={String(set.weight)}
                                            onChangeText={(t) => updateSet(ex.id, set.id, { weight: parseFloat(t) || 0 })}
                                        />
                                        <TextInput
                                            style={[styles.setInp, { flex: 1, color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                            keyboardType="numeric"
                                            value={String(set.targetReps)}
                                            onChangeText={(t) => updateSet(ex.id, set.id, { targetReps: parseInt(t) || 0 })}
                                        />
                                        <TextInput
                                            style={[styles.setInp, { flex: 1, color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                            keyboardType="numeric"
                                            value={String(set.targetRIR)}
                                            onChangeText={(t) => updateSet(ex.id, set.id, { targetRIR: parseFloat(t) || 0 })}
                                        />
                                        <TouchableOpacity onPress={() => removeSet(ex.id, set.id)} style={styles.setRemove}>
                                            <Text style={{ color: colors.error, fontSize: 16 }}>×</Text>
                                        </TouchableOpacity>
                                    </View>
                                ))}

                                <TouchableOpacity 
                                    onPress={() => addSet(ex.id)} 
                                    style={[styles.addSetBtn, { borderColor: colors.outlineVariant }]}
                                >
                                    <Text style={[styles.addSetBtnText, { color: colors.primary }]}>+ Añadir Serie</Text>
                                </TouchableOpacity>
                            </View>
                        </LiquidGlassCard>
                    ))}

                    <View style={{ height: 40 }} />
                    <Button 
                        variant="primary" 
                        onPress={handleSave}
                        style={{ marginHorizontal: 20, marginBottom: 40 }}
                    >
                        Guardar Cambios
                    </Button>
                </ScrollView>

                <AdvancedExercisePicker
                    visible={pickerVisible}
                    onClose={() => setPickerVisible(false)}
                    onSelect={addExercise}
                />
            </KeyboardAvoidingView>
        </SafeAreaView>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: 20,
        height: 64,
        justifyContent: 'space-between',
    },
    headerIcon: {
        width: 40,
        height: 40,
        borderRadius: 20,
        justifyContent: 'center',
        alignItems: 'center',
    },
    title: {
        fontSize: 20,
        fontWeight: '900',
        letterSpacing: -0.5,
    },
    scrollContent: {
        paddingVertical: 12,
    },
    sessionCard: {
        marginHorizontal: 16,
        padding: 20,
        marginBottom: 24,
    },
    label: {
        fontSize: 10,
        fontWeight: '900',
        textTransform: 'uppercase',
        letterSpacing: 1.5,
        marginBottom: 12,
    },
    input: {
        height: 52,
        borderRadius: 16,
        borderWidth: 1.5,
        paddingHorizontal: 16,
        fontSize: 16,
        fontWeight: '700',
        marginBottom: 12,
    },
    textArea: {
        height: 80,
        paddingTop: 12,
        textAlignVertical: 'top',
    },
    sectionHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingHorizontal: 20,
        marginBottom: 16,
    },
    sectionTitle: {
        fontSize: 22,
        fontWeight: '900',
        letterSpacing: -0.5,
    },
    addButton: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
        paddingHorizontal: 14,
        paddingVertical: 8,
        borderRadius: 20,
    },
    addButtonText: {
        fontSize: 13,
        fontWeight: '800',
    },
    exerciseCard: {
        marginHorizontal: 16,
        marginBottom: 16,
        padding: 16,
    },
    exerciseHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 16,
    },
    exerciseTitleRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
    },
    exerciseIndex: {
        width: 36,
        height: 36,
        borderRadius: 12,
        justifyContent: 'center',
        alignItems: 'center',
    },
    exerciseIndexText: {
        fontSize: 16,
        fontWeight: '900',
    },
    exerciseName: {
        fontSize: 17,
        fontWeight: '800',
    },
    exerciseMeta: {
        fontSize: 11,
        fontWeight: '600',
        marginTop: 2,
    },
    trashBtn: {
        padding: 8,
    },
    setsList: {
        gap: 8,
    },
    setsHeaderRow: {
        flexDirection: 'row',
        paddingHorizontal: 4,
        marginBottom: 4,
    },
    setLabel: {
        fontSize: 10,
        fontWeight: '800',
        color: 'rgba(0,0,0,0.4)',
        textAlign: 'center',
        textTransform: 'uppercase',
    },
    setRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
    },
    setIndexText: {
        fontSize: 12,
        fontWeight: '800',
        textAlign: 'center',
    },
    setInp: {
        height: 40,
        borderRadius: 10,
        textAlign: 'center',
        fontSize: 14,
        fontWeight: '800',
    },
    setRemove: {
        width: 32,
        height: 40,
        justifyContent: 'center',
        alignItems: 'center',
    },
    addSetBtn: {
        marginTop: 8,
        paddingVertical: 12,
        borderWidth: 1.5,
        borderStyle: 'dashed',
        borderRadius: 14,
        alignItems: 'center',
    },
    addSetBtnText: {
        fontSize: 13,
        fontWeight: '800',
    },
    emptyState: {
        alignItems: 'center',
        paddingVertical: 40,
        gap: 12,
    },
    emptyText: {
        fontSize: 15,
        fontWeight: '600',
    },
});
