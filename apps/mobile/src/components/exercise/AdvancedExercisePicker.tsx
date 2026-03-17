import React, { useState, useMemo } from 'react';
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
import { SearchIcon, XCircleIcon, ActivityIcon, PlusCircleIcon, StarIcon } from '../icons';
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
}

export const AdvancedExercisePicker: React.FC<AdvancedExercisePickerProps> = ({
    visible,
    onClose,
    onSelect,
}) => {
    const { exerciseList } = useExerciseStore();
    const [search, setSearch] = useState('');
    const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
    const [frequentExerciseIds] = useState<Set<string>>(new Set());
    const [favoriteExerciseIds, setFavoriteExerciseIds] = useState<Set<string>>(new Set());

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

    const closeWithAnimation = () => {
        translateY.value = withTiming(SCREEN_HEIGHT, {}, () => {
            runOnJS(onClose)();
        });
    };

    const categories = useMemo(() => {
        const cats = new Set<string>();
        exerciseList.forEach(ex => {
            if (ex.category) cats.add(ex.category);
        });
        return Array.from(cats);
    }, [exerciseList]);

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

                        <View style={{ height: 44 }}>
                            <FlatList
                                horizontal
                                showsHorizontalScrollIndicator={false}
                                data={categories}
                                contentContainerStyle={styles.categoryRow}
                                renderItem={({ item }) => (
                                    <TouchableOpacity
                                        onPress={() => setSelectedCategory(selectedCategory === item ? null : item)}
                                        style={[
                                            styles.categoryChip,
                                            selectedCategory === item && styles.categoryChipActive
                                        ]}
                                    >
                                        <Text style={[
                                            styles.categoryText,
                                            selectedCategory === item && styles.categoryTextActive
                                        ]}>{item}</Text>
                                    </TouchableOpacity>
                                )}
                            />
                        </View>

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
    categoryRow: {
        paddingHorizontal: 20,
        gap: 8,
    },
    categoryChip: {
        paddingHorizontal: 14,
        paddingVertical: 6,
        borderRadius: 14,
        backgroundColor: '#F1F5F9',
        borderWidth: 1,
        borderColor: '#E2E8F0',
        height: 34,
        justifyContent: 'center',
    },
    categoryChipActive: {
        backgroundColor: PWA.primary,
        borderColor: PWA.primary,
    },
    categoryText: {
        fontSize: 11,
        fontWeight: '800',
        color: '#64748B',
        textTransform: 'uppercase',
    },
    categoryTextActive: {
        color: '#FFFFFF',
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
