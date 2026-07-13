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
import CustomExerciseEditorModal from '../../components/exercise/CustomExerciseEditorModal';
import { LiquidGlassCard } from '../../components/ui/LiquidGlassCard';
import { Button } from '../../components/ui/Button';
import { useColors } from '../../theme';
import type { Session, Exercise, ExerciseSet } from '../../types/workout';
import { generateId } from '../../utils/generateId';
import ReactNativeHapticFeedback from '@/services/hapticsService';
import DraggableFlatList from 'react-native-draggable-flatlist';
import { 
    getDynamicAugeMetrics, 
    classifyStressLevel,
    calculateExerciseFatigueScale 
} from '../../services/auge';
import { AugeDashboard } from '../../components/auge/AugeDashboard';
import { 
    estimatePercent1RM, 
    getEffectiveRepsForRM,
    calculateBrzycki1RM
} from '../../utils/calculations';
import { updateSessionExercise } from './sessionEditorMutations';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

type EditorPart = {
    id: string;
    name: string;
    exercises: Exercise[];
};

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
    const [showCustomEditor, setShowCustomEditor] = useState(false);
    const [activePartId, setActivePartId] = useState<string | null>(null);

    // Derived parts for editing
    const parts = useMemo<EditorPart[]>(() => {
        if (!session) return [];
        if (session.parts && session.parts.length > 0) {
            return session.parts.map(p => ({
                id: p.id,
                name: p.name,
                exercises: p.exercises || []
            }));
        }
        // Fallback: Group by superset or just one big part A
        return [{
            id: 'default-a',
            name: 'A',
            exercises: session.exercises || []
        }];
    }, [session]);

    if (!session) return null;

    const handleSave = async () => {
        if (!session || !programId || !weekId) return;
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
                { id: generateId(), targetReps: 10, weight: 0, targetRIR: 2, intensityMode: 'rir' }
            ],
            restTime: 120,
            trainingMode: 'reps'
        };

        if (session) {
            if (activePartId) {
                const nextParts = parts.map(p => {
                    if (p.id === activePartId) return { ...p, exercises: [...p.exercises, newExercise] };
                    return p;
                });
                setSession({ ...session, parts: nextParts, exercises: [] });
            } else if (session.parts && session.parts.length > 0) {
                const targetPartId = session.parts[session.parts.length - 1]?.id;
                const nextParts = session.parts.map(part => (
                    part.id === targetPartId
                        ? { ...part, exercises: [...(part.exercises || []), newExercise] }
                        : part
                ));
                setSession({ ...session, parts: nextParts, exercises: [] });
            } else {
                setSession({
                    ...session,
                    exercises: [...session.exercises, newExercise]
                });
            }
        }
        setPickerVisible(false);
        setActivePartId(null);
        ReactNativeHapticFeedback.trigger('impactLight');
    };

    const handleCreateNew = () => {
            setPickerVisible(false);
            setShowCustomEditor(true);
        };

    const addPart = () => {
        if (!session) return;
        const existingParts = session.parts && session.parts.length > 0
            ? session.parts.map(part => ({ ...part, exercises: [...(part.exercises || [])] }))
            : [{
                id: generateId(),
                name: 'A',
                exercises: [...(session.exercises || [])],
            }];
        const nextLetter = String.fromCharCode(65 + existingParts.length); // A, B, C...
        const newPart: EditorPart = {
            id: generateId(),
            name: nextLetter,
            exercises: []
        };
        setSession({
            ...session,
            parts: [...existingParts, newPart],
            exercises: [],
        });
        ReactNativeHapticFeedback.trigger('impactMedium');
    };

    const removeExercise = (exerciseId: string) => {
        ReactNativeHapticFeedback.trigger('impactMedium');
        if (session?.parts && session.parts.length > 0) {
            const nextParts = session.parts.map(p => ({
                ...p,
                exercises: p.exercises.filter(ex => ex.id !== exerciseId)
            }));
            setSession({ ...session, parts: nextParts });
        } else {
            setSession({
                ...session,
                exercises: session.exercises.filter(ex => ex.id !== exerciseId)
            });
        }
    };

    const addSet = (exerciseId: string) => {
        if (!session) return;
        ReactNativeHapticFeedback.trigger('selection');
        setSession(updateSessionExercise(session, exerciseId, ex => {
            const lastSet = ex.sets[ex.sets.length - 1];
            const newSet: ExerciseSet = {
                id: generateId(),
                targetReps: lastSet?.targetReps ?? 10,
                weight: lastSet?.weight ?? 0,
                targetRIR: lastSet?.targetRIR ?? 2
            };
            return { ...ex, sets: [...ex.sets, newSet] };
        }));
    };

    const removeSet = (exerciseId: string, setId: string) => {
        if (!session) return;
        ReactNativeHapticFeedback.trigger('impactLight');
        setSession(updateSessionExercise(session, exerciseId, ex => ({
            ...ex,
            sets: ex.sets.filter(s => s.id !== setId),
        })));
    };

    const updateSet = (exerciseId: string, setId: string, patch: Partial<ExerciseSet>) => {
        if (!session) return;
        setSession(updateSessionExercise(session, exerciseId, ex => ({
            ...ex,
            sets: ex.sets.map(s => s.id === setId ? { ...s, ...patch } : s),
        })));
    };

    const addWarmupSet = (exerciseId: string) => {
        if (!session) return;
        ReactNativeHapticFeedback.trigger('selection');
        setSession(updateSessionExercise(session, exerciseId, ex => {
            const newWarmup: ExerciseSet = {
                id: generateId(),
                targetReps: 12,
                weight: 0,
                intensityMode: 'rir',
                targetRIR: 4
            };
            return {
                ...ex,
                warmupSets: [...(ex.warmupSets || []), newWarmup]
            };
        }));
    };

    const removeWarmupSet = (exerciseId: string, setId: string) => {
        if (!session) return;
        ReactNativeHapticFeedback.trigger('impactLight');
        setSession(updateSessionExercise(session, exerciseId, ex => ({
            ...ex,
            warmupSets: (ex.warmupSets as ExerciseSet[] || []).filter(s => s.id !== setId)
        })));
    };

    const updateWarmupSet = (exerciseId: string, setId: string, patch: Partial<ExerciseSet>) => {
        if (!session) return;
        setSession(updateSessionExercise(session, exerciseId, ex => ({
            ...ex,
            warmupSets: (ex.warmupSets as ExerciseSet[] || []).map(s => s.id === setId ? { ...s, ...patch } : s),
        })));
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
                    <AugeDashboard />
                    
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
                        <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Estructura</Text>
                        <TouchableOpacity 
                            onPress={addPart} 
                            style={[styles.addButton, { backgroundColor: colors.secondaryContainer }]}
                        >
                            <PlusCircleIcon size={18} color={colors.secondary} />
                            <Text style={[styles.addButtonText, { color: colors.secondary }]}>Añadir Parte</Text>
                        </TouchableOpacity>
                    </View>

                    {parts.map((part) => (
                        <View key={part.id} style={styles.partContainer}>
                            <View style={styles.partHeader}>
                                <View style={[styles.partBadge, { backgroundColor: colors.primary }]}>
                                    <Text style={[styles.partBadgeText, { color: colors.onPrimary }]}>PARTE {part.name}</Text>
                                </View>
                                <TouchableOpacity 
                                    onPress={() => {
                                        setActivePartId(part.id);
                                        setPickerVisible(true);
                                    }}
                                    style={styles.addExToPartBtn}
                                >
                                    <PlusCircleIcon size={16} color={colors.primary} />
                                    <Text style={[styles.addExToPartText, { color: colors.primary }]}>Añadir Ejercicio</Text>
                                </TouchableOpacity>
                            </View>

                            <DraggableFlatList
  data={part.exercises}
  keyExtractor={(item) => item.id}
  onDragEnd={({ data }) => {
    // Actualiza el orden al soltar
    const nextParts = parts.map(p => {
      if (p.id === part.id) return { ...p, exercises: data };
      return p;
    });
    setSession({ ...session, parts: nextParts, exercises: [] });
  }}
   renderItem={({ item: ex, drag, isActive }) => {
    const exIndex = part.exercises.findIndex(e => e.id === ex.id);
    return (
      <LiquidGlassCard key={ex.id} style={{
        ...styles.exerciseCard,
        ...(isActive ? { opacity: 0.7 } : {})
      }}> 
      <View style={styles.exerciseHeader}>
        <TouchableOpacity onLongPress={drag} style={{ marginRight: 9, padding: 4 }}>
          <Text style={{ fontSize: 23, color: colors.outlineVariant, fontWeight: '900' }}>≡</Text>
        </TouchableOpacity>
        <View style={styles.exerciseTitleRow}>
          <View style={[styles.exerciseIndex, { backgroundColor: colors.secondaryContainer }]}>
                                                <Text style={[styles.exerciseIndexText, { color: colors.secondary }]}>
                                                    {part.name}{exIndex + 1}
                                                </Text>
                                            </View>
                                            <View style={{ flex: 1 }}>
                                                <Text style={[styles.exerciseName, { color: colors.onSurface }]} numberOfLines={1}>{ex.name}</Text>
                                                <View style={styles.augeMetricsRow}>
                                                    <ActivityIcon size={10} color={colors.primary} />
                                                    <Text style={[styles.augeMetricTxt, { color: colors.onSurfaceVariant }]}>
                                                        FATIGA: {calculateExerciseFatigueScale ? calculateExerciseFatigueScale(ex, undefined) : 0}/10
                                                    </Text>
                                                </View>
                                            </View>
                                        </View>
                                        <TouchableOpacity onPress={() => removeExercise(ex.id)} style={styles.trashBtn}>
                                            <TrashIcon size={18} color={colors.error} />
                                        </TouchableOpacity>
                                    </View>

                                    <View style={styles.setsList}>
                                        <View style={styles.setsHeaderRow}>
                                            <Text style={[styles.setLabel, { flex: 0.5 }]}>#</Text>
                                            <Text style={[styles.setLabel, { flex: 1.2 }]}>Peso (kg)</Text>
                                            <Text style={[styles.setLabel, { flex: 1 }]}>Reps</Text>
                                            <Text style={[styles.setLabel, { flex: 1 }]}>RIR/RPE</Text>
                                            <View style={{ width: 32 }} />
                                        </View>

                                        {/* Warmup Sets */}
                                        {(ex.warmupSets as ExerciseSet[] || []).map((set, sIndex) => (
                                            <View key={set.id} style={[styles.setRow, { opacity: 0.7 }]}>
                                                <Text style={[styles.setIndexText, { flex: 0.5, color: colors.secondary, fontWeight: '900' }]}>W</Text>
                                                <TextInput
                                                    style={[styles.setInp, { flex: 1.2, color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                                    keyboardType="numeric"
                                                    value={String(set.weight ?? 0)}
                                                    onChangeText={(t) => updateWarmupSet(ex.id, set.id, { weight: parseFloat(t) || 0 })}
                                                />
                                                <TextInput
                                                    style={[styles.setInp, { flex: 1, color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                                    keyboardType="numeric"
                                                    value={String(set.targetReps ?? 12)}
                                                    onChangeText={(t) => updateWarmupSet(ex.id, set.id, { targetReps: parseInt(t) || 0 })}
                                                />
                                                <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
                                                    <Text style={{ fontSize: 10, fontWeight: '900', color: colors.secondary }}>WARMUP</Text>
                                                </View>
                                                <TouchableOpacity onPress={() => removeWarmupSet(ex.id, set.id)} style={styles.setRemove}>
                                                    <Text style={{ color: colors.error, fontSize: 16 }}>×</Text>
                                                </TouchableOpacity>
                                            </View>
                                        ))}

                                        {/* Add Warmup Button */}
                                        <TouchableOpacity 
                                            onPress={() => addWarmupSet(ex.id)}
                                            style={[styles.addWarmupBtn, { borderColor: colors.secondary, opacity: 0.6 }]}
                                        >
                                            <Text style={[styles.addWarmupBtnText, { color: colors.secondary }]}>+ Serie Calentamiento</Text>
                                        </TouchableOpacity>

                                        <View style={{ height: 1, backgroundColor: colors.outlineVariant, marginVertical: 8, opacity: 0.3 }} />

                                        {ex.sets.map((set, sIndex) => (
                                            <View key={set.id} style={styles.setRow}>
                                                <Text style={[styles.setIndexText, { flex: 0.5, color: colors.onSurfaceVariant }]}>
                                                    {sIndex + 1}
                                                </Text>
                                                <TextInput
                                                    style={[styles.setInp, { flex: 1.2, color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                                    keyboardType="numeric"
                                                    value={String(set.weight ?? 0)}
                                                    onChangeText={(t) => updateSet(ex.id, set.id, { weight: parseFloat(t) || 0 })}
                                                />
                                                <TextInput
                                                    style={[styles.setInp, { flex: 1, color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                                    keyboardType="numeric"
                                                    value={String(set.targetReps ?? 10)}
                                                    onChangeText={(t) => updateSet(ex.id, set.id, { targetReps: parseInt(t) || 0 })}
                                                />
                                                <View style={{ flex: 1.2, flexDirection: 'row', gap: 4 }}>
                                                    <View style={{ flex: 1 }}>
                                                        <TextInput
                                                            style={[styles.setInp, { color: colors.onSurface, backgroundColor: colors.surfaceContainer }]}
                                                            keyboardType="numeric"
                                                            value={String(set.intensityMode === 'rpe' ? (set.targetRPE ?? 8) : (set.targetRIR ?? 2))}
                                                            onChangeText={(t) => {
                                                                const val = parseFloat(t) || 0;
                                                                updateSet(ex.id, set.id, set.intensityMode === 'rpe' ? { targetRPE: val } : { targetRIR: val });
                                                            }}
                                                        />
                                                        {(() => {
                                                            const effReps = getEffectiveRepsForRM(set);
                                                            const percent = effReps ? estimatePercent1RM(effReps) : null;
                                                            if (!percent) return null;
                                                            return (
                                                                <Text style={{ fontSize: 8, color: colors.primary, textAlign: 'center', marginTop: 2, fontWeight: '900' }}>
                                                                    {percent}%
                                                                </Text>
                                                            );
                                                        })()}
                                                    </View>
                                                    <TouchableOpacity 
                                                        onPress={() => updateSet(ex.id, set.id, { intensityMode: set.intensityMode === 'rpe' ? 'rir' : 'rpe' })}
                                                        style={styles.intensityToggle}
                                                    >
                                                        <Text style={{ fontSize: 8, fontWeight: '900', color: colors.primary }}>
                                                            {set.intensityMode === 'rpe' ? 'RPE' : 'RIR'}
                                                        </Text>
                                                    </TouchableOpacity>
                                                </View>
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
      );
    }}
  />
                </View>
            ))}

                    <TouchableOpacity 
                        onPress={() => {
                            setActivePartId(null);
                            setPickerVisible(true);
                        }}
                        style={[styles.finalAddBtn, { backgroundColor: colors.surfaceVariant }]}
                    >
                        <PlusCircleIcon size={24} color={colors.onSurfaceVariant} />
                        <Text style={[styles.finalAddBtnTxt, { color: colors.onSurfaceVariant }]}>AÑADIR EJERCICIO SUELTO</Text>
                    </TouchableOpacity>

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
                    onCreateNew={handleCreateNew}
                />

                <CustomExerciseEditorModal
                    visible={showCustomEditor}
                    onClose={() => setShowCustomEditor(false)}
                    onSave={addExercise}
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
    emptyText: {
        fontSize: 15,
        fontWeight: '600',
    },
    partContainer: {
        marginBottom: 20,
    },
    partHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingHorizontal: 20,
        marginBottom: 10,
    },
    partBadge: {
        paddingHorizontal: 10,
        paddingVertical: 4,
        borderRadius: 6,
    },
    partBadgeText: {
        fontSize: 10,
        fontWeight: '900',
    },
    addExToPartBtn: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
    },
    addExToPartText: {
        fontSize: 11,
        fontWeight: '700',
    },
    augeMetricsRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 4,
        marginTop: 2,
    },
    augeMetricTxt: {
        fontSize: 9,
        fontWeight: '700',
        letterSpacing: 0.5,
    },
    intensityToggle: {
        width: 24,
        height: 40,
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'rgba(0,0,0,0.03)',
        borderRadius: 6,
    },
    finalAddBtn: {
        marginHorizontal: 20,
        paddingVertical: 20,
        borderRadius: 20,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        borderWidth: 1.5,
        borderStyle: 'dashed',
        opacity: 0.6,
    },
    finalAddBtnTxt: {
        fontSize: 12,
        fontWeight: '900',
        letterSpacing: 1,
    },
    addWarmupBtn: {
        paddingVertical: 8,
        borderWidth: 1,
        borderStyle: 'dashed',
        borderRadius: 10,
        alignItems: 'center',
        marginBottom: 4,
    },
    addWarmupBtnText: {
        fontSize: 10,
        fontWeight: '900',
        textTransform: 'uppercase',
    },
});
