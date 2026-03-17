import React, { useState, useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { WorkoutStackParamList } from '../../navigation/types';
import { useColors } from '../../theme';
import { ArrowLeftIcon, CheckIcon, CalendarIcon } from '../../components/icons';
import { useProgramStore } from '../../stores/programStore';
import SplitGallery from '../../components/programs/SplitGallery';
import { SplitTemplate } from '../../types/workout';

const DAYS_OF_WEEK = [
    { label: 'Domingo', value: 0 }, { label: 'Lunes', value: 1 }, { label: 'Martes', value: 2 },
    { label: 'Miércoles', value: 3 }, { label: 'Jueves', value: 4 }, { label: 'Viernes', value: 5 },
    { label: 'Sábado', value: 6 },
];

export const SplitEditorScreen: React.FC = () => {
    const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
    const route = useRoute<RouteProp<WorkoutStackParamList, 'SplitEditor'>>();
    const colors = useColors();
    const { programId } = route.params;
    
    const { programs, updateProgramSplit } = useProgramStore();
    const program = useMemo(() => programs.find(p => p.id === programId), [programs, programId]);

    const [selectedSplit, setSelectedSplit] = useState<SplitTemplate | null>(null);
    const [startDay, setStartDay] = useState(program?.startDay ?? 1);
    const [scope, setScope] = useState<'week' | 'block' | 'program'>('program');

    if (!program) {
        return (
            <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
                <Text style={{ color: colors.onSurface }}>Programa no encontrado</Text>
            </SafeAreaView>
        );
    }

    const handleSave = async () => {
        if (!selectedSplit) {
            Alert.alert("Atención", "Selecciona un split de la galería para aplicar cambios.");
            return;
        }

        Alert.alert(
            "Confirmar Cambio",
            `¿Estás seguro de que quieres aplicar el split "${selectedSplit.name}" a ${scope === 'program' ? 'todo el programa' : scope === 'block' ? 'este bloque' : 'esta semana'}? Se regenerarán las sesiones vacías.`,
            [
                { text: "Cancelar", style: "cancel" },
                { 
                    text: "Aplicar", 
                    onPress: async () => {
                        try {
                            await updateProgramSplit(programId, selectedSplit, startDay, scope);
                            Alert.alert("Éxito", "Split actualizado correctamente.");
                            navigation.goBack();
                        } catch (error) {
                            Alert.alert("Error", "No se pudo actualizar el split.");
                        }
                    }
                }
            ]
        );
    };

    return (
        <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
            <View style={styles.header}>
                <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
                    <ArrowLeftIcon size={24} color={colors.onSurface} />
                </TouchableOpacity>
                <Text style={[styles.title, { color: colors.onSurface }]}>Editor de Split</Text>
                <TouchableOpacity onPress={handleSave} style={[styles.saveBtn, { backgroundColor: colors.primary }]}>
                    <CheckIcon size={20} color={colors.onPrimary} />
                </TouchableOpacity>
            </View>

            <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
                {/* Current Settings */}
                <View style={[styles.section, { backgroundColor: colors.surfaceContainer }]}>
                    <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>CONFIGURACIÓN TEMPORAL</Text>
                    
                    <Text style={[styles.label, { color: colors.onSurface, marginTop: 12 }]}>Día de inicio del ciclo</Text>
                    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.dayScroll}>
                        {DAYS_OF_WEEK.map(d => (
                            <TouchableOpacity
                                key={d.value}
                                onPress={() => setStartDay(d.value)}
                                style={[
                                    styles.dayBadge,
                                    { backgroundColor: colors.surfaceContainerHigh },
                                    startDay === d.value && { backgroundColor: colors.primary }
                                ]}
                            >
                                <Text style={[styles.dayText, { color: startDay === d.value ? colors.onPrimary : colors.onSurfaceVariant }]}>
                                    {d.label.slice(0, 3)}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </ScrollView>

                    <Text style={[styles.label, { color: colors.onSurface, marginTop: 16 }]}>Alcance del cambio</Text>
                    <View style={styles.scopeContainer}>
                        {(['week', 'block', 'program'] as const).map(s => (
                            <TouchableOpacity
                                key={s}
                                onPress={() => setScope(s)}
                                style={[
                                    styles.scopeBadge,
                                    { backgroundColor: colors.surfaceContainerHigh, borderColor: colors.outlineVariant },
                                    scope === s && { backgroundColor: colors.secondaryContainer, borderColor: colors.secondary }
                                ]}
                            >
                                <Text style={[styles.scopeText, { color: scope === s ? colors.onSecondaryContainer : colors.onSurfaceVariant }]}>
                                    {s === 'week' ? 'Semana' : s === 'block' ? 'Bloque' : 'Programa'}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                </View>

                {/* Preview if selected */}
                {selectedSplit && (
                    <View style={[styles.previewSection, { backgroundColor: colors.primaryContainer + '30', borderColor: colors.primary }]}>
                        <View style={styles.previewHeader}>
                            <Text style={[styles.previewTitle, { color: colors.primary }]}>Nuevo Split: {selectedSplit.name}</Text>
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
                )}

                {/* Gallery */}
                <View style={styles.galleryContainer}>
                    <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant, marginLeft: 20, marginBottom: 10 }]}>CATÁLOGO DE SPLITS</Text>
                    <SplitGallery 
                        onSelect={setSelectedSplit}
                        currentSplitId={selectedSplit?.id || program.selectedSplitId}
                    />
                </View>
            </ScrollView>
        </SafeAreaView>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1 },
    header: { flexDirection: 'row', alignItems: 'center', padding: 16, gap: 16 },
    backBtn: { padding: 4 },
    title: { flex: 1, fontSize: 20, fontWeight: '900' },
    saveBtn: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
    content: { flex: 1 },
    section: { margin: 16, padding: 16, borderRadius: 24 },
    sectionLabel: { fontSize: 10, fontWeight: '900', letterSpacing: 1 },
    label: { fontSize: 14, fontWeight: '700', marginBottom: 8 },
    dayScroll: { gap: 8, paddingVertical: 4 },
    dayBadge: { width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center' },
    dayText: { fontSize: 12, fontWeight: '900' },
    scopeContainer: { flexDirection: 'row', gap: 8, marginTop: 4 },
    scopeBadge: { flex: 1, height: 40, borderRadius: 12, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
    scopeText: { fontSize: 12, fontWeight: '800' },
    previewSection: { margin: 16, padding: 16, borderRadius: 24, borderWidth: 1, gap: 12 },
    previewHeader: { flexDirection: 'row', justifyContent: 'space-between' },
    previewTitle: { fontSize: 14, fontWeight: '900' },
    patternBar: { flexDirection: 'row', gap: 4 },
    patternItem: { flex: 1, alignItems: 'center' },
    patternLabel: { fontSize: 8, fontWeight: '700', marginBottom: 4 },
    patternDot: { width: '100%', height: 6, borderRadius: 3 },
    galleryContainer: { flex: 1, paddingBottom: 40 },
});
