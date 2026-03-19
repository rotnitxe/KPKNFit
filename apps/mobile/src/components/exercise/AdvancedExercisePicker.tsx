import React, { useState, useMemo, useCallback } from 'react';
import {
    View,
    Text,
    StyleSheet,
    Modal,
    TextInput,
    TouchableOpacity,
    FlatList,
    Dimensions,
    Pressable,
    Platform,
} from 'react-native';
import Animated, {
    useSharedValue,
    useAnimatedStyle,
    withSpring,
    withTiming,
    runOnJS,
} from 'react-native-reanimated';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { ExerciseMuscleInfo } from '../../types/workout';
import { SearchIcon, XCircleIcon, ActivityIcon, PlusCircleIcon, StarIcon, InfoIcon, DumbbellIcon, GridIcon } from '../icons';
import { loadPersistedDomainPayload } from '../../services/mobilePersistenceService';
import type { WorkoutLogSummary } from '@kpkn/shared-types';
import { Canvas, Blur, Rect, ColorMatrix, Paint } from '@shopify/react-native-skia';

const { height: SCREEN_HEIGHT, width: SCREEN_WIDTH } = Dimensions.get('window');

const PWA = {
    page: '#FEF7FF',
    card: '#FFFFFF',
    border: '#E7E0EC',
    text: '#1C1B1F',
    muted: '#49454F',
    primary: '#6750A4',
    primarySoft: '#EADDFF',
    accent: '#22D3EE',
};

interface AdvancedExercisePickerProps {
    visible: boolean;
    onClose: () => void;
    onSelect: (exercise: ExerciseMuscleInfo) => void;
    onCreateNew: () => void;
}

export const AdvancedExercisePicker: React.FC<AdvancedExercisePickerProps> = ({
    visible,
    onClose,
    onSelect,
    onCreateNew,
}) => {
    const { exerciseList } = useExerciseStore();
    const [search, setSearch] = useState('');
    const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
    const [frequentExerciseIds] = useState<Set<string>>(new Set());
    const [favoriteExerciseIds, setFavoriteExerciseIds] = useState<Set<string>>(new Set());
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const translateY = useSharedValue(SCREEN_HEIGHT);

    React.useEffect(() => {
        if (visible) {
            translateY.value = withSpring(0, { damping: 20, stiffness: 90 });
            const favorites = exerciseList
                .filter(ex => ex.isFavorite)
                .map(ex => ex.id);
            setFavoriteExerciseIds(new Set(favorites));
        } else {
            translateY.value = withTiming(SCREEN_HEIGHT);
        }
    }, [visible, exerciseList]);

    const animatedStyle = useAnimatedStyle(() => ({
        transform: [{ translateY: translateY.value }],
    }));

    const closeWithAnimation = useCallback(() => {
        translateY.value = withTiming(SCREEN_HEIGHT, {}, () => {
            runOnJS(onClose)();
        });
    }, [onClose]);

    const categories = useMemo(() => {
        const cats = new Set<string>();
        exerciseList.forEach(ex => {
            if (ex.category) cats.add(ex.category);
        });
        return Array.from(cats);
    }, [exerciseList]);

    // Categories for Grid View
    const gridCategories = useMemo(() => [
        { id: 'Pecho', label: 'Pecho', cols: 'col-span-1' },
        { id: 'Espalda', label: 'Espalda', cols: 'col-span-1' },
        { id: 'Hombros', label: 'Hombros', cols: 'col-span-1' },
        { id: 'Piernas', label: 'Piernas', cols: 'col-span-1' },
        { id: 'Brazos', label: 'Brazos', cols: 'col-span-1' },
        { id: 'Core', label: 'Core', cols: 'col-span-1' },
    ], []);

    const filteredExercises = useMemo(() => {
        let list = exerciseList;
        if (selectedCategory) {
            list = list.filter(ex => ex.category === selectedCategory);
        }
        let searchResults = list;
        if (search.trim()) {
            const q = search.toLowerCase();
            searchResults = list.filter(ex =>
                ex.name.toLowerCase().includes(q) ||
                ex.involvedMuscles?.some(m => m.muscle.toString().toLowerCase().includes(q)) ||
                ex.equipment?.toLowerCase().includes(q)
            );
        }
        const ranked = searchResults.map(ex => {
            let score = 0;
            if (favoriteExerciseIds.has(ex.id)) score += 100;
            if (search.trim()) {
                const q = search.toLowerCase();
                if (ex.name.toLowerCase() === q) score += 200;
                else if (ex.name.toLowerCase().startsWith(q)) score += 100;
                else if (ex.name.toLowerCase().includes(q)) score += 30;
                
                if (ex.involvedMuscles?.some(m => m.muscle.toString().toLowerCase() === q)) score += 80;
                if (ex.equipment?.toLowerCase() === q) score += 50;
            }
            if (ex.tier === 'T1') score += 20;
            return { exercise: ex, score };
        });
        ranked.sort((a, b) => b.score - a.score || a.exercise.name.localeCompare(b.exercise.name));
        return ranked.map(r => r.exercise);
    }, [search, selectedCategory, exerciseList, favoriteExerciseIds]);

    const gesture = Gesture.Pan()
        .onUpdate((event) => {
            if (event.translationY > 0) translateY.value = event.translationY;
        })
        .onEnd((event) => {
            if (event.translationY > 150 || event.velocityY > 500) {
                runOnJS(closeWithAnimation)();
            } else {
                translateY.value = withSpring(0);
            }
        });

    if (!visible) return null;

    return (
        <Modal transparent visible={visible} animationType="none">
            <View style={styles.overlay}>
                <View style={StyleSheet.absoluteFill}>
                    <Canvas style={StyleSheet.absoluteFill}>
                        <Rect x={0} y={0} width={SCREEN_WIDTH} height={SCREEN_HEIGHT}>
                            <Blur blur={20} />
                            <ColorMatrix matrix={[
                                1, 0, 0, 0, 0,
                                0, 1, 0, 0, 0,
                                0, 0, 1, 0, 0,
                                0, 0, 0, 0.4, 0,
                            ]} />
                        </Rect>
                    </Canvas>
                </View>

                <Pressable style={StyleSheet.absoluteFill} onPress={closeWithAnimation} />
                
                <GestureDetector gesture={gesture}>
                    <Animated.View style={[styles.sheet, animatedStyle]}>
                        <View style={styles.handle} />
                        <View style={styles.header}>
                            <Text style={styles.title}>Ejercicios</Text>
                            <TouchableOpacity onPress={closeWithAnimation} style={styles.closeBtn}>
                                <XCircleIcon size={24} color={PWA.muted} />
                            </TouchableOpacity>
                        </View>

                        <View style={styles.searchContainer}>
                            <View style={styles.searchBar}>
                                <SearchIcon size={18} color={PWA.muted} />
                                <TextInput
                                    style={styles.searchInput}
                                    placeholder="Buscar ejercicio o músculo..."
                                    placeholderTextColor="#94A3B8"
                                    value={search}
                                    onChangeText={setSearch}
                                />
                            </View>
                        </View>

                        {/* View Mode Toggle & Create Button */}
                        <View style={styles.subHeader}>
                            <TouchableOpacity onPress={() => setViewMode(viewMode === 'grid' ? 'list' : 'grid')} style={styles.viewModeBtn}>
                                <GridIcon size={16} color={PWA.muted} />
                                <Text style={styles.subHeaderText}>{viewMode === 'grid' ? 'Cuadrícula' : 'Lista'}</Text>
                            </TouchableOpacity>
                            <TouchableOpacity onPress={onCreateNew} style={styles.createBtn}>
                                <PlusCircleIcon size={16} color={PWA.primary} />
                                <Text style={styles.createBtnText}>Crear Nuevo</Text>
                            </TouchableOpacity>
                        </View>

                        {viewMode === 'grid' && !search && !selectedCategory ? (
                            <View style={styles.gridContainer}>
                                {gridCategories.map((cat) => (
                                    <TouchableOpacity
                                        key={cat.id}
                                        onPress={() => {
                                            setSelectedCategory(cat.id);
                                            setViewMode('list');
                                        }}
                                        style={[styles.gridItem, cat.cols === 'col-span-1' && { height: 80 }]}
                                    >
                                        <Text style={styles.gridItemText}>{cat.label}</Text>
                                    </TouchableOpacity>
                                ))}
                            </View>
                        ) : (
                            <FlatList
                                data={filteredExercises}
                                keyExtractor={(item) => item.id}
                                contentContainerStyle={styles.listContent}
                                showsVerticalScrollIndicator={false}
                                renderItem={({ item }) => {
                                    const isFavorite = favoriteExerciseIds.has(item.id);
                                    return (
                                        <TouchableOpacity style={styles.item} onPress={() => onSelect(item)}>
                                            <View style={styles.itemIconContainer}>
                                                <ActivityIcon size={20} color={PWA.primary} />
                                            </View>
                                            <View style={styles.itemInfo}>
                                                <View style={styles.itemHeader}>
                                                    <Text style={styles.itemName}>{item.name}</Text>
                                                    {isFavorite && <StarIcon size={14} color="#FFD700" fill="#FFD700" />}
                                                </View>
                                                <Text style={styles.itemMeta}>{item.category} · {item.equipment}</Text>
                                            </View>
                                            <PlusCircleIcon size={22} color={PWA.primarySoft} />
                                        </TouchableOpacity>
                                    );
                                }}
                            />
                        )}
                        
                        {filteredExercises.length === 0 && (
                            <View style={{ padding: 20, alignItems: 'center' }}>
                                <Text style={{ color: PWA.muted, marginBottom: 10 }}>No se encontraron ejercicios.</Text>
                                <TouchableOpacity onPress={onCreateNew}>
                                    <Text style={{ color: PWA.primary }}>Crear Nuevo Ejercicio</Text>
                                </TouchableOpacity>
                            </View>
                        )}
                    </Animated.View>
                </GestureDetector>
            </View>
        </Modal>
    );
};

const styles = StyleSheet.create({
    overlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.2)',
        justifyContent: 'flex-end',
    },
    sheet: {
        height: SCREEN_HEIGHT * 0.85,
        backgroundColor: 'rgba(255,255,255,0.92)',
        borderTopLeftRadius: 36,
        borderTopRightRadius: 36,
        borderTopWidth: 1,
        borderTopColor: 'rgba(255,255,255,0.5)',
        shadowColor: '#000',
        shadowOffset: { width: 0, height: -10 },
        shadowOpacity: 0.1,
        shadowRadius: 15,
        elevation: 10,
    },
    handle: {
        width: 44,
        height: 4,
        backgroundColor: '#E2E8F0',
        borderRadius: 2,
        alignSelf: 'center',
        marginVertical: 12,
    },
    header: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingHorizontal: 24,
        marginBottom: 16,
    },
    title: {
        fontSize: 22,
        fontWeight: '900',
        color: PWA.text,
        letterSpacing: -0.5,
    },
    closeBtn: {
        padding: 4,
    },
    searchContainer: {
        paddingHorizontal: 20,
        marginBottom: 16,
    },
    searchBar: {
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: '#F1F5F9',
        borderRadius: 18,
        paddingHorizontal: 16,
        height: 52,
        gap: 12,
        borderWidth: 1,
        borderColor: '#E2E8F0',
    },
    searchInput: {
        flex: 1,
        fontSize: 15,
        color: PWA.text,
        fontWeight: '600',
    },
    subHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingHorizontal: 20,
        marginBottom: 16,
    },
    viewModeBtn: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
        padding: 8,
    },
    subHeaderText: {
        fontSize: 11,
        fontWeight: '700',
        color: PWA.muted,
        textTransform: 'uppercase',
    },
    createBtn: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 4,
        backgroundColor: PWA.primary,
        paddingHorizontal: 12,
        paddingVertical: 6,
        borderRadius: 12,
    },
    createBtnText: {
        fontSize: 11,
        fontWeight: '800',
        color: '#FFF',
    },
    gridContainer: {
        flex: 1,
        flexDirection: 'row',
        flexWrap: 'wrap',
        padding: 10,
        gap: 10,
    },
    gridItem: {
        flexBasis: '48%',
        backgroundColor: PWA.card,
        borderWidth: 1,
        borderColor: PWA.border,
        borderRadius: 16,
        justifyContent: 'center',
        alignItems: 'center',
        padding: 16,
    },
    gridItemText: {
        fontSize: 14,
        fontWeight: '800',
        color: PWA.text,
    },
    listContent: {
        paddingHorizontal: 20,
        paddingTop: 12,
        paddingBottom: 40,
    },
    item: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: 14,
        borderBottomWidth: 1,
        borderBottomColor: '#F1F5F9',
    },
    itemIconContainer: {
        width: 40,
        height: 40,
        borderRadius: 12,
        backgroundColor: '#F1F5F9',
        justifyContent: 'center',
        alignItems: 'center',
        marginRight: 14,
    },
    itemInfo: {
        flex: 1,
    },
    itemHeader: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
    },
    itemName: {
        fontSize: 15,
        fontWeight: '700',
        color: PWA.text,
    },
    itemMeta: {
        fontSize: 12,
        color: '#64748B',
        fontWeight: '500',
        marginTop: 2,
    },
});

export default React.memo(AdvancedExercisePicker);
